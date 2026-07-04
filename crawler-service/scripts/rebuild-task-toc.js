/**
 * Rebuild hierarchical table_of_contents for a completed crawl task.
 * Preserves full source nav hierarchy; marks uncrawled pages as available=false.
 * Run: node scripts/rebuild-task-toc.js 17
 */
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const cheerio = require('cheerio');
const structureDetector = require('../src/structure-detector');
const { deriveSiteKey, navHrefToStorageLocalPath } = require('../src/site-key');
const { buildTocFromNav, buildPathIndex, tocStats } = require('../src/nav-toc-builder');

const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const MYSQL_PWD = process.env.MYSQL_PASSWORD || '1234qwer';

function mysqlQuery(sql) {
  const cmd = `"${MYSQL_BIN}" -u root -p${MYSQL_PWD} -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`;
  return execSync(cmd, { encoding: 'utf8' }).trim();
}

function cleanField(value) {
  return (value || '').replace(/\r/g, '').trim();
}

function prefixNavTree(nodes, siteKey) {
  return (nodes || []).map((node) => {
    const n = { ...node };
    if (n.href) {
      n.href = navHrefToStorageLocalPath(siteKey, n.href);
    }
    if (n.children?.length) {
      n.children = prefixNavTree(n.children, siteKey);
    }
    return n;
  });
}

async function main() {
  const taskId = process.argv[2];
  if (!taskId) {
    console.error('Usage: node scripts/rebuild-task-toc.js <taskId>');
    process.exit(1);
  }

  const baseUrl = cleanField(
    mysqlQuery(`SELECT url FROM crawl_tasks WHERE id=${taskId} AND status='COMPLETED' LIMIT 1`)
  ).replace(/\/index\.html$/i, '/');
  if (!baseUrl) {
    console.error(`Task ${taskId} not found or not completed`);
    process.exit(1);
  }

  const siteKey = deriveSiteKey(baseUrl);
  const docLines = mysqlQuery(
    `SELECT id, title, url, local_path, IFNULL(translated_local_path,'') FROM documents WHERE task_id=${taskId}`
  ).split('\n').filter(Boolean);

  const docs = docLines.map((line) => {
    const [id, title, url, localPath, translatedLocalPath] = line.split('\t');
    return {
      id: Number(cleanField(id)),
      title: cleanField(title),
      url: cleanField(url),
      localPath: cleanField(localPath),
      translatedLocalPath: cleanField(translatedLocalPath) || null,
    };
  });

  const indexUrl = baseUrl.endsWith('/') ? `${baseUrl}index.html` : `${baseUrl}/index.html`;
  const res = await axios.get(indexUrl, { headers: { 'User-Agent': 'Mozilla/5.0' }, timeout: 30000 });
  const $ = cheerio.load(res.data);
  const isMysql = /dev\.mysql\.com\/doc\/refman/i.test(baseUrl);
  const navStructure = isMysql
    ? { type: 'mysql-refman' }
    : { type: 'antora' };
  let navTree = structureDetector.parseNavTree($, indexUrl, baseUrl, navStructure);
  navTree = prefixNavTree(navTree, siteKey);

  const pathIndex = buildPathIndex(docs, baseUrl);
  const toc = buildTocFromNav(navTree, pathIndex, baseUrl, { preserveFullNav: true });
  const stats = tocStats(toc);
  console.log(
    `Task ${taskId}: rebuilt TOC — top ${toc.length}, nodes ${stats.total}, maxDepth ${stats.max}, ` +
      `withChildren ${stats.withChildren}, unavailable ${stats.unavailable}`
  );

  const json = JSON.stringify(toc);
  const hex = Buffer.from(json, 'utf8').toString('hex').toUpperCase();
  const sqlFile = path.join(__dirname, `.toc-${taskId}.sql`);
  fs.writeFileSync(
    sqlFile,
    `UPDATE crawl_tasks SET table_of_contents=UNHEX('${hex}') WHERE id=${taskId};`,
    'utf8'
  );
  execSync(`"${MYSQL_BIN}" -u root -p${MYSQL_PWD} translation_app < "${sqlFile}"`, {
    stdio: 'inherit',
    shell: true,
  });
  fs.unlinkSync(sqlFile);
  console.log('Updated table_of_contents in database.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
