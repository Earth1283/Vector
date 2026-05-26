CREATE TABLE hello_sessions (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid      TEXT    NOT NULL,
    username  TEXT    NOT NULL,
    joined_at INTEGER NOT NULL
);
