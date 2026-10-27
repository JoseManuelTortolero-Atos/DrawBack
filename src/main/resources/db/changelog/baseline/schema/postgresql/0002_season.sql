-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE season (
    id               VARCHAR(10)  PRIMARY KEY CHECK (id LIKE '____-__'
        AND id NOT LIKE '____-___%'
        AND ASCII(SUBSTRING(id, 1, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(id, 2, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(id, 3, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(id, 4, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(id, 6, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(id, 7, 1)) BETWEEN 48 AND 57)
);
