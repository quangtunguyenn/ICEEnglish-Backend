# Trộn part theo lượt làm bài: trạng thái backend và hướng dẫn frontend

## 1. Những gì backend đã hoàn thành

- Hỗ trợ cấu hình trộn part khi import đề `MOCK` và `GENERAL_EXAM`.
- Khi tạo submission mới cho đề bật shuffle, backend sinh và lưu thứ tự part theo lượt đó.
  Khi quay lại submission đang làm dở, backend dùng lại thứ tự đã lưu.
- Hỗ trợ part cố định và nhóm shuffle. Không trộn câu hỏi trong cùng part.
- `start` trả `partOrder`; API lấy đề nhận thêm `submissionId` và trả part theo thứ tự của lượt.
- Kết quả submit, review và log thời gian có thông tin vị trí hiển thị. Chấm điểm vẫn dựa
  trên `questionId`, không dựa trên vị trí.
- Submission cũ không có thứ tự đã lưu và đề không bật shuffle vẫn dùng thứ tự gốc.
- Đã có test cho thuật toán, import, resume, quyền sở hữu, kết quả và chấm điểm.
  Đã kiểm thử tích hợp qua MockMvc với PostgreSQL cho cả `MOCK` và `GENERAL_EXAM`.

**Trạng thái triển khai:** migration V3 đã được áp dụng trên database cục bộ `ICEEnglish`.
Test tích hợp đã xác minh ghi/đọc JSONB, resume, quyền truy cập, nộp bài và review; riêng
`MOCK` được kiểm tra thêm log thời gian. Các dữ liệu test chạy trong transaction rollback.
Đây là test MockMvc trong tiến trình, dùng authentication giả lập và không thay thế kiểm tra
JWT/HTTP qua server đang chạy hoặc kiểm thử sau khi commit. Các môi trường khác vẫn cần áp dụng
migration và triển khai code.

## 2. Nguyên tắc tích hợp

- Base URL trong ví dụ: `/api/v1`. Các API học sinh yêu cầu đăng nhập bằng token của học sinh;
  import và preview yêu cầu tài khoản `ADMIN` hoặc `TEACHER`.
- Frontend **không tự shuffle**. Render `parts` theo thứ tự backend trả về.
- Luôn giữ và gửi đúng `submissionId` khi lấy đề cho lượt làm. Đáp án, draft và thời gian tiếp tục
  khóa bằng `questionId` hoặc `partId`, không khóa bằng chỉ số trong mảng.
- Dùng `displayIndex` cho vị trí part và `displayNumber` cho số câu. Không dùng `orderIndex`
  để xác định vị trí hiển thị khi đã có các trường này: `orderIndex` là giá trị gốc trong đề.
- `displayIndex` và `displayNumber` bắt đầu từ 1. Nếu lấy đề **không** có `submissionId`,
  các trường này có thể là `null`; frontend có thể đếm theo thứ tự part và câu được trả về.
- Hai lượt có thể tình cờ nhận cùng một thứ tự. Không có cam kết mỗi học sinh hoặc mỗi lượt
  luôn có hoán vị khác nhau.
- JSON ví dụ dưới đây rút gọn để làm rõ các trường liên quan; UUID chỉ là dữ liệu minh họa.

## 3. Luồng làm bài

### Bước 1: Bắt đầu hoặc tiếp tục lượt làm

```http
POST /api/v1/submissions/start/{examId}
Authorization: Bearer <student-token>
```

Không cần request body. Response `200 OK`, ví dụ:

```json
{
  "submissionId": "11111111-1111-4111-8111-111111111111",
  "examId": "22222222-2222-4222-8222-222222222222",
  "mode": "MOCK",
  "totalQuestions": 2,
  "timeLimitMinutes": 50,
  "startedAt": "2026-11-07T10:00:00",
  "expiresAt": "2026-11-07T10:50:00",
  "partOrder": [
    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
  ],
  "parts": [
    {
      "partId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "partName": "Grammar B",
      "orderIndex": 2,
      "displayIndex": 1,
      "recommendedTimeSeconds": 0
    },
    {
      "partId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "partName": "Grammar A",
      "orderIndex": 1,
      "displayIndex": 2,
      "recommendedTimeSeconds": 0
    }
  ]
}
```

