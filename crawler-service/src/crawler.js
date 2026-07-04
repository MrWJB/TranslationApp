const cheerio = require('cheerio');
const axios = require('axios');
const fs = require('fs-extra');
const path = require('path');
const { ensureSpringAssets, buildSpringDocumentHtml, SPRING_REFERENCE_BASE_URL } = require('./spring-html');
const structureDetector = require('./structure-detector');
const { detectProfile, resolveStructure, shouldExcludeUrl, countNavNodes } = require('./profiles');
const { deriveSiteKey, toStorageLocalPath: siteKeyStoragePath, navHrefToStorageLocalPath, normalizeSiteKey } = require('./site-key');
const { decodeHtmlBuffer, fetchHtml, fetchDocHtml } = require('./fetch-html');
const { fetchWithPuppeteer, isPuppeteerAvailable } = require('./fetch-browser');
const { ensureShellAssets, buildPageHtml } = require('./html-shells');
const { extractPageContent } = require('./content-extractor');
const { analyzeSite } = require('./analyze-site');
const { verifyCrawlResults } = require('./verify-crawl');

// 保存目录
const DOCS_DIR = path.join(__dirname, '../crawled-docs');
const VIDEOS_DIR = path.join(__dirname, '../crawled-videos');
const IMAGES_DIR = path.join(__dirname, '../crawled-docs/images');
const LOG_DIR = path.join(__dirname, '../logs');

// 任务类型定义
const TASK_TYPES = {
  DOCUMENT: 'document',   // 文档类型
  VIDEO: 'video',         // 视频类型
  AUDIO: 'audio',         // 音频类型
  IMAGE: 'image',         // 图片类型
  MIXED: 'mixed',         // 混合内容
  UNKNOWN: 'unknown'      // 未知类型
};

// 视频分类定义
const VIDEO_CATEGORIES = {
  ANIME: 'anime',           // 动漫
  SHORT_DRAMA: 'short-drama', // 短剧
  TV_SERIES: 'tv-series',   // 电视剧
  MOVIE: 'movie',           // 电影
  VARIETY: 'variety',       // 综艺
  EDUCATION: 'education',   // 教育
  OTHER: 'other'            // 其他
};

// 技术分类定义（文档）
const DOC_CATEGORIES = {
  JAVA: 'java',
  SPRING: 'spring',
  SPRING_BOOT: 'spring-boot',
  SPRING_CLOUD: 'spring-cloud',
  SPRING_MVC: 'spring-mvc',
  MYSQL: 'mysql',
  ORACLE: 'oracle',
  OTHER: 'other'
};

// 合并为统一的CATEGORIES导出
const CATEGORIES = DOC_CATEGORIES;

// 技术类型识别关键词
const CATEGORY_KEYWORDS = {
  [CATEGORIES.SPRING_BOOT]: [
    'spring-boot', 'springboot', 'spring boot', '@SpringBootApplication', 'SpringBootConfiguration',
    'SpringApplication.run', '@EnableAutoConfiguration', 'spring-boot-starter'
  ],
  [CATEGORIES.SPRING_CLOUD]: [
    'spring-cloud', 'springcloud', 'spring cloud', '@EnableDiscoveryClient', '@EnableFeignClients',
    'Eureka', 'Ribbon', 'Hystrix', 'Zuul', 'Gateway', 'spring-cloud-starter'
  ],
  [CATEGORIES.SPRING_MVC]: [
    'spring-mvc', 'springmvc', 'spring mvc', '@Controller', '@RequestMapping', '@GetMapping',
    '@PostMapping', 'DispatcherServlet', 'ModelAndView', 'ViewResolver'
  ],
  [CATEGORIES.SPRING]: [
    'spring-framework', 'springframework', 'spring framework', '@Autowired', '@Component', '@Service',
    '@Repository', '@Configuration', 'ApplicationContext', 'BeanFactory', 'IoC', 'DI',
    'docs.spring.io/spring-framework'
  ],
  [CATEGORIES.JAVA]: [
    'java.lang', 'java.util', 'java.io', 'java.net', 'java.nio', 'java.sql', 'java.time',
    'jdk.', 'javadoc', 'oracle.com/java', 'docs.oracle.com/javase', '@Override', 'public class',
    'public static void main'
  ],
  [CATEGORIES.MYSQL]: [
    'mysql', 'mysql.com', 'innodb', 'myisam', 'show databases', 'create database', 'select * from',
    'mysql-connector', '@MySQL'
  ],
  [CATEGORIES.ORACLE]: [
    'oracle', 'oracle.com', 'pl/sql', 'plsql', 'sysdate', 'dual', 'oracle-connector', '@Oracle'
  ]
};

// 确保目录存在
fs.ensureDirSync(DOCS_DIR);
fs.ensureDirSync(VIDEOS_DIR);
fs.ensureDirSync(IMAGES_DIR);
fs.ensureDirSync(LOG_DIR);

// 视频平台URL特征
const VIDEO_URL_PATTERNS = [
  'youtube.com', 'youtu.be',
  'bilibili.com', 'bilibili.cn',
  'vimeo.com', 'dailymotion.com',
  'youku.com', 'iqiyi.com', 'iq.com',
  'tencent.com/video', 'v.qq.com',
  'sohu.com/v', 'tv.sohu.com',
  'letv.com', 'le.com',
  'acfun.cn',
  'douyin.com', 'tiktok.com',
  'kuaishou.com',
  'ani.gamer.com.tw', 'gamer.com.tw',
  '/video/', '/play/', '/watch',
  '/stream/', '/movie/', '/tv/',
  '/anime/', '/episode/'
];

// 文档平台URL特征
const DOC_URL_PATTERNS = [
  'docs.spring.io', 'spring.io/reference',
  'dev.mysql.com/doc', 'docs.oracle.com',
  'developer.mozilla.org', 'docs.microsoft.com',
  'docs.google.com', 'readthedocs.io',
  'github.com/wiki', 'gitlab.com/wiki',
  '/docs/', '/documentation/', '/reference/',
  '/guide/', '/tutorial/', '/manual/',
  '/help/', '/faq/', '/api-docs/',
  '.pdf', '.md', '.html'
];

// 创建所有分类目录
const createAllDirectories = () => {
  // 文档分类目录
  Object.values(DOC_CATEGORIES).forEach(category => {
    const categoryDir = path.join(DOCS_DIR, category);
    fs.ensureDirSync(categoryDir);
    console.log(`Ensured doc directory exists: ${categoryDir}`);
  });
  
  // 视频分类目录
  Object.values(VIDEO_CATEGORIES).forEach(category => {
    const categoryDir = path.join(VIDEOS_DIR, category);
    fs.ensureDirSync(categoryDir);
    console.log(`Ensured video directory exists: ${categoryDir}`);
  });
};

// 创建分类目录（保持向后兼容）
const createCategoryDirectories = () => {
  createAllDirectories();
};

// 初始化分类目录
createAllDirectories();

// 存储日志记录
const writeLog = (logEntry) => {
  const logFile = path.join(LOG_DIR, `crawl-${new Date().toISOString().split('T')[0]}.log`);
  const logLine = JSON.stringify({
    timestamp: new Date().toISOString(),
    ...logEntry
  }) + '\n';
  fs.appendFileSync(logFile, logLine, 'utf8');
};

// 识别任务类型（基于URL）
const identifyTaskType = (url) => {
  const urlLower = url.toLowerCase();
  
  // 检查视频特征
  for (const pattern of VIDEO_URL_PATTERNS) {
    if (urlLower.includes(pattern.toLowerCase())) {
      return {
        type: TASK_TYPES.VIDEO,
        confidence: 0.9,
        reason: `URL contains video pattern: ${pattern}`
      };
    }
  }
  
  // 检查文档特征
  for (const pattern of DOC_URL_PATTERNS) {
    if (urlLower.includes(pattern.toLowerCase())) {
      return {
        type: TASK_TYPES.DOCUMENT,
        confidence: 0.9,
        reason: `URL contains document pattern: ${pattern}`
      };
    }
  }
  
  // 默认根据内容进一步识别
  return {
    type: TASK_TYPES.UNKNOWN,
    confidence: 0.5,
    reason: 'URL pattern not recognized, needs content analysis'
  };
};

// 根据视频元数据确定分类
const identifyVideoCategoryFromMetadata = (videoData) => {
  const metaCategory = (videoData.category || '').toLowerCase();
  
  if (metaCategory.includes('动漫') || metaCategory.includes('动画') || metaCategory.includes('番剧')) {
    return VIDEO_CATEGORIES.ANIME;
  }
  if (metaCategory.includes('短剧')) {
    return VIDEO_CATEGORIES.SHORT_DRAMA;
  }
  if (metaCategory.includes('电视剧') || metaCategory.includes('剧集')) {
    return VIDEO_CATEGORIES.TV_SERIES;
  }
  if (metaCategory.includes('综艺')) {
    return VIDEO_CATEGORIES.VARIETY;
  }
  if (metaCategory.includes('教育') || metaCategory.includes('教程')) {
    return VIDEO_CATEGORIES.EDUCATION;
  }
  if (metaCategory.includes('电影')) {
    return VIDEO_CATEGORIES.MOVIE;
  }
  
  return VIDEO_CATEGORIES.OTHER;
};

// 识别视频分类
const identifyVideoCategory = (url, title, content) => {
  const urlLower = url.toLowerCase();
  const titleLower = (title || '').toLowerCase();
  const contentLower = (content || '').toLowerCase();
  
  // 优先检查 lvtu777.com 的分类（根据URL路径和标题）
  if (urlLower.includes('lvtu777.com')) {
    const lvtuCategory = identifyLvtu777Category(urlLower, titleLower);
    if (lvtuCategory !== VIDEO_CATEGORIES.OTHER) {
      return lvtuCategory;
    }
  }
  
  // 检查其他网站的列表页
  if (urlLower.includes('/list/')) {
    const listCategory = identifyLvtu777ListCategory(urlLower, titleLower);
    if (listCategory !== VIDEO_CATEGORIES.OTHER) {
      return listCategory;
    }
  }
  
  const categoryPatterns = {
    [VIDEO_CATEGORIES.ANIME]: ['anime', '动漫', '动画', '番剧', 'acg', '动画番剧'],
    [VIDEO_CATEGORIES.SHORT_DRAMA]: ['short drama', '短剧', '微短剧', '迷你剧', '短视频剧'],
    [VIDEO_CATEGORIES.TV_SERIES]: ['tv series', '电视剧', '剧集', '连续剧', 'season', 'episode'],
    [VIDEO_CATEGORIES.MOVIE]: ['movie', '电影', '影片', 'film', 'cinema', '预告片'],
    [VIDEO_CATEGORIES.VARIETY]: ['variety', '综艺', '娱乐节目', '真人秀', '综艺节目'],
    [VIDEO_CATEGORIES.EDUCATION]: ['education', '教育', '教程', '课程', 'lecture', '教学', '培训']
  };
  
  for (const [category, patterns] of Object.entries(categoryPatterns)) {
    for (const pattern of patterns) {
      if (urlLower.includes(pattern) || titleLower.includes(pattern) || contentLower.includes(pattern)) {
        return category;
      }
    }
  }
  
  return VIDEO_CATEGORIES.OTHER;
};

// 识别 lvtu777.com 列表页的分类
const identifyLvtu777ListCategory = (urlLower, titleLower) => {
  // lvtu777.com 的列表页 URL 格式为 /list/数字.html
  // 根据页面标题识别分类
  if (titleLower.includes('电影')) {
    return VIDEO_CATEGORIES.MOVIE;
  }
  if (titleLower.includes('短剧')) {
    return VIDEO_CATEGORIES.SHORT_DRAMA;
  }
  if (titleLower.includes('剧') || titleLower.includes('电视剧')) {
    return VIDEO_CATEGORIES.TV_SERIES;
  }
  if (titleLower.includes('综艺')) {
    return VIDEO_CATEGORIES.VARIETY;
  }
  if (titleLower.includes('动漫') || titleLower.includes('动画') || titleLower.includes('番剧')) {
    return VIDEO_CATEGORIES.ANIME;
  }
  if (titleLower.includes('教育') || titleLower.includes('教程')) {
    return VIDEO_CATEGORIES.EDUCATION;
  }
  return VIDEO_CATEGORIES.OTHER;
};

// 识别 lvtu777.com 的分类（根据URL路径）
const identifyLvtu777Category = (urlLower, titleLower) => {
  // lvtu777.com 的URL结构
  // /movie/xxx.html - 电影
  // /anime/xxx.html - 动漫
  // /tv/xxx.html - 电视剧
  // /drama/xxx.html - 短剧
  // /variety/xxx.html - 综艺
  // /list/xxx.html - 需要根据标题识别
  
  if (urlLower.includes('/movie/')) {
    return VIDEO_CATEGORIES.MOVIE;
  }
  if (urlLower.includes('/anime/')) {
    return VIDEO_CATEGORIES.ANIME;
  }
  if (urlLower.includes('/tv/') || urlLower.includes('/tvshow/')) {
    return VIDEO_CATEGORIES.TV_SERIES;
  }
  if (urlLower.includes('/drama/') || urlLower.includes('/short/')) {
    return VIDEO_CATEGORIES.SHORT_DRAMA;
  }
  if (urlLower.includes('/variety/')) {
    return VIDEO_CATEGORIES.VARIETY;
  }
  if (urlLower.includes('/education/') || urlLower.includes('/course/')) {
    return VIDEO_CATEGORIES.EDUCATION;
  }
  
  // 列表页 /list/xxx.html - 需要根据标题识别
  if (urlLower.includes('/list/') && titleLower) {
    return identifyLvtu777ListCategory(urlLower, titleLower);
  }
  
  return VIDEO_CATEGORIES.OTHER;
};

// 识别内容类型
const identifyCategory = (url, title, content) => {
  const lowerUrl = url.toLowerCase();
  const lowerTitle = (title || '').toLowerCase();
  const lowerContent = (content || '').toLowerCase();

  // Spring官方文档URL优先归类
  if (lowerUrl.includes('docs.spring.io/spring-framework')) {
    return CATEGORIES.SPRING;
  }
  if (lowerUrl.includes('docs.spring.io/spring-boot')) {
    return CATEGORIES.SPRING_BOOT;
  }
  if (lowerUrl.includes('docs.spring.io/spring-cloud')) {
    return CATEGORIES.SPRING_CLOUD;
  }

  // 按优先级识别关键词（Spring Boot > Spring Cloud > Spring MVC > Spring）
  const priorityOrder = [
    CATEGORIES.SPRING_BOOT,
    CATEGORIES.SPRING_CLOUD,
    CATEGORIES.SPRING_MVC,
    CATEGORIES.MYSQL,
    CATEGORIES.ORACLE,
    CATEGORIES.JAVA,
    CATEGORIES.SPRING
  ];

  for (const category of priorityOrder) {
    const keywords = CATEGORY_KEYWORDS[category];
    for (const keyword of keywords) {
      if (lowerUrl.includes(keyword.toLowerCase()) ||
          lowerTitle.includes(keyword.toLowerCase()) ||
          lowerContent.includes(keyword.toLowerCase())) {
        console.log(`Identified category ${category} for: ${title}`);
        writeLog({
          level: 'INFO',
          message: `Category identified`,
          url,
          title,
          category
        });
        return category;
      }
    }
  }

  console.log(`No category identified for: ${title}, defaulting to 'other'`);
  writeLog({
    level: 'WARN',
    message: 'Category not identified, using default',
    url,
    title,
    category: CATEGORIES.OTHER
  });
  return CATEGORIES.OTHER;
};

// 根据 siteKey 获取存储路径
function getSiteStoragePath(siteKey, filename) {
  return path.join(DOCS_DIR, siteKey, filename);
}

/** @deprecated use getSiteStoragePath */
function getCategoryStoragePath(category, filename) {
  return getSiteStoragePath(category, filename);
}

function categoryRelativePath(localPath) {
  const normalized = localPath.replace(/\\/g, '/');
  const firstSegment = normalized.split('/')[0];
  if (Object.values(CATEGORIES).includes(firstSegment)) {
    return normalized.substring(firstSegment.length + 1);
  }
  return normalized;
}

function categoryFromDocBaseUrl(docBaseUrl) {
  const lower = (docBaseUrl || '').toLowerCase();
  if (lower.includes('spring-boot')) return CATEGORIES.SPRING_BOOT;
  if (lower.includes('spring-cloud')) return CATEGORIES.SPRING_CLOUD;
  if (lower.includes('spring-mvc')) return CATEGORIES.SPRING_MVC;
  if (lower.includes('spring-framework') || lower.includes('/spring/')) return CATEGORIES.SPRING;
  if (lower.includes('mysql')) return CATEGORIES.MYSQL;
  if (lower.includes('oracle')) return CATEGORIES.ORACLE;
  if (lower.includes('java')) return CATEGORIES.JAVA;
  return CATEGORIES.OTHER;
}

function toStorageLocalPath(siteKey, docRelativePath) {
  return siteKeyStoragePath(siteKey, docRelativePath);
}

