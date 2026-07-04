/**
 * Verify crawled-docs on disk vs DB document paths for recent tasks.
 * Run: node scripts/verify-tasks.js
 */
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DOCS_DIR = path.join(__dirname, '../crawled-docs');
const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const MYSQL_PWD = process.env.MYSQL_PASSWORD || '1234qwer';

function mysqlQuery(sql) {
  const cmd = `"${MYSQL_BIN}" -u root -p${MYSQL_PWD} -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`;
  try {
    const out = execSync(cmd, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
    return out.trim();
  } catch (e) {
    throw new Error(`MySQL query failed: ${e.stderr || e.message}`);
  }
}

function main() {
  console.log('=== 文档任务文件校验 ===\n');
  let anyFail = false;

  const taskLines = mysqlQuery(
    "SELECT t.id, t.status, IFNULL(t.category,''), LEFT(t.url,80), (SELECT COUNT(*) FROM documents d WHERE d.task_id=t.id) FROM crawl_tasks t WHERE t.task_type='document' ORDER BY t.id DESC LIMIT 10"
  ).split('\n').filter(Boolean);

  for (const line of taskLines) {
    const [id, status, category, url, docCount] = line.split('\t');
    const docLines = mysqlQuery(
      `SELECT local_path FROM documents WHERE task_id=${id} AND local_path IS NOT NULL LIMIT 500`
    ).split('\n').filter(Boolean);

    let present = 0;
    let missing = 0;
    const missingSample = [];
    for (const lp of docLines) {
      const clean = lp.replace(/\r/g, '').trim();
      const full = path.join(DOCS_DIR, clean.replace(/^\//, ''));
      if (fs.existsSync(full)) {
        present += 1;
      } else {
        missing += 1;
        if (missingSample.length < 3) missingSample.push(lp);
      }
    }
    const total = docLines.length;
    const ok = total > 0 && missing === 0;
    if (!ok && status === 'COMPLETED') anyFail = true;
    const icon = ok ? 'OK' : missing > 0 ? 'MISSING' : 'EMPTY';
    console.log(`Task ${id} [${icon}] ${category} | ${url}`);
    console.log(`  DB docs: ${docCount}, checked: ${total}, on disk: ${present}, missing: ${missing}`);
    if (missingSample.length) {
      console.log(`  missing sample: ${missingSample.join(', ')}`);
    }
    console.log('');
  }

  if (fs.existsSync(DOCS_DIR)) {
    console.log('=== crawled-docs 目录 ===');
    for (const dir of fs.readdirSync(DOCS_DIR)) {
      const full = path.join(DOCS_DIR, dir);
      if (!fs.statSync(full).isDirectory()) continue;
      let htmlCount = 0;
      const walk = (d) => {
        for (const e of fs.readdirSync(d)) {
          const p = path.join(d, e);
          if (fs.statSync(p).isDirectory()) walk(p);
          else if (e.endsWith('.html')) htmlCount += 1;
        }
      };
      walk(full);
      console.log(`  ${dir}/: ${htmlCount} html files`);
    }
  }

  process.exit(anyFail ? 1 : 0);
}

main();
