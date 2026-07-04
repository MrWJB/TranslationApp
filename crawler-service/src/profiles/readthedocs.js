module.exports = {
  id: 'readthedocs',
  name: 'Read the Docs',
  priority: 80,
  match: (url, $) => {
    const lower = (url || '').toLowerCase();
    return lower.includes('readthedocs.io') || lower.includes('readthedocs.org')
      || ($ && ($('.wy-nav-side').length > 0 || $('nav.wy-nav-side').length > 0));
  },
  confidence: 0.9,
  discovery: 'nav-only',
  excludePathPatterns: [/search\.html$/i, /genindex\.html$/i, /py-modindex\.html$/i],
  structure: {
    type: 'readthedocs',
    navRoot: 'nav.wy-nav-side, div.wy-nav-side',
    navList: 'ul',
    mainSelector: 'div.document, div[role="main"]',
    breadcrumbSelector: 'div.breadcrumbs, ul.wy-breadcrumbs',
  },
  htmlShell: 'minimal',
  assets: [],
  requiresJs: false,
};
