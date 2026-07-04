package com.translationapp.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class SiteKeyUtil {

    private SiteKeyUtil() {
    }

    /**
     * Derive stable storage key from document base URL (mirrors crawler deriveSiteKey).
     */
    public static String deriveSiteKey(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "unknown";
        }
        try {
            String normalized = baseUrl.trim();
            if (!normalized.endsWith("/")) {
                normalized += "/";
            }
            URI uri = URI.create(normalized);
            String pathname = uri.getPath() != null ? uri.getPath() : "";
            pathname = pathname.replaceAll("/index\\.html$", "").replaceAll("/$", "");
            String[] segments = pathname.isEmpty() ? new String[0] : pathname.split("/");

            if (segments.length == 0) {
                return uri.getHost() != null ? uri.getHost().replace('.', '-') : "unknown";
            }

            int from = Math.max(0, segments.length - 3);
            StringBuilder tail = new StringBuilder();
            for (int i = from; i < segments.length; i++) {
                if (tail.length() > 0) {
                    tail.append('-');
                }
                tail.append(segments[i]);
            }

            String key = tail.toString().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");

            if (!key.isBlank()) {
                return key;
            }

            String host = uri.getHost() != null ? uri.getHost().replace('.', '-') : "unknown";
            return host + "-" + md5Hex(pathname).substring(0, 8);
        } catch (Exception e) {
            return "unknown-" + System.currentTimeMillis();
        }
    }

    public static String toStorageLocalPath(String siteKey, String docRelativePath) {
        if (docRelativePath == null || docRelativePath.isBlank()) {
            return docRelativePath;
        }
        String relative = docRelativePath.replace('\\', '/').replaceAll("^/", "");
        if (siteKey == null || siteKey.isBlank()) {
            return relative;
        }
        String prefix = siteKey + "/";
        if (relative.startsWith(prefix)) {
            return relative;
        }
        return prefix + relative;
    }

    /** Antora component prefix in nav hrefs (spring-boot/reference/...), not content folders like core/. */
    public static String antoraComponentPrefix(String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return null;
        }
        if (siteKey.startsWith("spring-boot")) {
            return "spring-boot";
        }
        if (siteKey.startsWith("spring-framework")) {
            return "spring-framework";
        }
        if (siteKey.startsWith("spring-cloud")) {
            return "spring-cloud";
        }
        if (siteKey.startsWith("spring-mvc")) {
            return "spring-mvc";
        }
        return null;
    }

    /**
     * Map nav href to on-disk localPath (siteKey/relative).
     * Mirrors crawler-service site-key.js navHrefToStorageLocalPath.
     */
    public static String navHrefToStorageLocalPath(String siteKey, String navHref) {
        if (navHref == null || navHref.isBlank()) {
            return navHref;
        }
        String relative = navHref.replace('\\', '/').replaceAll("^/", "");
        if (relative.isBlank()) {
            return relative;
        }
        String prefix = siteKey + "/";
        if (relative.startsWith(prefix)) {
            return relative;
        }
        int slash = relative.indexOf('/');
        if (slash > 0) {
            String firstSegment = relative.substring(0, slash);
            String componentPrefix = antoraComponentPrefix(siteKey);
            if (componentPrefix != null && componentPrefix.equals(firstSegment)) {
                String withoutComponent = relative.substring(slash + 1);
                if (!withoutComponent.isBlank()) {
                    return toStorageLocalPath(siteKey, withoutComponent);
                }
            }
        }
        return toStorageLocalPath(siteKey, relative);
    }

    /**
     * UI/menu category for document list filtering (spring, spring-boot, …).
     */
    public static String deriveDisplayCategory(String url) {
        if (url == null || url.isBlank()) {
            return "other";
        }
        String lower = url.toLowerCase();
        if (lower.contains("spring-boot")) {
            return "spring-boot";
        }
        if (lower.contains("spring-cloud")) {
            return "spring-cloud";
        }
        if (lower.contains("spring-mvc")) {
            return "spring-mvc";
        }
        if (lower.contains("spring-framework") || lower.contains("docs.spring.io/spring/")) {
            return "spring";
        }
        if (lower.contains("mysql")) {
            return "mysql";
        }
        if (lower.contains("oracle")) {
            return "oracle";
        }
        if (lower.contains("java") || lower.contains("docs.oracle.com/javase")) {
            return "java";
        }
        return deriveSiteKey(url);
    }

    /**
     * Validate custom storage key (allows nested paths like spring-data/reference).
     */
    public static boolean isValidSiteKey(String siteKey) {
        if (siteKey == null || siteKey.isBlank() || siteKey.length() > 200) {
            return false;
        }
        String trimmed = siteKey.trim();
        if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.endsWith("/")) {
            return false;
        }
        if (trimmed.contains("//") || trimmed.matches(".*[\\\\:*?\"<>|].*")) {
            return false;
        }
        return trimmed.matches("^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?(\\/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?)*$");
    }

    public static String normalizeSiteKey(String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return null;
        }
        String trimmed = siteKey.trim();
        return isValidSiteKey(trimmed) ? trimmed : null;
    }

    /** Whether a stored category value belongs to a UI category filter. */
    public static boolean matchesDisplayCategory(String storedCategory, String filterCategory, String taskUrl) {
        if (filterCategory == null || filterCategory.isBlank()) {
            return true;
        }
        if (filterCategory.equals(storedCategory)) {
            return true;
        }
        String display = deriveDisplayCategory(taskUrl);
        if (filterCategory.equals(display)) {
            return true;
        }
        if ("spring".equals(filterCategory)) {
            return storedCategory != null && storedCategory.contains("spring-framework");
        }
        if ("spring-boot".equals(filterCategory)) {
            return storedCategory != null && storedCategory.contains("spring-boot");
        }
        return false;
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "00000000";
        }
    }
}
