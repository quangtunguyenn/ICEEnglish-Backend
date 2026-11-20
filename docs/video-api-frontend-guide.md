# Hướng dẫn API Video cho Frontend

Base URL: `/api/v1`. Các API học sinh/quản trị yêu cầu header `Authorization: Bearer <token>`; API guest được ghi rõ bên dưới không cần token.
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
    {
      "word": "asparagus",
      "meaning": "măng tây",
      "phonetic": "/əˈspær.ə.ɡəs/",
      "example_sentence": "I ate asparagus for lunch.",
      "example_sentence_translate": "Tôi ăn măng tây vào bữa trưa."
    }
  ]
}
```

Quy tắc:
- Bắt buộc: `video.title`, `video.videoUrl`. `video.type` nhận `lecture`, `practice`, `exam_tips` (không phân biệt hoa/thường); nếu bỏ trống thì mặc định `lecture` để tương thích các bundle cũ.
- `video.position` là tùy chọn; nếu bỏ qua sẽ là `null`. Khi có giá trị, dùng dạng `1` hoặc `1.1` (ví dụ `2.10`), phải duy nhất và được so sánh theo từng phần số nguyên.
- Với `lecture`, bắt buộc có `homework` với ít nhất 1 câu hỏi và ít nhất 1 `vocabularies`.
- Với `practice` hoặc `exam_tips`, có thể bỏ `homework` và/hoặc `vocabularies`; các phần này vẫn được import nếu có gửi. Khi không có homework, response `homeworkId` là `null`; khi không có từ vựng, `vocabularyCount` là `0`.
- Câu hỏi có thể dùng `options: {A,B,C,D}` hoặc các trường `optA..optD`. Đáp án đúng: `correctOption` (hoặc `correctOpt`), chỉ nhận `A`/`B`/`C`/`D`.
- `answers_url` lấy từ `homework.answers_url`; nếu không có thì lấy `video.answers_url`.
- Homework không có part, câu hỏi nằm trực tiếp trong exam.
- Lỗi dữ liệu trả `400` với thông báo tiếng Việt (JSON sai cú pháp có kèm dòng/cột).
- `phonetic`, `example_sentence` và `example_sentence_translate` là chuỗi tùy chọn hoặc `null`. Chuỗi trắng được lưu thành `null`; chuỗi không rỗng được giữ nguyên. Phiên âm tối đa 250 ký tự Unicode, mỗi câu ví dụ và bản dịch tối đa 5.000 ký tự Unicode.
- Nếu bản dịch không rỗng thì `example_sentence` cũng phải không rỗng; sai kiểu hoặc vượt giới hạn trả `400`. Bundle cũ không có các trường này vẫn dùng được. Ngoài snake_case, import cũng nhận alias `exampleSentence` và `exampleSentenceTranslate`.
- Trước khi triển khai backend mới, áp dụng migration `src/main/resources/db/migration/V7__Add_video_vocabulary_details.sql` theo quy trình database của dự án. Các cột mới nullable, dữ liệu cũ không cần import lại; migration không tự chạy bởi Flyway trong cấu hình hiện tại.
- Áp dụng thêm `src/main/resources/db/migration/V15__Add_video_position.sql` trước khi deploy tính năng sắp xếp; video cũ có `position: null`.

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
    "position": "1.1",
    "videoUrl": "https://www.youtube.com/watch?v=...",
    "documentUrl": "https://drive.google.com/...",
    "createdAt": "2026-11-05T20:00:00"
  }
]
```

Lưu ý: danh sách này **không** có `answers_url` (URL đáp án thuộc về homework). Danh sách sắp theo `position` dạng số; video chưa có vị trí nằm cuối theo `createdAt` tăng dần.

### 1.3 Danh sách tên video

`GET /videos/titles` — dùng cho danh sách chọn bài (ví dụ trang luyện tập từ vựng), cùng thứ tự với API danh sách video.

