/** Backward-compatible re-exports — prefer html-shells/ directly. */
const antora = require('./html-shells/antora');

const SPRING_REFERENCE_BASE_URL = 'https://docs.spring.io/spring-framework/reference/';

module.exports = {
  ASSET_BASE: antora.ASSET_BASE,
  SPRING_REFERENCE_BASE_URL,
  PREVIEW_ONLY_CSS: antora.PREVIEW_ONLY_CSS,
  ensureSpringAssets: (docsDir) => antora.ensureAssets(docsDir, SPRING_REFERENCE_BASE_URL),
  buildSpringDocumentHtml: antora.buildDocumentHtml,
};
