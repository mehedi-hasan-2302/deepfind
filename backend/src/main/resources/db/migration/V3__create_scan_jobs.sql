CREATE TABLE scan_jobs (
    job_id TEXT PRIMARY KEY NOT NULL,
    root_path_key TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('RUNNING', 'COMPLETED', 'FAILED', 'INTERRUPTED')),
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

CREATE INDEX scan_jobs_started_at_idx ON scan_jobs (started_at DESC);
CREATE INDEX scan_jobs_state_idx ON scan_jobs (state);
