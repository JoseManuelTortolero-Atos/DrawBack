-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE generation_file (
    id            BIGSERIAL    PRIMARY KEY,
    version_id    BIGINT       NOT NULL REFERENCES generation_version(id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL,
    content_type  VARCHAR(120) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    content       BYTEA        NOT NULL,
    CONSTRAINT uq_file UNIQUE (version_id, name)
);
