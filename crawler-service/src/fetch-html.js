const axios = require('axios');
const iconv = require('iconv-lite');

const CHARSET_ALIASES = {
  gb2312: 'gbk',
  gb_2312: 'gbk',
  'x-gbk': 'gbk',
  'cp936': 'gbk',
  'windows-936': 'gbk',
  'windows-949': 'cp949',
  big5: 'big5',
  'x-euc-jp': 'euc-jp',
  shift_jis: 'shift_jis',
  'x-sjis': 'shift_jis',
};

function normalizeCharset(raw) {
  if (!raw) return null;
  const cleaned = raw.trim().toLowerCase().replace(/^["']|["']$/g, '');
  return CHARSET_ALIASES[cleaned] || cleaned;
}

function charsetFromContentType(contentType) {
  if (!contentType) return null;
  const match = /charset=([^;\s]+)/i.exec(contentType);
  return match ? normalizeCharset(match[1]) : null;
}

function charsetFromHtml(buffer) {
  const headSample = buffer.slice(0, Math.min(buffer.length, 8192)).toString('latin1');
  const metaCharset = /<meta[^>]+charset=["']?\s*([^"'\s/>]+)/i.exec(headSample);
  if (metaCharset) return normalizeCharset(metaCharset[1]);
  const httpEquiv = /<meta[^>]+http-equiv=["']?content-type["']?[^>]+content=["'][^"']*charset=([^"'\s/>]+)/i.exec(headSample);
  if (httpEquiv) return normalizeCharset(httpEquiv[1]);
  return null;
}

function detectCharset(contentType, buffer) {
  return charsetFromContentType(contentType)
    || charsetFromHtml(buffer)
    || 'utf-8';
}

function decodeHtmlBuffer(data, contentType) {
  const buffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
  const charset = detectCharset(contentType, buffer);
  if (charset === 'utf-8' || charset === 'utf8') {
    return buffer.toString('utf8');
  }
  if (iconv.encodingExists(charset)) {
    return iconv.decode(buffer, charset);
  }
  return buffer.toString('utf8');
}

async function fetchHtml(url, options = {}) {
  const retries = options.retries ?? 1;
  const timeout = options.timeout ?? 30000;
  let lastError;
  for (let attempt = 0; attempt < retries; attempt++) {
    try {
      const response = await axios.get(url, {
        ...options,
        timeout,
        responseType: 'arraybuffer',
        decompress: true,
      });
      return decodeHtmlBuffer(response.data, response.headers['content-type']);
    } catch (error) {
      lastError = error;
      if (attempt < retries - 1) {
        await new Promise((resolve) => setTimeout(resolve, Math.pow(2, attempt) * 1000));
      }
    }
  }
  throw lastError;
}

function shouldUseBrowserFetch(url, requiresJs) {
  if (requiresJs) {
    return true;
  }
  const lower = (url || '').toLowerCase();
  return lower.includes('dev.mysql.com/doc/');
}

async function fetchDocHtml(url, options = {}) {
  const { requiresJs = false, timeout, retries, headers } = options;

  if (shouldUseBrowserFetch(url, requiresJs)) {
    const { fetchWithPuppeteer } = require('./fetch-browser');
    const result = await fetchWithPuppeteer(url, { timeout: timeout || 60000 });
    return result.data;
  }

  try {
    return await fetchHtml(url, { headers, timeout, retries });
  } catch (error) {
    const status = error.response?.status;
    if (status === 403 && shouldUseBrowserFetch(url, true)) {
      const { fetchWithPuppeteer } = require('./fetch-browser');
      const result = await fetchWithPuppeteer(url, { timeout: timeout || 60000 });
      return result.data;
    }
    throw error;
  }
}

module.exports = {
  decodeHtmlBuffer,
  detectCharset,
  fetchHtml,
  fetchDocHtml,
};
