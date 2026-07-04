const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const DOCS = path.join(__dirname, '../crawled-docs');

function q(sql) {
  return execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`, { encoding: 'utf8' }).trim();
}

console.log('=== Recent Spring Framework tasks ===');
console.log(q("SELECT id,status,max_pages,(SELECT COUNT(*) FROM documents d WHERE d.task_id=t.id),LEFT(url,60) FROM crawl_tasks t WHERE task_type='document' AND url LIKE '%spring-framework%' ORDER BY id DESC LIMIT 5"));

const id = process.argv[2] || q("SELECT id FROM crawl_tasks WHERE task_type='document' AND url LIKE '%spring-framework%' ORDER BY id DESC LIMIT 1");
console.log('\n=== Task', id, '===');

const raw = execSync(`"${M}" -u root -p1234qwer --skip-column-names --raw translation_app -e "SELECT table_of_contents FROM crawl_tasks WHERE id=${id}"`, { encoding: 'utf8', maxBuffer: 30 * 1024 * 1024 }).trim();
const toc = JSON.parse(raw);
const core = toc.find((x) => x.title === 'Core Technologies');
console.log('TOC top:', toc.length);
console.log('Core Technologies children count:', core?.children?.length);
console.log('Core children titles:', (core?.children || []).map((c) => c.title).slice(0, 15));
const coreApp = (core?.children || []).find((c) => c.title === 'Appendix');
console.log('Core > Appendix only?', (core?.children || []).length <= 2, 'children:', (core?.children || []).map((c) => c.title));

let html = 0;
const root = path.join(DOCS, 'spring-framework-reference');
if (fs.existsSync(root)) {
  const walk = (d) => { for (const e of fs.readdirSync(d)) { const p = path.join(d, e); if (fs.statSync(p).isDirectory()) walk(p); else if (e.endsWith('.html')) html++; } };
  walk(root);
}
console.log('Disk html spring-framework-reference:', html);
console.log('DB docs:', q(`SELECT COUNT(*) FROM documents WHERE task_id=${id}`));
