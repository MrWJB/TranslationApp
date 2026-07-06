package com.translationapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node.js 爬虫服务客户端，封装类型识别、文档/视频爬取、异步任务轮询等 HTTP 调用。
 */
@Slf4j
@Service
public class NodeCrawlerService {

    private static final long ASYNC_CRAWL_TIMEOUT_MS = 3 * 60 * 60 * 1000L;
    private static final int ASYNC_UNKNOWN_PROGRESS_LIMIT = 10;
    private static final long ASYNC_POLL_INTERVAL_MS = 1200L;

    private final RestTemplate restTemplate;
    private final RestTemplate crawlerProgressRestTemplate;
    private final ObjectMapper objectMapper;

    public NodeCrawlerService(
            RestTemplate restTemplate,
            @Qualifier("crawlerProgressRestTemplate") RestTemplate crawlerProgressRestTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.crawlerProgressRestTemplate = crawlerProgressRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${crawler.node-service.url:http://localhost:3000}")
    private String nodeServiceUrl;

    /**
     * 调用 Node 爬虫服务识别 URL 对应的任务类型。
     *
     * @param url 目标 URL
     * @return 任务类型识别结果
     */
    public TaskTypeInfo identifyTaskType(String url) {
        log.info("Calling Node.js crawler service to identify task type for URL: {}", url);

        try {
            String response = postJson("/identify-type", Map.of("url", url));
            if (response == null || response.isEmpty()) {
                log.warn("Empty response from type identification service, defaulting to document");
                return defaultTaskTypeInfo("No response from service");
            }

            TaskTypeInfo taskTypeInfo = parseTaskTypeInfo(objectMapper.readTree(response));
            log.info("Identified task type: {} with confidence: {}", taskTypeInfo.getType(), taskTypeInfo.getConfidence());
            return taskTypeInfo;
        } catch (RestClientException e) {
            log.error("HTTP error calling type identification service: {}", e.getMessage());
            return defaultTaskTypeInfo("Service unavailable: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to identify task type: {}", e.getMessage(), e);
            return defaultTaskTypeInfo("Error: " + e.getMessage());
        }
    }

    private TaskTypeInfo defaultTaskTypeInfo(String reason) {
        return new TaskTypeInfo("document", 0.5, reason);
    }

    private TaskTypeInfo parseTaskTypeInfo(JsonNode rootNode) {
        String taskType = rootNode.has("taskType") ? rootNode.get("taskType").asText() : "document";
        double confidence = rootNode.has("confidence") ? rootNode.get("confidence").asDouble() : 0.5;
        String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "";
        Map<String, List<String>> suggestions = parseTypeSuggestions(rootNode.path("suggestions"));
        return new TaskTypeInfo(taskType, confidence, reason, suggestions);
    }

    private Map<String, List<String>> parseTypeSuggestions(JsonNode suggestionsNode) {
        Map<String, List<String>> suggestions = new HashMap<>();
        if (suggestionsNode.isMissingNode()) {
            return suggestions;
        }

        appendCategorySuggestions(suggestions, suggestionsNode.path("document"), "document");
        appendCategorySuggestions(suggestions, suggestionsNode.path("video"), "video");
        return suggestions;
    }

    private void appendCategorySuggestions(
            Map<String, List<String>> suggestions,
            JsonNode typeNode,
            String suggestionKey) {
        if (typeNode.isMissingNode() || !typeNode.has("categories")) {
            return;
        }
        List<String> categories = new ArrayList<>();
        for (JsonNode categoryNode : typeNode.get("categories")) {
            categories.add(categoryNode.asText());
        }
        suggestions.put(suggestionKey, categories);
    }

    /**
     * 获取爬虫服务支持的任务类型与分类信息。
     *
     * @return 分类信息，失败时返回空对象
     */
    public CategoriesInfo getCategories() {
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/categories",
                    HttpMethod.GET,
                    null,
                    String.class
            );

            String response = responseEntity.getBody();
            if (response == null || response.isEmpty()) {
                return new CategoriesInfo();
            }

            JsonNode rootNode = objectMapper.readTree(response);
            CategoriesInfo info = new CategoriesInfo();
            
            // Task types
            if (rootNode.has("taskTypes")) {
                for (JsonNode type : rootNode.get("taskTypes")) {
                    info.taskTypes.add(type.asText());
                }
            }
            
            // Doc categories
            if (rootNode.has("docCategories")) {
                for (JsonNode cat : rootNode.get("docCategories")) {
                    info.docCategories.add(new CategoryInfo(
                        cat.has("code") ? cat.get("code").asText() : "",
                        cat.has("name") ? cat.get("name").asText() : ""
                    ));
                }
            }
            
            // Video categories
            if (rootNode.has("videoCategories")) {
                for (JsonNode cat : rootNode.get("videoCategories")) {
                    info.videoCategories.add(new CategoryInfo(
                        cat.has("code") ? cat.get("code").asText() : "",
                        cat.has("name") ? cat.get("name").asText() : ""
                    ));
                }
            }
            
            return info;
        } catch (Exception e) {
            log.error("Failed to get categories: {}", e.getMessage());
            return new CategoriesInfo();
        }
    }

