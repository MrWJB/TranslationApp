const antora = require('./antora');
const minimal = require('./minimal');

const SHELLS = {
  antora,
  minimal,
};

function getHtmlShell(shellId) {
  return SHELLS[shellId] || minimal;
}

async function ensureShellAssets(docsDir, profile) {
  const shell = getHtmlShell(profile?.htmlShell || 'minimal');
  if (profile?.htmlShell === 'antora' && profile.assets?.length) {
    await antora.ensureAssets(docsDir, profile.assetBaseUrl, profile.assets);
  } else if (shell.ensureAssets) {
    await shell.ensureAssets(docsDir);
  }
}

function buildPageHtml(shellId, title, articleHtml, navHtml, currentPath) {
  const shell = getHtmlShell(shellId);
  return shell.buildDocumentHtml(title, articleHtml, navHtml, currentPath);
}

module.exports = {
  getHtmlShell,
  ensureShellAssets,
  buildPageHtml,
};
