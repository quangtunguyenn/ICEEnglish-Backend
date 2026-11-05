// Load test cho ICEEnglish. Chạy: node loadtest/loadtest.mjs [N=1000] [BASE=http://localhost:8081]
// Mỗi kịch bản bắn N request ĐỒNG THỜI (mỗi học sinh 1 request) rồi in p50/p95/p99/max và số lỗi.
// Cần DB đã seed bằng loadtest/seed.sql và đã TRUNCATE sub_details, part_time_logs, submissions trước mỗi lần chạy.
import { Agent, setGlobalDispatcher } from 'undici';
import { execSync } from 'node:child_process';

const N = Number(process.argv[2] ?? 1000);
const BASE = process.argv[3] ?? 'http://localhost:8081';
const API = `${BASE}/api/v1`;

setGlobalDispatcher(new Agent({
  connections: N + 50, pipelining: 1, headersTimeout: 120000, bodyTimeout: 120000,
  connect: { timeout: 60000 },
}));

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
const results = [];

// Tuỳ chọn: APP_PID = PID của backend (Windows) để in CPU-ms mà backend tiêu tốn cho mỗi request.
function appCpu() {
  if (!process.env.APP_PID) return null;
  try {
    const out = execSync(`powershell -NoProfile -Command "(Get-Process -Id ${process.env.APP_PID}).TotalProcessorTime.TotalMilliseconds"`,
      { encoding: 'utf8' });
    return Number(out.trim());
  } catch { return null; }
}

async function timed(fn) {
  const start = performance.now();
  try {
    const res = await fn();
    return { ms: performance.now() - start, status: res.status, body: res.body, bytes: res.bytes };
  } catch (e) {
    return { ms: performance.now() - start, status: 0, error: String(e.cause?.code ?? e.message) };
  }
}

async function call(method, path, token, json) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(json ? { 'Content-Type': 'application/json' } : {}),
      'Accept-Encoding': 'gzip',
    },
    body: json ? JSON.stringify(json) : undefined,
  });
  const text = await res.text();
  return { status: res.status, body: text, bytes: text.length };
}

async function scenario(name, count, makeTask) {
  const cpu0 = appCpu();
  const started = performance.now();
  const runs = await Promise.all(Array.from({ length: count }, (_, i) => timed(() => makeTask(i))));
  const wall = performance.now() - started;
  const cpuMs = appCpu() - cpu0;
  const ok = runs.filter((r) => r.status >= 200 && r.status < 300);
  const sorted = ok.map((r) => r.ms).sort((a, b) => a - b);
  const errors = runs.length - ok.length;
  const errTypes = {};
  runs.filter((r) => !(r.status >= 200 && r.status < 300)).forEach((r) => {
    const k = r.status || r.error;
    errTypes[k] = (errTypes[k] ?? 0) + 1;
  });
  const avgBytes = ok.length ? Math.round(ok.reduce((s, r) => s + (r.bytes ?? 0), 0) / ok.length) : 0;
  const row = {
    name, n: count, ok: ok.length, errors, errTypes,
    p50: sorted.length ? Math.round(pct(sorted, 50)) : null,
    p95: sorted.length ? Math.round(pct(sorted, 95)) : null,
    p99: sorted.length ? Math.round(pct(sorted, 99)) : null,
    max: sorted.length ? Math.round(sorted[sorted.length - 1]) : null,
    wallMs: Math.round(wall), avgBytes, cpuMsPerReq: cpuMs == null ? null : Number((cpuMs / count).toFixed(1)),
  };
  results.push(row);
  console.log(`${name.padEnd(34)} n=${count} ok=${ok.length} err=${errors}${errors ? ' ' + JSON.stringify(errTypes) : ''}`
    + ` p50=${row.p50}ms p95=${row.p95}ms p99=${row.p99}ms max=${row.max}ms wall=${row.wallMs}ms body~${avgBytes}B`
    + (row.cpuMsPerReq == null ? '' : ` appCPU/req=${row.cpuMsPerReq}ms`));
  return runs;
}

const users = Array.from({ length: N }, (_, i) => `s${String(i + 1).padStart(4, '0')}`);
const tokens = new Array(N);

// 1. Đăng nhập đồng loạt
const loginRuns = await scenario('1 login (BCrypt)', N, async (i) => {
  const r = await call('POST', '/auth/login', null, { username: users[i], password: 'pass1234' });
  tokens[i] = r.body;
  return r;
});
if (loginRuns.some((r) => r.status !== 200)) console.log('! một số login lỗi, các bước sau chỉ chạy với token hợp lệ');
const auth = (i) => tokens[i];

