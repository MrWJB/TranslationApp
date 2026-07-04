const { execSync } = require('child_process');
const taskId = process.argv[2] || '17';
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const sql = `SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}`;
const cmd = `"${MYSQL_BIN}" -u root -p1234qwer -N -B translation_app -e "${sql}"`;
const out = execSync(cmd, { encoding: 'utf8' });
const toc = JSON.parse(out.trim());

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
console.log('first3 titles:', toc.slice(0, 3).map((n) => ({ title: n.title, childCount: (n.children || []).length })));
