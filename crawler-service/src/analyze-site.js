const axios = require('axios');
const cheerio = require('cheerio');
const { fetchDocHtml } = require('./fetch-html');
const structureDetector = require('./structure-detector');
const { detectProfile, resolveStructure, countNavNodes, topNavTitles } = require('./profiles');
const { deriveSiteKey } = require('./site-key');
const { findBestContentSelector } = require('./content-extractor');

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

async function fetchPageHtml(url) {
  const profileHint = detectProfile(url);
  return fetchDocHtml(url, {
    headers: { 'User-Agent': USER_AGENT },
    timeout: 60000,
    requiresJs: profileHint.requiresJs,
  });
}

async function analyzeSite(baseUrl) {
  const warnings = [];
  let html;
  try {
    html = await fetchPageHtml(baseUrl);
  } catch (error) {
    return {
      success: false,
      error: error.message,
      profile: null,
      warnings: ['Failed to fetch index page'],
    };
  }

  const $ = cheerio.load(html);
  const profile = detectProfile(baseUrl, $);
  const detected = structureDetector.detectStructure($, profile);
  const structure = resolveStructure(profile, detected);
  const siteKey = deriveSiteKey(baseUrl);
  const navTree = structureDetector.parseNavTree($, baseUrl, baseUrl, structure);
  const mainSelector = findBestContentSelector($, structure.mainSelector);
  const navCount = countNavNodes(navTree);
  const topNav = topNavTitles(navTree, 20);

  if (!navTree.length) {
    warnings.push('No navigation tree detected — sidebar may be flat');
  }
  if (!mainSelector) {
    warnings.push('No main content region detected');
  }
  if ($('#root').length && !mainSelector && navTree.length === 0) {
    warnings.push('Page may require JavaScript rendering (SPA detected)');
  }
  if (profile.requiresJs) {
    warnings.push('Profile marked as requiresJs — static fetch may be incomplete');
  }

  return {
    success: true,
    baseUrl,
    siteKey,
    profile: {
      id: profile.id,
      name: profile.name,
      confidence: profile.confidence,
      discovery: profile.discovery,
      htmlShell: profile.htmlShell,
    },
    structure: {
      type: structure.type,
      navRoot: structure.navRoot,
      navList: structure.navList,
      mainSelector: mainSelector || structure.mainSelector,
      breadcrumbSelector: structure.breadcrumbSelector,
    },
    navPreview: topNav,
    estimatedPages: navCount || null,
    warnings,
  };
}

module.exports = {
  analyzeSite,
};
