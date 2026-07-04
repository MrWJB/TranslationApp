const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const taskId = process.argv[2] || '18';
const raw = execSync(`"${M}" -u root -p1234qwer --skip-column-names --raw translation_app -e "SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}"`, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }).trim();
const toc = JSON.parse(raw);
console.log('top', toc.length, toc.map((x) => ({ title: x.title, ch: (x.children || []).length })));
const btp = toc.find((x) => (x.title || '').includes('Build Tool'));
console.log('Build Tool:', btp ? { title: btp.title, children: (btp.children || []).map((c) => c.title) } : 'NOT FOUND');
function find(nodes, kw) {
  for (const n of nodes || []) {
    if ((n.title || '').includes(kw)) return n;
    const f = find(n.children, kw);
    if (f) return f;
  }
  return null;
}
const maven = find(toc, 'Maven Plugin');
console.log('Maven Plugin path depth sample:', maven ? { title: maven.title, level: maven.level, parentHint: maven.localPath } : 'NOT FOUND');
