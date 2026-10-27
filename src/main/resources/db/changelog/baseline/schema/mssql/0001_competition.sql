-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE competition (
    id               VARCHAR(40)  PRIMARY KEY,
    code             VARCHAR(10)  NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    storage_dir      VARCHAR(60)  NOT NULL,
    output_suffix    VARCHAR(20)  NOT NULL DEFAULT '',
    sort_order       INT          NOT NULL DEFAULT 0
);
