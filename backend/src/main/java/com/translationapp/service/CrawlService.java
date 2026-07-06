package com.translationapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.dto.DocumentDTO;
import com.translationapp.service.NodeCrawlerService.CrawledPage;
import com.translationapp.service.NodeCrawlerService.PageSection;
import com.translationapp.entity.CrawlTask;
import com.translationapp.entity.Document;
import com.translationapp.repository.CrawlTaskRepository;
import com.translationapp.repository.DocumentRepository;
import com.translationapp.translator.DocumentTranslator;
import com.translationapp.translator.TranslationFailedException;
import com.translationapp.translator.TranslationMarkers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.translationapp.util.SiteKeyUtil;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {

    private static final String SPRING_DOC_BASE_URL = "https://docs.spring.io/spring-framework/reference/";

    private static final Set<String> EXCLUDED_PAGES = Set.of(
            "index.html",
            "search.html",
            "spring-projects.html"
    );

    private final NodeCrawlerService nodeCrawlerService;
    private final DocumentTranslator translator;
    private final LocalDocumentStorage localDocumentStorage;
    private final DocumentTocBuilder documentTocBuilder;
    private final DocumentNavParser documentNavParser;
    private final CrawlTaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    @Value("${translation.on-crawl:true}")
    private boolean translateOnCrawl;

    @Value("${translation.delay-ms:300}")
    private long translationDelayMs;

    /**
     * Create a new crawl task and process it asynchronously
     */
    @Async
    public void startCrawlTask(Long taskId, String url, int maxPages, String siteKey) {
        url = url != null ? url.trim() : url;
        CrawlTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("RUNNING");
        task.setMaxPages(maxPages);
        task.setProgressPhase("crawling");
        task.setProgressCurrent(0);
        task.setProgressTotal(maxPages);
        task.setProgressMessage("正在启动爬虫…");
        taskRepository.save(task);

        try {
            String taskType = task.getTaskType();
            log.info("Starting crawl task {} with type: {}", taskId, taskType);

            List<CrawledPage> pages;
            
            if ("video".equals(taskType)) {
                updateTaskProgress(taskId, "crawling", 0, maxPages, "正在爬取视频页面…");
                pages = nodeCrawlerService.crawlVideoPages(url, maxPages, task.getMediaCategory());
                processVideoTask(task, pages);
            } else {
                updateTaskProgress(taskId, "crawling", 0, maxPages, "正在准备爬取…");
                clearPreviousCrawlArtifacts(taskId, maxPages);
                String jobId = String.valueOf(taskId);
                nodeCrawlerService.startAsyncDocumentCrawl(jobId, url, maxPages, siteKey);
                updateTaskProgress(taskId, "crawling", 0, maxPages, "已连接爬虫，正在抓取…");
                pages = nodeCrawlerService.waitForAsyncDocumentCrawl(jobId, progress -> {
                    String message = progress.getMessage();
                    if (message == null || message.isBlank()) {
                        message = "正在抓取页面…";
                    }
                    updateTaskProgress(taskId, progress.getPhase(), progress.getCurrent(),
                            progress.getTotal() > 0 ? progress.getTotal() : maxPages,
                            message);
                });
                processDocumentTask(task, pages);
            }
        } catch (Exception e) {
            log.error("Crawl task {} failed: {}", taskId, e.getMessage(), e);
            task = taskRepository.findById(taskId).orElseThrow();
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setProgressMessage("爬取失败: " + e.getMessage());
            taskRepository.save(task);
        }
    }

    /**
     * Wipe on-disk crawl artifacts before a new document task.
     * Crawler also clears site folder; skip here to avoid double-delete races.
     */
    private void clearPreviousCrawlArtifacts(Long taskId, int maxPages) {
        documentNavParser.clearNavCacheFiles();
        updateTaskProgress(taskId, "crawling", 0, maxPages, "正在清理旧文件…");
    }

    /**
     * Mark task as running before async dispatch so the UI does not stay at PENDING while queued.
     */
    public void markTaskRunning(Long taskId, int maxPages) {
        CrawlTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("RUNNING");
        task.setMaxPages(maxPages);
        task.setProgressPhase("crawling");
        task.setProgressCurrent(0);
        task.setProgressTotal(maxPages);
        task.setProgressMessage("正在启动爬虫…");
        taskRepository.save(task);
    }

    private void updateTaskProgress(Long taskId, String phase, int current, int total, String message) {
        CrawlTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setProgressPhase(phase);
        task.setProgressCurrent(current);
        task.setProgressTotal(total);
        task.setProgressMessage(message);
        taskRepository.save(task);
    }

    /**
     * Process video crawl task
     */
    private void processVideoTask(CrawlTask task, List<CrawledPage> pages) {
        int documentOrder = 0;
        
        for (CrawledPage page : pages) {
            log.info("Processing video page: {} (category: {})", page.getTitle(), page.getCategory());
            
            Document doc = new Document();
            doc.setTitle(page.getTitle());
            doc.setUrl(page.getUrl());
            doc.setLocalPath(page.getLocalPath());
            doc.setCategory(page.getCategory());
            doc.setCrawlTask(task);
            doc.setSortOrder(documentOrder++);
            doc.setSectionLevel(0);
            doc.setOriginalContent("");
            doc.setTranslatedContent("");
            
            documentRepository.save(doc);
            
            log.info("Saved video document: {} (localPath: {})", page.getTitle(), page.getLocalPath());
        }

        if (documentOrder == 0) {
            task.setStatus("FAILED");
            task.setErrorMessage("未爬取到任何视频页面，请检查URL是否正确或网站是否可访问。");
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            log.warn("Video crawl task {} finished with 0 documents", task.getId());
            return;
        }
        
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        log.info("Video crawl task {} completed successfully with {} documents",
                task.getId(), documentOrder);
    }

    /**
     * Process document crawl task (Spring or generic)
     */
    private void processDocumentTask(CrawlTask task, List<CrawledPage> pages) {
        Long taskId = task.getId();
        task = taskRepository.findById(taskId).orElseThrow();
        String taskBaseUrl = task.getUrl();
        if (taskBaseUrl != null && !taskBaseUrl.endsWith("/")) {
            taskBaseUrl = taskBaseUrl + "/";
        }
        documentNavParser.clearCache();
        if (task.getUrl() != null && !task.getUrl().endsWith("/")) {
            task.setUrl(task.getUrl().trim() + "/");
        }
        task.setCategory(SiteKeyUtil.deriveDisplayCategory(task.getUrl()));
        taskRepository.save(task);
        
        boolean shouldTranslate = translateOnCrawl && translator.isConfigured();
        if (translateOnCrawl && !translator.isConfigured()) {
            log.warn("DeepL API key not configured; skipping translation for task {}", task.getId());
        }
        
        int documentOrder = 0;
        int translatedCount = 0;
        int totalPages = pages.size();
        
        Map<String, Map<String, Object>> urlToToc = new LinkedHashMap<>();

        updateTaskProgress(task.getId(), "processing", 0, totalPages, "正在保存文档到数据库…");

        for (CrawledPage page : pages) {

            String normalizedUrl = normalizePagePath(page.getUrl(), taskBaseUrl);
            if (EXCLUDED_PAGES.contains(normalizedUrl)) {
                log.info("Skipping excluded page: {}", page.getUrl());
                continue;
            }
            
            String title = page.getTitle();
            if (title == null || title.isBlank()) {
                title = deriveTitleFromPath(normalizedUrl);
            }
            
            Document doc = new Document();
            doc.setTitle(title);
            doc.setUrl(page.getUrl());
            doc.setLocalPath(page.getLocalPath());
            doc.setCategory(page.getCategory() != null ? page.getCategory() : task.getCategory());
            doc.setCrawlTask(task);
            doc.setSortOrder(documentOrder++);
            doc.setSectionLevel(0);
            doc.setOriginalContent("");
            doc.setTranslatedContent("");
            Document savedDoc = documentRepository.save(doc);

            if (shouldTranslate) {
                if (translatePageDocument(savedDoc)) {
                    translatedCount++;
                    savedDoc = documentRepository.save(savedDoc);
                }
                if (translationDelayMs > 0) {
                    try {
                        Thread.sleep(translationDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            urlToToc.put(page.getUrl(), createTocEntry(savedDoc, title, page.getUrl(), page.getLocalPath()));

            updateTaskProgress(task.getId(),
                    shouldTranslate ? "translating" : "processing",
                    documentOrder,
                    totalPages,
                    shouldTranslate
                            ? String.format("正在翻译 (%d/%d): %s", documentOrder, totalPages, title)
                            : String.format("已保存 (%d/%d): %s", documentOrder, totalPages, title));
        }

        updateTaskProgress(task.getId(), "building_toc", totalPages, totalPages, "正在构建文档目录…");

        if (documentOrder == 0) {
            if (localDocumentStorage.hasOriginalHtmlFiles()) {
                log.warn("Crawl task {} returned no importable pages; falling back to local crawled-docs", task.getId());
                populateTaskFromLocalFiles(task, task.getUrl());
                return;
            }
            task.setStatus("FAILED");
            task.setErrorMessage("未爬取到任何页面，请检查URL是否正确、网站是否可访问，以及 crawler-service (3000) 是否已启动。");
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            log.warn("Document crawl task {} finished with 0 documents", task.getId());
            return;
        }
        
        List<Map<String, Object>> siteNavTree = pages.isEmpty() ? null : pages.get(0).getSiteNavTree();

        List<Map<String, Object>> fullTableOfContents;
        try {
            fullTableOfContents = buildDocumentTableOfContents(urlToToc, true, taskBaseUrl, siteNavTree);
        } catch (Exception e) {
            log.warn("Structured TOC build failed for task {}, using flat list: {}",
                    task.getId(), e.getMessage(), e);
            fullTableOfContents = documentTocBuilder.buildFlat(urlToToc, taskBaseUrl);
        }

        try {
            task.setTableOfContents(objectMapper.writeValueAsString(fullTableOfContents));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize table of contents for task {}, using flat list",
                    task.getId(), e);
            try {
                task.setTableOfContents(objectMapper.writeValueAsString(
                        documentTocBuilder.buildFlat(urlToToc, taskBaseUrl)));
            } catch (JsonProcessingException ignored) {
                task.setTableOfContents("[]");
            }
        }

        String qualityReport = extractQualityReport(pages);
        if (qualityReport != null) {
            task.setQualityReport(qualityReport);
        }
        
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        task.setProgressPhase("completed");
        task.setProgressCurrent(documentOrder);
        task.setProgressTotal(documentOrder);
        task.setProgressMessage(String.format("完成，共 %d 页%s", documentOrder,
                translatedCount > 0 ? "，已翻译 " + translatedCount + " 页" : ""));
        taskRepository.save(task);
        
        log.info("Document crawl task {} completed successfully with {} documents ({} translated)",
                task.getId(), documentOrder, translatedCount);
    }

    private String normalizePagePath(String pageUrl, String taskBaseUrl) {
        if (pageUrl == null || taskBaseUrl == null) {
            return "";
        }
        String normalizedUrl = pageUrl.replace(taskBaseUrl, "");
        if (normalizedUrl.startsWith("/")) {
            normalizedUrl = normalizedUrl.substring(1);
        }
        return normalizedUrl;
    }

    private String determineDocumentCategory(String url) {
        return SiteKeyUtil.deriveDisplayCategory(url);
    }

    private String extractQualityReport(List<CrawledPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        return pages.get(0).getQualityReport();
    }

    /**
     * Import already-crawled HTML files from disk into a completed task.
     */
    @Transactional
    public CrawlTask importLocalDocuments(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = SPRING_DOC_BASE_URL;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        List<String> htmlFiles;
        try {
            htmlFiles = localDocumentStorage.listOriginalHtmlFiles();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan local documents: " + e.getMessage(), e);
        }

        if (htmlFiles.isEmpty()) {
            throw new RuntimeException("No local HTML documents found in crawled-docs directory");
        }

        CrawlTask task = new CrawlTask();
        task.setUrl(baseUrl);
        task.setTitle("本地文档: Spring Framework");
        task.setStatus("RUNNING");
        task.setTaskType("document");
        task.setCategory("spring");
        taskRepository.save(task);

        populateTaskFromLocalFiles(task, baseUrl);
        return task;
    }

    private void populateTaskFromLocalFiles(CrawlTask task, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = SPRING_DOC_BASE_URL;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        List<String> htmlFiles;
        try {
            htmlFiles = localDocumentStorage.listOriginalHtmlFiles();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan local documents: " + e.getMessage(), e);
        }

        if (htmlFiles.isEmpty()) {
            throw new RuntimeException("No local HTML documents found in crawled-docs directory");
        }

        Map<String, Map<String, Object>> urlToToc = new LinkedHashMap<>();
        int documentOrder = 0;

        for (String localPath : htmlFiles) {
            if (EXCLUDED_PAGES.contains(localPath)) {
                continue;
            }

            String pageUrl = localDocumentStorage.toPageUrl(localPath);
            
            if (documentRepository.existsByLocalPath(localPath)) {
                log.info("Skipping duplicate document: {}", localPath);
                continue;
            }

            String title = extractTitleFromHtml(localPath);

            Document doc = new Document();
            doc.setTitle(title);
            doc.setUrl(pageUrl);
            doc.setLocalPath(localPath);
            doc.setCategory(extractCategoryFromLocalPath(localPath));
            doc.setCrawlTask(task);
            doc.setSortOrder(documentOrder++);
            doc.setSectionLevel(0);
            doc.setOriginalContent("");
            doc.setTranslatedContent("");

            String translatedPath = localDocumentStorage.translatedRelativePath(localPath);
            if (localDocumentStorage.resolveDocsFile(translatedPath).isPresent()) {
                doc.setTranslatedLocalPath(translatedPath);
            }

            Document savedDoc = documentRepository.save(doc);
            urlToToc.put(pageUrl, createTocEntry(savedDoc, title, pageUrl, localPath));
        }

        List<Map<String, Object>> fullTableOfContents = buildDocumentTableOfContents(urlToToc, true, baseUrl, null);
        try {
            task.setTableOfContents(objectMapper.writeValueAsString(fullTableOfContents));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize table of contents", e);
        }

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        log.info("Imported {} local documents into task {}", documentOrder, task.getId());
    }

    public int refreshDocumentFormat() {
        return nodeCrawlerService.refreshAllPages();
    }

    private Map<String, Object> createTocEntry(Document doc, String title, String pageUrl, String localPath) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", doc.getId());
        entry.put("title", title);
        entry.put("level", 0);
        entry.put("url", pageUrl);
        entry.put("localPath", localPath);
        if (doc.getTranslatedLocalPath() != null) {
            entry.put("translatedLocalPath", doc.getTranslatedLocalPath());
        }
        entry.put("children", new ArrayList<Map<String, Object>>());
        return entry;
    }

    private List<Map<String, Object>> buildDocumentTableOfContents(
            Map<String, Map<String, Object>> urlToToc,
            boolean forceRefreshNav,
            String customBaseUrl) {
        return buildDocumentTableOfContents(urlToToc, forceRefreshNav, customBaseUrl, null);
    }

    private List<Map<String, Object>> buildDocumentTableOfContents(
            Map<String, Map<String, Object>> urlToToc,
            boolean forceRefreshNav,
            String customBaseUrl,
            List<Map<String, Object>> siteNavTree) {
        if (forceRefreshNav) {
            documentNavParser.clearCache();
        }

        if (siteNavTree != null && !siteNavTree.isEmpty()) {
            log.info("Building TOC from crawler siteNavTree ({} top-level entries)", siteNavTree.size());
            return documentTocBuilder.buildFromCrawlerNav(siteNavTree, urlToToc, EXCLUDED_PAGES, customBaseUrl);
        }

        applyNavTitles(urlToToc, forceRefreshNav, customBaseUrl);
        return documentTocBuilder.build(urlToToc, EXCLUDED_PAGES, false, customBaseUrl);
    }

    private void applyNavTitles(Map<String, Map<String, Object>> urlToToc, boolean forceRefreshNav, String customBaseUrl) {
        Map<String, String> titlesByPath = documentNavParser.loadTitleByPathMap(customBaseUrl, forceRefreshNav);
        if (titlesByPath.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : urlToToc.entrySet()) {
            String localPath = entry.getKey().replace(customBaseUrl, "");
            if (!customBaseUrl.endsWith("/") && localPath.startsWith("/")) {
                localPath = localPath.substring(1);
            }
            String navTitle = titlesByPath.get(localPath);
            if (navTitle == null || navTitle.isBlank()) {
                continue;
            }

            entry.getValue().put("title", navTitle);
            Object docId = entry.getValue().get("id");
            if (docId instanceof Long id) {
                documentRepository.findById(id).ifPresent(doc -> {
                    doc.setTitle(navTitle);
                    documentRepository.save(doc);
                });
            }
        }
    }

    /**
     * Re-translate task documents from stored originals (e.g. after fixing DeepL auth).
     *
     * @param taskId     crawl task id
     * @param onlyFailed when true, only pages whose translated HTML contains a prior failure marker
     */
    @Transactional
    public Map<String, Object> retranslateTaskDocuments(Long taskId, boolean onlyFailed) {
        if (!translator.isConfigured()) {
            throw new IllegalStateException(
                    "No translation provider configured; set translation.provider and credentials in application.yml");
        }
        CrawlTask task = getTask(taskId);
        List<Document> documents = documentRepository.findByCrawlTaskIdOrderBySortOrderAsc(taskId);
        int attempted = 0;
        int translated = 0;
        int skipped = 0;
        int failed = 0;

        for (Document doc : documents) {
            if (onlyFailed && !hasFailedTranslation(doc)) {
                skipped++;
                continue;
            }
            attempted++;
            if (translatePageDocument(doc)) {
                documentRepository.save(doc);
                translated++;
            } else {
                failed++;
            }
            if (translationDelayMs > 0) {
                try {
                    Thread.sleep(translationDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info(
                "Retranslate task {}: attempted={}, translated={}, skipped={}, failed={}",
                taskId,
                attempted,
                translated,
                skipped,
                failed);
        return Map.of(
                "taskId", task.getId(),
                "onlyFailed", onlyFailed,
                "attempted", attempted,
                "translated", translated,
                "skipped", skipped,
                "failed", failed,
                "message", "Retranslation finished");
    }

    private boolean hasFailedTranslation(Document doc) {
        String translatedPath = doc.getTranslatedLocalPath();
        if (translatedPath != null && !translatedPath.isBlank()) {
            String html = localDocumentStorage.readHtml(translatedPath).orElse("");
            if (TranslationMarkers.containsFailureMarker(html)) {
                return true;
            }
        }
        return TranslationMarkers.containsFailureMarker(doc.getTranslatedContent());
    }

    public CrawlTask rebuildTaskTableOfContents(Long taskId) {
        CrawlTask task = getTask(taskId);
        List<Document> documents = documentRepository.findByCrawlTaskIdOrderBySortOrderAsc(taskId);

        Map<String, Map<String, Object>> urlToToc = new LinkedHashMap<>();
        for (Document doc : documents) {
            urlToToc.put(doc.getUrl(), createTocEntry(doc, doc.getTitle(), doc.getUrl(), doc.getLocalPath()));
        }

        List<Map<String, Object>> fullTableOfContents = buildDocumentTableOfContents(urlToToc, true, task.getUrl(), null);
        try {
            task.setTableOfContents(objectMapper.writeValueAsString(fullTableOfContents));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize table of contents", e);
        }
        taskRepository.save(task);
        log.info("Rebuilt table of contents for task {} ({} top-level entries)", taskId, fullTableOfContents.size());
        return task;
    }

    private String extractCategoryFromLocalPath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return "other";
        }
        String normalizedPath = localPath.replace('\\', '/');
        
        // 排除资源目录
        if (normalizedPath.startsWith("_/") || 
            normalizedPath.startsWith("images/") ||
            normalizedPath.startsWith("images\\")) {
            return "other";
        }
        
        // 根目录文件（如 Spring Framework 概览页）归为 spring
        if (!normalizedPath.contains("/")) {
            return "spring";
        }
        
        // 根据第一级目录分类
        String firstDir = normalizedPath.split("/")[0];
        
        switch (firstDir) {
            case "spring-boot":
            case "spring-cloud":
            case "spring-mvc":
            case "spring":
            case "java":
            case "mysql":
            case "oracle":
                return firstDir;
            default:
                return "other";
        }
    }

    private String extractTitleFromHtml(String localPath) {
        return localDocumentStorage.readHtml(localPath)
                .map(html -> {
                    org.jsoup.nodes.Document doc = Jsoup.parse(html);
                    org.jsoup.nodes.Element h1 = doc.selectFirst("article.doc h1, main.article h1, .doc h1, h1");
                    if (h1 != null && !h1.text().isBlank()) {
                        return h1.text().trim();
                    }
                    String title = doc.title();
                    if (title != null && !title.isBlank()) {
                        return title.replaceAll(" :: Spring Framework$", "").trim();
                    }
                    return deriveTitleFromPath(localPath);
                })
                .orElseGet(() -> deriveTitleFromPath(localPath));
    }

    private String deriveTitleFromPath(String localPath) {
        String fileName = localPath.substring(localPath.lastIndexOf('/') + 1).replace(".html", "");
        if ("overview".equals(fileName)) {
            return "Overview";
        }
        return fileName.replace('-', ' ');
    }

    public int fixTaskCategories() {
        int fixedCount = 0;
        List<CrawlTask> tasks = taskRepository.findAll();
        
        for (CrawlTask task : tasks) {
            String url = task.getUrl();
            if (url == null) continue;
            
            String lowerUrl = url.toLowerCase();
            String oldCategory = task.getCategory();
            String newCategory = determineDocumentCategory(url);
            
            boolean categoryChanged = (oldCategory == null) ? (newCategory != null) : !oldCategory.equals(newCategory);
            if (categoryChanged) {
                task.setCategory(newCategory);
                taskRepository.save(task);
                fixedCount++;
                log.info("Fixed task category: {} -> {} for task {}", oldCategory, newCategory, task.getId());
            }
        }
        
        return fixedCount;
    }

    private boolean translatePageDocument(Document doc) {
        String localPath = doc.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            return false;
        }

        try {
            String originalHtml = localDocumentStorage.readHtml(localPath).orElse(null);
            if (originalHtml == null || originalHtml.isBlank()) {
                log.warn("HTML file not found for translation: {}", localPath);
                return false;
            }

            org.jsoup.nodes.Document htmlDoc = Jsoup.parse(originalHtml);
            org.jsoup.nodes.Element main = htmlDoc.selectFirst("main.article");
            if (main != null) {
                String translatedMain = translator.translateHtmlToChinese(main.html());
                if (TranslationMarkers.containsFailureMarker(translatedMain)) {
                    log.warn("Translation produced failure marker for {}, skipping write", localPath);
                    return false;
                }
                main.html(translatedMain);
            } else {
                String translatedBody = translator.translateHtmlToChinese(htmlDoc.body().html());
                if (TranslationMarkers.containsFailureMarker(translatedBody)) {
                    log.warn("Translation produced failure marker for {}, skipping write", localPath);
                    return false;
                }
                htmlDoc.body().html(translatedBody);
            }

            fixImageUrls(htmlDoc, localPath);

            String translatedPath = localDocumentStorage.translatedRelativePath(localPath);
            localDocumentStorage.writeHtml(translatedPath, htmlDoc.outerHtml());
            doc.setTranslatedLocalPath(translatedPath);
            log.info("Translated document: {} -> {}", localPath, translatedPath);
            return true;
        } catch (TranslationFailedException e) {
            log.warn("Failed to translate document {}: {}", localPath, e.getMessage());
            return false;
        } catch (IOException e) {
            log.warn("Failed to translate document {}: {}", localPath, e.getMessage());
            return false;
        }
    }

    /**
     * Rewrite relative image URLs so iframe preview can load assets from the backend.
     */
    void fixImageUrls(org.jsoup.nodes.Document htmlDoc, String localPath) {
        if (htmlDoc == null || localPath == null || localPath.isBlank()) {
            return;
        }

        String normalizedPath = localPath.replace('\\', '/');
        int lastSlash = normalizedPath.lastIndexOf('/');
        String docDir = lastSlash >= 0 ? normalizedPath.substring(0, lastSlash + 1) : "";

        for (org.jsoup.nodes.Element img : htmlDoc.select("img[src]")) {
            String src = img.attr("src").trim();
            if (src.isEmpty()
                    || src.startsWith("http://")
                    || src.startsWith("https://")
                    || src.startsWith("/api/crawl/")
                    || src.startsWith("data:")) {
                continue;
            }

            String assetPath = resolveRelativeAssetPath(docDir, src);
            img.attr("src", "/api/crawl/assets/" + assetPath);
        }
    }

    private String resolveRelativeAssetPath(String docDir, String relativeSrc) {
        Path resolved = Paths.get(docDir, relativeSrc).normalize();
        String normalized = resolved.toString().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /**
     * Recursively process document sections and build nested TOC simultaneously
     */
    private void processSectionsWithToc(
        List<PageSection> sections, 
        CrawlTask task, 
        Long parentDocId,
        String baseUrl,
        List<Map<String, Object>> tocList,
        int[] orderRef
    ) {
        for (PageSection section : sections) {
            // Translate section content preserving HTML structure
            String translatedContent = translator.translateHtmlToChinese(section.getContent());
            
            // Translate section title
            String translatedTitle = translator.translateToChinese(section.getTitle());
            
            Document doc = new Document();
            doc.setTitle(translatedTitle);
            doc.setUrl(baseUrl + "#" + (section.getId() != null ? section.getId() : ""));
            doc.setCrawlTask(task);
            doc.setSortOrder(orderRef[0]++);
            doc.setSectionLevel(section.getLevel());
            doc.setSectionId(section.getId());
            doc.setParentDocumentId(parentDocId);
            doc.setOriginalContent(section.getContent());
            doc.setTranslatedContent(translatedContent);
            Document savedDoc = documentRepository.save(doc);
            
            // Build TOC entry
            Map<String, Object> tocEntry = new HashMap<>();
            tocEntry.put("id", savedDoc.getId());
            tocEntry.put("title", translatedTitle);
            tocEntry.put("level", section.getLevel());
            tocEntry.put("url", savedDoc.getUrl());
            List<Map<String, Object>> childrenToc = new ArrayList<>();
            tocEntry.put("children", childrenToc);
            tocList.add(tocEntry);
            
            // Process subsections recursively
            if (section.getSubsections() != null && !section.getSubsections().isEmpty()) {
                processSectionsWithToc(
                    section.getSubsections(), 
                    task, 
                    savedDoc.getId(),
                    baseUrl,
                    childrenToc,
                    orderRef
                );
            }
        }
    }

    /**
     * Recursively process document sections (legacy, kept for compatibility)
     */
    private List<Document> processSections(
        List<PageSection> sections, 
        CrawlTask task, 
        Long parentDocId,
        int startOrder,
        String baseUrl
    ) {
        List<Document> docs = new ArrayList<>();
        int order = startOrder;
        
        for (PageSection section : sections) {
            // Translate section content preserving HTML structure
            String translatedContent = translator.translateHtmlToChinese(section.getContent());
            
            // Translate section title
            String translatedTitle = translator.translateToChinese(section.getTitle());
            
            Document doc = new Document();
            doc.setTitle(translatedTitle);
            doc.setUrl(baseUrl + "#" + (section.getId() != null ? section.getId() : ""));
            doc.setCrawlTask(task);
            doc.setSortOrder(order++);
            doc.setSectionLevel(section.getLevel());
            doc.setSectionId(section.getId());
            doc.setParentDocumentId(parentDocId);
            doc.setOriginalContent(section.getContent());
            doc.setTranslatedContent(translatedContent);
            
            docs.add(doc);
            
            // Process subsections recursively
            if (section.getSubsections() != null && !section.getSubsections().isEmpty()) {
                List<Document> subsectionDocs = processSections(
                    section.getSubsections(), 
                    task, 
                    doc.getId(),
                    order,
                    baseUrl
                );
                docs.addAll(subsectionDocs);
                order += subsectionDocs.size();
            }
        }
        
        return docs;
    }

    /**
     * Create a new crawl task with type identification
     */
    public CrawlTask createTask(String url) {
        CrawlTask task = new CrawlTask();
        task.setUrl(url != null ? url.trim() : url);
        task.setTitle("Crawl: " + url);
        task.setStatus("PENDING");
        
        // Auto identify task type
        NodeCrawlerService.TaskTypeInfo typeInfo = nodeCrawlerService.identifyTaskType(url);
        task.setTaskType(typeInfo.getType());
        task.setTypeConfidence(typeInfo.getConfidence());
        task.setTypeReason(typeInfo.getReason());
        task.setUserConfirmedType(false);
        
        return taskRepository.save(task);
    }

    /**
     * Create a new crawl task with specified type and category
     */
    public CrawlTask createTask(String url, String taskType, String category, String mediaCategory, 
                                Integer videoQuality, Boolean extractAudio, Boolean extractSubtitles) {
        CrawlTask task = new CrawlTask();
        task.setUrl(url != null ? url.trim() : url);
        task.setTitle("Crawl: " + url);
        task.setStatus("PENDING");
        task.setTaskType(taskType);
        task.setCategory(category);
        task.setMediaCategory(mediaCategory);
        task.setVideoQuality(videoQuality);
        task.setExtractAudio(extractAudio);
        task.setExtractSubtitles(extractSubtitles);
        task.setUserConfirmedType(true);
        
        // Also identify type for confidence info
        NodeCrawlerService.TaskTypeInfo typeInfo = nodeCrawlerService.identifyTaskType(url);
        task.setTypeConfidence(typeInfo.getConfidence());
        task.setTypeReason(typeInfo.getReason());
        
        return taskRepository.save(task);
    }

    /**
     * Identify task type for a URL without creating task
     */
    public NodeCrawlerService.TaskTypeInfo identifyUrlType(String url) {
        return nodeCrawlerService.identifyTaskType(url);
    }

    /**
     * Get all available categories
     */
    public NodeCrawlerService.CategoriesInfo getCategoriesInfo() {
        return nodeCrawlerService.getCategories();
    }

    public Map<String, Object> analyzeDocumentSite(String url) {
        return nodeCrawlerService.analyzeDocumentSite(url);
    }

    public List<CrawlTask> getAllTasks() {
        return taskRepository.findByOrderByCreatedAtDesc();
    }

    public Page<CrawlTask> getTasksWithPagination(Pageable pageable) {
        return taskRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public CrawlTask getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found"));
    }

    @Transactional
    public void deleteTask(Long id) {
        CrawlTask task = getTask(id);
        documentRepository.deleteByCrawlTaskId(task.getId());
        taskRepository.delete(task);
    }

    public List<Document> getTaskDocuments(Long taskId) {
        return documentRepository.findByCrawlTaskIdOrderBySortOrderAsc(taskId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<DocumentDTO> searchDocuments(String keyword) {
        return documentRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(this::toDocumentDTO)
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public DocumentDTO getDocumentDto(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
        return toDocumentDTO(doc);
    }

    private DocumentDTO toDocumentDTO(Document doc) {
        return DocumentDTO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .url(doc.getUrl())
                .taskId(doc.getCrawlTask() != null ? doc.getCrawlTask().getId() : null)
                .sortOrder(doc.getSortOrder())
                .sectionLevel(doc.getSectionLevel())
                .sectionId(doc.getSectionId())
                .parentDocumentId(doc.getParentDocumentId())
                .originalContent(doc.getOriginalContent())
                .translatedContent(doc.getTranslatedContent())
                .localPath(doc.getLocalPath())
                .translatedLocalPath(doc.getTranslatedLocalPath())
                .category(doc.getCategory())
                .build();
    }
    
    /**
     * Get table of contents for a task
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableOfContents(Long taskId) {
        CrawlTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        String tocJson = task.getTableOfContents();
        if (tocJson == null || tocJson.isEmpty()) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(tocJson, Object.class);
            // Handle both array and single object
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                return List.of((Map<String, Object>) parsed);
            } else {
                return List.of();
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize table of contents", e);
            return List.of();
        }
    }
}
