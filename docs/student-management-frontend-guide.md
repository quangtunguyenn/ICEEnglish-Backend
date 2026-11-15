# Hướng dẫn frontend: quản lý học sinh

Base URL mặc định: `http://localhost:8080/api/v1`.

Các API quản lý học sinh bên dưới chỉ dành cho **ADMIN**. Gửi token ở mỗi request:

```http
Authorization: Bearer <admin-token>
```

## 1. Trạng thái tài khoản

| `isActive` | Ý nghĩa |
|---|---|
| `true` | Học sinh đang hoạt động, có thể đăng nhập nếu thông tin xác thực hợp lệ. |
| `false` | Học sinh bị vô hiệu hóa, không thể đăng nhập hoặc sử dụng API cần xác thực. |

Tài khoản học sinh mới mặc định có `isActive: true`. Vô hiệu hóa là **xóa mềm**, không xóa tài khoản hoặc dữ liệu liên quan.

## 2. Lấy danh sách, lọc và tìm kiếm

```http
GET /admin/students
Authorization: Bearer <admin-token>
```

Các tham số tùy chọn:

| Tham số | Ví dụ | Mô tả |
|---|---|---|
| `isActive` | `true` hoặc `false` | Lọc theo trạng thái. Bỏ qua để lấy cả hai trạng thái. |
| `keyword` | `dang thi` | Tìm một phần họ tên; không phân biệt hoa/thường và dấu tiếng Việt. |

Ví dụ:

```http
GET /admin/students?isActive=false
GET /admin/students?keyword=dang%20thi
GET /admin/students?isActive=true&keyword=dang%20thi
```

Hai bộ lọc có thể kết hợp. Ví dụ `dang thi`, `Đặng Thị` đều tìm được `Đặng Thị Ánh`. Frontend cần mã hóa tham số URL.

Response `200 OK` là mảng học sinh, không phân trang:

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

- Không có kết quả: `[]`.
- Sắp xếp theo `createdAt` giảm dần, sau đó `id` giảm dần.
- `fullName`, `studentIrn`, `passwordMock`, `dob`, `gender`, `createdAt` có thể là `null` nếu chưa có dữ liệu. `dob` dùng định dạng `dd/MM/yyyy`; `createdAt` là thời gian địa phương Việt Nam, không kèm offset.
- `passwordMock` chỉ dùng cho thực hành, không phải mật khẩu đăng nhập. Danh sách không trả `passwordHash`.

## 3. Vô hiệu hóa hoặc kích hoạt học sinh

```http
PATCH /admin/students/{id}/is-active
Authorization: Bearer <admin-token>
Content-Type: application/json
```

Vô hiệu hóa:

```json
{ "isActive": false }
```

Kích hoạt lại:

```json
{ "isActive": true }
```

Response `200 OK` là object học sinh sau cập nhật, cùng cấu trúc với một phần tử trong danh sách. `{id}` là UUID của học sinh, không phải username.

Sau khi cập nhật thành công, frontend có thể cập nhật lại dòng tương ứng hoặc tải lại danh sách theo bộ lọc hiện tại.

## 4. Ảnh hưởng đến đăng nhập và JWT

- Tài khoản không hoạt động không đăng nhập được; sau khi kiểm tra đúng mật khẩu, backend trả `401 Unauthorized`.
- JWT của học sinh bị vô hiệu hóa sẽ bị từ chối ở các request cần xác thực sau khi thay đổi được lưu. Request đã xác thực trước đó vẫn có thể hoàn tất.
- Khi kích hoạt lại, JWT cũ **chưa hết hạn** vẫn có thể được sử dụng.
- `GET /auth/profile` trả thêm `isActive` cho tài khoản đang đăng nhập.

## 5. Các mã lỗi

| Mã | Trường hợp |
|---|---|
| `400 Bad Request` | Thiếu `isActive` trong PATCH hoặc tham số không hợp lệ. |
| `401 Unauthorized` | Thiếu token hợp lệ khi gọi API quản lý, hoặc tài khoản bị vô hiệu hóa khi đăng nhập. |
| `403 Forbidden` | Đã đăng nhập nhưng không có quyền ADMIN. |
| `404 Not Found` | ID không tồn tại hoặc không thuộc tài khoản học sinh. |

Định dạng body lỗi có thể khác nhau giữa lỗi xác thực, phân quyền và dữ liệu không hợp lệ; frontend nên xử lý theo HTTP status.

## 6. Triển khai

Backend cần áp dụng [migration V11](../src/main/resources/db/migration/V11__Add_user_active_status.sql) trước khi chạy phiên bản mới. Migration đặt `true` cho tài khoản chưa có trạng thái và giữ nguyên trạng thái đã có. Xem thêm [API quản trị](./admin-api-guide.md) để biết cách đăng nhập và tạo học sinh.
