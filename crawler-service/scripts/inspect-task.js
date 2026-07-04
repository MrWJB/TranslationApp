const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const DOCS = path.join(__dirname, '../crawled-docs');

function q(sql) {
  return execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`, { encoding: 'utf8' }).trim();
}

const id = process.argv[2] || '19';
console.log('=== Task', id, '===');
console.log(q(`SELECT id,status,max_pages,category,LEFT(url,80), LENGTH(IFNULL(table_of_contents,'')), quality_report FROM crawl_tasks WHERE id=${id}`));
const docCount = q(`SELECT COUNT(*) FROM documents WHERE task_id=${id}`);
console.log('DB documents:', docCount);

const siteKey = q(`SELECT DISTINCT SUBSTRING_INDEX(local_path,'/',1) FROM documents WHERE task_id=${id} LIMIT 1`);
if (siteKey) {
  const dir = path.join(DOCS, siteKey);
  let html = 0;
  if (fs.existsSync(dir)) {
    const walk = (d) => {
      for (const e of fs.readdirSync(d)) {
        const p = path.join(d, e);
        if (fs.statSync(p).isDirectory()) walk(p);
        else if (e.endsWith('.html')) html++;
      }
    };
    walk(dir);
  }
  console.log('Disk html under', siteKey + ':', html);
}

const tocLen = q(`SELECT LENGTH(table_of_contents) FROM crawl_tasks WHERE id=${id}`);
if (tocLen && tocLen !== 'NULL') {
  const raw = execSync(`"${M}" -u root -p1234qwer --skip-column-names --raw translation_app -e "SELECT table_of_contents FROM crawl_tasks WHERE id=${id}"`, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }).trim();
  try {
    const toc = JSON.parse(raw);
    function stats(n, d = 0) {
      let t = 0, c = 0, max = d;
      for (const x of n) {
        t++;
        if (x.children?.length) {
          c++;
          const s = stats(x.children, d + 1);
          t += s.t; c += s.c; max = Math.max(max, s.m);
        }
      }
      return { t, c, m: max };
    }
    const s = stats(toc);
    console.log('TOC top:', toc.length, 'nodes:', s.t, 'maxDepth:', s.m, 'withChildren:', s.c);
    console.log('Top titles:', toc.slice(0, 12).map((x) => x.title + '(' + (x.children?.length || 0) + ')').join(' | '));
    const btp = toc.find((x) => (x.title || '').includes('Build Tool'));
    console.log('Build Tool Plugins children:', (btp?.children || []).map((c) => c.title));
  } catch (e) {
    console.log('TOC parse error:', e.message);
  }
}
