/**
 * Classify why a TOC nav node is unavailable (not linked to a crawled document).
 */
const antoraSpring = require('./profiles/antora-spring');
const { shouldExcludeUrl } = require('./profiles');

function normalizePath(localPath) {
  if (!localPath) return '';
  return String(localPath).replace(/\\/g, '/').replace(/^\/+/, '').split('#')[0];
}

function stripSiteKeyPrefix(localPath) {
  const normalized = normalizePath(localPath);
  const slash = normalized.indexOf('/');
  if (slash <= 0) return normalized;
  const category = normalized.substring(0, slash);
  const remainder = normalized.substring(slash + 1);
  if (remainder.startsWith(`${category}/`)) {
    return remainder;
  }
  return normalized;
}

function isExternalApiPath(localPath, url = '') {
  const path = normalizePath(localPath);
  const href = url || '';
  if (/\/api\/java(?:\/|$)/i.test(path)) return true;
  if (/^api\/java(?:\/|$)/i.test(path)) return true;
  if (/\/api\/kotlin(?:\/|$)/i.test(path)) return true;
  if (/^api\/kotlin(?:\/|$)/i.test(path)) return true;
  if (/gradle-plugin\/api\/java(?:\/|$)/i.test(path)) return true;
  if (/maven-plugin\/api\/java(?:\/|$)/i.test(path)) return true;
  if (/appendix\/api\//i.test(path)) return true;
  if (/javadoc-api|kdoc-api/i.test(href)) return true;
  return false;
}

function isExcludedByProfilePath(localPath, baseUrl = 'https://docs.spring.io/spring-boot/4.1-SNAPSHOT/') {
  const path = stripSiteKeyPrefix(localPath);
  if (!path) return false;
  const pageUrl = baseUrl.endsWith('/') ? `${baseUrl}${path}` : `${baseUrl}/${path}`;
  return shouldExcludeUrl(pageUrl, antoraSpring, baseUrl);
}

/**
 * @returns {'external-api'|'excluded-by-profile'|'nav-folder'|'not-crawled'|null}
 */
function resolveUnavailableReason(node, localPath, url, baseUrl) {
  if (node?.external === true) {
    return 'external-api';
  }
  const path = normalizePath(localPath);
  const pageUrl = url || node?.url || '';
  if (isExternalApiPath(path, pageUrl)) {
    return 'external-api';
  }
  if (!path && node?.children?.length) {
    return 'nav-folder';
  }
  if (isExcludedByProfilePath(path, baseUrl)) {
    return 'excluded-by-profile';
  }
  return 'not-crawled';
}

module.exports = {
  normalizePath,
  isExternalApiPath,
  isExcludedByProfilePath,
  resolveUnavailableReason,
};
