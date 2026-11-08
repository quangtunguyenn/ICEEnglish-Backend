# API quản trị (Admin / Teacher)

Tài liệu dành cho frontend quản trị của ICEEnglish Backend. Base URL mặc định:

```text
http://localhost:8080/api/v1
```

Tất cả API dưới đây dùng JSON trừ các API upload ghi rõ `multipart/form-data`.

## 1. Đăng nhập và quyền truy cập

### Đăng nhập

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "your-password"
}
```

Response `200 OK` là JWT token dạng chuỗi. Gửi token ở các request cần đăng nhập:

```http
Authorization: Bearer <token>
```

### Quyền theo vai trò

| Vai trò | Quyền quản trị |
|---|---|
| `ADMIN` | Tạo tài khoản học sinh; tất cả API dành cho staff bên dưới |
| `TEACHER` | Tạo learning bundle; import/preview/publish/archive đề; bật/tắt trạng thái đáp án |
| `STUDENT` | Không được gọi API staff; có thể gọi các API đọc đề đã publish và đọc trạng thái đáp án |

API staff chấp nhận authority dạng `ADMIN`/`TEACHER` hoặc `ROLE_ADMIN`/`ROLE_TEACHER`.

### Xem tài khoản đang đăng nhập

```http
GET /auth/profile
Authorization: Bearer <token>
```

Response `200 OK`:

```json
{
  "id": "uuid",
  "username": "admin",
  "fullName": "Admin",
  "role": "ADMIN",
  "authorities": ["ROLE_ADMIN", "ADMIN"]
}
```

## 2. Quản lý tài khoản học sinh

### Tạo học sinh

Chỉ `ADMIN` được phép.

```http
POST /auth/students
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "username": "student001",
  "password": "temporary-password",
  "fullName": "Nguyen Van A"
}
```

Response hiện tại là entity `User` với HTTP `200 OK`. **Lưu ý bảo mật:** entity có trường `passwordHash`, vì vậy response hiện có thể bao gồm hash mật khẩu. Frontend tuyệt đối không hiển thị hoặc lưu trường này; backend nên đổi response sang DTO chỉ chứa `id`, `username`, `fullName`, `role`.

Backend hiện không có API danh sách, sửa, khóa hoặc xóa tài khoản.

## 3. Quản lý video và nội dung bài học

### Tạo learning bundle

`ADMIN` hoặc `TEACHER`. Tạo video, homework và từ vựng trong cùng một request.

```http
POST /videos/learning-bundle
Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

Field multipart bắt buộc:

| Field | Kiểu | Nội dung |
|---|---|---|
| `file` | file | File JSON theo cấu trúc learning bundle |

Ví dụ dùng cURL:

```bash
curl -X POST "http://localhost:8080/api/v1/videos/learning-bundle" \
  -H "Authorization: Bearer <staff-token>" \
  -F "file=@learning-bundle.json;type=application/json"
```

Các trường chính trong JSON:

```json
{
  "video": {
    "title": "Daily Conversation",
    "videoUrl": "https://www.youtube.com/watch?v=example",
    "documentUrl": "https://drive.google.com/..."
  },
  "homework": {
    "title": "Daily Conversation Homework",
    "answers_url": "https://drive.google.com/...",
    "questions": [
      {
        "orderIndex": 1,
        "content": "I _______ in the room now.",
        "optA": "am being",
        "optB": "was being",
        "optC": "have been being",
        "optD": "am",
        "correctOption": "D",
        "explanation": "Use the present simple of be."
      }
    ]
  },
  "vocabularies": [
    { "word": "conversation", "meaning": "cuộc hội thoại" }
  ]
}
```

Với câu hỏi, có thể gửi `options: {"A":"...", "B":"...", "C":"...", "D":"..."}` thay cho `optA`–`optD`. `correctOption` cũng chấp nhận `correct_option`, `correctOpt` hoặc `correct_opt`. Tên bài homework có thể đặt ở `homework.title` hoặc `homework.exam.title`. Xem file mẫu đầy đủ: [learning-bundle-sample.json](../samples/learning-bundle-sample.json).