    /**
     * Analyze document site structure before crawling (profile, nav preview, warnings).
     */
    public Map<String, Object> analyzeDocumentSite(String baseUrl) {
        baseUrl = normalizeUrl(baseUrl);
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("baseUrl", baseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/crawl/analyze",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String response = responseEntity.getBody();
            if (response == null || response.isBlank()) {
                return Map.of("success", false, "error", "Empty response from analyzer");
            }
            JsonNode root = objectMapper.readTree(response);
            return objectMapper.convertValue(root, Map.class);
        } catch (Exception e) {
            log.error("Failed to analyze document site: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Call Node.js crawler service to crawl document pages (auto-detects site structure).
     */
    public List<CrawledPage> crawlDocumentPages(String baseUrl, int maxPages) {
        baseUrl = normalizeUrl(baseUrl);
        log.info("Calling Node.js document crawler: {} for URL: {}, maxPages: {}", nodeServiceUrl, baseUrl, maxPages);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("baseUrl", baseUrl);
            requestBody.put("maxPages", maxPages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/crawl",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return parsePagesResponse(responseEntity.getBody(), "document crawler");
        } catch (RestClientException e) {
            log.error("HTTP error calling Node.js document crawler: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error calling Node.js document crawler: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to call Node.js document crawler: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Node.js document crawler: " + e.getMessage(), e);
        }
    }

    /**
     * Start async document crawl on Node.js service; poll progress via getCrawlProgress.
     */
    public void startAsyncDocumentCrawl(String jobId, String baseUrl, int maxPages, String siteKey) {
        baseUrl = normalizeUrl(baseUrl);
        log.info("Starting async document crawl job {}: {}, maxPages: {}, siteKey: {}", jobId, baseUrl, maxPages, siteKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("baseUrl", baseUrl);
        requestBody.put("maxPages", maxPages);
        requestBody.put("jobId", jobId);
        if (siteKey != null && !siteKey.isBlank()) {
            requestBody.put("siteKey", siteKey.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            crawlerProgressRestTemplate.exchange(
                    nodeServiceUrl + "/crawl/async",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (RestClientException e) {
            log.error("HTTP error starting async crawl: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error starting async crawl: " + e.getMessage(), e);
        }
    }

    public CrawlProgress getCrawlProgress(String jobId) {
        try {
            ResponseEntity<String> responseEntity = crawlerProgressRestTemplate.exchange(
                    nodeServiceUrl + "/crawl/progress/" + jobId,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            String response = responseEntity.getBody();
            if (response == null || response.isEmpty()) {
                return new CrawlProgress("unknown", "unknown", 0, 0, "无进度信息", null);
            }

            JsonNode node = objectMapper.readTree(response);
            return new CrawlProgress(
                    node.path("status").asText("unknown"),
                    node.path("phase").asText("unknown"),
                    node.path("current").asInt(0),
                    node.path("total").asInt(0),
                    node.path("message").asText(""),
                    node.has("error") && !node.get("error").isNull() ? node.get("error").asText() : null
            );
        } catch (Exception e) {
            log.warn("Failed to get crawl progress for job {}: {}", jobId, e.getMessage());
            return new CrawlProgress("unknown", "unknown", 0, 0, "获取进度失败", e.getMessage());
        }
    }

    public List<CrawledPage> getAsyncCrawlResult(String jobId) {
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/crawl/result/" + jobId,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            return parsePagesResponse(responseEntity.getBody(), "async document crawler");
        } catch (RestClientException e) {
            log.error("HTTP error fetching async crawl result: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error fetching async crawl result: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to fetch async crawl result: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch async crawl result: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询异步文档爬取任务，直到完成或失败。
     *
     * @param jobId      异步任务 ID
     * @param onProgress 进度回调，可为 null
     * @return 爬取结果页面列表
     */
    public List<CrawledPage> waitForAsyncDocumentCrawl(
            String jobId,
            java.util.function.Consumer<CrawlProgress> onProgress) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ASYNC_CRAWL_TIMEOUT_MS;
        AsyncPollTracker tracker = new AsyncPollTracker();

        while (true) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("爬取超时（超过3小时），请减少最大页面数或稍后重试");
            }

            CrawlProgress progress = getCrawlProgress(jobId);
            tracker.handleUnknownProgress(progress);
            tracker.notifyProgressChanged(progress, onProgress);

            List<CrawledPage> completedPages = resolveCompletedAsyncCrawl(jobId, progress);
            if (completedPages != null) {
                return completedPages;
            }

            Thread.sleep(ASYNC_POLL_INTERVAL_MS);
        }
    }

    private List<CrawledPage> resolveCompletedAsyncCrawl(String jobId, CrawlProgress progress) {
        if ("completed".equals(progress.getStatus())) {
            return getAsyncCrawlResult(jobId);
        }
        if ("failed".equals(progress.getStatus())) {
            throw new RuntimeException(progress.getError() != null
                    ? progress.getError()
                    : "Crawl job failed");
        }
        return null;
    }

    private static final class AsyncPollTracker {
        private int unknownProgressCount;
        private String lastMessage;
        private String lastPhase;
        private int lastCurrent = -1;
        private int lastTotal = -1;
        private boolean firstPoll = true;

        private void handleUnknownProgress(CrawlProgress progress) {
            if ("unknown".equals(progress.getStatus())) {
                unknownProgressCount++;
                if (unknownProgressCount >= ASYNC_UNKNOWN_PROGRESS_LIMIT) {
                    throw new RuntimeException("无法获取爬虫进度，请确认 crawler-service 已重启");
                }
                return;
            }
            unknownProgressCount = 0;
        }

        private void notifyProgressChanged(
                CrawlProgress progress,
                java.util.function.Consumer<CrawlProgress> onProgress) {
            if (onProgress == null) {
                return;
            }
            boolean changed = firstPoll
                    || !java.util.Objects.equals(progress.getMessage(), lastMessage)
                    || !java.util.Objects.equals(progress.getPhase(), lastPhase)
                    || progress.getCurrent() != lastCurrent
                    || progress.getTotal() != lastTotal;
            if (!changed) {
                return;
            }
            onProgress.accept(progress);
            lastMessage = progress.getMessage();
            lastPhase = progress.getPhase();
            lastCurrent = progress.getCurrent();
            lastTotal = progress.getTotal();
            firstPoll = false;
        }
    }

    /**
     * 调用 Node 爬虫服务抓取视频页面。
     */
    public List<CrawledPage> crawlVideoPages(String url, int maxPages, String category) {
        log.info("Calling Node.js video crawler service: {} for URL: {}, maxPages: {}, category: {}",
                nodeServiceUrl, url, maxPages, category);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", url);
            requestBody.put("maxPages", maxPages);
            requestBody.put("category", category != null ? category : "other");

            String response = postJson("/crawl-video", requestBody);
            return parsePagesResponse(response, "video crawler");
        } catch (RestClientException e) {
            log.error("HTTP error calling Node.js video crawler service: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error calling Node.js video crawler service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to call Node.js video crawler service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Node.js video crawler service: " + e.getMessage(), e);
        }
    }

    /**
     * 抓取文档页面（{@link #crawlDocumentPages} 的别名，兼容旧调用方）。
     *
     * @param baseUrl  站点根 URL
     * @param maxPages 最大页面数
     * @return 爬取结果
     */
    public List<CrawledPage> crawlPages(String baseUrl, int maxPages) {
        return crawlDocumentPages(baseUrl, maxPages);
    }

    /**
     * 抓取通用文档页面（与 {@link #crawlDocumentPages} 等价）。
     *
     * @param url      目标 URL
     * @param maxPages 最大页面数
     * @return 爬取结果
     */
    public List<CrawledPage> crawlGenericPages(String url, int maxPages) {
        return crawlDocumentPages(url, maxPages);
    }

    /**
     * 调用 Node 爬虫服务刷新全部已缓存文档格式。
     *
     * @return 刷新页面数量
     */
    public int refreshAllPages() {
        log.info("Calling Node.js crawler service to refresh document format: {}", nodeServiceUrl);

        try {
            String response = postJson("/refresh-all", Map.of());
            return parseRefreshCount(response);
        } catch (RestClientException e) {
            log.error("HTTP error calling Node.js refresh service: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error calling Node.js refresh service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to call Node.js refresh service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Node.js refresh service: " + e.getMessage(), e);
        }
    }

    /**
     * 向 Node 爬虫服务发送 POST JSON 请求。
     *
     * @param path        API 路径（不含 base URL）
     * @param requestBody 请求体
     * @return 响应正文
     */
    private String postJson(String path, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                nodeServiceUrl + path,
                HttpMethod.POST,
                entity,
                String.class
        );
        return responseEntity.getBody();
    }

    private int parseRefreshCount(String response) throws Exception {
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Empty response from crawler refresh service");
        }
        JsonNode rootNode = objectMapper.readTree(response);
        if (rootNode.has("pagesRefreshed")) {
            return rootNode.get("pagesRefreshed").asInt();
        }
        if (rootNode.has("pages") && rootNode.get("pages").isArray()) {
            return rootNode.get("pages").size();
        }
        return 0;
    }

    private List<CrawledPage> parsePagesResponse(String response, String serviceName) throws Exception {
        if (response == null || response.isEmpty()) {
            log.error("Empty response from {}", serviceName);
            throw new RuntimeException("Empty response from " + serviceName);
        }

        log.info("Received response from {}, length: {}", serviceName, response.length());
        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode pagesNode = validatePagesResponseRoot(rootNode, response, serviceName);
        List<CrawledPage> pages = buildPagesFromJson(pagesNode);
        attachSiteNavAndQualityReport(pages, pagesNode);
        log.info("Successfully parsed {} pages from {}", pages.size(), serviceName);
        return pages;
    }

    private JsonNode validatePagesResponseRoot(JsonNode rootNode, String response, String serviceName) {
        if (rootNode.has("error") && !rootNode.get("error").isNull()) {
            String errorMsg = rootNode.get("error").asText();
            throw new RuntimeException(errorMsg.isBlank() ? "Crawler service error" : errorMsg);
        }
        if (rootNode.has("success") && rootNode.get("success").isBoolean() && !rootNode.get("success").asBoolean()) {
            String errorMsg = rootNode.has("message") ? rootNode.get("message").asText() : "Crawler returned success=false";
            throw new RuntimeException(errorMsg);
        }
        if (!rootNode.has("pages")) {
            log.error("Invalid response from {}, missing 'pages' field: {}", serviceName,
                    response.length() > 500 ? response.substring(0, 500) + "..." : response);
            throw new RuntimeException("Invalid response from " + serviceName + ": missing 'pages' field");
        }
        JsonNode pagesNode = rootNode.get("pages");
        if (!pagesNode.isArray()) {
            throw new RuntimeException("Invalid 'pages' field format");
        }
        return pagesNode;
    }

    private List<CrawledPage> buildPagesFromJson(JsonNode pagesNode) {
        List<CrawledPage> pages = new ArrayList<>();
        List<Map<String, Object>> siteNavTree = null;

        for (JsonNode pageNode : pagesNode) {
            CrawledPage page = parsePage(pageNode);
            if (siteNavTree == null && pageNode.has("siteNavTree")) {
                siteNavTree = parseNavTree(pageNode.get("siteNavTree"));
            }
            if ((page.getNavTree() == null || page.getNavTree().isEmpty()) && pageNode.has("navTree")) {
                page.setNavTree(parseNavTree(pageNode.get("navTree")));
            }
            pages.add(page);
        }

        if (siteNavTree != null && !siteNavTree.isEmpty() && !pages.isEmpty()) {
            pages.get(0).setSiteNavTree(siteNavTree);
        }
        return pages;
    }

    private void attachSiteNavAndQualityReport(List<CrawledPage> pages, JsonNode pagesNode) {
        if (pagesNode.isEmpty() || pages.isEmpty()) {
            return;
        }
        if (pagesNode.get(0).has("qualityReport")) {
            pages.get(0).setQualityReport(pagesNode.get(0).get("qualityReport").toString());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseNavTree(JsonNode navNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (navNode == null || !navNode.isArray()) {
            return result;
        }
        for (JsonNode node : navNode) {
            Map<String, Object> item = new HashMap<>();
            if (node.has("title")) {
                item.put("title", node.get("title").asText());
            }
            if (node.has("href")) {
                item.put("href", node.get("href").asText());
            }
            if (node.has("url")) {
                item.put("url", node.get("url").asText());
            }
            if (node.has("depth")) {
                item.put("depth", node.get("depth").asInt());
            }
            if (node.has("children")) {
                item.put("children", parseNavTree(node.get("children")));
            } else {
                item.put("children", new ArrayList<Map<String, Object>>());
            }
            result.add(item);
        }
        return result;
    }

    /**
     * Get the URL to access a crawled HTML file
     */
    public String getHtmlFileUrl(String localPath) {
        return nodeServiceUrl + "/docs/" + localPath;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String normalized = url.trim();
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private CrawledPage parsePage(JsonNode pageNode) {
        CrawledPage page = new CrawledPage();
        page.setTitle(pageNode.has("title") ? pageNode.get("title").asText() : "Untitled");
        page.setUrl(pageNode.has("url") ? pageNode.get("url").asText() : "");
        page.setLocalPath(pageNode.has("localPath") ? pageNode.get("localPath").asText() : "");
        page.setCategory(pageNode.has("category") ? pageNode.get("category").asText() : "");
        if (pageNode.has("siteKey")) {
            page.setCategory(pageNode.get("siteKey").asText());
        }
        page.setHtmlContent(""); // Content is now served from static files

        // Parse sections
        List<PageSection> sections = new ArrayList<>();
        if (pageNode.has("sections")) {
            JsonNode sectionsNode = pageNode.get("sections");
            for (JsonNode sectionNode : sectionsNode) {
                PageSection section = parseSection(sectionNode, 1);
                sections.add(section);
            }
        }
        page.setSections(sections);

        // Parse links
        List<String> links = new ArrayList<>();
        if (pageNode.has("links")) {
            JsonNode linksNode = pageNode.get("links");
            for (JsonNode linkNode : linksNode) {
                links.add(linkNode.asText());
            }
        }
        page.setLinks(links);

        return page;
    }

    private PageSection parseSection(JsonNode sectionNode, int parentLevel) {
        PageSection section = new PageSection();
        section.setTitle(sectionNode.has("title") ? sectionNode.get("title").asText() : "");
        section.setId(sectionNode.has("id") ? sectionNode.get("id").asText() : "");
        section.setLevel(sectionNode.has("level") ? sectionNode.get("level").asInt() : parentLevel);
        section.setContent(sectionNode.has("content") ? sectionNode.get("content").asText() : "");

        // Parse subsections recursively
        List<PageSection> subsections = new ArrayList<>();
        if (sectionNode.has("subsections")) {
            JsonNode subsectionsNode = sectionNode.get("subsections");
            for (JsonNode subsectionNode : subsectionsNode) {
                PageSection subsection = parseSection(subsectionNode, section.getLevel() + 1);
                subsections.add(subsection);
            }
        }
        section.setSubsections(subsections);

        return section;
    }

    public static class CrawledPage {
        private String title;
        private String url;
        private String localPath;
        private String category;
        private String htmlContent;
        private List<PageSection> sections;
        private List<String> links;
        private List<Map<String, Object>> navTree;
        private List<Map<String, Object>> siteNavTree;
        private String qualityReport;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getHtmlContent() { return htmlContent; }
        public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
        public List<PageSection> getSections() { return sections; }
        public void setSections(List<PageSection> sections) { this.sections = sections; }
        public List<String> getLinks() { return links; }
        public void setLinks(List<String> links) { this.links = links; }
        public List<Map<String, Object>> getNavTree() { return navTree; }
        public void setNavTree(List<Map<String, Object>> navTree) { this.navTree = navTree; }
        public List<Map<String, Object>> getSiteNavTree() { return siteNavTree; }
        public void setSiteNavTree(List<Map<String, Object>> siteNavTree) { this.siteNavTree = siteNavTree; }
        public String getQualityReport() { return qualityReport; }
        public void setQualityReport(String qualityReport) { this.qualityReport = qualityReport; }
    }

    public static class PageSection {
        private String title;
        private String id;
        private int level;
        private String content;
        private List<PageSection> subsections;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<PageSection> getSubsections() { return subsections; }
        public void setSubsections(List<PageSection> subsections) { this.subsections = subsections; }
    }

    public static class TaskTypeInfo {
        private String type;
        private double confidence;
        private String reason;
        private Map<String, List<String>> suggestions;

        public TaskTypeInfo() {}

        public TaskTypeInfo(String type, double confidence, String reason) {
            this.type = type;
            this.confidence = confidence;
            this.reason = reason;
            this.suggestions = new HashMap<>();
        }

        public TaskTypeInfo(String type, double confidence, String reason, Map<String, List<String>> suggestions) {
            this.type = type;
            this.confidence = confidence;
            this.reason = reason;
            this.suggestions = suggestions;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Map<String, List<String>> getSuggestions() { return suggestions; }
        public void setSuggestions(Map<String, List<String>> suggestions) { this.suggestions = suggestions; }
    }

    public static class CategoryInfo {
        private String code;
        private String name;

        public CategoryInfo() {}

        public CategoryInfo(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class CategoriesInfo {
        private List<String> taskTypes = new ArrayList<>();
        private List<CategoryInfo> docCategories = new ArrayList<>();
        private List<CategoryInfo> videoCategories = new ArrayList<>();

        public List<String> getTaskTypes() { return taskTypes; }
        public void setTaskTypes(List<String> taskTypes) { this.taskTypes = taskTypes; }
        public List<CategoryInfo> getDocCategories() { return docCategories; }
        public void setDocCategories(List<CategoryInfo> docCategories) { this.docCategories = docCategories; }
        public List<CategoryInfo> getVideoCategories() { return videoCategories; }
        public void setVideoCategories(List<CategoryInfo> videoCategories) { this.videoCategories = videoCategories; }
    }

    public static class CrawlProgress {
        private final String status;
        private final String phase;
        private final int current;
        private final int total;
        private final String message;
        private final String error;

        public CrawlProgress(String status, String phase, int current, int total, String message, String error) {
            this.status = status;
            this.phase = phase;
            this.current = current;
            this.total = total;
            this.message = message;
            this.error = error;
        }

        public String getStatus() { return status; }
        public String getPhase() { return phase; }
        public int getCurrent() { return current; }
        public int getTotal() { return total; }
        public String getMessage() { return message; }
        public String getError() { return error; }
    }
}
