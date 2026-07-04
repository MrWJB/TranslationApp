/**
 * Compare stored TOC vs live Spring Antora nav for a task.
 * Run: node scripts/compare-toc-with-source.js 17
 */
const { execSync } = require('child_process');
const axios = require('axios');
const cheerio = require('cheerio');
const structureDetector = require('../src/structure-detector');
const { deriveSiteKey, navHrefToStorageLocalPath } = require('../src/site-key');

const MYSQL_BIN = process.env.MYSQL_BIN || 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
const taskId = process.argv[2] || '17';

function mysqlRaw(sql) {
  const cmd = `"${MYSQL_BIN}" -u root -p1234qwer --skip-column-names --raw translation_app -e "${sql.replace(/"/g, '\\"')}"`;
  return execSync(cmd, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }).trim();
}

function flattenNav(nodes, depth = 0, out = []) {
  for (const n of nodes || []) {
    out.push({
      title: n.title,
      href: n.href || n.localPath || '',
      depth,
      unavailable: n.available === false,
    });
    flattenNav(n.children, depth + 1, out);
  }
  return out;
}

function prefixNav(nodes, siteKey) {
  return (nodes || []).map((n) => ({
    ...n,
    href: n.href ? navHrefToStorageLocalPath(siteKey, n.href) : '',
    children: prefixNav(n.children, siteKey),
  }));
}

function diffTrees(sourceFlat, storedFlat) {
  const issues = [];
  const sourceTitles = sourceFlat.map((x) => x.title);
  const storedTitles = storedFlat.map((x) => x.title);

  // Top-level comparison
  const sourceTop = sourceFlat.filter((x) => x.depth === 0).map((x) => x.title);
  const storedTop = storedFlat.filter((x) => x.depth === 0).map((x) => x.title);
  const missingTop = sourceTop.filter((t) => !storedTop.includes(t));
  const extraTop = storedTop.filter((t) => !sourceTop.includes(t));
  if (missingTop.length) issues.push({ type: 'missing-top', items: missingTop });
  if (extraTop.length) issues.push({ type: 'extra-top', items: extraTop });

  // Depth mismatch for same title at same position context
  const minLen = Math.min(sourceFlat.length, storedFlat.length);
  let depthMismatches = 0;
  for (let i = 0; i < Math.min(50, minLen); i++) {
    if (sourceFlat[i]?.title === storedFlat[i]?.title && sourceFlat[i]?.depth !== storedFlat[i]?.depth) {
      depthMismatches++;
    }
  }

  // Title sequence comparison (ignoring availability)
  let titleMismatches = 0;
  const compareLen = Math.min(sourceFlat.length, storedFlat.length);
  for (let i = 0; i < compareLen; i++) {
    if (sourceFlat[i].title !== storedFlat[i].title) {
      titleMismatches++;
      if (titleMismatches <= 5) {
        issues.push({
          type: 'title-order',
          index: i,
          source: sourceFlat[i].title,
          stored: storedFlat[i].title,
        });
      }
    }
  }

  return {
    sourceCount: sourceFlat.length,
    storedCount: storedFlat.length,
    sourceTopLevel: sourceTop.length,
    storedTopLevel: storedTop.length,
    sourceTop,
    storedTop,
    missingTop,
    extraTop,
    depthMismatches,
    titleMismatches,
    compareLen,
    issues,
  };
}

async function main() {
  const baseUrl = mysqlRaw(`SELECT url FROM crawl_tasks WHERE id=${taskId}`).replace(/\/index\.html$/i, '/');
  const siteKey = deriveSiteKey(baseUrl);
  const tocJson = mysqlRaw(`SELECT table_of_contents FROM crawl_tasks WHERE id=${taskId}`);
  const storedToc = JSON.parse(tocJson);

  const indexUrl = baseUrl.endsWith('/') ? `${baseUrl}index.html` : `${baseUrl}/index.html`;
  const res = await axios.get(indexUrl, { headers: { 'User-Agent': 'Mozilla/5.0' }, timeout: 30000 });
  const $ = cheerio.load(res.data);
  let liveNav = structureDetector.parseNavTree($, indexUrl, baseUrl, { type: 'antora' });
  liveNav = prefixNav(liveNav, siteKey);

  const sourceFlat = flattenNav(liveNav);
  const storedFlat = flattenNav(storedToc);
  const report = diffTrees(sourceFlat, storedFlat);

  console.log('=== TOC vs Source Nav Comparison ===');
  console.log(`Task ${taskId} | ${baseUrl}`);
  console.log(`Source nav nodes: ${report.sourceCount} | Stored TOC nodes: ${report.storedCount}`);
  console.log(`Source top-level: ${report.sourceTopLevel} | Stored top-level: ${report.storedTopLevel}`);
  console.log('\nSource top-level titles:');
  report.sourceTop.forEach((t, i) => console.log(`  ${i + 1}. ${t}`));
  console.log('\nStored top-level titles:');
  report.storedTop.forEach((t, i) => console.log(`  ${i + 1}. ${t}`));
  if (report.missingTop.length) {
    console.log('\nMissing from stored TOC (present on source site):');
    report.missingTop.forEach((t) => console.log(`  - ${t}`));
  }
  if (report.extraTop.length) {
    console.log('\nExtra in stored TOC (not in source top-level):');
    report.extraTop.forEach((t) => console.log(`  - ${t}`));
  }

  if (report.titleMismatches) {
    console.log(`\nTitle order mismatches (first ${report.compareLen} nodes): ${report.titleMismatches}`);
    report.issues.filter((x) => x.type === 'title-order').forEach((x) => {
      console.log(`  @${x.index}: source="${x.source}" stored="${x.stored}"`);
    });
  }

  const unavailableCount = storedFlat.filter((x) => x.unavailable).length;
  if (unavailableCount) {
    console.log(`\nStored nodes not crawled (available=false): ${unavailableCount}`);
  }
  const findNode = (nodes, title) => (nodes || []).find((n) => n.title === title);
  const srcCore = findNode(liveNav, 'Core Technologies');
  const storedCore = findNode(storedToc, 'Core Technologies');
  console.log('\n--- Core Technologies children ---');
  console.log('Source:', (srcCore?.children || []).map((c) => c.title).join(' | '));
  console.log('Stored:', (storedCore?.children || []).map((c) => c.title).join(' | '));

  const srcBeans = findNode(srcCore?.children, 'The IoC Container');
  const storedBeans = findNode(storedCore?.children, 'The IoC Container');
  console.log('\n--- The IoC Container: first 5 children ---');
  console.log('Source:', (srcBeans?.children || []).slice(0, 5).map((c) => c.title));
  console.log('Stored:', (storedBeans?.children || []).slice(0, 5).map((c) => c.title));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
