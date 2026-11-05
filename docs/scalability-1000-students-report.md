# Báo cáo khả năng chịu tải – 1000 học sinh đồng thời

> Phạm vi: backend hiện tại (Spring Boot, JPA/Hibernate, PostgreSQL, JWT stateless), chạy **1 instance + 1 PostgreSQL**.
> Đây là **phân tích tĩnh từ mã nguồn + mô hình ước lượng**, chưa đo bằng kiểm thử tải. Các con số thời gian là ước lượng
> để so sánh tương đối; phải xác nhận bằng k6/JMeter (mục 7) trước khi chốt.

## 1. Kết luận nhanh

| Câu hỏi | Trả lời |
|---|---|
| 1000 HS đồng thời có khả thi trên 1 server? | **Có**, nhưng **chưa với cấu hình hiện tại** – cần một số chỉnh sửa nhỏ (mục 5). |
| Cần Kafka / RabbitMQ? | **Không.** Phần chậm là ghi DB, không phải xử lý nền. Hàng đợi chỉ làm HS phải chờ kết quả bất đồng bộ. |
| Cần Redis? | **Chưa.** Cache trong bộ nhớ (Caffeine) đủ cho 1 instance. Chỉ cần Redis khi chạy ≥ 2 instance và muốn dùng chung cache/rate limit. |
| Tác vụ nào gây chờ lâu / lỗi? | (1) Nộp bài đồng loạt, (2) Bắt đầu thi + tải đề đồng loạt, (3) Đăng nhập đồng loạt. Và **rủi ro mất bài** khi nộp sát giờ (mục 3.1). |
| Xem video, từ vựng, bài tập về nhà? | Nhẹ. Không đáng lo ở 1000 HS. |

## 2. Các giới hạn cấu hình hiện tại

| Hạng mục | Hiện trạng | Hệ quả |
|---|---|---|
| Pool DB (HikariCP) | Không cấu hình → **10 kết nối**, timeout chờ kết nối **30 s** | Chỉ 10 request chạm DB cùng lúc; còn lại xếp hàng, quá 30 s thì lỗi 500. |
| Tomcat | Mặc định 200 luồng, `accept-count` 100 | 1000 request đến cùng lúc: ~200 chạy, phần còn lại chờ. Không từ chối kết nối, nhưng trễ. |
| `spring.jpa.show-sql=true` | Bật | Mỗi câu SQL in ra stdout (đồng bộ). Nộp bài ~130 câu SQL → ~130 dòng log; trên console/Windows rất chậm và tuần tự hoá toàn hệ thống. |
| `ddl-auto=update` | Bật | Không an toàn cho production; không tạo index cho khoá ngoại. |
| Index | Không có `@Index`; Postgres **không** tự tạo index cho FK | Quét toàn bảng khi dữ liệu lớn (mục 4). |
| JDBC batching | Chưa bật | Mỗi `INSERT` là 1 round trip. |
| Cache | Không có (`@Cacheable` không tồn tại) | Đề thi bất biến vẫn bị đọc lại từ DB mỗi lần. |
| Nén HTTP | Không bật | Đề thi JSON lớn gửi nguyên văn (mục 3.2). |
| Rate limit đăng nhập | Không có | Dễ bị dò mật khẩu / quá tải CPU BCrypt. |
| Truy vấn người dùng mỗi request | `JwtAuthenticationFilter` gọi `userRepository.findByUsername` **mỗi request** | +1 truy vấn/request, dù role đã nằm trong JWT. |

## 3. Phân tích từng tác vụ

Ký hiệu: **S** = số câu SQL / request (đếm từ code). Thời gian giữ 1 kết nối DB ≈ S × (RTT + thời gian chạy SQL).
Mô hình: DB cùng máy ≈ 0,25 ms/câu; DB từ xa (cùng vùng cloud) ≈ 2 ms/câu. Thông lượng tối đa ≈ pool / thời gian giữ kết nối.

