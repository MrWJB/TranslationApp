const cheerio = require('cheerio');
const { fetchHtml } = require('../src/fetch-html');
const structureDetector = require('../src/structure-detector');
const { detectProfile } = require('../src/profiles');

const url = process.argv[2] || 'https://docs.spring.io/spring-data/commons/reference/4.2-SNAPSHOT/';

(async () => {
  const html = await fetchHtml(url);
  const $ = cheerio.load(html);
  const profile = detectProfile(url);
  const structure = profile.structure;
  const navUrls = [];
  $('nav.nav-menu a[href], nav.nav-menu a[data-path]').each((_, el) => {
    const h = $(el).attr('data-path') || $(el).attr('href');
    if (h && h.endsWith('.html') && !h.startsWith('http')) navUrls.push(h);
  });
  const navTree = structureDetector.parseNavTree($, url, url, structure);
  const treeUrls = [];
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.href) treeUrls.push(n.href);
      if (n.url) treeUrls.push(n.url);
      walk(n.children);
    }
  };
  walk(navTree);
  console.log('navUrls count:', navUrls.length);
  console.log('treeUrls count:', treeUrls.length);
  console.log('vector-search in navUrls:', navUrls.includes('repositories/vector-search.html'));
  console.log('vector-search in treeUrls:', treeUrls.some((u) => u.includes('vector-search')));
  console.log('property-paths in navUrls:', navUrls.includes('property-paths.html'));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
