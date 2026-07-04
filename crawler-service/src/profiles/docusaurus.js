module.exports = {
  id: 'docusaurus',
  name: 'Docusaurus',
  priority: 75,
  match: (url, $) => {
    return $ && ($('nav.theme-doc-sidebar').length > 0 || $('aside.theme-doc-sidebar-container').length > 0);
  },
  confidence: 0.88,
  discovery: 'nav-only',
  excludePathPatterns: [/search\.html$/i],
  structure: {
    type: 'docusaurus',
    navRoot: 'nav.theme-doc-sidebar, aside.theme-doc-sidebar-container',
    navList: 'ul.menu__list, ul',
    mainSelector: 'main article, article.theme-doc-markdown',
    breadcrumbSelector: 'nav.theme-doc-breadcrumbs, .breadcrumbs',
  },
  htmlShell: 'minimal',
  assets: [],
  requiresJs: false,
};
