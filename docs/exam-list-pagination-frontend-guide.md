# Frontend guide: exam list and mock score pagination

This guide covers the student exam-list screens for practice (`GENERAL_EXAM`)
and mock exams (`MOCK`), plus the submitted mock-score history. Responses are
plain JSON; the API does not wrap them in a `data` property.

## 1. Paginated exam lists

```http
GET /api/v1/exams?mode=GENERAL_EXAM&page=0&size=12&keyword=&sort=createdAt,desc
GET /api/v1/exams?mode=MOCK&page=0&size=12&keyword=&sort=createdAt,desc
Authorization: Bearer <access-token>
```

Query parameters:

| Parameter | Default | Behavior |
|---|---:|---|
| `mode` | none | `GENERAL_EXAM`, `MOCK`, or another supported exam type |
| `page` | `0` | Zero-based page number |
| `size` | `12` | Number of items; valid range is 1–100 |
| `keyword` | omitted | Title search ignores Vietnamese diacritics and letter case; whitespace is trimmed and collapsed |
| `sort` | `createdAt,desc` | Supports `createdAt` or `title`, followed by `asc` or `desc` |

For student accounts, the backend always filters `status=PUBLISHED` **before**
search and pagination. A client-supplied `status` is ignored for students.
`ADMIN`/`TEACHER` can pass `status=PUBLISHED`, `DRAFT`, or `ARCHIVED`; if omitted,
all statuses are included. Filtering is performed by the backend, so the
frontend must not filter the current page by status or mode.

Example response:

```json
{
  "items": [
    {
      "id": "exam-uuid",
      "title": "Đề thi thử Tốt nghiệp THPT",
      "mode": "MOCK",
      "status": "PUBLISHED",
      "durationMinutes": 50,
      "totalQuestions": 40,
      "createdAt": "2026-11-01T08:00:00+07:00",
      "attempted": true,
      "latestScore": 7.5
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 35,
  "totalPages": 3
}
```

Each paginated item contains:

- `id`, `title`, `mode`, `status`, `durationMinutes`, `totalQuestions`
- `createdAt`: ISO-8601 timestamp with Vietnam offset (`+07:00`)
- `attempted`: whether the current student has any submission for this exam,
  including an unfinished attempt
- `latestScore`: score from the most recently submitted attempt, or `null` if
  there is no submitted attempt

For non-student accounts, `attempted` and `latestScore` are `null`. Score values
use the existing backend scale and calculation (`totalCorrect * 0.25`).
Do not request `/submissions/practice/{examId}/latest-score` once per exam to
populate the paginated list; use these item fields instead. A `null`
`latestScore` means no submitted score, not a failed score request.

If either `page` or `size` is sent, the endpoint returns the paginated object.
If both are omitted, it keeps returning the legacy array response. During
migration, frontend code may normalize both shapes, but new list screens should
send `page` and `size` and consume the paginated response.

## 2. Paginated submitted mock scores

Use this endpoint for mock-exam history, rather than loading all submitted
scores at once:

```http
GET /api/v1/submissions/mock/scores?page=0&size=20&sort=submittedAt,desc
Authorization: Bearer <access-token>
```

`page` is zero-based, `size` defaults to 20 and must be from 1 to 100.
`sort` supports `submittedAt,asc` or `submittedAt,desc`; the default is
`submittedAt,desc`. A stable submission-id tie-breaker is applied by the
backend.

```json
{
  "items": [
    {
      "submissionId": "submission-uuid",
      "examId": "exam-uuid",
      "examTitle": "Đề thi thử Tốt nghiệp THPT",
      "totalQuestions": 40,
      "totalCorrect": 30,
      "correctCount": 30,
      "totalIncorrect": 10,
      "score": 7.5,
      "startedAt": "2026-11-05T08:00:00+07:00",
      "submittedAt": "2026-11-05T08:45:00+07:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`correctCount` is an alias for `totalCorrect`, included for clients that use
that field name. Both are counts of correct answers. Timestamps are server
timestamps with the Vietnam offset; parse them as ISO-8601 values and format
for display in `Asia/Ho_Chi_Minh`.

If both `page` and `size` are omitted, this endpoint retains its legacy array
response. Send them to receive the paginated object.

## 3. Frontend pagination behavior

- Start with `page=0`; reset to page 0 when `mode`, `keyword`, or page size
  changes.
- Use `totalPages` to disable Previous/Next at the first and last pages.
- Render an empty state when `items` is empty. Do not treat `totalPages=0` as an
  API error.
- Debounce keyword input if appropriate, and request the page again using the
  updated keyword.
- Debounce search input by 300 ms. Trim the typed string and omit `keyword`
  entirely when it is empty. Search matches regardless of Vietnamese accents
  (`de thi thu` finds `Đề thi thử`, and `đề` also finds titles beginning with
  `de`). Reset to page 0 when the keyword changes.
- Send a `status` filter only for staff screens. Student screens should not
  rely on client-side status filtering.
- A request with `size` below 1 or above 100, a negative `page`, or an unsupported
  `sort` returns a client error (`400`). Keep page size in the supported range.

Example TypeScript types:

```ts
type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type ExamListItem = {
  id: string;
  title: string;
  mode: "GENERAL_EXAM" | "MOCK" | "HOMEWORK";
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  durationMinutes: number | null;
  totalQuestions: number;
  createdAt: string | null;
  attempted: boolean | null;
  latestScore: number | null;
};

type MockScoreItem = {
  submissionId: string;
  examId: string;
  examTitle: string;
  totalQuestions: number;
  totalCorrect: number;
  correctCount: number;
  totalIncorrect: number;
  score: number;
  startedAt: string | null;
  submittedAt: string | null;
};
```
