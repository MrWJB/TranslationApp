const { execSync } = require('child_process');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const sql = "SELECT local_path FROM documents WHERE task_id=17 AND local_path LIKE '%testing%' LIMIT 5";
console.log(execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql}"`, { encoding: 'utf8' }));
