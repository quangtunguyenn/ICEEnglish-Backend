// Load test cho ICEEnglish: N học sinh (mặc định 1000) thao tác ĐỒNG THỜI.
// Chạy: node loadtest/loadtest.mjs [N=1000] [BASE=http://localhost:8081]
// Biến môi trường:
//   APP_PID    PID backend (Windows) để in CPU-ms backend tiêu tốn cho mỗi request.
//   ONLY       danh sách nhóm kịch bản, phân cách bằng dấu phẩy (mặc định: tất cả, trừ throttle).
//              Nhóm: video, exam, mock, general, homework, reading, guest, mixed, throttle.
//   SLO_SCALE  hệ số nhân ngưỡng p95 (mặc định 1; tăng khi chạy trên máy yếu / dùng chung với DB).
//   OUT_JSON   ghi kết quả JSON ra file này.
// Mỗi kịch bản bắn N request cùng lúc (mỗi học sinh 1 request), in p50/p95/p99/max, số lỗi và PASS/FAIL theo ngưỡng p95.
// Cần DB đã seed (loadtest/setup.ps1) và đã TRUNCATE sub_details, part_time_logs, submissions trước mỗi lần chạy (run.ps1).
import { Agent, setGlobalDispatcher } from 'undici';
import { execSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

const N = Number(process.argv[2] ?? 1000);
const BASE = process.argv[3] ?? 'http://localhost:8081';
const API = `${BASE}/api/v1`;
const SLO_SCALE = Number(process.env.SLO_SCALE ?? 1);
const ALL_GROUPS = ['video', 'exam', 'mock', 'general', 'homework', 'reading', 'guest', 'mixed'];
const groups = new Set((process.env.ONLY ?? ALL_GROUPS.join(',')).split(',').map((s) => s.trim()).filter(Boolean));
const enabled = (g) => groups.has(g);

setGlobalDispatcher(new Agent({
  connections: N + 50, pipelining: 1, headersTimeout: 180000, bodyTimeout: 180000,
  connectTimeout: 120000, connect: { timeout: 120000 },
}));

// Ngưỡng p95 (ms) cho 1000 request đồng thời trên một máy dùng chung với DB và công cụ tạo tải.
const SLO = { read: 3000, exam: 4000, start: 4000, track: 3000, submit: 10000, review: 5000, login: 30000, journey: 20000, burst: 5000 };

const results = [];
const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];

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

const RETRYABLE = new Set(['UND_ERR_CONNECT_TIMEOUT', 'ECONNRESET', 'ECONNREFUSED']);

// Trình duyệt tự thử lại khi mất kết nối; tính cả thời gian chờ thử lại vào độ trễ của request.
async function call(method, path, token, json, headers = {}) {
  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetch(`${API}${path}`, {
        method,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(json ? { 'Content-Type': 'application/json' } : {}),
          'Accept-Encoding': 'gzip',
          ...headers,
        },
        body: json ? JSON.stringify(json) : undefined,
      });
      const text = await res.text();
      return { status: res.status, body: text, bytes: text.length };
    } catch (e) {
      if (attempt >= 3 || !RETRYABLE.has(e.cause?.code)) throw e;
    }
  }
}

const json = (r) => JSON.parse(r.body);
const skipped = () => Promise.resolve({ status: 0, body: '' });

