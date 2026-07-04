const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const taskId = process.argv[2] || '17';
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const outFile = path.join(__dirname, `toc-${taskId}.json`);
const cmd = `"${MYSQL_BIN}" -u root -p1234qwer --skip-column-names --raw translation_app -e "SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}"`;
const raw = execSync(cmd, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 });
fs.writeFileSync(outFile, raw.trim(), 'utf8');
const toc = JSON.parse(raw.trim());

function stats(nodes, depth = 0) {
  let maxDepth = depth;
  let withChildren = 0;
  let total = 0;
  for (const n of nodes) {
    total++;
    const ch = n.children || [];
    if (ch.length) {
      withChildren++;
      const s = stats(ch, depth + 1);
      maxDepth = Math.max(maxDepth, s.maxDepth);
      total += s.total;
      withChildren += s.withChildren;
    }
  }
  return { maxDepth, withChildren, total };
}

const s = stats(toc);
console.log('topLevel:', toc.length);
console.log('stats:', s);
const core = toc.find((n) => n.title === 'Core Technologies');
console.log('Core Technologies children:', (core?.children || []).length);
