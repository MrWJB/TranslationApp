package com.translationapp.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.translationapp.dto.DocumentDTO;
import com.translationapp.entity.CrawlTask;
import com.translationapp.entity.Document;
import com.translationapp.repository.DocumentRepository;
import com.translationapp.service.CrawlService;
import com.translationapp.service.ExternalDocumentProxyService;
import com.translationapp.service.LocalDocumentStorage;
import com.translationapp.service.MenuService;
import com.translationapp.service.NodeCrawlerService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.translationapp.util.SiteKeyUtil;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private static final String PREVIEW_STYLE_MARKER = "translation-app-preview";
    private static final String PREVIEW_ONLY_CSS = """
            body { margin: 0 !important; background: #fff !important; display: block !important; padding-top: 0 !important; }
            .toolbar, .edit-this-page, nav.pagination, .nav-text { display: none !important; }
            .breadcrumbs li#copy-url:empty { display: none; }
            .nav-panel-explore, .nav-panel-search { display: none !important; }
            nav.nav-menu, .nav-container, aside.nav, aside.sidebar,
            .breadcrumbs-container, .breadcrumbs,
            main.article aside.sidebar, main.article .toc, main.article .toc-menu,
            article.doc .sectnav, article.doc nav.pagination { display: none !important; }
            main.article .content, .body > main.article { margin-left: 0 !important; max-width: none !important; }
            h1.page, #page-title, .page-title { display: block !important; visibility: visible !important; position: static !important; margin-top: 0 !important; padding-top: 0 !important; }
            article.doc, .doc .content, .content { padding-top: 0 !important; margin-top: 0 !important; }
            .body { display: block !important; }
            main.article { display: block !important; }
            .admonitionblock td.content { padding-left: 5rem !important; }
            .doc .admonitionblock td.content { padding-left: 5rem !important; }
            """;

    private static final String PREVIEW_ONLY_CSS_DARK = """
            body { margin: 0 !important; background: #252539 !important; display: block !important; padding-top: 0 !important; color: #e5eaf3 !important; }
            .toolbar, .edit-this-page, nav.pagination, .nav-text { display: none !important; }
            .breadcrumbs li#copy-url:empty { display: none; }
            .nav-panel-explore, .nav-panel-search { display: none !important; }
            nav.nav-menu, .nav-container, aside.nav, aside.sidebar,
            .breadcrumbs-container, .breadcrumbs,
            main.article aside.sidebar, main.article .toc, main.article .toc-menu,
            article.doc .sectnav, article.doc nav.pagination { display: none !important; }
            main.article .content, .body > main.article { margin-left: 0 !important; max-width: none !important; background: #252539 !important; }
            h1.page, #page-title, .page-title { display: block !important; visibility: visible !important; position: static !important; margin-top: 0 !important; padding-top: 0 !important; color: #e5eaf3 !important; }
            article.doc, .doc .content, .content { padding-top: 0 !important; margin-top: 0 !important; background: #252539 !important; color: #e5eaf3 !important; }
            .body { display: block !important; background: #252539 !important; }
            main.article { display: block !important; background: #252539 !important; }
            .admonitionblock td.content { padding-left: 5rem !important; }
            .doc .admonitionblock td.content { padding-left: 5rem !important; }
            h1, h2, h3, h4, h5, h6 { color: #e5eaf3 !important; }
            p, li, td, th { color: #e5eaf3 !important; }
            code { background: #2a2a3d !important; color: #e5eaf3 !important; }
            pre { background: #2a2a3d !important; color: #e5eaf3 !important; }
            a { color: #409eff !important; }
            blockquote { border-left-color: #409eff !important; background: #2a2a3d !important; }
            ::-webkit-scrollbar { width: 8px; height: 8px; }
            ::-webkit-scrollbar-track { background: #252539 !important; }
            ::-webkit-scrollbar-thumb { background: #4a4a6a !important; border-radius: 4px; }
            ::-webkit-scrollbar-thumb:hover { background: #6a6a8a !important; }
            table { border-collapse: collapse !important; width: 100% !important; }
            table, th, td { border-color: #4a4a6a !important; }
            th { background: #2a2a3d !important; color: #e5eaf3 !important; }
            td { background: #252539 !important; }
            tr:nth-child(even), tr:nth-child(odd) { background: #252539 !important; }
            .tableblock, .listingblock, .sidebarblock { background: #252539 !important; }
            .tableblock table, .listingblock table, .sidebarblock table { background: #252539 !important; }
            .tablist { background: #2a2a3d !important; border-color: #4a4a6a !important; }
            .tablist li { background: #2a2a3d !important; border-color: #4a4a6a !important; }
            .tablist li a { color: #e5eaf3 !important; }
            .tablist li[aria-selected="true"] { background: #4a4a6a !important; border-color: #4a4a6a !important; }
            .tablist li[aria-selected="false"] { background: #2a2a3d !important; border-color: #4a4a6a !important; }
            .tab-content, .exampleblock { background: #252539 !important; border-color: #4a4a6a !important; }
            .listingblock pre, .listingblock code { background: #2a2a3d !important; }
            """;
    private final CrawlService crawlService;
    private final DocumentRepository documentRepository;
    private final LocalDocumentStorage localDocumentStorage;
    private final ExternalDocumentProxyService externalDocumentProxyService;
    private final MenuService menuService;

    @PostMapping("/identify-type")
    public ResponseEntity<Map<String, Object>> identifyType(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        
        NodeCrawlerService.TaskTypeInfo typeInfo = crawlService.identifyUrlType(url);
        return ResponseEntity.ok(Map.of(
                "url", url,
                "taskType", typeInfo.getType(),
                "confidence", typeInfo.getConfidence(),
                "reason", typeInfo.getReason(),
                "suggestions", typeInfo.getSuggestions() != null ? typeInfo.getSuggestions() : Map.of()
        ));
    }

    @GetMapping("/categories-info")
    public ResponseEntity<Map<String, Object>> getCategoriesInfo() {
        NodeCrawlerService.CategoriesInfo info = crawlService.getCategoriesInfo();
        return ResponseEntity.ok(Map.of(
                "taskTypes", info.getTaskTypes(),
                "docCategories", info.getDocCategories().stream()
                        .map(c -> Map.of("code", c.getCode(), "name", c.getName()))
                        .collect(Collectors.toList()),
                "videoCategories", info.getVideoCategories().stream()
                        .map(c -> Map.of("code", c.getCode(), "name", c.getName()))
                        .collect(Collectors.toList())
        ));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeSite(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            url = request.get("baseUrl");
        }
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "url is required"));
        }
        return ResponseEntity.ok(crawlService.analyzeDocumentSite(normalizeUrl(url)));
    }

    @GetMapping("/match-menu")
    public ResponseEntity<com.translationapp.dto.DocumentMenuMatchDTO> matchDocumentMenu(
            @RequestParam String url,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String profileName) {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(menuService.matchDocumentCategoryMenu(normalizeUrl(url), category, profileName));
    }

    @GetMapping("/document-menus")
    public ResponseEntity<List<com.translationapp.dto.MenuDTO>> documentMenus() {
        return ResponseEntity.ok(menuService.findDocumentCategoryMenus());
    }

    @GetMapping("/storage-keys")
    public ResponseEntity<Map<String, Object>> listStorageKeys() {
        return ResponseEntity.ok(Map.of("keys", localDocumentStorage.listStorageKeys()));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCrawl(@Valid @RequestBody CrawlRequest request) {
        String url = normalizeUrl(request.getUrl());
        String siteKey = resolveSiteKey(request);
        if (siteKey != null && !SiteKeyUtil.isValidSiteKey(siteKey)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid storage key: use letters, numbers, hyphens, underscores, and / for nested paths"));
        }
        if (Boolean.TRUE.equals(request.getCreateMenu()) && request.getMenuName() != null && !request.getMenuName().isBlank()) {
            String menuPath = request.getMenuPath();
            if (menuPath == null || menuPath.isBlank()) {
                String normalizedCategory = SiteKeyUtil.deriveDisplayCategory(url);
                menuPath = "/documents/" + normalizedCategory;
            }
            menuService.ensureDocumentCategoryMenu(menuPath, request.getMenuName().trim(), "Document");
        }
        String category = request.getCategory();
        if (siteKey != null && !siteKey.isBlank()) {
            category = siteKey;
        }
        CrawlTask task = crawlService.createTask(
            url,
            request.getTaskType(),
            category,
            request.getMediaCategory(),
            request.getVideoQuality(),
            request.getExtractAudio(),
            request.getExtractSubtitles()
        );
        crawlService.markTaskRunning(task.getId(), request.getMaxPages());
        crawlService.startCrawlTask(task.getId(), url, request.getMaxPages(), siteKey);
        return ResponseEntity.ok(Map.of(
                "taskId", task.getId(),
                "taskType", task.getTaskType(),
                "category", task.getCategory() != null ? task.getCategory() : "",
                "mediaCategory", task.getMediaCategory() != null ? task.getMediaCategory() : "",
                "typeConfidence", task.getTypeConfidence(),
                "userConfirmed", task.getUserConfirmedType(),
                "message", "Crawl task started"
        ));
    }

    @PostMapping("/import-local")
    public ResponseEntity<Map<String, Object>> importLocalDocuments(
            @RequestParam(required = false, defaultValue = "https://docs.spring.io/spring-framework/reference/") String url) {
        CrawlTask task = crawlService.importLocalDocuments(url);
        return ResponseEntity.ok(Map.of(
                "taskId", task.getId(),
                "message", "Local documents imported",
                "documentCount", documentRepository.countByCrawlTaskId(task.getId())
        ));
    }

    @PostMapping("/refresh-format")
    public ResponseEntity<Map<String, Object>> refreshDocumentFormat() {
        int pagesRefreshed = crawlService.refreshDocumentFormat();
        return ResponseEntity.ok(Map.of(
                "message", "Document format refreshed",
                "pagesRefreshed", pagesRefreshed
        ));
    }


    @PostMapping("/tasks/{id}/retranslate")
    public ResponseEntity<Map<String, Object>> retranslateTask(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean onlyFailed) {
        Map<String, Object> result = crawlService.retranslateTaskDocuments(id, onlyFailed);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tasks/{id}/rebuild-toc")
    public ResponseEntity<Map<String, Object>> rebuildTaskToc(@PathVariable Long id) {
        CrawlTask task = crawlService.rebuildTaskTableOfContents(id);
        return ResponseEntity.ok(Map.of(
                "taskId", task.getId(),
                "message", "Table of contents rebuilt from site navigation"
        ));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable Long id) {
        CrawlTask task = crawlService.getTask(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", task.getId());
        result.put("url", task.getUrl());
        result.put("title", task.getTitle());
        result.put("status", task.getStatus());
        result.put("taskType", task.getTaskType());
        result.put("category", task.getCategory() != null ? task.getCategory() : "");
        result.put("mediaCategory", task.getMediaCategory() != null ? task.getMediaCategory() : "");
        result.put("typeConfidence", task.getTypeConfidence());
        result.put("userConfirmed", task.getUserConfirmedType());
        result.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");
        result.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
        result.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : "");
        result.put("tableOfContents", task.getTableOfContents() != null ? task.getTableOfContents() : "");
        result.put("qualityReport", task.getQualityReport() != null ? task.getQualityReport() : "");
        appendProgressFields(result, task);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<CrawlTask> taskPage = crawlService.getTasksWithPagination(pageRequest);
        
        List<Map<String, Object>> taskList = taskPage.getContent().stream().map(task -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", task.getId());
            map.put("url", task.getUrl());
            map.put("title", task.getTitle());
            map.put("status", task.getStatus());
            map.put("taskType", task.getTaskType());
            map.put("category", task.getCategory() != null ? task.getCategory() : "");
            map.put("mediaCategory", task.getMediaCategory() != null ? task.getMediaCategory() : "");
            map.put("typeConfidence", task.getTypeConfidence());
            map.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");
            map.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
            map.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : "");
            map.put("qualityReport", task.getQualityReport() != null ? task.getQualityReport() : "");
            appendProgressFields(map, task);
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
                "tasks", taskList,
                "total", taskPage.getTotalElements(),
                "page", taskPage.getNumber(),
                "size", taskPage.getSize(),
                "totalPages", taskPage.getTotalPages()
        ));
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        Map<String, Long> categoryCounts = documentRepository.countByCategory();
        
        List<Map<String, Object>> categories = List.of(
            Map.of("code", "java", "name", "Java", "count", categoryCounts.getOrDefault("java", 0L)),
            Map.of("code", "spring", "name", "Spring", "count", categoryCounts.getOrDefault("spring", 0L)),
            Map.of("code", "spring-boot", "name", "Spring Boot", "count", categoryCounts.getOrDefault("spring-boot", 0L)),
            Map.of("code", "spring-cloud", "name", "Spring Cloud", "count", categoryCounts.getOrDefault("spring-cloud", 0L)),
            Map.of("code", "spring-mvc", "name", "Spring Mvc", "count", categoryCounts.getOrDefault("spring-mvc", 0L)),
            Map.of("code", "mysql", "name", "Mysql", "count", categoryCounts.getOrDefault("mysql", 0L)),
            Map.of("code", "oracle", "name", "Oracle", "count", categoryCounts.getOrDefault("oracle", 0L)),
            Map.of("code", "other", "name", "其他", "count", categoryCounts.getOrDefault("other", 0L))
        );
        
        return ResponseEntity.ok(Map.of(
            "categories", categories,
            "total", categoryCounts.values().stream().mapToLong(Long::longValue).sum()
        ));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        crawlService.deleteTask(id);
        return ResponseEntity.ok(Map.of("message", "Crawl task deleted"));
    }

    @PostMapping("/fix-categories")
    public ResponseEntity<Map<String, Object>> fixTaskCategories() {
        int fixedCount = crawlService.fixTaskCategories();
        return ResponseEntity.ok(Map.of(
            "message", "Task categories fixed",
            "fixedCount", fixedCount
        ));
    }

    @GetMapping("/tasks/{id}/documents")
    public ResponseEntity<Map<String, Object>> getTaskDocuments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents = documentRepository.findByCrawlTaskId(id, pageRequest);
        
        List<DocumentDTO> documentDTOs = documents.getContent().stream()
            .map(doc -> DocumentDTO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .url(doc.getUrl())
                .taskId(id)
                .sortOrder(doc.getSortOrder())
                .sectionLevel(doc.getSectionLevel())
                .sectionId(doc.getSectionId())
                .parentDocumentId(doc.getParentDocumentId())
                .originalContent(doc.getOriginalContent())
                .translatedContent(doc.getTranslatedContent())
                .localPath(doc.getLocalPath())
                .translatedLocalPath(doc.getTranslatedLocalPath())
                .build())
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
                "documents", documentDTOs,
                "total", documents.getTotalElements(),
                "page", documents.getNumber(),
                "size", documents.getSize()
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocumentDTO>> searchDocuments(@RequestParam String keyword) {
        return ResponseEntity.ok(crawlService.searchDocuments(keyword));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(crawlService.getDocumentDto(id));
    }

    @GetMapping("/tasks/{id}/toc")
    public ResponseEntity<List<Map<String, Object>>> getTableOfContents(@PathVariable Long id) {
        return ResponseEntity.ok(crawlService.getTableOfContents(id));
    }

    @GetMapping("/images/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            Path imagesDir = localDocumentStorage.resolveImagesDir()
                    .orElse(Paths.get("../crawler-service/crawled-docs/images").normalize().toAbsolutePath());
            Path imagePath = imagesDir.resolve(filename).normalize();

            if (!imagePath.startsWith(imagesDir)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] imageBytes = Files.readAllBytes(imagePath);
            return ResponseEntity.ok()
                    .header("Content-Type", detectContentType(filename))
                    .header("Cache-Control", "public, max-age=86400")
                    .body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/assets/**")
    public ResponseEntity<byte[]> getAsset(HttpServletRequest request) {
        try {
            String prefix = request.getContextPath() + "/api/crawl/assets/";
            String fullPath = request.getRequestURI();
            if (!fullPath.startsWith(prefix)) {
                return ResponseEntity.badRequest().build();
            }

            String assetPath = java.net.URLDecoder.decode(fullPath.substring(prefix.length()), "UTF-8");
            Path filePath = localDocumentStorage.resolveAssetFile(assetPath).orElse(null);
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header("Content-Type", detectContentType(filePath.getFileName().toString()))
                    .header("Cache-Control", "public, max-age=86400")
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/docs")
    public ResponseEntity<String> getHtmlFile(@RequestParam String path, @RequestParam(required = false) String theme) {
        try {
            path = java.net.URLDecoder.decode(path, "UTF-8");
            
            Path htmlPath = localDocumentStorage.resolveDocsFile(path).orElse(null);
            if (htmlPath == null) {
                return ResponseEntity.notFound().build();
            }
            
            String htmlContent = Files.readString(htmlPath);
            htmlContent = applyPreviewOnlyStyles(htmlContent, theme);

            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .header("Cache-Control", "public, max-age=86400")
                    .body(htmlContent);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/external")
    public ResponseEntity<String> getExternalHtml(@RequestParam String url) {
        try {
            url = java.net.URLDecoder.decode(url, "UTF-8");
            String htmlContent = externalDocumentProxyService.fetchProxiedHtml(url);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .header("Cache-Control", "public, max-age=3600")
                    .body(htmlContent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String normalizeUrl(String url) {
        return url == null ? null : url.trim();
    }

    private static String resolveSiteKey(CrawlRequest request) {
        if (request.getSiteKey() != null && !request.getSiteKey().isBlank()) {
            return request.getSiteKey().trim();
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            return request.getCategory().trim();
        }
        return null;
    }

    private void appendProgressFields(Map<String, Object> map, CrawlTask task) {
        map.put("maxPages", task.getMaxPages() != null ? task.getMaxPages() : 0);
        map.put("progressPhase", task.getProgressPhase() != null ? task.getProgressPhase() : "");
        map.put("progressCurrent", task.getProgressCurrent() != null ? task.getProgressCurrent() : 0);
        map.put("progressTotal", task.getProgressTotal() != null ? task.getProgressTotal() : 0);
        map.put("progressMessage", task.getProgressMessage() != null ? task.getProgressMessage() : "");
        int total = task.getProgressTotal() != null && task.getProgressTotal() > 0
                ? task.getProgressTotal()
                : (task.getMaxPages() != null ? task.getMaxPages() : 0);
        int current = task.getProgressCurrent() != null ? task.getProgressCurrent() : 0;
        map.put("progressPercent", total > 0 ? Math.min(100, (int) Math.round(current * 100.0 / total)) : 0);
    }

    private String detectContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lowerFilename.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lowerFilename.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String applyPreviewOnlyStyles(String htmlContent, String theme) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return htmlContent;
        }

        String css = "dark".equals(theme) ? PREVIEW_ONLY_CSS_DARK : PREVIEW_ONLY_CSS;

        if (htmlContent.contains(PREVIEW_STYLE_MARKER)) {
            return htmlContent.replaceFirst(
                    "<style id=\"" + PREVIEW_STYLE_MARKER + "\">[\\s\\S]*?</style>",
                    "<style id=\"" + PREVIEW_STYLE_MARKER + "\">" + css + "</style>");
        }

        String styleBlock = "<style id=\"" + PREVIEW_STYLE_MARKER + "\">" + css + "</style>";
        if (htmlContent.contains("</head>")) {
            return htmlContent.replaceFirst("</head>", styleBlock + "\n</head>");
        }
        return styleBlock + htmlContent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CrawlRequest {
        @NotBlank
        private String url;

        @Min(1)
        @Max(500)
        private int maxPages = 5;

        private String taskType = "document";       // document, video, audio, image, mixed
        private String category;       // 文档分类
        private String siteKey;        // 自定义存储键（crawled-docs 下路径）
        private String mediaCategory;  // 视频分类
        private Integer videoQuality;  // 视频质量: 1080, 720, 480, 360
        private Boolean extractAudio = false;
        private Boolean extractSubtitles = false;
        private Boolean createMenu = false;
        private String menuName;
        private String menuPath;
    }
}
