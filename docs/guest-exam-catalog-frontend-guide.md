# Frontend guide: danh sách đề guest

Áp dụng cho trang **Luyện đề** (`/luyen-de`) và **Thi thử** (`/thi-thu`).

## Lấy danh sách

Không cần access token:

```http
GET /api/v1/guest/exams?mode=GENERAL_EXAM
GET /api/v1/guest/exams?mode=MOCK
```

Response là mảng các đề đã xuất bản của mode tương ứng, sắp xếp mới nhất trước:

```json
[
  {
    "id": "1ecd0981-9a4e-41f8-a653-be1f3c8a930e",
    "title": "Đề thi General Exam Tiếng Anh - Đề số 01 (40 câu)",
    "mode": "GENERAL_EXAM",
    "durationMinutes": null,
    "totalQuestions": 40,
    "accessible": true
  },
  {
    "id": "7c1f0000-0000-0000-0000-000000000000",
    "title": "Đề thi General Exam Tiếng Anh - Đề số 02 (40 câu)",
    "mode": "GENERAL_EXAM",
    "durationMinutes": null,
    "totalQuestions": 40,
    "accessible": false
  }
]
```

Mỗi item chỉ có `id`, `title`, `mode`, `durationMinutes`, `totalQuestions`, `accessible`. API không trả câu hỏi, đáp án hoặc file đề. Backend hiện trả toàn bộ danh sách; frontend tiếp tục tìm kiếm và phân trang 12 đề/trang ở client.

## Hiển thị và xử lý click

- `accessible: true`: cho phép khách vào luồng làm đề như hiện tại.
- `accessible: false`: vẫn hiển thị đề nhưng khóa hành động làm bài.
  - **Luyện đề:** nút “Đăng ký để làm đề”.
  - **Thi thử:** nút “Đăng ký để vào thi”.
  - Khi bấm, mở popup mời người dùng đăng ký tư vấn.
- `accessible` là boolean và luôn có trong response. Trong thời gian triển khai tương thích ngược, nếu gặp response cũ không có trường này thì có thể xem như `true`.

## Quyền truy cập được kiểm tra ở backend

Không chỉ ẩn hoặc khóa nút trên giao diện. Backend từ chối các thao tác guest trên đề không accessible với HTTP `403 Forbidden`, `message`:

```text
Đề này dành cho học viên ICE English.
```

Các request được kiểm tra gồm bắt đầu đề, lấy nội dung đề, cập nhật thời gian part, lấy part logs, nộp bài và lấy answer URL. Nếu nhận `403` khi đang xử lý đề, dừng luồng làm bài và hiển thị lời mời đăng ký tư vấn.

## Bật/tắt quyền trải nghiệm

Admin bật hoặc tắt quyền qua:

```http
PUT /api/v1/admin/exams/{examId}/guest-access
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{ "guestAccessible": true }
```

API trả `204 No Content`. Giá trị mới được phản ánh trong danh sách guest sau khi cập nhật.
