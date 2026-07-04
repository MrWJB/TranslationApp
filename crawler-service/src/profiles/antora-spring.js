module.exports = {
  id: 'antora-spring',
  name: 'Antora / Spring docs',
  priority: 100,
  match: (url) => url && url.toLowerCase().includes('docs.spring.io'),
  confidence: 0.95,
  discovery: 'nav-only',
  excludePathPatterns: [
    /search\.html$/i,
    /404\.html$/i,
    /spring-projects\.html$/i,
    /appendix\/api\//i,
    // External Javadoc/KDoc — not Antora prose pages
    /\/api\/java(?:\/|$)/i,
    /\/api\/kotlin(?:\/|$)/i,
    /gradle-plugin\/api\/java(?:\/|$)/i,
    /maven-plugin\/api\/java(?:\/|$)/i,
  ],
  structure: {
    type: 'antora',
    navRoot: 'nav.nav-menu',
    navList: 'ul.nav-list',
    mainSelector: 'main.article',
    breadcrumbSelector: '.breadcrumbs-container',
  },
  htmlShell: 'antora',
  assetBaseUrl: 'https://docs.spring.io/spring-framework/reference/',
  assets: [
    '_/css/site.css',
    '_/css/vendor/asciidoctor-tabs.css',
    '_/js/vendor/asciidoctor-tabs.js',
  ],
  requiresJs: false,
};
