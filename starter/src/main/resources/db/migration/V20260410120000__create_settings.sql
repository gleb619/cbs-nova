CREATE TABLE settings (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    value       VARCHAR(255) NOT NULL,
    description VARCHAR(255)
);
