# Hướng dẫn API Video cho Frontend

Base URL: `/api/v1`. Mọi API đều yêu cầu header `Authorization: Bearer <token>` (chỉ `/auth/login` là công khai).
Tên trường JSON dùng camelCase, ngoại trừ các trường ghi rõ snake_case (`answers_url`, `exam_id`, `video_url`, `part_type`, `passage_text`).

## Tổng quan luồng

1. **Admin/Teacher** upload file JSON để tạo bài học (video + homework + từ vựng).
2. **Học sinh** xem danh sách video → mở bài học (`learning-content`).
3. Học sinh làm homework: `start` → `submit` (xem phần 3).
4. Sau khi nộp, xem đáp án qua `answers_url`.

---

## 1. API Video

### 1.1 Tạo bài học (Admin/Teacher)

`POST /videos/learning-bundle` — `multipart/form-data`, field `file` là file JSON.
Quyền: `ADMIN` hoặc `TEACHER`. Trả về `201 Created`.

Mẫu file: `samples/learning-bundle-sample.json`.

```json
{
  "video": {
    "title": "CHUYEN DE 1 - THI DONG TU",
    "videoUrl": "https://www.youtube.com/watch?v=...",
    "documentUrl": "https://drive.google.com/...",
    "answers_url": "https://drive.google.com/..."
  },
  "homework": {
    "title": "Homework - 21/6/2026",
    "answers_url": "https://drive.google.com/...",
    "questions": [
      {
        "orderIndex": 1,
        "content": "This sauce goes _______ well with asparagus.",
        "options": { "A": "wonder", "B": "wonderfully", "C": "wonderful", "D": "wondering" },
        "correctOption": "B",
        "explanation": "Bổ nghĩa cho động từ => dùng trạng từ."
      }
    ]
  },
  "vocabularies": [
    { "word": "asparagus", "meaning": "măng tây" }
  ]
}
```

Quy tắc:
- Bắt buộc: `video.title`, `video.videoUrl`, `homework.title` (hoặc `homework.exam.title`), ít nhất 1 câu hỏi, ít nhất 1 từ vựng.
- Câu hỏi có thể dùng `options: {A,B,C,D}` hoặc các trường `optA..optD`. Đáp án đúng: `correctOption` (hoặc `correctOpt`), chỉ nhận `A`/`B`/`C`/`D`.
- `answers_url` lấy từ `homework.answers_url`; nếu không có thì lấy `video.answers_url`.
- Homework không có part, câu hỏi nằm trực tiếp trong exam.
- Lỗi dữ liệu trả `400` với thông báo tiếng Việt (JSON sai cú pháp có kèm dòng/cột).

Response:
```json
{ "videoId": "uuid", "homeworkId": "uuid", "vocabularyCount": 14 }
```

### 1.2 Danh sách video

`GET /videos?title=...` — `title` tùy chọn, tìm theo một phần tên (không phân biệt hoa thường).

```json
[
  {
    "id": "uuid",
    "title": "CHUYEN DE 1 - THI DONG TU",
    "videoUrl": "https://www.youtube.com/watch?v=...",
    "documentUrl": "https://drive.google.com/...",
    "createdAt": "2026-11-05T20:00:00"
  }
]
```

Lưu ý: danh sách này **không** có `answers_url` (URL đáp án thuộc về homework).

### 1.3 Danh sách tên video

`GET /videos/titles` — sắp xếp theo `title`, dùng cho danh sách chọn bài (ví dụ trang luyện tập từ vựng).

```json
[
  { "video_id": "uuid", "title": "CHUYEN DE 1 - THI DONG TU" }
]
```

Dùng `video_id` để gọi `GET /videos/{videoId}/vocabularies`.

### 1.4 Nội dung bài học (một lần gọi lấy đủ)

`GET /videos/{videoId}/learning-content`

```json
{
  "videoId": "uuid",
  "title": "CHUYEN DE 1 - THI DONG TU",
  "videoUrl": "https://www.youtube.com/watch?v=...",
  "documentUrl": "https://drive.google.com/...",
  "homework": { "...xem 1.6..." },
  "vocabularies": [ { "id": "uuid", "word": "asparagus", "meaning": "măng tây" } ]
}
```

`homework` là `null` nếu video chưa có homework.

### 1.5 Tài liệu

`GET /videos/{videoId}/document`

```json
{ "videoId": "uuid", "documentUrl": "https://drive.google.com/..." }
```

### 1.6 Homework của video

`GET /videos/{videoId}/homework` — `404` nếu video hoặc homework không tồn tại.

```json
{
  "id": "homework-exam-uuid",
  "title": "Homework - 21/6/2026",
  "examType": "HOMEWORK",
  "videoId": "uuid",
  "durationMinutes": null,
  "answersRevealed": false,
  "answers_url": "https://drive.google.com/...",
  "parts": [],
  "questions": [
    {
      "id": "question-uuid",
      "orderIndex": 21,
      "content": "This sauce goes _______ well with asparagus.",
      "optA": "wonder",
      "optB": "wonderfully",
      "optC": "wonderful",
      "optD": "wondering"
    }
  ]
}
```

- Homework luôn có `parts: []`; hiển thị câu hỏi từ `questions`. `id` của câu hỏi chính là `questionId` khi nộp bài.
- Response **không** chứa đáp án đúng hay lời giải; chúng chỉ có trong kết quả sau khi nộp.
- `answers_url` có thể `null` nếu bài chưa cấu hình.

### 1.7 Link đáp án của homework

`GET /videos/{videoId}/homework/answers`

```json
{ "videoId": "uuid", "homeworkId": "uuid", "answers_url": "https://drive.google.com/..." }
```

### 1.8 Từ vựng

