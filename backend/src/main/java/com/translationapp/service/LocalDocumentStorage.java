package com.translationapp.service;

import com.translationapp.util.SiteKeyUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
public class LocalDocumentStorage {

    public static final String TRANSLATED_PREFIX = "translated/";
    private static final String SPRING_DOC_BASE_URL = "https://docs.spring.io/spring-framework/reference/";

    @Value("${crawler.docs-dirs:}")
    private String configuredDocsDirs;

    private Path[] cachedBaseDirs = new Path[0];

    @PostConstruct
    void logResolvedBaseDirs() {
        cachedBaseDirs = discoverBaseDirs();
        for (Path baseDir : cachedBaseDirs) {
            log.info("Local document base directory: {}", baseDir);
        }
        if (cachedBaseDirs.length == 0) {
            log.warn("No crawled-docs directory found. HTML preview and import will not work.");
        } else {
            resolveDocsFile("overview.html").ifPresentOrElse(
                    path -> log.info("Sample document resolved: {}", path),
                    () -> log.warn("Sample document overview.html could not be resolved")
            );
        }
    }

    public Path[] docsBaseDirs() {
        if (cachedBaseDirs.length == 0) {
            cachedBaseDirs = discoverBaseDirs();
        }
        return cachedBaseDirs;
    }

    private Path[] discoverBaseDirs() {
        Set<Path> dirs = new LinkedHashSet<>();

        if (configuredDocsDirs != null && !configuredDocsDirs.isBlank()) {
            for (String configured : configuredDocsDirs.split(",")) {
                addIfDirectory(dirs, Paths.get(configured.trim()));
            }
        }

        Path userDir = Paths.get("").toAbsolutePath().normalize();
        Path workspaceRoot = "backend".equalsIgnoreCase(String.valueOf(userDir.getFileName()))
                ? userDir.getParent()
                : userDir;

        addIfDirectory(dirs, workspaceRoot.resolve("crawler-service/crawled-docs"));
        addIfDirectory(dirs, userDir.resolve("../crawler-service/crawled-docs"));
        addIfDirectory(dirs, userDir.resolve("crawled-docs"));
        addIfDirectory(dirs, userDir.resolve("../crawled-docs"));

        return dirs.toArray(Path[]::new);
    }

    private void addIfDirectory(Set<Path> dirs, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path absolute = candidate.toAbsolutePath().normalize();
        if (Files.isDirectory(absolute)) {
            dirs.add(absolute);
        }
    }

