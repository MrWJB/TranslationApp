const { execSync } = require('child_process');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
function q(sql) {
  return execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`, { encoding: 'utf8' }).trim();
}
console.log(q("SELECT id,status,category,LEFT(url,70), (SELECT COUNT(*) FROM documents d WHERE d.task_id=t.id) FROM crawl_tasks t WHERE task_type='document' AND (url LIKE '%spring-boot%' OR category LIKE '%spring-boot%') ORDER BY id DESC LIMIT 5"));
