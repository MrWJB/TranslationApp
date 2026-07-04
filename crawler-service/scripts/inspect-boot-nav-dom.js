const axios = require('axios');
const cheerio = require('cheerio');

(async () => {
  const base = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/';
  const r = await axios.get(base + 'index.html', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const $ = cheerio.load(r.data);
  const nav = $('nav.nav-menu').first();
  console.log('nav-menu count', $('nav.nav-menu').length);
  console.log('root ul.nav-list direct li count', nav.find('> ul.nav-list > li').length);
  console.log('first ul.nav-list li count', nav.find('ul.nav-list').first().children('li').length);

  // What does first top li contain?
  const firstLi = nav.find('ul.nav-list').first().children('li').first();
  console.log('first li text snippet', firstLi.text().trim().slice(0, 80));

  // Find Build Tool Plugins in DOM
  $('a.nav-link').each((i, a) => {
    const t = $(a).text().trim();
    if (t === 'Build Tool Plugins' || t === 'Maven Plugin') {
      const li = $(a).closest('li');
      console.log('---', t, 'data-depth=', li.attr('data-depth'), 'parent li depth', li.parent().closest('li').attr('data-depth'));
    }
  });
})();