function prefixNavTreeStoragePaths(nodes, siteKey) {
  if (!nodes || !nodes.length || !siteKey) {
    return nodes || [];
  }
  return nodes.map((node) => {
    const prefixed = { ...node };
    if (prefixed.href) {
      prefixed.href = navHrefToStorageLocalPath(siteKey, prefixed.href);
    }
    if (prefixed.children?.length) {
      prefixed.children = prefixNavTreeStoragePaths(prefixed.children, siteKey);
    }
    return prefixed;
  });
}

let imageCounter = 0;

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const TIMEOUT = Math.max(15000, parseInt(process.env.CRAWL_TIMEOUT || '60000', 10));
const CRAWL_CONCURRENCY = Math.max(1, parseInt(process.env.CRAWL_CONCURRENCY || '4', 10));

function reportProgress(onProgress, patch) {
  if (typeof onProgress === 'function') {
    onProgress(patch);
  }
}

function shortPageLabel(url, docBaseUrl) {
  if (!url) return '';
  const base = docBaseUrl || '';
  let label = url.replace(base, '').split('#')[0];
  if (label.length > 60) {
    label = '…' + label.slice(-57);
  }
  return label || url;
}
const MAX_RETRIES = 3;

const DEFAULT_HEADERS = {
  'User-Agent': USER_AGENT,
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
  'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
  'Accept-Encoding': 'gzip, deflate, br',
  'Referer': 'https://ani.gamer.com.tw/',
  'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
  'Sec-Ch-Ua-Mobile': '?0',
  'Sec-Ch-Ua-Platform': '"Windows"',
  'Sec-Fetch-Dest': 'document',
  'Sec-Fetch-Mode': 'navigate',
  'Sec-Fetch-Site': 'same-origin',
  'Sec-Fetch-User': '?1',
  'Upgrade-Insecure-Requests': '1',
  'Connection': 'keep-alive',
  'Cache-Control': 'max-age=0'
};

const VIDEO_HEADERS = {
  ...DEFAULT_HEADERS,
  'Referer': '',
  'Sec-Fetch-Site': 'none',
  'Cookie': 'pgv_pvid=1234567890; ts_uid=1234567890; pt2gguin=o0123456789; tvfe_boss_uuid=abcdef123456; pgv_info=ssid=s1234567890; ptisp=ctc'
};

const PROXY_CONFIG = process.env.HTTP_PROXY || process.env.http_proxy || null;

const axiosInstance = axios.create({
  timeout: TIMEOUT,
  proxy: PROXY_CONFIG ? {
    host: new URL(PROXY_CONFIG).hostname,
    port: parseInt(new URL(PROXY_CONFIG).port) || 8080,
    protocol: new URL(PROXY_CONFIG).protocol.replace(':', '')
  } : undefined
});

async function fetchWithRetry(url, options = {}, retries = MAX_RETRIES) {
  const wantsText = options.responseType === 'text';
  const fetchOptions = wantsText
    ? { ...options, responseType: 'arraybuffer' }
    : options;

  for (let i = 0; i < retries; i++) {
    try {
      const response = await axiosInstance.get(url, {
        ...fetchOptions,
        headers: { ...DEFAULT_HEADERS, ...options.headers }
      });
      if (wantsText) {
        response.data = decodeHtmlBuffer(response.data, response.headers['content-type']);
      }
      return response;
    } catch (error) {
      console.warn(`Fetch attempt ${i + 1} failed for ${url}: ${error.message}`);
      if (i === retries - 1) {
        throw error;
      }
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000));
    }
  }
}

async function fetchWithPuppeteerForVideo(url) {
  return fetchWithPuppeteer(url);
}

const ALLOWED_ATTRS = new Set([
  'id', 'class', 'href', 'src', 'alt', 'title', 'width', 'height', 'target', 'rel',
  'colspan', 'rowspan', 'scope', 'aria-label', 'aria-hidden', 'aria-controls', 'aria-labelledby',
  'role', 'data-path', 'data-depth', 'data-panel', 'data-component', 'data-version', 'type', 'aria-expanded',
]);

const NAV_PANEL_CACHE = path.join(DOCS_DIR, '_/nav-panel.html');
const NAV_PANEL_SOURCE_URL = SPRING_REFERENCE_BASE_URL + 'index.html';
const NAV_CACHE_MARKER_PREFIX = '<!-- nav-cache-base:';
const NAV_CACHE_MARKER_SUFFIX = ' -->';
let currentDocBaseUrl = SPRING_REFERENCE_BASE_URL;
let currentSiteProfile = null;
let currentSiteKey = null;
let documentCrawlLock = Promise.resolve();

function withDocumentCrawlLock(fn) {
  const run = documentCrawlLock.then(() => fn());
  documentCrawlLock = run.catch(() => {});
  return run;
}

/**
 * Remove crawled files for one site only (keeps other site folders intact).
 */
async function clearCrawledDocsForSite(siteKey, docBaseUrl = null) {
  await fs.ensureDir(DOCS_DIR);
  let removed = 0;

  if (siteKey) {
    const siteDir = path.join(DOCS_DIR, siteKey);
    if (await fs.pathExists(siteDir)) {
      await fs.remove(siteDir);
      removed += 1;
      console.log(`Cleared site folder: ${siteKey}`);
    }
    // Legacy category folders from older crawls (spring-boot, spring, etc.)
    const legacyMap = {
      'spring-boot-4-1-snapshot': ['spring-boot'],
      'spring-framework-reference': ['spring'],
    };
    for (const legacy of legacyMap[siteKey] || []) {
      const legacyDir = path.join(DOCS_DIR, legacy);
      if (await fs.pathExists(legacyDir)) {
        await fs.remove(legacyDir);
        removed += 1;
        console.log(`Cleared legacy folder: ${legacy}`);
      }
    }
  }

  if (docBaseUrl) {
    const cacheFile = getNavPanelCachePath(normalizeDocBaseUrl(docBaseUrl));
    if (fs.existsSync(cacheFile)) {
      await fs.remove(cacheFile);
      removed += 1;
    }
  }

  await fs.ensureDir(IMAGES_DIR);
  writeLog({
    level: 'INFO',
    message: 'Cleared crawled-docs for site before new crawl',
    siteKey,
    entriesRemoved: removed,
  });
  return removed;
}

/** @deprecated use clearCrawledDocsForSite */
async function clearCrawledDocsStorage() {
  await fs.ensureDir(DOCS_DIR);
  const entries = await fs.readdir(DOCS_DIR);
  for (const entry of entries) {
    await fs.remove(path.join(DOCS_DIR, entry));
  }
  await fs.ensureDir(IMAGES_DIR);
}

function readNavCacheMarker(html) {
  if (!html || !html.startsWith(NAV_CACHE_MARKER_PREFIX)) {
    return null;
  }
  const markerEnd = html.indexOf(NAV_CACHE_MARKER_SUFFIX);
  if (markerEnd < 0) {
    return null;
  }
  const marker = html.substring(0, markerEnd + NAV_CACHE_MARKER_SUFFIX.length);
  const match = marker.match(/<!-- nav-cache-base:(.+?) -->/);
  if (!match) {
    return null;
  }
  return {
    baseUrl: match[1],
    content: html.slice(marker.length),
  };
}

function readNavCacheFile(cacheFile, expectedBase) {
  if (!fs.existsSync(cacheFile)) {
    return '';
  }
  const html = fs.readFileSync(cacheFile, 'utf8');
  const parsed = readNavCacheMarker(html);
  if (parsed) {
    return parsed.baseUrl === expectedBase ? parsed.content : '';
  }
  // Legacy caches without a marker: only trust the default Spring Framework panel file.
  if (cacheFile === NAV_PANEL_CACHE && expectedBase === SPRING_REFERENCE_BASE_URL) {
    return html;
  }
  return '';
}

function writeNavCacheFile(cacheFile, expectedBase, navHtml) {
  fs.ensureDirSync(path.dirname(cacheFile));
  fs.writeFileSync(
    cacheFile,
    `${NAV_CACHE_MARKER_PREFIX}${expectedBase}${NAV_CACHE_MARKER_SUFFIX}\n${navHtml}`,
    'utf8'
  );
}

function normalizeDocBaseUrl(baseUrl) {
  if (!baseUrl) {
    return SPRING_REFERENCE_BASE_URL;
  }
  let normalized = baseUrl.trim();
  if (!normalized.endsWith('/')) {
    normalized += '/';
  }
  return normalized;
}

function getNavPanelCachePath(docBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  if (base === SPRING_REFERENCE_BASE_URL) {
    return NAV_PANEL_CACHE;
  }
  const cacheKey = base.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '');
  return path.join(DOCS_DIR, '_', `nav-panel-${cacheKey || 'default'}.html`);
}

function assertValidBaseUrl(baseUrl) {
  const normalized = String(baseUrl || '').trim();
  if (!normalized || !normalized.startsWith('http')) {
    throw new Error(`A valid HTTP(S) base URL is required: ${baseUrl}`);
  }
  return normalizeDocBaseUrl(normalized);
}

const OFFICIAL_TOP_LEVEL_CHAPTERS = [
  'overview.html',
  'core.html',
  'data-access.html',
  'web.html',
  'web-reactive.html',
  'rsocket.html',
  'testing.html',
  'integration.html',
  'languages.html',
  'appendix.html',
];

const INDEX_PAGE_PATHS = new Set(['', 'index.html']);

function isIndexPageUrl(url, docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  const pagePath = url.replace(base, '').replace(/\/$/, '');
  return INDEX_PAGE_PATHS.has(pagePath) || pagePath === 'index.html';
}

function countContentPages(results) {
  return results.filter((page) => !isIndexPageUrl(page.url)).length;
}

