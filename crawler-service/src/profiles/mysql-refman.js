module.exports = {
  id: 'mysql-refman',
  name: 'MySQL Reference Manual',
  priority: 90,
  match: (url, $) => {
    const lower = (url || '').toLowerCase();
    if (lower.includes('dev.mysql.com/doc/refman')) {
      return true;
    }
    return Boolean($ && ($('dl.toc').length > 0 || $('div#docs-body').length > 0));
  },
  confidence: 0.92,
  discovery: 'nav-only',
  excludePathPatterns: [
    /search\.html$/i,
    /404\.html$/i,
  ],
  structure: {
    type: 'mysql-refman',
    navRoot: 'dl.toc',
    navList: null,
    mainSelector: 'div#docs-body',
    breadcrumbSelector: null,
  },
  htmlShell: 'minimal',
  assets: [],
  requiresJs: true,
};
