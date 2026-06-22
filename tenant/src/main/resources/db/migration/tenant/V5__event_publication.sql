CREATE TABLE IF NOT EXISTS event_publication (
    id                UUID                     NOT NULL PRIMARY KEY,
    listener_id       VARCHAR(512)             NOT NULL,
    event_type        VARCHAR(512)             NOT NULL,
    serialized_event  TEXT                     NOT NULL,
    publication_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_event_publication_completion
    ON event_publication (completion_date)
    WHERE completion_date IS NULL;
