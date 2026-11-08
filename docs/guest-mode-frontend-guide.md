# Hướng dẫn API chế độ khách

Backend hỗ trợ API khách tại `/api/v1/guest`; không cần `Authorization`. Chỉ đề và video được cấu hình làm mẫu mới mở được. Học sinh đã đăng nhập tiếp tục dùng API hiện tại.

## 1. Cấu hình nội dung mẫu

Sau khi import và xuất bản đề, cấu hình các UUID trong môi trường chạy backend:

```properties
app.guest.exam-ids=11111111-1111-1111-1111-111111111111,22222222-2222-2222-2222-222222222222
app.guest.video-id=33333333-3333-3333-3333-333333333333
```

`app.guest.exam-ids` chứa nhiều ID đề `GENERAL_EXAM` và/hoặc `MOCK`; `app.guest.video-id` là một video. Có thể dùng biến môi trường Spring Boot `APP_GUEST_EXAM_IDS` và `APP_GUEST_VIDEO_ID`. Nếu không cấu hình, không đề hoặc video nào được mở cho khách. UUID sai định dạng làm ứng dụng khởi động lỗi; đề không tồn tại hoặc chưa xuất bản không xuất hiện trong danh sách. Thay cấu hình cần khởi động lại ứng dụng.

Không cần migration, thay API import hay tạo dữ liệu bài nộp trong database. Danh sách video vẫn hiển thị tất cả tiêu đề; chỉ video mẫu có thể mở nội dung.

## 2. Phiên làm bài

Mỗi lượt làm của khách có một mã phiên ngẫu nhiên, tạm thời nằm trong bộ nhớ của backend. API bắt đầu trả mã này ở trường `submissionId` để frontend dễ dùng cùng màn hình hiện tại. **Đây không phải ID của submission trong database** và không dùng được với API học sinh.

Gửi mã phiên trong header cho các request tiếp theo:

```http
X-Guest-Attempt: <submissionId từ API bắt đầu>
```

Giữ mã phiên như thông tin riêng của lượt làm, không đưa vào URL hoặc chia sẻ. Để tiếp tục lượt chưa nộp, gọi lại API bắt đầu tương ứng với header này; không gửi header khi bắt đầu lượt mới. Mỗi phiên tồn tại tối đa 24 giờ từ lúc bắt đầu. Tối đa 1.000 phiên trên mỗi instance; khi đầy, API bắt đầu trả `503`. Phiên hết hạn bị dọn khi có request bắt đầu lượt mới.

**Giới hạn vận hành:** phiên chỉ nằm trên instance đã tạo, mất khi instance khởi động lại và không chia sẻ giữa các instance. Nếu chạy nhiều instance cần sticky routing cho toàn bộ request của phiên; khi instance mất, lượt làm không khôi phục được. Không có lịch sử lâu dài, đồng bộ giữa thiết bị hoặc chuyển kết quả khách vào tài khoản. Nên cấu hình rate limiting ở gateway/reverse proxy cho API công khai; giới hạn 1.000 phiên không thay thế rate limiting.

Frontend có thể giữ tạm câu trả lời và mã phiên trên cùng trình duyệt để tiếp tục; phải xử lý trường hợp phiên hết hạn/mất. Không đảm bảo giới hạn thi thử một lần trên các trình duyệt hoặc thiết bị khác nhau.

## 3. Luyện đề và luyện thi

### 3.1 Danh sách đề mẫu

```http
GET /api/v1/guest/exams?mode=GENERAL_EXAM
GET /api/v1/guest/exams?mode=MOCK
```