function collectUrlsFromNavTree(nodes, docBaseUrl) {
  const urls = [];
  const seen = new Set();
  const base = normalizeDocBaseUrl(docBaseUrl);

  const walk = (list) => {
    if (!list || !list.length) {
      return;
    }
    for (const node of list) {
      let resolved = node.url;
      if (!resolved && node.href) {
        try {
          resolved = normalizeUrl(new URL(node.href, base).href.split('#')[0]);
        } catch {
          resolved = node.href.startsWith('http') ? node.href : base + node.href.replace(/^\.\//, '');
        }
      }
      if (resolved && resolved.startsWith(base) && resolved.endsWith('.html') && !seen.has(resolved)) {
        seen.add(resolved);
        urls.push(resolved);
      }
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };

  walk(nodes);
  return urls;
}

function extractNavPageUrls($, pageUrl, docBaseUrl = currentDocBaseUrl) {
  const urls = [];
  const base = normalizeDocBaseUrl(docBaseUrl);
  const seen = new Set();

  $('nav.nav-menu a[href], nav.nav-menu a[data-path]').each((_, el) => {
    const anchor = $(el);
    const dataPath = anchor.attr('data-path');
    const rawHref = dataPath && dataPath.trim() ? dataPath.trim() : anchor.attr('href');
    if (!rawHref || rawHref === '#' || rawHref.startsWith('javascript:')) {
      return;
    }
    try {
      const pageBase = normalizeDocBaseUrl(docBaseUrl);
      const resolved = normalizeUrl(new URL(rawHref, pageBase).href.split('#')[0]);
      if (resolved.startsWith(base) && resolved.endsWith('.html') && !seen.has(resolved)) {
        seen.add(resolved);
        urls.push(resolved);
      }
    } catch {
      // ignore malformed links
    }
  });

  return urls;
}

/**
 * Discover top-level chapter URLs from Antora nav, page links, or built-in list.
 */
function extractTopLevelChapterUrls($, pageUrl, docBaseUrl = currentDocBaseUrl) {
  const urls = [];
  const base = normalizeDocBaseUrl(docBaseUrl);

  $('nav.nav-menu a.nav-link').each((_, el) => {
    const href = $(el).attr('href');
    if (!href || href.startsWith('http') || href.startsWith('#')) {
      return;
    }
    const path = href.split('#')[0].replace(/^\.\//, '');
    if (/^[a-zA-Z0-9-]+\.html$/.test(path)) {
      const fullUrl = base + path;
      if (!urls.includes(fullUrl)) {
        urls.push(fullUrl);
      }
    }
  });

  if (urls.length > 0) {
    return urls;
  }

  for (const link of extractLinks($, pageUrl, docBaseUrl)) {
    if (isTopLevelDoc(link, docBaseUrl) && !urls.includes(link)) {
      urls.push(link);
    }
  }

  if (urls.length > 0) {
    return urls;
  }

  if (base === SPRING_REFERENCE_BASE_URL) {
    return OFFICIAL_TOP_LEVEL_CHAPTERS.map((chapter) => base + chapter);
  }

  return extractNavPageUrls($, pageUrl, docBaseUrl);
}

/**
 * 将URL转换为本地文件路径
 * 例如: https://docs.spring.io/spring-framework/reference/core/beans.html -> core/beans.html
 */
function urlToLocalPath(url, docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  let relativePath = url.replace(base, '');

  if (relativePath === '' || relativePath === url) {
    try {
      const urlObj = new URL(url);
      const baseObj = new URL(base);
      if (urlObj.origin === baseObj.origin && urlObj.pathname.startsWith(baseObj.pathname)) {
        relativePath = urlObj.pathname.substring(baseObj.pathname.length);
      } else {
        relativePath = 'index.html';
      }
    } catch {
      relativePath = 'index.html';
    }
  }

  relativePath = relativePath.replace(/^\//, '');
  if (!relativePath || relativePath === '/') {
    relativePath = 'index.html';
  }

  if (!relativePath.endsWith('.html')) {
    relativePath += '.html';
  }

  return relativePath;
}

/**
 * 下载图片并保存到本地
 */
async function downloadImage(src, pageUrl) {
  try {
    // 解析图片URL
    let imageUrl;
    if (src.startsWith('http')) {
      imageUrl = src;
    } else if (src.startsWith('/')) {
      imageUrl = 'https://docs.spring.io' + src;
    } else {
      const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
      imageUrl = basePath + src;
    }

    // 标准化URL
    imageUrl = normalizeUrl(imageUrl);

    // 生成文件名
    imageCounter++;
    let extension = 'png';
    if (imageUrl.includes('.')) {
      let potentialExt = imageUrl.substring(imageUrl.lastIndexOf('.') + 1);
      const queryIndex = potentialExt.indexOf('?');
      if (queryIndex > 0) {
        potentialExt = potentialExt.substring(0, queryIndex);
      }
      if (/^[a-zA-Z0-9]{2,4}$/.test(potentialExt)) {
        extension = potentialExt.toLowerCase();
      }
    }

    const filename = `img_${imageCounter}.${extension}`;
    const localPath = path.join(IMAGES_DIR, filename);

    // 如果已存在，跳过
    if (fs.existsSync(localPath)) {
      return `/api/crawl/images/${filename}`;
    }

    // 下载图片
    console.log(`Downloading image: ${imageUrl} -> ${filename}`);
    const response = await axios.get(imageUrl, {
      headers: { 'User-Agent': USER_AGENT },
      timeout: TIMEOUT,
      responseType: 'arraybuffer'
    });

    fs.writeFileSync(localPath, response.data);
    return `/api/crawl/images/${filename}`;
  } catch (error) {
    console.warn(`Failed to download image: ${src} (${error.message})`);
    return src;
  }
}

/**
 * 标准化URL
 */
function normalizeUrl(url) {
  while (url.includes('/../')) {
    url = url.replace(/\/[^/]+\/\.\.\//, '/');
  }
  
  while (url.includes('://../')) {
    url = url.replace(/:\/\/[^/]+\/\.\.\//, '://');
  }
  
  return url;
}

function isSafeUrl(value) {
  if (!value) return true;
  const lowerValue = value.trim().toLowerCase();
  return lowerValue.startsWith('http://') ||
    lowerValue.startsWith('https://') ||
    lowerValue.startsWith('/api/crawl/images/') ||
    lowerValue.startsWith('#') ||
    lowerValue.startsWith('./') ||
    lowerValue.startsWith('../');
}

function sanitizeElement(element) {
  const attributes = element.attr() || {};
  for (const [name, value] of Object.entries(attributes)) {
    const lowerName = name.toLowerCase();
    if (lowerName.startsWith('on')
      || (!ALLOWED_ATTRS.has(lowerName) && !lowerName.startsWith('data-'))) {
      element.removeAttr(name);
      continue;
    }

    if ((lowerName === 'href' || lowerName === 'src') && !isSafeUrl(value)) {
      element.removeAttr(name);
    }
  }
}

/**
 * 处理内容元素，下载图片
 */
async function processContentElements(container, pageUrl, $) {
  let html = '';
  
  for (const child of container.children().toArray()) {
    const element = $(child);
    const tagName = element[0].tagName.toLowerCase();
    
    // 跳过导航和侧边栏
    if (element.hasClass('sidebar') || element.hasClass('navigation') || 
        element.hasClass('toc') || element.attr('id') === 'toc' ||
        element.hasClass('breadcrumbs-container') || element.hasClass('breadcrumbs') ||
        element.hasClass('page-navigation') || element.hasClass('footer') ||
        element.hasClass('sidebar-nav') || element.hasClass('nav-panel') ||
        element.hasClass('doc-nav') || element.hasClass('page-nav') ||
        element.hasClass('edit-this-page') || element.hasClass('github-link') ||
        element.hasClass('sidebar-links') || element.hasClass('prev') ||
        element.hasClass('next') || element.hasClass('pagination')) {
      continue;
    }
    
    // Also skip elements with sidebar/nav in class or id
    const className = element.attr('class') || '';
    const elementId = element.attr('id') || '';
    if (className.includes('sidebar') || className.includes('navigation') || 
        className.includes('page-nav') || className.includes('doc-nav') ||
        elementId.includes('sidebar') || elementId.includes('navigation') ||
        elementId.includes('toc')) {
      continue;
    }
    
    // 处理图片
    if (tagName === 'img') {
      let src = element.attr('src');
      if (src) {
        const localPath = await downloadImage(src, pageUrl);
        if (localPath) {
          element.attr('src', localPath);
        }
      }
      sanitizeElement(element);
      html += element.prop('outerHTML') + '\n';
    }
    // 保留结构元素
    else if (/^h[1-6]|p|ul|ol|li|dl|dt|dd|table|thead|tbody|tr|th|td|div|section|article|blockquote|pre|code|figure|figcaption|hr|details|summary|a|span|strong|em|b|i|sub|sup$/.test(tagName)) {
      // 处理元素内的图片
      for (const img of element.find('img').toArray()) {
        const imgElement = $(img);
        let src = imgElement.attr('src');
        if (src) {
          const localPath = await downloadImage(src, pageUrl);
          if (localPath) {
            imgElement.attr('src', localPath);
          }
        }
      }
      element.find('*').each((_, nested) => sanitizeElement($(nested)));
      sanitizeElement(element);
      html += element.prop('outerHTML') + '\n';
    } else {
      // 递归处理其他元素
      html += await processContentElements(element, pageUrl, $);
    }
  }
  
  return html;
}

/**
 * 提取HTML内容
 */
async function extractHtmlContent($, pageUrl) {
  let html = '';

  // Remove unwanted elements first - comprehensive removal
  $('script, style, nav, header, footer, aside, .sidebar, .navigation, .sidebar-links, .page-navigation, .prev, .next, .pagination, .toc, #toc, .breadcrumbs-container, .breadcrumbs, .sidebar-nav, .nav-panel, .doc-nav, .page-nav, .edit-this-page, .github-link').remove();

  // Also remove any element with sidebar/nav in its class or id
  $('[class*="sidebar"], [class*="navigation"], [class*="page-nav"], [class*="doc-nav"], [id*="sidebar"], [id*="navigation"], [id*="toc"]').remove();

  // Prioritize selectors that are more likely to contain main content
  const contentSelectors = [
    'article',           // Main content in semantic HTML
    '.doc-content',      // Documentation-specific class
    '.main-content',     // Main content class
    '.content',          // General content class
    'main .content',     // Content inside main tag
    '#content',          // Content with ID
    '.sect1',            // Section content
    'main',              // Main tag
    '.section',          // Section class
    '.documentation'     // Documentation class
  ];

  let contentContainer = null;
  for (const selector of contentSelectors) {
    contentContainer = $(selector).first();
    if (contentContainer.length) {
      break;
    }
  }

  if (contentContainer && contentContainer.length) {
    html = await processContentElements(contentContainer, pageUrl, $);
  } else {
    html = $('body').html() || '';
  }

  return html.trim();
}

function resolveReferencePath(href, pageUrl, docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  const pageBase = pageUrl.endsWith('/')
    ? pageUrl
    : pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
  const resolved = normalizeUrl(new URL(href, pageBase).href);
  if (!resolved.startsWith(base)) {
    return null;
  }
  return resolved.replace(base, '');
}

function rewriteInternalLinks(root, $, pageUrl, docBaseUrl = currentDocBaseUrl, storageSiteKey = null) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  const siteKey = storageSiteKey || currentSiteKey || deriveSiteKey(base);
  root.find('a[href]').each((_, element) => {
    const link = $(element);
    const href = link.attr('href');
    if (!href || href.startsWith('#')) {
      return;
    }

    if (href.startsWith('http')) {
      if (!href.startsWith(base)) {
        link.attr('target', '_blank');
        link.attr('rel', 'noopener');
      }
      return;
    }

    try {
      const targetPath = resolveReferencePath(href, pageUrl, docBaseUrl);
      if (targetPath) {
        link.attr('data-path', toStorageLocalPath(siteKey, targetPath));
        link.attr('href', '#');
      }
    } catch (error) {
      // Ignore malformed relative links
    }
  });
}

/**
 * 提取官方左侧导航面板（含 Spring Framework 标题与版本号）
 */
async function extractNavPanel($, pageUrl, sanitizeElementFn) {
  const navContainer = $('div.nav-container').first();
  if (!navContainer.length) {
    return '';
  }

  const panel = cheerio.load(navContainer.prop('outerHTML') || '', null, false);
  const root = panel.root().children().first();

  root.find('script, .search, .browse-version, .DocSearch-Button').remove();
  rewriteInternalLinks(root, panel, pageUrl, currentDocBaseUrl, currentSiteKey || deriveSiteKey(currentDocBaseUrl));
  root.find('*').each((_, nested) => sanitizeElementFn(panel(nested)));
  sanitizeElementFn(root);

  return root.prop('outerHTML') || '';
}

async function loadNavPanelHtml(docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  const cacheFile = getNavPanelCachePath(base);
  const cached = readNavCacheFile(cacheFile, base);
  if (cached) {
    return cached;
  }

  const sourceUrl = base.endsWith('index.html') ? base : base + 'index.html';

  try {
    const html = await fetchHtml(sourceUrl, {
      headers: { 'User-Agent': USER_AGENT },
      timeout: TIMEOUT,
    });
    const $ = cheerio.load(html);
    const navHtml = await extractNavPanel($, sourceUrl, sanitizeElement);
    if (navHtml) {
      writeNavCacheFile(cacheFile, base, navHtml);
      console.log(`Cached nav panel for ${base}`);
    }
    return navHtml;
  } catch (error) {
    console.warn(`Failed to load nav panel from ${sourceUrl}: ${error.message}`);
    return '';
  }
}

/**
 * 提取 Spring 官方 main.article 内容，保留原始 AsciiDoc 结构与样式类名
 */
async function extractMainArticle($, pageUrl, downloadImageFn, sanitizeElementFn, storageCategory = null) {
  const main = $('main.article').first();
  if (!main.length) {
    return extractHtmlContent($, pageUrl);
  }

  main.find('script, .edit-this-page, .toolbar, nav.pagination, .nav-text, .sidebar-links').remove();

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

  rewriteInternalLinks(main, $, pageUrl, currentDocBaseUrl, storageCategory);

  main.find('*').each((_, nested) => sanitizeElementFn($(nested)));
  sanitizeElementFn(main);

  return main.prop('outerHTML') || '';
}

/**
 * 提取侧边栏目录导航
 */
function extractSidebarNav($, pageUrl) {
  let sidebarHtml = '';
  
  // Try to find the sidebar/navigation
  const sidebarSelectors = [
    '.sidebar',
    '.nav-container',
    '.toc',
    '#toc',
    'aside',
    '.page-navigation',
    '.doc-navigation'
  ];
  
  for (const selector of sidebarSelectors) {
    const sidebar = $(selector).first();
    if (sidebar.length) {
      sidebarHtml = sidebar.prop('outerHTML');
      console.log(`Found sidebar using selector: ${selector}`);
      break;
    }
  }
  
  return sidebarHtml;
}

/**
 * 爬取单个页面并保存为HTML文件
 */
async function crawlPage(url, options = {}) {
  console.log(`Crawling page: ${url}`);
  
  const startTime = new Date();
  const pageTimeout = options.timeout || TIMEOUT;
  const pageRetries = options.retries ?? MAX_RETRIES;
  
  try {
    const profileHint = currentSiteProfile || detectProfile(currentDocBaseUrl || url);
    const html = await fetchDocHtml(url, {
      headers: { 'User-Agent': USER_AGENT },
      timeout: pageTimeout,
      retries: pageRetries,
      requiresJs: profileHint?.requiresJs,
    });

    const $ = cheerio.load(html);
    
    const title = extractTitle($);
    const profile = currentSiteProfile || detectProfile(currentDocBaseUrl, $);
    const detected = structureDetector.detectStructure($, profile);
    const structure = resolveStructure(profile, detected);
    await ensureShellAssets(DOCS_DIR, profile);

    let navTree = structureDetector.parseNavTree($, url, currentDocBaseUrl, structure);
    if (!navTree.length && profile.id === 'antora-spring') {
      const navPanelHtml = await loadNavPanelHtml(currentDocBaseUrl);
      if (navPanelHtml) {
        const navDoc = cheerio.load(`<html><body>${navPanelHtml}</body></html>`);
        navTree = structureDetector.parseNavTree(navDoc, url, currentDocBaseUrl, { type: 'antora', navRoot: 'div.nav-container', navList: 'ul.nav-list' });
      }
    }

    const siteKey = currentSiteKey || deriveSiteKey(currentDocBaseUrl);
    const navUrls = extractNavPageUrls($, url, currentDocBaseUrl);
    const { articleHtml, empty: emptyContent } = await extractPageContent($, url, {
      structure,
      profile,
      downloadImageFn: downloadImage,
      sanitizeElementFn: sanitizeElement,
      rewriteLinksFn: (root, $doc, pageUrl) => rewriteInternalLinks(root, $doc, pageUrl, currentDocBaseUrl, siteKey),
      siteKey,
    });
    const sections = extractSections($);
    const links = extractLinks($, url, currentDocBaseUrl);
    const topLevelChapterUrls = extractTopLevelChapterUrls($, url, currentDocBaseUrl);

    const filename = urlToLocalPath(url, currentDocBaseUrl);
    const localPath = toStorageLocalPath(siteKey, filename);
    const fullPath = path.join(DOCS_DIR, localPath);
    fs.ensureDirSync(path.dirname(fullPath));

    const fullHtml = buildPageHtml(profile.htmlShell || 'minimal', title, articleHtml, '', localPath);
    fs.writeFileSync(fullPath, fullHtml, 'utf8');
    
    const crawlTime = new Date() - startTime;
    
    console.log(`Saved: ${localPath}`);
    console.log(`Successfully crawled: ${title} (siteKey: ${siteKey}, sections: ${sections.length}, content length: ${articleHtml.length}, time: ${crawlTime}ms)`);
    
    writeLog({
      level: 'INFO',
      message: 'Page crawled successfully',
      url,
      title,
      siteKey,
      profileId: profile.id,
      localPath,
      storagePath: fullPath,
      sectionsCount: sections.length,
      contentLength: articleHtml.length,
      crawlTimeMs: crawlTime
    });
    
    return {
      title,
      url,
      localPath,
      storagePath: fullPath,
      category: siteKey,
      siteKey,
      profileId: profile.id,
      emptyContent,
      sections,
      links,
      navUrls,
      navTree,
      topLevelChapterUrls,
    };
  } catch (error) {
    console.error(`Failed to crawl ${url}: ${error.message}`);
    writeLog({
      level: 'ERROR',
      message: 'Failed to crawl page',
      url,
      error: error.message
    });
    throw error;
  }
}

function countMatchedNavNodes(nodes, crawledPaths) {
  let matched = 0;
  let total = 0;
  const walk = (list) => {
    if (!list) return;
    for (const node of list) {
      if (node.href) {
        total += 1;
        if (crawledPaths.has(node.href)) {
          matched += 1;
        }
      }
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };
  walk(nodes);
  return { matched, total };
}

function buildQualityReport(profile, siteNavTree, results, crawlStats) {
  const crawledPaths = new Set(results.map((r) => r.localPath));
  const { matched, total } = countMatchedNavNodes(siteNavTree, crawledPaths);
  const emptyPages = results.filter((r) => r.emptyContent).length;
  return {
    profileId: profile?.id || 'unknown',
    navCoverage: total > 0 ? Math.round((matched / total) * 1000) / 1000 : null,
    matchedNavNodes: matched,
    totalNavNodes: total,
    emptyPages,
    excludedUrls: crawlStats?.excludedCount || 0,
    crawledPages: results.length,
    pathMatchFailures: crawlStats?.pathMatchFailures || [],
  };
}

function collectAllNavTargetUrls(indexPage, siteNavTree, docBaseUrl) {
  const urls = [];
  const seen = new Set();
  const base = normalizeDocBaseUrl(docBaseUrl);

  const add = (rawUrl) => {
    if (!rawUrl) return;
    let normalized;
    try {
      normalized = normalizeUrl(String(rawUrl).split('#')[0]);
    } catch {
      return;
    }
    if (!normalized.startsWith(base) || !normalized.endsWith('.html')) {
      return;
    }
    if (seen.has(normalized)) {
      return;
    }
    seen.add(normalized);
    urls.push(normalized);
  };

  for (const navUrl of indexPage?.navUrls || []) {
    add(navUrl);
  }
  for (const treeUrl of collectUrlsFromNavTree(siteNavTree, docBaseUrl)) {
    add(treeUrl);
  }
  return urls;
}

function crawledUrlSet(results) {
  const set = new Set();
  for (const page of results || []) {
    if (page?.url) {
      set.add(normalizeUrl(page.url.split('#')[0]));
    }
  }
  return set;
}

/**
 * 爬取多个页面
 */
async function crawlMultiplePages(baseUrl, maxPages, onProgress, profileOverride = null, storageSiteKey = null) {
  baseUrl = assertValidBaseUrl(baseUrl);
  currentDocBaseUrl = normalizeDocBaseUrl(baseUrl);
  currentSiteKey = normalizeSiteKey(storageSiteKey) || deriveSiteKey(currentDocBaseUrl);
  currentSiteProfile = profileOverride || currentSiteProfile || detectProfile(baseUrl);

  const results = [];
  const visited = new Set();
  let siteNavTree = [];
  const siteKey = currentSiteKey;
  const profile = currentSiteProfile;
  const crawlStats = { excludedCount: 0, pathMatchFailures: [] };
  const discoveryMode = profile.discovery || 'nav-only';
  const followPageLinks = discoveryMode === 'nav-then-links';

  try {
  reportProgress(onProgress, {
    phase: 'crawling',
    current: 0,
    total: maxPages,
    message: `正在分析站点导航（${profile.name}）…`,
  });

  const enqueueDocUrl = (rawUrl, queue, discovered) => {
    if (!rawUrl) {
      return;
    }
    let normalized;
    try {
      normalized = normalizeUrl(rawUrl.split('#')[0]);
    } catch {
      return;
    }
    if (!normalized.startsWith(currentDocBaseUrl)) {
      return;
    }
    if (!normalized.endsWith('.html')) {
      return;
    }
    if (shouldExcludeUrl(normalized, profile, currentDocBaseUrl)) {
      crawlStats.excludedCount += 1;
      return;
    }
    if (discovered.has(normalized)) {
      return;
    }
    discovered.add(normalized);
    queue.push(normalized);
  };

  try {
    const indexUrl = normalizeUrl(baseUrl.split('#')[0]);
    const indexPage = await crawlPage(indexUrl);
    results.push(indexPage);
    visited.add(indexUrl);
    visited.add(normalizeUrl(currentDocBaseUrl + 'index.html'));

    if (indexPage.navTree && indexPage.navTree.length) {
      siteNavTree = indexPage.navTree;
    }

    const discovered = new Set([...visited]);
    const queue = [];

    for (const navUrl of indexPage.navUrls || []) {
      enqueueDocUrl(navUrl, queue, discovered);
    }
    for (const treeUrl of collectUrlsFromNavTree(siteNavTree, currentDocBaseUrl)) {
      enqueueDocUrl(treeUrl, queue, discovered);
    }
    if (followPageLinks) {
      for (const link of indexPage.links || []) {
        enqueueDocUrl(link, queue, discovered);
      }
      for (const chapterUrl of indexPage.topLevelChapterUrls || []) {
        enqueueDocUrl(chapterUrl, queue, discovered);
      }
    }

    const navTargetUrls = collectAllNavTargetUrls(indexPage, siteNavTree, currentDocBaseUrl);
    const contentNavCount = navTargetUrls.filter((u) => !isIndexPageUrl(u, currentDocBaseUrl)).length;
    const effectiveMaxPages = discoveryMode === 'nav-only'
      ? Math.max(maxPages, contentNavCount)
      : maxPages;

    console.log(`Queued ${queue.length} pages (discovery: ${discoveryMode}, nav targets: ${contentNavCount}, effective max: ${effectiveMaxPages})`);

    const successfulUrls = crawledUrlSet(results);
    let idx = 0;
    while (idx < queue.length && countContentPages(results) < effectiveMaxPages) {
      const batch = [];
      while (batch.length < CRAWL_CONCURRENCY && idx < queue.length && countContentPages(results) + batch.length < effectiveMaxPages) {
        const pageUrl = queue[idx];
        idx++;
        if (!successfulUrls.has(pageUrl)) {
          batch.push(pageUrl);
        }
      }

      if (batch.length === 0) {
        if (idx < queue.length) {
          continue;
        }
        break;
      }

      reportProgress(onProgress, {
        phase: 'crawling',
        current: countContentPages(results),
        total: Math.min(effectiveMaxPages, Math.max(queue.length, countContentPages(results) + batch.length)),
        message: `正在爬取 ${batch.length} 页（已完成 ${countContentPages(results)}/${effectiveMaxPages}）…`,
      });

      const batchResults = await Promise.all(
        batch.map(async (pageUrl) => {
          try {
            return { pageUrl, page: await crawlPage(pageUrl) };
          } catch (error) {
            console.error(`Failed to crawl sub-page ${pageUrl}: ${error.message}`);
            writeLog({
              level: 'ERROR',
              message: 'Failed to crawl page',
              url: pageUrl,
              error: error.message,
            });
            return { pageUrl, page: null };
          }
        })
      );

      for (const { pageUrl, page } of batchResults) {
        if (!page) {
          continue;
        }
        results.push(page);
        successfulUrls.add(pageUrl);
        if (followPageLinks) {
          for (const link of page.links || []) {
            enqueueDocUrl(link, queue, discovered);
          }
        }
        reportProgress(onProgress, {
          phase: 'crawling',
          current: countContentPages(results),
          total: Math.min(effectiveMaxPages, Math.max(queue.length, countContentPages(results))),
          message: `已爬取: ${shortPageLabel(pageUrl, currentDocBaseUrl)} (${countContentPages(results)}/${effectiveMaxPages})`,
        });
      }
    }

    const missingNavUrls = navTargetUrls.filter((u) => !successfulUrls.has(u) && !isIndexPageUrl(u, currentDocBaseUrl));
    if (missingNavUrls.length > 0) {
      console.log(`Retrying ${missingNavUrls.length} missing nav pages sequentially…`);
      reportProgress(onProgress, {
        phase: 'crawling',
        current: countContentPages(results),
        total: effectiveMaxPages,
        message: `补爬 ${missingNavUrls.length} 个未成功的导航页面…`,
      });
      for (const pageUrl of missingNavUrls) {
        if (successfulUrls.has(pageUrl)) {
          continue;
        }
        try {
          const page = await crawlPage(pageUrl, { timeout: TIMEOUT, retries: MAX_RETRIES + 1 });
          if (page) {
            results.push(page);
            successfulUrls.add(pageUrl);
            reportProgress(onProgress, {
              phase: 'crawling',
              current: countContentPages(results),
              total: effectiveMaxPages,
              message: `补爬成功: ${shortPageLabel(pageUrl, currentDocBaseUrl)}`,
            });
          }
        } catch (error) {
          console.error(`Nav retry failed for ${pageUrl}: ${error.message}`);
          writeLog({
            level: 'ERROR',
            message: 'Failed to crawl page',
            url: pageUrl,
            error: error.message,
          });
        }
      }
    }

    console.log(`Discovered ${discovered.size} pages under ${currentDocBaseUrl}, crawled ${results.length}, excluded ${crawlStats.excludedCount}`);
  } catch (error) {
    console.error(`Failed to crawl index page ${baseUrl}: ${error.message}`);
  }

  if (siteNavTree.length && results.length) {
    results[0].siteNavTree = prefixNavTreeStoragePaths(siteNavTree, siteKey);
  }
  if (results.length) {
    const verification = verifyCrawlResults(results, DOCS_DIR, results[0].siteNavTree || siteNavTree, baseUrl);
    results[0].qualityReport = {
      ...(results[0].qualityReport || buildQualityReport(profile, results[0].siteNavTree || siteNavTree, results, crawlStats)),
      verification,
    };
    results[0].siteKey = siteKey;
    results[0].profileId = profile.id;
    if (!verification.passed) {
      console.warn(`Crawl verification: ${verification.missingFileCount} missing files, coverage ${verification.fileCoverage}`);
    } else {
      console.log(`Crawl verification passed (${verification.presentOnDisk}/${verification.crawledPages} on disk)`);
    }
  }

  console.log(`Crawled ${results.length} pages from ${baseUrl}`);
  return results;
  } finally {
    currentDocBaseUrl = SPRING_REFERENCE_BASE_URL;
    currentSiteProfile = null;
    currentSiteKey = null;
  }
}

/**
 * 获取章节路径
 */
function getChapterPath(url, docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  let pagePath = url.replace(base, '');
  if (pagePath.endsWith('.html')) {
    return pagePath.substring(0, pagePath.length - 5);
  }
  return pagePath;
}

function getPagePath(url, docBaseUrl = currentDocBaseUrl) {
  return getChapterPath(url, docBaseUrl);
}

/**
 * 发现直接子页面
 */
function discoverDirectSubPages(links, pagePath, discovered) {
  const subPages = [];
  const prefix = pagePath + '/';
  
  for (const link of links) {
    if (discovered.has(link)) continue;
    
    const linkPath = link.replace(SPRING_REFERENCE_BASE_URL, '');
    if (linkPath.startsWith(prefix) && linkPath.endsWith('.html')) {
      const relativePath = linkPath.substring(prefix.length);
      if (!relativePath.includes('/')) {
        subPages.push(link);
      }
    }
  }
  
  return subPages;
}

/**
 * 检查是否是顶级文档
 */
function isTopLevelDoc(url, docBaseUrl = currentDocBaseUrl) {
  const base = normalizeDocBaseUrl(docBaseUrl);
  const pagePath = url.replace(base, '');
  return /^[a-zA-Z0-9-]+\.html$/.test(pagePath);
}

/**
 * 提取标题
 */
function extractTitle($) {
  const h1 = $('h1.title, h1').first();
  if (h1.length) {
    return h1.text().trim();
  }

  const h2 = $('h2.title, h2').first();
  if (h2.length) {
    return h2.text().trim();
  }

  const titleElement = $('title');
  if (titleElement.length) {
    const title = titleElement.text().trim();
    if (title.includes('::')) {
      const parts = title.split('::').map((part) => part.trim()).filter(Boolean);
      if (parts.length > 0) {
        return parts[parts.length - 1];
      }
    }
    return title.split(/\s*[-|]\s*/)[0].trim();
  }

  return 'Untitled';
}

/**
 * 提取章节
 */
function extractSections($) {
  const sections = [];
  
  const sect1Elements = $('.sect1');
  if (sect1Elements.length) {
    sect1Elements.each((_, sect1) => {
      const docSection = extractAsciiDocSection($(sect1), 1, $);
      sections.push(docSection);
    });
  } else {
    return extractSectionsFromHeadings($);
  }

  return sections;
}

/**
 * 提取AsciiDoc章节
 */
function extractAsciiDocSection(sectionElement, level, $) {
  const section = {
    title: '',
    id: '',
    level: level,
    content: '',
    subsections: []
  };
  
  const titleElement = sectionElement.children('h2, h3, h4, h5, h6, .secttitle').first();
  if (titleElement.length) {
    section.title = titleElement.text().trim();
    section.id = titleElement.attr('id') || '';
  }
  
  let content = '';
  sectionElement.children().each((_, child) => {
    const element = $(child);
    const tagName = element[0].tagName.toLowerCase();
    
    if (/^h[1-6]$/.test(tagName)) return;
    if (element.hasClass('sect2') || element.hasClass('sect3') || 
        element.hasClass('sect4') || element.hasClass('sect5')) {
      return;
    }
    
    content += element.prop('outerHTML') + '\n';
  });
  
  section.content = content.trim();
  
  const nestedSectClass = `sect${level + 1}`;
  const nestedElements = sectionElement.find(`> .${nestedSectClass}`);
  if (nestedElements.length) {
    nestedElements.each((_, nested) => {
      const nestedSection = extractAsciiDocSection($(nested), level + 1, $);
      section.subsections.push(nestedSection);
    });
  }
  
  return section;
}

/**
 * 从标题提取章节
 */
function extractSectionsFromHeadings($) {
  const sections = [];
  const headingStack = [];
  
  const headings = $('h1, h2, h3, h4, h5, h6');
  
  headings.each((index, heading) => {
    const element = $(heading);
    const level = parseInt(heading.tagName[1]);
    const title = element.text().trim();
    const id = element.attr('id') || '';
    
    let content = '';
    let next = element.next();
    while (next.length && !/^h[1-6]$/.test(next[0].tagName.toLowerCase())) {
      content += next.prop('outerHTML') + '\n';
      next = next.next();
    }
    
    const newSection = {
      title,
      id,
      level,
      content: content.trim(),
      subsections: []
    };
    
    while (headingStack.length > 0 && headingStack[headingStack.length - 1].level >= level) {
      headingStack.pop();
    }
    
    if (headingStack.length === 0) {
      sections.push(newSection);
    } else {
      headingStack[headingStack.length - 1].subsections.push(newSection);
    }
    
    headingStack.push(newSection);
  });
  
  return sections;
}

/**
 * 提取链接
 */
function extractLinks($, pageUrl, docBaseUrl = currentDocBaseUrl) {
  const links = [];
  const baseUrl = normalizeDocBaseUrl(docBaseUrl);

  $('a[href]').each((_, link) => {
    let href = $(link).attr('href');

    if (!href) return;

    if (href.startsWith('/')) {
      try {
        href = new URL(href, baseUrl).href;
      } catch {
        href = 'https://docs.spring.io' + href;
      }
    } else if (!href.startsWith('http')) {
      const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
      href = basePath + href;
    }

    href = normalizeUrl(href);

    if (href.startsWith(baseUrl)) {
      const pageUrlOnly = href.split('#')[0];
      if (pageUrlOnly && !links.includes(pageUrlOnly)) {
        links.push(pageUrlOnly);
      }
    }
  });

  return links;
}

function listLocalHtmlFiles(category = null) {
  const files = [];

  function walk(dir, relativeDir) {
    for (const entry of fs.readdirSync(dir)) {
      const fullPath = path.join(dir, entry);
      const relativePath = relativeDir ? `${relativeDir}/${entry}` : entry;
      const stat = fs.statSync(fullPath);

      if (stat.isDirectory()) {
        if (entry === 'images' || entry === '_' || entry === 'translated') {
          continue;
        }
        
        const isCategoryDir = Object.values(CATEGORIES).includes(entry);
        if (category && isCategoryDir && entry !== category) {
          continue;
        }
        
        walk(fullPath, relativePath);
        continue;
      }

      if (entry.endsWith('.html') && !relativePath.startsWith('translated/')) {
        files.push({
          path: relativePath.replace(/\\/g, '/'),
          category: relativePath.split('/')[0] || 'other'
        });
      }
    }
  }

  walk(DOCS_DIR, '');
  return files.sort((a, b) => a.path.localeCompare(b.path));
}

function getCategoryStats() {
  const stats = {};
  const files = listLocalHtmlFiles();
  
  files.forEach(file => {
    const cat = file.category;
    if (!stats[cat]) {
      stats[cat] = { count: 0, files: [] };
    }
    stats[cat].count++;
    stats[cat].files.push(file.path);
  });
  
  return stats;
}

function addTabAriaAttributes($) {
  $('.tabs').each((_, tabsElement) => {
    const tabs = $(tabsElement);
    const tablist = tabs.find('.tablist ul');
    const tabsList = tablist.find('li.tab');
    const tabpanels = tabs.find('.tabpanel');

    tabsList.each((index, tab) => {
      const tabElement = $(tab);
      const tabId = tabElement.attr('id');
      const panel = tabpanels.eq(index);
      const panelId = panel.attr('id');

      if (panelId) {
        tabElement.attr('aria-controls', panelId);
      }
      if (tabId && panel.length) {
        panel.attr('aria-labelledby', tabId);
      }
    });
  });
}

async function rebuildLocalHtmlShell() {
  await ensureSpringAssets(DOCS_DIR);
  const files = listLocalHtmlFiles();
  const results = [];

  for (const file of files) {
    const localPath = file.path;
    const fullPath = path.join(DOCS_DIR, localPath);
    try {
      const html = fs.readFileSync(fullPath, 'utf8');
      const $ = cheerio.load(html);
      $('div.nav-container').remove();
      const main = $('main.article').first();
      if (!main.length) {
        continue;
      }

      main.find('script, .edit-this-page, .toolbar, nav.pagination, .nav-text, .sidebar-links').remove();

      addTabAriaAttributes($);

      const title = $('title').first().text().trim() || localPath;
      const updatedMain = $('main.article').first();
      const rebuilt = buildSpringDocumentHtml(
        title,
        updatedMain.prop('outerHTML') || '',
        '',
        localPath
      );
      fs.writeFileSync(fullPath, rebuilt, 'utf8');
      results.push({
        title,
        url: currentDocBaseUrl + categoryRelativePath(localPath),
        localPath,
        category: file.category
      });
      console.log(`Rebuilt shell: ${localPath}`);
    } catch (error) {
      console.error(`Failed to rebuild ${localPath}: ${error.message}`);
    }
  }

  return results;
}

async function refreshAllPages() {
  const rebuilt = await rebuildLocalHtmlShell();
  if (rebuilt.length > 0) {
    return rebuilt;
  }

  await ensureSpringAssets(DOCS_DIR);
  const files = listLocalHtmlFiles();
  const results = [];

  for (const file of files) {
    const localPath = file.path;
    const relativePath = categoryRelativePath(localPath);
    const url = currentDocBaseUrl + relativePath;
    try {
      const page = await crawlPage(url);
      results.push(page);
      console.log(`Refreshed format: ${localPath}`);
    } catch (error) {
      console.error(`Failed to refresh ${localPath}: ${error.message}`);
    }
  }

  return results;
}

/**
 * 通用页面爬取（支持任意URL）
 */
async function extractStructuredPageContent($, pageUrl, structure) {
  if (structure.type === 'antora') {
    const mainHtml = await extractMainArticle($, pageUrl, downloadImage, sanitizeElement);
    if (mainHtml) {
      return mainHtml;
    }
  }

  const main = $(structure.mainSelector).first().clone();
  if (!main.length) {
    return extractHtmlContent($, pageUrl);
  }

  main.find('script, style, .edit-this-page, .toolbar, nav.pagination, .nav-container, aside.nav, nav.nav-menu, .sidebar-links').remove();

  for (const img of main.find('img').toArray()) {
    const imgElement = $(img);
    const src = imgElement.attr('src');
    if (src) {
      const localPath = await downloadImage(src, pageUrl);
      if (localPath) {
        imgElement.attr('src', localPath);
      }
    }
  }

  main.find('*').each((_, nested) => sanitizeElement($(nested)));
  sanitizeElement(main);

  return main.prop('outerHTML') || '';
}

async function crawlGenericPage(url, basePath = '', siteBaseUrl = null) {
  console.log(`Crawling generic page: ${url}`);
  
  const startTime = new Date();
  const resolvedBaseUrl = siteBaseUrl || url;
  
  try {
    const response = await fetchWithRetry(url, {
      headers: { 
        'User-Agent': USER_AGENT, 
        'Referer': url,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive'
      },
      responseType: 'text'
    });

    const html = response.data;
    const $ = cheerio.load(html);
    const structure = structureDetector.detectStructure($);
    
    const title = extractTitle($);
    const links = extractGenericLinks($, url);
    const navTree = structureDetector.parseNavTree($, url, resolvedBaseUrl, structure);
    const breadcrumbsHtml = structureDetector.extractBreadcrumbs($, structure);
    const sidebarHtml = structureDetector.extractSidebarHtml($, structure, url, resolvedBaseUrl);
    const articleHtml = await extractStructuredPageContent($, url, structure);
    console.log(`Found ${links.length} links on page ${url} (structure: ${structure.type})`);
    if (links.length > 0 && links.length < 10) {
      console.log('Links:', links.join(', '));
    }
    
    const category = identifyCategory(url, title, articleHtml);
    const filename = urlToGenericLocalPath(url, basePath);
    const fullPath = getCategoryStoragePath(category, filename);
    fs.ensureDirSync(path.dirname(fullPath));

    const fullHtml = buildDocumentHtml(title, articleHtml, sidebarHtml, breadcrumbsHtml, structure);
    fs.writeFileSync(fullPath, fullHtml, 'utf8');
    
    const crawlTime = new Date() - startTime;
    
    console.log(`Saved generic page: ${category}/${filename}`);
    
    writeLog({
      level: 'INFO',
      message: 'Generic page crawled successfully',
      url,
      title,
      category,
      structureType: structure.type,
      localPath: `${category}/${filename}`,
      storagePath: fullPath,
      contentLength: articleHtml.length,
      crawlTimeMs: crawlTime
    });
    
    return {
      title,
      url,
      localPath: `${category}/${filename}`,
      storagePath: fullPath,
      category,
      links,
      navTree,
      structureType: structure.type
    };
  } catch (error) {
    console.error(`Failed to crawl generic page ${url}: ${error.message}`);
    writeLog({
      level: 'ERROR',
      message: 'Failed to crawl generic page',
      url,
      error: error.message
    });
    throw error;
  }
}

/**
 * 爬取视频页面，提取视频元数据
 */
async function crawlVideoPage(url, category = 'other') {
  console.log(`Crawling video page: ${url}, category: ${category}`);
  
  const startTime = new Date();
  
  try {
    let response;
    let html;
    
    const usePuppeteer = isPuppeteerAvailable() && !url.includes('lvtu777.com');
    
    if (usePuppeteer) {
      try {
        response = await fetchWithPuppeteer(url, { stealth: true, timeout: 30000 });
        html = response.data;
        console.log(`Successfully fetched ${url} with Puppeteer`);
      } catch (puppeteerError) {
        console.warn(`Puppeteer failed for ${url}: ${puppeteerError.message}, falling back to axios`);
        response = await fetchWithRetry(url, {
          headers: { ...VIDEO_HEADERS, Referer: url },
          responseType: 'text'
        });
        html = response.data;
      }
    } else {
      response = await fetchWithRetry(url, {
        headers: { ...VIDEO_HEADERS, Referer: url },
        responseType: 'text'
      });
      html = response.data;
    }

    const $ = cheerio.load(html);
    
    const title = extractTitle($);
    const videoData = extractVideoMetadata($, url);
    const links = extractGenericLinks($, url);
    
    const videoCategory = identifyVideoCategory(url, title, html);
    const actualCategory = category !== 'other' ? category : videoCategory;
    
    const videoDir = path.join(VIDEOS_DIR, actualCategory);
    fs.ensureDirSync(videoDir);
    
    const filename = urlToVideoLocalPath(url);
    const fullPath = path.join(videoDir, filename);
    
    const videoInfo = {
      url,
      title,
      category: actualCategory,
      videoData,
      crawledAt: new Date().toISOString()
    };
    
    fs.writeFileSync(fullPath, JSON.stringify(videoInfo, null, 2), 'utf8');
    
    const crawlTime = new Date() - startTime;
    
    console.log(`Saved video metadata: ${actualCategory}/${filename}`);
    
    writeLog({
      level: 'INFO',
      message: 'Video page crawled successfully',
      url,
      title,
      category: actualCategory,
      localPath: `${actualCategory}/${filename}`,
      storagePath: fullPath,
      videoInfo: videoData,
      crawlTimeMs: crawlTime
    });
    
    return {
      title,
      url,
      localPath: `${actualCategory}/${filename}`,
      storagePath: fullPath,
      category: actualCategory,
      videoData,
      links
    };
  } catch (error) {
    const errorDetails = error.response ? 
      `HTTP ${error.response.status}: ${error.response.statusText}` : 
      error.message;
    console.error(`Failed to crawl video page ${url}:`, error);
    console.error(`Error details: ${errorDetails}`);
    writeLog({
      level: 'ERROR',
      message: 'Failed to crawl video page',
      url,
      error: errorDetails,
      fullError: JSON.stringify(error, Object.getOwnPropertyNames(error))
    });
    throw error;
  }
}

/**
 * 爬取视频网站
 */
async function crawlVideoSite(url, maxPages = 100, category = 'other') {
  console.log(`Starting video crawl: ${url}, maxPages: ${maxPages}, category: ${category}`);
  
  const results = [];
  const visited = new Set();
  const baseDomain = new URL(url).hostname;
  
  try {
    // 先获取页面内容，根据内容识别分类
    let pageTitle = '';
    try {
      const response = await fetchWithRetry(url, {
        headers: { ...VIDEO_HEADERS, Referer: url },
        responseType: 'text'
      });
      const $ = cheerio.load(response.data);
      pageTitle = $('title').first().text().trim() || '';
    } catch (e) {
      console.warn(`Failed to fetch page title from ${url}: ${e.message}`);
    }
    
    const detectedCategory = identifyVideoCategory(url, pageTitle, '');
    const actualCategory = category !== 'other' ? category : detectedCategory;
    console.log(`Detected category: ${actualCategory} for URL: ${url}, title: ${pageTitle}`);
    
    // 检查是否是列表页，如果是则提取详情页链接并处理分页
    let pagesToCrawl = [];
    let detailPages = [];
    let otherVideoPages = [];
    
    if (url.includes('/list/')) {
      // 从列表页提取详情页链接
      const detailLinks = await extractVideoDetailLinksFromListPage(url, actualCategory);
      for (const link of detailLinks) {
        if (!visited.has(link)) {
          detailPages.push(link);
          visited.add(link);
        }
      }
      console.log(`Extracted ${detailLinks.length} video detail links from list page: ${url}`);
      
      // 提取分页链接并遍历所有分页
      const paginationLinks = await extractListPagePagination(url);
      for (const pageUrl of paginationLinks) {
        if (!visited.has(pageUrl)) {
          visited.add(pageUrl);
          console.log(`Crawling pagination page: ${pageUrl}`);
          const pageDetailLinks = await extractVideoDetailLinksFromListPage(pageUrl, actualCategory);
          for (const link of pageDetailLinks) {
            if (!visited.has(link)) {
              detailPages.push(link);
              visited.add(link);
            }
          }
          console.log(`Extracted ${pageDetailLinks.length} video detail links from ${pageUrl}, total: ${detailPages.length}`);
        }
      }
      console.log(`Total extracted ${detailPages.length} video detail links from all pages`);
      
      // 对于列表页，不保存列表页本身，只提取详情页链接
      // 详情页会在后面的循环中被爬取
    } else {
      // 普通视频页面
      const firstPage = await crawlVideoPage(url, actualCategory);
      results.push(firstPage);
      visited.add(url);
      
      for (const link of firstPage.links) {
        if (!visited.has(link) && isSameDomain(link, baseDomain)) {
          if (isVideoDetailLink(link)) {
            detailPages.push(link);
            visited.add(link);
          } else if (isVideoLink(link)) {
            otherVideoPages.push(link);
            visited.add(link);
          }
        }
      }
    }
    
    pagesToCrawl = [...detailPages, ...otherVideoPages];
    
    console.log(`Found ${detailPages.length} video detail pages, ${otherVideoPages.length} other video pages`);
    
    // 记录从列表页提取的详情页数量（这些不需要限制数量）
    const listPageDetailCount = detailPages.length;
    
    let idx = 0;
    while (idx < pagesToCrawl.length) {
      const pageUrl = pagesToCrawl[idx];
      idx++;
      
      // 对于列表页自动发现的详情页，限制数量
      // 对于从列表页直接提取的详情页，不限制数量
      const isFromListPage = idx <= listPageDetailCount;
      if (!isFromListPage && results.length >= maxPages) {
        break;
      }
      
      try {
        const page = await crawlVideoPage(pageUrl, actualCategory);
        results.push(page);
        
        // 只从列表页提取的详情页中继续发现新链接
        if (isFromListPage) {
          for (const link of page.links) {
            if (!visited.has(link) && isSameDomain(link, baseDomain)) {
              if (isVideoDetailLink(link)) {
                pagesToCrawl.splice(idx, 0, link);
                visited.add(link);
                idx++;
              } else if (isVideoLink(link)) {
                pagesToCrawl.push(link);
                visited.add(link);
              }
            }
          }
        }
      } catch (error) {
        console.error(`Failed to crawl video page ${pageUrl}: ${error.message}`);
      }
    }
    
    console.log(`Crawled ${results.length} video pages from ${url}`);
  } catch (error) {
    let errorDetails = '';
    if (error.response) {
      errorDetails = `HTTP ${error.response.status}: ${error.response.statusText}`;
    } else if (error.code) {
      errorDetails = error.code;
      if (error.code === 'ETIMEDOUT') {
        errorDetails = 'Connection timed out - the site may be unreachable from your network';
      } else if (error.code === 'ENOTFOUND') {
        errorDetails = 'Domain not found - please check the URL';
      }
    } else if (error.message) {
      errorDetails = error.message;
    } else {
      errorDetails = 'Unknown error';
    }
    console.error(`Failed to crawl video site ${url}:`, error);
    const wrappedError = new Error(`Failed to reach video site: ${errorDetails}`);
    wrappedError.originalError = error;
    throw wrappedError;
  }
  
  return results;
}

function isVideoDetailLink(url) {
  const urlLower = url.toLowerCase();
  // 排除列表页
  if (urlLower.includes('/list/')) {
    return false;
  }
  // 详情页 URL 模式
  return urlLower.includes('/movie/') || 
         urlLower.includes('/anime/') || 
         urlLower.includes('/tv/') || 
         urlLower.includes('/drama/') || 
         urlLower.includes('/variety/') ||
         urlLower.includes('/play/') ||
         urlLower.match(/\/\d+\.html$/);
}

// 从列表页 HTML 中提取视频详情页链接
async function extractVideoDetailLinksFromListPage(url, category) {
  console.log(`Extracting video detail links from list page: ${url}`);
  
  const detailLinks = [];
  
  try {
    const response = await fetchWithRetry(url, {
      headers: { ...VIDEO_HEADERS, Referer: url },
      responseType: 'text'
    });
    
    const $ = cheerio.load(response.data);
    
    // 从页面中提取视频详情页链接
    // 尝试多种可能的选择器
    const selectors = [
      '.myui-vodlist__thumb a[href]',
      '.myui-content__thumb a[href]',
      '.myui-vodlist li a[href]',
      '.myui-content__list li a[href]',
      '.list a[href]',
      '.vodlist a[href]',
      '.video-list a[href]',
      '.content a[href]',
      '.main a[href]',
      '.container a[href]',
      '.col a[href]',
      '.row a[href]',
      'ul li a[href]',
      'div a[href]',
      'a[href]'
    ];
    
    for (const selector of selectors) {
      $(selector).each((_, link) => {
        const href = $(link).attr('href');
        if (href) {
          let fullUrl = href;
          if (href.startsWith('/')) {
            const base = new URL(url);
            fullUrl = base.protocol + '//' + base.host + href;
          }
          
          if (isVideoDetailLink(fullUrl) && !detailLinks.includes(fullUrl)) {
            detailLinks.push(fullUrl);
          }
        }
      });
    }
    
    console.log(`Found ${detailLinks.length} video detail links from list page`);
  } catch (error) {
    console.error(`Failed to extract video detail links from ${url}: ${error.message}`);
  }
  
  return detailLinks;
}

/**
 * 从列表页提取所有分页链接
 */
async function extractListPagePagination(url) {
  const paginationLinks = [];
  
  try {
    const response = await fetchWithRetry(url, {
      headers: { ...VIDEO_HEADERS, Referer: url },
      responseType: 'text'
    });
    
    const $ = cheerio.load(response.data);
    
    // 尝试多种分页选择器
    const paginationSelectors = [
      '.myui-page a[href]',
      '.page a[href]',
      '.pagination a[href]',
      '.list-page a[href]',
      '.vod-page a[href]',
      '.page-list a[href]'
    ];
    
    const baseUrl = new URL(url);
    const basePath = baseUrl.pathname;
    
    for (const selector of paginationSelectors) {
      $(selector).each((_, link) => {
        const href = $(link).attr('href');
        if (href) {
          let fullUrl = href;
          if (href.startsWith('/')) {
            fullUrl = baseUrl.protocol + '//' + baseUrl.host + href;
          } else if (!href.startsWith('http')) {
            fullUrl = baseUrl.protocol + '//' + baseUrl.host + '/' + href;
          }
          
          // 检查是否是同一列表页的分页链接
          try {
            const parsed = new URL(fullUrl);
            if (parsed.hostname === baseUrl.host && parsed.pathname.startsWith('/list/')) {
              if (!paginationLinks.includes(fullUrl)) {
                paginationLinks.push(fullUrl);
              }
            }
          } catch (e) {
            // 忽略无效URL
          }
        }
      });
    }
    
    // 按页码排序
    paginationLinks.sort((a, b) => {
      const pageA = parseInt(a.match(/\/list\/(\d+)\.html/)?.[1] || '0');
      const pageB = parseInt(b.match(/\/list\/(\d+)\.html/)?.[1] || '0');
      return pageA - pageB;
    });
    
    console.log(`Found ${paginationLinks.length} pagination links`);
  } catch (error) {
    console.error(`Failed to extract pagination from ${url}: ${error.message}`);
  }
  
  return paginationLinks;
}

/**
 * 爬取通用网站
 */
async function crawlGenericSite(url, maxPages = 50, onProgress) {
  console.log(`Starting generic crawl: ${url}, maxPages: ${maxPages}`);

  reportProgress(onProgress, {
    phase: 'crawling',
    current: 0,
    total: maxPages,
    message: '正在分析页面结构…',
  });

  const results = [];
  const visited = new Set();
  const parsedUrl = new URL(url);
  const baseDomain = parsedUrl.hostname;
  const basePath = parsedUrl.pathname.replace(/\/index\.html$/, '').replace(/\/$/, '') + '/';
  const baseUrl = parsedUrl.protocol + '//' + parsedUrl.host + basePath;
  let siteNavTree = [];
  
  console.log(`Limiting crawl to URL prefix: ${baseUrl}`);
  
  try {
    const firstPage = await crawlGenericPage(url, basePath, baseUrl);
    results.push(firstPage);
    visited.add(url);
    if (firstPage.navTree && firstPage.navTree.length) {
      siteNavTree = firstPage.navTree;
    }
    
    let pagesToCrawl = [];
    for (const link of firstPage.links) {
      if (!visited.has(link) && isSameDomain(link, baseDomain) && link.startsWith(baseUrl)) {
        pagesToCrawl.push(link);
        visited.add(link);
      }
    }
    
    console.log(`Found ${pagesToCrawl.length} links to crawl`);

    let idx = 0;
    while (idx < pagesToCrawl.length && results.length < maxPages) {
      const batch = [];
      while (batch.length < CRAWL_CONCURRENCY && idx < pagesToCrawl.length && results.length + batch.length < maxPages) {
        const pageUrl = pagesToCrawl[idx];
        idx++;
        if (!visited.has(pageUrl)) {
          batch.push(pageUrl);
          visited.add(pageUrl);
        }
      }

      if (batch.length === 0) {
        break;
      }

      reportProgress(onProgress, {
        phase: 'crawling',
        current: results.length,
        total: Math.min(maxPages, Math.max(pagesToCrawl.length, results.length + batch.length)),
        message: `正在爬取 ${batch.length} 页（已完成 ${results.length}/${maxPages}）…`,
      });

      const batchResults = await Promise.all(
        batch.map(async (pageUrl) => {
          try {
            return { pageUrl, page: await crawlGenericPage(pageUrl, basePath, baseUrl) };
          } catch (error) {
            console.error(`Failed to crawl page ${pageUrl}: ${error.message}`);
            return { pageUrl, page: null };
          }
        })
      );

      for (const { pageUrl, page } of batchResults) {
        if (!page) {
          continue;
        }
        results.push(page);
        for (const link of page.links) {
          if (!visited.has(link) && isSameDomain(link, baseDomain) && link.startsWith(baseUrl)) {
            pagesToCrawl.push(link);
            visited.add(link);
          }
        }
        reportProgress(onProgress, {
          phase: 'crawling',
          current: results.length,
          total: Math.min(maxPages, Math.max(pagesToCrawl.length, results.length)),
          message: `已爬取: ${shortPageLabel(pageUrl, baseUrl)} (${results.length}/${maxPages})`,
        });
      }
    }
    
    console.log(`Crawled ${results.length} pages from ${url}`);
  } catch (error) {
    console.error(`Failed to crawl site ${url}: ${error.message}`);
  }
  
  if (siteNavTree.length && results.length) {
    const siteKey = deriveSiteKey(baseUrl);
    results[0].siteNavTree = prefixNavTreeStoragePaths(siteNavTree, siteKey);
  }
  
  return results;
}

/**
 * Unified document crawl: auto-detects Antora/Spring vs generic doc sites.
 */
async function crawlDocumentSite(baseUrl, maxPages = 50, onProgress, options = {}) {
  return withDocumentCrawlLock(async () => {
    baseUrl = assertValidBaseUrl(baseUrl);
    const customSiteKey = normalizeSiteKey(options.siteKey);
    const siteKey = customSiteKey || deriveSiteKey(baseUrl);

    await clearCrawledDocsForSite(siteKey, baseUrl);
    reportProgress(onProgress, {
      phase: 'crawling',
      current: 0,
      total: maxPages,
      message: `已清理 ${siteKey} 旧文件，正在检测站点结构…`,
    });

    try {
      const profileHint = detectProfile(baseUrl);
      const html = await fetchDocHtml(baseUrl, {
        headers: { 'User-Agent': USER_AGENT, Referer: baseUrl },
        requiresJs: profileHint.requiresJs,
      });
      const $ = cheerio.load(html);
      const profile = detectProfile(baseUrl, $);
      currentSiteProfile = profile;
      currentSiteKey = siteKey;
      currentDocBaseUrl = normalizeDocBaseUrl(baseUrl);

      console.log(`Detected profile "${profile.id}" for ${baseUrl} (discovery: ${profile.discovery}, siteKey: ${siteKey})`);
      await ensureShellAssets(DOCS_DIR, profile);

      return crawlMultiplePages(baseUrl, maxPages, onProgress, profile, siteKey);
    } catch (error) {
      console.warn(`Profile detection failed for ${baseUrl}, falling back to generic crawl: ${error.message}`);
      const profile = detectProfile(baseUrl);
      currentSiteKey = siteKey;
      currentDocBaseUrl = normalizeDocBaseUrl(baseUrl);
      return crawlMultiplePages(baseUrl, maxPages, onProgress, profile, siteKey);
    }
  });
}

/**
 * 检查URL是否在基础路径下
 */
function isWithinBasePath(urlStr, domain, basePath) {
  try {
    const url = new URL(urlStr);
    if (url.hostname !== domain) return false;
    
    let urlPath = url.pathname;
    let targetPath = basePath;
    
    // 移除末尾斜杠进行标准化比较
    if (urlPath.endsWith('/')) urlPath = urlPath.slice(0, -1);
    if (targetPath.endsWith('/')) targetPath = targetPath.slice(0, -1);
    
    // 检查URL路径是否以基础路径开头，或者URL是基础路径的直接子页面
    return urlPath.startsWith(targetPath) || urlPath + '/' === targetPath;
  } catch (e) {
    return false;
  }
}

/**
 * 从 JSON-LD 中提取视频元数据
 */
function extractFromJsonLd($) {
  const result = {};
  
  $('script[type="application/ld+json"]').each((_, script) => {
    try {
      const jsonStr = $(script).html();
      const json = JSON.parse(jsonStr);
      const items = Array.isArray(json) ? json : [json];
      
      for (const item of items) {
        const type = item['@type'] || '';
        
        if (type === 'VideoObject' || type === 'Movie' || type === 'TVSeries') {
          if (item.name) result.title = item.name;
          if (item.description) result.description = item.description;
          if (item.image) result.coverImage = Array.isArray(item.image) ? item.image[0] : item.image;
          if (item.url) result.pageUrl = item.url;
          
          if (item.genre) {
            result.genre = Array.isArray(item.genre) ? item.genre.join(' / ') : item.genre;
            result.category = result.genre;
          }
          
          if (item.aggregateRating) {
            result.rating = item.aggregateRating.ratingValue || '';
            result.ratingCount = item.aggregateRating.ratingCount || '';
          }
          
          if (item.actor) {
            result.actors = Array.isArray(item.actor) 
              ? item.actor.map(a => a.name || a).join(' / ')
              : (item.actor.name || item.actor);
          }
          
          if (item.director) {
            result.director = Array.isArray(item.director) 
              ? item.director.map(d => d.name || d).join(' / ')
              : (item.director.name || item.director);
          }
          
          if (item.dateCreated || item.datePublished) {
            result.year = (item.dateCreated || item.datePublished).substring(0, 4);
          }
          
          if (item.numberOfEpisodes) {
            result.totalEpisodes = item.numberOfEpisodes;
          }
          
          if (item.seasonNumber) {
            result.season = item.seasonNumber;
          }
          
          if (item.tags) {
            result.tags = Array.isArray(item.tags) ? item.tags.join(' / ') : item.tags;
          }
        }
      }
    } catch (e) {
      console.warn('Failed to parse JSON-LD:', e.message);
    }
  });
  
  return result;
}

/**
 * 提取 ani.gamer.com.tw 特定的视频元数据
 */
function extractAniGamerMeta($, pageUrl) {
  const result = {};
  
  result.title = $('.anime-name').text().trim() || 
                 $('h1').text().trim() || 
                 $('.title').text().trim() || '';
  
  let coverImage = $('.anime-cover img').attr('src') || 
                   $('.cover img').attr('src') || 
                   $('meta[property="og:image"]').attr('content') || '';
  
  if (coverImage && (coverImage.includes('load.png') || coverImage.includes('placeholder'))) {
    coverImage = $('img[alt*="封面"]').attr('src') || 
                 $('.poster img').attr('src') || 
                 $('.thumbnail img').attr('src') || 
                 '';
  }
  
  if (coverImage && coverImage.startsWith('/')) {
    const base = new URL(pageUrl);
    coverImage = base.protocol + '//' + base.host + coverImage;
  }
  
  result.coverImage = coverImage;
  
  const scoreEl = $('.score');
  if (scoreEl.length) {
    const scoreText = scoreEl.text().trim();
    const match = scoreText.match(/([\d.]+)/);
    result.rating = match ? match[1] : '';
  }
  
  $('.data-info').each((_, info) => {
    const text = $(info).text().trim();
    if (text.includes('分类') || text.includes('类型')) {
      result.category = text.replace(/分类[：:]/g, '').replace(/类型[：:]/g, '').trim();
    } else if (text.includes('地区')) {
      result.region = text.replace(/地区[：:]/g, '').trim();
    } else if (text.includes('年份')) {
      result.year = text.replace(/年份[：:]/g, '').trim();
    } else if (text.includes('更新')) {
      result.updateTime = text.replace(/更新[：:]/g, '').trim();
    }
  });
  
  const castEl = $('.cast');
  if (castEl.length) {
    const castText = castEl.text().trim();
    result.actors = castText.replace(/主演[：:]/g, '').trim();
  }
  
  const directorEl = $('.director');
  if (directorEl.length) {
    const directorText = directorEl.text().trim();
    result.director = directorText.replace(/导演[：:]/g, '').trim();
  }
  
  const introEl = $('.intro');
  if (introEl.length) {
    result.description = introEl.text().trim();
  }
  
  const episodes = [];
  $('.episode-list a, .episodes a, .play-list a').each((_, link) => {
    const episodeUrl = $(link).attr('href');
    const episodeTitle = $(link).text().trim();
    
    if (episodeUrl && episodeTitle) {
      let fullUrl = episodeUrl;
      if (episodeUrl.startsWith('/')) {
        const base = new URL(pageUrl);
        fullUrl = base.protocol + '//' + base.host + episodeUrl;
      } else if (!episodeUrl.startsWith('http')) {
        const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
        fullUrl = basePath + episodeUrl;
      }
      
      const episodeMatch = episodeTitle.match(/第(\d+)集/);
      episodes.push({
        title: episodeTitle,
        url: fullUrl,
        episodeNumber: episodeMatch ? parseInt(episodeMatch[1]) : episodes.length + 1
      });
    }
  });
  
  if (episodes.length > 0) {
    result.episodes = episodes;
    result.totalEpisodes = episodes.length;
  }
  
  const tags = [];
  $('.tags a, .tag a').each((_, link) => {
    tags.push($(link).text().trim());
  });
  if (tags.length > 0) {
    result.tags = tags.join(' / ');
  }
  
  return result;
}

/**
 * 提取 lvtu777.com 特定的视频元数据
 */
function extractLvtu777Meta($, pageUrl) {
  const result = {};
  
  const detailEl = $('.myui-content__detail');
  const thumbEl = $('.myui-vodlist__thumb') || $('.myui-content__thumb');
  
  if (detailEl.length) {
    result.title = detailEl.find('h1').text().trim() || $('h1').text().trim() || '';
    
    const match = result.title.match(/(.+?)\s+(更新|全集)/);
    if (match) {
      result.title = match[1].trim();
    }
    
    detailEl.find('.text-muted').each((_, el) => {
      const text = $(el).text().trim();
      if (text.includes('分类')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.category = nextEl.text().trim();
        }
      } else if (text.includes('地区')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.region = nextEl.text().trim();
        }
      } else if (text.includes('年份')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.year = nextEl.text().trim();
        }
      } else if (text.includes('主演')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.actors = nextEl.text().trim();
        }
      } else if (text.includes('导演')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.director = nextEl.text().trim();
        }
      } else if (text.includes('评分')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          const scoreText = nextEl.text().trim();
          const scoreMatch = scoreText.match(/(\d+(\.\d+)?)/);
          result.rating = scoreMatch ? scoreMatch[1] : '';
        }
      } else if (text.includes('状态')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          result.status = nextEl.text().trim();
        }
      } else if (text.includes('集数')) {
        const nextEl = $(el).next();
        if (nextEl.length) {
          const countText = nextEl.text().trim();
          const countMatch = countText.match(/(\d+)/);
          result.totalEpisodes = countMatch ? parseInt(countMatch[1]) : 0;
        }
      }
    });
    
    const scoreEl = detailEl.find('.score');
    if (scoreEl.length) {
      const scoreText = scoreEl.text().trim();
      const scoreMatch = scoreText.match(/(\d+(\.\d+)?)/);
      result.rating = scoreMatch ? scoreMatch[1] : '';
    }
    
    const introEl = detailEl.find('.tab-content, .intro-content, .description');
    if (introEl.length) {
      result.description = introEl.text().trim().substring(0, 500);
    }
    
    const tags = [];
    detailEl.find('.tags a, .tag a').each((_, link) => {
      tags.push($(link).text().trim());
    });
    if (tags.length > 0) {
      result.tags = tags.join(' / ');
    }
  }
  
  let coverImage = '';
  
  if (thumbEl && thumbEl.length) {
    const coverImg = thumbEl.find('img').first();
    if (coverImg.length) {
      coverImage = coverImg.attr('data-original') || 
                   coverImg.attr('data-src') || 
                   coverImg.attr('data-lazy-src') || 
                   coverImg.attr('src') || '';
    }
  }
  
  if (!coverImage || coverImage.includes('load.png') || coverImage.includes('placeholder')) {
    const lazyImgs = $('img.lazyload');
    if (lazyImgs.length > 0) {
      const firstLazyImg = lazyImgs.eq(0);
      coverImage = firstLazyImg.attr('data-original') || 
                   firstLazyImg.attr('data-src') || 
                   firstLazyImg.attr('src') || '';
    }
  }
  
  if (!coverImage || coverImage.includes('load.png') || coverImage.includes('placeholder')) {
    coverImage = $('meta[property="og:image"]').attr('content') || 
                 $('meta[name="og:image"]').attr('content') || 
                 $('.cover img').attr('data-original') || 
                 $('.cover img').attr('data-src') || 
                 $('.cover img').attr('src') || 
                 $('img[alt*="封面"]').attr('data-original') || 
                 $('img[alt*="封面"]').attr('data-src') || 
                 $('img[alt*="封面"]').attr('src') || 
                 $('.poster img').attr('data-original') || 
                 $('.poster img').attr('data-src') || 
                 $('.poster img').attr('src') || 
                 $('.video-cover img').attr('data-original') || 
                 $('.video-cover img').attr('data-src') || 
                 $('.video-cover img').attr('src') || 
                 $('img[class*="cover"]').attr('data-original') || 
                 $('img[class*="cover"]').attr('data-src') || 
                 $('img[class*="cover"]').attr('src') || 
                 '';
  }
  
  if (coverImage && coverImage.includes('load.png')) {
    const alternativeImgs = $('img').not('[src*="load.png"]').not('[src*="placeholder"]');
    if (alternativeImgs.length > 0) {
      coverImage = alternativeImgs.eq(0).attr('data-original') || 
                   alternativeImgs.eq(0).attr('data-src') || 
                   alternativeImgs.eq(0).attr('src') || '';
    }
  }
  
  if (coverImage && coverImage.startsWith('/')) {
    const base = new URL(pageUrl);
    coverImage = base.protocol + '//' + base.host + coverImage;
  }
  
  result.coverImage = coverImage;
  
  const episodes = [];
  $('.myui-content__list li a, .myui-vodlist__detail a').each((_, link) => {
    const episodeUrl = $(link).attr('href');
    const episodeTitle = $(link).text().trim();
    
    if (episodeUrl && episodeTitle) {
      let fullUrl = episodeUrl;
      if (episodeUrl.startsWith('/')) {
        const base = new URL(pageUrl);
        fullUrl = base.protocol + '//' + base.host + episodeUrl;
      } else if (!episodeUrl.startsWith('http')) {
        const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
        fullUrl = basePath + episodeUrl;
      }
      
      const episodeMatch = episodeTitle.match(/第(\d+)集/);
      episodes.push({
        title: episodeTitle,
        url: fullUrl,
        episodeNumber: episodeMatch ? parseInt(episodeMatch[1]) : episodes.length + 1
      });
    }
  });
  
  if (episodes.length > 0) {
    result.episodes = episodes;
    result.totalEpisodes = episodes.length;
  }
  
  return result;
}

