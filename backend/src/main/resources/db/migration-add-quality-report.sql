-- Add quality_report column for crawl quality metrics (nav coverage, etc.)
ALTER TABLE crawl_tasks ADD COLUMN IF NOT EXISTS quality_report TEXT NULL;
