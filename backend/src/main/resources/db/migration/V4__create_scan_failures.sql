CREATE TABLE scan_failures (
    failure_id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL,
    absolute_path TEXT NOT NULL,
    reason TEXT NOT NULL,
    message TEXT NOT NULL,
    recorded_at TEXT NOT NULL,
    FOREIGN KEY (job_id) REFERENCES scan_jobs (job_id) ON DELETE CASCADE
);

CREATE INDEX scan_failures_job_id_idx ON scan_failures (job_id, failure_id DESC);
