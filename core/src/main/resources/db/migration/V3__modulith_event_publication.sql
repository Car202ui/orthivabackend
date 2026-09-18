-- Spring Modulith event publication registry (transactional outbox for domain events
-- exchanged between modules, e.g. "order submitted" -> notification/payment).
CREATE TABLE event_publication (
    id                     UUID PRIMARY KEY,
    listener_id            VARCHAR(512) NOT NULL,
    event_type             VARCHAR(512) NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMPTZ NOT NULL,
    completion_date        TIMESTAMPTZ,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMPTZ
);
CREATE INDEX idx_event_publication_incomplete ON event_publication(completion_date) WHERE completion_date IS NULL;
