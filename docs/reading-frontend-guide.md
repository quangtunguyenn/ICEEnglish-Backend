# Hướng dẫn tích hợp frontend: thư viện đọc hiểu

## 1. Phạm vi và triển khai backend

- Khách thấy metadata của mọi bài `PUBLISHED` và chỉ mở bài có `guestAccessible=true`.
- Học sinh đã đăng nhập mở mọi bài `PUBLISHED`. Admin quản lý bài ở API riêng.
- Chọn đáp án nhận phản hồi ngay, có thể thử lại; không lưu lịch sử hoặc điểm vào database.
- Từ vựng bài đọc tách khỏi từ vựng video. Có thể dùng lại component luyện từ vựng, nhưng phải gọi API bài đọc.

**Trước khi dùng API:** cần áp dụng `src/main/resources/db/migration/V6__Create_reading_library.sql` theo quy trình triển khai PostgreSQL của dự án. Dự án hiện không có Flyway tự chạy migration; chỉ build/chạy backend không đảm bảo bảng đã được tạo. Migration chỉ tạo 5 bảng `reading_*` và index của chúng, không sửa bảng cũ. Sau đó admin import và publish ít nhất một bài. Không chạy SQL trên production nếu chưa theo quy trình triển khai/backup của hệ thống.

Base URL `/api/v1`. Response là JSON trực tiếp, **không có lớp `data`**. API học sinh và admin gửi `Authorization: Bearer <JWT>`; API khách không yêu cầu đăng nhập. API cũ không đổi.

## 2. Danh sách và chủ đề

```http
GET /api/v1/guest/reading-blogs?page=0&size=12
GET /api/v1/reading-blogs?page=0&size=12
GET /api/v1/guest/reading-blogs/topics
GET /api/v1/reading-blogs/topics
```

Tham số tùy chọn của danh sách:

| Tham số | Mô tả |
|---|---|
| `page` | Trang từ 0, mặc định 0 |
| `size` | 1–100, mặc định 12 |
| `keyword` | Tìm chuỗi trong tiêu đề, không phân biệt hoa thường; tối đa 200 ký tự |
| `topic` | Slug chủ đề, ví dụ `environment` |
| `contentType` | `ARTICLE`, `MAGAZINE`, `STORY` hoặc `PASSAGE` |

```json
{
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "title": "Small Gardens, Big Changes",
      "topic": { "slug": "environment", "name": "Environment" },
      "summary": "How a school garden can benefit students and neighbours.",
      "contentType": "ARTICLE",
      "difficulty": "MEDIUM",
      "estimatedReadingMinutes": 3,
      "coverImageUrl": null,
      "accessible": false,
      "status": "PUBLISHED",
      "guestAccessible": false,
      "publishedAt": "2026-11-09T01:00:00+07:00"
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1
}
```

Với khách, `accessible=false` nghĩa là hiển thị thẻ khóa và yêu cầu đăng nhập khi bấm. Với học sinh, `accessible=true` cho mọi bài trong danh sách. Danh sách không trả nội dung hoặc câu hỏi của bài khóa. API chủ đề trả mảng `{ "slug", "name" }` của chủ đề có bài `PUBLISHED`.

## 3. Đọc bài và từ vựng

```http
GET /api/v1/guest/reading-blogs/{blogId}
GET /api/v1/reading-blogs/{blogId}
GET /api/v1/guest/reading-blogs/{blogId}/vocabularies
GET /api/v1/reading-blogs/{blogId}/vocabularies
```

Ví dụ response chi tiết rút gọn:

```json
{
  "blog": { "id": "11111111-1111-1111-1111-111111111111", "title": "Small Gardens", "accessible": true },
  "revision": 1,
  "passageBlocks": [
    { "id": "p1", "text": "Students created a garden.", "translation": "Học sinh tạo ra một khu vườn." }
  ],
  "questions": [
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "orderIndex": 1,
      "content": "What is the main idea?",
      "skillType": "SKIMMING",
      "answers": [
        { "id": "33333333-3333-3333-3333-333333333333", "optionKey": "A", "content": "A school garden helps students." }
      ]
    }
  ],
  "vocabularies": [
    {
      "id": "44444444-4444-4444-4444-444444444444",
      "orderIndex": 1,
      "word": "garden",
      "phonetic": null,
      "meaning": "khu vườn",
      "exampleSentence": null,
      "exampleTranslation": null,
      "passageBlockId": "p1"
    }
  ],
  "sourceName": null,
  "sourceUrl": null
}
```

Ví dụ chỉ rút gọn các trường của `blog` và số lựa chọn; thực tế mỗi câu có bốn phương án A–D. Nội dung và bản dịch là **văn bản thuần**, render như text, không dùng `innerHTML`. `translation`, phiên âm, ví dụ và thông tin nguồn có thể `null`. Thời gian đọc chỉ tham khảo.

Route luyện từ vựng nên lấy lại dữ liệu bằng API khi tải lại trang, không phụ thuộc hoàn toàn vào `location.state`. Không gọi API video hoặc thêm bài đọc vào danh sách chuyên đề video. Với khách, bài khóa, chưa publish hoặc không tồn tại trả `404` ở cả API chi tiết và từ vựng.

## 4. Trả lời câu hỏi

Chọn một đáp án và gọi:

```http
POST /api/v1/guest/reading-blogs/{blogId}/questions/{questionId}/answer
Content-Type: application/json
```

Học sinh dùng cùng đường dẫn nhưng bỏ `/guest`. Body:

