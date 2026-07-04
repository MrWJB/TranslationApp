const { execSync } = require('child_process');
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const MYSQL_PWD = process.env.MYSQL_PASSWORD || '1234qwer';

function mysqlQuery(sql) {
  const cmd = `"${MYSQL_BIN}" -u root -p${MYSQL_PWD} -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`;
  return execSync(cmd, { encoding: 'utf8', maxBuffer: 50 * 1024 * 1024 }).trim();
}

const taskId = process.argv[2] || '22';
const tocRaw = mysqlQuery(`SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}`);
const data = JSON.parse(tocRaw);
let avail = 0, unavail = 0, ext = 0;
function walk(n) {
  for (const x of n) {
    if (x.available !== false) avail++;
    else unavail++;
    if (x.external) ext++;
    if (x.children) walk(x.children);
  }
}
walk(data);
console.log(`Task ${taskId}: top=${data.length} avail=${avail} unavail=${unavail} external=${ext}`);
const extNodes = [];
function findExt(n) {
  for (const x of n) {
    if (x.external || /Java API|Kotlin|Wiki/i.test(x.title)) {
      extNodes.push({ title: x.title, url: x.url, available: x.available, external: x.external });
    }
    if (x.children) findExt(x.children);
  }
}
findExt(data);
console.log('External-like nodes:', extNodes);
console.log('Docs:', mysqlQuery(`SELECT COUNT(*) FROM documents WHERE task_id=${taskId}`));
console.log('Sample doc:', mysqlQuery(`SELECT local_path,url FROM documents WHERE task_id=${taskId} LIMIT 3`));
