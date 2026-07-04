const CONTENT_CANDIDATE_SELECTORS = [
  'main.article',
  'article.doc',
  'article.content',
  'article.theme-doc-markdown',
  'article.md-content',
  'div.document',
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
  'div#docs-body',
  '#content',
  '.content',
];

const CHROME_REMOVE = [
  'script', 'style', 'nav', 'header', 'footer', 'aside',
  '.sidebar', '.navigation', '.sidebar-links', '.page-navigation',
  '.prev', '.next', '.pagination', '.toc', '#toc',
  '.breadcrumbs-container', '.breadcrumbs', '.sidebar-nav',
  '.nav-panel', '.doc-nav', '.page-nav', '.edit-this-page',
  '.github-link', '.nav-container', '.nav-menu', '.toolbar',
];

function scoreContentElement($, el) {
  const text = $(el).text().trim();
  const textLen = text.length;
  if (textLen < 80) {
    return 0;
  }
  const paragraphs = $(el).find('p').length;
  const headings = $(el).find('h1,h2,h3,h4,h5,h6').length;
  const codeBlocks = $(el).find('pre, code').length;
  const links = $(el).find('a').length;
  const linkRatio = textLen > 0 ? (links * 40) / textLen : 0;

  return textLen + paragraphs * 50 + headings * 30 + codeBlocks * 20 - linkRatio * 100;
}

function findBestContentSelector($, profileMainSelector) {
  if (profileMainSelector) {
    const el = $(profileMainSelector).first();
    if (el.length && el.text().trim().length > 50) {
      return profileMainSelector;
    }
  }

  let bestSelector = null;
  let bestScore = 0;
  for (const selector of CONTENT_CANDIDATE_SELECTORS) {
    const el = $(selector).first();
    if (!el.length) {
      continue;
    }
    const score = scoreContentElement($, el);
    if (score > bestScore) {
      bestScore = score;
      bestSelector = selector;
    }
  }
  return bestSelector;
}

async function extractPageContent($, pageUrl, options = {}) {
  const {
    structure = {},
    profile = null,
    downloadImageFn,
    sanitizeElementFn,
    rewriteLinksFn,
    siteKey,
  } = options;

  const mainSelector = findBestContentSelector($, structure.mainSelector || profile?.structure?.mainSelector);
  if (!mainSelector) {
    return { title: '', breadcrumbs: '', articleHtml: '', mainSelector: null, empty: true };
  }

  const main = $(mainSelector).first().clone();
  for (const sel of CHROME_REMOVE) {
    main.find(sel).remove();
  }
  main.find('[class*="sidebar"], [id*="sidebar"], [class*="navigation"]').remove();

  if (typeof downloadImageFn === 'function') {
    for (const img of main.find('img').toArray()) {
      const imgElement = $(img);
      const src = imgElement.attr('src');
      if (src) {
        const localPath = await downloadImageFn(src, pageUrl);
        if (localPath) {
          imgElement.attr('src', localPath);
        }
      }
    }
  }

  if (typeof rewriteLinksFn === 'function') {
    rewriteLinksFn(main, $, pageUrl, siteKey);
  }

  if (typeof sanitizeElementFn === 'function') {
    main.find('*').each((_, nested) => sanitizeElementFn($(nested)));
    sanitizeElementFn(main);
  }

  const breadcrumbsSelector = structure.breadcrumbSelector || profile?.structure?.breadcrumbSelector;
  let breadcrumbs = '';
  if (breadcrumbsSelector) {
    const bc = $(breadcrumbsSelector).first();
    breadcrumbs = bc.length ? bc.prop('outerHTML') || '' : '';
  }

  const articleHtml = main.prop('outerHTML') || '';
  return {
    title: '',
    breadcrumbs,
    articleHtml,
    mainSelector,
    empty: !articleHtml.trim() || main.text().trim().length < 30,
  };
}

module.exports = {
  CONTENT_CANDIDATE_SELECTORS,
  findBestContentSelector,
  extractPageContent,
};
