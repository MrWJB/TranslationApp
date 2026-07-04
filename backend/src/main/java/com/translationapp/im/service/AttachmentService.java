package com.translationapp.im.service;

import com.translationapp.im.config.ImProperties;
import com.translationapp.im.config.MinioProperties;
import com.translationapp.im.dto.AttachmentCompleteDTO;
import com.translationapp.im.dto.PresignRequestDTO;
import com.translationapp.im.dto.PresignResponseDTO;
import com.translationapp.im.entity.Attachment;
import com.translationapp.im.repository.AttachmentRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MinioProperties minioProperties;
    private final ImProperties imProperties;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Transactional
    public PresignResponseDTO presign(Long userId, PresignRequestDTO request) {
        if (request.getSize() > imProperties.getAttachment().getMaxSizeBytes()) {
            throw new IllegalArgumentException("File too large");
        }
        if (!isMimeAllowed(request.getMimeType())) {
            throw new IllegalArgumentException("MIME type not allowed");
        }

        Attachment attachment = new Attachment();
        attachment.setUploaderId(userId);
        attachment.setBucket(minioProperties.getBucket());
        attachment.setFileName(request.getFileName());
        attachment.setMimeType(request.getMimeType());
        attachment.setSizeBytes(request.getSize());
        attachment = attachmentRepository.save(attachment);

        String objectKey = buildObjectKey(attachment.getId(), request.getFileName());
        attachment.setObjectKey(objectKey);
        attachmentRepository.save(attachment);

        PresignResponseDTO response = new PresignResponseDTO();
        response.setAttachmentId(attachment.getId());
        response.setObjectKey(objectKey);
        response.setMinioAvailable(isMinioAvailable());

        if (isMinioAvailable()) {
            try {
                String url = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.PUT)
                                .bucket(minioProperties.getBucket())
                                .object(objectKey)
                                .expiry(15, TimeUnit.MINUTES)
                                .build());
                response.setUploadUrl(url);
            } catch (Exception e) {
                log.warn("MinIO presign failed: {}", e.getMessage());
                response.setMinioAvailable(false);
            }
        }
        return response;
    }

    @Transactional
    public Attachment complete(Long userId, AttachmentCompleteDTO request) {
        Attachment attachment = attachmentRepository.findById(request.getAttachmentId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        if (!attachment.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }
        if (request.getSha256() != null) {
            attachment.setSha256(request.getSha256());
        }
        if (isMinioAvailable()) {
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(attachment.getBucket())
                        .object(attachment.getObjectKey())
                        .build());
            } catch (Exception e) {
                log.warn("MinIO object not found, marking complete anyway: {}", e.getMessage());
            }
        }
        return attachmentRepository.save(attachment);
    }

    public Map<String, String> getDownloadUrl(Long userId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));

        Map<String, String> result = new HashMap<>();
        result.put("fileName", attachment.getFileName());
        result.put("mimeType", attachment.getMimeType());

        if (!isMinioAvailable()) {
            result.put("downloadUrl", null);
            result.put("minioAvailable", "false");
            return result;
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(attachment.getBucket())
                            .object(attachment.getObjectKey())
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
            result.put("downloadUrl", url);
            result.put("minioAvailable", "true");
        } catch (Exception e) {
            log.warn("MinIO download presign failed: {}", e.getMessage());
            result.put("downloadUrl", null);
            result.put("minioAvailable", "false");
        }
        return result;
    }

    private boolean isMinioAvailable() {
        return minioProperties.isEnabled() && minioClient != null;
    }

    private String buildObjectKey(Long attachmentId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        String prefix = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return prefix + "/" + attachmentId + "/" + fileName;
    }

    private boolean isMimeAllowed(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String[] allowed = imProperties.getAttachment().getAllowedMimeTypes().split(",");
        for (String pattern : allowed) {
            pattern = pattern.trim();
            if (pattern.endsWith("/*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (mimeType.startsWith(prefix)) {
                    return true;
                }
            } else if (pattern.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }
}
