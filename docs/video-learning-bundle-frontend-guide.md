# Frontend guide: video, learning bundle và guest access

Base URL: `/api/v1`.

## 1. Lấy ID và tên video

Backend đã có API lấy toàn bộ ID và title; không cần thêm endpoint trùng.

### Dành cho tài khoản đã đăng nhập

```http
GET /api/v1/videos/titles
Authorization: Bearer <token>
```

```json
[
  { "video_id": "uuid-1", "title": "CHUYÊN ĐỀ 1", "position": "1" },
  { "video_id": "uuid-2", "title": "MẸO LÀM BÀI", "position": null }
]
```

### Dành cho khách

```http
GET /api/v1/guest/videos
```

```json
[
  { "videoId": "uuid-1", "title": "CHUYÊN ĐỀ 1", "position": "1", "accessible": true },
  { "videoId": "uuid-2", "title": "MẸO LÀM BÀI", "position": null, "accessible": false }
]
```

API guest không cần token và trả ID/title của tất cả video. Dùng `accessible` để xác định khách có được mở nội dung hay không. Danh sách không trả URL video/tài liệu.

Các danh sách video được sắp xếp theo `position` dạng số (`1`, `1.1`, `1.10`, `2`), không theo thứ tự chữ. Video không có vị trí nằm cuối, theo `createdAt` tăng dần. `GET /api/v1/videos?title=...` cũng trả `position` và dùng cùng thứ tự.

## 2. Nạp learning bundle

```http
POST /api/v1/videos/learning-bundle
Authorization: Bearer <admin-or-teacher-token>
Content-Type: multipart/form-data
```

Gửi file JSON trong field `file`. Trường `video.type` quyết định yêu cầu đối với homework và từ vựng:

| `video.type` | Ý nghĩa | Homework | Từ vựng |
|---|---|---|---|
| `lecture` | Bài giảng | Bắt buộc, tối thiểu 1 câu | Bắt buộc, tối thiểu 1 mục |
| `practice` | Giải bài tập cụ thể | Tùy chọn | Tùy chọn |
| `exam_tips` | Mẹo làm bài | Tùy chọn | Tùy chọn |

`video.title` và `video.videoUrl` luôn bắt buộc. `video.type` không phân biệt chữ hoa/thường. Nếu bỏ trường `type`, backend mặc định là `lecture` để các bundle cũ tiếp tục tuân theo validation trước đây. `video.position` là tùy chọn; nếu bỏ qua, giá trị mặc định là `null`.

Ví dụ bundle chỉ có video:

```json
{
  "video": {
    "type": "exam_tips",
    "title": "Mẹo làm bài đọc hiểu",
    "videoUrl": "https://www.youtube.com/watch?v=..."
  }
}
```

Bundle loại `practice` hoặc `exam_tips` có thể gửi riêng homework hoặc từ vựng nếu có. Phần nào được gửi vẫn phải đúng cấu trúc và validation hiện hành. Khi không có homework, response `homeworkId` là `null`; khi không có từ vựng, `vocabularyCount` là `0`.

`video.position` nhận dạng `1` hoặc `1.1` (một hoặc hai phần số nguyên, ví dụ `2.10`). Giá trị phải duy nhất; gửi `null` hoặc chuỗi rỗng để bỏ vị trí khi cập nhật bằng API quản trị. Các trường tùy chọn khác trong `video` gồm `documentUrl` và `answers_url`. Cấu trúc câu hỏi, đáp án và từ vựng xem [video-api-frontend-guide.md](./video-api-frontend-guide.md).

## 3. Sắp xếp video (admin/teacher)

```http
PUT /api/v1/admin/videos/{videoId}/position
Authorization: Bearer <admin-or-teacher-token>
Content-Type: application/json
```

```json
{ "position": "2.3" }
```

Response thành công là `204 No Content`. Gửi `{"position": null}` hoặc `{"position": ""}` để xóa vị trí. Backend trả `400` nếu sai định dạng, `404` nếu không tìm thấy video và `409` nếu vị trí đã được dùng (message có tên video đang giữ nếu có). Video có cùng `position` bị từ chối, nhưng nhiều video có `position: null` được phép.

Admin và teacher đều được phép đổi vị trí; quyền bật/tắt guest access vẫn chỉ dành cho admin.

## 4. Mở nội dung video

```http
GET /api/v1/videos/{videoId}/learning-content
Authorization: Bearer <token>
```

Response có `homework: null` khi bundle không có homework và `vocabularies: []` khi bundle không có từ vựng. API lấy homework riêng trả `404` nếu video không có homework. Các API học sinh hiện có không thay đổi.

## 5. Quyền truy cập của khách

Admin bật/tắt quyền cho từng video:

```http
PUT /api/v1/admin/videos/{videoId}/guest-access
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{ "guestAccessible": true }
```

Gửi `false` để khóa video. Response là `204 No Content`; video mới mặc định bị khóa với khách. Thay đổi này không yêu cầu sửa `application.properties` hoặc nạp lại bundle.

Khách chỉ mở được video có `accessible: true` trong `GET /api/v1/guest/videos`. Các request guest lấy learning content, từ vựng hoặc homework của video bị khóa trả `403 Forbidden`; ID không tồn tại trả `404`.

`video.type` được lưu trong database, nhưng chưa được thêm vào response của các API video hiện có để giữ nguyên cấu trúc response. Migration `V14__Add_video_type.sql` đặt các video cũ thành `LECTURE`.

Áp dụng migration `V15__Add_video_position.sql` trước khi deploy phiên bản có sắp xếp video. Video cũ giữ `position: null` và vì vậy vẫn xuất hiện cuối danh sách theo thời gian tạo.
