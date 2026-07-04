package com.translationapp.config;

import com.translationapp.repository.CrawlTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Marks stale RUNNING tasks as failed so the UI does not stay stuck forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckCrawlTaskRecovery implements ApplicationRunner {

    private final CrawlTaskRepository taskRepository;

    @Override
    public void run(ApplicationArguments args) {
        var stuckTasks = taskRepository.findByStatus("RUNNING");
        if (stuckTasks.isEmpty()) {
            return;
        }

        for (var task : stuckTasks) {
            task.setStatus("FAILED");
            task.setErrorMessage("任务因服务重启而中断，请重新发起爬取。");
            task.setProgressPhase("failed");
            task.setProgressMessage("已中断，请重新爬取");
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            log.warn("Marked stale RUNNING task {} as FAILED", task.getId());
        }
    }
}
