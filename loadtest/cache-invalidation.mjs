// Kiểm tra cache được xoá đúng khi staff thao tác. Chạy: node loadtest/cache-invalidation.mjs [BASE]
// Lưu ý: script archive đề GENERAL_EXAM và tạo thêm 1 video trong DB load test (dùng DB iceenglish_loadtest).
import { readFileSync } from 'node:fs';

const API = `${process.argv[2] ?? 'http://localhost:8081'}/api/v1`;
async function call(method, path, token, body) {
  const res = await fetch(`${API}${path}`, { method, headers: token ? { Authorization: `Bearer ${token}` } : {}, body });
  const text = await res.text();
  let json = text; try { json = JSON.parse(text); } catch { /* text */ }
  return { status: res.status, body: json };
}
const login = async (u) => (await call('POST', '/auth/login', null, undefined) , (await fetch(`${API}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: 'pass1234' }) }).then((r) => r.text())));

let failures = 0;
const check = (name, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${name} ${extra}`); if (!ok) failures++; };

const admin = await login('admin');
const student = await login('s0002');

const exams = (await call('GET', '/exams', student)).body;
const general = exams.find((e) => (e.examType ?? e.exam_type) === 'GENERAL_EXAM');
const revealedKey = (dto) => dto.answersRevealed ?? dto.is_answers_revealed ?? dto.answers_revealed;

// 1. toggle answers-revealed phải thấy ngay trên API đề của học sinh (đã được cache)
const before = (await call('GET', `/exams/${general.id}`, student)).body;
await call('GET', `/exams/${general.id}`, student); // chắc chắn đã nằm trong cache
const t1 = await call('PATCH', `/exams/${general.id}/answers-revealed`, admin);
const after = (await call('GET', `/exams/${general.id}`, student)).body;
check('toggle answers-revealed -> đề học sinh cập nhật ngay', t1.status === 200 && revealedKey(before) !== revealedKey(after),
  `(${revealedKey(before)} -> ${revealedKey(after)})`);
await call('PATCH', `/exams/${general.id}/answers-revealed`, admin);

// 2. toggle homework -> API homework/learning-content của video cập nhật ngay
const titles = (await call('GET', '/videos/titles', student)).body;
const v = titles[1].video_id;
const hw1 = (await call('GET', `/videos/${v}/homework`, student)).body;
await call('GET', `/videos/${v}/learning-content`, student);
await call('PATCH', `/exams/${hw1.id}/answers-revealed`, admin);
const hw2 = (await call('GET', `/videos/${v}/homework`, student)).body;
const lc2 = (await call('GET', `/videos/${v}/learning-content`, student)).body;
check('toggle homework -> /homework cập nhật ngay', revealedKey(hw1) !== revealedKey(hw2));
check('toggle homework -> /learning-content cập nhật ngay', revealedKey(lc2.homework) === revealedKey(hw2));
await call('PATCH', `/exams/${hw1.id}/answers-revealed`, admin);

// 3. tạo bundle mới -> danh sách video / titles thấy ngay
const n0 = (await call('GET', '/videos/titles', student)).body.length;
const list0 = (await call('GET', '/videos', student)).body.length;
const form = new FormData();
const bundle = JSON.parse(readFileSync(new URL('../samples/learning-bundle-sample.json', import.meta.url), 'utf8'));
bundle.video.title = 'Cache test ' + Date.now();
form.append('file', new Blob([JSON.stringify(bundle)], { type: 'application/json' }), 'bundle.json');
const created = await call('POST', '/videos/learning-bundle', admin, form);
const n1 = (await call('GET', '/videos/titles', student)).body.length;
const list1 = (await call('GET', '/videos', student)).body.length;
check('tạo bundle -> /videos/titles và /videos thấy video mới ngay', created.status < 300 && n1 === n0 + 1 && list1 === list0 + 1,
  `(status ${created.status}, titles ${n0}->${n1}, videos ${list0}->${list1})`);

// 4. archive đề -> học sinh không còn thấy/không bắt đầu được, dù đề đã nằm trong cache
await call('GET', `/exams/${general.id}`, student);
await call('POST', `/submissions/start/${general.id}`, student); // nạp snapshot vào cache
const arch = await call('PATCH', `/exams/${general.id}/archive`, admin);
const getAfter = await call('GET', `/exams/${general.id}`, student);
const startAfter = await call('POST', `/submissions/start/${general.id}`, await login('s0003'));
check('archive -> học sinh GET đề nhận 404', arch.status < 300 && getAfter.status === 404, `(archive ${arch.status}, get ${getAfter.status})`);
check('archive -> học sinh không bắt đầu thi được', startAfter.status >= 400, `(start ${startAfter.status})`);
const listAfter = (await call('GET', '/exams', student)).body;
check('archive -> danh sách đề không còn đề đã lưu trữ', !listAfter.some((e) => e.id === general.id));

console.log(failures === 0 ? '\nTẤT CẢ ĐẠT' : `\n${failures} KIỂM TRA THẤT BẠI`);
process.exit(failures ? 1 : 0);
