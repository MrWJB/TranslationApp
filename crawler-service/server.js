const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs-extra');
const crawler = require('./src/crawler');
const crawlJobs = require('./src/crawl-jobs');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// 自动创建所有分类目录
const initAllDirectories = () => {
  console.log('Initializing all category directories...');
  
  // 文档分类目录
  Object.values(crawler.DOC_CATEGORIES).forEach(category => {
    const categoryDir = path.join(crawler.DOCS_DIR, category);
    if (!fs.existsSync(categoryDir)) {
      fs.mkdirSync(categoryDir, { recursive: true });
      console.log(`Created doc category directory: ${category}`);
    }
  });
  
  // 视频分类目录
  Object.values(crawler.VIDEO_CATEGORIES).forEach(category => {
    const categoryDir = path.join(crawler.VIDEOS_DIR, category);
    if (!fs.existsSync(categoryDir)) {
      fs.mkdirSync(categoryDir, { recursive: true });
      console.log(`Created video category directory: ${category}`);
    }
  });
};

// 静态文件服务：提供爬取的HTML文档、图片和视频
app.use('/docs', express.static(crawler.DOCS_DIR));
app.use('/videos', express.static(crawler.VIDEOS_DIR));
app.use('/images', express.static(path.join(crawler.DOCS_DIR, 'images')));

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'crawler-service' });
});

