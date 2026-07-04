const axios = require('axios');
const fs = require('fs-extra');
const path = require('path');

const ASSET_BASE = '/api/crawl/assets';
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const TIMEOUT = 30000;

const DEFAULT_ASSET_FILES = [
  '_/css/site.css',
  '_/css/vendor/asciidoctor-tabs.css',
  '_/js/vendor/asciidoctor-tabs.js',
];

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function ensureAssets(docsDir, assetBaseUrl, assetFiles = DEFAULT_ASSET_FILES) {
  const base = assetBaseUrl || 'https://docs.spring.io/spring-framework/reference/';
  for (const assetPath of assetFiles) {
    const target = path.join(docsDir, assetPath);
    if (fs.existsSync(target)) {
      continue;
    }
    fs.ensureDirSync(path.dirname(target));
    const url = base.endsWith('/') ? base + assetPath : `${base}/${assetPath}`;
    try {
      const response = await axios.get(url, {
        headers: { 'User-Agent': USER_AGENT },
        timeout: TIMEOUT,
        responseType: 'arraybuffer',
      });
      fs.writeFileSync(target, response.data);
      console.log(`Downloaded asset: ${assetPath}`);
    } catch (error) {
      console.warn(`Failed to download asset ${assetPath}: ${error.message}`);
    }
  }
}

const PREVIEW_ONLY_CSS = `
    body { margin: 0 !important; background: #fff !important; }
    .toolbar, .edit-this-page, nav.pagination, .nav-text { display: none !important; }
    .breadcrumbs li#copy-url:empty { display: none; }
    .nav-container, .nav-panel-explore, .nav-panel-search { display: none !important; }
    article.doc .sectnav, article.doc nav.pagination { display: none !important; }
    .admonitionblock td.content { padding-left: 5rem !important; }
    .doc .admonitionblock td.content { padding-left: 5rem !important; }
`;

function buildDocumentHtml(title, articleHtml, navHtml = '', currentPath = '') {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <link rel="stylesheet" href="${ASSET_BASE}/_/css/site.css">
  <link rel="stylesheet" href="${ASSET_BASE}/_/css/vendor/asciidoctor-tabs.css">
  <style id="translation-app-preview">${PREVIEW_ONLY_CSS}
  </style>
  <script src="${ASSET_BASE}/_/js/vendor/asciidoctor-tabs.js" defer></script>
</head>
<body class="article">
  <div class="body">
    ${navHtml || ''}
    ${articleHtml}
  </div>
  <script>
    document.querySelectorAll('a[data-path]').forEach(function(link) {
      link.addEventListener('click', function(event) {
        event.preventDefault();
        var targetPath = link.getAttribute('data-path');
        if (targetPath && window.parent && window.parent !== window) {
          window.parent.postMessage({ type: 'navigate', path: targetPath }, '*');
        }
      });
    });
  </script>
</body>
</html>`;
}

module.exports = {
  ASSET_BASE,
  PREVIEW_ONLY_CSS,
  DEFAULT_ASSET_FILES,
  ensureAssets,
  buildDocumentHtml,
};
