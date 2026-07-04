package com.translationapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.translationapp.util.SiteKeyUtil;
import com.translationapp.util.TocUnavailableReason;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DocumentTocBuilder {

    private final DocumentNavParser navParser;

    public List<Map<String, Object>> build(
            Map<String, Map<String, Object>> urlToToc,
            Set<String> excludedPages,
            boolean forceRefreshNav,
            String customBaseUrl) {

        List<DocumentNavParser.NavItem> navTree = navParser.loadNavigationTree(customBaseUrl, forceRefreshNav);

        Map<String, Map<String, Object>> pathIndex = buildPathIndex(urlToToc, customBaseUrl);
        List<Map<String, Object>> toc = new ArrayList<>();
        Set<String> usedPaths = new HashSet<>();

        for (DocumentNavParser.NavItem item : navTree) {
            Map<String, Object> entry = buildNavEntry(item, pathIndex, excludedPages, usedPaths, customBaseUrl);
            if (entry != null) {
                toc.add(entry);
            }
        }

        if (navTree.isEmpty()) {
            appendOrphanPages(toc, urlToToc, excludedPages, usedPaths, customBaseUrl);
        }
        return toc;
    }

    public List<Map<String, Object>> buildFromNavItems(
            List<DocumentNavParser.NavItem> navTree,
            Map<String, Map<String, Object>> urlToToc,
            Set<String> excludedPages,
            String customBaseUrl) {

        if (navTree == null || navTree.isEmpty()) {
            return buildFlat(urlToToc, customBaseUrl);
        }

        Map<String, Map<String, Object>> pathIndex = buildPathIndex(urlToToc, customBaseUrl);
        List<Map<String, Object>> toc = new ArrayList<>();
        Set<String> usedPaths = new HashSet<>();

        for (DocumentNavParser.NavItem item : navTree) {
            Map<String, Object> entry = buildNavEntry(item, pathIndex, excludedPages, usedPaths, customBaseUrl);
            if (entry != null) {
                toc.add(entry);
            }
        }

        // Cached/live nav defines the doc hierarchy; do not append unmatched crawl pages.
        return toc;
    }

    public List<Map<String, Object>> buildFromCrawlerNav(
            List<Map<String, Object>> crawlerNavTree,
            Map<String, Map<String, Object>> urlToToc,
            Set<String> excludedPages,
            String baseUrl) {

        if (crawlerNavTree == null || crawlerNavTree.isEmpty()) {
            return buildFlat(urlToToc, baseUrl);
        }

        Map<String, Map<String, Object>> pathIndex = buildPathIndex(urlToToc, baseUrl);
        List<Map<String, Object>> toc = new ArrayList<>();
        Set<String> usedPaths = new HashSet<>();
        for (Map<String, Object> node : crawlerNavTree) {
            Map<String, Object> entry = buildCrawlerEntry(node, pathIndex, excludedPages, usedPaths, baseUrl);
            if (entry != null) {
                toc.add(entry);
            }
        }
        // Structured Antora nav is authoritative; avoid flat orphan append that clutters the sidebar.
        return toc;
    }

    /** Flat TOC fallback when structured nav is unavailable. */
    public List<Map<String, Object>> buildFlat(Map<String, Map<String, Object>> urlToToc, String baseUrl) {
        List<Map<String, Object>> flat = new ArrayList<>();
        for (Map<String, Object> entry : urlToToc.values()) {
            flat.add(copyEntry(entry));
        }
        flat.sort((a, b) -> {
            String pathA = (String) a.getOrDefault("localPath", "");
            String pathB = (String) b.getOrDefault("localPath", "");
            return pathA.compareTo(pathB);
        });
        return flat;
    }

    private Map<String, Object> buildCrawlerEntry(
            Map<String, Object> node,
            Map<String, Map<String, Object>> pathIndex,
            Set<String> excludedPages,
            Set<String> usedPaths,
            String baseUrl) {

        String localPath = node.get("href") != null ? node.get("href").toString() : "";
        if (localPath.isBlank() || isExcludedFromToc(localPath, excludedPages)) {
            localPath = null;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childNodes = (List<Map<String, Object>>) node.get("children");
        List<Map<String, Object>> childEntries = new ArrayList<>();
        if (childNodes != null) {
            for (Map<String, Object> child : childNodes) {
                Map<String, Object> childEntry = buildCrawlerEntry(child, pathIndex, excludedPages, usedPaths, baseUrl);
                if (childEntry != null) {
                    childEntries.add(childEntry);
                }
            }
        }

        Map<String, Object> source = localPath != null ? lookupByPath(pathIndex, localPath, baseUrl) : null;

        if (source == null && childEntries.isEmpty()) {
            return buildNavOnlyEntry(node, baseUrl);
        }

        Map<String, Object> entry = source != null ? copyEntry(source) : new LinkedHashMap<>();
        entry.put("title", node.getOrDefault("title", "Untitled"));
        entry.put("level", Math.max(0, toInt(node.get("depth"), 1) - 1));
        entry.put("available", source != null);
        if (source == null) {
            boolean external = Boolean.TRUE.equals(node.get("external"));
            String pageUrl = externalUrlFromNode(node);
            if (pageUrl == null && localPath != null && !localPath.isBlank()) {
                pageUrl = joinUrl(baseUrl, localPath);
            }
            if (shouldTreatAsExternalViewable(external, localPath, pageUrl != null ? pageUrl : "")) {
                applyExternalViewableFromNav(entry, localPath, externalUrlFromNode(node), baseUrl);
            } else {
                applyUnavailableMetadata(
                        entry,
                        localPath,
                        pageUrl != null ? pageUrl : "",
                        external,
                        !childEntries.isEmpty());
                if (localPath != null) {
                    entry.put("localPath", localPath);
                    entry.put("url", pageUrl);
                }
            }
        } else if (localPath != null) {
            entry.put("localPath", localPath);
        }
        if (source == null && localPath != null && !Boolean.TRUE.equals(entry.get("external"))) {
            if (!entry.containsKey("localPath")) {
                entry.put("localPath", localPath);
            }
            if (!entry.containsKey("url")) {
                entry.put("url", joinUrl(baseUrl, localPath));
            }
        }
        if (localPath != null) {
            usedPaths.add(localPath);
            Object storedPath = entry.get("localPath");
            if (storedPath != null && !storedPath.toString().isBlank()) {
                usedPaths.add(storedPath.toString());
            }
        }
        entry.put("children", childEntries);
        return entry;
    }

    private Map<String, Object> buildNavOnlyEntry(
            Map<String, Object> node,
            String baseUrl) {
        String localPath = node.get("href") != null ? node.get("href").toString() : "";
        if (localPath.isBlank()) {
            localPath = null;
        }
        return buildNavOnlyEntry(
                String.valueOf(node.getOrDefault("title", "Untitled")),
                localPath,
                toInt(node.get("depth"), 1),
                Boolean.TRUE.equals(node.get("external")),
                externalUrlFromNode(node),
                baseUrl);
    }

    private Map<String, Object> buildNavOnlyEntry(
            String title,
            String localPath,
            int depth,
            boolean external,
            String absoluteUrl,
            String baseUrl) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", title);
        entry.put("level", Math.max(0, depth - 1));
        String pageUrl = localPath != null && !localPath.isBlank() ? joinUrl(baseUrl, localPath) : "";
        if (shouldTreatAsExternalViewable(external, localPath, pageUrl)) {
            applyExternalViewableFromNav(entry, localPath, absoluteUrl, baseUrl);
        } else {
            entry.put("available", false);
            applyUnavailableMetadata(entry, localPath, pageUrl, external, false);
            if (localPath != null && !localPath.isBlank()) {
                entry.put("localPath", localPath);
                if (!entry.containsKey("url")) {
                    entry.put("url", pageUrl);
                }
            }
        }
        entry.put("children", new ArrayList<Map<String, Object>>());
        return entry;
    }

    private boolean shouldTreatAsExternalViewable(boolean external, String localPath, String pageUrl) {
        if (external) {
            return true;
        }
        return TocUnavailableReason.isExternalApiPath(localPath, pageUrl);
    }

    private void applyExternalViewableFromNav(
            Map<String, Object> entry,
            String localPath,
            String absoluteUrl,
            String baseUrl) {
        String extUrl = absoluteUrl != null && !absoluteUrl.isBlank()
                ? absoluteUrl
                : (localPath != null && !localPath.isBlank() ? joinUrl(baseUrl, localPath) : null);
        if (extUrl == null || extUrl.isBlank()) {
            return;
        }
        applyExternalViewable(entry, extUrl);
    }

    private String externalUrlFromNode(Map<String, Object> node) {
        Object url = node.get("url");
        if (url != null && url.toString().startsWith("http")) {
            return url.toString();
        }
        return null;
    }

    private void applyExternalViewable(Map<String, Object> entry, String absoluteUrl) {
        entry.put("available", true);
        entry.put("external", true);
        entry.put("url", absoluteUrl);
        entry.remove("localPath");
        entry.remove("unavailableReason");
    }

    private Map<String, Object> buildNavEntry(
            DocumentNavParser.NavItem navItem,
            Map<String, Map<String, Object>> pathIndex,
            Set<String> excludedPages,
            Set<String> usedPaths,
            String baseUrl) {

        String localPath = navItem.getHref();
        if (isExcludedFromToc(localPath, excludedPages)) {
            return null;
        }

        List<Map<String, Object>> childEntries = new ArrayList<>();
        for (DocumentNavParser.NavItem child : navItem.getChildren()) {
            Map<String, Object> childEntry = buildNavEntry(child, pathIndex, excludedPages, usedPaths, baseUrl);
            if (childEntry != null) {
                childEntries.add(childEntry);
            }
        }

        Map<String, Object> source = localPath != null && !localPath.isBlank()
                ? lookupByPath(pathIndex, localPath, baseUrl)
                : null;

        if (source == null && childEntries.isEmpty()) {
            return buildNavOnlyEntry(navItem.getTitle(), localPath, navItem.getDepth(), navItem.isExternal(),
                    navItem.getAbsoluteUrl(), baseUrl);
        }

        Map<String, Object> entry = source != null ? copyEntry(source) : new LinkedHashMap<>();
        entry.put("title", navItem.getTitle());
        entry.put("level", Math.max(0, navItem.getDepth() - 1));
        entry.put("available", source != null);
        if (source == null) {
            String pageUrl = navItem.getAbsoluteUrl() != null && !navItem.getAbsoluteUrl().isBlank()
                    ? navItem.getAbsoluteUrl()
                    : (localPath != null && !localPath.isBlank() ? joinUrl(baseUrl, localPath) : "");
            if (shouldTreatAsExternalViewable(navItem.isExternal(), localPath, pageUrl)) {
                applyExternalViewableFromNav(entry, localPath, navItem.getAbsoluteUrl(), baseUrl);
            } else {
                applyUnavailableMetadata(entry, localPath, pageUrl, navItem.isExternal(), !childEntries.isEmpty());
            }
        }
        if (source == null && localPath != null && !localPath.isBlank() && !Boolean.TRUE.equals(entry.get("external"))) {
            entry.put("localPath", localPath);
            if (!entry.containsKey("url")) {
                entry.put("url", joinUrl(baseUrl, localPath));
            }
        }
        if (localPath != null && !localPath.isBlank()) {
            usedPaths.add(localPath);
            Object storedPath = entry.get("localPath");
            if (storedPath != null && !storedPath.toString().isBlank()) {
                usedPaths.add(storedPath.toString());
            }
        }
        entry.put("children", childEntries);
        return entry;
    }

    private boolean isExcludedFromToc(String localPath, Set<String> excludedPages) {
        if (localPath == null || localPath.isBlank()) {
            return false;
        }
        // index.html is skipped during crawl ingest but should remain in structured nav (Overview).
        if ("index.html".equals(localPath)) {
            return false;
        }
        return excludedPages.contains(localPath);
    }

    private Map<String, Object> buildEntry(
            DocumentNavParser.NavItem navItem,
            Map<String, Map<String, Object>> urlToToc,
            Set<String> excludedPages,
            Set<String> usedPaths,
            String baseUrl) {

        String localPath = navItem.getHref();
        if (excludedPages.contains(localPath)) {
            return null;
        }

        String pageUrl = joinUrl(baseUrl, localPath);
        Map<String, Object> source = urlToToc.get(pageUrl);

        List<Map<String, Object>> childEntries = new ArrayList<>();
        for (DocumentNavParser.NavItem child : navItem.getChildren()) {
            Map<String, Object> childEntry = buildEntry(child, urlToToc, excludedPages, usedPaths, baseUrl);
            if (childEntry != null) {
                childEntries.add(childEntry);
            }
        }

        if (source == null && childEntries.isEmpty()) {
            return null;
        }

        Map<String, Object> entry = source != null ? copyEntry(source) : new LinkedHashMap<>();
        entry.put("title", navItem.getTitle());
        entry.put("level", Math.max(0, navItem.getDepth() - 1));
        if (source == null) {
            entry.put("localPath", localPath);
            entry.put("url", pageUrl);
        }
        entry.put("children", childEntries);
        if (localPath != null && !localPath.isBlank()) {
            usedPaths.add(localPath);
            Object storedPath = entry.get("localPath");
            if (storedPath != null && !storedPath.toString().isBlank()) {
                usedPaths.add(storedPath.toString());
            }
        }
        return entry;
    }

    private void appendOrphanPages(
            List<Map<String, Object>> toc,
            Map<String, Map<String, Object>> urlToToc,
            Set<String> excludedPages,
            Set<String> usedPaths,
            String baseUrl) {

        List<Map<String, Object>> orphans = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : urlToToc.entrySet()) {
            String pageUrl = entry.getKey();
            if (pageUrl.contains("#")) {
                continue;
            }
            String localPath = pageUrl.replace(baseUrl, "");
            if (!baseUrl.endsWith("/") && localPath.startsWith("/")) {
                localPath = localPath.substring(1);
            }
            if (isExcludedFromToc(localPath, excludedPages) || usedPaths.contains(localPath)) {
                continue;
            }
            orphans.add(copyEntry(entry.getValue()));
        }

        orphans.sort((a, b) -> {
            String pathA = (String) a.getOrDefault("localPath", "");
            String pathB = (String) b.getOrDefault("localPath", "");
            return pathA.compareTo(pathB);
        });
        toc.addAll(orphans);
    }

    private String joinUrl(String baseUrl, String localPath) {
        if (baseUrl.endsWith("/")) {
            return baseUrl + localPath;
        }
        return baseUrl + "/" + localPath;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private void applyUnavailableMetadata(
            Map<String, Object> entry,
            String localPath,
            String pageUrl,
            boolean external,
            boolean hasChildren) {
        String reason = TocUnavailableReason.resolve(localPath, pageUrl, external, hasChildren);
        entry.put("unavailableReason", reason);
        if (external || TocUnavailableReason.isExternalApiPath(localPath, pageUrl)) {
            entry.put("external", true);
        }
    }

    private Map<String, Object> copyEntry(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.put("children", new ArrayList<Map<String, Object>>());
        return copy;
    }

    private Map<String, Map<String, Object>> buildPathIndex(
            Map<String, Map<String, Object>> urlToToc,
            String baseUrl) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : urlToToc.entrySet()) {
            String pageUrl = entry.getKey();
            index.put(pageUrl, entry.getValue());
            String localPath = pageUrl.replace(baseUrl, "");
            if (!baseUrl.endsWith("/") && localPath.startsWith("/")) {
                localPath = localPath.substring(1);
            }
            if (!localPath.isBlank()) {
                index.put(localPath, entry.getValue());
            }
            Object storedPath = entry.getValue().get("localPath");
            if (storedPath != null && !storedPath.toString().isBlank()) {
                index.put(storedPath.toString(), entry.getValue());
            }
        }
        return index;
    }

    private Map<String, Object> lookupByPath(
            Map<String, Map<String, Object>> pathIndex,
            String localPath,
            String baseUrl) {
        if (localPath == null || localPath.isBlank()) {
            return null;
        }

        for (String candidate : pathLookupCandidates(localPath, baseUrl)) {
            Map<String, Object> source = pathIndex.get(candidate);
            if (source != null) {
                return source;
            }
            source = pathIndex.get(joinUrl(baseUrl, candidate));
            if (source != null) {
                return source;
            }
        }

        Map<String, Object> overviewMatch = lookupOverviewPage(pathIndex, localPath, baseUrl);
        if (overviewMatch != null) {
            return overviewMatch;
        }

        String suffix = "/" + normalizeStorageLocalPath(localPath);
        Map<String, Object> bestMatch = null;
        String bestKey = null;
        for (Map.Entry<String, Map<String, Object>> entry : pathIndex.entrySet()) {
            String key = entry.getKey();
            if (key.equals(localPath) || key.endsWith(suffix)) {
                if (bestKey == null || key.length() < bestKey.length()) {
                    bestKey = key;
                    bestMatch = entry.getValue();
                }
            }
        }
        return bestMatch;
    }

    private Map<String, Object> lookupOverviewPage(
            Map<String, Map<String, Object>> pathIndex,
            String localPath,
            String baseUrl) {
        if (!"index.html".equals(normalizeStorageLocalPath(localPath))) {
            return null;
        }
        String category = categoryFromBaseUrl(baseUrl);
        if (category == null) {
            return null;
        }
        return pathIndex.get(category + "/index.html");
    }

    private String categoryFromBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String lower = baseUrl.toLowerCase();
        if (lower.contains("spring-boot")) {
            return "spring-boot";
        }
        if (lower.contains("spring-cloud")) {
            return "spring-cloud";
        }
        if (lower.contains("spring-mvc")) {
            return "spring-mvc";
        }
        if (lower.contains("spring-framework") || lower.contains("/spring/")) {
            return "spring";
        }
        return null;
    }

    /** Normalize nav/crawler paths to match stored document localPath values. */
    private String normalizeStorageLocalPath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return localPath;
        }
        String normalized = localPath.replace('\\', '/').replaceAll("^/+", "");
        int firstSlash = normalized.indexOf('/');
        if (firstSlash > 0) {
            String category = normalized.substring(0, firstSlash);
            String remainder = normalized.substring(firstSlash + 1);
            if (remainder.startsWith(category + "/")) {
                return category + "/" + remainder.substring(category.length() + 1);
            }
        }
        return normalized;
    }

    private List<String> pathLookupCandidates(String localPath, String baseUrl) {
        List<String> candidates = new ArrayList<>();
        String normalized = normalizeStorageLocalPath(localPath);
        candidates.add(normalized);
        if (!normalized.equals(localPath)) {
            candidates.add(localPath);
        }
        String siteKey = SiteKeyUtil.deriveSiteKey(baseUrl);
        if (siteKey != null && !siteKey.isBlank()) {
            String withSiteKey = SiteKeyUtil.toStorageLocalPath(siteKey, normalized);
            if (!candidates.contains(withSiteKey)) {
                candidates.add(withSiteKey);
            }
            String navStorage = SiteKeyUtil.navHrefToStorageLocalPath(siteKey, normalized);
            if (!candidates.contains(navStorage)) {
                candidates.add(navStorage);
            }
        }
        int firstSlash = normalized.indexOf('/');
        if (firstSlash > 0) {
            String category = normalized.substring(0, firstSlash);
            String remainder = normalized.substring(firstSlash + 1);
            if (!remainder.startsWith(category + "/")) {
                candidates.add(category + "/" + category + "/" + remainder);
            }
        }
        return candidates;
    }
}
