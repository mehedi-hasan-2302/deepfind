CREATE TABLE indexed_roots (
    path_key TEXT PRIMARY KEY NOT NULL,
    absolute_path TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at TEXT NOT NULL,
    last_selected_at TEXT NOT NULL,
    last_indexed_at TEXT
);

CREATE UNIQUE INDEX indexed_roots_absolute_path_uq ON indexed_roots (absolute_path);
