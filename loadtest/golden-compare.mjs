// So sánh hai file golden (xem golden.mjs). Bỏ qua thứ tự khoá JSON, thời gian máy chủ và các trường phụ thuộc
// việc xáo trộn part ngẫu nhiên; sắp xếp lại mảng để không phụ thuộc thứ tự. Dùng: node loadtest/golden-compare.mjs a.json b.json
import { readFileSync } from 'node:fs';

const IGNORED = new Set(['serverNow', 'partOrder', 'displayIndex', 'displayNumber', 'partDisplayIndex', 'timestamp']);

function normalize(value) {
  if (Array.isArray(value)) return value.map(normalize).map((v) => JSON.stringify(v)).sort();
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .filter(([key]) => !IGNORED.has(key))
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, v]) => [key, normalize(v)]));
  }
  return value;
}

const [fileA, fileB] = process.argv.slice(2);
const a = JSON.parse(readFileSync(fileA, 'utf8'));
const b = JSON.parse(readFileSync(fileB, 'utf8'));
const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
let diffs = 0;
for (const key of keys) {
  if (JSON.stringify(normalize(a[key])) !== JSON.stringify(normalize(b[key]))) {
    diffs++;
    console.log(`KHÁC ${key}: ${a[key]?.status} -> ${b[key]?.status}`);
  }
}
console.log(`${keys.size} response, ${diffs} khác biệt`);
process.exitCode = diffs ? 1 : 0;
