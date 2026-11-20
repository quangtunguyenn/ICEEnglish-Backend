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
      "displayIndex": 1,
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
- Elapsed seconds are whole server-clock seconds, summed across all intervals spent in the part. A recorded interval shorter than one second is `0`; `null` means no recorded/recoverable interval.
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

Returns all identifiable parts in the attempt's order, including parts without
recorded time. It is scoped to the authenticated student's submission:

```json
[
  {
    "partId": "part-uuid",
    "partName": "Part 1",
    "displayIndex": 1,
    "timeSpentSeconds": 420,
    "recommendedTimeSeconds": 600
  },
  {
    "partId": "part-2-uuid",
    "partName": "Part 2",
    "displayIndex": 2,
    "timeSpentSeconds": null,
    "recommendedTimeSeconds": null
  }
]
```

The response fields are:

- `partId` and `partName`: identify the part.
- `displayIndex`: position in this attempt, including shuffled attempts.
- `timeSpentSeconds`: summed server-recorded elapsed seconds; `null` if unknown or not recorded.
- `recommendedTimeSeconds`: the recommendation snapshotted for this attempt; `null` if missing or not recoverable. Editing the exam later does not change the snapshot.

While the attempt is in progress, the active part's returned time includes the
elapsed interval up to the API request. On submission, the server closes the
active interval at the server's receipt time. Repeated reads after submission
return the same final values.

The server cannot detect a hidden/closed tab or a client connection loss. The
active interval therefore continues through those gaps and is assigned to the
active part when the next accepted part event or submission arrives. This is
server elapsed time, not a measure of focused study time. A failed tracking
request is not inferred or reconstructed.

Older attempts can return `null` for `recommendedTimeSeconds` because no
per-attempt snapshot exists. Missing times are not replaced with `0`.

The guest endpoint
`GET /api/v1/guest/exams/{examId}/part-logs` returns the same fields and uses
`X-Guest-Attempt: <submissionId>`; it validates the attempt against the exam.
Guest attempts are held in server memory and do not provide durable history.

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
      "displayIndex": 1,
      "timeSpentSeconds": 420,
      "recommendedTimeSeconds": 600
    }
  ]
}
```

Practice history also returns `submittedAt` with `+07:00`. Use these server-returned values for history and result screens.

Homework currently has no parts and does not support per-part timing; its
`partTimeLogs` remains empty.

## Show time compared with the recommendation

Use `partId` to match logs to parts and `displayIndex` to order them for this
attempt. Do not rely on the exam's original part order because parts may be
shuffled.

When both `timeSpentSeconds` and `recommendedTimeSeconds` are non-null, show
the actual duration, the recommended duration, and their difference. For
example, `280` seconds versus `240` seconds can be displayed as:

> 4 min 40 sec / recommended 4 min - 40 sec longer

If actual time is shorter, describe it as "X sec shorter"; if equal, say it
matches the recommendation. This comparison describes time only: do not infer
that a student did well, poorly, quickly, or inaccurately from it.

If either value is `null`, do not calculate or display a comparison. A real
`timeSpentSeconds: 0` is different from `null` and should remain zero. Format
durations from integer seconds (for example, `280` becomes `4 min 40 sec`); do not
round them to whole minutes before calculating the difference.

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
| `409` | The attempt is expired or already submitted, the student already used their mock attempt, or the requested tracking mode is unsupported | Show the server's message and refresh the attempt state |
| `5xx` | An unexpected error occurred | Show a generic retry/unavailable message; do not infer server state from the local clock |

The backend maps `IllegalArgumentException` to `400`; attempt-not-found also follows this path. Only known attempt-state conflicts return `409`; unexpected server state errors remain `5xx`. Do not rely on a specific machine-readable error code for those cases.

## Rollout note

Backend stores the active part and server-recorded start time in the submission
row. Apply `V5__Add_server_part_timing_to_submissions.sql` and
`V16__Add_part_recommendation_snapshots.sql` before deploying the backend
version that uses server-owned part timing and recommendation snapshots. These
are backend database migrations; the frontend team does not need to run them.
