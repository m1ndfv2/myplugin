CREATE TABLE IF NOT EXISTS supporter_keys (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    duration_days INT NOT NULL,
    created_by INT NOT NULL DEFAULT 0,
    used_by INT NOT NULL DEFAULT 0,
    created_at INT NOT NULL,
    used_at INT NOT NULL DEFAULT 0
);
