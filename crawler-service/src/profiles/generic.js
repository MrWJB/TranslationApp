module.exports = {
  id: 'generic',
  name: 'Generic documentation',
  priority: 0,
  match: () => true,
  confidence: 0.5,
  discovery: 'nav-then-links',
  excludePathPatterns: [
    /search\.html$/i,
    /404\.html$/i,
    /login\.html$/i,
  ],
  structure: {
    navRoot: null,
    navList: null,
    mainSelector: null,
    breadcrumbSelector: null,
  },
  htmlShell: 'minimal',
  assets: [],
  requiresJs: false,
};
