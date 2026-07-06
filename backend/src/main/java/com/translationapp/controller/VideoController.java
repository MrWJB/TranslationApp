package com.translationapp.controller;

import com.translationapp.service.VideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频元数据 REST 控制器。
 */
@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "*")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * 获取视频分页列表。
     *
     * @param category 分类编码，可为空
     * @param page     页码，从 1 开始
     * @param size     每页条数
     * @return 分页结果
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getVideoList(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) throws IOException {
        
        List<Map<String, Object>> videos = videoService.getVideoList(category);
        
        int total = videos.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        
        List<Map<String, Object>> pageData = start < total ? videos.subList(start, end) : List.of();
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", pageData);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单个视频详情。
     *
     * @param id       视频 ID
     * @param category 分类编码，可为空
     * @return 视频详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getVideoDetail(
            @PathVariable String id,
            @RequestParam(required = false) String category) throws IOException {
        
        Map<String, Object> video = videoService.getVideoDetail(id, category);
        
        if (video == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("message", "视频不存在");
            return ResponseEntity.status(404).body(error);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", video);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 删除视频元数据文件。
     *
     * @param id       视频 ID
     * @param category 分类编码，可为空
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteVideo(
            @PathVariable String id,
            @RequestParam(required = false) String category) throws IOException {
        
        boolean deleted = videoService.deleteVideo(id, category);
        
        Map<String, Object> result = new HashMap<>();
        if (deleted) {
            result.put("code", 200);
            result.put("message", "删除成功");
        } else {
            result.put("code", 404);
            result.put("message", "视频不存在");
            return ResponseEntity.status(404).body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有视频分类目录。
     *
     * @return 分类名称列表
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() throws IOException {
        List<String> categories = videoService.getCategories();
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", categories);
        
        return ResponseEntity.ok(result);
    }
}