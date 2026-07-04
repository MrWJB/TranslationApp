const { execSync } = require('child_process');
const MYSQL_BIN = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const sql = "SELECT local_path FROM documents WHERE task_id=17 AND local_path LIKE '%beans.html'";
const cmd = `"${MYSQL_BIN}" -u root -p1234qwer -N -B translation_app -e "${sql}"`;
const rows = execSync(cmd, { encoding: 'utf8' }).trim().split('\n');
console.log('beans paths', rows);
const idx = new Map(rows.map((r) => [r.trim(), true]));
for (const c of ['spring-framework-reference/core/beans.html', 'core/beans.html']) {
  console.log(c, idx.has(c));
}
