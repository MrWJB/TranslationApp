package com.translationapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class NodeCrawlerService {

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
     * Identify task type from URL
     */
    public TaskTypeInfo identifyTaskType(String url) {
        log.info("Calling Node.js crawler service to identify task type for URL: {}", url);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/identify-type",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String response = responseEntity.getBody();
            if (response == null || response.isEmpty()) {
                log.warn("Empty response from type identification service, defaulting to document");
                return new TaskTypeInfo("document", 0.5, "No response from service");
            }

            JsonNode rootNode = objectMapper.readTree(response);
            
            String taskType = rootNode.has("taskType") ? rootNode.get("taskType").asText() : "document";
            double confidence = rootNode.has("confidence") ? rootNode.get("confidence").asDouble() : 0.5;
            String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "";
            
            // Get suggestions
            Map<String, List<String>> suggestions = new HashMap<>();
            if (rootNode.has("suggestions")) {
                JsonNode suggestionsNode = rootNode.get("suggestions");
                if (suggestionsNode.has("document")) {
                    JsonNode docNode = suggestionsNode.get("document");
                    if (docNode.has("categories")) {
                        List<String> docCategories = new ArrayList<>();
                        for (JsonNode cat : docNode.get("categories")) {
                            docCategories.add(cat.asText());
                        }
                        suggestions.put("document", docCategories);
                    }
                }
                if (suggestionsNode.has("video")) {
                    JsonNode videoNode = suggestionsNode.get("video");
                    if (videoNode.has("categories")) {
                        List<String> videoCategories = new ArrayList<>();
                        for (JsonNode cat : videoNode.get("categories")) {
                            videoCategories.add(cat.asText());
                        }
                        suggestions.put("video", videoCategories);
                    }
                }
            }

            log.info("Identified task type: {} with confidence: {}", taskType, confidence);
            return new TaskTypeInfo(taskType, confidence, reason, suggestions);
        } catch (RestClientException e) {
            log.error("HTTP error calling type identification service: {}", e.getMessage());
            return new TaskTypeInfo("document", 0.5, "Service unavailable: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to identify task type: {}", e.getMessage(), e);
            return new TaskTypeInfo("document", 0.5, "Error: " + e.getMessage());
        }
    }

    /**
     * Get all categories from crawler service
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
     * Poll async crawl until completed or failed.
     */
    public List<CrawledPage> waitForAsyncDocumentCrawl(String jobId, java.util.function.Consumer<CrawlProgress> onProgress)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3 * 60 * 60 * 1000L;
        int unknownProgressCount = 0;
        String lastMessage = null;
        String lastPhase = null;
        int lastCurrent = -1;
        int lastTotal = -1;
        boolean firstPoll = true;

        while (true) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("爬取超时（超过3小时），请减少最大页面数或稍后重试");
            }

            CrawlProgress progress = getCrawlProgress(jobId);
            if ("unknown".equals(progress.getStatus())) {
                unknownProgressCount++;
                if (unknownProgressCount >= 10) {
                    throw new RuntimeException("无法获取爬虫进度，请确认 crawler-service 已重启");
                }
            } else {
                unknownProgressCount = 0;
            }

            if (onProgress != null) {
                boolean changed = firstPoll
                        || !java.util.Objects.equals(progress.getMessage(), lastMessage)
                        || !java.util.Objects.equals(progress.getPhase(), lastPhase)
                        || progress.getCurrent() != lastCurrent
                        || progress.getTotal() != lastTotal;
                if (changed) {
                    onProgress.accept(progress);
                    lastMessage = progress.getMessage();
                    lastPhase = progress.getPhase();
                    lastCurrent = progress.getCurrent();
                    lastTotal = progress.getTotal();
                    firstPoll = false;
                }
            }

            if ("completed".equals(progress.getStatus())) {
                return getAsyncCrawlResult(jobId);
            }
            if ("failed".equals(progress.getStatus())) {
                throw new RuntimeException(progress.getError() != null
                        ? progress.getError()
                        : "Crawl job failed");
            }

            Thread.sleep(1200);
        }
    }

    /**
     * Call Node.js crawler service to crawl pages (Spring docs legacy endpoint).
     */
    public List<CrawledPage> crawlPages(String baseUrl, int maxPages) {
        return crawlDocumentPages(baseUrl, maxPages);
    }

    public int refreshAllPages() {
        log.info("Calling Node.js crawler service to refresh document format: {}", nodeServiceUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/refresh-all",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String response = responseEntity.getBody();
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
        } catch (RestClientException e) {
            log.error("HTTP error calling Node.js refresh service: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error calling Node.js refresh service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to call Node.js refresh service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Node.js refresh service: " + e.getMessage(), e);
        }
    }

    /**
     * Call Node.js crawler service to crawl video pages
     */
    public List<CrawledPage> crawlVideoPages(String url, int maxPages, String category) {
        log.info("Calling Node.js video crawler service: {} for URL: {}, maxPages: {}, category: {}", 
                nodeServiceUrl, url, maxPages, category);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", url);
            requestBody.put("maxPages", maxPages);
            requestBody.put("category", category != null ? category : "other");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    nodeServiceUrl + "/crawl-video",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String response = responseEntity.getBody();
            
            if (response == null || response.isEmpty()) {
                log.error("Empty response from video crawler service");
                throw new RuntimeException("Empty response from video crawler service");
            }

            JsonNode rootNode = objectMapper.readTree(response);
            
            // Check for error response from crawler service
            if (rootNode.has("success") && !rootNode.get("success").asBoolean()) {
                String errorMsg = rootNode.has("error") ? rootNode.get("error").asText() : "Unknown error";
                String errorType = rootNode.has("errorType") ? rootNode.get("errorType").asText() : "CRAWL_ERROR";
                log.error("Video crawler service returned error: {} (type: {})", errorMsg, errorType);
                throw new RuntimeException("Video crawl failed: " + errorMsg);
            }
            
            if (!rootNode.has("pages")) {
                log.error("Invalid response from video crawler service, missing 'pages' field");
                throw new RuntimeException("Invalid response from video crawler service: missing 'pages' field");
            }

            JsonNode pagesNode = rootNode.get("pages");
            
            if (!pagesNode.isArray()) {
                log.error("Invalid 'pages' field, expected array");
                throw new RuntimeException("Invalid 'pages' field format");
            }

            List<CrawledPage> pages = new ArrayList<>();
            
            for (JsonNode pageNode : pagesNode) {
                CrawledPage page = parsePage(pageNode);
                pages.add(page);
            }

            log.info("Successfully parsed {} video pages from Node.js crawler service", pages.size());
            return pages;
        } catch (RestClientException e) {
            log.error("HTTP error calling Node.js video crawler service: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP error calling Node.js video crawler service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to call Node.js video crawler service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Node.js video crawler service: " + e.getMessage(), e);
        }
    }

    public List<CrawledPage> crawlGenericPages(String url, int maxPages) {
        return crawlDocumentPages(url, maxPages);
    }

    private List<CrawledPage> parsePagesResponse(String response, String serviceName) throws Exception {
        if (response == null || response.isEmpty()) {
            log.error("Empty response from {}", serviceName);
            throw new RuntimeException("Empty response from " + serviceName);
        }

        log.info("Received response from {}, length: {}", serviceName, response.length());
        JsonNode rootNode = objectMapper.readTree(response);

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

        if (!pagesNode.isEmpty() && pagesNode.get(0).has("qualityReport")) {
            pages.get(0).setQualityReport(pagesNode.get(0).get("qualityReport").toString());
        }

        log.info("Successfully parsed {} pages from {}", pages.size(), serviceName);
        return pages;
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
