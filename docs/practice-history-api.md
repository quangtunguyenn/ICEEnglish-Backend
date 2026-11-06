# API lịch sử làm bài luyện đề

Các API này dành cho học sinh và chỉ áp dụng cho đề `GENERAL_EXAM`. Responses
được trả trực tiếp dưới dạng JSON, theo quy ước hiện tại của backend (không có
wrapper `data`).

## Danh sách đề

```http
GET /api/v1/exams?mode=GENERAL_EXAM
Authorization: Bearer <accessToken>
```

Mỗi đề có thêm trường `hasSubmitted`, tính cho tài khoản đang đăng nhập. Trường
này chỉ tính lượt `GENERAL_EXAM` đã nộp; không dùng lỗi tải API để suy ra rằng
học sinh chưa làm bài.

## Lịch sử các lượt đã nộp

```http
GET /api/v1/submissions/practice/{examId}/history?page=0&size=20
Authorization: Bearer <accessToken>
```

`page` bắt đầu từ 0, `size` mặc định là 20 và giới hạn tối đa là 100. Kết quả
được sắp xếp theo `submittedAt` mới nhất trước, sau đó theo `submissionId` để
thứ tự ổn định khi thời điểm nộp trùng nhau.

```json
{
  "examId": "exam-uuid",
  "examTitle": "Đề luyện tập số 1",
  "items": [
    {
      "submissionId": "submission-uuid",
      "submittedAt": "2026-11-06T07:30:00Z",
      "score": 8.5,
      "totalQuestions": 40,
      "totalCorrect": 34
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Khi học sinh chưa có lượt nộp, `items` rỗng. `submittedAt` được trả dưới dạng
UTC. Vì entity hiện lưu `LocalDateTime` không kèm múi giờ, backend chuyển đổi
dựa trên timezone mặc định của JVM; timezone đó cần giữ nhất quán với timezone
đã dùng khi ghi dữ liệu. Page âm, `size` nhỏ hơn 1 hoặc lớn hơn 100 trả lỗi `400`.

## Review một lượt cụ thể

```http
GET /api/v1/submissions/practice/{submissionId}/review
Authorization: Bearer <accessToken>
```

Response dùng cùng cấu trúc với
`GET /api/v1/submissions/practice/{examId}/latest-review`, nhưng xác định đúng
lượt từ `submissionId`. Chỉ chủ sở hữu mới xem được lượt `GENERAL_EXAM` đã nộp;
lượt không tồn tại, thuộc người khác, chưa nộp hoặc không phải luyện đề trả
`404`.

`submittedAt` trong response review có offset UTC (`Z`), giống API lịch sử.
Do cột hiện lưu `LocalDateTime`, phép chuyển đổi dùng timezone mặc định của JVM
như mô tả ở trên.

Chi tiết gồm tất cả câu của đề, kể cả câu chưa trả lời (`selectedOpt: null`).
`correctOpt` và `explanation` tuân theo `answersRevealed` của đề như API
`latest-review` hiện có.
