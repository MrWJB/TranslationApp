/**
 * In-memory crawl job tracking for async crawls and progress polling.
 */
const jobs = new Map();

function createJob(jobId, maxPages) {
  const job = {
    jobId,
    status: 'running',
    phase: 'crawling',
    current: 0,
    total: maxPages || 0,
    message: '已启动，正在连接站点…',
    pages: null,
    error: null,
    startedAt: Date.now(),
    updatedAt: Date.now(),
  };
  jobs.set(String(jobId), job);
  return job;
}

function updateJob(jobId, patch) {
  const job = jobs.get(String(jobId));
  if (!job) return null;
  Object.assign(job, patch, { updatedAt: Date.now() });
  return job;
}

function completeJob(jobId, pages) {
  return updateJob(jobId, {
    status: 'completed',
    phase: 'done',
    pages,
    current: pages?.length || 0,
    message: `爬取完成，共 ${pages?.length || 0} 页`,
  });
}

function failJob(jobId, error) {
  return updateJob(jobId, {
    status: 'failed',
    phase: 'failed',
    error: error?.message || String(error),
    message: error?.message || String(error),
  });
}

function getJob(jobId) {
  return jobs.get(String(jobId)) || null;
}

function removeJob(jobId) {
  jobs.delete(String(jobId));
}

/** Remove jobs older than 24h to avoid memory leak */
function pruneOldJobs() {
  const cutoff = Date.now() - 24 * 60 * 60 * 1000;
  for (const [id, job] of jobs.entries()) {
    if (job.updatedAt < cutoff) {
      jobs.delete(id);
    }
  }
}

setInterval(pruneOldJobs, 60 * 60 * 1000);

module.exports = {
  createJob,
  updateJob,
  completeJob,
  failJob,
  getJob,
  removeJob,
};