/**
 * 提取 bilibili.com 特定的视频元数据
 */
function extractBilibiliMeta($, pageUrl) {
  const result = {};
  
  result.title = $('h1').text().trim() || 
                 $('meta[property="og:title"]').attr('content') || '';
  
  result.description = $('meta[name="description"]').attr('content') || 
                       $('meta[property="og:description"]').attr('content') || '';
  
  result.coverImage = $('meta[property="og:image"]').attr('content') || 
                      $('.cover img').attr('src') || 
                      $('img[alt*="封面"]').attr('src') || '';
  
  result.pageUrl = $('meta[property="og:url"]').attr('content') || pageUrl;
  
  $('script').each((_, script) => {
    const content = $(script).html();
    if (!content) return;
    
    const initialStateMatch = content.match(/window\.__INITIAL_STATE__\s*=\s*({[\s\S]*?});/);
    if (initialStateMatch) {
      try {
        const state = JSON.parse(initialStateMatch[1]);
        
        if (state.bangumi && state.bangumi.currentEpisode) {
          const episode = state.bangumi.currentEpisode;
          if (episode.index) result.title = episode.index;
          if (episode.title) result.title += ' ' + episode.title;
        }
        
        if (state.bangumi && state.bangumi.mediaInfo) {
          const media = state.bangumi.mediaInfo;
          if (media.title) result.title = media.title;
          if (media.evaluate && media.evaluate.score) result.rating = media.evaluate.score;
          if (media.evaluate && media.evaluate.count) result.ratingCount = media.evaluate.count;
          if (media.cover) result.coverImage = media.cover;
          if (media.intro) result.description = media.intro;
          if (media.totalEpisodes) result.totalEpisodes = media.totalEpisodes;
          if (media.seasonTitle) result.season = media.seasonTitle;
          if (media.isFinish === 1) result.status = '已完结';
          else if (media.isFinish === 0) result.status = '连载中';
          if (media.typeName) result.category = media.typeName;
          if (media.styles && media.styles.length) result.genre = media.styles.join(' / ');
        }
        
        if (state.bangumi && state.bangumi.episodes) {
          const episodes = [];
          state.bangumi.episodes.forEach((ep, index) => {
            episodes.push({
              title: ep.index + (ep.title ? ' ' + ep.title : ''),
              url: pageUrl.replace(/ep\d+/, 'ep' + ep.id),
              episodeNumber: index + 1
            });
          });
          if (episodes.length > 0) {
            result.episodes = episodes;
            result.totalEpisodes = episodes.length;
          }
        }
        
        if (state.videoData) {
          const video = state.videoData;
          if (video.title) result.title = video.title;
          if (video.desc) result.description = video.desc;
          if (video.pic) result.coverImage = video.pic;
          if (video.view) result.views = video.view;
          if (video.author && video.author.name) result.author = video.author.name;
          if (video.stat && video.stat.coin) result.rating = video.stat.coin;
        }
        
      } catch (e) {
        console.warn('Failed to parse bilibili INITIAL_STATE:', e.message);
      }
    }
    
    const playInfoMatch = content.match(/playinfo\s*=\s*({[\s\S]*?});/);
    if (playInfoMatch) {
      try {
        const playinfo = JSON.parse(playInfoMatch[1]);
        if (playinfo.dash && playinfo.dash.video) {
          playinfo.dash.video.forEach(stream => {
            if (stream.baseUrl) {
              if (!result.videoUrls) result.videoUrls = [];
              if (!result.videoUrls.includes(stream.baseUrl)) {
                result.videoUrls.push(stream.baseUrl);
              }
            }
          });
        }
      } catch (e) {
        console.warn('Failed to parse bilibili playinfo:', e.message);
      }
    }
  });
  
  const tags = [];
  $('.tag a, .video-tag, .bangumi-tag').each((_, link) => {
    tags.push($(link).text().trim());
  });
  if (tags.length > 0) {
    result.tags = tags.join(' / ');
  }
  
  return result;
}

