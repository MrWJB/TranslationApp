package com.translationapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class VideoService {

    @Value("${crawler.video-directory:./crawler-service/crawled-videos}")
    private String videoDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> getVideoList(String category) throws IOException {
        Path basePath = Paths.get(videoDirectory);
        if (!Files.exists(basePath)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> videos = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(basePath)) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path jsonPath : jsonFiles) {
                String cat = jsonPath.getParent().getFileName().toString();
                if (category != null && !category.isEmpty() && !cat.equals(category)) {
                    continue;
                }

                try {
                    File file = jsonPath.toFile();
                    JsonNode root = objectMapper.readTree(file);
                    
                    Map<String, Object> video = new LinkedHashMap<>();
                    video.put("id", jsonPath.getFileName().toString().replace(".json", ""));
                    video.put("title", getTextValue(root, "title", ""));
                    video.put("url", getTextValue(root, "url", ""));
                    video.put("category", cat);

                    JsonNode videoData = root.get("videoData");
                    if (videoData != null) {
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
                    } else {
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

                    video.put("crawledAt", getTextValue(root, "crawledAt", ""));
                    video.put("localPath", jsonPath.toString());

                    videos.add(video);
                } catch (Exception e) {
                    System.err.println("Failed to parse video file: " + jsonPath + ", error: " + e.getMessage());
                }
            }
        }

        videos.sort((a, b) -> {
            String timeA = (String) a.get("crawledAt");
            String timeB = (String) b.get("crawledAt");
            return Comparator.nullsLast(String::compareTo).compare(timeB, timeA);
        });

        return videos;
    }

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
                for (JsonNode ep : episodes) {
                    Map<String, Object> episode = new LinkedHashMap<>();
                    episode.put("title", getTextValue(ep, "title", ""));
                    episode.put("url", getTextValue(ep, "url", ""));
                    episode.put("episodeNumber", getIntValue(ep, "episodeNumber", 0));
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
                for (JsonNode url : videoUrls) {
                    urls.add(url.asText());
                }
                video.put("videoUrls", urls);
            }

            JsonNode m3u8Urls = videoData.get("m3u8Urls");
            if (m3u8Urls != null && m3u8Urls.isArray()) {
                List<String> urls = new ArrayList<>();
                for (JsonNode url : m3u8Urls) {
                    urls.add(url.asText());
                }
                video.put("m3u8Urls", urls);
            }
        }

        video.put("crawledAt", getTextValue(root, "crawledAt", ""));

        return video;
    }

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

    public List<String> getCategories() throws IOException {
        Path basePath = Paths.get(videoDirectory);
        if (!Files.exists(basePath)) {
            return Collections.emptyList();
        }

        List<String> categories = new ArrayList<>();
        try (Stream<Path> paths = Files.list(basePath)) {
            paths.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .forEach(categories::add);
        }
        return categories;
    }

    private Path findVideoFile(Path basePath, String id) throws IOException {
        try (Stream<Path> paths = Files.walk(basePath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(id + ".json"))
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