module.exports = {
  id: 'mkdocs',
  name: 'MkDocs Material',
  priority: 75,
  match: (url, $) => {
    const lower = (url || '').toLowerCase();
    return lower.includes('mkdocs') || ($ && ($('nav.md-nav').length > 0 || $('article.md-content').length > 0));
  },
  confidence: 0.88,
  discovery: 'nav-only',
  excludePathPatterns: [/search\.html$/i],
  structure: {
    type: 'mkdocs',
    navRoot: 'nav.md-nav, nav[aria-label="Navigation"]',
    navList: 'ul.md-nav__list, ul',
    mainSelector: 'article.md-content, main.md-content',
    breadcrumbSelector: 'nav.md-path, .md-path',
  },
  htmlShell: 'minimal',
  assets: [],
  requiresJs: false,
};
