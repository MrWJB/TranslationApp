/**
 * Generic document structure detection for technical documentation sites.
 * Supports Antora/Spring docs and common doc site patterns.
 */

const NAV_ROOT_SELECTORS = [
  'nav.nav-menu',
  'aside nav',
  'aside.sidebar nav',
  '.sidebar nav',
  '.sidebar-nav',
  '.doc-sidebar nav',
  '.navigation nav',
  'nav.sidebar',
  'nav.toc',
  '.toc nav',
  '#sidebar nav',
  'aside',
  'nav[role="navigation"]',
  'nav'
];

const NAV_LIST_SELECTORS = [
  'ul.nav-list',
  'ul.menu',
  'ul.toc',
  'ol.toc',
  '.nav-list',
  '.sidebar-links',
  'ul'
];

const MAIN_CONTENT_SELECTORS = [
  'main.article',
  'article.doc',
  'article.content',
  'article',
  'main .content',
  'main',
  '[role="main"]',
  '.doc-content',
  '.main-content',
  '.content-body',
  '.markdown-body',
  '.documentation',
  '.sect1',
  '#content',
  '.content'
];

const BREADCRUMB_SELECTORS = [
  '.breadcrumbs-container',
  '.breadcrumbs',
  'nav.breadcrumb',
  '.breadcrumb',
  '[aria-label="breadcrumb"]'
];

const CHROME_REMOVE_SELECTORS = [
  'script',
  'style',
  '.edit-this-page',
  '.toolbar',
  '.search',
  '.DocSearch-Button',
  '.browse-version',
  'nav.pagination',
  '.page-navigation .prev',
  '.page-navigation .next'
];

function detectStructure($, profile = null) {
  if (profile?.structure?.navRoot || profile?.structure?.mainSelector) {
    const navRoot = profile.structure.navRoot || detectFirstMatch($, NAV_ROOT_SELECTORS);
    const mainSelector = profile.structure.mainSelector || detectMainSelector($);
    return {
      type: profile.structure.type || (navRoot ? 'profile' : 'generic'),
      navRoot,
      navList: profile.structure.navList || (navRoot ? detectNavListSelector($, navRoot) : null),
      mainSelector: mainSelector || null,
      breadcrumbSelector: profile.structure.breadcrumbSelector || detectBreadcrumbSelector($),
    };
  }

  const antoraNav = $('nav.nav-menu ul.nav-list').first();
  if (antoraNav.length) {
    return {
      type: 'antora',
      navRoot: 'nav.nav-menu',
      navList: 'ul.nav-list',
      mainSelector: detectMainSelector($) || 'main.article',
      breadcrumbSelector: detectBreadcrumbSelector($)
    };
  }

  const navRoot = detectFirstMatch($, NAV_ROOT_SELECTORS);
  const mainSelector = detectMainSelector($);

  return {
    type: navRoot ? 'sidebar-nav' : 'generic',
    navRoot: navRoot,
    navList: navRoot ? detectNavListSelector($, navRoot) : null,
    mainSelector: mainSelector || null,
    breadcrumbSelector: detectBreadcrumbSelector($)
  };
}

function detectFirstMatch($, selectors) {
  for (const selector of selectors) {
    const el = $(selector).first();
    if (el.length && hasNavLinks(el)) {
      return selector;
    }
  }
  return null;
}

function detectMainSelector($) {
  for (const selector of MAIN_CONTENT_SELECTORS) {
    const el = $(selector).first();
    if (el.length && el.text().trim().length > 50) {
      return selector;
    }
  }
  return null;
}

function detectBreadcrumbSelector($) {
  for (const selector of BREADCRUMB_SELECTORS) {
    if ($(selector).first().length) {
      return selector;
    }
  }
  return null;
}

function detectNavListSelector($, navRoot) {
  const root = $(navRoot).first();
  for (const selector of NAV_LIST_SELECTORS) {
    if (root.find(selector).first().length || root.is(selector)) {
      return selector;
    }
  }
  return 'ul';
}

function hasNavLinks(container) {
  const links = container.find('a[href]').filter((_, el) => {
    const href = container.find(el).attr('href') || '';
    return href && !href.startsWith('#') && !href.startsWith('javascript:');
  });
  return links.length >= 2;
}

