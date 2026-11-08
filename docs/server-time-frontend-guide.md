# Frontend guide: server-owned time and part tracking

This guide describes the backend time changes and the API contract the frontend should use for practice (`GENERAL_EXAM`), mock exams (`MOCK`), and homework (`HOMEWORK`).

## Rules frontend must follow

- The backend server is the authority for business time. Never send a client-generated start time, submit time, expiry time, or elapsed duration for the backend to trust.
- Server timestamps use `Asia/Ho_Chi_Minh` and include the `+07:00` offset, for example `2026-11-07T17:00:00+07:00`.
- Use `startedAt` and `expiresAt` returned by the start API. They describe the server-created attempt; do not generate replacements in the browser.
- A browser countdown is informational only. The backend still decides whether an attempt is expired. Do not use the student's wall clock (`Date.now()`) to authorize submit or extend an attempt.
- To display a timestamp, parse the ISO-8601 value as an instant and format it in `Asia/Ho_Chi_Minh`.
- Elapsed time per part is now calculated by the backend from server timestamps. The frontend signals the active part; it no longer reports how many seconds were spent there.

## API flow

Every endpoint below requires the student's bearer token:

```http
Authorization: Bearer <accessToken>
```

### 1. Start or resume an attempt

```http
POST /api/v1/submissions/start/{examId}
```

No request body. The backend chooses the mode and time limit from the exam.

Response example:

```json
{
  "submissionId": "submission-uuid",
  "examId": "exam-uuid",
  "mode": "MOCK",
  "totalQuestions": 40,
  "timeLimitMinutes": 50,
  "serverNow": "2026-11-07T17:00:02+07:00",
  "startedAt": "2026-11-07T17:00:00+07:00",
  "expiresAt": "2026-11-07T17:50:00+07:00",
  "parts": [
    {
      "partId": "part-uuid",
      "partName": "Part 1",
      "orderIndex": 1,
      "recommendedTimeSeconds": 600
    }
  ]
}
```

- Store `submissionId` for all later operations.
- `serverNow` is the server clock captured while building the response. Frontend can use it with a monotonic timer to draw a closer countdown without trusting the local wall clock.
- MOCK attempts have a server-calculated `expiresAt`. Practice (`GENERAL_EXAM`) and homework may have `expiresAt: null`.
- If an unfinished attempt already exists, the backend may return that existing attempt and its original `startedAt`/`expiresAt`. Do not reset the local attempt clock when receiving it.
- If an attempt is expired, only the backend can determine that; it can reject tracking or submission.

### 2. Tell the backend which MOCK or practice part is active

```http
PUT /api/v1/submissions/{submissionId}/parts/{partId}/time
```

No request body. Example:

```ts
async function setActivePart(
  submissionId: string,
  partId: string,
  accessToken: string,
): Promise<void> {
  const response = await fetch(
    `/api/v1/submissions/${submissionId}/parts/${partId}/time`,
    {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  );

  if (!response.ok) {
    throw new Error(`Could not change active part (${response.status})`);
  }
}
```

Call once when the student enters a part and each time they switch to a different part. Do not call every second or send `timeSpentSeconds`.

- A repeated call for the already-active part does not reset its timer.
- When changing parts, the server closes the previous part interval and adds its elapsed seconds to that part's saved log.
- This endpoint supports `MOCK` and `GENERAL_EXAM` submissions. Homework does not support part-time tracking.
- The attempt must belong to the authenticated student, be in progress, not be expired, and the part must belong to its exam.
- If this request fails, show/retry the error; do not silently assume that server tracking started. The user can still continue the UI, but the backend will not have a reliable active-part interval until it accepts a call.

### 3. Submit the attempt

```http
POST /api/v1/submissions/submit
Content-Type: application/json
```

Example request:

```json
{
  "submissionId": "submission-uuid",
  "exam_id": "exam-uuid",
  "answers": [
    { "questionId": "question-uuid-1", "selectedOpt": "A" },
    { "questionId": "question-uuid-2", "selectedOpt": null }
  ]
}
```

The backend stamps `submittedAt`, grades the answers, and closes/records the active part using server time. Do not send `startedAt`, `submittedAt`, or client-computed part durations.

For MOCK, the backend rejects submission after `expiresAt` plus the configured 30-second submit grace period. The grace period is for request/network delay; it is not additional time the frontend should promise to students. For practice and homework without a time limit, the attempt is not subject to that MOCK expiry.

Older clients may still send `partLogs`, but the server ignores their `timeSpentSeconds`. Remove `partLogs` from new client requests.

### 4. Read saved part logs

```http
GET /api/v1/submissions/{submissionId}/part-logs
```

Returns the saved per-part durations:

```json
[
  {
    "partId": "part-uuid",
    "partName": "Part 1",
    "timeSpentSeconds": 420
  }
]
```

The active part is only flushed when the student switches to another part or submits. Therefore, while an attempt is in progress, this endpoint does not include the time elapsed in the currently active part yet. After successful submission, the final part interval is included.

### 5. Display the result

Submission and review responses contain `startedAt` and `submittedAt` as ISO-8601 timestamps with Vietnam offset:

```json
{
  "startedAt": "2026-11-07T17:00:00+07:00",
  "submittedAt": "2026-11-07T17:42:10+07:00",
  "partTimeLogs": [
    {
      "partId": "part-uuid",
      "partName": "Part 1",
      "timeSpentSeconds": 420
    }
  ]
}
```

Practice history also returns `submittedAt` with `+07:00`. Use these server-returned values for history and result screens.

## Frontend timer suggestion

The server is authoritative, but a client countdown can be rendered from the server-provided `expiresAt`. To avoid dependence on changes to the student's wall clock after the response arrives, anchor the remaining duration to a monotonic timer such as `performance.now()`:

```ts
const serverNowMs = Date.parse(startResponse.serverNow);
const expiresAtMs = Date.parse(startResponse.expiresAt);
const receivedAtMonotonicMs = performance.now();
const initialRemainingMs = Math.max(0, expiresAtMs - serverNowMs);

function getDisplayRemainingMs(): number {
  return Math.max(
    0,
    initialRemainingMs - (performance.now() - receivedAtMonotonicMs),
  );
}
```

This is only a display countdown. It starts from server-provided `serverNow` and advances using a monotonic browser timer, so it does not depend on the student's wall clock after the response arrives. Network delay may still make the display a little behind; it cannot change the server's expiry decision. The frontend must handle a rejected submit.

To format timestamps consistently:

```ts
function formatVietnamTime(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
}
```

## Errors to handle

| HTTP | Meaning | Frontend action |
|---:|---|---|
| `400` | Invalid request, invalid part ownership, or other `IllegalArgumentException` | Show validation error; do not mark the part as tracked |
| `401` | Missing/invalid/expired authentication token | Run the login refresh flow |
| `403` | User is not allowed to perform this operation | Show the permission error |
| `5xx` | The current submission exception handler does not map `IllegalStateException` to a client status; this currently includes wrong mode, already submitted, and expired attempts | Stop the timer UI, show a generic retry/unavailable message, and do not infer server state from the local clock |

The backend currently maps `IllegalArgumentException` to `400`; attempt-not-found also follows this path. Domain errors thrown as `IllegalStateException` are not yet normalized by the submission exception handler. Do not rely on a specific machine-readable error code for those cases.

## Rollout note

Backend stores the active part and server-recorded start time in the submission row. Apply database migration `V5__Add_server_part_timing_to_submissions.sql` before deploying the backend version that uses server-owned part timing. No migration needs to be run by the frontend team.
