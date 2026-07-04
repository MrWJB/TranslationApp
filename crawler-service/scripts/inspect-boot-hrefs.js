const axios = require('axios');
const cheerio = require('cheerio');
const sd = require('../src/structure-detector');
const { navHrefToStorageLocalPath, deriveSiteKey } = require('../src/site-key');

(async () => {
  const base = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/';
  const siteKey = deriveSiteKey(base);
  const r = await axios.get(base + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  const tree = sd.parseNavTree($, base + 'index.html', base, { type: 'antora' });

  const ref = tree.find((x) => x.title === 'Reference');
  console.log('Reference href:', ref?.href, 'children', ref?.children?.length);
  ref?.children?.slice(0, 5).forEach((c) => {
    console.log('  child', c.title, 'href', c.href, '-> storage', navHrefToStorageLocalPath(siteKey, c.href));
  });

  const tut = tree.find((x) => x.title === 'Tutorials');
  console.log('Tutorials href:', tut?.href, 'children', tut?.children?.length);
  tut?.children?.forEach((c) => console.log('  ', c.title, c.href, '->', navHrefToStorageLocalPath(siteKey, c.href)));

  const btp = tree.find((x) => x.title === 'Build Tool Plugins');
  btp?.children?.forEach((c) => console.log('BTP', c.title, c.href, '->', navHrefToStorageLocalPath(siteKey, c.href)));
})();