// Lấy danh sách video + id đề từ API
const list = JSON.parse((await call('GET', '/videos/titles', auth(0))).body);
const videoIds = list.map((v) => v.video_id);
const examList = JSON.parse((await call('GET', '/exams', auth(0))).body);
const mock = examList.find((e) => e.examType === 'MOCK' || e.exam_type === 'MOCK');
const general = examList.find((e) => (e.examType ?? e.exam_type) === 'GENERAL_EXAM');
const homeworkVideo = videoIds[0];

// 2. Xem video / từ vựng / danh sách
await scenario('2a GET /videos/titles', N, (i) => call('GET', '/videos/titles', auth(i)));
await scenario('2b GET learning-content', N, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/learning-content`, auth(i)));
await scenario('2c GET vocabularies', N, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/vocabularies`, auth(i)));
await scenario('2d GET homework', N, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/homework`, auth(i)));

// 3. Tải đề thi MOCK
let mockQuestions = [];
const examRuns = await scenario('3 GET /exams/{mock} (tải đề)', N, async (i) => {
  const r = await call('GET', `/exams/${mock.id}`, auth(i));
  if (i === 0 && r.status === 200) {
    const dto = JSON.parse(r.body);
    mockQuestions = (dto.parts ?? []).flatMap((p) => p.questions ?? []);
  }
  return r;
});
console.log(`  (đề MOCK có ${mockQuestions.length} câu)`);

// 4. Bắt đầu thi MOCK đồng loạt
const starts = new Array(N);
await scenario('4 POST start MOCK', N, async (i) => {
  const r = await call('POST', `/submissions/start/${mock.id}`, auth(i));
  if (r.status === 200) starts[i] = JSON.parse(r.body);
  return r;
});

// 5. Nộp bài MOCK đồng loạt (100 câu + partLogs)
const opts = ['A', 'B', 'C', 'D'];
await scenario('5 POST submit MOCK (100 câu)', N, (i) => {
  if (!starts[i]) return Promise.resolve({ status: 0, body: '' });
  return call('POST', '/submissions/submit', auth(i), {
    submissionId: starts[i].submissionId,
    exam_id: mock.id,
    answers: mockQuestions.map((q, k) => ({ questionId: q.id, selectedOpt: opts[(k + i) % 4] })),
    partLogs: (starts[i].parts ?? []).map((p) => ({ partId: p.partId, timeSpentSeconds: 120 })),
  });
});

// 6. Xem lại bài thi thử
await scenario('6 GET review MOCK', N, (i) => call('GET', `/submissions/mock/${mock.id}/review`, auth(i)));
await scenario('6b GET mock scores', N, (i) => call('GET', '/submissions/mock/scores', auth(i)));

// 7. Luyện đề GENERAL_EXAM: bắt đầu + nộp
const gStarts = new Array(N);
let gQuestions = [];
const gDto = JSON.parse((await call('GET', `/exams/${general.id}`, auth(0))).body);
gQuestions = (gDto.parts ?? []).flatMap((p) => p.questions ?? []);
await scenario('7a POST start GENERAL_EXAM', N, async (i) => {
  const r = await call('POST', `/submissions/start/${general.id}`, auth(i));
  if (r.status === 200) gStarts[i] = JSON.parse(r.body);
  return r;
});
await scenario('7b POST submit GENERAL_EXAM', N, (i) => {
  if (!gStarts[i]) return Promise.resolve({ status: 0, body: '' });
  return call('POST', '/submissions/submit', auth(i), {
    submissionId: gStarts[i].submissionId, exam_id: general.id,
    answers: gQuestions.map((q, k) => ({ questionId: q.id, selectedOpt: opts[(k + i) % 4] })),
  });
});

// 8. Bài về nhà: bắt đầu + nộp
const hwDto = JSON.parse((await call('GET', `/videos/${homeworkVideo}/homework`, auth(0))).body);
const hwStarts = new Array(N);
await scenario('8a POST start HOMEWORK', N, async (i) => {
  const r = await call('POST', `/submissions/homework/start/${homeworkVideo}`, auth(i));
  if (r.status === 200) hwStarts[i] = JSON.parse(r.body);
  return r;
});
await scenario('8b POST submit HOMEWORK', N, (i) => {
  if (!hwStarts[i]) return Promise.resolve({ status: 0, body: '' });
  return call('POST', '/submissions/homework/submit', auth(i), {
    submissionId: hwStarts[i].submissionId, exam_id: hwDto.id,
    answers: (hwDto.questions ?? []).map((q, k) => ({ questionId: q.id, selectedOpt: opts[(k + i) % 4] })),
  });
});

// 9. Ghi thời gian theo part (kịch bản xấu: mọi học sinh gửi 1 lần cùng lúc, trên lượt thi MOCK mới)
console.log('\nJSON:' + JSON.stringify(results));
