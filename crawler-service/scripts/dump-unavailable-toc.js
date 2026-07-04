const { execSync } = require('child_process');
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const MYSQL_PWD = process.env.MYSQL_PASSWORD || '1234qwer';

function mysqlQuery(sql) {
  const cmd = `"${MYSQL_BIN}" -u root -p${MYSQL_PWD} -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`;
  return execSync(cmd, { encoding: 'utf8' }).trim();
}

const taskId = process.argv[2] || '18';
const raw = mysqlQuery(`SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}`);
const toc = JSON.parse(raw);

const roots = ['Rest APIs', 'Java APIs', 'Kotlin APIs', 'Specifications', 'Appendix'];
for (const name of roots) {
  const node = toc.find((n) => n.title === name);
  if (!node) continue;
  console.log(`\n=== ${name} ===`);
  console.log(JSON.stringify({
    available: node.available,
    unavailableReason: node.unavailableReason,
    childrenSample: (node.children || []).slice(0, 2).map((c) => ({
      title: c.title,
      available: c.available,
      unavailableReason: c.unavailableReason,
      external: c.external,
    })),
  }, null, 2));
}
