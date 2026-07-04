const axios = require('axios');
const cheerio = require('cheerio');

(async () => {
  const r = await axios.get('https://docs.spring.io/spring-framework/reference/index.html', {
    headers: { 'User-Agent': 'Mozilla/5.0' },
  });
  const $ = cheerio.load(r.data);
  const lis = $('nav.nav-menu ul.nav-list').first().children('li');
  console.log('top li count', lis.length);
  lis.slice(0, 4).each((i, el) => {
    const li = $(el);
    console.log('---', i, '---');
    console.log(li.html().slice(0, 500));
  });
})();