/**
 * 提取 iqiyi.com 特定的视频元数据
 */
function extractIqiyiMeta($, pageUrl) {
  const result = {};
  
  result.title = $('h1').text().trim() || 
                 $('.video-title').text().trim() || 
                 $('meta[property="og:title"]').attr('content') || '';
  
  result.description = $('meta[name="description"]').attr('content') || 
                       $('meta[property="og:description"]').attr('content') || '';
  
  result.coverImage = $('meta[property="og:image"]').attr('content') || 
                      $('.album-pic img').attr('src') || 
                      $('.video-cover img').attr('src') || '';
  
  $('script').each((_, script) => {
    const content = $(script).html();
    if (!content) return;
    
    const videoInfoMatch = content.match(/var\s+videoInfo\s*=\s*({[\s\S]*?});/);
    if (videoInfoMatch) {
      try {
        const info = JSON.parse(videoInfoMatch[1]);
        if (info.name) result.title = info.name;
        if (info.description) result.description = info.description;
        if (info.imageUrl) result.coverImage = info.imageUrl;
        if (info.score) result.rating = info.score;
        if (info.categories) result.category = info.categories.join(' / ');
        if (info.actors) result.actors = info.actors.join(' / ');
        if (info.director) result.director = info.director;
        if (info.year) result.year = info.year;
        if (info.area) result.region = info.area;
        if (info.totalCount) result.totalEpisodes = info.totalCount;
      } catch (e) {
        console.warn('Failed to parse iqiyi videoInfo:', e.message);
      }
    }
  });
  
  return result;
}

