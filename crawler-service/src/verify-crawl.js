const fs = require('fs-extra');
const path = require('path');
const { navHrefToStorageLocalPath, deriveSiteKey } = require('./site-key');

function navPathMatchesCrawled(navHref, crawledPaths, docsDir, siteKey) {
  if (!navHref) return false;
  const candidates = new Set([
    navHref,
    navHrefToStorageLocalPath(siteKey, navHref.replace(/^[^/]+\//, '')),
  ]);
  if (siteKey && !navHref.startsWith(siteKey + '/')) {
    candidates.add(`${siteKey}/${navHref.replace(/^\//, '')}`);
    candidates.add(navHrefToStorageLocalPath(siteKey, navHref));
  }
  for (const c of candidates) {
    if (crawledPaths.has(c)) return true;
    const full = path.join(docsDir, c.replace(/^\//, ''));
    if (fs.existsSync(full)) return true;
  }
  return false;
}

/**
 * Verify crawled pages exist on disk and match nav tree paths.
 */
function verifyCrawlResults(results, docsDir, siteNavTree = [], baseUrl = '') {
  const missingFiles = [];
  const emptyContent = [];
  const presentFiles = [];

  for (const page of results || []) {
    const localPath = page.localPath;
    if (!localPath) continue;

    const fullPath = path.join(docsDir, localPath.replace(/^\//, ''));
    if (page.emptyContent) {
      emptyContent.push(localPath);
    }
    if (fs.existsSync(fullPath)) {
      presentFiles.push(localPath);
    } else {
      missingFiles.push(localPath);
    }
  }

  const crawledPaths = new Set(presentFiles);
  const navMissing = [];
  let navTotal = 0;
  let navMatched = 0;
  const siteKey = baseUrl ? deriveSiteKey(baseUrl) : (siteNavTree[0]?.href?.split('/')[0] || '');

  const walkNav = (nodes) => {
    if (!nodes) return;
    for (const node of nodes) {
      if (node.href) {
        navTotal += 1;
        if (navPathMatchesCrawled(node.href, crawledPaths, docsDir, siteKey)) {
          navMatched += 1;
        } else {
          navMissing.push(node.href);
        }
      }
      if (node.children?.length) {
        walkNav(node.children);
      }
    }
  };
  walkNav(siteNavTree);

  const total = results?.length || 0;
  const fileCoverage = total > 0 ? presentFiles.length / total : 0;
  const navCoverage = navTotal > 0 ? navMatched / navTotal : null;
  const passed = missingFiles.length === 0 && fileCoverage >= 0.95;

  return {
    passed,
    fileCoverage: Math.round(fileCoverage * 1000) / 1000,
    navCoverage: navCoverage != null ? Math.round(navCoverage * 1000) / 1000 : null,
    crawledPages: total,
    presentOnDisk: presentFiles.length,
    missingFiles: missingFiles.slice(0, 20),
    missingFileCount: missingFiles.length,
    emptyContentPages: emptyContent.length,
    navMissingPaths: navMissing.slice(0, 20),
    navMissingCount: navMissing.length,
    totalNavNodes: navTotal,
    matchedNavNodes: navMatched,
  };
}

module.exports = { verifyCrawlResults };