    public Optional<Path> resolveDocsFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }

        String normalizedRelative = relativePath.replace('\\', '/').trim();
        if (normalizedRelative.startsWith("/") || normalizedRelative.contains("..")) {
            return Optional.empty();
        }

        for (Path baseDir : docsBaseDirs()) {
            Path absoluteBase = baseDir.toAbsolutePath().normalize();
            Path candidate = absoluteBase.resolve(normalizedRelative).normalize();
            if (candidate.startsWith(absoluteBase) && Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<Path> resolveImagesDir() {
        for (Path baseDir : docsBaseDirs()) {
            Path imagesDir = baseDir.resolve("images").normalize();
            if (Files.isDirectory(imagesDir)) {
                return Optional.of(imagesDir);
            }
        }
        return Optional.empty();
    }

    public Optional<Path> resolveAssetFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }

        String normalizedRelative = relativePath.replace('\\', '/').trim();
        if (normalizedRelative.startsWith("/") || normalizedRelative.contains("..")) {
            return Optional.empty();
        }

        for (Path baseDir : docsBaseDirs()) {
            Path absoluteBase = baseDir.toAbsolutePath().normalize();
            Path candidate = absoluteBase.resolve(normalizedRelative).normalize();
            if (candidate.startsWith(absoluteBase) && Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<String> readHtml(String relativePath) {
        return resolveDocsFile(relativePath).map(path -> {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read HTML file: " + relativePath, e);
            }
        });
    }

    public Path writeHtml(String relativePath, String content) throws IOException {
        Path absoluteBase = getPrimaryBaseDir()
                .orElseThrow(() -> new IOException("No crawled-docs directory found"));

        String normalizedRelative = relativePath.replace('\\', '/').trim();
        Path target = absoluteBase.resolve(normalizedRelative).normalize();
        if (!target.startsWith(absoluteBase)) {
            throw new IOException("Invalid document path: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return target;
    }

    public String translatedRelativePath(String localPath) {
        return TRANSLATED_PREFIX + localPath;
    }

    public Optional<Path> getPrimaryBaseDir() {
        Path[] baseDirs = docsBaseDirs();
        if (baseDirs.length > 0) {
            return Optional.of(baseDirs[0]);
        }
        return Optional.empty();
    }

    public List<String> listOriginalHtmlFiles() throws IOException {
        Optional<Path> baseDir = getPrimaryBaseDir();
        if (baseDir.isEmpty()) {
            return List.of();
        }

        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(baseDir.get())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .forEach(path -> {
                        String relative = baseDir.get().relativize(path.normalize()).toString().replace('\\', '/');
                        if (!relative.startsWith(TRANSLATED_PREFIX)
                                && !relative.startsWith("images/")
                                && !relative.startsWith("_/")) {
                            files.add(relative);
                        }
                    });
        }
        files.sort(String::compareTo);
        return files;
    }

    public boolean hasOriginalHtmlFiles() {
        try {
            return !listOriginalHtmlFiles().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Delete all contents of crawled-docs directories (keeps the root folder).
     */
    public void clearAllCrawledDocs() {
        for (Path baseDir : docsBaseDirs()) {
            try {
                clearDirectoryContents(baseDir);
                log.info("Cleared crawled-docs contents: {}", baseDir);
            } catch (IOException e) {
                log.warn("Failed to clear crawled-docs at {}: {}", baseDir, e.getMessage());
            }
        }
    }

    /**
     * Delete only one site's folder under crawled-docs (preserves other sites' files).
     */
    public void clearSiteKey(String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return;
        }
        for (Path baseDir : docsBaseDirs()) {
            Path siteDir = baseDir.resolve(siteKey).normalize();
            if (siteDir.startsWith(baseDir) && Files.isDirectory(siteDir)) {
                try {
                    deleteRecursively(siteDir);
                    log.info("Cleared site folder: {}", siteDir);
                } catch (IOException e) {
                    log.warn("Failed to clear site folder {}: {}", siteDir, e.getMessage());
                }
            }
        }
    }

    /**
     * List top-level storage keys (directories) under crawled-docs.
     */
    public List<String> listStorageKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Path baseDir : docsBaseDirs()) {
            if (!Files.isDirectory(baseDir)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(baseDir)) {
                for (Path entry : entries.toList()) {
                    if (!Files.isDirectory(entry)) {
                        continue;
                    }
                    String name = entry.getFileName().toString();
                    if (name.startsWith("_") || name.equals("images")) {
                        continue;
                    }
                    if (SiteKeyUtil.isValidSiteKey(name)) {
                        keys.add(name);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to list storage keys at {}: {}", baseDir, e.getMessage());
            }
        }
        return new ArrayList<>(keys);
    }

    /** Count documents whose HTML file is missing on disk. */
    public int countMissingFiles(List<String> relativePaths) {
        int missing = 0;
        for (String relativePath : relativePaths) {
            if (resolveDocsFile(relativePath).isEmpty()) {
                missing++;
            }
        }
        return missing;
    }

    private void clearDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                deleteRecursively(entry);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.walk(path)) {
                List<Path> reversed = children.sorted((a, b) -> b.compareTo(a)).toList();
                for (Path child : reversed) {
                    Files.deleteIfExists(child);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    public String toPageUrl(String localPath) {
        return SPRING_DOC_BASE_URL + localPath;
    }
}