- Lưu `submissionId` theo tài khoản và `examId`; dùng ID này để lấy đề, lưu draft, ghi thời
  gian và nộp bài. `partOrder` là danh sách ID theo thứ tự hiển thị; `parts` trong response
  này cũng đã được sắp theo thứ tự đó.
- Nếu đã có lượt chưa nộp và còn hiệu lực, gọi lại `start` sẽ trả lại lượt và thứ tự cũ.
  Với `MOCK`, backend hiện chỉ cho một lượt; lượt đã nộp hoặc hết hạn không được bắt đầu lại.
- Nếu khôi phục `submissionId` từ localStorage, cần bảo đảm nó thuộc tài khoản và đề hiện tại.
  Nếu server không chấp nhận ID, hiển thị lỗi và kiểm tra lại trạng thái lượt; không tự tạo
  thứ tự mới hoặc âm thầm dùng submission của tài khoản khác.
- Lưu `submissionId` không đồng nghĩa đáp án đã được lưu hoặc đồng bộ lên server.

### Bước 2: Lấy đề theo submission

```http
GET /api/v1/exams/{examId}?submissionId={submissionId}
Authorization: Bearer <student-token>
```

Response `200 OK`, ví dụ rút gọn:

```json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "title": "Mock exam",
  "examType": "MOCK",
  "shuffleParts": true,
  "parts": [
    {
      "id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "partName": "Grammar B",
      "partType": "GRAMMAR",
      "orderIndex": 2,
      "displayIndex": 1,
      "fixedPosition": false,
      "shuffleGroup": "grammar",
      "questions": [
        {
          "id": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
          "orderIndex": 2,
          "displayNumber": 1,
          "content": "Choose the correct form.",
          "optA": "go",
          "optB": "goes",
          "optC": "going",
          "optD": "gone"
        }
      ]
    },
    {
      "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "partName": "Grammar A",
      "partType": "GRAMMAR",
      "orderIndex": 1,
      "displayIndex": 2,
      "fixedPosition": false,
      "shuffleGroup": "grammar",
      "questions": [
        {
          "id": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
          "orderIndex": 1,
          "displayNumber": 2,
          "content": "Choose the correct word.",
          "optA": "is",
          "optB": "are",
          "optC": "am",
          "optD": "be"
        }
      ]
    }
  ],
  "questions": []
}
```

- Render `parts` và `questions` trong từng part theo đúng thứ tự response. Dùng
  `displayNumber` cho tiêu đề câu, bảng điều hướng và những nơi khác cần số câu.
- Backend kiểm tra submission thuộc học sinh đang đăng nhập **và** thuộc `examId` được yêu cầu.
  Submission không tồn tại, của người khác hoặc không khớp đề trả `404`.
- Không truyền `submissionId`: API giữ hành vi cũ và trả thứ tự gốc. Học sinh chỉ lấy được
  đề `PUBLISHED`. Nếu đề không còn được publish, không giả định endpoint này sẽ cho phép
  khôi phục bài dù đã có `submissionId`.
- Nội dung câu hỏi không kèm đáp án đúng hoặc lời giải. Các trường `fixedPosition`,
  `shuffleGroup`, `shuffleParts` mô tả cấu hình, không yêu cầu frontend thực hiện shuffle.

### Bước 3: Ghi thời gian và nộp bài

API ghi thời gian part hiện chỉ dùng cho `MOCK`:

```http
PUT /api/v1/submissions/{submissionId}/parts/{partId}/time
Authorization: Bearer <student-token>
Content-Type: application/json

{"timeSpentSeconds": 120}
```

Response thành công: `204 No Content`. `partId` là ID ổn định, không phải `displayIndex`.

Nộp bài như hiện tại:

```http
POST /api/v1/submissions/submit
Authorization: Bearer <student-token>
Content-Type: application/json
```