`GET /videos/{videoId}/vocabularies`

```json
[ { "id": "uuid", "word": "asparagus", "meaning": "măng tây" } ]
```

### 1.9 Trạng thái hiển thị đáp án

`GET /exams/{examId}/answers-revealed` — mọi tài khoản đã đăng nhập đều gọi được; `404` nếu không có exam. `examId` là `homework.id` (mục 1.6).

```json
{ "id": "exam-uuid", "is_answers_revealed": false }
```

### 1.10 Bật/tắt hiển thị đáp án (Admin/Teacher)

`PATCH /exams/{examId}/answers-revealed` — không cần body, đảo giá trị `is_answers_revealed` hiện tại và trả về giá trị mới (cùng dạng response như 1.9). Quyền: `ADMIN` hoặc `TEACHER`.

```json
{ "id": "exam-uuid", "is_answers_revealed": true }
```

Giá trị này cũng xuất hiện ở các response exam dưới tên `answersRevealed`.

---

## 2. Mã lỗi thường gặp

| Mã | Ý nghĩa |
|---|---|
| 400 | Dữ liệu không hợp lệ (ví dụ file JSON sai) |
| 401 | Thiếu hoặc hết hạn token |
| 403 | Không đủ quyền (ví dụ học sinh gọi API tạo bài học) |
| 404 | Không tìm thấy video hoặc homework |

---

## 3. Làm bài homework

### 3.1 Bắt đầu

`POST /submissions/homework/start/{videoId}` (tài khoản `STUDENT`, không cần body).

```json
{
  "submissionId": "uuid",
  "examId": "homework-exam-uuid",
  "mode": "HOMEWORK",
  "totalQuestions": 12,
  "timeLimitMinutes": null,
  "startedAt": "2026-11-05T21:00:00",
  "expiresAt": null,
  "parts": []
}
```

Nếu đã có lượt chưa nộp thì API trả lại lượt đó.

### 3.2 Nộp bài

`POST /submissions/homework/submit`

```json
{
  "submissionId": "uuid từ bước start",
  "exam_id": "homework-exam-uuid",
  "answers": [
    { "questionId": "question-uuid", "selectedOpt": "B" },
    { "questionId": "question-uuid", "selectedOpt": null }
  ]
}
```

- Không gửi `partLogs` (homework không có part).
- `selectedOpt`: `A`/`B`/`C`/`D` hoặc `null` nếu bỏ trống. Mỗi `questionId` chỉ gửi một lần.
- Điểm = số câu đúng × 0.25.

Response (rút gọn):

```json
{
  "submissionId": "uuid",
  "examId": "uuid",
  "examTitle": "Homework - 21/6/2026",
  "totalQuestions": 12,
  "totalCorrect": 10,
  "totalIncorrect": 2,
  "score": 2.5,
  "startedAt": "...",
  "submittedAt": "...",
  "partTimeLogs": [],
  "partTranslations": [],
  "details": [
    {
      "questionId": "uuid",
      "orderIndex": 21,
      "content": "...",
      "selectedOpt": "B",
      "correctOpt": "B",
      "isCorrect": true,
      "explanation": "...",
      "relatedVideo": null
    }
  ]
}
```

`relatedVideo` chỉ có giá trị với câu sai, dạng `{ "videoId", "videoTitle", "video_url" }`.

Mẫu response đầy đủ: `samples/result-general-exam-sample.json`, `samples/result-mock-exam-sample.json`.

Với `GET /submissions/practice/{examId}/latest-review`, `relatedVideo` được trả cho mọi câu có
video liên kết, kể cả câu làm đúng và khi đề chưa công bố đáp án. Nếu chưa công bố đáp án,
`correctOpt` và `explanation` vẫn là `null`; câu không có video liên kết có `relatedVideo: null`.

Response của API luyện đề còn có `parts` gồm tất cả các phần theo thứ tự, với `partId`,
`partName`, `partType`, `orderIndex` và `passageText` (có thể là `null`). Mỗi câu trong
`details` có `partId` để liên kết với `parts`, cùng `optA`, `optB`, `optC`, `optD` để
hiển thị bốn lựa chọn. Các trường này được trả cả khi chưa công bố đáp án.

`GET /submissions/mock/{examId}/review` cũng trả `parts`, `details[].partId`, bốn lựa chọn
và `relatedVideo` cho mọi câu có video liên kết, bất kể kết quả hay trạng thái công bố đáp án.
API thi thử vẫn trả `correctOpt` và `explanation` như trước.

`POST /submissions/submit` cũng trả `parts` và `details[].partId`, `optA`, `optB`, `optC`, `optD`
như trên, cả khi nộp lần đầu hoặc gửi lại bài đã nộp. `POST /submissions/homework/submit`
cũng trả bốn lựa chọn; homework không có part nên `parts` là `[]` và câu hỏi không có `partId`.
Các API nộp bài vẫn chỉ trả `relatedVideo` cho câu sai như trước.

`GET /submissions/homework/{examId}/latest-review` trả nội dung câu hỏi và bốn lựa chọn
`optA`, `optB`, `optC`, `optD` trong `details`, nhưng không trả trường `relatedVideo`.
Điểm số, lựa chọn của học sinh, đáp án đúng và lời giải vẫn được trả như trước.

### 3.3 Link đáp án sau khi nộp

`GET /submissions/exams/{examId}/answer-url` → `{ "answer_url": "https://drive.google.com/..." }`
(lưu ý khóa là `answer_url`, không có chữ `s`). Trả `404` nếu học sinh chưa nộp bài hoặc đề chưa có link.

Nếu chỉ cần hiển thị link đáp án ngay trong trang bài học, có thể dùng `answers_url` ở mục 1.6 hoặc 1.7.
