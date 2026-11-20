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

Tên đăng nhập không tồn tại hoặc mật khẩu sai trả `401` với cùng thông báo.

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
  "studentIrn": null,
  "passwordMock": null,
  "dob": null,
  "gender": null,
  "isActive": true,
  "role": "ADMIN",
  "authorities": ["ROLE_ADMIN", "ADMIN"]
}
```

Profile học sinh trả thêm `studentIrn`, `passwordMock`, `dob`, `gender`. Các trường này là `null` với tài khoản chưa có dữ liệu.

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
  "fullName": "Nguyen Van A",
  "studentIrn": "IRN-001",
  "passwordMock": "practice-123",
  "dob": "06/05/2010",
  "gender": "FEMALE"
}
```

`studentIrn`, `passwordMock`, `dob`, `gender` là tùy chọn, nên request cũ vẫn hợp lệ. `dob` dùng định dạng `dd/MM/yyyy`. `passwordMock` do admin tạo để học sinh tập làm quen thao tác; đây chỉ là dữ liệu hiển thị/thực hành, không dùng để đăng nhập hay xác thực. `gender` là chuỗi và backend hiện không giới hạn danh sách giá trị.

Response hiện tại là entity `User` với HTTP `200 OK`; các trường profile được trả trong response, nhưng `passwordHash` không được trả.

Sau khi deploy cần áp dụng `src/main/resources/db/migration/V8__Add_student_profile_fields.sql` và `src/main/resources/db/migration/V9__Rename_student_practice_and_dob_fields.sql` nếu môi trường không tự cập nhật schema bằng Hibernate. V9 giữ lại dữ liệu đã lưu ở các cột `sbd_mock`/`bod` trước đó.

### Danh sách, tìm kiếm và trạng thái

Chỉ `ADMIN` được phép. Học sinh mới mặc định `isActive: true`; migration V11 đặt `true` cho tài khoản cũ chưa có trạng thái.

```http
GET /admin/students
GET /admin/students?isActive=false
GET /admin/students?keyword=dang%20thi
GET /admin/students?isActive=true&keyword=dang%20thi
Authorization: Bearer <admin-token>
```

