const { execSync } = require('child_process');
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const sql = 'SELECT local_path FROM documents WHERE task_id=17 LIMIT 8';
const cmd = `"${MYSQL_BIN}" -u root -p1234qwer -N -B translation_app -e "${sql}"`;
console.log(execSync(cmd, { encoding: 'utf8' }));
