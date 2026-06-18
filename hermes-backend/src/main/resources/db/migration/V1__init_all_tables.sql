-- =============================================
-- Hermes Platform - Initial Schema (MySQL 8)
-- Version: V1
-- Date: 2026-06-15
-- =============================================

-- User Table
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100),
    `role` VARCHAR(20) DEFAULT 'viewer',
    `enabled` BOOLEAN DEFAULT TRUE,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_login_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cluster Table
CREATE TABLE IF NOT EXISTS `cluster` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `namenode` VARCHAR(255) NOT NULL,
    `resourcemanager` VARCHAR(255) NOT NULL,
    `version` VARCHAR(20),
    `auth_type` VARCHAR(20) DEFAULT 'simple',
    `keytab_path` VARCHAR(255),
    `principal` VARCHAR(100),
    `enabled` BOOLEAN DEFAULT TRUE,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Operation Log Table (Audit)
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT,
    `cluster_id` BIGINT,
    `module` VARCHAR(20) NOT NULL,           -- hdfs / yarn / mr
    `action` VARCHAR(50) NOT NULL,
    `target` VARCHAR(500),
    `result` VARCHAR(20),
    `detail` TEXT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_cluster (user_id, cluster_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Metric Snapshot Table (for history trends)
CREATE TABLE IF NOT EXISTS `metric_snapshot` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cluster_id` BIGINT,
    `module` VARCHAR(20) NOT NULL,           -- hdfs / yarn / queue
    `used_space` BIGINT,
    `file_count` BIGINT,
    `num_node_managers` INT,
    `total_memory_mb` BIGINT,
    `running_applications` INT,
    `extra_json` JSON,                       -- 用于存储队列级详细数据
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_module_time (module, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Job Template Table (MapReduce)
CREATE TABLE IF NOT EXISTS `job_template` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(200) NOT NULL,
    `description` TEXT,
    `jar_hdfs_path` VARCHAR(500) NOT NULL,
    `main_class` VARCHAR(200) NOT NULL,
    `default_args` TEXT,
    `input_path` VARCHAR(500),
    `output_path` VARCHAR(500),
    `queue` VARCHAR(100) DEFAULT 'default',
    `created_by` BIGINT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Queue Alert Rule Table
CREATE TABLE IF NOT EXISTS `queue_alert_rule` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `queue_name` VARCHAR(100) NOT NULL,
    `metric` VARCHAR(50) NOT NULL,           -- usedCapacity / numApplications
    `threshold` DOUBLE NOT NULL,
    `operator` VARCHAR(10) DEFAULT '>',       -- >, >=, <, <=
    `enabled` BOOLEAN DEFAULT TRUE,
    `notify_email` VARCHAR(200),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 初始化数据（可选）
-- =============================================
-- INSERT INTO `user` (username, password, enabled) VALUES ('admin', '$2a$10$...', TRUE);