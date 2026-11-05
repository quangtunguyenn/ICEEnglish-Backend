// So sánh nội dung response của các API chính (để kiểm tra cache không làm đổi kết quả).
// Chạy: node loadtest/golden.mjs <outFile> [BASE]   (DB phải vừa TRUNCATE submissions, xem run.ps1)
import { writeFileSync } from 'node:fs';

const out = process.argv[2];
const API = `${process.argv[3] ?? 'http://localhost:8081'}/api/v1`;
const VOLATILE = new Set(['submissionId', 'startedAt', 'submittedAt', 'expiresAt', 'createdAt']);

async function call(method, path, token, json) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(json ? { 'Content-Type': 'application/json' } : {}) },
    body: json ? JSON.stringify(json) : undefined,
  });
  const text = await res.text();
  let body = text;
  try { body = JSON.parse(text); } catch { /* giữ nguyên text */ }
  return { status: res.status, body };
}

function strip(v) {
  if (Array.isArray(v)) return v.map(strip);
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).filter(([k]) => !VOLATILE.has(k)).map(([k, x]) => [k, strip(x)]));
  }
  return v;
}

const log = {};
const rec = (name, r) => { log[name] = { status: r.status, body: strip(r.body) }; return r; };

const token = (await call('POST', '/auth/login', null, { username: 's0001', password: 'pass1234' })).body;
const titles = rec('titles', await call('GET', '/videos/titles', token)).body;
const v0 = titles[0].video_id;
rec('videos', await call('GET', '/videos', token));
rec('learning-content', await call('GET', `/videos/${v0}/learning-content`, token));
const hw = rec('homework', await call('GET', `/videos/${v0}/homework`, token)).body;
rec('homework-answers', await call('GET', `/videos/${v0}/homework/answers`, token));
rec('vocabularies', await call('GET', `/videos/${v0}/vocabularies`, token));
rec('document', await call('GET', `/videos/${v0}/document`, token));
rec('video-404', await call('GET', `/videos/00000000-0000-0000-0000-000000000000/homework`, token));
const exams = rec('exams', await call('GET', '/exams', token)).body;
rec('exams-mock-filter', await call('GET', '/exams?mode=MOCK', token));
const byType = (t) => exams.find((e) => (e.examType ?? e.exam_type) === t);
const mock = byType('MOCK'), general = byType('GENERAL_EXAM');
const mockDto = rec('exam-mock', await call('GET', `/exams/${mock.id}`, token)).body;
rec('exam-404', await call('GET', `/exams/00000000-0000-0000-0000-000000000000`, token));

const opts = ['A', 'B', 'C', 'D'];
const answersFor = (qs) => qs.map((q, k) => ({ questionId: q.id, selectedOpt: k % 7 === 0 ? null : opts[k % 4] }));
const mockQs = mockDto.parts.flatMap((p) => p.questions);

const mStart = rec('mock-start', await call('POST', `/submissions/start/${mock.id}`, token)).body;
rec('mock-start-again', await call('POST', `/submissions/start/${mock.id}`, token));
const mSubmit = {
  submissionId: mStart.submissionId, exam_id: mock.id, answers: answersFor(mockQs),
  partLogs: mStart.parts.map((p, i) => ({ partId: p.partId, timeSpentSeconds: 60 + i })),
};
rec('mock-submit', await call('POST', '/submissions/submit', token, mSubmit));
rec('mock-submit-replay', await call('POST', '/submissions/submit', token, mSubmit));
rec('mock-review', await call('GET', `/submissions/mock/${mock.id}/review`, token));
rec('mock-scores', await call('GET', '/submissions/mock/scores', token));
rec('mock-start-after-submit', await call('POST', `/submissions/start/${mock.id}`, token));
rec('part-logs', await call('GET', `/submissions/${mStart.submissionId}/part-logs`, token));
rec('answer-url', await call('GET', `/submissions/exams/${mock.id}/answer-url`, token));
rec('submit-wrong-exam', await call('POST', '/submissions/submit', token, { ...mSubmit, exam_id: general.id }));

const gDto = (await call('GET', `/exams/${general.id}`, token)).body;
const gStart = rec('general-start', await call('POST', `/submissions/start/${general.id}`, token)).body;
rec('general-submit', await call('POST', '/submissions/submit', token, {
  submissionId: gStart.submissionId, exam_id: general.id, answers: answersFor(gDto.parts.flatMap((p) => p.questions)),
}));
rec('general-latest-score', await call('GET', `/submissions/practice/${general.id}/latest-score`, token));

const hStart = rec('homework-start', await call('POST', `/submissions/homework/start/${v0}`, token)).body;
rec('homework-submit', await call('POST', '/submissions/homework/submit', token, {
  submissionId: hStart.submissionId, exam_id: hw.id, answers: answersFor(hw.questions),
}));
rec('homework-submit-not-homework', await call('POST', '/submissions/homework/submit', token, {
  submissionId: mStart.submissionId, exam_id: mock.id, answers: [],
}));

writeFileSync(out, JSON.stringify(log, null, 2));
console.log(`ghi ${Object.keys(log).length} response vào ${out}`);
