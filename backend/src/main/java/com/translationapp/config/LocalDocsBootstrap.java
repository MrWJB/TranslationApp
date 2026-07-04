package com.translationapp.config;

import com.translationapp.repository.CrawlTaskRepository;
import com.translationapp.service.CrawlService;
import com.translationapp.service.LocalDocumentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalDocsBootstrap implements ApplicationRunner {

    private static final String DEFAULT_BASE_URL = "https://docs.spring.io/spring-framework/reference/";

    private final CrawlTaskRepository taskRepository;
    private final CrawlService crawlService;
    private final LocalDocumentStorage localDocumentStorage;

    @Value("${app.import-local-on-startup:true}")
    private boolean importOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!importOnStartup) {
            return;
        }
        if (taskRepository.count() > 0) {
            return;
        }
        if (!localDocumentStorage.hasOriginalHtmlFiles()) {
            return;
        }

        try {
            var task = crawlService.importLocalDocuments(DEFAULT_BASE_URL);
            log.info("Auto-imported local documents into task {}", task.getId());
        } catch (Exception e) {
            log.error("Failed to auto-import local documents: {}", e.getMessage(), e);
        }
    }
}