// 识别URL任务类型
app.post('/identify-type', (req, res) => {
  try {
    const { url } = req.body;
    
    if (!url) {
      return res.status(400).json({ error: 'url is required' });
    }
    
    const typeInfo = crawler.identifyTaskType(url);
    
    res.json({
      success: true,
      url,
      taskType: typeInfo.type,
      confidence: typeInfo.confidence,
      reason: typeInfo.reason,
      suggestions: {
        document: {
          categories: Object.values(crawler.DOC_CATEGORIES).filter(c => c !== 'other'),
          defaultCategory: 'java'
        },
        video: {
          categories: Object.values(crawler.VIDEO_CATEGORIES).filter(c => c !== 'other'),
          defaultCategory: 'movie'
        }
      }
    });
  } catch (error) {
    console.error('Type identification error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get category stats
app.get('/categories', (req, res) => {
  try {
    const stats = crawler.getCategoryStats();
    
    // 文档分类
    const docCategories = Object.keys(crawler.DOC_CATEGORIES)
      .filter(c => c !== 'OTHER')
      .map(key => ({
        code: crawler.DOC_CATEGORIES[key],
        name: key.charAt(0) + key.slice(1).toLowerCase().replace(/_/g, ' ')
      }));
    
    // 视频分类
    const videoCategories = Object.keys(crawler.VIDEO_CATEGORIES)
      .filter(c => c !== 'OTHER')
      .map(key => ({
        code: crawler.VIDEO_CATEGORIES[key],
        name: key.charAt(0) + key.slice(1).toLowerCase().replace(/_/g, ' ')
      }));
    
    res.json({
      success: true,
      taskTypes: Object.values(crawler.TASK_TYPES),
      docCategories,
      videoCategories,
      stats
    });
  } catch (error) {
    console.error('Failed to get category stats:', error);
    res.status(500).json({ error: error.message });
  }
});

/** Verify local paths exist on disk */
app.post('/crawl/verify', (req, res) => {
  try {
    const localPaths = req.body.localPaths || [];
    const missing = [];
    const present = [];
    for (const p of localPaths) {
      const full = path.join(crawler.DOCS_DIR, String(p).replace(/^\//, ''));
      if (fs.existsSync(full)) {
        present.push(p);
      } else {
        missing.push(p);
      }
    }
    res.json({
      success: true,
      total: localPaths.length,
      present: present.length,
      missing: missing.length,
      missingPaths: missing.slice(0, 50),
      passed: missing.length === 0,
    });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/** Analyze site structure before crawling */
app.post('/crawl/analyze', async (req, res) => {
  try {
    const baseUrl = String(req.body.baseUrl || req.body.url || '').trim();
    if (!baseUrl) {
      return res.status(400).json({ success: false, error: 'baseUrl is required' });
    }
    const result = await crawler.analyzeSite(baseUrl);
    res.json(result);
  } catch (error) {
    console.error('Analyze error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Crawl endpoint
app.post('/crawl', async (req, res) => {
  try {
    const baseUrl = String(req.body.baseUrl || '').trim();
    const maxPages = req.body.maxPages ?? 300;
    
    if (!baseUrl) {
      return res.status(400).json({ error: 'baseUrl is required' });
    }

    console.log(`Starting crawl: ${baseUrl}, maxPages: ${maxPages}`);
    
    const results = await crawler.crawlDocumentSite(baseUrl, maxPages);
    
    res.json({
      success: true,
      pagesCrawled: results.length,
      pages: results
    });
  } catch (error) {
    console.error('Crawl error:', error);
    res.status(500).json({ error: error.message });
  }
});

/** Start async crawl; poll GET /crawl/progress/:jobId then GET /crawl/result/:jobId */
app.post('/crawl/async', async (req, res) => {
  try {
    const baseUrl = String(req.body.baseUrl || '').trim();
    const maxPages = req.body.maxPages ?? 300;
    const jobId = req.body.jobId;
    const siteKey = req.body.siteKey ? String(req.body.siteKey).trim() : null;

    if (!baseUrl) {
      return res.status(400).json({ error: 'baseUrl is required' });
    }
    if (!jobId) {
      return res.status(400).json({ error: 'jobId is required' });
    }

    const existing = crawlJobs.getJob(jobId);
    if (existing && existing.status === 'running') {
      return res.status(409).json({ error: 'Job already running', jobId });
    }

    crawlJobs.createJob(jobId, maxPages);
    crawlJobs.updateJob(jobId, {
      phase: 'crawling',
      current: 0,
      total: maxPages,
      message: '已启动，正在连接站点…',
    });
    console.log(`Starting async crawl job ${jobId}: ${baseUrl}, maxPages: ${maxPages}`);

    res.status(202).json({ success: true, jobId, status: 'running' });

    const crawlOptions = siteKey ? { siteKey } : {};
    crawler.crawlDocumentSite(baseUrl, maxPages, (progress) => {
      crawlJobs.updateJob(jobId, {
        phase: progress.phase || 'crawling',
        current: progress.current ?? 0,
        total: progress.total ?? maxPages,
        message: progress.message || '',
      });
    }, crawlOptions).then((pages) => {
      crawlJobs.completeJob(jobId, pages);
      console.log(`Async crawl job ${jobId} completed: ${pages.length} pages`);
    }).catch((error) => {
      crawlJobs.failJob(jobId, error);
      console.error(`Async crawl job ${jobId} failed:`, error.message);
    });
  } catch (error) {
    console.error('Async crawl error:', error);
    res.status(500).json({ error: error.message });
  }
});

app.get('/crawl/progress/:jobId', (req, res) => {
  const job = crawlJobs.getJob(req.params.jobId);
  if (!job) {
    return res.status(404).json({ error: 'Job not found', jobId: req.params.jobId });
  }
  res.json({
    jobId: job.jobId,
    status: job.status,
    phase: job.phase,
    current: job.current,
    total: job.total,
    message: job.message,
    error: job.error || null,
  });
});

app.get('/crawl/result/:jobId', (req, res) => {
  const job = crawlJobs.getJob(req.params.jobId);
  if (!job) {
    return res.status(404).json({ error: 'Job not found', jobId: req.params.jobId });
  }
  if (job.status === 'running') {
    return res.status(202).json({ status: 'running', jobId: job.jobId });
  }
  if (job.status === 'failed') {
    return res.status(500).json({ success: false, error: job.error, jobId: job.jobId });
  }
  res.json({
    success: true,
    jobId: job.jobId,
    pagesCrawled: job.pages?.length || 0,
    pages: job.pages || [],
  });
});

// Generic crawl endpoint (supports any URL)
app.post('/crawl-generic', async (req, res) => {
  try {
    const { url, maxPages = 50 } = req.body;
    
    if (!url) {
      return res.status(400).json({ error: 'url is required' });
    }

    console.log(`Starting generic crawl: ${url}, maxPages: ${maxPages}`);
    
    const results = await crawler.crawlDocumentSite(url, maxPages);
    
    res.json({
      success: true,
      pagesCrawled: results.length,
      pages: results
    });
  } catch (error) {
    console.error('Generic crawl error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Video crawl endpoint
app.post('/crawl-video', async (req, res) => {
  const { url, maxPages = 100, category = 'other' } = req.body;
  
  if (!url) {
    return res.status(400).json({ error: 'url is required' });
  }

  try {
    console.log(`Starting video crawl: ${url}, maxPages: ${maxPages}, category: ${category}`);
    
    const results = await crawler.crawlVideoSite(url, maxPages, category);
    
    res.json({
      success: true,
      pagesCrawled: results.length,
      pages: results
    });
  } catch (error) {
    console.error('Video crawl error:', error);
    const originalError = error.originalError || error;
    const errorCode = originalError.code || '';
    const errorMessage = originalError.message || error.message;
    const isNetworkError = errorCode === 'ETIMEDOUT' || 
                          errorCode === 'ECONNREFUSED' || 
                          errorCode === 'EHOSTUNREACH' ||
                          errorCode === 'ENOTFOUND' ||
                          errorMessage.includes('Connection refused');
    const statusCode = isNetworkError ? 503 : 500;
    res.status(statusCode).json({
      success: false,
      error: error.message,
      errorType: isNetworkError ? 'NETWORK_ERROR' : 'CRAWL_ERROR',
      url: url,
      originalErrorCode: errorCode
    });
  }
});

// Batch crawl all video categories from lvtu777.com
app.post('/crawl-all-videos', async (req, res) => {
  const { maxPagesPerCategory = 10000 } = req.body;
  
  const videoCategories = [
    { url: 'https://www.lvtu777.com/list/1.html', name: '电影', category: 'movie' },
    { url: 'https://www.lvtu777.com/list/2.html', name: '电视剧', category: 'tv-series' },
    { url: 'https://www.lvtu777.com/list/3.html', name: '综艺', category: 'variety' },
    { url: 'https://www.lvtu777.com/list/4.html', name: '动漫', category: 'anime' },
    { url: 'https://www.lvtu777.com/list/25.html', name: '短剧', category: 'short-drama' }
  ];
  
  const results = [];
  const errors = [];
  
  console.log(`Starting batch crawl of ${videoCategories.length} categories, maxPagesPerCategory: ${maxPagesPerCategory}`);
  
  for (const cat of videoCategories) {
    console.log(`Crawling category: ${cat.name} (${cat.url})...`);
    try {
      const result = await crawler.crawlVideoSite(cat.url, maxPagesPerCategory, cat.category);
      results.push({
        category: cat.name,
        code: cat.category,
        url: cat.url,
        pagesCrawled: result.length,
        success: true
      });
      console.log(`Completed category: ${cat.name}, crawled ${result.length} pages`);
    } catch (error) {
      console.error(`Failed to crawl category ${cat.name}: ${error.message}`);
      errors.push({
        category: cat.name,
        code: cat.category,
        url: cat.url,
        error: error.message,
        success: false
      });
    }
  }
  
  const totalPages = results.reduce((sum, r) => sum + r.pagesCrawled, 0);
  
  res.json({
    success: errors.length === 0,
    totalCategories: videoCategories.length,
    successCategories: results.length,
    failedCategories: errors.length,
    totalPagesCrawled: totalPages,
    results,
    errors
  });
});

// Video download endpoint - downloads video from m3u8 URL
app.post('/download-video', async (req, res) => {
  const { m3u8Url, category = 'other', title = 'untitled' } = req.body;
  
  if (!m3u8Url) {
    return res.status(400).json({ error: 'm3u8Url is required' });
  }

  try {
    console.log(`Starting video download: ${m3u8Url}`);
    
    const result = await crawler.downloadVideo(m3u8Url, category, title, (stage, progress, info) => {
      console.log(`[${stage}] ${progress}: ${info}`);
    });
    
    if (result.success) {
      res.json({
        success: true,
        ...result
      });
    } else {
      res.status(500).json({
        success: false,
        error: result.error
      });
    }
  } catch (error) {
    console.error('Video download error:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// Parse m3u8 endpoint - just parse without downloading
app.post('/parse-m3u8', async (req, res) => {
  const { url } = req.body;
  
  if (!url) {
    return res.status(400).json({ error: 'url is required' });
  }

  try {
    const result = await crawler.parseM3U8(url);
    res.json(result);
  } catch (error) {
    console.error('Parse m3u8 error:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

app.post('/refresh-all', async (req, res) => {
  try {
    console.log('Refreshing all local pages with Spring original format...');
    const results = await crawler.refreshAllPages();
    res.json({
      success: true,
      pagesRefreshed: results.length,
      pages: results
    });
  } catch (error) {
    console.error('Refresh error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get status
app.get('/status', (req, res) => {
  res.json({
    status: 'running',
    uptime: process.uptime(),
    memory: process.memoryUsage()
  });
});

app.listen(PORT, () => {
  initAllDirectories();
  console.log(`Crawler service running on port ${PORT}`);
});