| Tác vụ | S (ước tính) | Giữ kết nối (DB local) | 1000 HS cùng lúc, pool 10 hiện tại | Đánh giá |
|---|---|---|---|---|
| Đăng nhập | 1 + **BCrypt ~80 ms CPU** | ~80 ms CPU | ~20 s cho người cuối (4 vCPU); các request khác cũng bị chậm | 🔴 |
| Xem danh sách video / titles | 2 | <1 ms | tức thì | 🟢 |
| Xem video (`learning-content`) | ~8–10 | ~3 ms | <1 s | 🟢 |
| Từ vựng (`vocabularies`) | 3 | ~1 ms | tức thì | 🟢 |
| Tải đề thi (`GET /exams/{id}`) | ~5 + payload lớn | ~2 ms | DB ổn, **nghẽn băng thông** (3.2) | 🟠 |
| Bắt đầu thi (`POST /submissions/start`) | ~8 | ~3 ms | ~0,3 s | 🟢 (DB) / 🟠 (đi kèm tải đề) |
| **Nộp bài thi (MOCK/GENERAL_EXAM, ~100 câu)** | **~130** (≈107 là INSERT lẻ) | ~35–40 ms | **~4 s** (DB local), **~28 s+ và lỗi 500** (DB từ xa) | 🔴 |
| Xem kết quả / review MOCK | ~115 (N+1) | ~30 ms | ~3 s | 🟠 |
| Bắt đầu bài về nhà | ~10 | ~3 ms | <1 s | 🟢 |
| Nộp bài về nhà (~12 câu) | ~23 | ~6 ms | <1 s | 🟢 |
| Ghi thời gian theo part (`PUT .../time`) | ~7 | ~2 ms | phụ thuộc tần suất gọi (3.5) | 🟠 |

### 3.1. Nộp bài đồng loạt – điểm rủi ro cao nhất

Mã: `SubmissionService.submitExam`.

1. **N câu INSERT lẻ**: `subDetailRepository.saveAll` sinh 1 `INSERT` cho mỗi câu (chưa bật batch) → ~100 round trip.
2. **N+1 ở `savePartTimeLogs`**: mỗi `partLog` có 1 `SELECT` + 1 `INSERT` riêng.
3. **Lazy load lặp**: `submission.getExam()`, `exam.getParts()` (được gọi 2–3 lần), `question.getPart()` (mỗi part một truy vấn).
4. **`findAllByExamId` tải lại toàn bộ câu hỏi** (gồm `explanation` dạng TEXT) cho mỗi lần nộp, dù đề không đổi. Truy vấn dùng `WHERE p.exam.id = :id OR q.exam.id = :id` nên khó dùng index.
5. **Hết giờ = từ chối bài**: hàm kiểm tra `!now.isBefore(startedAt + timeLimit)` dùng **giờ lúc xử lý**, không có thời gian ân hạn. Với MOCK, mọi HS bắt đầu cùng giờ → frontend tự nộp **cùng một giây** lúc hết giờ → request xếp hàng vài giây → bị trả `"Lượt thi đã hết thời gian."` → **HS mất toàn bộ bài làm** (đáp án chỉ nằm ở frontend, server không lưu tạm).
6. **Nộp lặp**: nếu request đầu thực ra đã thành công nhưng HS không nhận được phản hồi (timeout) rồi bấm nộp lại → lỗi `"Lượt thi này đã được nộp."` thay vì trả kết quả cũ.

Ưu điểm cần ghi nhận: khoá `FOR UPDATE` chỉ trên **dòng submission của chính HS đó** nên các HS khác nhau **không chặn nhau**, không có nguy cơ deadlock giữa HS.

### 3.2. Bắt đầu thi / tải đề đồng loạt

- `GET /api/v1/exams/{id}` trả về cả `questions` lẫn `parts[].questions` kèm `passage_text` → JSON có thể **60–300 KB**, **không nén**.
  1000 HS × 200 KB = **~200 MB** bị đẩy ra trong vài giây; trên đường truyền 100 Mbps cần **~16 s**. Đây có thể là nghẽn thực tế đầu tiên, trước cả DB.
- Nội dung đề `PUBLISHED` là bất biến → rất phù hợp để cache (server và HTTP).
- `startExam` đếm câu bằng `findAllByExamId(...).size()` (tải toàn bộ câu hỏi chỉ để đếm).
- `findByUsernameForUpdate` khoá dòng `users` của chính HS → chỉ tuần tự hoá các request song song của cùng 1 HS (hợp lý, tránh tạo trùng lượt thi).

