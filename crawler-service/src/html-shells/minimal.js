function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const MINIMAL_CSS = `
  body { margin: 0; padding: 1.5rem 2rem; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; line-height: 1.6; color: #24292f; background: #fff; }
  img { max-width: 100%; height: auto; }
  pre, code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
  pre { overflow-x: auto; padding: 1rem; background: #f6f8fa; border-radius: 6px; }
  table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
  th, td { border: 1px solid #d0d7de; padding: 0.5rem 0.75rem; text-align: left; }
  nav, aside, header, footer, .sidebar, .nav-container { display: none !important; }
`;

function buildDocumentHtml(title, articleHtml, _navHtml = '', currentPath = '') {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <style id="translation-app-minimal">${MINIMAL_CSS}</style>
</head>
<body>
  ${articleHtml}
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

async function ensureAssets() {
  // no external assets for minimal shell
}

module.exports = {
  MINIMAL_CSS,
  ensureAssets,
  buildDocumentHtml,
};
