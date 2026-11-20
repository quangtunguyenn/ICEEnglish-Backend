# Tóm tắt thay đổi thông tin hồ sơ học sinh

## Các trường mới

Bảng `users` và entity `User` có thêm:

| API field | Cột database | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|---|
| `studentIrn` | `student_irn` | chuỗi, tối đa 50 ký tự | Mã IRN học sinh |
| `passwordMock` | `password_mock` | chuỗi, tối đa 100 ký tự | Mật khẩu luyện tập do admin tạo |
| `dob` | `dob` | ngày | Ngày sinh, định dạng API `dd/MM/yyyy` |
| `gender` | `gender` | chuỗi, tối đa 20 ký tự | Giới tính |

Các trường đều tùy chọn để tương thích với request tạo học sinh cũ. Profile của tài khoản chưa có dữ liệu sẽ trả `null` cho các trường này.

`passwordMock` chỉ dùng làm dữ liệu luyện tập/hiển thị để học sinh làm quen thao tác. Không dùng trường này để đăng nhập hoặc xác thực; xác thực hiện tại vẫn dùng trường `password` của request đăng nhập.

## API thay đổi

### Tạo học sinh

`POST /api/v1/auth/students` — chỉ admin.

Ví dụ request:

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

`dob` phải gửi theo định dạng `dd/MM/yyyy`. `passwordMock` được lưu độc lập với `password`.

### Lấy profile

`GET /api/v1/auth/profile` — yêu cầu bearer token.

Response profile có thêm:

```json
{
  "studentIrn": "IRN-001",
  "passwordMock": "practice-123",
  "dob": "06/05/2010",
  "gender": "FEMALE"
}
```

Các field hiện được serialize theo camelCase như trên. Request tạo học sinh cũng nhận tên field camelCase.

## Database và migration

- [V8__Add_student_profile_fields.sql](../src/main/resources/db/migration/V8__Add_student_profile_fields.sql) thêm các cột thông tin học sinh.
- [V9__Rename_student_practice_and_dob_fields.sql](../src/main/resources/db/migration/V9__Rename_student_practice_and_dob_fields.sql) đổi tên `sbd_mock` thành `password_mock` và `bod` thành `dob`; nếu cột mới đã tồn tại, migration chép dữ liệu cũ sang các giá trị mới còn trống.
- Đã chạy V8 và V9 trên database local `ICEEnglish` tại `localhost:5432` trong transaction và xác nhận các cột `student_irn`, `password_mock`, `dob`, `gender` tồn tại. Việc này chỉ áp dụng cho database local đó; cần triển khai migration riêng trên các môi trường khác.

## Tài liệu và kiểm thử

- Hướng dẫn API frontend: [admin-api-guide.md](./admin-api-guide.md)
- Kiểm thử service: `AuthServiceTest` đạt 3/3 test, bao gồm kiểm tra dữ liệu tạo học sinh, profile cho tài khoản cũ và serialization ngày `dd/MM/yyyy`.
- `git diff --check` không phát hiện lỗi whitespace.

## Lưu ý bảo mật hiện tại

API tạo học sinh không trả `passwordHash` (hash xác thực). Không nhầm
`passwordHash` với `passwordMock`, là dữ liệu thực hành được trả theo hợp đồng.
