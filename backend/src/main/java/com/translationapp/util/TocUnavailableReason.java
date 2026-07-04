package com.translationapp.util;

import java.util.regex.Pattern;

public final class TocUnavailableReason {

    public static final String EXTERNAL_API = "external-api";
    public static final String EXCLUDED_BY_PROFILE = "excluded-by-profile";
    public static final String NAV_FOLDER = "nav-folder";
    public static final String NOT_CRAWLED = "not-crawled";

    private static final Pattern API_JAVA = Pattern.compile("(?:^|/)api/java(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern API_KOTLIN = Pattern.compile("(?:^|/)api/kotlin(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADLE_API_JAVA = Pattern.compile("gradle-plugin/api/java(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_API_JAVA = Pattern.compile("maven-plugin/api/java(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPENDIX_API = Pattern.compile("appendix/api/", Pattern.CASE_INSENSITIVE);

    private TocUnavailableReason() {
    }

    public static String normalizePath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return "";
        }
        String normalized = localPath.replace('\\', '/').replaceAll("^/+", "");
        int hash = normalized.indexOf('#');
        if (hash >= 0) {
            normalized = normalized.substring(0, hash);
        }
        return normalized;
    }

    public static boolean isExternalApiPath(String localPath, String url) {
        String path = normalizePath(localPath);
        String href = url != null ? url : "";
        if (API_JAVA.matcher(path).find()) {
            return true;
        }
        if (API_KOTLIN.matcher(path).find()) {
            return true;
        }
        if (GRADLE_API_JAVA.matcher(path).find()) {
            return true;
        }
        if (MAVEN_API_JAVA.matcher(path).find()) {
            return true;
        }
        if (APPENDIX_API.matcher(path).find()) {
            return true;
        }
        return href.contains("javadoc-api") || href.contains("kdoc-api");
    }

    public static boolean isExcludedByProfilePath(String localPath) {
        String path = normalizePath(localPath);
        if (path.isBlank()) {
            return false;
        }
        return APPENDIX_API.matcher(path).find()
                || API_JAVA.matcher(path).find()
                || API_KOTLIN.matcher(path).find()
                || GRADLE_API_JAVA.matcher(path).find()
                || MAVEN_API_JAVA.matcher(path).find();
    }

    public static String resolve(String localPath, String url, boolean external, boolean hasChildren) {
        if (external) {
            return EXTERNAL_API;
        }
        if (isExternalApiPath(localPath, url)) {
            return EXTERNAL_API;
        }
        if ((localPath == null || localPath.isBlank()) && hasChildren) {
            return NAV_FOLDER;
        }
        if (isExcludedByProfilePath(localPath)) {
            return EXCLUDED_BY_PROFILE;
        }
        return NOT_CRAWLED;
    }
}
