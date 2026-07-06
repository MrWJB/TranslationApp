package com.translationapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 爬取视频元数据读取服务。
 */
@Slf4j
@Service
public class VideoService {

    @Value("${crawler.video-directory:./crawler-service/crawled-videos}")
    private String videoDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取视频列表，可按分类过滤。
     *
     * @param category 分类名称，为空时返回全部
     * @return 视频摘要列表
     */
    public List<Map<String, Object>> getVideoList(String category) throws IOException {
        Path basePath = Paths.get(videoDirectory);
        if (!Files.exists(basePath)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> videos = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(basePath)) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path jsonPath : jsonFiles) {
                String cat = jsonPath.getParent().getFileName().toString();
                if (category != null && !category.isEmpty() && !cat.equals(category)) {
                    continue;
                }

                try {
                    videos.add(parseVideoSummary(jsonPath, cat));
                } catch (Exception e) {
                    log.warn("Failed to parse video file {}: {}", jsonPath, e.getMessage());
                }
            }
        }

        videos.sort((firstVideo, secondVideo) -> {
            String firstTime = (String) firstVideo.get("crawledAt");
            String secondTime = (String) secondVideo.get("crawledAt");
            return Comparator.nullsLast(String::compareTo).compare(secondTime, firstTime);
        });

        return videos;
    }

    /**
     * 获取单个视频的详细信息。
     *
     * @param id       视频 ID
     * @param category 分类名称，可为空
     * @return 视频详情，不存在时返回 null
     */
    public Map<String, Object> getVideoDetail(String id, String category) throws IOException {
        Path basePath = Paths.get(videoDirectory);

        Path jsonPath;
        if (category != null && !category.isEmpty()) {
            jsonPath = basePath.resolve(category).resolve(id + ".json");
        } else {
            jsonPath = findVideoFile(basePath, id);
        }

        if (!Files.exists(jsonPath)) {
            return null;
        }

        File file = jsonPath.toFile();
        JsonNode root = objectMapper.readTree(file);

        Map<String, Object> video = new LinkedHashMap<>();
        video.put("id", id);
        video.put("title", getTextValue(root, "title", ""));
        video.put("url", getTextValue(root, "url", ""));
        video.put("category", jsonPath.getParent().getFileName().toString());

        JsonNode videoData = root.get("videoData");
        if (videoData != null) {
            populateVideoDetailFields(video, videoData);
        }

        video.put("crawledAt", getTextValue(root, "crawledAt", ""));

        return video;
    }

    /**
     * 删除指定视频元数据文件。
     *
     * @param id       视频 ID
     * @param category 分类名称，可为空
     * @return 删除成功返回 true
     */
    public boolean deleteVideo(String id, String category) throws IOException {
        Path basePath = Paths.get(videoDirectory);

        Path jsonPath;
        if (category != null && !category.isEmpty()) {
            jsonPath = basePath.resolve(category).resolve(id + ".json");
        } else {
            jsonPath = findVideoFile(basePath, id);
        }

        if (Files.exists(jsonPath)) {
            Files.delete(jsonPath);
            return true;
        }
        return false;
    }

    /**
     * 获取所有视频分类目录名称。
     *
     * @return 分类名称列表
     */
    public List<String> getCategories() throws IOException {
        Path basePath = Paths.get(videoDirectory);
        if (!Files.exists(basePath)) {
            return Collections.emptyList();
        }

        List<String> categories = new ArrayList<>();
        try (Stream<Path> paths = Files.list(basePath)) {
            paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .forEach(categories::add);
        }
        return categories;
    }

    private Map<String, Object> parseVideoSummary(Path jsonPath, String category) throws IOException {
        File file = jsonPath.toFile();
        JsonNode root = objectMapper.readTree(file);

        Map<String, Object> video = new LinkedHashMap<>();
        video.put("id", jsonPath.getFileName().toString().replace(".json", ""));
        video.put("title", getTextValue(root, "title", ""));
        video.put("url", getTextValue(root, "url", ""));
        video.put("category", category);

        JsonNode videoData = root.get("videoData");
        if (videoData != null) {
            populateVideoSummaryFields(video, videoData);
        } else {
            populateEmptyVideoSummaryFields(video);
        }

        video.put("crawledAt", getTextValue(root, "crawledAt", ""));
        video.put("localPath", jsonPath.toString());
        return video;
    }

    private void populateVideoSummaryFields(Map<String, Object> video, JsonNode videoData) {
        video.put("coverImage", getTextValue(videoData, "coverImage", ""));
        video.put("description", getTextValue(videoData, "description", ""));
        video.put("rating", getTextValue(videoData, "rating", ""));
        video.put("region", getTextValue(videoData, "region", ""));
        video.put("actors", getTextValue(videoData, "actors", ""));
        video.put("director", getTextValue(videoData, "director", ""));
        video.put("year", getTextValue(videoData, "year", ""));
        video.put("status", getTextValue(videoData, "status", ""));
        video.put("tags", getTextValue(videoData, "tags", ""));

        JsonNode episodes = videoData.get("episodes");
        if (episodes != null && episodes.isArray()) {
            video.put("totalEpisodes", episodes.size());
        } else {
            video.put("totalEpisodes", getIntValue(videoData, "totalEpisodes", 0));
        }
    }

    private void populateEmptyVideoSummaryFields(Map<String, Object> video) {
        video.put("coverImage", "");
        video.put("description", "");
        video.put("rating", "");
        video.put("region", "");
        video.put("actors", "");
        video.put("director", "");
        video.put("year", "");
        video.put("status", "");
        video.put("tags", "");
        video.put("totalEpisodes", 0);
    }

    private void populateVideoDetailFields(Map<String, Object> video, JsonNode videoData) {
        video.put("coverImage", getTextValue(videoData, "coverImage", ""));
        video.put("thumbnail", getTextValue(videoData, "thumbnail", ""));
        video.put("description", getTextValue(videoData, "description", ""));
        video.put("rating", getTextValue(videoData, "rating", ""));
        video.put("ratingCount", getTextValue(videoData, "ratingCount", ""));
        video.put("region", getTextValue(videoData, "region", ""));
        video.put("actors", getTextValue(videoData, "actors", ""));
        video.put("director", getTextValue(videoData, "director", ""));
        video.put("year", getTextValue(videoData, "year", ""));
        video.put("status", getTextValue(videoData, "status", ""));
        video.put("tags", getTextValue(videoData, "tags", ""));
        video.put("genre", getTextValue(videoData, "genre", ""));
        video.put("season", getTextValue(videoData, "season", ""));
        video.put("updateTime", getTextValue(videoData, "updateTime", ""));
        video.put("views", getTextValue(videoData, "views", ""));
        video.put("duration", getTextValue(videoData, "duration", ""));

        JsonNode episodes = videoData.get("episodes");
        if (episodes != null && episodes.isArray()) {
            List<Map<String, Object>> episodeList = new ArrayList<>();
            for (JsonNode episodeNode : episodes) {
                Map<String, Object> episode = new LinkedHashMap<>();
                episode.put("title", getTextValue(episodeNode, "title", ""));
                episode.put("url", getTextValue(episodeNode, "url", ""));
                episode.put("episodeNumber", getIntValue(episodeNode, "episodeNumber", 0));
                episodeList.add(episode);
            }
            video.put("episodes", episodeList);
            video.put("totalEpisodes", episodeList.size());
        } else {
            video.put("episodes", Collections.emptyList());
            video.put("totalEpisodes", getIntValue(videoData, "totalEpisodes", 0));
        }

        JsonNode videoUrls = videoData.get("videoUrls");
        if (videoUrls != null && videoUrls.isArray()) {
            List<String> urls = new ArrayList<>();
            for (JsonNode urlNode : videoUrls) {
                urls.add(urlNode.asText());
            }
            video.put("videoUrls", urls);
        }

        JsonNode m3u8Urls = videoData.get("m3u8Urls");
        if (m3u8Urls != null && m3u8Urls.isArray()) {
            List<String> urls = new ArrayList<>();
            for (JsonNode urlNode : m3u8Urls) {
                urls.add(urlNode.asText());
            }
            video.put("m3u8Urls", urls);
        }
    }

    private Path findVideoFile(Path basePath, String id) throws IOException {
        try (Stream<Path> paths = Files.walk(basePath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(id + ".json"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String getTextValue(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }

    private int getIntValue(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asInt(defaultValue);
    }
}