`GET /admin/students` trả mảng JSON (không phân trang), chỉ gồm học sinh; không có kết quả trả `[]`. Hai tham số đều tùy chọn, có thể kết hợp. `isActive` nhận `true` hoặc `false`; `keyword` tìm một phần họ tên, không phân biệt hoa/thường hoặc dấu tiếng Việt, ví dụ `dang thi` tìm được `Đặng Thị Ánh`. Kết quả mới nhất trước theo `createdAt`, sau đó `id` giảm dần.

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "username": "student001",
    "fullName": "Đặng Thị Ánh",
    "studentIrn": "IRN-001",
    "passwordMock": "practice-123",
    "dob": "06/05/2010",
    "gender": "FEMALE",
    "isActive": true,
    "createdAt": "2026-01-02T10:00:00"
  }
]
```

Danh sách và response cập nhật không chứa `passwordHash`. `createdAt` là thời gian địa phương Việt Nam, có thể `null` nếu dữ liệu cũ không có thời điểm tạo.

```http
PATCH /admin/students/{id}/is-active
Authorization: Bearer <admin-token>
Content-Type: application/json
```

Gửi `{"isActive": false}` để vô hiệu hóa (xóa mềm), `{"isActive": true}` để kích hoạt lại. Response `200 OK` là object học sinh sau cập nhật, cùng cấu trúc với phần tử danh sách. Không xóa dữ liệu liên quan. `400` nếu thiếu/sai trạng thái hoặc tham số; `404` nếu ID không phải học sinh; `401` nếu chưa xác thực, `403` nếu không phải ADMIN.

Tài khoản không hoạt động không đăng nhập được (`401`) và JWT cũ bị từ chối ngay ở các request được xác thực sau khi thay đổi được commit. Khi kích hoạt lại, JWT cũ chưa hết hạn vẫn có thể sử dụng. Các request đã xác thực trước thời điểm vô hiệu hóa có thể hoàn tất. Mỗi request có JWT cần một truy vấn DB để kiểm tra trạng thái; role vẫn được cache theo cấu hình hiện tại.

**Triển khai:** áp dụng `src/main/resources/db/migration/V11__Add_user_active_status.sql` trước khi deploy backend mới. Migration giữ nguyên trạng thái đã có và đặt `true` cho giá trị chưa có.

## 3. Quản lý video và nội dung bài học

### Tạo learning bundle

`ADMIN` hoặc `TEACHER`. Tạo video và, tùy loại nội dung, homework/từ vựng trong cùng một request.

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
    "type": "lecture",
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

`video.type` nhận `lecture`, `practice` hoặc `exam_tips`; nếu bỏ qua thì mặc định `lecture` để bundle cũ tiếp tục hoạt động. `lecture` yêu cầu homework có ít nhất một câu hỏi và ít nhất một mục từ vựng. `practice`/`exam_tips` có thể không gửi homework và từ vựng; khi homework không có, response `homeworkId` là `null`, khi từ vựng không có, `vocabularyCount` là `0`.
`video.position` là tùy chọn trong bundle (mặc định `null`), nhận dạng số như `1`, `1.1`, `2.10` và phải duy nhất nếu có giá trị.

Với câu hỏi, có thể gửi `options: {"A":"...", "B":"...", "C":"...", "D":"..."}` thay cho `optA`–`optD`. `correctOption` cũng chấp nhận `correct_option`, `correctOpt` hoặc `correct_opt`. Tên bài homework có thể đặt ở `homework.title` hoặc `homework.exam.title`. Xem file mẫu đầy đủ: [learning-bundle-sample.json](../samples/learning-bundle-sample.json).

Response `201 Created`:

```json
{
  "videoId": "uuid",
  "homeworkId": "uuid",
  "vocabularyCount": 3
}
```

### Cập nhật thứ tự video

Admin và teacher có thể gán hoặc xóa vị trí cho video. Gửi `null` hoặc chuỗi rỗng để xóa; vị trí được so sánh theo từng phần số nguyên. Mọi danh sách video trả `position`, xếp video có vị trí trước theo thứ tự số, video không có vị trí sau theo `createdAt` tăng dần.

```http
PUT /admin/videos/{videoId}/position
Authorization: Bearer <admin-or-teacher-token>
Content-Type: application/json
```

```json
{ "position": "2.3" }
```

Response `204 No Content`; `400` nếu sai định dạng, `404` nếu không có video, `409` nếu trùng vị trí. Áp dụng migration `src/main/resources/db/migration/V15__Add_video_position.sql` trước khi deploy.

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
      "part_type": "ARRANGEMENT",
      "part_name": "Arrangement",
      "recommended_time_seconds": 300,
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

`recommended_time_seconds` là trường tùy chọn. Nếu gửi, phải là số nguyên dương
khớp thời gian chuẩn của `part_type`; nếu bỏ qua, thời gian gợi ý được lưu và
trả là `null`. Khi học sinh bắt đầu lượt `GENERAL_EXAM` hoặc `MOCK`, backend
lưu giá trị này cho từng part của lượt để kết quả lịch sử không đổi nếu đề được
chỉnh sửa sau đó.

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

Response gồm thông tin đề và `parts[].questions[]`, trong đó mỗi câu có `correctOpt`, `explanation`, `relatedVideoIds` và `relatedVideoId` (video đầu tiên để tương thích). API này trả cả đáp án; không dùng ở màn hình học sinh.

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

### Xóa tất cả đề nháp

Chỉ `ADMIN` hoặc `TEACHER`. Xóa cứng các đề đang ở trạng thái `DRAFT` cùng dữ liệu
phần/câu hỏi liên quan; các đề `PUBLISHED` và `ARCHIVED` không bị ảnh hưởng.

```http
DELETE /exams/drafts
Authorization: Bearer <staff-token>
```

Response `200 OK`:

```json
{ "deletedCount": 3 }
```

`deletedCount` bằng 0 nếu không có đề nháp.

### Bật/tắt quyền trải nghiệm cho khách

Chỉ `ADMIN` được phép. Giá trị mặc định của đề mới là `false`.

```http
PUT /admin/exams/{examId}/guest-access
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{ "guestAccessible": true }
```

Response `204 No Content`. Danh sách guest vẫn hiển thị tất cả đề `PUBLISHED`; trường `accessible` phản ánh cờ này. Khách chỉ có thể bắt đầu và thao tác với đề khi cờ là `true`.

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
