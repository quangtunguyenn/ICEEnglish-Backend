# Hướng dẫn tích hợp frontend: Thống kê đề thi thử

Tài liệu dành cho màn hình Admin/Staff quản lý và xem thống kê đề thi thử MOCK.

## 1. Thông tin chung

- API prefix: `/api/v1/admin/mock-exams`
- Tất cả endpoint cần JWT:

  ```http
  Authorization: Bearer <accessToken>
  ```

- Chỉ `ADMIN` và `TEACHER` được truy cập. Token thiếu/sai/hết hạn trả `401`; đăng nhập nhưng không đủ quyền trả `403`.
- Response thành công là JSON trực tiếp, không bọc trong `data`. Tên field dùng `camelCase`.
- Timestamp do backend trả về có offset `+07:00` (`Asia/Ho_Chi_Minh`). Frontend không dùng giờ máy học sinh để tính hạn làm bài hay thời điểm nộp; khi hiển thị, giữ đúng thời điểm và định dạng theo múi giờ `Asia/Ho_Chi_Minh`.
- Các danh sách có thể rỗng; giao diện nên có trạng thái empty state thay vì coi đó là lỗi.
- Backend chỉ thống kê lượt MOCK đã nộp. Điểm tính theo thang 10, làm tròn hai chữ số.
- Nếu một học sinh có nhiều lượt nộp cho một đề, chỉ lượt có điểm cao nhất được tính. Nếu bằng điểm: ưu tiên nộp sớm, thời lượng ngắn hơn, rồi `submissionId` tăng dần.

> **Giới hạn dữ liệu hiện tại:** Schema chưa lưu thời điểm publish riêng, nên `publishedAt` là thời điểm tạo đề (`createdAt`). Schema cũng chưa có email; item bảng xếp hạng không trả `email`.

## 2. Luồng màn hình đề xuất

1. Tải danh sách đề bằng `GET /api/v1/admin/mock-exams`.
2. Khi chọn một đề, tải overview bằng `GET /{examId}/stats/overview`.
3. Tải bảng câu hỏi bằng `GET /{examId}/stats/questions`; có thể tải song song với overview.
4. Nếu cần bảng xếp hạng, gọi `GET /{examId}/stats/top?limit=10`.
5. Khi người dùng chọn một khoảng điểm trên biểu đồ, frontend có thể dùng chính dữ liệu bucket để hiển thị bộ lọc/nhãn. API danh sách submissions theo bucket chưa được triển khai.

Với MOCK, khi học sinh vào một part hoặc chuyển sang part khác, gọi
`PUT /api/v1/submissions/{submissionId}/parts/{partId}/time` một lần, không cần
body. Không gửi duration đo bằng đồng hồ frontend. Backend đo thời gian từ lúc
nhận sự kiện; gọi lặp lại cho part đang active không reset bộ đếm. Body `partLogs`
cũ khi submit có thể bỏ đi; backend không còn dùng số giây do client báo cáo.

## 3. Danh sách đề MOCK đã publish

```http
GET /api/v1/admin/mock-exams?page=0&size=20&keyword=
```

| Query | Mặc định | Ghi chú |
|---|---:|---|
| `page` | `0` | Đánh số từ 0 |
| `size` | `20` | Từ 1 đến 100 |
| `keyword` | rỗng | Tìm gần đúng, không phân biệt hoa/thường, trên tiêu đề |

Đề được sắp xếp `createdAt` mới nhất trước. Response mẫu:

