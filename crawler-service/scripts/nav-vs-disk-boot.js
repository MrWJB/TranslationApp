const axios = require('axios');
const cheerio = require('cheerio');
const sd = require('../src/structure-detector');
const { deriveSiteKey, navHrefToStorageLocalPath } = require('../src/site-key');
const { shouldExcludeUrl } = require('../src/profiles');
const fs = require('fs');
const path = require('path');

function collectHrefs(nodes, out = []) {
  for (const n of nodes || []) {
    if (n.href) out.push(n.href);
    collectHrefs(n.children, out);
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
  const base = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/';
  const siteKey = deriveSiteKey(base);
  const profile = require('../src/profiles/antora-spring');

  const r = await axios.get(base + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  let nav = prefixNav(sd.parseNavTree($, base + 'index.html', base, { type: 'antora' }), siteKey);
  const hrefs = collectHrefs(nav);

  const disk = new Set();
  const root = path.join(__dirname, '../crawled-docs/spring-boot-4-1-snapshot');
  const walk = (d, rel = '') => {
    for (const e of fs.readdirSync(d)) {
      const p = path.join(d, e);
      const r2 = rel ? `${rel}/${e}` : e;
      if (fs.statSync(p).isDirectory()) walk(p, r2);
      else if (e.endsWith('.html')) disk.add(`${siteKey}/${r2.replace(/\\/g, '/')}`);
    }
  };
  walk(root);

  let excluded = 0;
  let missing = 0;
  let found = 0;
  const missingSample = [];
  for (const href of hrefs) {
    const url = base + href.replace(/^spring-boot-4-1-snapshot\//, '').replace(/^[^/]+\//, ''); // rough
    if (shouldExcludeUrl(href, profile, base)) {
      excluded++;
      continue;
    }
    if (disk.has(href)) found++;
    else {
      missing++;
      if (missingSample.length < 15) missingSample.push(href);
    }
  }
  console.log('Nav hrefs:', hrefs.length, 'on disk:', found, 'missing:', missing, 'excluded by profile:', excluded);
  console.log('Missing sample:', missingSample);
})();
