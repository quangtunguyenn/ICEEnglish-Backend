# Trộn thứ tự part theo lượt làm bài

## Import cấu hình

Trong JSON import, cấu hình trộn nằm trong `exam`:

```json
{
  "exam": {
    "title": "Mock exam",
    "exam_type": "MOCK",
    "duration_minutes": 50,
    "shuffle_parts": true
  }
}
```

`shuffle_parts` là boolean, mặc định `false`. Có thể cấu hình từng part bằng
`fixed_position` (giữ part tại vị trí gốc) và `shuffle_group` (chỉ xáo trộn
trong cùng nhóm). Nếu không đặt nhóm, các part không cố định thuộc cùng nhóm
mặc định. Không trộn câu hỏi bên trong part.

## Bắt đầu và tiếp tục lượt

Gọi:

```http
POST /api/v1/submissions/start/{examId}
Authorization: Bearer <access-token>
```

Response chứa `partOrder` (danh sách `partId` theo thứ tự hiển thị) và `parts`
đã sắp theo thứ tự đó. Mỗi part có `orderIndex` gốc và `displayIndex` bắt đầu
từ 1. Khi tiếp tục lượt đang làm dở, backend trả lại thứ tự đã lưu.

Lấy nội dung đề gắn với đúng lượt:

```http
GET /api/v1/exams/{examId}?submissionId={submissionId}
Authorization: Bearer <access-token>
```

Frontend render part theo đúng thứ tự response; dùng `displayIndex` cho vị trí
hiển thị và `displayNumber` cho số câu trên màn hình. Giữ câu trả lời theo
`questionId` và thời gian theo `partId`, không theo chỉ số mảng.

Khi `submissionId` không được truyền, endpoint đề trả thứ tự gốc. Backend kiểm tra
submission thuộc người dùng hiện tại và đúng đề thi. Không tự xáo trộn dữ liệu
ở frontend.

## Triển khai

Migration `V3__Add_submission_part_shuffle.sql` bổ sung cấu hình shuffle và
thứ tự part đã chọn cho mỗi submission. Phải áp dụng migration trước khi deploy
code sử dụng các cột này; không chạy migration thủ công ở môi trường production
nếu chưa theo quy trình triển khai database của dự án.