### 3.3. Đăng nhập đồng loạt

- `BCryptPasswordEncoder` mặc định (cost 10) ≈ 50–100 ms CPU/lần. 1000 đăng nhập cùng lúc trên 4 vCPU ≈ 12–25 s cho người cuối; trong lúc đó 200 luồng Tomcat bị chiếm → **các API khác cũng chậm theo**.
- Token sống 24 giờ nên tình huống này chỉ xảy ra khi cả lớp đăng nhập cùng lúc (đầu buổi thi).
- Chưa có giới hạn tần suất; thông báo lỗi phân biệt "Tài khoản không tồn tại" / "Sai mật khẩu" (lộ danh sách tài khoản).
- Không nên hạ cost BCrypt để chạy nhanh (giảm bảo mật). Nên giãn tải (3 ở mục 5) và giới hạn tần suất.

### 3.4. Xem video, từ vựng, bài tập về nhà

- Video phát từ YouTube/Drive → **backend không phục vụ luồng video**, chỉ trả metadata.
- Mỗi request ~5–10 câu SQL nhỏ (có lazy load tuần tự theo từng part). Với 1000 HS đồng thời vẫn dưới 1 s.
- `GET /videos` không phân trang (`findAll`) – chưa vấn đề khi số video còn vài trăm.

### 3.5. Ghi thời gian theo part (MOCK)

`PUT /submissions/{id}/parts/{partId}/time` ≈ 7 câu SQL/lần. Nếu frontend gọi mỗi 1 giây cho mỗi HS → **1000 req/s ≈ 7000 SQL/s** → vượt pool 10. Nếu chỉ gọi khi chuyển part hoặc mỗi ≥ 30 s thì không sao. Khuyến nghị: chỉ gửi `partLogs` kèm lúc nộp bài (API nộp đã hỗ trợ).

### 3.6. Tăng trưởng dữ liệu (tác động sau vài tháng)

- `sub_details` tăng ~100 dòng/lượt thi. 1000 HS × 20 lượt ≈ 2 triệu dòng. Không có index trên `submission_id` → `findBySubmissionId` (review kết quả) phải quét toàn bảng (hàng chục–trăm ms mỗi lần, và nhân với số HS xem đồng thời).
- Các truy vấn lọc theo `exam_id`, `part_id`, `video_id`, `(user_id, exam_id, mode)` cũng quét toàn bảng khi bảng lớn.

## 4. Index nên thêm

| Bảng | Cột | Phục vụ |
|---|---|---|
| `sub_details` | `submission_id` | review kết quả (`findBySubmissionId`) |
| `submissions` | `(user_id, exam_id, mode, started_at DESC)` | bắt đầu thi, điểm, review |
| `questions` | `exam_id`, `part_id`, `related_video_id` | tải câu hỏi theo đề/part |
| `exam_parts` | `exam_id` | tải part của đề |
| `exams` | `(video_id, exam_type)`, `(status, created_at)` | bài về nhà theo video, danh sách đề |
| `vocabularies` | `video_id` | từ vựng theo video |
| `part_time_logs` | `submission_id` (đã có unique `(submission, part)`) | log thời gian |

Lưu ý: `ddl-auto=update` chỉ tạo index khai báo trong `@Table(indexes=...)`; file `db/migration/V1__...sql` **không tự chạy** vì dự án chưa có Flyway/Liquibase.

## 5. Phương án đề xuất (xếp theo hiệu quả / chi phí)

### Mức 1 – Bắt buộc trước khi chạy 1000 HS (cấu hình + sửa nhỏ, rủi ro thấp)