async function scenario(name, count, slo, makeTask) {
  const cpu0 = appCpu();
  const started = performance.now();
  const runs = await Promise.all(Array.from({ length: count }, (_, i) => timed(() => makeTask(i))));
  const wall = performance.now() - started;
  const cpuMs = cpu0 == null ? null : appCpu() - cpu0;
  const isOk = (r) => r.status >= 200 && r.status < 300;
  const ok = runs.filter(isOk);
  const sorted = ok.map((r) => r.ms).sort((a, b) => a - b);
  const errors = runs.length - ok.length;
  const errTypes = {};
  runs.filter((r) => !isOk(r)).forEach((r) => {
    const k = r.status || r.error;
    errTypes[k] = (errTypes[k] ?? 0) + 1;
  });
  const limit = Math.round(slo * SLO_SCALE);
  const p95 = sorted.length ? Math.round(pct(sorted, 95)) : null;
  const row = {
    name, n: count, ok: ok.length, errors, errTypes,
    p50: sorted.length ? Math.round(pct(sorted, 50)) : null,
    p95, p99: sorted.length ? Math.round(pct(sorted, 99)) : null,
    max: sorted.length ? Math.round(sorted[sorted.length - 1]) : null,
    wallMs: Math.round(wall), rps: Math.round(count / (wall / 1000)),
    avgBytes: ok.length ? Math.round(ok.reduce((s, r) => s + (r.bytes ?? 0), 0) / ok.length) : 0,
    cpuMsPerReq: cpuMs == null ? null : Number((cpuMs / count).toFixed(1)),
    slo: limit, pass: errors === 0 && p95 != null && p95 <= limit,
  };
  results.push(row);
  console.log(`${row.pass ? 'PASS' : 'FAIL'} ${name.padEnd(38)} n=${count} ok=${ok.length} err=${errors}${errors ? ' ' + JSON.stringify(errTypes) : ''}`
    + ` p50=${row.p50}ms p95=${p95}ms (<=${limit}) p99=${row.p99}ms max=${row.max}ms wall=${row.wallMs}ms ${row.rps}rps body~${row.avgBytes}B`
    + (row.cpuMsPerReq == null ? '' : ` appCPU/req=${row.cpuMsPerReq}ms`));
  return runs;
}

