CREATE TABLE scan_jobs_v5 (
    job_id TEXT PRIMARY KEY NOT NULL,
    root_path_key TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('RUNNING', 'PAUSING', 'PAUSED', 'COMPLETED', 'FAILED', 'INTERRUPTED')),
    current_path TEXT,
    entries_discovered INTEGER NOT NULL DEFAULT 0,
    files_discovered INTEGER NOT NULL DEFAULT 0,
    directories_discovered INTEGER NOT NULL DEFAULT 0,
    symbolic_links_discovered INTEGER NOT NULL DEFAULT 0,
    other_entries_discovered INTEGER NOT NULL DEFAULT 0,
    entries_skipped INTEGER NOT NULL DEFAULT 0,
    failures INTEGER NOT NULL DEFAULT 0,
    entries_indexed INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    FOREIGN KEY (root_path_key) REFERENCES indexed_roots (path_key)
);

INSERT INTO scan_jobs_v5 SELECT * FROM scan_jobs;

CREATE TABLE scan_failures_v5 (
    failure_id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL,
    absolute_path TEXT NOT NULL,
    reason TEXT NOT NULL,
    message TEXT NOT NULL,
    recorded_at TEXT NOT NULL,
    FOREIGN KEY (job_id) REFERENCES scan_jobs_v5 (job_id) ON DELETE CASCADE
);

INSERT INTO scan_failures_v5 SELECT * FROM scan_failures;

DROP TABLE scan_failures;
DROP TABLE scan_jobs;
ALTER TABLE scan_jobs_v5 RENAME TO scan_jobs;
ALTER TABLE scan_failures_v5 RENAME TO scan_failures;

CREATE INDEX scan_jobs_started_at_idx ON scan_jobs (started_at DESC);
CREATE INDEX scan_jobs_state_idx ON scan_jobs (state);
CREATE INDEX scan_failures_job_id_idx ON scan_failures (job_id, failure_id DESC);