```json
{
  "items": [
    {
      "examId": "c0a80123-4567-489a-8abc-123456789abc",
      "title": "Đề thi thử Tốt nghiệp THPT",
      "durationMinutes": 50,
      "totalQuestions": 40,
      "publishedAt": "2026-11-01T15:00:00+07:00",
      "submittedCount": 120,
      "averageScore": 6.85
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `averageScore` là `null` nếu chưa có lượt nộp.
- `items: []` nghĩa là không có đề phù hợp.
- Backend hiện giới hạn `size` tối đa 100.

## 4. Tổng quan một đề

```http
GET /api/v1/admin/mock-exams/{examId}/stats/overview
```

Response mẫu:

```json
{
  "examId": "c0a80123-4567-489a-8abc-123456789abc",
  "title": "Đề thi thử Tốt nghiệp THPT",
  "totalQuestions": 40,
  "submittedCount": 120,
  "averageScore": 6.85,
  "highestScore": 9.75,
  "lowestScore": 2.5,
  "medianScore": 7,
  "averageDurationSeconds": 2700,
  "scoreDistribution": [
    { "label": "0-3", "min": 0, "max": 3, "count": 4, "percent": 3.33 },
    { "label": "3-4", "min": 3, "max": 4, "count": 8, "percent": 6.67 },
    { "label": "10", "min": 10, "max": 10, "count": 1, "percent": 0.83 }
  ]
}
```

Các khoảng mặc định: `0-3`, `3-4`, `4-5`, `5-6`, `6-7`, `7-8`, `8-9`, `9-10`, và `10`.

- Khoảng thường dùng biên nửa mở `[min, max)`. Ví dụ điểm đúng `3` thuộc bucket `3-4`, không thuộc `0-3`.
- Bucket `10` chỉ đếm điểm tuyệt đối 10.
- Backend luôn trả đủ buckets theo thứ tự tăng dần, kể cả bucket có `count: 0`.
- `percent` của bucket tính trên `submittedCount`; khi chưa có bài nộp, tất cả bucket có `count: 0`, `percent: 0`.
- Có thể truyền mốc tùy chỉnh:

  ```http
  GET /api/v1/admin/mock-exams/{examId}/stats/overview?buckets=0,3,4,5,6,7,8,9,10
  ```

  Các mốc phải tăng dần, bắt đầu bằng 0 và kết thúc bằng 10. Nhãn bucket được backend tạo từ mốc; bucket điểm tuyệt đối vẫn có nhãn `10`.
- Khi chưa có bài nộp: `submittedCount` bằng 0; `averageScore`, `highestScore`, `lowestScore`, `medianScore`, `averageDurationSeconds` là `null`. Biểu đồ vẫn có đủ buckets.
- `averageDurationSeconds` là thời lượng trung bình tính từ lúc bắt đầu đến lúc nộp, làm tròn đến giây gần nhất.

## 5. Thống kê từng câu hỏi

```http
GET /api/v1/admin/mock-exams/{examId}/stats/questions
```

Response mẫu:

```json
{
  "examId": "c0a80123-4567-489a-8abc-123456789abc",
  "submittedCount": 120,
  "questions": [
    {
      "questionId": "c0a80123-4567-489a-8abc-123456789abd",
      "partId": "c0a80123-4567-489a-8abc-123456789abe",
      "partName": "Part 3",
      "questionNumber": 1,
      "content": "Question text",
      "correctOpt": "B",
      "answeredCount": 118,
      "unansweredCount": 2,
      "correctCount": 94,
      "wrongCount": 24,
      "correctPercent": 79.66,
      "wrongPercent": 20.34,
      "optionStats": [
        { "option": "A", "count": 71, "percent": 60.17, "correct": false },
        { "option": "B", "count": 12, "percent": 10.17, "correct": true },
        { "option": "C", "count": 12, "percent": 10.17, "correct": false },
        { "option": "D", "count": 23, "percent": 19.49, "correct": false }
      ]
    }
  ]
}
```

- Câu được sắp xếp theo thứ tự gốc của đề; `questionNumber` là thứ tự 1..N, không phụ thuộc thứ tự trộn khi học sinh làm.
- `answeredCount` là số lượt có lựa chọn; `unansweredCount` là số lượt nộp nhưng bỏ trống câu đó.
- `correctPercent`, `wrongPercent` và phần trăm từng lựa chọn dùng `answeredCount` làm mẫu số; lượt bỏ trống không tính vào phần trăm.
- `optionStats` luôn có đủ A/B/C/D, kể cả lựa chọn có số lượng 0.
- Nếu không ai trả lời câu đó, các phần trăm của câu và lựa chọn là `null`; hiển thị `—` hoặc trạng thái chưa có dữ liệu, không hiển thị như 0%.
- Khi đề chưa có bài nộp, `questions` vẫn chứa nội dung câu hỏi, `submittedCount` bằng 0, số đếm bằng 0, phần trăm là `null`.

## 6. Bảng xếp hạng

```http
GET /api/v1/admin/mock-exams/{examId}/stats/top?limit=10
```

- `limit` mặc định 10; chấp nhận từ 1 đến 100.
- Thứ tự: điểm giảm dần → nộp sớm hơn → thời lượng ngắn hơn → `submissionId` tăng dần.
- `rank` là thứ hạng liên tục, không đồng hạng.

Response mẫu:

```json
{
  "examId": "c0a80123-4567-489a-8abc-123456789abc",
  "limit": 10,
  "items": [
    {
      "rank": 1,
      "userId": "c0a80123-4567-489a-8abc-123456789abd",
      "fullName": "Nguyễn Văn A",
      "submissionId": "c0a80123-4567-489a-8abc-123456789abe",
      "score": 9.75,
      "correctCount": 39,
      "totalQuestions": 40,
      "durationSeconds": 2410,
      "submittedAt": "2026-11-05T16:12:33+07:00"
    }
  ]
}
```

`items: []` nghĩa là chưa có học sinh nộp. Response không có `email`; dùng `fullName` để hiển thị tên học sinh.

## 7. Kiểu TypeScript gợi ý

```ts
export type ExamPage = {
  items: MockExamItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type MockExamItem = {
  examId: string;
  title: string;
  durationMinutes: number | null;
  totalQuestions: number;
  publishedAt: string | null;
  submittedCount: number;
  averageScore: number | null;
};

export type ScoreBucket = {
  label: string;
  min: number;
  max: number;
  count: number;
  percent: number;
};

export type MockExamOverview = {
  examId: string;
  title: string;
  totalQuestions: number;
  submittedCount: number;
  averageScore: number | null;
  highestScore: number | null;
  lowestScore: number | null;
  medianScore: number | null;
  averageDurationSeconds: number | null;
  scoreDistribution: ScoreBucket[];
};

export type OptionStat = {
  option: "A" | "B" | "C" | "D";
  count: number;
  percent: number | null;
  correct: boolean;
};

export type QuestionStat = {
  questionId: string;
  partId: string | null;
  partName: string | null;
  questionNumber: number;
  content: string;
  correctOpt: string;
  answeredCount: number;
  unansweredCount: number;
  correctCount: number;
  wrongCount: number;
  correctPercent: number | null;
  wrongPercent: number | null;
  optionStats: OptionStat[];
};

export type MockExamQuestions = {
  examId: string;
  submittedCount: number;
  questions: QuestionStat[];
};

export type MockExamTopItem = {
  rank: number;
  userId: string;
  fullName: string | null;
  submissionId: string;
  score: number;
  correctCount: number;
  totalQuestions: number;
  durationSeconds: number;
  submittedAt: string;
};

export type MockExamTop = {
  examId: string;
  limit: number;
  items: MockExamTopItem[];
};
```

## 8. Ví dụ gọi API bằng fetch

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

const vietnamDateTime = (timestamp: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(timestamp));

async function adminGet<T>(path: string, accessToken: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/mock-exams${path}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? `Request failed (${response.status})`);
  }

  return response.json() as Promise<T>;
}

const exams = await adminGet<ExamPage>("?page=0&size=20", accessToken);
const examId = exams.items[0]?.examId;

if (examId) {
  const [overview, questionStats, top] = await Promise.all([
    adminGet<MockExamOverview>(`/${examId}/stats/overview`, accessToken),
    adminGet<MockExamQuestions>(`/${examId}/stats/questions`, accessToken),
    adminGet<MockExamTop>(`/${examId}/stats/top?limit=10`, accessToken),
  ]);
}
```

Khi tạo query string động, dùng `URLSearchParams` để encode keyword và buckets:

```ts
const query = new URLSearchParams({
  page: String(page),
  size: String(size),
  keyword,
});
const result = await adminGet<ExamPage>(`?${query}`, accessToken);
```

## 9. Trạng thái lỗi nên xử lý

| HTTP | Ý nghĩa | Gợi ý UI |
|---:|---|---|
| `400` | Query không hợp lệ, ví dụ `size`, `limit` vượt giới hạn hoặc `buckets` không tăng dần | Hiển thị message từ response; kiểm tra lại tham số |
| `401` | Token thiếu, sai hoặc hết hạn | Thực hiện luồng đăng nhập lại |
| `403` | User không có quyền Admin/Staff | Hiển thị thông báo không đủ quyền |
| `404` | `examId` không tồn tại hoặc đề không phải MOCK | Quay lại danh sách hoặc báo đề không còn khả dụng |

Danh sách đề và bảng top hiện trả dữ liệu phân trang/giới hạn tương ứng; API thống kê danh sách bài nộp theo bucket chưa có trong backend.