```json
{ "revision": 1, "selectedOption": "C" }
```

`revision` lấy từ response chi tiết; `selectedOption` là một trong `A`, `B`, `C`, `D`, không phải ID của answer. Response:

```json
{
  "questionId": "22222222-2222-2222-2222-222222222222",
  "selectedOption": "C",
  "isCorrect": true,
  "optionExplanation": "Mốc thời gian xuất hiện ở đầu đoạn hai.",
  "questionExplanation": "Tìm cụm chỉ thời gian ở đầu đoạn hai.",
  "evidenceBlockIds": ["p2"]
}
```

`optionExplanation` có thể null. Hiển thị giải thích và liên kết về đoạn theo `evidenceBlockIds`; người dùng có thể chọn lại. Trước khi trả lời, API chi tiết không chứa đáp án đúng, giải thích hay ID đoạn bằng chứng. Do được phản hồi ngay và thử lại, đây là chế độ học, không phải bài thi bí mật.

Frontend có thể giữ thống kê tạm như “X/Y câu đã trả lời đúng”; tự quy định rõ cách tính khi chọn nhiều lần. Không có API nộp cả bài, lịch sử hay khôi phục tiến độ trên server. Nếu nhận `409` do revision cũ, thông báo và tải lại bài.

Kỹ năng: `SKIMMING`, `SCANNING`, `INFERENCE`, `VOCABULARY_IN_CONTEXT`, `REFERENCE`.

## 5. API quản trị: ADMIN

```http
POST  /api/v1/admin/reading-blogs/import
GET   /api/v1/admin/reading-blogs
GET   /api/v1/admin/reading-blogs/{blogId}
PATCH /api/v1/admin/reading-blogs/{blogId}/publish
PATCH /api/v1/admin/reading-blogs/{blogId}/archive
PUT   /api/v1/admin/reading-blogs/{blogId}/guest-access
```

Import gửi `multipart/form-data`, field `file` là JSON; mẫu tại `samples/reading-blog-sample.json`. Thành công trả `201`:

```json
{ "id": "11111111-1111-1111-1111-111111111111", "status": "DRAFT", "revision": 1 }
```

Import tạo bài mới ở `DRAFT`, kể cả khi cho phép khách. Admin xem trước bằng API chi tiết quản trị (gồm đáp án và lời giải), sau đó publish. Bản đầu không có API chỉnh sửa hoặc xóa vật lý; archive để ẩn bài.

Danh sách admin dùng tham số lọc như danh sách người học, thêm `status=DRAFT|PUBLISHED|ARCHIVED`; bỏ `status` để xem tất cả. Preview trả `blog`, `revision`, `passageBlocks`, `questions`, `vocabularies`, `sourceName`, `sourceUrl`, `createdBy`. Các lựa chọn trong preview có `correct` và `explanation`.

Đặt quyền khách bằng:

```json
{ "guestAccessible": true }
```

Publish, archive và cập nhật quyền trả `204`. Sau khi quyền bị tắt hoặc bài archive, backend kiểm tra lại quyền cho request tiếp theo; dữ liệu bài đã gửi tới trình duyệt trước đó không thể thu hồi.

### Quy tắc file import

- `schema_version` phải là `1`. `blog` cần title, summary, topic có slug/name, content type, difficulty và `guest_accessible`.
- Slug chỉ gồm chữ thường ASCII, số và dấu `-`; slug đã tồn tại phải có cùng tên sau khi bỏ khoảng trắng đầu/cuối.
- Từ 1–200 đoạn; 0–500 từ vựng; 1–200 câu hỏi; mỗi câu đúng bốn đáp án A–D và một đáp án đúng.
- `order_index` là số nguyên dương, không trùng trong cùng danh sách. ID đoạn không trùng; ID đoạn ở từ vựng và bằng chứng phải tồn tại. Mỗi câu cần ít nhất một đoạn bằng chứng và giải thích.
- Kích thước file tối đa 2 MiB ở service; cấu hình multipart của máy chủ có thể áp dụng giới hạn nhỏ hơn. Tên trường import dùng `snake_case` như file mẫu; response API dùng `camelCase`.
- Lỗi import không tạo bài dở dang. Không import nội dung chưa có quyền đăng tải.
- Nên giới hạn tần suất API khách tại gateway hoặc reverse proxy; module này không tự áp dụng rate limiting.

## 6. Xử lý lỗi và kiểm thử frontend

Các lỗi nghiệp vụ trả JSON `timestamp`, `status`, `error`, `message`, `details`:

- `400`: tham số, JSON hoặc đáp án không hợp lệ.
- `401`: thiếu JWT khi gọi API học sinh/admin.
- `403`: đã đăng nhập nhưng không đủ quyền admin.
- `404`: bài không tồn tại hoặc không được phép mở; câu hỏi/lựa chọn không thuộc bài.
- `409`: revision cũ, slug chủ đề trùng tên khác hoặc bài không đủ điều kiện publish.

Các lỗi HTTP khác như multipart quá lớn có thể do Spring/server trả theo định dạng riêng. Frontend cần hiển thị lỗi và cho thử lại, không biến lỗi thành danh sách rỗng.

Nghiệm thu: danh sách khách có bài khóa; khách không mở được bằng cách sửa URL; học sinh thấy mọi bài đã publish; bản dịch đúng đoạn; trả lời hiển thị phản hồi ngay; bật/tắt quyền khách có hiệu lực; luyện từ vựng không làm thay đổi danh sách video; `DRAFT` và `ARCHIVED` không lộ cho người học.
