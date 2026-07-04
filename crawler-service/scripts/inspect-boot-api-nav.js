const axios = require('axios');
const cheerio = require('cheerio');

function walk($, li, indent = '') {
  const link = $(li).children('a.nav-link').first();
  const title = link.text().trim() || '(folder)';
  const href = link.attr('href');
  const external = link.hasClass('link-external');
  const depth = $(li).attr('data-depth');
  console.log(`${indent}[d${depth}] "${title}" href=${href || '(none)'} external=${external}`);
  $(li).children('ul.nav-list').children('li').each((_, child) => walk($, child, indent + '  '));
}

(async () => {
  const base = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/';
  const r = await axios.get(base + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  const nav = $('nav.nav-menu').first();
  nav.find('ul.nav-list').first().children('li').each((_, li) => walk($, li));
})();