function check(name, pass, detail = '') {
  results.push({ name, check: true, pass });
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`);
}

const opts = ['A', 'B', 'C', 'D'];
const answersFor = (questions, i) => questions.map((q, k) => ({ questionId: q.id, selectedOpt: opts[(k + i) % 4] }));
const users = Array.from({ length: N }, (_, i) => `s${String(i + 1).padStart(4, '0')}`);
const tokens = new Array(N);
const auth = (i) => tokens[i];

// ---------------------------------------------------------------- 1. Đăng nhập đồng loạt
const loginRuns = await scenario('1 login (BCrypt)', N, SLO.login, async (i) => {
  const r = await call('POST', '/auth/login', null, { username: users[i], password: 'pass1234' });
  tokens[i] = r.body;
  return r;
});
if (loginRuns.some((r) => r.status !== 200)) console.log('! một số login lỗi, các bước sau chỉ chạy với token hợp lệ');

const videoIds = json(await call('GET', '/videos/titles', auth(0))).map((v) => v.video_id);
const examList = json(await call('GET', '/exams', auth(0)));
const typeOf = (e) => e.examType ?? e.exam_type;
const mock = examList.find((e) => typeOf(e) === 'MOCK');
const general = examList.find((e) => typeOf(e) === 'GENERAL_EXAM');
const homeworkVideo = videoIds[0];
const questionsOf = (dto) => (dto.parts ?? []).flatMap((p) => p.questions ?? []);

// ---------------------------------------------------------------- 2. Xem video / từ vựng / danh sách
if (enabled('video')) {
  await scenario('2a GET /videos/titles', N, SLO.read, (i) => call('GET', '/videos/titles', auth(i)));
  await scenario('2b GET /videos (toàn bộ)', N, SLO.read, (i) => call('GET', '/videos', auth(i)));
  await scenario('2c GET learning-content', N, SLO.read, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/learning-content`, auth(i)));
  await scenario('2d GET vocabularies', N, SLO.read, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/vocabularies`, auth(i)));
  await scenario('2e GET homework', N, SLO.read, (i) => call('GET', `/videos/${videoIds[i % videoIds.length]}/homework`, auth(i)));
}
if (enabled('exam')) {
  await scenario('2f GET /exams (danh sách đề)', N, SLO.read, (i) => call('GET', '/exams', auth(i)));
}

// ---------------------------------------------------------------- 3-6. Thi thử MOCK: tải đề, bắt đầu, ghi giờ theo part, nộp, xem lại
if (enabled('mock')) {
  let mockQuestions = [];
  await scenario('3 GET /exams/{mock} (tải đề)', N, SLO.exam, async (i) => {
    const r = await call('GET', `/exams/${mock.id}`, auth(i));
    if (i === 0 && r.status === 200) mockQuestions = questionsOf(json(r));
    return r;
  });
  console.log(`  (đề MOCK có ${mockQuestions.length} câu)`);

  const starts = new Array(N);
  await scenario('4 POST start MOCK', N, SLO.start, async (i) => {
    const r = await call('POST', `/submissions/start/${mock.id}`, auth(i));
    if (r.status === 200) starts[i] = json(r);
    return r;
  });
  const timeUrl = (i, k) => `/submissions/${starts[i].submissionId}/parts/${starts[i].parts[k].partId}/time`;
  await scenario('4b PUT part time (vào part 1)', N, SLO.track, (i) => (starts[i] ? call('PUT', timeUrl(i, 0), auth(i)) : skipped()));
  await scenario('4c PUT part time (chuyển part 2)', N, SLO.track, (i) => (starts[i] ? call('PUT', timeUrl(i, 1), auth(i)) : skipped()));
  await scenario('4d GET part-logs', N, SLO.read, (i) => (starts[i] ? call('GET', `/submissions/${starts[i].submissionId}/part-logs`, auth(i)) : skipped()));

  const submitBody = (i) => ({ submissionId: starts[i].submissionId, exam_id: mock.id, answers: answersFor(mockQuestions, i) });
  await scenario('5 POST submit MOCK (100 câu)', N, SLO.submit, (i) =>
    (starts[i] ? call('POST', '/submissions/submit', auth(i), submitBody(i)) : skipped()));
  await scenario('5b POST submit MOCK lặp (idempotent)', N, SLO.review, (i) =>
    (starts[i] ? call('POST', '/submissions/submit', auth(i), submitBody(i)) : skipped()));
  await scenario('6 GET review MOCK', N, SLO.review, (i) => call('GET', `/submissions/mock/${mock.id}/review`, auth(i)));
  await scenario('6b GET mock scores', N, SLO.read, (i) => call('GET', '/submissions/mock/scores', auth(i)));
  await scenario('6c GET answer-url', N, SLO.read, (i) => call('GET', `/submissions/exams/${mock.id}/answer-url`, auth(i)));
}

// ---------------------------------------------------------------- 7. Luyện đề GENERAL_EXAM
if (enabled('general')) {
  const gQuestions = questionsOf(json(await call('GET', `/exams/${general.id}`, auth(0))));
  const gStarts = new Array(N);
  await scenario('7a POST start GENERAL_EXAM', N, SLO.start, async (i) => {
    const r = await call('POST', `/submissions/start/${general.id}`, auth(i));
    if (r.status === 200) gStarts[i] = json(r);
    return r;
  });
  await scenario('7b POST submit GENERAL_EXAM', N, SLO.submit, (i) =>
    (gStarts[i] ? call('POST', '/submissions/submit', auth(i), {
      submissionId: gStarts[i].submissionId, exam_id: general.id, answers: answersFor(gQuestions, i),
    }) : skipped()));
  await scenario('7c GET practice latest-score', N, SLO.read, (i) => call('GET', `/submissions/practice/${general.id}/latest-score`, auth(i)));
  await scenario('7d GET practice history', N, SLO.read, (i) => call('GET', `/submissions/practice/${general.id}/history`, auth(i)));
  await scenario('7e GET practice latest-review', N, SLO.review, (i) => call('GET', `/submissions/practice/${general.id}/latest-review`, auth(i)));
}

// ---------------------------------------------------------------- 8. Bài về nhà
if (enabled('homework')) {
  const hwDto = json(await call('GET', `/videos/${homeworkVideo}/homework`, auth(0)));
  const hwStarts = new Array(N);
  await scenario('8a POST start HOMEWORK', N, SLO.start, async (i) => {
    const r = await call('POST', `/submissions/homework/start/${homeworkVideo}`, auth(i));
    if (r.status === 200) hwStarts[i] = json(r);
    return r;
  });
  await scenario('8b POST submit HOMEWORK', N, SLO.submit, (i) =>
    (hwStarts[i] ? call('POST', '/submissions/homework/submit', auth(i), {
      submissionId: hwStarts[i].submissionId, exam_id: hwDto.id, answers: answersFor(hwDto.questions ?? [], i),
    }) : skipped()));
  await scenario('8c GET homework latest-review', N, SLO.review, (i) => call('GET', `/submissions/homework/${hwDto.id}/latest-review`, auth(i)));
}

// ---------------------------------------------------------------- 9. Bài đọc
const readingBlogs = [];
if (enabled('reading') || enabled('mixed')) {
  const page = json(await call('GET', '/reading-blogs?size=100', auth(0)));
  const details = await Promise.all(page.items.map(async (b) => json(await call('GET', `/reading-blogs/${b.id}`, auth(0)))));
  details.forEach((d) => readingBlogs.push({ id: d.blog.id, revision: d.revision, questions: d.questions.map((q) => q.id) }));
}
const readingAnswer = (i, k = 0) => {
  const blog = readingBlogs[(i + k) % readingBlogs.length];
  return call('POST', `/reading-blogs/${blog.id}/questions/${blog.questions[(i + k) % blog.questions.length]}/answer`,
    auth(i), { revision: blog.revision, selectedOption: opts[(i + k) % 4] });
};
if (enabled('reading') && readingBlogs.length) {
  await scenario('9a GET reading topics', N, SLO.read, (i) => call('GET', '/reading-blogs/topics', auth(i)));
  await scenario('9b GET reading list', N, SLO.read, (i) => call('GET', `/reading-blogs?size=12&page=${i % 2}`, auth(i)));
  await scenario('9c GET reading list (lọc chủ đề)', N, SLO.read, (i) => call('GET', `/reading-blogs?topic=topic-${1 + (i % 4)}`, auth(i)));
  await scenario('9d GET reading detail (đọc bài)', N, SLO.read, (i) => call('GET', `/reading-blogs/${readingBlogs[i % readingBlogs.length].id}`, auth(i)));
  await scenario('9e GET reading vocabularies', N, SLO.read, (i) => call('GET', `/reading-blogs/${readingBlogs[i % readingBlogs.length].id}/vocabularies`, auth(i)));
  await scenario('9f POST reading answer (chấm từng câu)', N, SLO.read, (i) => readingAnswer(i));
  await scenario('9g POST reading answer x5/học sinh', N * 5, SLO.burst, (j) => readingAnswer(j % N, Math.floor(j / N)));
}

// ---------------------------------------------------------------- 10. Khách (không đăng nhập)
if (enabled('guest')) {
  // Mỗi phiên khách (đề + bài về nhà) tính vào giới hạn 1000 phiên của GuestService, nên mỗi loại tối đa 450.
  const G = Math.min(N, 450);
  const guestVideo = '11111111-1111-1111-1111-111111111111';
  const guestExams = json(await call('GET', '/guest/exams?mode=GENERAL_EXAM'));
  const gExam = guestExams[0];
  await scenario('10a guest GET videos', G, SLO.read, () => call('GET', '/guest/videos'));
  await scenario('10b guest GET exams', G, SLO.read, () => call('GET', '/guest/exams?mode=GENERAL_EXAM'));
  await scenario('10c guest GET learning-content', G, SLO.read, () => call('GET', `/guest/videos/${guestVideo}/learning-content`));
  await scenario('10d guest GET vocabularies', G, SLO.read, () => call('GET', `/guest/videos/${guestVideo}/vocabularies`));
  const tokensG = new Array(G);
  await scenario('10e guest POST start exam', G, SLO.start, async (i) => {
    const r = await call('POST', `/guest/exams/${gExam.id}/start`);
    if (r.status === 200) tokensG[i] = json(r);
    return r;
  });
  const gh = (i) => ({ 'X-Guest-Attempt': tokensG[i]?.submissionId });
  await scenario('10f guest GET exam detail', G, SLO.exam, (i) => (tokensG[i] ? call('GET', `/guest/exams/${gExam.id}`, null, null, gh(i)) : skipped()));
  await scenario('10g guest PUT part time', G, SLO.track, (i) =>
    (tokensG[i] ? call('PUT', `/guest/exams/${gExam.id}/parts/${tokensG[i].parts[0].partId}/time`, null, null, gh(i)) : skipped()));
  const guestQuestions = questionsOf(json(await call('GET', `/guest/exams/${gExam.id}`, null, null, gh(0))));
  await scenario('10h guest POST submit exam', G, SLO.submit, (i) =>
    (tokensG[i] ? call('POST', `/guest/exams/${gExam.id}/submit`, null, {
      submissionId: tokensG[i].submissionId, exam_id: gExam.id, answers: answersFor(guestQuestions, i),
    }, gh(i)) : skipped()));
  await scenario('10i guest POST homework start', G, SLO.start, () => call('POST', `/guest/videos/${guestVideo}/homework/start`));
  const guestReading = json(await call('GET', '/guest/reading-blogs?size=100')).items.filter((b) => b.accessible);
  if (guestReading.length) {
    await scenario('10j guest GET reading detail', G, SLO.read, (i) => call('GET', `/guest/reading-blogs/${guestReading[i % guestReading.length].id}`));
  }
}

// ---------------------------------------------------------------- 11. Hành trình hỗn hợp: mọi học sinh cùng làm nhiều việc một lúc
if (enabled('mixed') && readingBlogs.length) {
  const gQuestions = questionsOf(json(await call('GET', `/exams/${general.id}`, auth(0))));
  await scenario('11 hành trình hỗn hợp (luyện đề+đọc+video)', N, SLO.journey, async (i) => {
    const steps = [
      () => call('GET', '/exams', auth(i)),
      () => call('GET', `/videos/${videoIds[i % videoIds.length]}/learning-content`, auth(i)),
      () => call('GET', `/videos/${videoIds[i % videoIds.length]}/vocabularies`, auth(i)),
      () => call('GET', `/reading-blogs/${readingBlogs[i % readingBlogs.length].id}`, auth(i)),
      () => readingAnswer(i, 0),
      () => readingAnswer(i, 1),
      async () => {
        const start = await call('POST', `/submissions/start/${general.id}`, auth(i));
        if (start.status !== 200) return start;
        const s = json(start);
        await call('PUT', `/submissions/${s.submissionId}/parts/${s.parts[0].partId}/time`, auth(i));
        return call('POST', '/submissions/submit', auth(i), {
          submissionId: s.submissionId, exam_id: general.id, answers: answersFor(gQuestions, i),
        });
      },
      () => call('GET', `/submissions/practice/${general.id}/latest-score`, auth(i)),
    ];
    let last = { status: 200, body: '' };
    for (const step of steps) {
      last = await step();
      if (last.status < 200 || last.status >= 300) return last;
    }
    return last;
  });
}

// ---------------------------------------------------------------- 12. Chống dò mật khẩu (chạy khi ONLY=throttle)
if (enabled('throttle')) {
  const probe = `locktest-${Date.now()}`;
  const statuses = [];
  for (let k = 0; k < 14; k++) statuses.push((await call('POST', '/auth/login', null, { username: probe, password: 'wrong' })).status);
  check('12 đăng nhập sai nhiều lần bị tạm khoá (429)', statuses[statuses.length - 1] === 429, `statuses=${statuses.join(',')}`);
  const okLogin = await call('POST', '/auth/login', null, { username: users[0], password: 'pass1234' });
  check('12 tài khoản khác vẫn đăng nhập được', okLogin.status === 200);
}

const failed = results.filter((r) => !r.pass);
console.log(`\nTổng kết: ${results.length - failed.length}/${results.length} đạt${failed.length ? '; chưa đạt: ' + failed.map((r) => r.name).join(' | ') : ''}`);
if (process.env.OUT_JSON) writeFileSync(process.env.OUT_JSON, JSON.stringify(results, null, 2));
console.log('\nJSON:' + JSON.stringify(results));
process.exitCode = failed.length ? 1 : 0;