```json
[
  { "video_id": "uuid", "title": "CHUYEN DE 1 - THI DONG TU", "position": "1.1" }
]
```

Dùng `video_id` để gọi `GET /videos/{videoId}/vocabularies`.

### 1.3.1 Quyền truy cập guest

Danh sách dành cho khách là `GET /guest/videos` (không cần token), trả tất cả video với `videoId`, `title`, `position`, `accessible`. Frontend hiển thị cả video khóa; chỉ cho khách mở nội dung khi `accessible: true`. Nếu khách gọi trực tiếp API nội dung hoặc homework của video khóa, backend trả `403`; video không tồn tại trả `404`.

Admin bật/tắt quyền truy cập guest cho từng video:

```http
PUT /admin/videos/{videoId}/guest-access
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{ "guestAccessible": true }
```

Gửi `false` để khóa video. Response là `204 No Content`; video mới mặc định không mở cho khách. Thao tác này không thay đổi quyền truy cập của học sinh đã đăng nhập và không làm thay đổi response của `GET /videos/titles`.

### 1.3.2 Cập nhật thứ tự video (Admin/Teacher)

```http
PUT /admin/videos/{videoId}/position
Authorization: Bearer <admin-or-teacher-token>
Content-Type: application/json
```

```json
{ "position": "2.3" }
```

Response là `204 No Content`. Gửi `null` hoặc chuỗi rỗng để bỏ vị trí. Trả `400` kèm `message` nếu sai định dạng, `404` nếu không có video, `409` kèm `message` (và tên video nếu có) nếu trùng vị trí. Teacher có thể đổi vị trí nhưng vẫn không thể bật/tắt quyền guest access.

### 1.3.3 Xóa cứng video (Admin)

Xóa vĩnh viễn video và dữ liệu học tập liên quan: từ vựng, các đề/homework gắn
với video, lượt làm homework cùng câu trả lời và log thời gian. Các câu hỏi khác
có liên kết `relatedVideo` tới video này sẽ được giữ lại nhưng gỡ liên kết.
Thao tác này không thể hoàn tác.

```http
DELETE /admin/videos/{videoId}
Authorization: Bearer <admin-token>
```

Response `204 No Content`; trả `404` nếu không tìm thấy video. Chỉ `ADMIN` được
phép gọi API này.

### 1.4 Nội dung bài học (một lần gọi lấy đủ)

`GET /videos/{videoId}/learning-content`

```json
{
  "videoId": "uuid",
  "title": "CHUYEN DE 1 - THI DONG TU",
  "videoUrl": "https://www.youtube.com/watch?v=...",
  "documentUrl": "https://drive.google.com/...",
  "homework": { "...xem 1.6..." },
  "vocabularies": [ {
    "id": "uuid", "word": "asparagus", "meaning": "măng tây",
    "phonetic": "/əˈspær.ə.ɡəs/", "exampleSentence": "I ate asparagus for lunch.",
    "exampleSentenceTranslate": "Tôi ăn măng tây vào bữa trưa."
  } ]
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
[ {
  "id": "uuid", "word": "asparagus", "meaning": "măng tây",
  "phonetic": "/əˈspær.ə.ɡəs/", "exampleSentence": "I ate asparagus for lunch.",
  "exampleSentenceTranslate": "Tôi ăn măng tây vào bữa trưa."
} ]
```

Ba trường mới trả về `null` nếu không có dữ liệu. Hai endpoint video mẫu của khách cũng trả cùng các trường, nhưng vẫn kiểm tra quyền truy cập video như trước. Với flashcard, frontend hiển thị `word` hoặc `meaning` theo mặt thẻ; cả hai mặt dùng `phonetic` và câu tiếng Anh `exampleSentence`. Bản dịch `exampleSentenceTranslate` dành cho khu vực từ vựng trên trang video.

### 1.8.1 Flashcard yêu thích

Các endpoint sau dành cho học sinh đã đăng nhập:

