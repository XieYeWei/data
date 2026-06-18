-- ============================================================
-- Hermes 数据中台平台 - MySQL 完整建库建表脚本
-- 数据库: hermes
-- 说明: 除日志内容(由Hadoop/容器管理)外,所有后端数据存储于此
-- 字符集: utf8mb4 (支持中文和emoji)
-- ============================================================

CREATE DATABASE IF NOT EXISTS `hermes`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `hermes`;

-- ============================================================
-- 1. 用户表 - 存储平台登录用户信息
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`              BIGINT       AUTO_INCREMENT  COMMENT '用户ID',
    `username`        VARCHAR(50)  NOT NULL         COMMENT '用户名（唯一）',
    `password`        VARCHAR(255) NOT NULL         COMMENT 'BCrypt加密密码',
    `email`           VARCHAR(100)                  COMMENT '电子邮箱',
    `role`            VARCHAR(20)  DEFAULT 'viewer' COMMENT '角色: admin/operator/viewer',
    `enabled`         TINYINT(1)   DEFAULT 1        COMMENT '是否启用: 1启用 0禁用',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_time` DATETIME                      COMMENT '最后登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表-平台登录用户';

-- ============================================================
-- 2. 集群表 - Hadoop集群连接配置
-- ============================================================
CREATE TABLE IF NOT EXISTS `cluster` (
    `id`              BIGINT       AUTO_INCREMENT  COMMENT '集群ID',
    `name`            VARCHAR(100) NOT NULL         COMMENT '集群显示名称',
    `namenode`        VARCHAR(255) NOT NULL         COMMENT 'HDFS NameNode地址(hdfs://host:port)',
    `resourcemanager` VARCHAR(255) NOT NULL         COMMENT 'YARN RM地址(host:port)',
    `version`         VARCHAR(20)                   COMMENT 'Hadoop版本号',
    `auth_type`       VARCHAR(20)  DEFAULT 'simple' COMMENT '认证方式: simple/kerberos',
    `keytab_path`     VARCHAR(255)                  COMMENT 'Kerberos keytab路径',
    `principal`       VARCHAR(100)                  COMMENT 'Kerberos principal',
    `enabled`         TINYINT(1)   DEFAULT 1        COMMENT '是否启用: 1启用 0禁用',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集群表-Hadoop集群连接配置';

-- ============================================================
-- 3. 操作审计日志表 - 所有用户操作记录
-- ============================================================
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`          BIGINT       AUTO_INCREMENT  COMMENT '日志ID',
    `user_id`     BIGINT                        COMMENT '操作用户ID',
    `username`    VARCHAR(100) DEFAULT ''       COMMENT '操作时用户名（冗余，用户删除后仍可追溯）',
    `cluster_id`  BIGINT                        COMMENT '目标集群ID',
    `module`      VARCHAR(20)  NOT NULL         COMMENT '操作模块: hdfs/yarn/mr/user',
    `action`      VARCHAR(50)  NOT NULL         COMMENT '操作类型: list/upload/delete/create/submit/kill',
    `target`      VARCHAR(500)                  COMMENT '操作目标路径/名称',
    `result`      VARCHAR(20)                   COMMENT '操作结果: success/failed',
    `detail`      TEXT                          COMMENT '详细参数和响应(JSON)',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_cluster` (`user_id`, `cluster_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_module_action` (`module`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表-记录所有用户操作';

-- ============================================================
-- 4. 监控快照表 - HDFS/YARN/队列定时采集指标
-- ============================================================
CREATE TABLE IF NOT EXISTS `metric_snapshot` (
    `id`                   BIGINT       AUTO_INCREMENT  COMMENT '快照ID',
    `cluster_id`           BIGINT                        COMMENT '集群ID',
    `module`               VARCHAR(20)  NOT NULL         COMMENT '指标模块: hdfs/yarn/queue',
    `used_space`           BIGINT                        COMMENT 'HDFS已用空间(字节)',
    `file_count`           BIGINT                        COMMENT 'HDFS文件总数',
    `num_node_managers`    INT                           COMMENT 'YARN NodeManager数量',
    `total_memory_mb`      BIGINT                        COMMENT 'YARN总内存(MB)',
    `running_applications` INT                           COMMENT 'YARN运行中应用数',
    `extra_json`           TEXT                          COMMENT '额外指标(JSON,如队列容量)',
    `create_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    PRIMARY KEY (`id`),
    KEY `idx_module_time` (`module`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控快照表-HDFS/YARN/队列定时采集指标';

-- ============================================================
-- 5. 作业模板表 - MapReduce/Spark作业模板
-- ============================================================
CREATE TABLE IF NOT EXISTS `job_template` (
    `id`             BIGINT       AUTO_INCREMENT  COMMENT '模板ID',
    `name`           VARCHAR(200) NOT NULL         COMMENT '模板名称',
    `description`    TEXT                          COMMENT '模板描述',
    `jar_hdfs_path`  VARCHAR(500) NOT NULL         COMMENT 'Jar包HDFS路径',
    `main_class`     VARCHAR(200) NOT NULL         COMMENT '主类名',
    `default_args`   TEXT                          COMMENT '默认参数',
    `input_path`     VARCHAR(500)                  COMMENT '默认输入路径',
    `output_path`    VARCHAR(500)                  COMMENT '默认输出路径',
    `queue`          VARCHAR(100) DEFAULT 'default' COMMENT 'YARN队列名',
    `type`           VARCHAR(50)                   COMMENT '模板分类: WiFi/Spark/自定义',
    `use_count`      INT          DEFAULT 0        COMMENT '使用次数统计',
    `last_used_time` DATETIME                      COMMENT '最后使用时间',
    `created_by`     BIGINT                        COMMENT '创建者用户ID',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业模板表-MR/Spark作业模板';

-- ============================================================
-- 6. 队列告警规则表 - YARN队列告警配置
-- ============================================================
CREATE TABLE IF NOT EXISTS `queue_alert_rule` (
    `id`           BIGINT       AUTO_INCREMENT  COMMENT '规则ID',
    `queue_name`   VARCHAR(100) NOT NULL         COMMENT 'YARN队列名',
    `metric`       VARCHAR(50)  NOT NULL         COMMENT '监控指标: usedCapacity/numApplications/usedMemoryMB/usedVCores',
    `threshold`    DOUBLE       NOT NULL         COMMENT '告警阈值',
    `operator`     VARCHAR(10)  DEFAULT '>'      COMMENT '比较运算符: >/>=/</<=/=',
    `enabled`      TINYINT(1)   DEFAULT 1        COMMENT '是否启用: 1启用 0禁用',
    `notify_email` VARCHAR(200)                  COMMENT '通知邮箱',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_queue` (`queue_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队列告警规则表-YARN队列告警配置';

-- ============================================================
-- 7. 文件笔记表 - HDFS文件/目录注释
-- ============================================================
CREATE TABLE IF NOT EXISTS `file_note` (
    `id`          BIGINT       AUTO_INCREMENT  COMMENT '笔记ID',
    `cluster_id`  VARCHAR(50)  NOT NULL         COMMENT '集群ID',
    `path`        VARCHAR(1000) NOT NULL        COMMENT 'HDFS文件路径',
    `note`        TEXT                          COMMENT '笔记内容',
    `created_by`  VARCHAR(100)                  COMMENT '创建者用户名',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cluster_path` (`cluster_id`, `path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件笔记表-HDFS文件/目录注释';

-- ============================================================
-- 8. 数据目录_表注册表 - 数据目录中注册的表/数据集
-- ============================================================
CREATE TABLE IF NOT EXISTS `catalog_table` (
    `id`                BIGINT       AUTO_INCREMENT  COMMENT '表ID',
    `cluster_id`        VARCHAR(50)  NOT NULL         COMMENT '集群ID',
    `name`              VARCHAR(200) NOT NULL         COMMENT '表名',
    `hdfs_path`         VARCHAR(1000) NOT NULL        COMMENT 'HDFS路径',
    `schema_name`       VARCHAR(200) DEFAULT 'default' COMMENT '逻辑Schema名(数据库名)',
    `format`            VARCHAR(50)  DEFAULT 'TEXT'   COMMENT '数据格式: TEXT/CSV/Parquet/ORC/Avro',
    `owner`             VARCHAR(100)                  COMMENT '负责人',
    `description`       TEXT                          COMMENT '表描述',
    `row_count`         BIGINT       DEFAULT 0        COMMENT '估算行数',
    `file_count`        INT          DEFAULT 0        COMMENT '文件数量',
    `total_size_bytes`  BIGINT       DEFAULT 0        COMMENT '总大小(字节)',
    `partition_columns` VARCHAR(500)                  COMMENT '分区列(逗号分隔)',
    `created_by`        VARCHAR(100)                  COMMENT '注册者用户名',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cluster_schema` (`cluster_id`, `schema_name`),
    KEY `idx_cluster_name` (`cluster_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据目录-表注册表-注册的表/数据集';

-- ============================================================
-- 9. 数据目录_列定义表 - 表的列/字段定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `catalog_column` (
    `id`               BIGINT       AUTO_INCREMENT  COMMENT '列ID',
    `table_id`         BIGINT       NOT NULL         COMMENT '所属表ID',
    `name`             VARCHAR(200) NOT NULL         COMMENT '列名',
    `type`             VARCHAR(100) NOT NULL         COMMENT '数据类型: STRING/INT/BIGINT/DOUBLE/DECIMAL/TIMESTAMP/DATE/BOOLEAN',
    `comment`          VARCHAR(500)                  COMMENT '列注释',
    `nullable`         TINYINT(1)   DEFAULT 1        COMMENT '是否可空: 1可空 0不可空',
    `is_partition`     TINYINT(1)   DEFAULT 0        COMMENT '是否分区列: 1是 0否',
    `ordinal_position` INT          NOT NULL         COMMENT '列序号(从1开始)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据目录-列定义表-表的字段定义';

-- ============================================================
-- 10. 数据目录_标签表 - 可复用的标签(彩色)
-- ============================================================
CREATE TABLE IF NOT EXISTS `catalog_tag` (
    `id`          BIGINT       AUTO_INCREMENT  COMMENT '标签ID',
    `name`        VARCHAR(100) NOT NULL         COMMENT '标签名(唯一)',
    `color`       VARCHAR(20)  DEFAULT '#3b82f6' COMMENT '标签颜色(十六进制)',
    `description` VARCHAR(500)                  COMMENT '标签描述',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据目录-标签表-可复用的标签';

-- ============================================================
-- 11. 数据目录_表标签关联表 - 表与标签的多对多关联
-- ============================================================
CREATE TABLE IF NOT EXISTS `catalog_table_tag` (
    `table_id` BIGINT NOT NULL COMMENT '表ID',
    `tag_id`   BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`table_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据目录-表标签关联-表与标签的多对多';

-- ============================================================
-- 12. 工作流定义表 - 工作流编排定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `workflow_definition` (
    `id`              BIGINT       AUTO_INCREMENT  COMMENT '工作流ID',
    `name`            VARCHAR(200) NOT NULL         COMMENT '工作流名称',
    `description`     TEXT                          COMMENT '工作流描述',
    `cluster_id`      VARCHAR(50)  NOT NULL         COMMENT '目标集群ID',
    `schedule_cron`   VARCHAR(100)                  COMMENT '定时cron表达式(空=手动触发)',
    `max_retries`     INT          DEFAULT 0        COMMENT '失败最大重试次数',
    `timeout_minutes` INT          DEFAULT 60       COMMENT '超时时间(分钟)',
    `enabled`         TINYINT(1)   DEFAULT 1        COMMENT '是否启用: 1启用 0禁用',
    `created_by`      BIGINT                        COMMENT '创建者用户ID',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `webhook_url`     VARCHAR(500)                  COMMENT '执行完成回调Webhook URL',
    PRIMARY KEY (`id`),
    KEY `idx_enabled_cron` (`enabled`, `schedule_cron`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表-工作流编排定义';

-- ============================================================
-- 13. 工作流步骤表(DAG节点) - 工作流中的每个执行步骤
-- ============================================================
CREATE TABLE IF NOT EXISTS `workflow_step` (
    `id`               BIGINT       AUTO_INCREMENT  COMMENT '步骤ID',
    `workflow_id`      BIGINT       NOT NULL         COMMENT '所属工作流ID',
    `name`             VARCHAR(200) NOT NULL         COMMENT '步骤名称',
    `step_type`        VARCHAR(50)  NOT NULL         COMMENT '步骤类型: MAPREDUCE/SHELL/WAIT/HTTP',
    `template_id`      BIGINT                        COMMENT 'MR模板ID(仅MAPREDUCE类型)',
    `command`          TEXT                          COMMENT 'SHELL命令或HTTP URL',
    `script_path`      VARCHAR(500)                  COMMENT '脚本HDFS路径',
    `input_path`       VARCHAR(500)                  COMMENT '输入路径(MR类型)',
    `output_path`      VARCHAR(500)                  COMMENT '输出路径(MR类型)',
    `args`             TEXT                          COMMENT '额外参数(JSON)',
    `queue`            VARCHAR(100) DEFAULT 'default' COMMENT 'YARN队列名',
    `step_order`       INT          NOT NULL         COMMENT '步骤排序序号',
    `depends_on`       TEXT                          COMMENT '依赖步骤(JSON数组:[step_name1,step_name2])',
    `retry_count`      INT          DEFAULT 0        COMMENT '本步骤重试次数',
    `timeout_minutes`  INT          DEFAULT 30       COMMENT '本步骤超时(分钟)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流步骤表-DAG节点-每个执行步骤定义';

-- ============================================================
-- 14. 工作流执行实例表 - 每次执行的记录
-- ============================================================
CREATE TABLE IF NOT EXISTS `workflow_execution` (
    `id`              BIGINT       AUTO_INCREMENT  COMMENT '执行ID',
    `workflow_id`     BIGINT       NOT NULL         COMMENT '工作流ID',
    `status`          VARCHAR(20)  DEFAULT 'PENDING' COMMENT '执行状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
    `trigger_type`    VARCHAR(20)  DEFAULT 'MANUAL' COMMENT '触发方式: MANUAL/SCHEDULED/API',
    `start_time`      DATETIME                      COMMENT '开始时间',
    `end_time`        DATETIME                      COMMENT '结束时间',
    `duration_ms`     BIGINT                        COMMENT '执行耗时(毫秒)',
    `error_message`   TEXT                          COMMENT '错误信息',
    `created_by`      BIGINT                        COMMENT '触发者用户ID',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_workflow_id` (`workflow_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行实例表-每次执行的记录';

-- ============================================================
-- 15. 工作流步骤执行记录表 - 每一步的执行详情
-- ============================================================
CREATE TABLE IF NOT EXISTS `workflow_step_execution` (
    `id`             BIGINT       AUTO_INCREMENT  COMMENT '步骤执行ID',
    `execution_id`   BIGINT       NOT NULL         COMMENT '所属执行实例ID',
    `step_id`        BIGINT       NOT NULL         COMMENT '步骤定义ID',
    `status`         VARCHAR(20)  DEFAULT 'PENDING' COMMENT '执行状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/TIMEOUT',
    `application_id` VARCHAR(100)                  COMMENT 'YARN Application ID(MR/Spark类型)',
    `log_path`       VARCHAR(500)                  COMMENT '日志路径',
    `start_time`     DATETIME                      COMMENT '开始时间',
    `end_time`       DATETIME                      COMMENT '结束时间',
    `duration_ms`    BIGINT                        COMMENT '执行耗时(毫秒)',
    `error_message`  TEXT                          COMMENT '错误信息',
    `attempt`        INT          DEFAULT 1        COMMENT '第几次尝试',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_execution_id` (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流步骤执行记录表-每一步的执行详情';

-- ============================================================
-- 16. 健康评分历史表 - 每日集群健康评分(0-100)
-- ============================================================
CREATE TABLE IF NOT EXISTS `health_score_history` (
    `id`          BIGINT       AUTO_INCREMENT  COMMENT '记录ID',
    `score`       INT          NOT NULL         COMMENT '健康评分(0-100)',
    `score_date`  DATE         NOT NULL         COMMENT '评分日期(唯一)',
    `hdfs_used`   BIGINT                        COMMENT 'HDFS已用空间(字节)',
    `hdfs_total`  BIGINT                        COMMENT 'HDFS总容量(字节)',
    `nm_count`    INT                           COMMENT 'NodeManager数量',
    `app_count`   INT                           COMMENT '运行中应用数',
    `detail_json` TEXT                          COMMENT '详细指标(JSON)',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_score_date` (`score_date`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康评分历史表-每日集群健康评分(0-100)';

-- ============================================================
-- 16. 操作类型标签表 - 操作审计模块中文标签映射
-- ============================================================
CREATE TABLE IF NOT EXISTS `action_label` (
    `action`   VARCHAR(50)  NOT NULL  COMMENT '操作类型标识',
    `label`    VARCHAR(100) NOT NULL  COMMENT '中文显示名称',
    `module`   VARCHAR(20)  DEFAULT '' COMMENT '所属模块(可选过滤)',
    PRIMARY KEY (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作类型中文标签映射表';

-- 初始标签数据
INSERT IGNORE INTO `action_label` VALUES
('list', '列表', ''),
('upload', '上传', ''),
('download', '下载', ''),
('mkdir', '创建目录', ''),
('delete', '删除', ''),
('chmod', '修改权限', ''),
('getAcl', '查看ACL', ''),
('setAcl', '设置ACL', ''),
('removeAcl', '移除ACL', ''),
('rename', '重命名', ''),
('move', '移动', ''),
('search', '搜索', ''),
('trash', '移入回收站', ''),
('restore', '从回收站恢复', ''),
('emptyTrash', '清空回收站', ''),
('cleanExpiredTrash', '清理过期回收站', ''),
('getStatus', '查看状态', ''),
('getSummary', '查看概要', ''),
('submit', '提交作业', ''),
('kill', '终止作业', ''),
('create', '创建', ''),
('start', '启动', ''),
('stop', '停止', ''),
('update', '更新', ''),
('register', '注册', ''),
('discover', '自动发现', ''),
('edit', '编辑', ''),
('remove', '移除', ''),
('addColumn', '添加列', ''),
('editColumn', '编辑列', ''),
('removeColumn', '移除列', ''),
('addTag', '添加标签', ''),
('removeTag', '移除标签', ''),
('manageTags', '管理标签', ''),
('approve', '审批', ''),
('moveApp', '移动应用', ''),
('cancelSubmitted', '取消提交', ''),
('create-template', '创建模板', ''),
('update-template', '编辑模板', ''),
('delete-template', '删除模板', ''),
('export-csv', '导出CSV', ''),
('create-alert', '创建告警规则', ''),
('update-alert', '更新告警规则', ''),
('delete-alert', '删除告警规则', ''),
('adjust-weight', '调整权重', ''),
('download-log', '下载日志', ''),
('reset-password', '重置密码', ''),
('switch', '切换', '');
