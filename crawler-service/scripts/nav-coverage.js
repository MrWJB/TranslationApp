const { execSync } = require('child_process');
const axios = require('axios');
const cheerio = require('cheerio');
const sd = require('../src/structure-detector');
const { deriveSiteKey, navHrefToStorageLocalPath } = require('../src/site-key');

const M = 'D:\\software\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
function q(sql) {
  return execSync(`"${M}" -u root -p1234qwer -N -B translation_app -e "${sql.replace(/"/g, '\\"')}"`, { encoding: 'utf8' }).trim();
}

function collectUrls(nodes, baseUrl, out = []) {
  for (const n of nodes || []) {
    if (n.href) out.push(n.href);
    collectUrls(n.children, baseUrl, out);
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

(async () => {
  const base = 'https://docs.spring.io/spring-framework/reference/';
  const siteKey = deriveSiteKey(base);
  const r = await axios.get(base + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  let nav = prefixNav(sd.parseNavTree($, base + 'index.html', base, { type: 'antora' }), siteKey);
  const navPaths = collectUrls(nav).map((h) => `${siteKey}/${h.replace(/^[^/]+\//, h.includes('/') ? '' : '')}`);

  const allPaths = new Set(
    q('SELECT local_path FROM documents WHERE task_id=17').split('\n').filter(Boolean).map((p) => p.trim())
  );

  const navHrefs = collectUrls(nav);
  let missing = 0;
  let found = 0;
  const missingSample = [];
  for (const href of navHrefs) {
    const storage = navHrefToStorageLocalPath(siteKey, href);
    if (allPaths.has(storage)) found++;
    else {
      missing++;
      if (missingSample.length < 15) missingSample.push({ href, storage });
    }
  }
  console.log('Nav hrefs:', navHrefs.length, 'found on disk:', found, 'missing:', missing);
  console.log('Missing sample:', missingSample);

  // Where in nav order does crawl stop?
  let lastFound = '';
  let firstMissing = '';
  for (const href of navHrefs) {
    const storage = navHrefToStorageLocalPath(siteKey, href);
    if (allPaths.has(storage)) lastFound = storage;
    else if (!firstMissing) firstMissing = storage;
  }
  console.log('\nLast crawled in nav order:', lastFound);
  console.log('First missing in nav order:', firstMissing);
})();
