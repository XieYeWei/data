# 工作流编排引擎 — 开发计划

## 概览
构建基于 DAG 的轻量级工作流引擎，复用现有的 JobTemplate + MrService 体系，支持多步骤编排、依赖管理、定时调度、执行监控。

---

## 1. 数据模型 (4 张新表 + 扩展 1 张)

### 1.1 `workflow_definition` — 工作流定义
```sql
CREATE TABLE IF NOT EXISTS `workflow_definition` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(200) NOT NULL,
    `description` TEXT,
    `cluster_id` VARCHAR(50) NOT NULL,
    `schedule_cron` VARCHAR(100),               -- 定时表达式(空=手动触发)
    `max_retries` INT DEFAULT 0,                 -- 失败重试次数
    `timeout_minutes` INT DEFAULT 60,            -- 超时分钟
    `enabled` BOOLEAN DEFAULT TRUE,
    `created_by` BIGINT,                         -- FK user.id
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 1.2 `workflow_step` — 工作流步骤(DAG 节点)
```sql
CREATE TABLE IF NOT EXISTS `workflow_step` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id` BIGINT NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `step_type` VARCHAR(50) NOT NULL,            -- MAPREDUCE / SHELL / HIVE / SPARK / WAIT / HTTP
    `template_id` BIGINT,                        -- FK job_template.id(仅 MAPREDUCE 类型)
    `command` TEXT,                               -- SHELL 类型: shell 命令
    `script_path` VARCHAR(500),                  -- SHELL 类型: HDFS 脚本路径
    `input_path` VARCHAR(500),                   -- MR/Spark 输入
    `output_path` VARCHAR(500),                  -- MR/Spark 输出
    `args` TEXT,                                  -- 额外参数(JSON)
    `queue` VARCHAR(100) DEFAULT 'default',       -- YARN 队列
    `step_order` INT NOT NULL,                    -- 拓扑排序序号(用于布局)
    `depends_on` TEXT,                            -- JSON 数组: ["step_id1", "step_id2"]
    `retry_count` INT DEFAULT 0,
    `timeout_minutes` INT DEFAULT 30,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ws_workflow ON workflow_step(workflow_id);
```

### 1.3 `workflow_execution` — 工作流执行实例
```sql
CREATE TABLE IF NOT EXISTS `workflow_execution` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id` BIGINT NOT NULL,
    `status` VARCHAR(20) DEFAULT 'PENDING',      -- PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED
    `trigger_type` VARCHAR(20) DEFAULT 'MANUAL', -- MANUAL / SCHEDULED / API
    `start_time` DATETIME,
    `end_time` DATETIME,
    `duration_ms` BIGINT,
    `error_message` TEXT,
    `created_by` BIGINT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_we_workflow ON workflow_execution(workflow_id);
CREATE INDEX IF NOT EXISTS idx_we_status ON workflow_execution(status);
CREATE INDEX IF NOT EXISTS idx_we_time ON workflow_execution(create_time);
```

### 1.4 `workflow_step_execution` — 步骤执行记录
```sql
CREATE TABLE IF NOT EXISTS `workflow_step_execution` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `execution_id` BIGINT NOT NULL,
    `step_id` BIGINT NOT NULL,
    `status` VARCHAR(20) DEFAULT 'PENDING',      -- PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/TIMEOUT
    `application_id` VARCHAR(100),               -- YARN application ID (MR/Spark 类型)
    `log_path` VARCHAR(500),
    `start_time` DATETIME,
    `end_time` DATETIME,
    `duration_ms` BIGINT,
    `error_message` TEXT,
    `attempt` INT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wse_execution ON workflow_step_execution(execution_id);
```

### 1.5 扩展 JobTemplate
- 增加字段 `step_count` INT(可选，表示该模板被多少步骤引用)
- 保持向后兼容

---

## 2. 后端 API (`WorkflowController`)

### 2.1 工作流定义 CRUD
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/workflows` | 列表(分页+搜索) |
| POST | `/api/v1/workflows` | 创建工作流 |
| GET | `/api/v1/workflows/{id}` | 详情(含步骤列表) |
| PUT | `/api/v1/workflows/{id}` | 更新 |
| DELETE | `/api/v1/workflows/{id}` | 删除(级联删除步骤) |
| PATCH | `/api/v1/workflows/{id}/toggle` | 启用/禁用 |

