package com.translationapp.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic documentation navigation parser.
 * Detects Antora (Spring docs) and common sidebar/TOC patterns.
 */
@Slf4j
@Service
public class DocumentNavParser {

    private static final String SPRING_FRAMEWORK_REFERENCE_BASE =
            "https://docs.spring.io/spring-framework/reference/";
    private static final String NAV_CACHE_MARKER_PREFIX = "<!-- nav-cache-base:";
    private static final String NAV_CACHE_MARKER_SUFFIX = " -->";

    private static final String[] GENERIC_NAV_SELECTORS = {
            "nav.nav-menu",
            "aside nav",
            ".sidebar nav",
            ".sidebar-nav",
            ".doc-sidebar nav",
            "nav.sidebar",
            "nav[role=navigation]",
            "aside"
    };

    private final RestTemplate restTemplate;

    @Value("${crawler.nav-cache-hours:24}")
    private long navCacheHours;

    @Value("${crawler.nav-fetch-timeout-ms:30000}")
    private long navFetchTimeoutMs;

    private volatile List<NavItem> cachedNavTree;
    private volatile String cachedNavUrl;

    public DocumentNavParser(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void clearCache() {
        cachedNavTree = null;
        cachedNavUrl = null;
    }

    public List<NavItem> loadNavigationTree(String customBaseUrl) {
        return loadNavigationTree(customBaseUrl, false);
    }

    public List<NavItem> loadNavigationTree(String customBaseUrl, boolean forceRefresh) {
        if (!forceRefresh && cachedNavTree != null && !cachedNavTree.isEmpty()
                && customBaseUrl != null && customBaseUrl.equals(cachedNavUrl)) {
            return cachedNavTree;
        }

        String indexUrl = normalizeIndexUrl(customBaseUrl);
        Optional<String> navHtml = Optional.empty();
        if (!forceRefresh) {
            navHtml = readNavCacheFile(indexUrl);
            if (navHtml.isEmpty()) {
                navHtml = loadNavPanelFromLocalDocs(customBaseUrl);
            }
        }
        if (navHtml.isEmpty() && forceRefresh) {
            navHtml = loadNavHtml(indexUrl, true);
        }
        if (navHtml.isEmpty() && forceRefresh) {
            navHtml = loadNavPanelFromLocalDocs(customBaseUrl);
        }
        if (navHtml.isPresent()) {
            List<NavItem> parsed = parseNavigation(navHtml.get(), customBaseUrl);
            if (!parsed.isEmpty()) {
                log.info("Loaded navigation with {} top-level entries from {}", parsed.size(), indexUrl);
                cachedNavTree = parsed;
                cachedNavUrl = customBaseUrl;
                return parsed;
            }
        }

        log.warn("No navigation tree found for {}", customBaseUrl);
        cachedNavTree = new ArrayList<>();
        cachedNavUrl = customBaseUrl;
        return cachedNavTree;
    }

    /** Load nav from on-disk cache only (no network). */
    public List<NavItem> loadCachedNavigationTree(String customBaseUrl) {
        String indexUrl = normalizeIndexUrl(customBaseUrl);
        Optional<String> navHtml = readNavCacheFile(indexUrl, true);
        if (navHtml.isEmpty()) {
            navHtml = loadNavPanelFromLocalDocs(customBaseUrl);
        }
        if (navHtml.isEmpty()) {
            return List.of();
        }
        return parseNavigation(navHtml.get(), customBaseUrl);
    }

    public List<NavItem> parseNavigation(String html, String baseUrl) {
        Document doc = org.jsoup.Jsoup.parse(html);
        List<NavItem> antora = parseAntoraNav(doc, baseUrl);
        if (!antora.isEmpty()) {
            return antora;
        }
        List<NavItem> mysqlDlToc = parseMysqlDlToc(doc, baseUrl);
        if (!mysqlDlToc.isEmpty()) {
            return mysqlDlToc;
        }
        return parseGenericNav(doc, baseUrl);
    }

    /** MySQL Reference Manual uses DocBook {@code dl.toc} on the index page. */
    private List<NavItem> parseMysqlDlToc(Document doc, String baseUrl) {
        Element dl = doc.selectFirst("dl.toc");
        if (dl == null) {
            return List.of();
        }
        return parseDlTocItems(dl, baseUrl, 1);
    }

    private List<NavItem> parseDlTocItems(Element dl, String baseUrl, int depth) {
        List<NavItem> items = new ArrayList<>();
        for (Element child : dl.children()) {
            if (!"dt".equals(child.tagName())) {
                continue;
            }
            Element link = child.selectFirst("a");
            String title = link != null ? link.text().trim() : child.text().trim();
            if (title.isBlank()) {
                continue;
            }

            String href = link != null ? resolveNavHref(link) : null;
            List<NavItem> children = new ArrayList<>();
            Element sibling = child.nextElementSibling();
            if (sibling != null && "dd".equals(sibling.tagName())) {
                Element nestedDl = sibling.selectFirst("> dl");
                if (nestedDl != null) {
                    children.addAll(parseDlTocItems(nestedDl, baseUrl, depth + 1));
                }
            }

            if ((href == null || href.isBlank()) && children.isEmpty()) {
                continue;
            }

            NavItem item = new NavItem(href != null ? href : "", title, depth);
            item.getChildren().addAll(children);
            items.add(item);
        }
        return items;
    }

    public List<NavItem> parseAntoraNav(String html) {
        return parseAntoraNav(org.jsoup.Jsoup.parse(html), null);
    }

    public List<NavItem> parseAntoraNav(Document doc, String baseUrl) {
        Element navMenu = doc.selectFirst("nav.nav-menu");
        if (navMenu == null) {
            return List.of();
        }

        Element rootList = navMenu.selectFirst("ul.nav-list");
        if (rootList == null) {
            return List.of();
        }

        List<NavItem> items = new ArrayList<>();
        for (Element topLi : rootList.children()) {
            if (!"li".equals(topLi.tagName())) {
                continue;
            }
            Element nestedList = topLi.selectFirst("> ul.nav-list");
            if (nestedList != null) {
                items.addAll(parseNavListItems(nestedList, 1, baseUrl));
            } else {
                NavItem item = parseNavItem(topLi, 1, baseUrl);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private List<NavItem> parseGenericNav(Document doc, String baseUrl) {
        for (String selector : GENERIC_NAV_SELECTORS) {
            Element navRoot = doc.selectFirst(selector);
            if (navRoot == null) {
                continue;
            }
            Element list = navRoot.selectFirst("ul, ol");
            if (list != null && list.select("a[href]").size() >= 2) {
                List<NavItem> items = parseNavListItems(list, 1, baseUrl);
                if (!items.isEmpty()) {
                    return items;
                }
            }
            List<NavItem> flat = parseFlatNavLinks(navRoot, baseUrl);
            if (!flat.isEmpty()) {
                return flat;
            }
        }
        return List.of();
    }

    private List<NavItem> parseFlatNavLinks(Element navRoot, String baseUrl) {
        List<NavItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Element link : navRoot.select("a[href]")) {
            String href = normalizeHref(link.attr("href"), baseUrl);
            if (href == null || href.isBlank() || seen.contains(href)) {
                continue;
            }
            String title = link.text().trim();
            if (title.isBlank()) {
                continue;
            }
            seen.add(href);
            items.add(new NavItem(href, title, 1));
        }
        return items;
    }

    private List<NavItem> parseNavListItems(Element list, int depth, String baseUrl) {
        List<NavItem> items = new ArrayList<>();
        for (Element li : list.children()) {
            if (!"li".equals(li.tagName())) {
                continue;
            }
            NavItem item = parseNavItem(li, depth, baseUrl);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private NavItem parseNavItem(Element li, int depth, String baseUrl) {
        Element link = li.selectFirst("> a, > a.nav-link");
        Element childList = li.selectFirst("> ul, > ol, > ul.nav-list");
        List<NavItem> children = new ArrayList<>();
        if (childList != null) {
            children.addAll(parseNavListItems(childList, depth + 1, baseUrl));
        }

        if (link == null) {
            if (children.isEmpty()) {
                return null;
            }
            String title = li.select("> span.nav-text").text().trim();
            if (title.isBlank()) {
                title = li.ownText().trim();
            }
            if (title.isBlank()) {
                return null;
            }
            NavItem folder = new NavItem("", title, depth);
            folder.getChildren().addAll(children);
            return folder;
        }

        boolean external = link.hasClass("link-external");
        String rawHref = link.attr("href").trim();
        String href = resolveNavHref(link);
        String absoluteUrl = null;
        if (external && !rawHref.isBlank()) {
            if (rawHref.startsWith("http")) {
                int hash = rawHref.indexOf('#');
                absoluteUrl = hash >= 0 ? rawHref.substring(0, hash) : rawHref;
            } else if (baseUrl != null && !baseUrl.isBlank()) {
                String relative = normalizeHref(rawHref, null);
                if (relative != null && !relative.isBlank()) {
                    absoluteUrl = joinNavUrl(baseUrl, relative);
                }
            }
        }
        String title = link.text().trim();
        if (title.isBlank()) {
            return null;
        }

        if ((href == null || href.isBlank()) && absoluteUrl == null && children.isEmpty()) {
            return null;
        }

        NavItem item = new NavItem(href != null ? href : "", title, depth, external, absoluteUrl);
        item.getChildren().addAll(children);
        return item;
    }

    private String joinNavUrl(String baseUrl, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        if (relativePath.startsWith("http")) {
            return relativePath;
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + relativePath.replaceAll("^/+", "");
    }

    private String resolveNavHref(Element link) {
        String dataPath = link.attr("data-path");
        if (dataPath != null && !dataPath.isBlank()) {
            return normalizeHref(dataPath, null);
        }
        return normalizeHref(link.attr("href"), null);
    }

    public Map<String, String> loadTitleByPathMap(String customBaseUrl, boolean forceRefresh) {
        Map<String, String> titles = new LinkedHashMap<>();
        collectTitles(loadNavigationTree(customBaseUrl, forceRefresh), titles);
        return titles;
    }

    private void collectTitles(List<NavItem> items, Map<String, String> titles) {
        for (NavItem item : items) {
            titles.put(item.getHref(), item.getTitle());
            collectTitles(item.getChildren(), titles);
        }
    }

    private String normalizeIndexUrl(String customBaseUrl) {
        String indexUrl = customBaseUrl;
        if (indexUrl == null || indexUrl.isBlank()) {
            return "";
        }
        if (!indexUrl.endsWith("/")) {
            indexUrl += "/";
        }
        if (!indexUrl.endsWith("index.html")) {
            indexUrl += "index.html";
        }
        return indexUrl;
    }

    private Optional<String> loadNavHtml(String indexUrl, boolean forceRefresh) {
        String cacheKey = String.valueOf(indexUrl.hashCode());
        Path cacheFile = Paths.get("data/doc-nav-" + cacheKey + ".html");
        if (!forceRefresh) {
            Optional<String> cached = readNavCacheFile(indexUrl, false);
            if (cached.isPresent()) {
                return cached;
            }
            return Optional.empty();
        }

        try {
            Files.deleteIfExists(cacheFile);
        } catch (IOException e) {
            log.debug("Failed to delete nav cache: {}", e.getMessage());
        }

        try {
            String html = fetchNavHtmlWithTimeout(indexUrl);
            if (html != null && (html.contains("nav-list") || html.contains("<nav") || html.contains("sidebar")
                    || html.contains("dl class=\"toc\"") || html.contains("dl.toc"))) {
                Files.createDirectories(cacheFile.getParent());
                Files.writeString(cacheFile, html);
                return Optional.of(html);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch nav HTML from {}: {}", indexUrl, e.getMessage());
        }

        return readNavCacheFile(indexUrl, true);
    }

    /**
     * Remove backend nav HTML caches (data/doc-nav-*.html).
     */
    public void clearNavCacheFiles() {
        Path dataDir = Paths.get("data");
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (var stream = Files.list(dataDir)) {
            List<Path> removed = stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("doc-nav-") && name.endsWith(".html");
                    })
                    .toList();
            for (Path cacheFile : removed) {
                Files.deleteIfExists(cacheFile);
            }
            if (!removed.isEmpty()) {
                log.info("Cleared {} backend nav cache file(s)", removed.size());
            }
        } catch (IOException e) {
            log.warn("Failed to clear nav cache files: {}", e.getMessage());
        }
    }

    private Optional<String> readNavCacheFile(String indexUrl) {
        return readNavCacheFile(indexUrl, false);
    }

    private Optional<String> readNavCacheFile(String indexUrl, boolean ignoreTtl) {
        Path cacheFile = Paths.get("data/doc-nav-" + indexUrl.hashCode() + ".html");
        try {
            if (Files.exists(cacheFile)) {
                if (!ignoreTtl) {
                    Instant modified = Files.getLastModifiedTime(cacheFile).toInstant();
                    if (Duration.between(modified, Instant.now()).toHours() >= navCacheHours) {
                        return Optional.empty();
                    }
                }
                return Optional.of(Files.readString(cacheFile));
            }
        } catch (IOException e) {
            log.debug("Failed to read nav cache: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String fetchNavHtmlWithTimeout(String indexUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(navFetchTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(navFetchTimeoutMs));
        RestTemplate timedClient = new RestTemplate(factory);
        return timedClient.getForObject(indexUrl, String.class);
    }

    private Optional<String> loadNavPanelFromLocalDocs(String customBaseUrl) {
        if (customBaseUrl == null || customBaseUrl.isBlank()) {
            return Optional.empty();
        }
        String normalizedBase = normalizeDocBaseUrl(customBaseUrl);
        Path[] candidates = resolveNavPanelCachePaths(normalizedBase);
        for (Path candidate : candidates) {
            try {
                Path absolute = candidate.toAbsolutePath().normalize();
                if (!Files.isRegularFile(absolute)) {
                    continue;
                }
                String html = validateNavCacheContent(Files.readString(absolute), normalizedBase);
                if (html != null && !html.isBlank()) {
                    log.info("Using local nav panel cache: {}", absolute);
                    return Optional.of(html);
                }
            } catch (IOException e) {
                log.debug("Failed to read nav panel {}: {}", candidate, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private String normalizeDocBaseUrl(String customBaseUrl) {
        String normalized = customBaseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private Path[] resolveNavPanelCachePaths(String normalizedBase) {
        String fileName = navPanelCacheFileName(normalizedBase);
        return new Path[] {
                Paths.get("../crawler-service/crawled-docs/_/" + fileName),
                Paths.get("crawler-service/crawled-docs/_/" + fileName),
        };
    }

    private String navPanelCacheFileName(String normalizedBase) {
        if (SPRING_FRAMEWORK_REFERENCE_BASE.equals(normalizedBase)) {
            return "nav-panel.html";
        }
        String cacheKey = normalizedBase.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_|_$", "");
        return "nav-panel-" + cacheKey + ".html";
    }

    private String validateNavCacheContent(String html, String expectedBase) {
        if (html == null || html.isBlank()) {
            return null;
        }
        if (html.startsWith(NAV_CACHE_MARKER_PREFIX)) {
            int markerEnd = html.indexOf(NAV_CACHE_MARKER_SUFFIX);
            if (markerEnd < 0) {
                return null;
            }
            String marker = html.substring(0, markerEnd + NAV_CACHE_MARKER_SUFFIX.length());
            String cachedBase = marker
                    .substring(NAV_CACHE_MARKER_PREFIX.length(), marker.length() - NAV_CACHE_MARKER_SUFFIX.length())
                    .trim();
            if (!expectedBase.equals(cachedBase)) {
                log.warn("Ignoring nav cache with mismatched base URL: expected {}, found {}", expectedBase, cachedBase);
                return null;
            }
            return html.substring(markerEnd + NAV_CACHE_MARKER_SUFFIX.length()).trim();
        }
        if (SPRING_FRAMEWORK_REFERENCE_BASE.equals(expectedBase)) {
            return html;
        }
        log.warn("Ignoring legacy nav cache without base marker for {}", expectedBase);
        return null;
    }

    private int parseDepth(String depthAttr) {
        try {
            return Integer.parseInt(depthAttr);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String normalizeHref(String href, String baseUrl) {
        if (href == null) {
            return null;
        }
        href = href.trim();
        if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")) {
            return null;
        }
        if (href.startsWith("http")) {
            if (baseUrl != null && !href.startsWith(baseUrl)) {
                return null;
            }
            try {
                return new java.net.URL(href).getPath().replaceFirst("^/", "");
            } catch (Exception e) {
                return null;
            }
        }
        if (href.startsWith("./")) {
            href = href.substring(2);
        }
        int hashIndex = href.indexOf('#');
        if (hashIndex >= 0) {
            href = href.substring(0, hashIndex);
        }
        return href;
    }

    @Getter
    public static class NavItem {
        private final String href;
        private final String title;
        private final int depth;
        private final boolean external;
        private final String absoluteUrl;
        private final List<NavItem> children = new ArrayList<>();

        public NavItem(String href, String title, int depth) {
            this(href, title, depth, false, null);
        }

        public NavItem(String href, String title, int depth, boolean external) {
            this(href, title, depth, external, null);
        }

        public NavItem(String href, String title, int depth, boolean external, String absoluteUrl) {
            this.href = href;
            this.title = title;
            this.depth = depth;
            this.external = external;
            this.absoluteUrl = absoluteUrl;
        }
    }
}