1. **Chống mất bài lúc hết giờ**: thêm thời gian ân hạn phía server (vd. 30–60 s) cho `submitExam`, hoặc tính hết hạn theo thời điểm request **đến** thay vì lúc xử lý. Frontend: giữ đáp án trong `localStorage`, tự thử nộp lại; thêm **jitter ngẫu nhiên 0–5 s** cho nộp tự động lúc hết giờ.
2. **Nộp bài idempotent**: nếu lượt thi đã nộp, trả lại kết quả đã chấm thay vì ném lỗi.
3. **Cấu hình `application.properties`** (giá trị tham khảo, chỉnh theo máy):

   ```properties
   spring.jpa.show-sql=false
   spring.jpa.hibernate.ddl-auto=validate          # hoặc none; dùng Flyway cho schema
   spring.jpa.open-in-view=false                   # cần test lại các chỗ lazy-load ngoài transaction
   spring.jpa.properties.hibernate.jdbc.batch_size=50
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true

   spring.datasource.hikari.maximum-pool-size=30   # kiểm tra max_connections của Postgres (mặc định 100)
   spring.datasource.hikari.minimum-idle=10
   spring.datasource.hikari.connection-timeout=10000

   server.compression.enabled=true
   server.compression.mime-types=application/json,text/plain
   server.compression.min-response-size=1024
   server.tomcat.threads.max=300
   server.tomcat.accept-count=500
   ```

4. **Bỏ truy vấn role mỗi request**: đọc `role` từ claim JWT (đã có `jwtService.extractRole`), hoặc cache `username → role` 1–5 phút bằng Caffeine.
5. **Thêm index** (mục 4).
6. **`startExam`**: thay `findAllByExamId(...).size()` bằng truy vấn `COUNT` (hoặc lấy từ cache đề).

### Mức 2 – Giảm số câu SQL của các tác vụ nặng

7. **Cache đề thi (Caffeine, `@Cacheable`)**: danh sách câu hỏi + part của đề `PUBLISHED`, nội dung bài về nhà theo video, danh sách video, từ vựng theo video. Xoá cache khi publish/archive/import/toggle. Sau khi cache, nộp bài không còn phải đọc câu hỏi/part/video từ DB.
8. **Sửa N+1**:
   - `reviewMockExam`: một truy vấn `SELECT d FROM SubDetail d JOIN FETCH d.question q LEFT JOIN FETCH q.part ... WHERE d.submission.id = :id`.
   - `submitExam`/`firstQuestionPartTypes`: lấy question kèm `part` bằng `JOIN FETCH`/`@EntityGraph`, hoặc dùng cache đề.
   - `savePartTimeLogs`: một truy vấn `findBySubmissionId` rồi so khớp trong bộ nhớ, thay vì SELECT từng part.
9. **Viết lại `findAllByExamId`** thành hai truy vấn đơn giản hoặc `UNION` thay cho `OR` trên join, để dùng được index.
10. **HTTP cache cho đề**: `Cache-Control`/`ETag` cho `GET /exams/{id}` và nội dung video/homework (vì bất biến sau publish). Với tải đề đồng loạt, nên đặt **Nginx/CDN** phía trước để HS tải đề từ cache thay vì từ backend.
11. **Giảm payload đề**: không trả lặp câu hỏi ở cả `questions` và `parts[].questions` nếu frontend chỉ dùng một dạng.

### Mức 3 – Mở rộng và vận hành

12. **Chạy 2 instance sau load balancer** (ứng dụng stateless nhờ JWT, không có session). Nếu cache phải thống nhất giữa các instance thì dùng **Redis**, nếu chấp nhận TTL ngắn (30–60 s) thì Caffeine riêng mỗi instance là đủ.
13. **Rate limit đăng nhập** ở Nginx (hoặc Bucket4j): vd. 5 lần/phút/IP+username. Thống nhất thông báo lỗi để không lộ tài khoản tồn tại.
14. **Giãn tải đăng nhập** khi thi: hướng dẫn HS đăng nhập trước giờ thi (token 24 h), frontend tái dùng token.
15. **PostgreSQL**: để DB trên máy/mạng gần backend (RTT càng thấp càng quan trọng vì nộp bài nhiều round trip), `shared_buffers` ~25% RAM, bật `pg_stat_statements` để tìm truy vấn chậm.
16. **Giám sát**: thêm Actuator + Micrometer (pool Hikari `active/pending`, p95/p99 của từng endpoint, GC) để nhìn thấy hàng đợi thật.

### Khi nào mới cần hàng đợi (Kafka/RabbitMQ) hoặc Redis