Response `201 Created`:

```json
{
  "videoId": "uuid",
  "homeworkId": "uuid",
  "vocabularyCount": 3
}
```

## 4. Quản lý đề thi GENERAL_EXAM và MOCK

### Import đề từ JSON

`ADMIN` hoặc `TEACHER`. Import tạo đề ở trạng thái `DRAFT`; cần preview rồi publish để học sinh truy cập.

```http
POST /exams/import?examType=GENERAL_EXAM
Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

`examType` là query parameter bắt buộc, chỉ nhận `GENERAL_EXAM` hoặc `MOCK`. Field multipart `file` chứa JSON đề. Với `MOCK`, cần có `duration_minutes` ở root hoặc trong `exam`.

Cấu trúc JSON rút gọn:

```json
{
  "exam": {
    "title": "Practice Exam 1",
    "duration_minutes": 60,
    "shuffle_parts": true,
    "video_id": "uuid"
  },
  "parts": [
    {
      "part_type": "GRAMMAR",
      "part_name": "Grammar",
      "order_index": 1,
      "questions": [
        {
          "order_index": 1,
          "content": "Choose the correct answer.",
          "options": {
            "A": "option A",
            "B": "option B",
            "C": "option C",
            "D": "option D"
          },
          "correct_option": "A",
          "explanation": "Explanation shown in staff preview."
        }
      ]
    }
  ]
}
```

`exam.shuffle_parts` là boolean tùy chọn, mặc định `false`. Khi là `true`, backend
sinh và lưu thứ tự part riêng cho từng lượt làm (không trộn câu hỏi bên trong
part). Có thể đặt `fixed_position: true` trên part để giữ nguyên vị trí, hoặc
dùng `shuffle_group` để chỉ trộn các part cùng nhóm với nhau.

Các loại `part_type` hợp lệ: `GRAMMAR`, `CLOZE_TEST`, `ARRANGEMENT`, `READING_8`, `READING_10`, `FILLIN_CLOZE`. Đề phải có cấu trúc và thứ tự câu/part hợp lệ; API trả lỗi kèm đường dẫn trường JSON không hợp lệ.

Database cần có migration `V3__Add_submission_part_shuffle.sql` để lưu cấu hình
trộn và thứ tự part của lượt làm. Không chạy migration trên môi trường đang dùng
trong quá trình sửa mã; xác nhận migration đã áp dụng trước khi deploy.

Response `201 Created`:

```json
{
  "id": "uuid",
  "title": "Practice Exam 1",
  "status": "DRAFT",
  "totalParts": 1,
  "totalQuestions": 1,
  "warnings": []
}
```

Header `Location` trỏ đến `/api/v1/exams/{id}/preview`.

**Lưu ý về `answers_url`:** DTO import có khai báo trường này nhưng logic lưu đề hiện đang gán `answersUrl` bằng `null`. Vì vậy gửi `answers_url` trong file import hiện không lưu URL. Đối với homework tạo qua learning bundle, `answers_url` được hỗ trợ.

### Preview đề (bao gồm đáp án và lời giải)

Chỉ `ADMIN` hoặc `TEACHER`. Dùng để giáo viên kiểm tra trước khi publish.

```http
GET /exams/{examId}/preview
Authorization: Bearer <staff-token>
```

Response gồm thông tin đề và `parts[].questions[]`, trong đó mỗi câu có `correctOpt`, `explanation`, và `relatedVideoId`. API này trả cả đáp án; không dùng ở màn hình học sinh.

### Đọc đề không có đáp án

```http
GET /exams/{examId}
Authorization: Bearer <token>
```

- Học sinh chỉ lấy được đề `PUBLISHED`; đề khác trạng thái trả `404`.
- Staff có thể lấy đề ở mọi trạng thái, nhưng response này không chứa đáp án.
- Muốn xem đáp án/lời giải, staff dùng endpoint preview.

### Danh sách đề

```http
GET /exams
GET /exams?mode=MOCK
GET /exams?mode=GENERAL_EXAM
GET /exams?mode=HOMEWORK
Authorization: Bearer <token>
```

Học sinh chỉ nhận đề `PUBLISHED`; backend áp dụng lọc trước khi phân trang và
bỏ qua `status` do client gửi. ADMIN/TEACHER có thể truyền
`status=PUBLISHED|DRAFT|ARCHIVED`; bỏ trống `status` để lấy mọi trạng thái.
`page` mặc định 0, `size` mặc định 12 (tối đa 100), `keyword` tìm theo tiêu đề
không phân biệt hoa thường, `sort` mặc định `createdAt,desc`.

Request có `page` hoặc `size` trả object phân trang (`items`, `page`, `size`,
`totalElements`, `totalPages`). Với học sinh, mỗi item có thêm `attempted` và
`latestScore` theo lượt nộp gần nhất; với staff hai field này là `null`. Khi
không truyền cả `page` và `size`, endpoint giữ response mảng cũ để tương thích.

### Publish đề

Chỉ `ADMIN` hoặc `TEACHER`. Đề `ARCHIVED` không thể publish lại.

```http
PATCH /exams/{examId}/publish
Authorization: Bearer <staff-token>
```

Response `204 No Content`. Sau khi publish, đề xuất hiện trong danh sách và học sinh có thể bắt đầu làm.

### Archive đề

Chỉ `ADMIN` hoặc `TEACHER`.

```http
PATCH /exams/{examId}/archive
Authorization: Bearer <staff-token>
```

Response `204 No Content`. Archive không xóa dữ liệu; đề không còn được liệt kê là đề đã publish và học sinh không thể truy cập như đề đang mở.

### Trạng thái hiển thị đáp án

Lấy trạng thái (mọi user đã đăng nhập):

```http
GET /exams/{examId}/answers-revealed
Authorization: Bearer <token>
```

Response `200 OK`:

```json
{
  "id": "uuid",
  "is_answers_revealed": false
}
```

Đảo trạng thái (chỉ `ADMIN` hoặc `TEACHER`):

```http
PATCH /exams/{examId}/answers-revealed
Authorization: Bearer <staff-token>
```

Response có cùng cấu trúc và trả trạng thái mới. Đây là API **toggle**, không nhận giá trị `true/false` trong body. Hiện cờ này chưa tự động chặn endpoint answer URL của bài nộp; không xem nó như cơ chế phân quyền đáp án hoàn chỉnh.

## 5. Mã lỗi thường gặp

| HTTP | Ý nghĩa |
|---|---|
| `400 Bad Request` | Thiếu/sai dữ liệu đầu vào, JSON import sai cấu trúc hoặc vi phạm quy tắc đề |
| `401 Unauthorized` | Thiếu, sai hoặc hết hạn JWT |
| `403 Forbidden` | Đã đăng nhập nhưng vai trò không có quyền gọi API |
| `404 Not Found` | Không tìm thấy video/đề hoặc học sinh truy cập đề chưa publish |
| `409 Conflict` | Một số xung đột trạng thái, ví dụ publish đề đã archive |
| `500 Internal Server Error` | Lỗi chưa được ánh xạ thành lỗi nghiệp vụ; kiểm tra log backend |

## 6. Những điều frontend cần lưu ý

- Đính kèm `Authorization: Bearer <token>` ở mọi request ngoại trừ login.
- Ẩn/hiện nút theo role để cải thiện UX, nhưng backend vẫn là nơi quyết định quyền cuối cùng.
- Quy trình đề thi: **Import → Preview → Publish**. Có thể archive đề đã publish.
- `PATCH /answers-revealed` là toggle; nên đọc trạng thái hiện tại trước khi hiển thị nút thao tác để tránh toggle nhầm.
- Cache nội dung backend được làm mới sau khi tạo learning bundle, publish, archive và toggle đáp án.
- Không có API quản trị user để list/update/delete; hiện chỉ có tạo học sinh.
