/**
 * Simulate DocumentTocBuilder.buildFromCrawlerNav path matching for task 17.
 */
const { execSync } = require('child_process');
const sd = require('../src/structure-detector');
const axios = require('axios');
const cheerio = require('cheerio');
const { deriveSiteKey, navHrefToStorageLocalPath } = require('../src/site-key');

const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const siteKey = 'spring-framework-reference';
const baseUrl = 'https://docs.spring.io/spring-framework/reference/';

function prefixNavTree(nodes, key) {
  return nodes.map((node) => {
    const n = { ...node };
    if (n.href) n.href = navHrefToStorageLocalPath(key, n.href);
    if (n.children?.length) n.children = prefixNavTree(n.children, key);
    return n;
  });
}

function mysqlPaths(taskId) {
  const sql = `SELECT local_path, url FROM documents WHERE task_id=${taskId}`;
  const cmd = `"${MYSQL_BIN}" -u root -p1234qwer -N -B translation_app -e "${sql}"`;
  return execSync(cmd, { encoding: 'utf8' }).trim().split('\n').filter(Boolean);
}

function buildPathIndex(rows) {
  const index = new Map();
  for (const line of rows) {
    const [localPath, url] = line.split('\t');
    index.set(localPath, { localPath, url });
    index.set(url, { localPath, url });
  }
  return index;
}

function lookup(index, localPath) {
  if (!localPath) return null;
  return index.get(localPath) || null;
}

function buildEntry(node, index, depth = 0) {
  let localPath = node.href || '';
  const childNodes = node.children || [];
  const childEntries = [];
  for (const c of childNodes) {
    const ce = buildEntry(c, index, depth + 1);
    if (ce) childEntries.push(ce);
  }
  const source = localPath ? lookup(index, localPath) : null;
  if (!source && !childEntries.length) return null;
  return {
    title: node.title,
    localPath,
    matched: !!source,
    childCount: childEntries.length,
    children: childEntries,
  };
}

async function main() {
  const rows = mysqlPaths(17);
  const index = buildPathIndex(rows);
  const r = await axios.get(baseUrl + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  let navTree = sd.parseNavTree($, baseUrl + 'index.html', baseUrl, { type: 'antora' });
  navTree = prefixNavTree(navTree, siteKey);
  console.log('nav top', navTree.length);
  const core = navTree.find((x) => x.title === 'Core Technologies');
  console.log('core child hrefs', (core?.children || []).slice(0, 2).map((c) => c.href));

  const toc = [];
  for (const node of navTree) {
    const e = buildEntry(node, index);
    if (e) toc.push(e);
  }

  function stats(nodes, d = 0) {
    let max = d, total = 0, withCh = 0;
    for (const n of nodes) {
      total++;
      if (n.children?.length) {
        withCh++;
        const s = stats(n.children, d + 1);
        max = Math.max(max, s.max);
        total += s.total;
        withCh += s.withCh;
      }
    }
    return { max, total, withCh };
  }

  console.log('simulated top', toc.length, stats(toc));
  console.log('core tech entry', JSON.stringify(toc.find((x) => x.title === 'Core Technologies'), null, 2).slice(0, 1500));
}

main().catch((e) => { console.error(e); process.exit(1); });
