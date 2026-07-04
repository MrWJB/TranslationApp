const generic = require('./generic');
const antoraSpring = require('./antora-spring');
const mysqlRefman = require('./mysql-refman');
const readthedocs = require('./readthedocs');
const mkdocs = require('./mkdocs');
const docusaurus = require('./docusaurus');

const PROFILES = [
  antoraSpring,
  mysqlRefman,
  readthedocs,
  mkdocs,
  docusaurus,
  generic,
];

function detectProfile(url, $ = null) {
  let best = generic;
  let bestScore = generic.confidence || 0.5;

  for (const profile of PROFILES) {
    if (profile.id === 'generic') {
      continue;
    }
    try {
      if (profile.match(url, $)) {
        const score = profile.confidence || 0.8;
        if (score >= bestScore) {
          best = profile;
          bestScore = score;
        }
      }
    } catch {
      // ignore matcher errors
    }
  }

  return { ...best };
}

function resolveStructure(profile, detectedStructure) {
  const merged = { ...(detectedStructure || {}) };
  if (profile?.structure) {
    if (profile.structure.type) merged.type = profile.structure.type;
    if (profile.structure.navRoot) merged.navRoot = profile.structure.navRoot;
    if (profile.structure.navList) merged.navList = profile.structure.navList;
    if (profile.structure.mainSelector) merged.mainSelector = profile.structure.mainSelector;
    if (profile.structure.breadcrumbSelector) merged.breadcrumbSelector = profile.structure.breadcrumbSelector;
  }
  return merged;
}

function shouldExcludeUrl(url, profile, docBaseUrl) {
  if (!url || !profile?.excludePathPatterns?.length) {
    return false;
  }
  let relative = url;
  try {
    const base = docBaseUrl.endsWith('/') ? docBaseUrl : `${docBaseUrl}/`;
    if (url.startsWith(base)) {
      relative = url.substring(base.length);
    } else {
      const parsed = new URL(url);
      const baseParsed = new URL(base);
      if (parsed.origin === baseParsed.origin && parsed.pathname.startsWith(baseParsed.pathname)) {
        relative = parsed.pathname.substring(baseParsed.pathname.length);
      }
    }
  } catch {
    relative = url;
  }

  return profile.excludePathPatterns.some((pattern) => pattern.test(relative) || pattern.test(url));
}

function countNavNodes(nodes) {
  if (!nodes || !nodes.length) {
    return 0;
  }
  let count = 0;
  for (const node of nodes) {
    count += 1;
    if (node.children?.length) {
      count += countNavNodes(node.children);
    }
  }
  return count;
}

function topNavTitles(nodes, limit = 20) {
  if (!nodes) {
    return [];
  }
  return nodes.slice(0, limit).map((n) => ({
    title: n.title,
    href: n.href || '',
    childCount: n.children?.length || 0,
  }));
}

module.exports = {
  PROFILES,
  detectProfile,
  resolveStructure,
  shouldExcludeUrl,
  countNavNodes,
  topNavTitles,
};
