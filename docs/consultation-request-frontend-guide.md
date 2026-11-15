# API yêu cầu tư vấn

Base URL: `/api/v1`. Dữ liệu là JSON. Bảng yêu cầu tư vấn độc lập, không liên kết tài khoản hay bảng khác.

## Gửi yêu cầu

`POST /consultation-requests` — công khai, không cần token.

```json
{
  "studentFullname": "Nguyen Van A",
  "bod": 2008,
  "targetGrade": 8.5,
  "phoneNumberRequest": "0901234567"
}
```

- `studentFullname`: bắt buộc, không chỉ có khoảng trắng, tối đa 150 ký tự sau khi bỏ khoảng trắng đầu/cuối.
- `bod`: năm sinh dạng số nguyên, từ 1 đến năm hiện tại theo giờ Việt Nam; không phải ngày sinh đầy đủ.
- `targetGrade`: số từ 0 đến 10, bao gồm cả hai đầu; có thể là số thập phân.
- `phoneNumberRequest`: chuỗi bắt buộc, tối đa 30 ký tự sau khi bỏ khoảng trắng đầu/cuối; dùng chuỗi để giữ số 0 ở đầu.
- Server tự gán `dateRequest` và `status`; không gửi các trường này.

Response `201 Created`:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "studentFullname": "Nguyen Van A",
  "bod": 2008,
  "targetGrade": 8.5,
  "phoneNumberRequest": "0901234567",
  "dateRequest": "2026-01-02T00:30:00",
  "status": "NOT_DONE"
}
```

`dateRequest` là thời gian địa phương `Asia/Ho_Chi_Minh` (UTC+07:00), không kèm offset. `NOT_DONE` là chưa xử lý; `DONE` là đã xử lý.

## Danh sách và lọc

`GET /consultation-requests` — cần `Authorization: Bearer <token>` của ADMIN hoặc TEACHER.

Response `200 OK` là mảng các object cùng cấu trúc như response tạo; mảng rỗng nếu không có kết quả. Kết quả được sắp xếp theo `dateRequest` giảm dần, rồi `id` giảm dần. Không phân trang.

- Toàn bộ: `GET /consultation-requests`
- Theo trạng thái: `GET /consultation-requests?status=NOT_DONE` hoặc `?status=DONE`
- Theo ngày gửi: `GET /consultation-requests?date=2026-01-02`
- Kết hợp: `GET /consultation-requests?status=DONE&date=2026-01-02`

Ngày dùng định dạng `yyyy-MM-dd`; lọc toàn bộ ngày theo giờ Việt Nam, từ 00:00 đến trước 00:00 ngày kế tiếp.

## Cập nhật trạng thái

`PATCH /consultation-requests/{id}/status` — cần token ADMIN hoặc TEACHER.

```json
{ "status": "DONE" }
```

Có thể đổi lại thành `NOT_DONE`. Response `200 OK` là object yêu cầu sau cập nhật.

## Lỗi và triển khai

- `400 Bad Request`: thiếu hoặc sai dữ liệu, trạng thái hoặc định dạng ngày không hợp lệ.
- `401 Unauthorized`: thiếu token hợp lệ khi đọc hoặc cập nhật.
- `403 Forbidden`: đã đăng nhập nhưng không phải ADMIN/TEACHER.
- `404 Not Found`: không tìm thấy yêu cầu khi cập nhật.

Áp dụng migration `src/main/resources/db/migration/V10__Create_consultation_requests.sql` theo quy trình triển khai database của dự án; Hibernate `ddl-auto=update` cũng có thể tạo bảng nhưng không thay thế đầy đủ các ràng buộc và index của migration.
