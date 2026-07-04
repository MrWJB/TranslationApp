const axios = require('axios');
const cheerio = require('cheerio');

async function main() {
  const url = 'https://docs.spring.io/spring-framework/reference/core/beans.html';
  const { data: html } = await axios.get(url, {
    headers: { 'User-Agent': 'Mozilla/5.0' },
    timeout: 30000,
  });
  const $ = cheerio.load(html);
  console.log('stylesheets:');
  $('link[rel="stylesheet"]').each((_, el) => {
    console.log(' ', $(el).attr('href'));
  });
  console.log('article classes:', $('article').first().attr('class'));
  console.log('main classes:', $('main').first().attr('class'));
  console.log('h1:', $('h1').first().text().trim());
  console.log('doc-content exists:', $('.doc-content').length);
}

main().catch(console.error);