```json
{
  "submissionId": "11111111-1111-4111-8111-111111111111",
  "exam_id": "22222222-2222-4222-8222-222222222222",
  "answers": [
    {"questionId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd", "selectedOpt": "B"},
    {"questionId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "selectedOpt": "A"}
  ],
  "partLogs": [
    {"partId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "timeSpentSeconds": 120}
  ]
}
```

`partLogs` là tùy chọn. Không thay đổi ID khi đổi thứ tự hiển thị. Backend vẫn kiểm tra
submission, đề, câu hỏi, đáp án và chấm theo `questionId`.

## 4. Kết quả và log

Response của `POST /submissions/submit` và các API review bên dưới có các trường liên quan:

```json
{
  "submissionId": "11111111-1111-4111-8111-111111111111",
  "examId": "22222222-2222-4222-8222-222222222222",
  "parts": [
    {
      "partId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "partName": "Grammar B",
      "partType": "GRAMMAR",
      "orderIndex": 2,
      "displayIndex": 1,
      "passageText": null
    },
    {
      "partId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "partName": "Grammar A",
      "partType": "GRAMMAR",
      "orderIndex": 1,
      "displayIndex": 2,
      "passageText": null
    }
  ],
  "details": [
    {
      "questionId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
      "partId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "orderIndex": 2,
      "partDisplayIndex": 1,
      "displayNumber": 1,
      "selectedOpt": "B",
      "isCorrect": true
    }
  ],
  "partTimeLogs": [
    {
      "partId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "partName": "Grammar B",
      "timeSpentSeconds": 120,
      "displayIndex": 1
    }
  ]
}
```

Ví dụ đã lược bỏ các trường nội dung, đáp án và thống kê khác. Với lượt có thứ tự đã lưu,
`parts` và `details` theo thứ tự lượt đó. Với lượt cũ không có thứ tự đã lưu, `details` giữ
thứ tự cũ theo `orderIndex`; để hiển thị nhất quán trong mọi trường hợp, frontend nhóm bằng
`partId`, render theo `parts[].displayIndex` và dùng `details[].displayNumber`.

Các API review:

| API | Dữ liệu |
|---|---|
| `GET /api/v1/submissions/practice/{submissionId}/review` | Một submission `GENERAL_EXAM` đã nộp của học sinh |
| `GET /api/v1/submissions/practice/{examId}/latest-review` | Lượt luyện đề đã nộp gần nhất |
| `GET /api/v1/submissions/mock/{examId}/review` | Kết quả thi thử đã nộp |

Review luyện đề vẫn tuân theo trạng thái công bố đáp án hiện có; không giả định `correctOpt`
và `explanation` luôn có giá trị. `partTranslations` được liên kết bằng `partId`.

Lấy log riêng:

```http
GET /api/v1/submissions/{submissionId}/part-logs
Authorization: Bearer <student-token>
```

Response là mảng các object có `partId`, `partName`, `timeSpentSeconds`, `displayIndex`.
Chỉ các part có log mới xuất hiện; có thể trả mảng rỗng. Với submission cũ không có thứ tự
đã lưu, thứ tự mảng log giữ hành vi cũ, do đó frontend nên dùng `displayIndex` để sắp xếp
khi cần. Không giả định tất cả part đều có log.

## 5. Cấu hình đề cho staff

API import hiện dùng `multipart/form-data`, không nhận trực tiếp JSON body:

```http
POST /api/v1/exams/import?examType=GENERAL_EXAM
Authorization: Bearer <staff-token>
Content-Type: multipart/form-data
```

Field `file` chứa JSON. `examType` bắt buộc là `GENERAL_EXAM` hoặc `MOCK`; `MOCK` cần
`duration_minutes`. Các trường mới trong file:

| Trường import | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `exam.shuffle_parts` | boolean | `false` | Bật shuffle cho submission mới |
| `parts[].fixed_position` | boolean | `false` | Giữ vị trí trong danh sách part gốc |
| `parts[].shuffle_group` | string hoặc `null` | `null` | Chỉ đổi chỗ với part không cố định cùng nhóm |

