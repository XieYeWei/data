-- =============================================
-- Hermes Platform - 初始数据（MySQL版）
-- 首次部署时执行一次
-- =============================================

USE `hermes`;

-- 默认管理员用户 (密码: admin, BCrypt加密)
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `role`, `enabled`)
VALUES ('admin', '$2b$12$5Zxki4ZOTXp9DqBuW0JDzeWloCcXnlpyNYimANWgbnjdCIO28tony', 'admin@hermes.local', 'admin', 1);

-- 默认观察者用户 (密码: viewer)
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `role`, `enabled`)
VALUES ('viewer', '$2b$12$5Zxki4ZOTXp9DqBuW0JDzeWloCcXnlpyNYimANWgbnjdCIO28tony', 'viewer@hermes.local', 'viewer', 1);

-- 默认集群
INSERT IGNORE INTO `cluster` (`name`, `namenode`, `resourcemanager`, `version`, `auth_type`, `enabled`)
VALUES ('demo-cluster', 'hdfs://mycluster', 'localhost:8032', '3.3.6', 'simple', 1);