### 2.2 工作流步骤 CRUD
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/workflows/{wid}/steps` | 步骤列表 |
| POST | `/api/v1/workflows/{wid}/steps` | 新增步骤 |
| PUT | `/api/v1/workflows/{wid}/steps/{id}` | 更新步骤 |
| DELETE | `/api/v1/workflows/{wid}/steps/{id}` | 删除步骤 |
| PUT | `/api/v1/workflows/{wid}/steps/reorder` | 批量调整步骤顺序 + 依赖 |

### 2.3 执行管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/workflows/{id}/execute` | 手动触发执行 |
| GET | `/api/v1/workflows/{id}/executions` | 执行历史(分页) |
| GET | `/api/v1/executions/{id}` | 执行详情(含步骤状态) |
| GET | `/api/v1/executions/{id}/steps` | 步骤执行详情 |
| POST | `/api/v1/executions/{id}/cancel` | 取消执行 |
| GET | `/api/v1/executions/{id}/log` | 步骤日志聚合 |

---

## 3. 引擎核心 (`WorkflowEngine`)

### 3.1 执行流程
```
trigger_execution(workflow_id)
  → 创建 WorkflowExecution(status=RUNNING)
  → 解析 DAG: 拓扑排序所有步骤
  → 将入度为 0 的步骤加入"就绪队列"
  → 循环:
    → 从就绪队列取步骤
    → 异步执行步骤(线程池)
    → 步骤完成 → 更新其下游入度 → 入度为 0 的加入就绪队列
    → 所有步骤完成 → 更新 execution 状态
  → 记录总时长
```

### 3.2 步骤执行器(`StepExecutor`)
- `MapReduceStepExecutor`: 复用现有 MrService.submitJobFromTemplate()
- `ShellStepExecutor`: SSH/docker exec 执行 shell 命令
- `WaitStepExecutor`: 等待指定时间
- `HttpStepExecutor`: HTTP 回调

### 3.3 调度器(`WorkflowScheduler`)
- 基于 Spring `@Scheduled` (每分钟轮询)
- 查询 `workflow_definition WHERE enabled=true AND schedule_cron IS NOT NULL`
- 使用 CronExpression 计算下次触发时间
- 到期自动创建执行

### 3.4 线程模型
- 使用 `ExecutorService` (可配置池大小, 默认 5)
- 每个工作流执行在独立线程中
- 步骤级并发: 无依赖的步骤可并行执行

---

## 4. 前端

### 4.1 新标签页: "🔗 工作流"
- 位置：侧边栏，排在"MR 模板"之后
- 子视图：
  - **工作流列表** — 表格(名称、状态、定时、最后执行时间、操作)
  - **工作流设计器** — DAG 可视化编辑器

### 4.2 DAG 设计器
- 使用纯 CSS/SVG 绘制 DAG(不引入第三方图库)
- 节点=步骤卡片(名称、类型图标、状态色)
- 有向边=箭头连线(依赖方向)
- 操作：
  - 新增步骤按钮 → 弹窗选择类型
  - 拖拽排列位置
  - 点击节点编辑
  - 连线: 选中源节点→点击目标节点创建依赖
  - 右键删除边

### 4.3 执行监控
- 执行历史列表(时间、状态、持续时长)
- 详情页：DAG 图实时着色(运行中=蓝色/成功=绿色/失败=红色)
- 展开步骤查看 YARN application ID 链接

### 4.4 设计参考
```
┌──────────────────────────────┐
│ [Step1: 数据采集]             │
│       ↓                      │
│ [Step2: 数据清洗] ← [预检]    │
│       ↓                      │
│ [Step3: 数据分析]             │
│       ↓                      │
│ [Step4: 结果导出]             │
└──────────────────────────────┘
```
简化实现：使用 el-card + flex 布局 + CSS 箭头模拟 DAG，不引入复杂图形库。

---

## 5. 实施顺序

1. **阶段 A** (核心): schema + 4 实体 + Mapper + WorkflowController CRUD + 前端列表
2. **阶段 B** (执行引擎): WorkflowEngine + StepExecutor + 线程池 + DAG 拓扑排序 + 前端执行触发
3. **阶段 C** (增强): 调度器 + DAG 可视化编辑器 + 执行历史监控 + 日志聚合
4. **阶段 D** (扩展): Shell/HTTP 步骤类型 + 重试策略 + 通知(webhook/email)