Ví dụ response:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "title": "Đề mẫu",
    "mode": "MOCK",
    "durationMinutes": 50,
    "totalQuestions": 40
  }
]
```

Chỉ trả đề có ID trong cấu hình, đúng chế độ và `PUBLISHED`. Danh sách rỗng nếu chưa có đề mẫu phù hợp.

### 3.2 Bắt đầu, lấy đề và chuyển phần

```http
POST /api/v1/guest/exams/{examId}/start
```

Không cần body. Response cùng dạng API bắt đầu của học sinh: `submissionId`, `examId`, `mode`, `totalQuestions`, `timeLimitMinutes`, `serverNow`, `startedAt`, `expiresAt`, `partOrder`, `parts`. Giữ `submissionId` làm mã phiên và `partOrder` để hiển thị. Backend chọn thứ tự part theo cấu hình của đề.

```http
GET /api/v1/guest/exams/{examId}
X-Guest-Attempt: <submissionId>
```

Response theo DTO đề hiện tại, các part và `displayNumber` đã xếp theo phiên; không chứa đáp án đúng/lời giải hoặc URL đáp án. Lưu lựa chọn theo `questionId`, không dựa vào thứ tự mảng.

Khi vào hoặc chuyển part:

```http
PUT /api/v1/guest/exams/{examId}/parts/{partId}/time
X-Guest-Attempt: <submissionId>
```

Trả `204`. Backend đo thời gian từ các sự kiện; lặp lại part đang mở không đặt lại đồng hồ. `GET /api/v1/guest/exams/{examId}/part-logs` với cùng header trả các khoảng đã chốt; trước khi nộp không bao gồm khoảng đang chạy, sau khi nộp trả kết quả cuối.

Với `MOCK`, backend quyết định hạn nộp từ thời điểm bắt đầu và thời lượng đề. `serverNow`/`expiresAt` dùng để hiển thị đồng hồ; thời gian ân hạn nộp cấu hình `app.exam.submit-grace-seconds`, mặc định 30 giây, không phải thời gian làm thêm. Khi hết giờ backend từ chối chuyển part; sau hạn cộng ân hạn backend từ chối nộp. `GENERAL_EXAM` không có hạn nộp. Thời gian hiển thị theo múi giờ Việt Nam.

### 3.3 Nộp bài và xem kết quả

```http
POST /api/v1/guest/exams/{examId}/submit
X-Guest-Attempt: <submissionId>
Content-Type: application/json
```

```json
{
  "answers": [
    { "questionId": "44444444-4444-4444-4444-444444444444", "selectedOpt": "B" },
    { "questionId": "55555555-5555-5555-5555-555555555555", "selectedOpt": null }
  ]
}
```

`selectedOpt` là `A`, `B`, `C`, `D` hoặc `null`. Mỗi câu chỉ gửi một lần; câu không gửi được tính là bỏ trống. Backend từ chối câu không thuộc đề hoặc đáp án sai định dạng.

Response dùng cùng DTO kết quả như học sinh: `examId`, `examTitle`, tổng số câu, đúng/sai, `score`, `startedAt`, `submittedAt`, `parts`, `partTimeLogs`, `partTranslations`, `details` (các lựa chọn, đáp án đã chọn, đáp án đúng, lời giải, v.v.). Điểm = số câu đúng × 0,25. `submissionId` trong **response kết quả là null** vì không có bản ghi DB; tiếp tục dùng mã từ response bắt đầu nếu cần request lại. Khách luôn xem được đáp án và lời giải sau khi nộp.

Nếu gửi lại trong cùng phiên, backend trả kết quả lần nộp đầu, kể cả body mới khác; dùng để xử lý lỗi mạng. `GET /api/v1/guest/exams/{examId}/answer-url` kèm header phiên trả URL đáp án sau khi nộp nếu đề có URL; trước khi nộp trả `403`, không có URL trả `404`. Video liên quan ngoài chuyên đề mẫu không xuất hiện trong kết quả để tránh lộ URL.

## 4. Video, homework và từ vựng

```http
GET /api/v1/guest/videos
```

```json
[
  { "videoId": "33333333-3333-3333-3333-333333333333", "title": "Chuyên đề mẫu", "accessible": true },
  { "videoId": "66666666-6666-6666-6666-666666666666", "title": "Chuyên đề khác", "accessible": false }
]
```

Danh sách không trả URL nội dung. Frontend hiển thị các mục `accessible: false` là khóa và yêu cầu đăng nhập khi bấm. Với video mẫu:

```http
GET /api/v1/guest/videos/{videoId}/learning-content
GET /api/v1/guest/videos/{videoId}/vocabularies
```

`learning-content` trả video, tài liệu, homework và từ vựng như API học sinh, nhưng không trả URL đáp án homework trước khi nộp. Video khác trả `404` dù khách biết ID.

### 4.1 Homework

```http
POST /api/v1/guest/videos/{videoId}/homework/start
```

Response bắt đầu có cùng dạng như trên, `mode: "HOMEWORK"`, không có thời hạn và danh sách part rỗng. Giữ `submissionId` làm mã phiên.

```http
POST /api/v1/guest/videos/{videoId}/homework/submit
X-Guest-Attempt: <submissionId>
Content-Type: application/json
```

Body là `{ "answers": [...] }` như khi nộp đề. Backend tự tìm homework gắn với video mẫu và trả kết quả, đáp án, lời giải; không tạo lịch sử. Lấy URL đáp án sau khi nộp (nếu có):

```http
GET /api/v1/guest/videos/{videoId}/homework/answers
X-Guest-Attempt: <submissionId>
```

Response cùng dạng API homework hiện tại: `videoId`, `homeworkId`, `answers_url` (có thể null). Chưa nộp trả `403`. Nộp lại cùng phiên trả kết quả đầu tiên.

Từ vựng của video mẫu dùng cùng dữ liệu và tương tác frontend hiện tại; những chuyên đề khác yêu cầu đăng nhập.

## 5. Lỗi và kiểm thử tích hợp

- `400`: chế độ hoặc nội dung nộp không hợp lệ.
- `403`: yêu cầu URL đáp án trước khi nộp.
- `404`: nội dung không được phép, không tồn tại, hoặc phiên không tồn tại/hết hạn/mất sau restart.
- `409`: lượt đã nộp hoặc đã hết giờ trong các thao tác không được phép.
- `503`: đã đạt giới hạn phiên của instance.

Frontend cần xử lý lỗi rõ ràng, không tự chuyển sang API học sinh khi khách bị từ chối. Kiểm tra: danh sách chỉ có đề mẫu; video khác không mở được bằng cách thay ID; xem/điều hướng part và đồng hồ; chấm và nộp lại; hiển thị kết quả; homework/từ vựng mẫu; luồng học sinh không thay đổi.

Link video/tài liệu đã được cấp có thể bị chia sẻ; quyền truy cập trực tiếp còn phụ thuộc cấu hình YouTube/Drive hoặc nhà cung cấp lưu trữ.