/**
 * 提取 youku.com 特定的视频元数据
 */
function extractYoukuMeta($, pageUrl) {
  const result = {};
  
  result.title = $('h1').text().trim() || 
                 $('meta[property="og:title"]').attr('content') || '';
  
  result.description = $('meta[name="description"]').attr('content') || 
                       $('meta[property="og:description"]').attr('content') || '';
  
  result.coverImage = $('meta[property="og:image"]').attr('content') || '';
  
  $('script').each((_, script) => {
    const content = $(script).html();
    if (!content) return;
    
    const playerMatch = content.match(/player\s*=\s*({[\s\S]*?});/);
    if (playerMatch) {
      try {
        const player = JSON.parse(playerMatch[1]);
        if (player.videoMeta) {
          const meta = player.videoMeta;
          if (meta.title) result.title = meta.title;
          if (meta.description) result.description = meta.description;
          if (meta.image) result.coverImage = meta.image;
          if (meta.score) result.rating = meta.score;
          if (meta.categories) result.category = meta.categories.join(' / ');
          if (meta.actors) result.actors = meta.actors.join(' / ');
          if (meta.director) result.director = meta.director;
          if (meta.year) result.year = meta.year;
          if (meta.area) result.region = meta.area;
          if (meta.totalEpisode) result.totalEpisodes = meta.totalEpisode;
        }
      } catch (e) {
        console.warn('Failed to parse youku player:', e.message);
      }
    }
  });
  
  return result;
}