function normalizeHref(href, pageUrl) {
  if (!href || href.startsWith('#') || href.startsWith('javascript:')) {
    return null;
  }
  href = href.trim();
  if (href.startsWith('http')) {
    return href;
  }
  try {
    const base = pageUrl.endsWith('/')
      ? pageUrl
      : pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

function hrefToLocalPath(href, baseUrl) {
  if (!href || !baseUrl) {
    return null;
  }
  try {
    const base = new URL(baseUrl);
    const target = new URL(href);
    if (target.origin !== base.origin) {
      return null;
    }
    let pathname = target.pathname;
    if (pathname.endsWith('/')) {
      pathname += 'index.html';
    } else if (!pathname.endsWith('.html') && !pathname.includes('.')) {
      pathname += '.html';
    }
    let relative = pathname;
    if (base.pathname && base.pathname !== '/') {
      const basePath = base.pathname.replace(/\/index\.html$/, '').replace(/\/$/, '');
      if (relative.startsWith(basePath)) {
        relative = relative.substring(basePath.length);
      }
    }
    return relative.replace(/^\//, '');
  } catch {
    return null;
  }
}

function parseMysqlRefmanNavTree($, pageUrl, baseUrl) {
  const dl = $('dl.toc').first();
  if (!dl.length) {
    return [];
  }
  return parseDlTocItems(dl, $, pageUrl, baseUrl, 1);
}

function parseDlTocItems(dl, $, pageUrl, baseUrl, depth) {
  const items = [];
  dl.children('dt').each((_, dtEl) => {
    const dt = $(dtEl);
    const link = dt.find('a').first();
    const title = link.length ? link.text().trim() : dt.text().trim();
    if (!title) {
      return;
    }

    const href = link.length ? resolveNavLinkHref(link, pageUrl) : null;
    let children = [];
    const dd = dt.next('dd');
    if (dd.length) {
      const nestedDl = dd.children('dl').first();
      if (nestedDl.length) {
        children = parseDlTocItems(nestedDl, $, pageUrl, baseUrl, depth + 1);
      }
    }

    if (!href && children.length === 0) {
      return;
    }

    const localPath = href ? hrefToLocalPath(href, baseUrl) : null;
    items.push({
      title,
      href: localPath || (href ? href.replace(baseUrl, '') : ''),
      url: href || '',
      depth,
      children,
    });
  });
  return items;
}

function parseNavTree($, pageUrl, baseUrl, structure) {
  if (structure.type === 'mysql-refman') {
    return parseMysqlRefmanNavTree($, pageUrl, baseUrl);
  }
  if (structure.type === 'antora') {
    return parseAntoraNavTree($, pageUrl, baseUrl);
  }
  if (!structure.navRoot) {
    return [];
  }

  const navRoot = $(structure.navRoot).first().clone();
  navRoot.find('script, .search').remove();
  const list = structure.navList
    ? navRoot.find(structure.navList).first()
    : navRoot.find('ul, ol').first();

  if (!list.length) {
    return parseFlatNavLinks(navRoot, pageUrl, baseUrl);
  }

  return parseListItems(list, $, pageUrl, baseUrl, 1);
}

function parseAntoraNavTree($, pageUrl, baseUrl) {
  const navMenu = $('nav.nav-menu').first();
  if (!navMenu.length) {
    return [];
  }

  const rootList = navMenu.find('ul.nav-list').first();
  if (!rootList.length) {
    return [];
  }

  const items = [];
  rootList.children('li').each((_, topLi) => {
    const el = $(topLi);
    const nestedList = el.children('ul.nav-list, ul, ol').first();
    if (nestedList.length) {
      items.push(...parseListItems(nestedList, $, pageUrl, baseUrl, 1));
    } else {
      const item = parseNavListItem(el, $, pageUrl, baseUrl, 1);
      if (item) {
        items.push(item);
      }
    }
  });
  return items;
}

function parseListItems(list, $, pageUrl, baseUrl, depth) {
  const items = [];
  list.children('li').each((_, li) => {
    const item = parseNavListItem($(li), $, pageUrl, baseUrl, depth);
    if (item) {
      items.push(item);
    }
  });
  return items;
}

function resolveNavLinkHref(link, pageUrl) {
  const dataPath = link.attr('data-path');
  if (dataPath && dataPath.trim()) {
    return normalizeHref(dataPath.trim(), pageUrl);
  }
  const href = link.attr('href');
  if (!href || href === '#' || href.startsWith('javascript:')) {
    return null;
  }
  return normalizeHref(href, pageUrl);
}

function parseNavListItem(li, $, pageUrl, baseUrl, depth) {
  const link = li.children('a.nav-link, a').first();
  const childList = li.children('ul.nav-list, ul, ol').first();
  const children = childList.length
    ? parseListItems(childList, $, pageUrl, baseUrl, depth + 1)
    : [];

  if (!link.length) {
    if (children.length === 0) {
      return null;
    }
    const title = li.find('> span.nav-text, > button.nav-item-toggle').first().text().trim()
      || li.clone().children('ul, ol').remove().end().text().trim();
    if (!title) {
      return null;
    }
    return { title, href: '', url: '', depth, children };
  }

  const title = link.text().trim();
  if (!title) {
    return null;
  }

  const href = resolveNavLinkHref(link, pageUrl);
  const localPath = href ? hrefToLocalPath(href, baseUrl) : null;

  if (!href && children.length === 0) {
    return null;
  }

  const external = link.hasClass('link-external');
  return {
    title,
    href: localPath || (href ? href.replace(baseUrl, '') : ''),
    url: href || '',
    depth,
    external,
    children,
  };
}

function parseFlatNavLinks(navRoot$, pageUrl, baseUrl) {
  const items = [];
  const seen = new Set();
  navRoot$.find('a[href], a[data-path]').each((_, anchor) => {
    const el = navRoot$(anchor);
    const title = el.text().trim();
    const href = resolveNavLinkHref(el, pageUrl);
    if (!title || !href || seen.has(href)) {
      return;
    }
    seen.add(href);
    items.push({
      title,
      href: hrefToLocalPath(href, baseUrl) || href,
      url: href,
      depth: 1,
      children: []
    });
  });
  return items;
}

function extractBreadcrumbs($, structure) {
  if (!structure.breadcrumbSelector) {
    return '';
  }
  const el = $(structure.breadcrumbSelector).first();
  return el.length ? el.prop('outerHTML') || '' : '';
}

function extractSidebarHtml($, structure, pageUrl, baseUrl) {
  if (!structure.navRoot) {
    return '';
  }
  const sidebar = $(structure.navRoot).first().clone();
  sidebar.find('script, .search, .DocSearch-Button').remove();
  rewriteInternalLinks(sidebar, pageUrl, baseUrl);
  return sidebar.prop('outerHTML') || '';
}

function rewriteInternalLinks(root, pageUrl, baseUrl) {
  root.find('a[href]').each((_, anchor) => {
    const link = root.find(anchor);
    const href = link.attr('href');
    if (!href || href.startsWith('#')) {
      return;
    }
    const resolved = normalizeHref(href, pageUrl);
    if (!resolved) {
      return;
    }
    try {
      const base = new URL(baseUrl);
      const target = new URL(resolved);
      if (target.origin === base.origin) {
        link.attr('href', resolved);
      } else {
        link.attr('target', '_blank');
        link.attr('rel', 'noopener');
      }
    } catch {
      // ignore malformed links
    }
  });
}

function extractMainContentHtml($, pageUrl, structure, processContentFn) {
  const clone = $.root().clone();
  const scoped = clone(structure.mainSelector).first();

  if (!scoped.length) {
    return '';
  }

  scoped.find('script, .edit-this-page, .toolbar, nav.pagination, .sidebar-links').remove();

  if (typeof processContentFn === 'function') {
    return processContentFn(scoped, pageUrl, clone);
  }

  return scoped.prop('outerHTML') || '';
}

function isAntoraSite($) {
  return $('nav.nav-menu ul.nav-list').length > 0 || $('main.article').length > 0;
}

function isSpringDocsUrl(url) {
  return url && url.includes('docs.spring.io');
}

module.exports = {
  detectStructure,
  parseNavTree,
  extractBreadcrumbs,
  extractSidebarHtml,
  extractMainContentHtml,
  normalizeHref,
  hrefToLocalPath,
  isAntoraSite,
  isSpringDocsUrl,
  MAIN_CONTENT_SELECTORS,
  NAV_ROOT_SELECTORS
};
