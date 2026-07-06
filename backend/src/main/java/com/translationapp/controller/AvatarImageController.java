package com.translationapp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 用户头像静态资源访问控制器。
 */
@RestController
@RequestMapping("/api/images/avatars")
@RequiredArgsConstructor
public class AvatarImageController {

    @Value("${app.avatar.upload-dir:uploads/avatars}")
    private String avatarUploadDir;

    /**
     * 根据文件名返回头像图片资源。
     *
     * @param filename 头像文件名
     * @return 图片资源响应，文件不存在时返回 404
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveAvatar(@PathVariable String filename) throws MalformedURLException {
        Path dir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
        Path file = dir.resolve(filename).normalize();
        if (!file.startsWith(dir) || !file.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        String contentType = filename.endsWith(".png") ? "image/png"
                : filename.endsWith(".gif") ? "image/gif"
                : filename.endsWith(".webp") ? "image/webp"
                : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
