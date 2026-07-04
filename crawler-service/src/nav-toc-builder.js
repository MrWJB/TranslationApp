/**
 * Build hierarchical TOC from Antora/crawler nav tree + crawled documents.
 */
const { stripSiteKeyPrefix } = require('./site-key');
const { resolveUnavailableReason, isExternalApiPath } = require('./unavailable-reason');

function normalizeStorageLocalPath(localPath) {
  if (!localPath) return localPath;
  let normalized = localPath.replace(/\\/g, '/').replace(/^\/+/, '');
  const firstSlash = normalized.indexOf('/');
  if (firstSlash > 0) {
    const category = normalized.substring(0, firstSlash);
    const remainder = normalized.substring(firstSlash + 1);
    if (remainder.startsWith(`${category}/`)) {
      return `${category}/${remainder.substring(category.length + 1)}`;
    }
  }
  return normalized;
}

function pathLookupCandidates(localPath) {
  const candidates = [];
  const normalized = normalizeStorageLocalPath(localPath);
  candidates.push(normalized);
  if (normalized !== localPath) candidates.push(localPath);
  const firstSlash = normalized.indexOf('/');
  if (firstSlash > 0) {
    const category = normalized.substring(0, firstSlash);
    const remainder = normalized.substring(firstSlash + 1);
    if (!remainder.startsWith(`${category}/`)) {
      candidates.push(`${category}/${category}/${remainder}`);
    }
  }
  return candidates;
}

function joinUrl(baseUrl, localPath) {
  const relative = stripSiteKeyPrefix(localPath, localPath.split('/')[0]);
  if (baseUrl.endsWith('/')) return baseUrl + relative;
  return `${baseUrl}/${relative}`;
}

function buildPathIndex(docs, baseUrl) {
  const index = new Map();
  for (const doc of docs) {
    index.set(doc.localPath, doc);
    if (doc.url) index.set(doc.url, doc);
    const relative = stripSiteKeyPrefix(doc.localPath, doc.localPath.split('/')[0]);
    if (relative && relative !== doc.localPath) {
      index.set(relative, doc);
    }
    if (baseUrl && doc.url) {
      const fromBase = doc.url.replace(baseUrl, '').replace(/^\//, '');
      if (fromBase) index.set(fromBase, doc);
    }
  }
  return index;
}

function lookupByPath(index, localPath, baseUrl) {
  if (!localPath) return null;
  for (const candidate of pathLookupCandidates(localPath)) {
    if (index.has(candidate)) return index.get(candidate);
    const url = joinUrl(baseUrl, candidate);
    if (index.has(url)) return index.get(url);
  }
  let best = null;
  let bestKey = null;
  const suffix = `/${normalizeStorageLocalPath(localPath)}`;
  for (const [key, doc] of index.entries()) {
    if (key === localPath || key.endsWith(suffix)) {
      if (!bestKey || key.length < bestKey.length) {
        bestKey = key;
        best = doc;
      }
    }
  }
  return best;
}

/**
 * @param {object[]} navNodes
 * @param {Map} pathIndex
 * @param {string} baseUrl
 * @param {{ preserveFullNav?: boolean }} options
 */
function buildTocFromNav(navNodes, pathIndex, baseUrl, options = {}) {
  const preserveFullNav = options.preserveFullNav !== false;
  const entries = [];
  for (const node of navNodes || []) {
    const entry = buildNavEntry(node, pathIndex, baseUrl, preserveFullNav);
    if (entry) entries.push(entry);
  }
  return entries;
}

function buildNavEntry(node, pathIndex, baseUrl, preserveFullNav) {
  const localPath = node.href || '';
  const childEntries = (node.children || [])
    .map((c) => buildNavEntry(c, pathIndex, baseUrl, preserveFullNav))
    .filter(Boolean);

  const source = localPath ? lookupByPath(pathIndex, localPath, baseUrl) : null;

  if (!preserveFullNav && !source && !childEntries.length) {
    return null;
  }

  const pageUrl = source?.url || (node.url ? node.url : (localPath ? joinUrl(baseUrl, localPath) : ''));
  const isExternalViewable = (Boolean(node.external) || isExternalApiPath(localPath, pageUrl)) && pageUrl.startsWith('http');
  const entry = {
    id: source?.id ?? null,
    title: node.title,
    level: Math.max(0, (node.depth || 1) - 1),
    url: pageUrl,
    localPath: source?.localPath || (isExternalViewable ? null : localPath || null),
    available: Boolean(source) || isExternalViewable,
    children: childEntries,
  };
  if (isExternalViewable) {
    entry.external = true;
    entry.localPath = null;
    delete entry.unavailableReason;
  } else if (!source) {
    const reason = resolveUnavailableReason(node, localPath, pageUrl, baseUrl);
    if (reason) {
      entry.unavailableReason = reason;
    }
    if (node.external) {
      entry.external = true;
    }
  }
  if (source?.translatedLocalPath) {
    entry.translatedLocalPath = source.translatedLocalPath;
  }
  return entry;
}

function tocStats(nodes, depth = 0) {
  let max = depth;
  let total = 0;
  let withChildren = 0;
  let unavailable = 0;
  for (const n of nodes || []) {
    total += 1;
    if (!n.available) unavailable += 1;
    const ch = n.children || [];
    if (ch.length) {
      withChildren += 1;
      const s = tocStats(ch, depth + 1);
      max = Math.max(max, s.max);
      total += s.total;
      withChildren += s.withChildren;
      unavailable += s.unavailable;
    }
  }
  return { max, total, withChildren, unavailable };
}

module.exports = {
  buildTocFromNav,
  buildPathIndex,
  lookupByPath,
  tocStats,
  normalizeStorageLocalPath,
};