- **Hàng đợi**: chỉ cân nhắc nếu sau này có tác vụ chậm tách rời được (gửi email, chấm tự luận bằng AI, xuất báo cáo). Chấm trắc nghiệm hiện chỉ vài ms trong bộ nhớ.
- **Redis**: khi ≥ 2 instance cần cache chung / rate limit chung / lưu tạm đáp án đang làm dở (autosave) – xem tính năng tùy chọn bên dưới.
- **Tùy chọn – autosave đáp án**: nếu muốn HS không mất bài khi rớt mạng, thêm endpoint lưu tạm đáp án theo part (ghi nhẹ, ít lần) hoặc lưu ở `localStorage` phía frontend (rẻ nhất, nên làm trước).

## 6. Kỳ vọng sau khi áp dụng

Ước lượng (DB cùng máy; DB từ xa nhân thời gian DB với ~5–8 lần trước khi cache):

| Tác vụ (1000 HS cùng lúc) | Hiện tại | Sau Mức 1 | Sau Mức 1 + 2 |
|---|---|---|---|
| Nộp bài thi 100 câu | ~4 s (local) / 28 s+ & lỗi (DB xa) | ~1–2 s | <1 s |
| Xem review MOCK | ~3 s | ~1 s | <0,5 s |
| Bắt đầu thi | ~0,3 s (+ tải đề chậm do băng thông) | ~0,2 s | ~0,1 s, đề có nén/cache |
| Đăng nhập | ~20 s | ~20 s (CPU-bound) | giãn tải + 2 instance → ~10 s |
| Video / từ vựng / bài về nhà | <1 s | <0,5 s | <0,2 s |

Đăng nhập là tác vụ duy nhất bị giới hạn bởi CPU; chỉ cải thiện bằng thêm CPU/instance hoặc giãn tải, không bằng cache.

## 7. Kế hoạch kiểm thử tải (bắt buộc để xác nhận)

Công cụ: k6 hoặc JMeter, chạy trên máy khác với backend; dùng DB có dữ liệu giả lập gần thực tế (≥ 100 đề, ≥ 1 triệu `sub_details`).

| Kịch bản | Tải | Chỉ tiêu đạt |
|---|---|---|
| A. Đăng nhập đồng loạt | 1000 VU trong 10 s | p95 < 15 s, 0 lỗi |
| B. Tải đề + bắt đầu thi | 1000 VU đồng thời | p95 < 3 s, 0 lỗi 5xx |
| C. Nộp bài MOCK 100 câu, 7 part | 1000 VU đồng thời trong 5 s | p95 < 3 s, 0 lỗi, `hikari pending` về 0 |
| D. Nộp sát hạn | nộp trong ±2 s quanh thời điểm hết hạn | 0 bài bị từ chối oan |
| E. Review MOCK | 1000 VU | p95 < 2 s |
| F. Xem video + từ vựng + homework | 1000 VU, giữ 5 phút | p95 < 500 ms |
| G. Tải trộn | 1000 VU: 60% xem, 20% homework, 20% thi | không tăng lỗi, CPU < 70% |

Theo dõi: `hikaricp_connections_pending`, p95/p99 theo endpoint, CPU backend/DB, `pg_stat_activity` (số kết nối), `pg_stat_statements` (câu SQL tốn nhất), băng thông ra.

## 8. Thứ tự thực hiện gợi ý

1. Mục 5.1–5.6 (cấu hình, ân hạn hết giờ, nộp idempotent, bỏ query role, index, COUNT).
2. Chạy kịch bản C và D để có số đo thật.
3. Mục 5.7–5.9 (cache đề, sửa N+1, viết lại truy vấn câu hỏi).
4. Nén/cache HTTP + Nginx (5.10); chạy lại B.
5. Thêm instance thứ 2 và rate limit đăng nhập (5.12–5.13) nếu số đo cho thấy cần.

## 9. Lưu ý phụ (ngoài hiệu năng)

- `JwtService` có **khoá bí mật mặc định ghi cứng** trong mã. Production phải đặt `application.security.jwt.secret-key` qua biến môi trường.
- `answersRevealed` chưa được dùng để chặn `GET /submissions/exams/{examId}/answer-url`.
- `spring-boot-devtools` nên bảo đảm không nằm trong bản đóng gói production.
