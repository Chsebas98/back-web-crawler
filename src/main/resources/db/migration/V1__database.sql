-- Flyway migration: creates the usage-tracking schema.
-- Flyway runs this once via JDBC at application startup (see application.properties
-- for why JDBC is used here even though the app itself talks to Postgres over R2DBC).
--
-- Only usage/audit data is persisted. Scraped stories are NOT stored: with just
-- 30 entries per request, filtering/sorting in memory is cheaper than a DB round trip,
-- so the database exists purely to track how the API is being used.

CREATE TABLE IF NOT EXISTS crawler_usage (
    id                 BIGSERIAL PRIMARY KEY,
    request_timestamp  TIMESTAMPTZ NOT NULL,
    filter             VARCHAR(50) NOT NULL,
    execution_time_ms  BIGINT,
    stories_scraped    INTEGER,
    stories_returned   INTEGER,
    status             VARCHAR(20) NOT NULL,
    error_message      TEXT
);

-- Supports querying usage history ordered by most recent request.
CREATE INDEX IF NOT EXISTS idx_crawler_usage_request_timestamp
    ON crawler_usage (request_timestamp DESC);
