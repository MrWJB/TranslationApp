/**
 * Profile detection and nav parsing tests (run: node test/profiles.test.js)
 */
const assert = require('assert');
const cheerio = require('cheerio');
const { detectProfile, resolveStructure, shouldExcludeUrl } = require('../src/profiles');
const structureDetector = require('../src/structure-detector');
const { deriveSiteKey } = require('../src/site-key');

function testSiteKey() {
  const key = deriveSiteKey('https://docs.spring.io/spring-boot/4.1-SNAPSHOT/');
  assert.ok(key.includes('spring-boot'), `expected spring-boot in key, got ${key}`);
  console.log('  siteKey spring-boot:', key);
}

function testAntoraProfile() {
  const url = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/';
  const html = `
    <nav class="nav-menu"><ul class="nav-list"><li><a class="nav-link" data-path="index.html">Overview</a></li></ul></nav>
    <main class="article"><p>Spring Boot documentation content here with enough text to detect.</p></main>
  `;
  const $ = cheerio.load(html);
  const profile = detectProfile(url, $);
  assert.strictEqual(profile.id, 'antora-spring');
  const structure = resolveStructure(profile, structureDetector.detectStructure($, profile));
  const nav = structureDetector.parseNavTree($, url, url, structure);
  assert.ok(nav.length >= 1, 'expected nav items');
  assert.ok(shouldExcludeUrl(url + 'appendix/api/actuator/health.html', profile, url));
  assert.ok(shouldExcludeUrl(url + 'api/java/index.html', profile, url));
  assert.ok(!shouldExcludeUrl(url + 'api/rest/actuator/index.html', profile, url));
  assert.ok(!shouldExcludeUrl(url + 'reference/actuator/index.html', profile, url));
  console.log('  antora-spring profile OK');
}

function testReadTheDocsProfile() {
  const url = 'https://docs.example.readthedocs.io/en/latest/';
  const html = `
    <nav class="wy-nav-side"><ul><li><a href="index.html">Home</a></li></ul></nav>
    <div class="document"><p>ReadTheDocs style documentation with sufficient content length for detection.</p></div>
  `;
  const $ = cheerio.load(html);
  const profile = detectProfile(url, $);
  assert.strictEqual(profile.id, 'readthedocs');
  console.log('  readthedocs profile OK');
}

function testMkDocsProfile() {
  const html = `
    <nav class="md-nav"><ul class="md-nav__list"><li><a href="index.html">Home</a></li></ul></nav>
    <article class="md-content"><p>MkDocs material documentation page with enough text.</p></article>
  `;
  const $ = cheerio.load(html);
  const profile = detectProfile('https://example.com/', $);
  assert.strictEqual(profile.id, 'mkdocs');
  console.log('  mkdocs profile OK');
}

function testMysqlRefmanProfile() {
  const url = 'https://dev.mysql.com/doc/refman/8.4/en/';
  const html = `
    <div class="toc"><dl class="toc">
      <dt><a href="preface.html">Preface and Legal Notices</a></dt>
      <dt><a href="introduction.html">1 General Information</a></dt>
      <dd><dl><dt><a href="introduction-installing.html">1.1 Installing</a></dt></dl></dd>
    </dl></div>
    <div id="docs-body"><p>MySQL 8.4 Reference Manual with enough content for detection.</p></div>
  `;
  const $ = cheerio.load(html);
  const profile = detectProfile(url, $);
  assert.strictEqual(profile.id, 'mysql-refman');
  const structure = resolveStructure(profile, structureDetector.detectStructure($, profile));
  const nav = structureDetector.parseNavTree($, url, url, structure);
  assert.ok(nav.length >= 2, 'expected mysql nav items');
  assert.strictEqual(structure.mainSelector, 'div#docs-body');
  console.log('  mysql-refman profile OK');
}

function testDocusaurusProfile() {
  const html = `
    <nav class="theme-doc-sidebar"><ul class="menu__list"><li><a href="/docs/intro">Intro</a></li></ul></nav>
    <main><article class="theme-doc-markdown"><p>Docusaurus documentation content here.</p></article></main>
  `;
  const $ = cheerio.load(html);
  const profile = detectProfile('https://docusaurus.io/', $);
  assert.strictEqual(profile.id, 'docusaurus');
  console.log('  docusaurus profile OK');
}

function testNavHrefPaths() {
  const { navHrefToStorageLocalPath } = require('../src/site-key');
  const sf = 'spring-framework-reference';
  assert.strictEqual(
    navHrefToStorageLocalPath(sf, 'core/beans.html'),
    'spring-framework-reference/core/beans.html'
  );
  assert.strictEqual(
    navHrefToStorageLocalPath(sf, 'overview.html'),
    'spring-framework-reference/overview.html'
  );
  const sb = 'spring-boot-4-1-snapshot';
  assert.strictEqual(
    navHrefToStorageLocalPath(sb, 'spring-boot/reference/using.html'),
    'spring-boot-4-1-snapshot/reference/using.html'
  );
  assert.strictEqual(
    navHrefToStorageLocalPath(sb, 'reference/using.html'),
    'spring-boot-4-1-snapshot/reference/using.html'
  );
  console.log('  navHrefToStorageLocalPath OK');
}

function testNavTocBuilder() {
  const { buildTocFromNav, buildPathIndex, tocStats } = require('../src/nav-toc-builder');
  const docs = [{ id: 1, localPath: 'spring-framework-reference/core/beans.html', url: 'https://x/core/beans.html' }];
  const nav = [{
    title: 'Core',
    href: 'spring-framework-reference/core.html',
    depth: 1,
    children: [{
      title: 'Beans',
      href: 'spring-framework-reference/core/beans.html',
      depth: 2,
      children: [],
    }, {
      title: 'Missing',
      href: 'spring-framework-reference/core/missing.html',
      depth: 2,
      children: [],
    }],
  }, {
    title: 'Testing',
    href: 'spring-framework-reference/testing.html',
    depth: 1,
    children: [],
  }];
  const index = buildPathIndex(docs, 'https://docs.spring.io/spring-framework/reference/');
  const toc = buildTocFromNav(nav, index, 'https://docs.spring.io/spring-framework/reference/', { preserveFullNav: true });
  assert.strictEqual(toc.length, 2);
  assert.strictEqual(toc[0].children.length, 2);
  assert.strictEqual(toc[0].children[0].available, true);
  assert.strictEqual(toc[0].children[1].available, false);
  assert.strictEqual(toc[1].available, false);
  const stats = tocStats(toc);
  assert.strictEqual(stats.unavailable, 3);
  console.log('  navTocBuilder OK');
}

console.log('profiles.test.js');
testSiteKey();
testNavHrefPaths();
testNavTocBuilder();
testAntoraProfile();
testReadTheDocsProfile();
testMkDocsProfile();
testMysqlRefmanProfile();
testDocusaurusProfile();
console.log('All profile tests passed.');