`shuffle_group` không được là chuỗi rỗng hoặc chỉ có khoảng trắng; tối đa 255 ký tự. Khoảng
trắng đầu/cuối được bỏ khi lưu. Các part có nhóm `null` thuộc một nhóm chung. Cùng nhóm
không đảm bảo các part đi liền nhau hay giữ thứ tự tương đối.

Xem [JSON mẫu đầy đủ](exam-import-sample.json). Sau khi import, staff xem:

```http
GET /api/v1/exams/{examId}/preview
Authorization: Bearer <staff-token>
```

Preview luôn trả thứ tự gốc, kèm `shuffleParts`, `parts[].fixedPosition` và
`parts[].shuffleGroup`; part `READING_8`/`READING_10` có thêm `parts[].translate` (có thể `null`,
loại part khác luôn `null`); preview có cả đáp án và lời giải, không dùng cho học sinh. Hiện
chưa có API sửa hoặc bật/tắt cấu hình shuffle sau import.

## 6. Giới hạn nội dung và tương thích

- Backend không tự thay `{{n}}` hoặc sửa số câu viết cứng trong `content`, `passage_text`
  và lời giải. Đặc biệt, số ô trống trong passage cloze có thể vẫn là số gốc. Cần giáo viên
  kiểm tra nội dung trước khi bật shuffle và frontend/backend thống nhất cách xử lý nếu muốn
  dùng placeholder.
- Câu hỏi vẫn giữ thứ tự gốc **trong từng part**; chỉ thay thứ tự các part.
- Vị trí cố định là vị trí trong danh sách sắp theo `orderIndex`, không nhất thiết bằng chính
  giá trị `orderIndex` nếu các giá trị không liên tiếp.
- `HOMEWORK` hiện có câu hỏi trực tiếp trên đề, không có part để shuffle; các luồng homework
  hiện tại không được chuyển sang shuffle part.
- Nếu đề không bật shuffle hoặc submission cũ chưa có bản ghi thứ tự, dùng thứ tự gốc.
- Không có bảo đảm nội dung đề được snapshot riêng theo từng submission. Không sửa cấu trúc
  part của đề đã có lượt làm nếu chưa có chính sách tương thích cho các lượt đó.

## 7. Checklist frontend

- [ ] Lưu và khôi phục đúng `submissionId` theo học sinh và đề.
- [ ] Gọi API đề với `submissionId`; không shuffle hoặc sắp lại theo `orderIndex`.
- [ ] Dùng `displayNumber` ở trang làm bài, điều hướng câu và kết quả.
- [ ] Giữ đáp án/draft theo `questionId`; log thời gian theo `partId`.
- [ ] Nhóm kết quả theo `partId` và thứ tự `parts[].displayIndex`; xử lý log bị thiếu.
- [ ] Xử lý lỗi submission không hợp lệ hoặc không thuộc học sinh, không tự đổi thứ tự.
- [ ] Kiểm tra đề cũ, đề không shuffle, reload/resume, submit và review.
- [ ] Kiểm tra nội dung số câu viết cứng trước khi bật shuffle cho đề.

## 8. Ghi chú triển khai backend

Migration [V3__Add_submission_part_shuffle.sql](../src/main/resources/db/migration/V3__Add_submission_part_shuffle.sql)
thêm `exams.shuffle_parts`, `exam_parts.fixed_position`, `exam_parts.shuffle_group` và
`submissions.part_order` kiểu JSONB. Boolean mặc định `false`; submission cũ không cần backfill.
Dự án hiện chưa cấu hình Flyway tự chạy migration. Cần áp dụng migration có kiểm soát trước
khi triển khai code mới, sau khi sao lưu và kiểm tra theo quy trình của môi trường.

Để chạy lại test tích hợp PostgreSQL trên database được cấu hình cho ứng dụng:

```powershell
.\mvnw.cmd '-Dtest=PartShufflePostgresIntegrationTest' '-Dshuffle.integration=true' test
```

Test yêu cầu schema đã được migrate, khởi động Hibernate ở chế độ `validate` và rollback các
dữ liệu test. Chỉ chạy trên database đã được phép dùng để kiểm thử; không tự chạy trên production.
