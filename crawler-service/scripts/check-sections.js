const { execSync } = require('child_process');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
function q(sql) {
  return execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`, { encoding: 'utf8' }).trim();
}

const paths = [
  'spring-framework-reference/testing.html',
  'spring-framework-reference/integration.html',
  'spring-framework-reference/languages.html',
  'spring-framework-reference/testing/',
  'spring-framework-reference/integration/',
];
for (const p of paths) {
  console.log(p, '->', q(`SELECT local_path FROM documents WHERE task_id=17 AND local_path LIKE '%${p}%' LIMIT 3`) || '(none)');
}

console.log('\nmaxPages:', q('SELECT max_pages FROM crawl_tasks WHERE id=17'));
console.log('doc count:', q('SELECT COUNT(*) FROM documents WHERE task_id=17'));
