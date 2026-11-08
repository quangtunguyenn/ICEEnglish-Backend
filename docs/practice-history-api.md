# API lịch sử làm bài luyện đề

Danh sách đề phục vụ `GENERAL_EXAM` và `MOCK`; lịch sử luyện đề bên dưới áp dụng
cho `GENERAL_EXAM`. Responses được trả trực tiếp dưới dạng JSON, theo quy ước
hiện tại của backend (không có wrapper `data`).

## Danh sách đề và phân trang

```http
GET /api/v1/exams?mode=GENERAL_EXAM
Authorization: Bearer <accessToken>
```

Khi gọi với tài khoản học sinh, backend luôn lọc `status=PUBLISHED` trước khi
trả kết quả; tham số `status` từ client bị bỏ qua. Có thể phân trang và tìm kiếm:

```http
GET /api/v1/exams?mode=GENERAL_EXAM&page=0&size=12&keyword=&sort=createdAt,desc
GET /api/v1/exams?mode=MOCK&page=0&size=12&keyword=&sort=createdAt,desc
```

Response phân trang có `items`, `page`, `size`, `totalElements` và `totalPages`.
Mỗi item gồm `id`, `title`, `mode`, `status`, `durationMinutes`,
`totalQuestions`, `createdAt` (offset `+07:00`), `attempted` và `latestScore`.
`attempted` cho biết học sinh đã có lượt làm (kể cả lượt đang làm dở) cho
`GENERAL_EXAM`, `MOCK` và `HOMEWORK`;
`latestScore` chỉ lấy từ lượt đã nộp gần nhất và sẽ là `null` nếu chưa có lượt
nộp. Với ADMIN/TEACHER hai field này là `null`. Điểm dùng cùng cách tính với API
điểm hiện tại (`totalCorrect * 0.25`).

`page` mặc định 0, `size` mặc định 12 và phải nằm trong 1..100. `keyword` tìm
không phân biệt hoa thường trong tiêu đề. `sort` hỗ trợ `createdAt` hoặc `title`
với hướng `asc`/`desc`; mặc định là `createdAt,desc`. Nếu không truyền cả
`page` lẫn `size`, backend giữ response mảng cũ để tương thích.

```json
{
  "items": [
    {
      "id": "exam-uuid",
      "title": "Đề luyện tập số 1",
      "mode": "GENERAL_EXAM",
      "status": "PUBLISHED",
      "durationMinutes": null,
      "totalQuestions": 40,
      "createdAt": "2026-11-06T14:30:00+07:00",
      "attempted": true,
      "latestScore": 8.5
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1
}
```

ADMIN/TEACHER có thể lọc bằng `status=PUBLISHED|DRAFT|ARCHIVED`; không truyền
`status` sẽ bao gồm mọi trạng thái. Các role không phải staff chỉ nhận đề
`PUBLISHED`.

Khi chưa truyền tham số phân trang, mỗi đề `GENERAL_EXAM` vẫn có `hasSubmitted`
để tương thích API cũ; request phân trang dùng `attempted` và `latestScore`.
Không dùng lỗi tải API để suy ra rằng học sinh chưa làm bài.

### Điểm thi thử đã nộp

```http
GET /api/v1/submissions/mock/scores?page=0&size=20&sort=submittedAt,desc
Authorization: ******
```

Response phân trang dùng cùng metadata (`items`, `page`, `size`,
`totalElements`, `totalPages`); mỗi item có `submissionId`, `examId`,
`examTitle`, `score`, `totalCorrect`, `totalQuestions`, `startedAt` và
`submittedAt`. `correctCount` là alias mới của `totalCorrect` để đồng bộ hợp đồng
màn hình thi thử. Timestamp có offset `+07:00`. Page mặc định 0, size mặc định 20,
giới hạn size là 100. Nếu không truyền `page` lẫn `size`, endpoint giữ mảng
response cũ.

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
      "submittedAt": "2026-11-06T14:30:00+07:00",
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

Khi học sinh chưa có lượt nộp, `items` rỗng. Timestamp được tạo và diễn giải
theo đồng hồ server, với múi giờ `Asia/Ho_Chi_Minh` (`+07:00`). Entity hiện lưu
`LocalDateTime` không kèm múi giờ; ứng dụng cấu hình timezone server/JVM là giờ
Việt Nam và dùng giờ này thống nhất khi ghi/đọc. Page âm, `size` nhỏ hơn 1 hoặc
lớn hơn 100 trả lỗi `400`.

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

`startedAt` và `submittedAt` trong response review có offset `+07:00`, giống API
lịch sử. Các timestamp không có offset trước đây cần được hiểu theo timezone
server đã dùng khi ghi dữ liệu; không tự diễn giải theo timezone của trình duyệt.

Chi tiết gồm tất cả câu của đề, kể cả câu chưa trả lời (`selectedOpt: null`).
`correctOpt` và `explanation` tuân theo `answersRevealed` của đề như API
`latest-review` hiện có.
