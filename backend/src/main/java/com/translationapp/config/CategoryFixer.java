package com.translationapp.config;

import com.translationapp.service.CrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryFixer implements ApplicationRunner {

    private final CrawlService crawlService;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            int fixedCount = crawlService.fixTaskCategories();
            if (fixedCount > 0) {
                log.info("Fixed {} task categories on startup", fixedCount);
            }
        } catch (Exception e) {
            log.error("Failed to fix task categories on startup: {}", e.getMessage(), e);
        }
    }
}
