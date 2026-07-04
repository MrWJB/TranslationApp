-- Add crawl progress columns (run once on existing translation_app database)
USE translation_app;

ALTER TABLE crawl_tasks ADD COLUMN max_pages INT NULL;
ALTER TABLE crawl_tasks ADD COLUMN progress_phase VARCHAR(32) NULL;
ALTER TABLE crawl_tasks ADD COLUMN progress_current INT NULL DEFAULT 0;
ALTER TABLE crawl_tasks ADD COLUMN progress_total INT NULL DEFAULT 0;
ALTER TABLE crawl_tasks ADD COLUMN progress_message VARCHAR(512) NULL;