/**
 * 提取通用视频页面元数据（作为后备）
 */
function extractGenericVideoMeta($, pageUrl) {
  const result = {};
  
  result.title = $('h1').first().text().trim() || 
                 $('title').first().text().trim() || 
                 $('.video-title').first().text().trim() || 
                 $('.title').first().text().trim() || '';
  
  result.description = $('meta[name="description"]').attr('content') || 
                       $('meta[property="og:description"]').attr('content') || 
                       $('.description').text().trim() || 
                       $('.intro').text().trim() || 
                       $('.synopsis').text().trim() || '';
  
  let coverImage = $('meta[property="og:image"]').attr('content') || 
                   $('meta[name="twitter:image"]').attr('content') || 
                   '';
  
  if (!coverImage || coverImage.includes('load.png') || coverImage.includes('placeholder')) {
    coverImage = $('.cover img').attr('src') || 
                 $('img[alt*="cover"]').attr('src') || 
                 $('.poster img').attr('src') || 
                 $('.thumbnail img').attr('src') || 
                 $('.video-cover img').attr('src') || 
                 $('img[class*="cover"]').attr('src') || 
                 '';
  }
  
  if (coverImage && coverImage.includes('load.png')) {
    const alternativeImgs = $('img').not('[src*="load.png"]').not('[src*="placeholder"]');
    if (alternativeImgs.length > 0) {
      coverImage = alternativeImgs.eq(0).attr('src') || '';
    }
  }
  
  if (coverImage && coverImage.startsWith('/')) {
    const base = new URL(pageUrl);
    coverImage = base.protocol + '//' + base.host + coverImage;
  }
  
  result.coverImage = coverImage;
  
  result.pageUrl = $('meta[property="og:url"]').attr('content') || pageUrl;
  
  $('meta').each((_, meta) => {
    const name = $(meta).attr('name') || '';
    const property = $(meta).attr('property') || '';
    const content = $(meta).attr('content') || '';
    
    if (name.includes('rating') || property.includes('rating')) {
      result.rating = content;
    } else if (name.includes('genre') || property.includes('genre')) {
      result.genre = content;
      result.category = content;
    } else if (name.includes('actor') || property.includes('actor')) {
      result.actors = content;
    } else if (name.includes('director') || property.includes('director')) {
      result.director = content;
    } else if (name.includes('year') || property.includes('year')) {
      result.year = content;
    } else if (name.includes('country') || property.includes('country')) {
      result.region = content;
    } else if (name.includes('status') || property.includes('status')) {
      result.status = content;
    } else if (name.includes('count') || property.includes('count')) {
      if (content.match(/\d+/)) {
        result.totalEpisodes = parseInt(content.match(/\d+/)[0]);
      }
    }
  });
  
  const infoSelectors = [
    '.info', '.detail-info', '.meta-info', '.video-info', 
    '.movie-info', '.film-info', '.anime-info', '.data-info',
    '.vod-info', '.content-info', '.video-meta', '.basic-info'
  ];
  
  for (const selector of infoSelectors) {
    $(selector).each((_, info) => {
      const text = $(info).text().trim();
      if (text.includes('分类') || text.includes('类型')) {
        const match = text.match(/(分类|类型)[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.category = match[2].trim();
        }
      } else if (text.includes('地区')) {
        const match = text.match(/地区[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.region = match[2].trim();
        }
      } else if (text.includes('年份')) {
        const match = text.match(/年份[：:](\d{4})/);
        if (match) {
          result.year = match[1];
        }
      } else if (text.includes('主演')) {
        const match = text.match(/主演[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.actors = match[2].trim();
        }
      } else if (text.includes('导演')) {
        const match = text.match(/导演[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.director = match[2].trim();
        }
      } else if (text.includes('评分')) {
        const match = text.match(/评分[：:]([\d.]+)/);
        if (match) {
          result.rating = match[1];
        }
      } else if (text.includes('集数')) {
        const match = text.match(/集数[：:](\d+)/);
        if (match) {
          result.totalEpisodes = parseInt(match[1]);
        }
      } else if (text.includes('更新')) {
        const match = text.match(/更新[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.updateTime = match[2].trim();
        }
      } else if (text.includes('状态')) {
        const match = text.match(/状态[：:](.+?)([，,。.]|$)/);
        if (match) {
          result.status = match[2].trim();
        }
      }
    });
  }
  
  const scoreSelectors = ['.score', '.rating', '.imdb-rating', '.douban-rating', '.video-score'];
  for (const selector of scoreSelectors) {
    const scoreEl = $(selector).first();
    if (scoreEl.length && !result.rating) {
      const scoreText = scoreEl.text().trim();
      const scoreMatch = scoreText.match(/([\d.]+)/);
      if (scoreMatch) {
        result.rating = scoreMatch[1];
      }
    }
  }
  
  const episodes = [];
  const episodeSelectors = [
    '.episode-list a', '.episodes a', '.play-list a', '.video-list a',
    '.vodlist a', '.list-episode a', '.list-item a', '.media-list a',
    'a[href*="episode"]', 'a[href*="play"]', 'a[href*="/v/"]', '.episode-item a'
  ];
  
  for (const selector of episodeSelectors) {
    $(selector).each((_, link) => {
      const episodeUrl = $(link).attr('href');
      const episodeTitle = $(link).text().trim();
      
      if (episodeUrl && episodeTitle && episodeTitle.length > 0) {
        let fullUrl = episodeUrl;
        if (episodeUrl.startsWith('/')) {
          const base = new URL(pageUrl);
          fullUrl = base.protocol + '//' + base.host + episodeUrl;
        } else if (!episodeUrl.startsWith('http')) {
          const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
          fullUrl = basePath + episodeUrl;
        }
        
        if (!episodes.some(e => e.url === fullUrl)) {
          const episodeMatch = episodeTitle.match(/第(\d+)集/);
          episodes.push({
            title: episodeTitle,
            url: fullUrl,
            episodeNumber: episodeMatch ? parseInt(episodeMatch[1]) : episodes.length + 1
          });
        }
      }
    });
  }
  
  if (episodes.length > 0) {
    result.episodes = episodes;
    if (!result.totalEpisodes) {
      result.totalEpisodes = episodes.length;
    }
  }
  
  const tags = [];
  const tagSelectors = ['.tags a', '.tag a', '.video-tag a', '.movie-tag a', '.anime-tag a'];
  for (const selector of tagSelectors) {
    $(selector).each((_, link) => {
      const tagText = $(link).text().trim();
      if (tagText && !tags.includes(tagText)) {
        tags.push(tagText);
      }
    });
  }
  if (tags.length > 0) {
    result.tags = tags.join(' / ');
  }
  
  return result;
}

/**
 * 提取视频元数据（三层提取策略）
 */
function extractVideoMetadata($, pageUrl) {
  const videoData = {
    title: '',
    description: '',
    coverImage: '',
    thumbnail: '',
    pageUrl: pageUrl,
    videoUrls: [],
    m3u8Urls: [],
    duration: '',
    views: '',
    uploadDate: '',
    author: '',
    category: '',
    genre: '',
    region: '',
    rating: '',
    ratingCount: '',
    actors: '',
    director: '',
    episodes: [],
    totalEpisodes: 0,
    year: '',
    season: '',
    status: '',
    tags: '',
    updateTime: ''
  };
  
  const jsonLdData = extractFromJsonLd($);
  Object.assign(videoData, jsonLdData);
  
  const urlHost = new URL(pageUrl).hostname;
  
  if (urlHost.includes('ani.gamer.com.tw')) {
    const aniGamerData = extractAniGamerMeta($, pageUrl);
    Object.keys(aniGamerData).forEach(key => {
      if (aniGamerData[key]) videoData[key] = aniGamerData[key];
    });
  } else if (urlHost.includes('lvtu777.com')) {
    const lvtu777Data = extractLvtu777Meta($, pageUrl);
    Object.keys(lvtu777Data).forEach(key => {
      if (lvtu777Data[key]) videoData[key] = lvtu777Data[key];
    });
  } else if (urlHost.includes('bilibili.com')) {
    const bilibiliData = extractBilibiliMeta($, pageUrl);
    Object.keys(bilibiliData).forEach(key => {
      if (bilibiliData[key]) videoData[key] = bilibiliData[key];
    });
  } else if (urlHost.includes('qq.com')) {
    const qqData = extractQqVideoMeta($, pageUrl);
    Object.keys(qqData).forEach(key => {
      if (qqData[key]) videoData[key] = qqData[key];
    });
  } else if (urlHost.includes('iqiyi.com')) {
    const iqiyiData = extractIqiyiMeta($, pageUrl);
    Object.keys(iqiyiData).forEach(key => {
      if (iqiyiData[key]) videoData[key] = iqiyiData[key];
    });
  } else if (urlHost.includes('youku.com')) {
    const youkuData = extractYoukuMeta($, pageUrl);
    Object.keys(youkuData).forEach(key => {
      if (youkuData[key]) videoData[key] = youkuData[key];
    });
  }
  
  const inlineJsonData = extractFromInlineScripts($);
  Object.keys(inlineJsonData).forEach(key => {
    if (!videoData[key] && inlineJsonData[key]) {
      videoData[key] = inlineJsonData[key];
    }
  });
  
  const genericData = extractGenericVideoMeta($, pageUrl);
  Object.keys(genericData).forEach(key => {
    if (!videoData[key] && genericData[key]) {
      videoData[key] = genericData[key];
    }
  });
  
  videoData.videoUrls = findM3U8Links($, pageUrl);
  videoData.m3u8Urls = videoData.videoUrls.filter(u => u.includes('.m3u8'));
  
  if (!videoData.thumbnail && videoData.coverImage) {
    videoData.thumbnail = videoData.coverImage;
  }
  
  return videoData;
}

/**
 * 从内联脚本中提取视频数据
 */
function extractFromInlineScripts($) {
  const result = {};
  
  $('script').each((_, script) => {
    const content = $(script).html();
    if (!content) return;
    
    const dataMatches = [
      /window\.__INITIAL_STATE__\s*=\s*({[\s\S]*?});/,
      /window\.__data__\s*=\s*({[\s\S]*?});/,
      /window\.__videoInfo__\s*=\s*({[\s\S]*?});/,
      /window\.__INITIAL_DATA__\s*=\s*({[\s\S]*?});/,
      /window\.initialState\s*=\s*({[\s\S]*?});/,
      /var\s+__INITIAL_STATE__\s*=\s*({[\s\S]*?});/,
      /var\s+videoInfo\s*=\s*({[\s\S]*?});/,
      /var\s+playerInfo\s*=\s*({[\s\S]*?});/,
      /var\s+albumInfo\s*=\s*({[\s\S]*?});/,
      /var\s+bangumiInfo\s*=\s*({[\s\S]*?});/,
      /\bvideoInfo\s*=\s*({[\s\S]*?});/,
      /\bplayerInfo\s*=\s*({[\s\S]*?});/,
      /\balbumInfo\s*=\s*({[\s\S]*?});/,
      /\bbangumiInfo\s*=\s*({[\s\S]*?});/,
      /__NEXT_DATA__\s*=\s*({[\s\S]*?})<\/script>/
    ];
    
    for (const pattern of dataMatches) {
      const match = content.match(pattern);
      if (match) {
        try {
          const data = JSON.parse(match[1]);
          extractFromParsedData(data, result);
        } catch (e) {
          console.warn('Failed to parse inline script data:', e.message);
        }
        break;
      }
    }
  });
  
  return result;
}

function extractFromParsedData(data, result) {
  if (!data || typeof data !== 'object') return;
  
  const keys = Object.keys(data);
  
  if (data.title) result.title = data.title;
  if (data.name) result.title = data.name;
  if (data.description) result.description = data.description;
  if (data.summary) result.description = data.summary;
  if (data.intro) result.description = data.intro;
  
  if (data.image) {
    result.coverImage = Array.isArray(data.image) ? data.image[0] : data.image;
  }
  if (data.cover) result.coverImage = data.cover;
  if (data.coverImage) result.coverImage = data.coverImage;
  if (data.poster) result.coverImage = data.poster;
  if (data.thumbnail) result.thumbnail = data.thumbnail;
  
  if (data.rating) result.rating = data.rating;
  if (data.score) result.rating = data.score;
  if (data.ratingValue) result.rating = data.ratingValue;
  
  if (data.category) result.category = data.category;
  if (data.genre) result.genre = data.genre;
  if (data.type) result.category = data.type;
  
  if (data.region) result.region = data.region;
  if (data.area) result.region = data.area;
  
  if (data.year) result.year = data.year;
  if (data.releaseYear) result.year = data.releaseYear;
  
  if (data.actors) result.actors = Array.isArray(data.actors) ? data.actors.join(' / ') : data.actors;
  if (data.actor) result.actors = Array.isArray(data.actor) ? data.actor.join(' / ') : data.actor;
  
  if (data.director) result.director = Array.isArray(data.director) ? data.director.join(' / ') : data.director;
  
  if (data.totalEpisodes) result.totalEpisodes = data.totalEpisodes;
  if (data.episodeCount) result.totalEpisodes = data.episodeCount;
  if (data.numberOfEpisodes) result.totalEpisodes = data.numberOfEpisodes;
  
  if (data.status) result.status = data.status;
  if (data.updateStatus) result.status = data.updateStatus;
  
  if (data.tags) result.tags = Array.isArray(data.tags) ? data.tags.join(' / ') : data.tags;
  if (data.tag) result.tags = Array.isArray(data.tag) ? data.tag.join(' / ') : data.tag;
  
  if (data.views) result.views = data.views;
  if (data.playCount) result.views = data.playCount;
  
  for (const key of keys) {
    if (typeof data[key] === 'object') {
      extractFromParsedData(data[key], result);
    }
  }
}

/**
 * 提取 QQ视频 特定的视频元数据
 */
function extractQqVideoMeta($, pageUrl) {
  const result = {};
  
  result.title = $('h1').text().trim() || 
                 $('.video-title').text().trim() || 
                 $('meta[property="og:title"]').attr('content') || 
                 $('meta[name="title"]').attr('content') || '';
  
  result.description = $('meta[name="description"]').attr('content') || 
                       $('meta[property="og:description"]').attr('content') || '';
  
  result.coverImage = $('meta[property="og:image"]').attr('content') || 
                      $('.poster img').attr('src') || 
                      $('.cover img').attr('src') || 
                      $('.video-cover img').attr('src') || '';
  
  $('script').each((_, script) => {
    const content = $(script).html();
    if (!content) return;
    
    const videoInfoMatch = content.match(/var\s+videoInfo\s*=\s*({[\s\S]*?});/);
    if (videoInfoMatch) {
      try {
        const info = JSON.parse(videoInfoMatch[1]);
        if (info.title) result.title = info.title;
        if (info.name) result.title = info.name;
        if (info.description) result.description = info.description;
        if (info.image) result.coverImage = info.image;
        if (info.cover) result.coverImage = info.cover;
        if (info.score) result.rating = info.score;
        if (info.rating) result.rating = info.rating;
        if (info.category) result.category = info.category;
        if (info.genre) result.genre = info.genre;
        if (info.actors) result.actors = Array.isArray(info.actors) ? info.actors.join(' / ') : info.actors;
        if (info.actor) result.actors = Array.isArray(info.actor) ? info.actor.join(' / ') : info.actor;
        if (info.director) result.director = Array.isArray(info.director) ? info.director.join(' / ') : info.director;
        if (info.year) result.year = info.year;
        if (info.region) result.region = info.region;
        if (info.area) result.region = info.area;
        if (info.totalEpisode) result.totalEpisodes = info.totalEpisode;
        if (info.episodeCount) result.totalEpisodes = info.episodeCount;
        if (info.status) result.status = info.status;
        if (info.tags) result.tags = Array.isArray(info.tags) ? info.tags.join(' / ') : info.tags;
      } catch (e) {
        console.warn('Failed to parse qq videoInfo:', e.message);
      }
    }
    
    const playerMatch = content.match(/QZPlayer\(\s*({[\s\S]*?})\s*\)/);
    if (playerMatch) {
      try {
        const player = JSON.parse(playerMatch[1]);
        if (player.title) result.title = player.title;
        if (player.cover) result.coverImage = player.cover;
        if (player.videos) {
          const videos = Array.isArray(player.videos) ? player.videos : [player.videos];
          videos.forEach(v => {
            if (v.url) {
              if (!result.videoUrls) result.videoUrls = [];
              if (!result.videoUrls.includes(v.url)) {
                result.videoUrls.push(v.url);
              }
            }
          });
        }
      } catch (e) {
        console.warn('Failed to parse QZPlayer config:', e.message);
      }
    }
    
    const episodeMatch = content.match(/episodeList\s*=\s*({[\s\S]*?});/);
    if (episodeMatch) {
      try {
        const episodeData = JSON.parse(episodeMatch[1]);
        if (episodeData.episodes) {
          result.episodes = episodeData.episodes.map((e, i) => ({
            title: e.title || `第${i + 1}集`,
            url: e.url || '',
            episodeNumber: e.number || i + 1
          }));
          result.totalEpisodes = result.episodes.length;
        }
      } catch (e) {
        console.warn('Failed to parse episodeList:', e.message);
      }
    }
  });
  
  const infoItems = $('.video-info-item, .info-item, .meta-item');
  infoItems.each((_, item) => {
    const text = $(item).text().trim();
    if (text.includes('分类') || text.includes('类型')) {
      const match = text.match(/(分类|类型)[：:](.+)/);
      if (match) result.category = match[2].trim();
    } else if (text.includes('地区')) {
      const match = text.match(/地区[：:](.+)/);
      if (match) result.region = match[2].trim();
    } else if (text.includes('年份')) {
      const match = text.match(/年份[：:](\d{4})/);
      if (match) result.year = match[1];
    } else if (text.includes('主演')) {
      const match = text.match(/主演[：:](.+)/);
      if (match) result.actors = match[2].trim();
    } else if (text.includes('导演')) {
      const match = text.match(/导演[：:](.+)/);
      if (match) result.director = match[2].trim();
    } else if (text.includes('评分')) {
      const match = text.match(/评分[：:]([\d.]+)/);
      if (match) result.rating = match[1];
    } else if (text.includes('集数')) {
      const match = text.match(/集数[：:](\d+)/);
      if (match) result.totalEpisodes = parseInt(match[1]);
    } else if (text.includes('更新')) {
      const match = text.match(/更新[：:](.+)/);
      if (match) result.updateTime = match[2] ? match[2].trim() : match[1].trim();
    }
  });
  
  return result;
}

/**
 * 提取通用链接
 */
function extractGenericLinks($, pageUrl) {
  const links = [];
  
  $('a[href]').each((_, link) => {
    let href = $(link).attr('href');
    
    if (!href) return;
    
    if (href.startsWith('/')) {
      const base = new URL(pageUrl);
      href = base.protocol + '//' + base.host + href;
    } else if (!href.startsWith('http')) {
      const basePath = pageUrl.substring(0, pageUrl.lastIndexOf('/') + 1);
      href = basePath + href;
    }
    
    href = normalizeUrl(href);
    
    if (href.startsWith('http') && !links.includes(href)) {
      links.push(href);
    }
  });
  
  return links;
}

/**
 * 检查是否是视频链接
 */
function isVideoLink(url) {
  const urlLower = url.toLowerCase();
  return VIDEO_URL_PATTERNS.some(pattern => urlLower.includes(pattern.toLowerCase()));
}

/**
 * 检查是否同域名
 */
function isSameDomain(url, baseDomain) {
  try {
    const urlDomain = new URL(url).hostname;
    return urlDomain === baseDomain || urlDomain.endsWith('.' + baseDomain);
  } catch {
    return false;
  }
}

/**
 * 通用URL转换为本地路径
 */
function urlToGenericLocalPath(url, basePath = '') {
  try {
    const parsed = new URL(url);
    let pathname = parsed.pathname;
    if (pathname === '/' || pathname === '') {
      pathname = '/index';
    }
    if (pathname.endsWith('/')) {
      pathname = pathname.slice(0, -1);
    }
    if (!pathname.endsWith('.html')) {
      pathname += '.html';
    }
    const sanitized = pathname.replace(/[^a-zA-Z0-9\-_.\/]/g, '_');
    
    let localPath = sanitized.substring(1);
    
    if (basePath) {
      const basePathNoTrailing = basePath.endsWith('/') ? basePath.slice(0, -1) : basePath;
      const basePathWithoutLeading = basePathNoTrailing.substring(1);
      
      if (localPath.startsWith(basePathWithoutLeading + '/')) {
        localPath = localPath.substring(basePathWithoutLeading.length + 1);
      } else if (localPath === basePathWithoutLeading + '.html') {
        localPath = 'index.html';
      }
    }
    
    if (!localPath) {
      localPath = 'index.html';
    }
    
    return localPath;
  } catch {
    const hash = url.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    return `page_${hash}.html`;
  }
}

/**
 * 视频URL转换为本地路径
 */
function urlToVideoLocalPath(url) {
  try {
    const parsed = new URL(url);
    let pathname = parsed.pathname;
    if (pathname === '/' || pathname === '') {
      pathname = '/index';
    }
    const sanitized = pathname.replace(/[^a-zA-Z0-9\-_.\/]/g, '_');
    const parts = sanitized.split('/').filter(p => p);
    const filename = parts.length > 0 ? parts[parts.length - 1] : 'index';
    return filename + '.json';
  } catch {
    const hash = url.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    return `video_${hash}.json`;
  }
}

/**
 * 构建通用文档HTML
 */
function buildDocumentHtml(title, content, sidebarHtml = '', breadcrumbsHtml = '', structure = null) {
  const layoutClass = structure && structure.type === 'antora' ? 'doc antora-layout' : 'doc generic-layout';
  const sidebarBlock = sidebarHtml
    ? `<aside class="doc-sidebar preserved-nav">${sidebarHtml}</aside>`
    : '';
  const breadcrumbsBlock = breadcrumbsHtml
    ? `<div class="doc-breadcrumbs preserved-breadcrumbs">${breadcrumbsHtml}</div>`
    : '';

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(title)}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; line-height: 1.6; color: #333; }
    .doc-layout { display: flex; min-height: 100vh; }
    .doc-sidebar { width: 280px; flex-shrink: 0; padding: 16px; border-right: 1px solid #e0e0e0; overflow-y: auto; background: #fafafa; }
    .doc-main { flex: 1; padding: 24px 32px; max-width: 960px; }
    .doc-breadcrumbs { margin-bottom: 16px; color: #5f6368; font-size: 14px; }
    h1 { color: #1a73e8; border-bottom: 2px solid #1a73e8; padding-bottom: 10px; }
    h2 { color: #202124; margin-top: 30px; }
    h3 { color: #5f6368; }
    a { color: #1a73e8; text-decoration: none; }
    a:hover { text-decoration: underline; }
    code { background: #f8f9fa; padding: 2px 6px; border-radius: 3px; font-family: Consolas, monospace; }
    pre { background: #f8f9fa; padding: 15px; border-radius: 8px; overflow-x: auto; }
    blockquote { border-left: 4px solid #1a73e8; margin: 0; padding-left: 15px; color: #5f6368; }
    table { border-collapse: collapse; width: 100%; margin: 15px 0; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background: #f8f9fa; }
    img { max-width: 100%; height: auto; border-radius: 4px; }
    @media (max-width: 900px) {
      .doc-layout { flex-direction: column; }
      .doc-sidebar { width: 100%; border-right: none; border-bottom: 1px solid #e0e0e0; }
    }
  </style>
</head>
<body class="${layoutClass}">
  <div class="doc-layout">
    ${sidebarBlock}
    <div class="doc-main">
      ${breadcrumbsBlock}
      ${content}
    </div>
  </div>
</body>
</html>`;
}

function buildGenericDocumentHtml(title, content) {
  return buildDocumentHtml(title, content);
}

/**
 * HTML转义
 */
function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * 查找页面中的 m3u8 链接
 */
function findM3U8Links($, pageUrl) {
  const m3u8Links = [];
  
  // 1. 从 meta 标签查找
  $('meta[property="og:video"]').each((_, el) => {
    const content = $(el).attr('content');
    if (content && content.includes('.m3u8')) {
      m3u8Links.push(content);
    }
  });
  
  // 2. 从 script 标签查找 (大多数视频网站)
  $('script').each((_, script) => {
    const content = $(script).html();
    if (content) {
      const matches = content.match(/(https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*)/gi);
      if (matches) {
        matches.forEach(m => {
          if (!m3u8Links.includes(m)) m3u8Links.push(m);
        });
      }
    }
  });
  
  // 3. 从 video 标签的 src 属性查找
  $('video').each((_, video) => {
    const src = $(video).attr('src');
    if (src && src.includes('.m3u8')) {
      m3u8Links.push(src);
    }
  });
  
  // 4. 从 source 标签查找
  $('source').each((_, source) => {
    const src = $(source).attr('src');
    if (src && src.includes('.m3u8')) {
      m3u8Links.push(src);
    }
  });
  
  return m3u8Links;
}

/**
 * 解析 m3u8 文件获取 ts 片段列表
 */
async function parseM3U8(m3u8Url) {
  try {
    const response = await fetchWithRetry(m3u8Url, { responseType: 'text' });
    const content = response.data;
    const lines = content.split('\n');
    
    const segments = [];
    let baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf('/') + 1);
    
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith('#')) {
        // 处理相对路径和绝对路径
        if (trimmed.startsWith('http')) {
          segments.push(trimmed);
        } else if (trimmed.startsWith('/')) {
          const urlObj = new URL(m3u8Url);
          segments.push(`${urlObj.origin}${trimmed}`);
        } else {
          segments.push(`${baseUrl}${trimmed}`);
        }
      }
    }
    
    return {
      success: true,
      url: m3u8Url,
      segments: segments,
      segmentCount: segments.length,
      resolution: extractResolution(content)
    };
  } catch (error) {
    console.error(`Failed to parse m3u8: ${m3u8Url}`, error.message);
    return {
      success: false,
      url: m3u8Url,
      error: error.message,
      segments: [],
      segmentCount: 0
    };
  }
}

/**
 * 从 m3u8 内容中提取分辨率信息
 */
function extractResolution(m3u8Content) {
  const lines = m3u8Content.split('\n');
  for (const line of lines) {
    if (line.includes('RESOLUTION=')) {
      const match = line.match(/RESOLUTION=(\d+x\d+)/);
      if (match) return match[1];
    }
    if (line.includes('#EXT-X-STREAM-INF')) {
      const match = line.match(/RESOLUTION=\d+x(\d+)/);
      if (match) return `${Math.round(match[1] * 16 / 9)}x${match[1]}`;
    }
  }
  return 'unknown';
}

/**
 * 下载单个 ts 片段
 */
async function downloadTsSegment(url, outputPath, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      const response = await axiosInstance.get(url, {
        responseType: 'arraybuffer',
        timeout: 30000
      });
      fs.writeFileSync(outputPath, response.data);
      return { success: true, path: outputPath };
    } catch (error) {
      if (i === retries - 1) {
        return { success: false, error: error.message, url };
      }
      await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
    }
  }
}

/**
 * 下载所有 ts 片段
 */
async function downloadTsSegments(segments, outputDir, progressCallback) {
  const results = [];
  const total = segments.length;
  
  for (let i = 0; i < segments.length; i++) {
    const segmentUrl = segments[i];
    const filename = `segment_${String(i).padStart(5, '0')}.ts`;
    const outputPath = path.join(outputDir, filename);
    
    const result = await downloadTsSegment(segmentUrl, outputPath);
    results.push({ ...result, index: i, url: segmentUrl });
    
    if (progressCallback) {
      progressCallback(i + 1, total, filename);
    }
  }
  
  const successCount = results.filter(r => r.success).length;
  return {
    success: successCount === total,
    total,
    successCount,
    failedCount: total - successCount,
    results,
    outputDir
  };
}

/**
 * 使用 ffmpeg 合并 ts 片段为 mp4
 */
async function mergeTsToMp4(segmentsDir, outputPath, progressCallback) {
  const { exec } = require('child_process');
  const util = require('util');
  const execPromise = util.promisify(exec);
  
  return new Promise((resolve, reject) => {
    const tsPattern = path.join(segmentsDir, 'segment_*.ts');
    const outputDir = path.dirname(outputPath);
    const filename = path.basename(outputPath, '.mp4');
    const concatFile = path.join(outputDir, `${filename}_concat.txt`);
    
    // 创建 concat 文件
    const files = fs.readdirSync(segmentsDir)
      .filter(f => f.startsWith('segment_') && f.endsWith('.ts'))
      .sort();
    
    const concatContent = files.map(f => `file '${path.join(segmentsDir, f)}'`).join('\n');
    fs.writeFileSync(concatFile, concatContent);
    
    // ffmpeg 命令
    const ffmpegCmd = `ffmpeg -y -f concat -safe 0 -i "${concatFile}" -c copy "${outputPath}"`;
    
    console.log(`Merging ${files.length} ts segments to ${outputPath}...`);
    
    exec(ffmpegCmd, { cwd: outputDir }, async (error, stdout, stderr) => {
      // 清理临时文件
      try {
        fs.removeSync(concatFile);
        fs.removeSync(segmentsDir);
      } catch (e) {
        console.warn('Failed to cleanup temp files:', e.message);
      }
      
      if (error) {
        console.error('ffmpeg error:', stderr);
        reject(new Error(`ffmpeg failed: ${error.message}`));
        return;
      }
      
      // 获取文件大小
      const stats = fs.statSync(outputPath);
      const fileSizeMB = (stats.size / (1024 * 1024)).toFixed(2);
      
      resolve({
        success: true,
        outputPath,
        fileSize: stats.size,
        fileSizeMB,
        duration: extractDuration(stderr)
      });
    });
  });
}

/**
 * 从 ffmpeg 输出中提取时长
 */
function extractDuration(stderr) {
  const match = stderr.match(/Duration: (\d+):(\d+):(\d+\.\d+)/);
  if (match) {
    const hours = parseInt(match[1]);
    const minutes = parseInt(match[2]);
    const seconds = parseFloat(match[3]);
    return hours * 3600 + minutes * 60 + seconds;
  }
  return 0;
}

/**
 * 下载视频完整流程
 */
async function downloadVideo(m3u8Url, category, title, progressCallback) {
  console.log(`Starting video download: ${m3u8Url}`);
  const startTime = Date.now();
  
  try {
    // 1. 解析 m3u8
    if (progressCallback) progressCallback('parsing', 0, 'Parsing m3u8...');
    const m3u8Info = await parseM3U8(m3u8Url);
    
    if (!m3u8Info.success || m3u8Info.segments.length === 0) {
      throw new Error(`Failed to parse m3u8: ${m3u8Info.error || 'No segments found'}`);
    }
    
    console.log(`Found ${m3u8Info.segmentCount} segments, resolution: ${m3u8Info.resolution}`);
    
    // 2. 创建输出目录
    const safeTitle = title.replace(/[<>:"/\\|?*]/g, '_').substring(0, 100);
    const videoDir = path.join(VIDEOS_DIR, category, 'downloads', safeTitle);
    const segmentsDir = path.join(videoDir, 'segments');
    fs.ensureDirSync(segmentsDir);
    
    // 3. 下载 ts 片段
    if (progressCallback) progressCallback('downloading', 0, `Downloading ${m3u8Info.segmentCount} segments...`);
    
    const downloadResult = await downloadTsSegments(
      m3u8Info.segments,
      segmentsDir,
      (current, total, filename) => {
        if (progressCallback) {
          progressCallback('downloading', current, `${current}/${total}: ${filename}`);
        }
      }
    );
    
    if (!downloadResult.success) {
      throw new Error(`Failed to download ${downloadResult.failedCount} segments`);
    }
    
    // 4. 合并为 mp4
    if (progressCallback) progressCallback('merging', 0, 'Merging with ffmpeg...');
    
    const outputPath = path.join(videoDir, `${safeTitle}.mp4`);
    const mergeResult = await mergeTsToMp4(segmentsDir, outputPath);
    
    const totalTime = ((Date.now() - startTime) / 1000).toFixed(2);
    
    console.log(`Video downloaded successfully: ${outputPath}`);
    console.log(`File size: ${mergeResult.fileSizeMB} MB, Time: ${totalTime}s`);
    
    return {
      success: true,
      title,
      m3u8Url,
      category,
      outputPath,
      fileSize: mergeResult.fileSize,
      fileSizeMB: mergeResult.fileSizeMB,
      duration: mergeResult.duration,
      segmentCount: m3u8Info.segmentCount,
      resolution: m3u8Info.resolution,
      downloadTime: totalTime
    };
    
  } catch (error) {
    console.error(`Video download failed: ${error.message}`);
    return {
      success: false,
      m3u8Url,
      error: error.message,
      downloadTime: ((Date.now() - startTime) / 1000).toFixed(2)
    };
  }
}

module.exports = {
  crawlPage,
  crawlMultiplePages,
  crawlDocumentSite,
  clearCrawledDocsStorage,
  clearCrawledDocsForSite,
  verifyCrawlResults,
  analyzeSite,
  deriveSiteKey,
  crawlGenericPage,
  crawlGenericSite,
  crawlVideoPage,
  crawlVideoSite,
  refreshAllPages,
  rebuildLocalHtmlShell,
  DOCS_DIR,
  VIDEOS_DIR,
  identifyCategory,
  identifyTaskType,
  identifyVideoCategory,
  getCategoryStoragePath,
  listLocalHtmlFiles,
  getCategoryStats,
  writeLog,
  createAllDirectories,
  CATEGORIES,
  DOC_CATEGORIES,
  VIDEO_CATEGORIES,
  TASK_TYPES,
  // 新增视频下载功能
  findM3U8Links,
  parseM3U8,
  downloadVideo,
  downloadTsSegments,
  mergeTsToMp4
};
