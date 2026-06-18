-- File Notes table for per-path notes/tags
CREATE TABLE IF NOT EXISTS `file_note` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cluster_id` VARCHAR(50) NOT NULL,
    `path` VARCHAR(1000) NOT NULL,
    `note` TEXT,
    `created_by` VARCHAR(100),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cluster_path (cluster_id, path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
