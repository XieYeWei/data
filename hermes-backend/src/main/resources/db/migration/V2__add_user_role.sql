-- =============================================
-- V2: Add role column to user table
-- =============================================

-- H2 (dev)
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `role` VARCHAR(20) DEFAULT 'viewer';

-- MySQL (prod) - safe to run multiple times
-- ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `role` VARCHAR(20) DEFAULT 'viewer';

-- Update existing admin users
UPDATE `user` SET `role` = 'admin' WHERE `username` = 'admin';
UPDATE `user` SET `role` = 'viewer' WHERE `role` IS NULL OR `role` = '';
