const crypto = require('crypto');

/**
 * Derive a stable storage key from a document base URL.
 * e.g. https://docs.spring.io/spring-boot/4.1-SNAPSHOT/ → spring-boot-4-1-snapshot
 */
function deriveSiteKey(baseUrl) {
  if (!baseUrl) {
    return 'unknown';
  }
  try {
    const url = new URL(baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);
    let pathname = url.pathname.replace(/\/index\.html$/i, '').replace(/\/$/, '');
    const segments = pathname.split('/').filter(Boolean);

    if (segments.length === 0) {
      return url.hostname.replace(/\./g, '-');
    }

    const tail = segments.slice(-3).join('-');
    const normalized = tail
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');

    if (normalized) {
      return normalized;
    }

    const hash = crypto.createHash('md5').update(pathname).digest('hex').slice(0, 8);
    return `${url.hostname.replace(/\./g, '-')}-${hash}`;
  } catch {
    return `unknown-${Date.now()}`;
  }
}

function toStorageLocalPath(siteKey, docRelativePath) {
  const relative = (docRelativePath || '').replace(/^\//, '');
  if (!relative) {
    return relative;
  }
  if (!siteKey) {
    return relative;
  }
  const prefix = `${siteKey}/`;
  if (relative.startsWith(prefix)) {
    return relative;
  }
  return `${siteKey}/${relative}`;
}

function stripSiteKeyPrefix(localPath, siteKey) {
  const normalized = (localPath || '').replace(/\\/g, '/');
  if (!siteKey) {
    return normalized;
  }
  const prefix = `${siteKey}/`;
  if (normalized.startsWith(prefix)) {
    return normalized.substring(prefix.length);
  }
  return normalized;
}

function siteKeyFromLocalPath(localPath) {
  const normalized = (localPath || '').replace(/\\/g, '/');
  const slash = normalized.indexOf('/');
  if (slash <= 0) {
    return null;
  }
  return normalized.substring(0, slash);
}

/** Antora component segment in nav hrefs (e.g. spring-boot/reference/...), not content folders like core/. */
function antoraComponentPrefix(siteKey) {
  if (!siteKey) {
    return null;
  }
  if (siteKey.startsWith('spring-boot')) {
    return 'spring-boot';
  }
  if (siteKey.startsWith('spring-framework')) {
    return 'spring-framework';
  }
  if (siteKey.startsWith('spring-cloud')) {
    return 'spring-cloud';
  }
  if (siteKey.startsWith('spring-mvc')) {
    return 'spring-mvc';
  }
  return null;
}

/**
 * Map nav href values to on-disk localPath (siteKey/relative).
 * Only strips a leading Antora component name when it matches the site (e.g. spring-boot/reference → reference).
 */
function navHrefToStorageLocalPath(siteKey, navHref) {
  const relative = (navHref || '').replace(/^\//, '');
  if (!relative) {
    return relative;
  }
  const prefix = `${siteKey}/`;
  if (relative.startsWith(prefix)) {
    return relative;
  }
  const slash = relative.indexOf('/');
  if (slash > 0) {
    const firstSegment = relative.substring(0, slash);
    const componentPrefix = antoraComponentPrefix(siteKey);
    if (componentPrefix && firstSegment === componentPrefix) {
      const withoutComponent = relative.substring(slash + 1);
      if (withoutComponent) {
        return toStorageLocalPath(siteKey, withoutComponent);
      }
    }
  }
  return toStorageLocalPath(siteKey, relative);
}

/**
 * Validate a custom storage key / path (allows nested dirs like spring-data/reference).
 */
function validateSiteKey(siteKey) {
  if (!siteKey || typeof siteKey !== 'string') {
    return false;
  }
  const trimmed = siteKey.trim();
  if (!trimmed || trimmed.length > 200) {
    return false;
  }
  if (trimmed.includes('..') || trimmed.startsWith('/') || trimmed.endsWith('/')) {
    return false;
  }
  if (trimmed.includes('//') || /[\\:*?"<>|]/.test(trimmed)) {
    return false;
  }
  return /^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?(\/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?)*$/.test(trimmed);
}

function normalizeSiteKey(siteKey) {
  if (!siteKey || typeof siteKey !== 'string') {
    return null;
  }
  const trimmed = siteKey.trim();
  return validateSiteKey(trimmed) ? trimmed : null;
}

module.exports = {
  deriveSiteKey,
  toStorageLocalPath,
  stripSiteKeyPrefix,
  siteKeyFromLocalPath,
  antoraComponentPrefix,
  navHrefToStorageLocalPath,
  validateSiteKey,
  normalizeSiteKey,
};