- `GET /vocabularies/favorites` trả danh sách yêu thích cùng tối đa 40 từ lấy ngẫu nhiên từ `vocabularies` làm đáp án nhiễu cho trắc nghiệm.
- `POST /vocabularies/favorites` nhận `{"word":"apple","meaning":"quả táo"}`. Từ và nghĩa được lưu tự do theo tài khoản; thêm trùng sẽ trả lại thẻ đã lưu.
- `DELETE /vocabularies/favorites/{favoriteId}` xóa thẻ của học sinh hiện tại.

Thẻ lật dùng đúng từ và nghĩa do học sinh nhập. Ở chế độ chọn đáp án, ba đáp án nhiễu được lấy ngẫu nhiên từ kho `vocabularies`. Khách chưa đăng nhập được nhắc đăng ký tư vấn khi bấm tạo; thẻ yêu thích không thuộc danh sách bài học ở sidebar.

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
| 409 | Vị trí video đã được dùng |

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

`relatedVideo` chứa video đầu tiên để tương thích ngược; `relatedVideos` chứa toàn bộ video liên quan dạng `{ "videoId", "videoTitle", "accessible", "video_url" }[]`. Cả hai chỉ có giá trị với câu sai.

Trong kết quả làm bài của khách, `relatedVideos` vẫn liệt kê đủ tên các video liên quan. `accessible: true` cho phép khách mở video; với `accessible: false`, frontend giữ tên video và yêu cầu đăng nhập khi khách bấm. Kết quả khách không trả `video_url`.

Mẫu response đầy đủ: `samples/result-general-exam-sample.json`, `samples/result-mock-exam-sample.json`.

Với `GET /submissions/practice/{examId}/latest-review`, `relatedVideo` và `relatedVideos` được trả cho mọi câu có
video liên kết, kể cả câu làm đúng và khi đề chưa công bố đáp án. Nếu chưa công bố đáp án,
`correctOpt` và `explanation` vẫn là `null`; câu không có video liên kết có `relatedVideo: null`.

Response của API luyện đề còn có `parts` gồm tất cả các phần theo thứ tự, với `partId`,
`partName`, `partType`, `orderIndex` và `passageText` (có thể là `null`). Mỗi câu trong
`details` có `partId` để liên kết với `parts`, cùng `optA`, `optB`, `optC`, `optD` để
hiển thị bốn lựa chọn. Các trường này được trả cả khi chưa công bố đáp án.

`GET /submissions/mock/{examId}/review` cũng trả `parts`, `details[].partId`, bốn lựa chọn
và `relatedVideo`/`relatedVideos` cho mọi câu có video liên kết, bất kể kết quả hay trạng thái công bố đáp án.
API thi thử vẫn trả `correctOpt` và `explanation` như trước.

`POST /submissions/submit` cũng trả `parts` và `details[].partId`, `optA`, `optB`, `optC`, `optD`
như trên, cả khi nộp lần đầu hoặc gửi lại bài đã nộp. `POST /submissions/homework/submit`
cũng trả bốn lựa chọn; homework không có part nên `parts` là `[]` và câu hỏi không có `partId`.
Các API nộp bài vẫn chỉ trả video liên quan cho câu sai như trước.

`GET /submissions/homework/{examId}/latest-review` trả nội dung câu hỏi và bốn lựa chọn
`optA`, `optB`, `optC`, `optD` trong `details`, nhưng không trả trường video liên quan.
Điểm số, lựa chọn của học sinh, đáp án đúng và lời giải vẫn được trả như trước.

### 3.3 Link đáp án sau khi nộp

`GET /submissions/exams/{examId}/answer-url` → `{ "answer_url": "https://drive.google.com/..." }`
(lưu ý khóa là `answer_url`, không có chữ `s`). Trả `404` nếu học sinh chưa nộp bài hoặc đề chưa có link.

Nếu chỉ cần hiển thị link đáp án ngay trong trang bài học, có thể dùng `answers_url` ở mục 1.6 hoặc 1.7.
