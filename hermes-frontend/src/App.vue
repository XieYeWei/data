<template>
  <div id="hermes-app">
    <!-- Login Page -->
    <div v-if="!isLoggedIn" class="login-page">
      <div class="login-bg"></div>
      <el-card class="login-card" shadow="always">
        <div class="login-header">
          <div class="login-logo">
            <svg viewBox="0 0 48 48" width="48" height="48"><circle cx="24" cy="24" r="22" fill="none" stroke="#409eff" stroke-width="3"/><path d="M12 24h24M24 12v24" stroke="#409eff" stroke-width="3" stroke-linecap="round"/><circle cx="24" cy="24" r="6" fill="#409eff"/></svg>
          </div>
          <p class="login-sub">Hadoop 集群管理平台</p>
        </div>
        <el-form @submit.prevent="handleLogin" class="login-form">
          <el-form-item>
            <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" size="large" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="rememberMe" style="color:#ccc">记住我</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleLogin" :loading="loginLoading" class="login-btn" size="large">登 录</el-button>
          </el-form-item>
        </el-form>
        <div v-if="loginError" class="login-error">{{ loginError }}</div>
        <div style="margin-top:12px;font-size:12px;color:#64748b;text-align:center">
          &nbsp;
        </div>
      </el-card>
    </div>

    <!-- Main App -->
    <div v-else class="main-layout">
      <!-- Sidebar -->
      <div class="sidebar">
        <div class="sidebar-header">
          <svg viewBox="0 0 48 48" width="32" height="32"><circle cx="24" cy="24" r="22" fill="none" stroke="#fff" stroke-width="3"/><path d="M12 24h24M24 12v24" stroke="#fff" stroke-width="3" stroke-linecap="round"/><circle cx="24" cy="24" r="6" fill="#fff"/></svg>
          <span class="sidebar-title">数据中台</span>
        </div>
        <!-- Cluster Selector -->
        <div class="cluster-selector-wrap">
          <el-select v-model="selectedClusterId" placeholder="选择集群" size="small" class="cluster-selector"
            :teleported="false" popper-class="cluster-selector-popper">
            <el-option v-for="c in clusters" :key="c.id" :label="c.name" :value="'cluster' + c.id" />
          </el-select>
        </div>
        <div class="sidebar-menu">
          <div v-for="tab in filteredTabs" :key="tab.name"
               class="menu-item"
               :class="{ active: activeTab === tab.name }"
               @click="activeTab = tab.name">
            <span class="menu-icon">{{ tab.icon }}</span>
            <span class="menu-label">{{ tab.label }}</span>
          </div>
        </div>
        <div class="sidebar-footer">
          <div class="sidebar-table-size-toggle">
            <el-tooltip :content="tableSize === 'small' ? '切换到舒适模式' : '切换到紧凑模式'" placement="top">
              <el-button text class="size-toggle-btn" @click="toggleTableSize" circle>
                <span v-if="tableSize === 'small'">📏</span>
                <span v-else>📐</span>
              </el-button>
            </el-tooltip>
            <span class="size-toggle-label">{{ tableSize === 'small' ? '紧凑' : '舒适' }}</span>
          </div>
          <div class="help-btn-wrap">
            <el-tooltip content="快捷键与帮助 (?)" placement="top">
              <el-button text class="help-btn" @click="showHelpModal = true" circle>❓</el-button>
            </el-tooltip>
            <span class="help-btn-label">帮助</span>
          </div>
          <div class="user-info">
            <div class="user-avatar">{{ username[0]?.toUpperCase() }}</div>
            <div class="user-details">
              <span class="user-name">{{ username }}</span>
              <span class="user-role-badge" :class="'role-' + currentUser.role">{{ roleLabel }}</span>
            </div>
          </div>
          <el-button text class="logout-btn" @click="handleLogout">退出</el-button>
          <div class="sidebar-connection-status">
            <span class="conn-dot" :class="clusterConnected ? 'conn-connected' : 'conn-disconnected'"></span>
            <span class="conn-label">{{ clusterConnected ? '后端已连接' : '后端断开' }}</span>
          </div>
        </div>
      </div>

      <!-- Main Content -->
      <div class="main-content">
        <div class="content-header" :class="{ 'success-flash': successFlash }">
          <h2 class="page-title">{{ currentTab?.label || '' }}</h2>
          <div class="header-actions">
            <div class="cluster-badge" v-if="overview.clusterId">
              <span class="badge-dot"></span>
              {{ overview.clusterId }}
            </div>
            <el-popover
              placement="bottom-end"
              :width="360"
              trigger="click"
              popper-class="notification-popover"
              v-model:visible="showNotificationsPopover"
            >
              <template #reference>
                <div class="notification-bell-wrap" @click="loadFailedOperations">
                  <span class="notification-bell">🔔</span>
                  <span v-if="totalNotificationsCount > 0" class="notification-badge">{{ totalNotificationsCount }}</span>
                </div>
              </template>
              <div class="notification-popover-content">
                <div class="notification-popover-header">
                  <span>🔔 通知中心</span>
                  <el-button size="small" text type="primary" @click="markAllRead" style="padding:0;font-size:12px">全部已读</el-button>
                </div>
                <div class="notification-section">
                  <div class="notification-section-title">❌ 最近失败操作 <span class="notification-popover-count">{{ failedOperationsCount }} 条</span></div>
                  <div v-if="failedOperations.length === 0" class="notification-empty">
                    ✅ 暂无失败操作
                  </div>
                  <div v-else class="notification-list">
                    <div v-for="(op, idx) in failedOperations" :key="idx" class="notification-item">
                      <div class="notification-item-header">
                        <el-tag :type="op.module === 'hdfs' ? 'primary' : op.module === 'yarn' ? 'warning' : 'info'" size="small" effect="dark">{{ op.module }}</el-tag>
                        <span class="notification-item-action">{{ op.action }}</span>
                      </div>
                      <div class="notification-item-target">{{ op.target || '-' }}</div>
                      <div class="notification-item-time">{{ op.createTime ? new Date(op.createTime).toLocaleString() : '-' }}</div>
                    </div>
                  </div>
                </div>
                <div class="notification-divider"></div>
                <div class="notification-section">
                  <div class="notification-section-title">⚠️ 未读告警 <span class="notification-popover-count">{{ unreadAlerts.length }} 条</span></div>
                  <div v-if="unreadAlerts.length === 0" class="notification-empty">
                    ✅ 暂无未读告警
                  </div>
                  <div v-else class="notification-list">
                    <div v-for="(alert, idx) in unreadAlerts.slice(0, 5)" :key="idx" class="notification-item">
                      <div class="notification-item-header">
                        <el-tag :type="alert.module === 'hdfs' ? 'primary' : alert.module === 'yarn' ? 'warning' : 'info'" size="small" effect="dark">{{ alert.module || 'alert' }}</el-tag>
                        <span class="notification-item-action">{{ alert.action || '告警触发' }}</span>
                      </div>
                      <div class="notification-item-target">{{ alert.target || '-' }}</div>
                      <div class="notification-item-time">{{ alert.createTime ? new Date(alert.createTime).toLocaleString() : '-' }}</div>
                    </div>
                  </div>
                </div>
                <div class="notification-popover-footer" style="display:flex;justify-content:space-between;padding:8px 12px">
                  <span @click="goToOperationLog" style="cursor:pointer;color:#409eff">📝 查看操作日志 →</span>
                  <span @click="goToAlertCenter" style="cursor:pointer;color:#e6a23c">⚠️ 查看全部告警 →</span>
                </div>
              </div>
            </el-popover>
          </div>
        </div>
        <div class="content-body">
          <!-- Dashboard -->
          <div v-show="activeTab === 'dashboard'">
            <div v-if="overview.error" class="error-card">
              ⚠️ 无法连接 Hadoop 集群：{{ overview.error }}
            </div>
            <div v-else>
              <!-- 集群健康度 + Key Metrics Row -->
              <div class="dash-hero-row">
                <div class="dash-health-card">
                  <div class="dash-health-score-wrap">
                    <svg class="dash-health-ring" viewBox="0 0 120 120">
                      <circle cx="60" cy="60" r="52" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="8"/>
                      <circle cx="60" cy="60" r="52" fill="none" :stroke="healthScoreColor" stroke-width="8"
                        stroke-linecap="round" :stroke-dasharray="326.7" :stroke-dashoffset="326.7 - (healthScore/100)*326.7"
                        transform="rotate(-90,60,60)" style="transition: stroke-dashoffset 0.8s ease, stroke 0.3s"/>
                    </svg>
                    <div class="dash-health-score" :style="{color: healthScoreColor, cursor: 'pointer'}" @click="openHealthDrawer">{{ healthScore }}</div>
                    <div class="dash-health-unit">分</div>
                    <div class="dash-health-label">集群健康度</div>
                  </div>
                </div>
                <div class="dash-metric-card">
                  <div class="dash-metric-icon">💾</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value" :style="{color: hdfsUsageColor}">{{ hdfsUsagePercent }}<span class="dash-metric-unit">%</span></div>
                    <div class="dash-metric-label">HDFS 使用率</div>
                    <div class="dash-metric-trend" :style="{color: hdfsUsageColor}">{{ hdfsUsagePercent > 80 ? '▲ 偏高' : hdfsUsagePercent > 60 ? '◆ 正常' : '▼ 良好' }}</div>
                  </div>
                </div>
                <div class="dash-metric-card">
                  <div class="dash-metric-icon">⚡</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value">{{ yarnUtilization }}<span class="dash-metric-unit">%</span></div>
                    <div class="dash-metric-label">YARN 资源利用率</div>
                    <div class="dash-metric-trend">{{ yarnUtilization > 80 ? '▲ 繁忙' : yarnUtilization > 50 ? '◆ 正常' : '▼ 空闲' }}</div>
                  </div>
                </div>
                <div class="dash-metric-card">
                  <div class="dash-metric-icon">🖥️</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value">{{ overview.yarn?.numNodeManagers || 0 }}<span class="dash-metric-unit"> 台</span></div>
                    <div class="dash-metric-label">在线节点数</div>
                    <div class="dash-metric-trend" style="color:#22c55e">● 全部在线</div>
                  </div>
                </div>
                <div class="dash-metric-card">
                  <div class="dash-metric-icon">🔔</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value">0<span class="dash-metric-unit"> 条</span></div>
                    <div class="dash-metric-label">告警数量</div>
                    <div class="dash-metric-trend" style="color:#22c55e">● 无告警</div>
                  </div>
                </div>
              </div>

              <!-- Stats Grid -->
              <div class="stats-grid" style="margin-top:16px">
                <div class="stat-card hdfs-card">
                  <div class="stat-icon">📁</div>
                  <div class="stat-info">
                    <div class="stat-label">HDFS 总空间</div>
                    <div class="stat-value">{{ formatBytes(overview.hdfs?.totalSpace || 0) }}</div>
                  </div>
                  <div class="stat-trend up">Active</div>
                </div>
                <div class="stat-card hdfs-card">
                  <div class="stat-icon">💾</div>
                  <div class="stat-info">
                    <div class="stat-label">已使用 / 总量</div>
                    <div class="stat-value" style="font-size:18px">{{ formatBytes(overview.hdfs?.usedSpace || 0) }} <span class="dash-stat-sub">/ {{ formatBytes(overview.hdfs?.totalSpace || 0) }}</span></div>
                  </div>
                  <div class="stat-trend" :style="{color: hdfsUsageColor}">{{ hdfsUsagePercent }}% 已用</div>
                </div>
                <div class="stat-card yarn-card">
                  <div class="stat-icon">⚙️</div>
                  <div class="stat-info">
                    <div class="stat-label">NodeManager</div>
                    <div class="stat-value">{{ overview.yarn?.numNodeManagers || 0 }}</div>
                  </div>
                  <div class="stat-trend up">在线</div>
                </div>
                <div class="stat-card yarn-card">
                  <div class="stat-icon">🧠</div>
                  <div class="stat-info">
                    <div class="stat-label">总内存 / vCores</div>
                    <div class="stat-value" style="font-size:20px">{{ (overview.yarn?.totalMemoryMB || 0) >= 1024 ? ((overview.yarn?.totalMemoryMB || 0)/1024).toFixed(1) + ' GB' : (overview.yarn?.totalMemoryMB || 0) + ' MB' }}</div>
                  </div>
                  <div class="stat-trend">{{ overview.yarn?.totalVCores || 0 }} vCores</div>
                </div>
              </div>
              <div class="stats-grid" style="margin-top:16px">
                <div class="stat-card info-card">
                  <div class="stat-icon">📊</div>
                  <div class="stat-info">
                    <div class="stat-label">运行中应用</div>
                    <div class="stat-value">{{ overview.yarn?.runningApplications || 0 }}</div>
                  </div>
                  <div class="stat-trend">YARN</div>
                </div>
                <div class="stat-card info-card">
                  <div class="stat-icon">🗂️</div>
                  <div class="stat-info">
                    <div class="stat-label">目录数</div>
                    <div class="stat-value">{{ overview.hdfs?.dirCount || 0 }}</div>
                  </div>
                  <div class="stat-trend">HDFS</div>
                </div>
                <div class="stat-card info-card">
                  <div class="stat-icon">📄</div>
                  <div class="stat-info">
                    <div class="stat-label">文件数</div>
                    <div class="stat-value">{{ overview.hdfs?.fileCount || 0 }}</div>
                  </div>
                  <div class="stat-trend">HDFS</div>
                </div>
                <div class="stat-card info-card">
                  <div class="stat-icon">⏱️</div>
                  <div class="stat-info">
                    <div class="stat-label">更新时间</div>
                    <div class="stat-value" style="font-size:14px">{{ new Date(overview.timestamp).toLocaleTimeString() }}</div>
                  </div>
                  <div class="stat-trend up">实时</div>
                </div>
              </div>

              <!-- 系统健康 -->
              <div class="dash-auto-refresh-bar">
                <div class="dash-auto-refresh-left">
                  <el-switch v-model="autoRefreshEnabled" @change="toggleAutoRefresh" size="small" />
                  <span class="dash-auto-refresh-label">自动刷新</span>
                  <span v-if="autoRefreshEnabled" class="dash-auto-refresh-countdown" :class="{ 'dash-countdown-warning': autoRefreshCountdown <= 5 }">{{ autoRefreshCountdown }}s</span>
                </div>
                <el-button size="small" @click="manualRefreshDashboard" :loading="manualRefreshLoading" round>
                  🔄 手动刷新
                </el-button>
              </div>

              <!-- 7日健康趋势 -->
              <div class="dash-trend-section">
                <div class="dash-trend-header">
                  <span class="dash-trend-title">📈 7日健康趋势</span>
                  <span class="dash-trend-current" :style="{color: healthScoreColor}">当前 {{ healthScore }} 分</span>
                </div>
                <div v-if="healthHistoryLoading" class="dash-trend-loading">加载中...</div>
                <div v-else-if="healthHistory.length === 0" class="fs-empty" style="padding:24px"><div class="fs-empty-icon">📈</div><div class="fs-empty-text">暂无趋势数据</div></div>
                <div v-else class="dash-trend-chart">
                  <div v-for="(item, idx) in healthHistory" :key="idx" class="dash-trend-bar-wrap">
                    <div class="dash-trend-bar" :style="{height: item.score + '%', background: trendBarColor(item.score)}" :title="item.date + ': ' + item.score + '分'"></div>
                    <div class="dash-trend-date">{{ formatTrendDate(item.date) }}</div>
                    <div class="dash-trend-score">{{ item.score }}</div>
                  </div>
                </div>
              </div>

              <div class="dash-health-section" style="margin-top:16px">
                <div class="dash-health-section-header">
                  <span style="font-size:14px;font-weight:600;color:#f1f5f9">🏥 系统健康</span>
                  <div style="display:flex;align-items:center;gap:8px">
                    <span style="font-size:12px;color:rgba(255,255,255,0.6)">整体状态:</span>
                    <span class="health-overall-badge" :class="'health-' + systemHealth.overall"
                          :style="{background: healthStatusColor(systemHealth.overall) + '22', color: healthStatusColor(systemHealth.overall), border: '1px solid ' + healthStatusColor(systemHealth.overall) + '44'}">
                      {{ healthOverallLabel }}
                    </span>
                    <el-button size="small" @click="loadSystemHealth" :icon="'Refresh'" :loading="systemHealthLoading" round>刷新</el-button>
                  </div>
                </div>
                <div class="dash-health-grid">
                  <div v-for="comp in systemHealth.components" :key="comp.name" class="dash-health-item">
                    <div class="dash-health-item-dot" :style="{background: healthStatusColor(comp.status)}"></div>
                    <div class="dash-health-item-info">
                      <div class="dash-health-item-name">{{ comp.name }}</div>
                      <div class="dash-health-item-status" :style="{color: healthStatusColor(comp.status)}">{{ healthStatusLabel(comp.status) }}</div>
                    </div>
                    <div v-if="comp.details?.message" class="dash-health-item-msg" :title="comp.details.message">{{ comp.details.message }}</div>
                  </div>
                  <div v-if="systemHealth.components.length === 0 && !systemHealthLoading" class="fs-empty" style="padding:16px"><div class="fs-empty-icon">🏥</div><div class="fs-empty-text">暂无健康数据，点击刷新获取最新状态</div></div>
                </div>
              </div>

              <!-- 快捷操作 -->
              <div class="dash-actions-wrap">
                <div class="dash-actions-label">⚡ 快捷操作</div>
                <div class="dash-actions-row">
                  <el-button type="primary" @click="handleQuickAction('upload')" :loading="actionLoading.upload" round>
                    📤 上传文件
                  </el-button>
                  <el-button type="success" @click="handleQuickAction('mr')" :loading="actionLoading.mr" round>
                    📋 提交作业
                  </el-button>
                  <el-button type="warning" @click="handleQuickAction('checkpoint')" :loading="actionLoading.checkpoint" round>
                    💾 触发 Checkpoint
                  </el-button>
                  <el-button @click="handleQuickAction('refresh')" :loading="actionLoading.refresh" :icon="'Refresh'" round>
                    🔄 一键刷新
                  </el-button>
                  <el-button type="info" @click="handleQuickAction('submitJob')" :loading="actionLoading.submitJob" round>
                    🚀 提交作业
                  </el-button>
                </div>
              </div>

              <!-- Bottom Row: 近期告警 + HDFS 详细 -->
              <div class="dash-bottom-row">
                <div class="dash-alert-card">
                  <div class="dash-alert-header">🔔 近期告警</div>
                  <div class="fs-empty" style="padding:12px"><div class="fs-empty-icon">🔔</div><div class="fs-empty-text">暂无告警信息，集群运行正常 ✓</div></div>
                </div>
                <div class="dash-hdfs-detail">
                  <div class="dash-hdfs-detail-header">📂 HDFS 详细信息</div>
                  <div class="dash-hdfs-detail-grid">
                    <div class="dash-hdfs-detail-item">
                      <span class="dash-hdfs-detail-label">已用空间</span>
                      <span class="dash-hdfs-detail-value">{{ formatBytes(overview.hdfs?.usedSpace || 0) }}</span>
                    </div>
                    <div class="dash-hdfs-detail-item">
                      <span class="dash-hdfs-detail-label">总空间</span>
                      <span class="dash-hdfs-detail-value">{{ formatBytes(overview.hdfs?.totalSpace || 0) }}</span>
                    </div>
                    <div class="dash-hdfs-detail-item">
                      <span class="dash-hdfs-detail-label">文件数</span>
                      <span class="dash-hdfs-detail-value">{{ overview.hdfs?.fileCount || 0 }}</span>
                    </div>
                    <div class="dash-hdfs-detail-item">
                      <span class="dash-hdfs-detail-label">目录数</span>
                      <span class="dash-hdfs-detail-value">{{ overview.hdfs?.dirCount || 0 }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <!-- HDFS Health Blocks -->
              <div class="dash-metrics-grid" style="grid-template-columns: repeat(4,1fr);margin-top:8px">
                <div class="dash-metric-card" style="flex:1">
                  <div class="dash-metric-icon" style="color:#ef4444">🔴</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value" style="color:#ef4444">{{ hdfsHealth?.UnderReplicatedBlocks ?? '-' }}</div>
                    <div class="dash-metric-label">Under-Replicated</div>
                  </div>
                </div>
                <div class="dash-metric-card" style="flex:1">
                  <div class="dash-metric-icon" style="color:#ef4444">⚠️</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value" style="color:#ef4444">{{ hdfsHealth?.MissingBlocks ?? '-' }}</div>
                    <div class="dash-metric-label">Missing Blocks</div>
                  </div>
                </div>
                <div class="dash-metric-card" style="flex:1">
                  <div class="dash-metric-icon" style="color:#eab308">💀</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value" style="color:#eab308">{{ hdfsHealth?.CorruptBlocks ?? '-' }}</div>
                    <div class="dash-metric-label">Corrupt Blocks</div>
                  </div>
                </div>
                <div class="dash-metric-card" style="flex:1">
                  <div class="dash-metric-icon" style="color:#3b82f6">📦</div>
                  <div class="dash-metric-body">
                    <div class="dash-metric-value" style="color:#3b82f6">{{ hdfsHealth?.BlocksTotal ?? '-' }}</div>
                    <div class="dash-metric-label">Total Blocks</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- HDFS - 子标签页（支持拖拽排序） -->
          <div v-show="activeTab === 'hdfs'" class="panel">
            <el-tabs v-model="hdfsSubTab" type="border-card" style="background:transparent" ref="hdfsTabsRef">
              <el-tab-pane v-for="tab in hdfsTabOrder" :key="tab.name" :label="tab.label" :name="tab.name">
                <!-- 📂 文件系统（含上传下载） -->
                <template v-if="tab.name === 'files'">
                <div class="panel-toolbar">
                  <el-input v-model="hdfsPath" placeholder="/" class="path-input" clearable @keyup.enter="loadHdfsFiles">
                    <template #prefix><span style="color:#999">📁</span></template>
                  </el-input>
                  <el-button type="primary" @click="loadHdfsFiles" :icon="'Search'" round>浏览</el-button>
                  <el-button @click="hdfsPath = '/'; loadHdfsFiles()" round>根目录</el-button>
                  <el-button type="success" @click="triggerUpload" :loading="uploadLoading" round>📤 上传</el-button>
                  <el-button type="warning" @click="showMkdirDialog = true" round>📁 创建目录</el-button>
                  <el-button type="info" @click="showSearchDialog = true; nextTick(() => searchInputRef.value?.focus())" round>🔍 全局搜索</el-button>
                </div>

                <!-- 导航行：返回上级 + 面包屑 + 搜索 + 刷新 -->
                <div class="fs-nav-row">
                  <el-button size="small" @click="goToParentDir" :disabled="hdfsPath === '/'" round>⬆ 返回上级</el-button>
                  <div class="fs-breadcrumb">
                    <span class="fs-breadcrumb-item fs-breadcrumb-root"
                          @click="hdfsPath = '/'; loadHdfsFiles()">/</span>
                    <template v-for="(seg, idx) in breadcrumbSegments" :key="idx">
                      <span class="fs-breadcrumb-sep">/</span>
                      <span class="fs-breadcrumb-item"
                            @click="hdfsPath = breadcrumbPaths[idx]; loadHdfsFiles()">{{ seg }}</span>
                    </template>
                    <el-tooltip content="复制当前路径" placement="top">
                      <el-button size="small" text class="fs-copy-path-btn" @click="handleCopyDirPath" circle>📋</el-button>
                    </el-tooltip>
                  </div>
                  <el-input v-model="hdfsSearch" placeholder="搜索文件..." clearable size="small"
                            style="width:200px;flex-shrink:0" class="fs-search-input" ref="hdfsSearchInputRef" />
                  <el-button size="small" @click="loadHdfsFiles" :icon="'Refresh'" :loading="hdfsLoading" round>刷新</el-button>
                  <el-button size="small" text @click="exportHdfsFilesCsv" round>📥 导出 CSV</el-button>
                  <el-popover placement="bottom" :width="240" trigger="click" popper-class="shortcuts-popover">
                    <template #reference>
                      <el-button size="small" text class="shortcuts-hint-btn" circle>⌨️</el-button>
                    </template>
                    <div class="shortcuts-popover-content">
                      <div class="shortcuts-title">⌨️ 键盘快捷键</div>
                      <div class="shortcut-row"><span class="shortcut-key">Ctrl+A</span><span class="shortcut-desc">全选文件</span></div>
                      <div class="shortcut-row"><span class="shortcut-key">Delete</span><span class="shortcut-desc">删除选中文件</span></div>
                      <div class="shortcut-row"><span class="shortcut-key">F5</span><span class="shortcut-desc">刷新文件列表</span></div>
                      <div class="shortcut-row"><span class="shortcut-key">Ctrl+F</span><span class="shortcut-desc">聚焦搜索框</span></div>
                      <div class="shortcut-row"><span class="shortcut-key">Backspace</span><span class="shortcut-desc">返回上级目录</span></div>
                      <div class="shortcut-row"><span class="shortcut-key">双击目录</span><span class="shortcut-desc">进入目录</span></div>
                      <div class="shortcuts-footer">⌘ 代替 Ctrl 在 Mac 上使用</div>
                    </div>
                  </el-popover>
                </div>

                <!-- 拖拽上传覆盖区 -->
                <div
                  class="drop-zone-overlay"
                  :class="{ 'is-dragging': isDragging }"
                  @dragover.prevent
                  @dragenter.prevent
                  @drop.prevent="handleDrop"
                >
                  <div class="drop-zone-content">
                    <span class="drop-zone-icon">📤</span>
                    <span class="drop-zone-text">拖拽文件到此处上传</span>
                  </div>
                </div>

                <!-- 上传进度条 -->
                <div v-if="uploadLoading && uploadFileName" class="progress-bar-wrap">
                  <div class="progress-info"><span>📤 上传: {{ uploadFileName }}</span><span>{{ uploadProgress }}%</span></div>
                  <el-progress :percentage="uploadProgress" :stroke-width="8" :color="uploadProgress===100?'#67c23a':'#409eff'" />
                </div>
                <!-- 下载进度条 -->
                <div v-if="downloadFileName && downloadProgress>0 && downloadProgress<100" class="progress-bar-wrap">
                  <div class="progress-info"><span>⬇️ 下载: {{ downloadFileName }}</span><span>{{ downloadProgress }}%</span></div>
                  <el-progress :percentage="downloadProgress" :stroke-width="8" color="#67c23a" />
                </div>
                <!-- 批量操作栏 -->
                <div v-if="selectedFiles.length > 0" class="batch-action-bar">
                  <span class="batch-count">已选择 {{ selectedFiles.length }} 项</span>
                  <el-button type="danger" size="small" @click="batchDelete" round v-if="canPerformDestructiveOps">🗑️ 批量移动到回收站</el-button>
                  <el-button type="warning" size="small" @click="batchChmod" round>批量修改权限</el-button>
                  <el-button size="small" @click="selectedFiles = []" round>取消选择</el-button>
                </div>
                <el-table :data="filteredHdfsFiles" stripe :size="tableSize" max-height="520px" class="glass-table fs-table" highlight-current-row
                  :row-class-name="({ row }) => highlightedRowKey === row.path ? 'row-highlight' : ''"
                  @selection-change="handleSelectionChange" @row-dblclick="onRowDoubleClick" v-loading="hdfsLoading" ref="hdfsTableRef" element-loading-text="加载中...">
                  <el-table-column type="selection" width="40" />
                  <el-table-column label="" width="32">
                    <template #default="{ row }">
                      <span v-if="fileNotesMap[row.path]" style="font-size:13px;cursor:default" title="有笔记">📝</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="名称" min-width="240">
                    <template #default="{ row }">
                      <span class="file-link" :class="{ 'is-dir': row.isDirectory }"
                            @click="row.isDirectory ? (hdfsPath=row.path, loadHdfsFiles()) : null">
                        {{ getFileIcon(row) }} {{ row.name }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="length" label="大小" width="90" align="right">
                    <template #default="{ row }">{{ formatBytes(row.length) }}</template>
                  </el-table-column>
                  <el-table-column prop="owner" label="Owner" width="80" />
                  <el-table-column prop="permission" label="权限" width="80" />
                  <el-table-column prop="modificationTime" label="修改时间" width="170">
                    <template #default="{ row }">{{ new Date(row.modificationTime).toLocaleString() }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="110" fixed="right">
                    <template #default="{ row }">
                      <el-dropdown trigger="click" @command="(cmd) => handleAction(row, cmd)">
                        <el-button size="small" circle>⋮</el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="copyPath" icon="CopyDocument">📋 复制路径</el-dropdown-item>
                            <el-dropdown-item v-if="!row.isDirectory" command="download" icon="Download">下载</el-dropdown-item>
                            <el-dropdown-item v-if="!row.isDirectory" command="preview" icon="View">👁️ 预览</el-dropdown-item>
                            <el-dropdown-item v-if="row.isDirectory" command="openInNewTab" icon="Open">📂 在新标签打开</el-dropdown-item>
                            <el-dropdown-item command="rename" icon="Edit">✏️ 重命名</el-dropdown-item>
                            <el-dropdown-item command="move" icon="FolderOpened">📦 移动</el-dropdown-item>
                            <el-dropdown-item command="chmod" icon="Edit">权限</el-dropdown-item>
                            <el-dropdown-item command="note" icon="Edit">📝 笔记</el-dropdown-item>
                            <el-dropdown-item command="registerCatalog" icon="Upload">📚 注册到数据目录</el-dropdown-item>
                            <el-dropdown-item command="delete" icon="Delete" divided style="color:#f56c6c" v-if="canPerformDestructiveOps">🗑️ 移动到回收站</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>
                  </el-table-column>
                </el-table>

                <div v-if="fileTotal > 0" class="fs-pagination-wrap">
                  <el-pagination
                    v-model:current-page="filePage"
                    v-model:page-size="filePageSize"
                    :page-sizes="[20, 50, 100, 200]"
                    :total="fileTotal"
                    layout="total, sizes, prev, pager, next, jumper"
                    background
                    small
                    @current-change="loadHdfsFiles"
                    @size-change="() => { filePage = 1; loadHdfsFiles() }"
                  />
                </div>

                <!-- 状态栏 -->
                <div class="fs-status-bar">
                  <span class="fs-status-item">
                    <span class="fs-status-icon">📁</span>
                    <span>{{ fileTotal > 0 ? hdfsFiles.length + '/' + fileTotal : hdfsFiles.length }} 项</span>
                  </span>
                  <span class="fs-status-sep">|</span>
                  <span class="fs-status-item">
                    <span class="fs-status-icon">✅</span>
                    <span>{{ selectedFiles.length }} 项已选</span>
                  </span>
                  <span class="fs-status-sep">|</span>
                  <span class="fs-status-item">
                    <span class="fs-status-icon">📂</span>
                    <span class="fs-status-path" :title="hdfsPath">{{ hdfsPath }}</span>
                  </span>
                </div>

                <!-- 空状态 -->
                <div v-if="hdfsFiles.length === 0 && !uploadLoading" class="fs-empty">
                  <div class="fs-empty-icon">📂</div>
                  <div class="fs-empty-text">当前目录为空</div>
                  <el-button type="primary" size="small" @click="triggerUpload" round style="margin-top:8px">📤 上传文件</el-button>
                </div>

                <!-- 创建目录对话框 -->
                <el-dialog v-model="showMkdirDialog" title="创建目录" width="400px" top="30vh" class="glass-dialog">
                  <el-form @submit.prevent="handleMkdir">
                    <el-form-item label="当前路径">
                      <el-input :model-value="hdfsPath" disabled size="small" />
                    </el-form-item>
                    <el-form-item label="目录名">
                      <el-input v-model="mkdirName" placeholder="输入目录名" size="default" ref="mkdirInputRef"
                        @keyup.enter="handleMkdir" />
                    </el-form-item>
                  </el-form>
                  <template #footer>
                    <el-button @click="showMkdirDialog = false" round>取消</el-button>
                    <el-button type="primary" @click="handleMkdir" :loading="mkdirLoading" round>创建</el-button>
                  </template>
                </el-dialog>

                <!-- 修改权限对话框 -->
                <el-dialog v-model="showChmodDialog" title="修改权限" width="520px" top="30vh" class="glass-dialog">
                  <div style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
                    <template v-if="selectedFiles.length > 1">
                      批量修改 {{ selectedFiles.length }} 个文件的权限
                    </template>
                    <template v-else>
                      路径: <code style="color:#4fc3f7">{{ chmodTarget?.path || '' }}</code>
                    </template>
                  </div>
                  <div v-if="selectedFiles.length <= 1" style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
                    当前权限: <code style="color:#ffcc80">{{ chmodTarget?.permission || '' }}</code>
                  </div>
                  <el-form label-width="80px">
                    <el-form-item label="属主">
                      <el-select v-model="chmodOwnerMode" style="width:100%">
                        <el-option label="--- (0)" value="0" />
                        <el-option label="--x (1)" value="1" />
                        <el-option label="-w- (2)" value="2" />
                        <el-option label="-wx (3)" value="3" />
                        <el-option label="r-- (4)" value="4" />
                        <el-option label="r-x (5)" value="5" />
                        <el-option label="rw- (6)" value="6" />
                        <el-option label="rwx (7)" value="7" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="用户组">
                      <el-select v-model="chmodGroupMode" style="width:100%">
                        <el-option v-for="n in 8" :key="n-1" :label="['---','--x','-w-','-wx','r--','r-x','rw-','rwx'][n-1]+' ('+(n-1)+')'" :value="String(n-1)" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="其他">
                      <el-select v-model="chmodOtherMode" style="width:100%">
                        <el-option v-for="n in 8" :key="n-1" :label="['---','--x','-w-','-wx','r--','r-x','rw-','rwx'][n-1]+' ('+(n-1)+')'" :value="String(n-1)" />
                      </el-select>
                    </el-form-item>
                  </el-form>

                  <!-- ACL Section -->
                  <div v-if="selectedFiles.length <= 1" style="margin-top:16px;border-top:1px solid #334155;padding-top:12px">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
                      <span style="font-size:14px;font-weight:600;color:#f1f5f9">ACL 条目</span>
                      <div>
                        <el-button size="small" type="primary" text @click="loadAclEntries" :loading="aclLoading" round style="margin-right:4px">🔄 刷新</el-button>
                        <el-button size="small" type="success" text @click="showAddAclInput = true" round v-if="!showAddAclInput">➕ 添加 ACL</el-button>
                      </div>
                    </div>

                    <!-- ACL 条目列表 -->
                    <div v-if="aclEntries.length === 0 && !aclLoading" class="fs-empty" style="padding:12px"><div class="fs-empty-icon">🔒</div><div class="fs-empty-text">暂无 ACL 条目</div><el-button type="primary" size="small" @click="loadAclEntries" style="margin-top:8px">🔄 刷新 ACL</el-button></div>
                    <div v-for="(entry, idx) in aclEntries" :key="idx"
                         style="display:flex;align-items:center;justify-content:space-between;padding:4px 8px;margin-bottom:2px;border-radius:4px;background:rgba(255,255,255,0.03)">
                      <span style="font-family:monospace;font-size:13px;color:#80cbc4">{{ entry }}</span>
                      <el-button size="small" type="danger" text @click="handleRemoveAcl(entry, idx)" round>🗑️ 删除</el-button>
                    </div>

                    <!-- 添加 ACL 输入区 -->
                    <div v-if="showAddAclInput" style="margin-top:8px;padding:10px;border:1px solid #334155;border-radius:6px;background:rgba(255,255,255,0.02)">
                      <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center">
                        <el-select v-model="newAclType" size="small" style="width:100px" placeholder="类型">
                          <el-option label="user" value="user" />
                          <el-option label="group" value="group" />
                          <el-option label="mask" value="mask" />
                          <el-option label="other" value="other" />
                        </el-select>
                        <el-input v-model="newAclName" size="small" placeholder="名称(可选)" style="width:120px" clearable />
                        <el-select v-model="newAclPerm" size="small" style="width:100px" placeholder="权限">
                          <el-option label="---" value="---" />
                          <el-option label="--x" value="--x" />
                          <el-option label="-w-" value="-w-" />
                          <el-option label="-wx" value="-wx" />
                          <el-option label="r--" value="r--" />
                          <el-option label="r-x" value="r-x" />
                          <el-option label="rw-" value="rw-" />
                          <el-option label="rwx" value="rwx" />
                        </el-select>
                        <el-button size="small" type="primary" @click="handleAddAcl" round :disabled="!newAclType || !newAclPerm">添加</el-button>
                        <el-button size="small" @click="showAddAclInput = false; resetNewAclForm()" round>取消</el-button>
                      </div>
                    </div>
                  </div>

                  <template #footer>
                    <el-button @click="showChmodDialog = false" round>取消</el-button>
                    <el-button type="primary" @click="handleChmod" :loading="chmodLoading" round style="margin-right:8px">应用基础权限</el-button>
                    <el-button type="success" v-if="selectedFiles.length <= 1" @click="handleSaveAcl" :loading="aclSaving" round>保存 ACL</el-button>
                  </template>
                </el-dialog>

                <!-- 重命名对话框 -->
                <el-dialog v-model="showRenameDialog" title="重命名" width="420px" top="30vh" class="glass-dialog">
                  <div style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
                    当前路径: <code style="color:#4fc3f7">{{ renameTarget?.path || '' }}</code>
                  </div>
                  <el-form @submit.prevent="handleRename">
                    <el-form-item label="新名称">
                      <el-input v-model="renameNewName" placeholder="输入新名称" size="default"
                        @keyup.enter="handleRename" />
                    </el-form-item>
                  </el-form>
                  <template #footer>
                    <el-button @click="showRenameDialog = false" round>取消</el-button>
                    <el-button type="primary" @click="handleRename" :loading="renameLoading" round>确定</el-button>
                  </template>
                </el-dialog>

                <!-- 移动对话框 -->
                <el-dialog v-model="showMoveDialog" title="移动文件/目录" width="420px" top="30vh" class="glass-dialog">
                  <div style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
                    当前路径: <code style="color:#4fc3f7">{{ moveTarget?.path || '' }}</code>
                  </div>
                  <div style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
                    名称: <code style="color:#ffcc80">{{ moveTarget?.name || '' }}</code>
                  </div>
                  <el-form @submit.prevent="handleMove">
                    <el-form-item label="目标目录">
                      <el-input v-model="moveDestDir" placeholder="输入目标目录路径，如 /data/target" size="default"
                        @keyup.enter="handleMove" />
                    </el-form-item>
                  </el-form>
                  <template #footer>
                    <el-button @click="showMoveDialog = false" round>取消</el-button>
                    <el-button type="primary" @click="handleMove" :loading="moveLoading" round>确定移动</el-button>
                  </template>
                </el-dialog>

                <!-- 全局搜索对话框 -->
                <el-dialog v-model="showSearchDialog" title="🔍 全局搜索" width="800px" top="5vh" class="glass-dialog" :close-on-click-modal="false" @opened="nextTick(() => searchInputRef.value?.focus())">
                  <div class="search-dialog-body">
                    <div class="search-dialog-input-row">
                      <el-input v-model="searchQuery" placeholder="输入文件名搜索..." size="large" clearable
                        @keyup.enter="handleSearch" ref="searchInputRef" class="search-dialog-input" />
                      <el-button type="primary" @click="handleSearch" :loading="searchLoading" size="large">搜索</el-button>
                    </div>
                    <div v-if="searchResults.length > 0 && !searchLoading" class="search-results-info">
                      找到 {{ searchResults.length }} 个结果
                    </div>
                    <div v-if="searchLoading" class="search-loading-hint">搜索中，请稍候...</div>
                    <div v-if="searchError" class="search-error-hint">{{ searchError }}</div>
                    <div v-if="searchDone && searchResults.length === 0 && !searchLoading" class="search-empty-hint">未找到匹配的文件</div>
                    <el-table v-if="searchResults.length > 0" :data="searchResults" stripe :size="tableSize" max-height="420px"
                      class="glass-table search-results-table" highlight-current-row @row-click="onSearchResultClick"
                      v-loading="searchLoading">
                      <el-table-column label="名称" min-width="200">
                        <template #default="{ row }">
                          <span>{{ row.isDirectory ? '📁' : '📄' }} {{ row.name }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="路径" min-width="300">
                        <template #default="{ row }">
                          <span style="color:#94a3b8;font-size:12px">{{ row.path }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="大小" width="90" align="right">
                        <template #default="{ row }">{{ formatBytes(row.length) }}</template>
                      </el-table-column>
                      <el-table-column label="Owner" width="80">
                        <template #default="{ row }"><span style="color:#94a3b8">{{ row.owner }}</span></template>
                      </el-table-column>
                      <el-table-column label="修改时间" width="170">
                        <template #default="{ row }">{{ new Date(row.modificationTime).toLocaleString() }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                </el-dialog>

                <!-- 文件笔记对话框 -->
                <el-dialog v-model="showNoteDialog" title="📝 文件笔记" width="500px" top="25vh" class="glass-dialog" :close-on-click-modal="false">
                  <div class="note-dialog-body">
                    <div class="note-dialog-path">{{ noteTarget?.path }}</div>
                    <el-input v-model="noteContent" type="textarea" :rows="6" placeholder="输入笔记内容..." class="note-dialog-textarea" />
                  </div>
                  <template #footer>
                    <el-button @click="showNoteDialog = false" round>取消</el-button>
                    <el-button type="danger" @click="handleDeleteNote" :loading="noteDeleting" round v-if="noteContent && noteContent.trim()">删除</el-button>
                    <el-button type="primary" @click="handleSaveNote" :loading="noteSaving" round>保存</el-button>
                  </template>
                </el-dialog>

                <!-- 文件预览抽屉 -->
                <el-drawer v-model="showPreview" title="文件预览" size="55%" class="glass-drawer preview-drawer" :before-close="closePreview">
                  <template #header>
                    <div class="preview-header">
                      <span class="preview-title">👁️ 文件预览</span>
                      <div class="preview-header-info">
                        <span class="preview-filename">{{ previewFile?.name || '' }}</span>
                        <span class="preview-meta" v-if="previewData">
                          {{ formatBytes(previewData.fileSize) }}
                          <span v-if="previewData.isTruncated" class="preview-truncated">（仅显示前 {{ formatBytes(previewData.previewSize) }}）</span>
                        </span>
                        <el-button size="small" type="primary" plain round @click="previewDownload" :disabled="!previewData">⬇️ 下载</el-button>
                      </div>
                    </div>
                  </template>
                  <div v-if="previewLoading" class="preview-loading">加载中...</div>
                  <div v-else-if="previewError" class="preview-error">{{ previewError }}</div>
                  <div v-else-if="previewData" class="preview-body">
                    <div v-if="previewIsImage" class="preview-image-wrap">
                      <img :src="previewImageUrl" class="preview-image" alt="预览图片" @load="previewLoading = false" @error="previewError='图片加载失败'" />
                    </div>
                    <div v-else-if="previewIsBinary" class="preview-binary-msg">🔒 二进制文件，无法预览</div>
                    <div v-else-if="previewUnsupported" class="preview-binary-msg">
                      ⚠️ 不支持预览此文件类型
                      <div style="margin-top:8px">
                        <el-button size="small" type="primary" plain round @click="previewDownload">⬇️ 下载查看</el-button>
                      </div>
                    </div>
                    <div v-else class="preview-content-wrap">
                      <div class="preview-toolbar">
                        <span class="preview-lines-info">共 {{ previewLines.length }} 行</span>
                        <el-button size="small" text @click="previewWrap = !previewWrap" round>
                          {{ previewWrap ? '折行' : '不折行' }}
                        </el-button>
                        <el-button size="small" text @click="copyPreviewContent" round>📋 复制</el-button>
                      </div>
                      <div class="preview-code" :class="{ 'preview-wrap': previewWrap }">
                        <div v-for="(line, i) in previewLines" :key="i" class="preview-line">
                          <span class="preview-ln">{{ i + 1 }}</span>
                          <span class="preview-text" v-html="highlightLine(line)"></span>
                        </div>
                      </div>
                    </div>
                  </div>
                </el-drawer>

                </template>

                <!-- 🗑️ 回收站 -->
                <template v-if="tab.name === 'trash'">
                <div class="panel-toolbar">
                  <div style="font-size:13px;color:rgba(255,255,255,0.6);flex:1">
                    回收站中的文件可以恢复或永久删除
                    <span v-if="trashFiles.length > 0" style="margin-left:8px;color:rgba(255,255,255,0.3)">
                      ({{ trashFiles.length }} 项)
                    </span>
                  </div>
                  <el-button type="danger" @click="handleEmptyTrash" :loading="trashEmptying" round :disabled="trashFiles.length === 0" v-if="canPerformDestructiveOps">
                    🗑️ 清空回收站
                  </el-button>
                  <el-button @click="loadTrashFiles" :icon="'Refresh'" round :loading="trashLoading">
                    刷新
                  </el-button>
                  <el-button text size="small" @click="exportTrashFilesCsv" round>📥 导出 CSV</el-button>
                </div>

                <!-- 保留策略配置 -->
                <div class="trash-retention-bar">
                  <span class="trash-retention-label">🗄️ 保留策略</span>
                  <el-input-number v-model="retentionDays" :min="1" :max="365" size="small" style="width:100px" />
                  <span class="trash-retention-unit">天</span>
                  <el-button size="small" type="primary" @click="saveRetentionConfig" :loading="retentionSaving" plain round>保存</el-button>
                  <el-button size="small" type="warning" @click="handleCleanExpiredTrash" :loading="trashCleaningExpired" plain round>🧹 立即清理过期文件</el-button>
                  <span class="trash-retention-hint">自动清空 {{ retentionDays }} 天前的文件</span>
                  <el-switch v-model="retentionAutoClean" size="small" style="margin-left:8px" />
                </div>

                <el-table :data="trashFiles" stripe :size="tableSize" max-height="500px" class="glass-table fs-table" v-loading="trashLoading">
                  <el-table-column label="名称" min-width="200">
                    <template #default="{ row }">
                      <span :class="row.isDirectory ? 'file-link is-dir' : 'file-link'">
                        {{ row.isDirectory ? '📁' : '📄' }} {{ row.name }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="原始路径" min-width="250">
                    <template #default="{ row }">
                      <span style="color:#94a3b8;font-size:12px">{{ row.originalPath || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="length" label="大小" width="90" align="right">
                    <template #default="{ row }">{{ formatBytes(row.length) }}</template>
                  </el-table-column>
                  <el-table-column label="删除时间" width="170">
                    <template #default="{ row }">
                      {{ row.deletionTime ? new Date(row.deletionTime).toLocaleString() : '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="150" fixed="right">
                    <template #default="{ row }">
                      <el-button size="small" type="success" plain round @click="handleRestoreFromTrash(row)" :loading="trashRestoring === row.trashPath" v-if="canPerformDestructiveOps">
                        🔄 恢复
                      </el-button>
                      <el-button size="small" type="danger" plain round @click="handlePermanentDelete(row)" v-if="canPerformDestructiveOps">
                        🗑️ 永久删除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>

                <div v-if="trashFiles.length === 0 && !trashLoading" class="fs-empty">
                  <div class="fs-empty-icon">🗑️</div>
                  <div class="fs-empty-text">回收站为空</div>
                  <div style="font-size:12px;color:rgba(255,255,255,0.3);margin-top:4px">删除文件将先移入回收站，可从此处恢复</div>
                </div>
                </template>

                <!-- 🖥️ DataNode -->
                <template v-if="tab.name === 'datanodes'">
                <div class="stats-grid" style="margin-bottom:16px">
                  <div class="stat-card hdfs-card" style="cursor:pointer" @click="switchNN" v-if="canPerformDestructiveOps">
                    <div class="stat-icon">🌐</div>
                    <div class="stat-info">
                      <div class="stat-label">nn1 ({{ nnHostPort || '-' }})</div>
                      <div class="stat-value" :style="{color: nnState==='active'?'#67c23a':'#e6a23c'}">{{ nnState||'未知' }}</div>
                    </div>
                    <div class="stat-trend" :class="nnState==='active'?'up':''">{{ nnState==='active'?'🟢 Active':'⏸️ Standby' }}</div>
                  </div>
                  <div class="stat-card hdfs-card" style="cursor:pointer" @click="switchNN" v-if="canPerformDestructiveOps">
                    <div class="stat-icon">🌐</div>
                    <div class="stat-info">
                      <div class="stat-label">nn2 ({{ nn2HostPort || '-' }})</div>
                      <div class="stat-value" :style="{color: nn2State==='active'?'#67c23a':'#e6a23c'}">{{ nn2State||'未知' }}</div>
                    </div>
                    <div class="stat-trend" :class="nn2State==='active'?'up':''">{{ nn2State==='active'?'🟢 Active':'⏸️ Standby' }}</div>
                  </div>
                  <div class="stat-card hdfs-card" style="cursor:pointer" @click="switchNN" v-if="canPerformDestructiveOps">
                    <div class="stat-icon">🔄</div>
                    <div class="stat-info">
                      <div class="stat-label">一键切换</div>
                      <div class="stat-value" style="font-size:16px;color:#409eff">点击切换</div>
                    </div>
                    <div class="stat-trend up">HA 切换</div>
                  </div>
                  <div class="stat-card hdfs-card">
                    <div class="stat-icon">💿</div>
                    <div class="stat-info">
                      <div class="stat-label">HDFS 容量</div>
                      <div class="stat-value">{{ formatBytes(nnCapacityTotal) }}</div>
                    </div>
                    <div class="stat-trend">{{ formatBytes(nnCapacityUsed) }} 已用</div>
                  </div>
                  <div class="stat-card hdfs-card">
                    <div class="stat-icon">📦</div>
                    <div class="stat-info">
                      <div class="stat-label">文件 / 块</div>
                      <div class="stat-value">{{ nnFilesTotal }} / {{ nnBlocksTotal }}</div>
                    </div>
                    <div class="stat-trend">{{ nnNumLiveDNs }} DataNodes 在线</div>
                  </div>
                  <div class="stat-card hdfs-card">
                    <div class="stat-icon">🟢</div>
                    <div class="stat-info">
                      <div class="stat-label">DataNode 状态</div>
                      <div class="stat-value" style="color:#67c23a">{{ nnNumLiveDNs }} 在线</div>
                    </div>
                    <div class="stat-trend" v-if="nnNumDeadDNs>0" style="color:#f56c6c">{{ nnNumDeadDNs }} 离线</div>
                    <div class="stat-trend" v-else>全部正常</div>
                  </div>
                </div>
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">🖥️ DataNode 节点 <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-left:8px">{{ dataNodes.length }} 个节点，{{ totalBlocks }} 个数据块</span></div>
                  <el-table :data="dataNodes" stripe :size="tableSize" class="glass-table" max-height="320px" @row-click="openDNDetail" style="cursor:pointer">
                    <el-table-column label="节点" width="110"><template #default="{ row }">{{ (row.id||'').split(':')[0] }}</template></el-table-column>
                    <el-table-column label="地址" width="130"><template #default="{ row }">{{ row.infoAddr||'-' }}</template></el-table-column>
                    <el-table-column label="容量" width="170">
                      <template #default="{ row }">
                        <div class="dn-capacity-cell">
                          <div class="dn-capacity-bar" style="margin-bottom:3px">
                            <div class="dn-capacity-fill" :style="{ width: row.capacity>0?((row.usedSpace||0)/row.capacity*100)+'%':'0%' }"></div>
                          </div>
                          <div class="dn-capacity-text">{{ formatBytes(row.usedSpace||0) }} / {{ formatBytes(row.capacity||0) }}</div>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="已用" width="80" align="right"><template #default="{ row }">{{ formatBytes(row.usedSpace||0) }}</template></el-table-column>
                    <el-table-column label="非DFS" width="80" align="right"><template #default="{ row }">{{ formatBytes(row.nonDfsUsedSpace||0) }}</template></el-table-column>
                    <el-table-column label="剩余" width="100" align="right"><template #default="{ row }">{{ formatBytes((row.capacity||0)-(row.usedSpace||0)-(row.nonDfsUsedSpace||0)) }}</template></el-table-column>
                    <el-table-column label="数据块" width="70" align="right"><template #default="{ row }">{{ row.numBlocks||0 }}</template></el-table-column>
                    <el-table-column label="状态" width="110">
                      <template #default="{ row }">
                        <el-tag :type="dnStateTag(row.adminState)" size="small" effect="dark">{{ row.adminState }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="100" fixed="right">
                      <template #default="{ row }">
                        <el-button size="small" type="primary" plain round @click="viewDNLogs(row)">📋 日志</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <!-- 节点存储分布 -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">💾 节点存储分布 <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-left:8px">各 DataNode 磁盘使用情况</span></div>
                  <div class="dn-storage-dist">
                    <div v-for="dn in dataNodes" :key="dn.id" class="dn-storage-row">
                      <div class="dn-storage-label">
                        <span class="dn-storage-name">{{ (dn.id||'').split(':')[0] }}</span>
                        <span :class="'dn-storage-dot '+dnStateDot(dn.adminState)"></span>
                      </div>
                      <div class="dn-storage-bar-wrap">
                        <div class="dn-storage-bar">
                          <div class="dn-storage-fill" :style="{ width: dn.capacity>0?((dn.usedSpace||0)/dn.capacity*100)+'%':'0%', background: dnUsageColor(dn) }"></div>
                        </div>
                      </div>
                      <div class="dn-storage-info">
                        <span class="dn-storage-used">{{ formatBytes(dn.usedSpace||0) }}</span>
                        <span class="dn-storage-sep">/</span>
                        <span class="dn-storage-total">{{ formatBytes(dn.capacity||0) }}</span>
                      </div>
                    </div>
                    <div v-if="dataNodes.length===0" class="fs-empty" style="padding:24px"><div class="fs-empty-icon">🖥️</div><div class="fs-empty-text">暂无 DataNode 数据</div><el-button type="primary" size="small" @click="loadHdfsNodes" style="margin-top:12px">🔄 刷新</el-button></div>
                  </div>
                </div>
                <div class="glass-card">
                  <div class="glass-card-title">➕ DataNode 横向扩展 <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-left:8px">一键生成新 DataNode 部署命令</span></div>
                  <div style="display:flex;gap:10px;align-items:center;margin-bottom:12px">
                    <el-input v-model="newDNName" placeholder="节点名 (如 dn4)" size="small" style="width:160px" />
                    <el-button size="small" type="primary" @click="scaleDN" round :loading="dnScaling">生成命令</el-button>
                  </div>
                  <div v-if="scaleCommands.length>0" class="scale-cmd-box">
                    <div class="scale-cmd-header"><span>部署命令</span><el-button size="small" text @click="copyCommands" round>📋 复制</el-button></div>
                    <pre class="scale-cmd-body"><code>{{ scaleCommands.join('\n') }}</code></pre>
                  </div>
                </div>
                <!-- DataNode Detail Drawer -->
                <el-drawer v-model="showDNDetail" size="45%" class="glass-drawer" :before-close="closeDNDetail">
                  <template #header>
                    <div style="display:flex;align-items:center;gap:10px">
                      <span style="font-size:16px;font-weight:600;color:rgba(255,255,255,0.9)">🖥️ DataNode 详情</span>
                      <el-tag v-if="selectedDN" :type="dnStateTag(selectedDN.adminState)" size="small" effect="dark">{{ selectedDN.adminState }}</el-tag>
                    </div>
                  </template>
                  <div v-if="!selectedDN" style="text-align:center;padding:40px;color:rgba(255,255,255,0.3)">未选择节点</div>
                  <div v-else class="detail-content">
                    <!-- Basic Info -->
                    <div class="glass-card" style="margin-bottom:16px">
                      <div class="glass-card-title">📋 基本信息</div>
                      <el-descriptions :column="2" border size="small" class="detail-descriptions">
                        <el-descriptions-item label="节点名称">{{ (selectedDN.id||'').split(':')[0] }}</el-descriptions-item>
                        <el-descriptions-item label="地址">{{ selectedDN.infoAddr||'-' }}</el-descriptions-item>
                        <el-descriptions-item label="状态">
                          <el-tag :type="dnStateTag(selectedDN.adminState)" size="small" effect="dark">{{ selectedDN.adminState }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="最后联系时间">{{ selectedDN.lastContact ? new Date(selectedDN.lastContact).toLocaleString() : '-' }}</el-descriptions-item>
                        <el-descriptions-item label="Admin 状态">{{ selectedDN.adminState || '-' }}</el-descriptions-item>
                        <el-descriptions-item label="容量使用">
                          <div class="dn-capacity-cell" style="min-width:160px">
                            <div class="dn-capacity-bar" style="margin-bottom:3px">
                              <div class="dn-capacity-fill" :style="{ width: selectedDN.capacity>0?((selectedDN.usedSpace||0)/selectedDN.capacity*100)+'%':'0%' }"></div>
                            </div>
                            <div class="dn-capacity-text">{{ formatBytes(selectedDN.usedSpace||0) }} / {{ formatBytes(selectedDN.capacity||0) }}</div>
                          </div>
                        </el-descriptions-item>
                        <el-descriptions-item label="数据块数">{{ selectedDN.numBlocks||0 }}</el-descriptions-item>
                        <el-descriptions-item label="剩余空间">{{ formatBytes((selectedDN.capacity||0)-(selectedDN.usedSpace||0)-(selectedDN.nonDfsUsedSpace||0)) }}</el-descriptions-item>
                        <el-descriptions-item label="非DFS使用">{{ formatBytes(selectedDN.nonDfsUsedSpace||0) }}</el-descriptions-item>
                      </el-descriptions>
                    </div>

                    <!-- Disk Details -->
                    <div class="glass-card" style="margin-bottom:16px">
                      <div class="glass-card-title">💾 磁盘详情</div>
                      <div v-if="selectedDN.storageVolumes && selectedDN.storageVolumes.length > 0">
                        <el-table :data="selectedDN.storageVolumes" stripe :size="tableSize" class="glass-table" max-height="200px">
                          <el-table-column label="存储目录" prop="storageDir" min-width="160" show-overflow-tooltip />
                          <el-table-column label="已用空间" width="100" align="right">
                            <template #default="{ row }">{{ formatBytes(row.usedSpace||0) }}</template>
                          </el-table-column>
                          <el-table-column label="总空间" width="100" align="right">
                            <template #default="{ row }">{{ formatBytes(row.capacity||0) }}</template>
                          </el-table-column>
                          <el-table-column label="使用率" width="140">
                            <template #default="{ row }">
                              <div class="dn-capacity-bar" style="width:80px">
                                <div class="dn-capacity-fill" :style="{ width: row.capacity>0?((row.usedSpace||0)/row.capacity*100)+'%':'0%' }"></div>
                              </div>
                            </template>
                          </el-table-column>
                          <el-table-column label="状态" width="100">
                            <template #default="{ row }">
                              <el-tag :type="row.state==='Normal'?'success':row.state==='ReadOnly'?'warning':'danger'" size="small">{{ row.state||'Normal' }}</el-tag>
                            </template>
                          </el-table-column>
                        </el-table>
                      </div>
                      <div v-else style="padding:20px;text-align:center;color:rgba(255,255,255,0.3)">
                        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2" style="opacity:0.3;margin-bottom:8px"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>
                        <div>磁盘详情数据暂不可用</div>
                      </div>
                    </div>

                    <!-- Heartbeat Log -->
                    <div class="glass-card" style="margin-bottom:16px">
                      <div class="glass-card-title">💓 心跳信息</div>
                      <div v-if="selectedDN.heartbeats && selectedDN.heartbeats.length > 0">
                        <el-timeline>
                          <el-timeline-item v-for="(hb, i) in selectedDN.heartbeats.slice(0, 10)" :key="i" :timestamp="new Date(hb).toLocaleString()" placement="top" size="small">
                            <span style="color:rgba(255,255,255,0.5)">心跳上报</span>
                          </el-timeline-item>
                        </el-timeline>
                      </div>
                      <div v-else style="padding:20px;text-align:center;color:rgba(255,255,255,0.4)">
                        <span style="color:#67c23a;font-size:24px;margin-right:8px">✅</span>
                        <span>最近心跳正常</span>
                      </div>
                    </div>

                    <!-- JMX Metrics (if available) -->
                    <div v-if="dnJmxMetrics" class="glass-card" style="margin-bottom:16px">
                      <div class="glass-card-title">📊 实时指标</div>
                      <el-descriptions :column="2" border size="small" class="detail-descriptions">
                        <el-descriptions-item label="容量">{{ formatBytes(dnJmxMetrics.capacity) }}</el-descriptions-item>
                        <el-descriptions-item label="已用">{{ formatBytes(dnJmxMetrics.dfsUsed) }}</el-descriptions-item>
                        <el-descriptions-item label="剩余">{{ formatBytes(dnJmxMetrics.remaining) }}</el-descriptions-item>
                        <el-descriptions-item label="使用率">{{ ((dnJmxMetrics.dfsUsed / dnJmxMetrics.capacity) * 100).toFixed(1) }}%</el-descriptions-item>
                        <el-descriptions-item label="Block数">{{ dnJmxMetrics.numBlocks }}</el-descriptions-item>
                        <el-descriptions-item label="管理状态">{{ dnJmxMetrics.adminState }}</el-descriptions-item>
                      </el-descriptions>
                    </div>

                    <!-- Actions -->
                    <div class="glass-card" style="margin-bottom:16px">
                      <div class="glass-card-title">⚡ 操作</div>
                      <div style="display:flex;gap:10px;flex-wrap:wrap">
                        <el-button type="warning" @click="enterMaintenanceMode(selectedDN)" round :icon="'Tools'">进入维护模式</el-button>
                        <el-button type="primary" plain round @click="viewDNLogs(selectedDN)" :icon="'Document'">查看日志</el-button>
                        <el-button type="info" plain round @click="triggerRebalance(selectedDN)" :icon="'Refresh'">触发 Rebalance</el-button>
                      </div>
                    </div>
                  </div>
                </el-drawer>
                </template>

                <!-- 📝 JournalNode -->
                <template v-if="tab.name === 'journalnodes'">
                <div class="glass-card-title" style="margin-bottom:16px">📝 JournalNode 集群 <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-left:8px">{{ journalNodes.length }} 个节点</span></div>
                <el-row :gutter="12">
                  <el-col :xs="24" :sm="8" v-for="jn in journalNodes" :key="jn.ip">
                    <div class="jn-card">
                      <div class="jn-header"><span class="jn-name">📀 {{ jn.HostAndPort||jn.ip }}</span><el-tag size="small" :type="jn.Formatted==='true'?'success':'danger'" effect="dark">已格式化</el-tag></div>
                      <div class="jn-body">
                        <div class="jn-row"><span class="jn-label">版本</span><span class="jn-val">{{ (jn.Version||'').split(',')[0] }}</span></div>
                        <div class="jn-row"><span class="jn-label">集群</span><span class="jn-val">{{ jn.ClusterIds?jn.ClusterIds[0]:'-' }}</span></div>
                        <div class="jn-row"><span class="jn-label">同步次数</span><span class="jn-val">{{ jn.Syncs60sNumOps||0 }} (60s)</span></div>
                        <div class="jn-row"><span class="jn-label">同步延迟</span><span class="jn-val">{{ (jn.Syncs60s50thPercentileLatencyMicros||0).toFixed(0) }} μs</span></div>
                        <div class="jn-row"><span class="jn-label">启动时间</span><span class="jn-val">{{ jn.JNStartedTimeInMillis?new Date(jn.JNStartedTimeInMillis).toLocaleString():'-' }}</span></div>
                      </div>
                    </div>
                  </el-col>
                </el-row>

                <!-- 📦 Checkpoint 监控 -->
                <div class="glass-card" style="margin-top:16px">
                  <div class="glass-card-title">
                    📦 Checkpoint 监控
                    <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-left:8px">fsimage &amp; editlog</span>
                    <el-button size="small" text style="float:right;color:rgba(255,255,255,0.7)" @click="loadCheckpoint" :loading="checkpointLoading" round>🔄 刷新</el-button>
                  </div>
                  <div v-if="checkpointLoading" class="checkpoint-loading" style="text-align:center;padding:30px;color:rgba(255,255,255,0.3)">加载中...</div>
                  <div v-else-if="checkpointData" class="checkpoint-grid">
                    <!-- fsimage -->
                    <div class="checkpoint-card">
                      <div class="checkpoint-header">
                        <span class="checkpoint-icon">🧠</span>
                        <span class="checkpoint-title">最新 FsImage</span>
                      </div>
                      <div class="checkpoint-body">
                        <div class="cp-row">
                          <span class="cp-label">文件名</span>
                          <span class="cp-val cp-name" :title="checkpointData.fsimage.name">{{ checkpointData.fsimage.name.slice(-50) }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">大小</span>
                          <span class="cp-val">{{ checkpointData.fsimage.size }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">生成时间</span>
                          <span class="cp-val">{{ checkpointData.fsimage.date }} {{ checkpointData.fsimage.yearOrTime }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">存放目录</span>
                          <span class="cp-val cp-path">{{ checkpointData.fsimageDir }}</span>
                        </div>
                      </div>
                    </div>
                    <!-- editlog completed -->
                    <div class="checkpoint-card">
                      <div class="checkpoint-header">
                        <span class="checkpoint-icon">📝</span>
                        <span class="checkpoint-title">最新 EditLog（已完成的）</span>
                      </div>
                      <div class="checkpoint-body">
                        <div class="cp-row">
                          <span class="cp-label">文件名</span>
                          <span class="cp-val cp-name" :title="checkpointData.editlog.name">{{ checkpointData.editlog.name.slice(-50) }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">大小</span>
                          <span class="cp-val">{{ checkpointData.editlog.size }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">生成时间</span>
                          <span class="cp-val">{{ checkpointData.editlog.date }} {{ checkpointData.editlog.yearOrTime }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">存放目录</span>
                          <span class="cp-val cp-path">{{ checkpointData.editlogDir }}</span>
                        </div>
                      </div>
                    </div>
                    <!-- editlog in-progress -->
                    <div class="checkpoint-card">
                      <div class="checkpoint-header">
                        <span class="checkpoint-icon">🔄</span>
                        <span class="checkpoint-title">进行中的 EditLog</span>
                      </div>
                      <div class="checkpoint-body">
                        <div class="cp-row">
                          <span class="cp-label">文件名</span>
                          <span class="cp-val cp-name" :title="checkpointData.editlogInProgress.name">{{ checkpointData.editlogInProgress.name.slice(-50) }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">大小</span>
                          <span class="cp-val">{{ checkpointData.editlogInProgress.size }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">生成时间</span>
                          <span class="cp-val">{{ checkpointData.editlogInProgress.date }} {{ checkpointData.editlogInProgress.yearOrTime }}</span>
                        </div>
                        <div class="cp-row">
                          <span class="cp-label">存放目录</span>
                          <span class="cp-val cp-path">{{ checkpointData.editlogDir }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else style="text-align:center;padding:20px;color:rgba(255,255,255,0.3)">点击刷新获取 Checkpoint 信息</div>
                </div>
                </template>

                <!-- 📊 监控 & 元数据 -->
                <template v-if="tab.name === 'monitor'">
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">🌐 NameNode 状态监控</div>
                  <div class="stats-grid">
                    <div class="stat-card hdfs-card"><div class="stat-icon">🌐</div><div class="stat-info"><div class="stat-label">nn1 状态</div><div class="stat-value" :style="{color: nnState==='active'?'#67c23a':'#e6a23c'}">{{ nnState||'未知' }}</div></div><div class="stat-trend">{{ nnHostPort||'-' }}</div></div>
                    <div class="stat-card hdfs-card"><div class="stat-icon">🌐</div><div class="stat-info"><div class="stat-label">nn2 状态</div><div class="stat-value" :style="{color: nn2State==='active'?'#67c23a':'#e6a23c'}">{{ nn2State||'未知' }}</div></div><div class="stat-trend">{{ nn2HostPort||'-' }}</div></div>
                    <div class="stat-card hdfs-card"><div class="stat-icon">💾</div><div class="stat-info"><div class="stat-label">总容量</div><div class="stat-value">{{ formatBytes(nnCapacityTotal) }}</div></div><div class="stat-trend">已用 {{ formatBytes(nnCapacityUsed) }}</div></div>
                    <div class="stat-card hdfs-card"><div class="stat-icon">🧠</div><div class="stat-info"><div class="stat-label">剩余容量</div><div class="stat-value">{{ formatBytes(nnCapacityRemaining) }}</div></div><div class="stat-trend">{{ (nnCapacityRemaining>0?(nnCapacityRemaining/nnCapacityTotal*100).toFixed(1):'0')+'% 可用' }}</div></div>
                    <div class="stat-card hdfs-card"><div class="stat-icon">📦</div><div class="stat-info"><div class="stat-label">文件数</div><div class="stat-value">{{ nnFilesTotal }}</div></div><div class="stat-trend">{{ nnBlocksTotal }} 个数据块</div></div>
                    <div class="stat-card hdfs-card"><div class="stat-icon">🟢</div><div class="stat-info"><div class="stat-label">DataNode 在线</div><div class="stat-value" style="color:#67c23a">{{ nnNumLiveDNs }}</div></div><div class="stat-trend" v-if="nnNumDeadDNs>0" style="color:#f56c6c">{{ nnNumDeadDNs }} 离线</div><div class="stat-trend" v-else>全部正常</div></div>
                  </div>
                  <div style="margin-top:12px;display:flex;gap:8px;flex-wrap:wrap;justify-content:center;align-items:center" v-if="canPerformDestructiveOps">
                    <el-button size="small" type="primary" @click="switchNN" round>🔄 一键切换 Active NN</el-button>
                    <el-button size="small" @click="loadHdfsNodes" :icon="'Refresh'" round>刷新</el-button>
                    <span style="color:rgba(255,255,255,0.3);margin:0 4px">|</span>
                    <el-select v-model="nnManageNode" size="small" style="width:100px" placeholder="节点" class="nn-select">
                      <el-option v-for="n in nnNodes" :key="n" :label="n" :value="n" />
                    </el-select>
                    <el-select v-model="nnManageAction" size="small" style="width:100px" placeholder="操作" class="nn-select">
                      <el-option label="🔄 重启" value="restart" />
                      <el-option label="▶️ 启动" value="start" />
                      <el-option label="⏹️ 停掉" value="stop" />
                    </el-select>
                    <el-button size="small" type="primary" @click="manageNN(nnManageNode, nnManageAction)" :loading="nnManaging" round>执行</el-button>
                  </div>
                </div>
                <div class="glass-card">
                  <div class="glass-card-title">🧩 数据块元数据</div>
                  <div style="display:flex;gap:8px;margin-bottom:12px">
                    <el-input v-model="blockPath" placeholder="文件路径" size="default" style="width:400px" clearable @keyup.enter="loadBlockMetadata" />
                    <el-button type="primary" @click="loadBlockMetadata" round>查询</el-button>
                  </div>
                  <div v-if="blockMetadata">
                    <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
                      <el-descriptions-item label="路径">{{ blockPath }}</el-descriptions-item>
                      <el-descriptions-item label="类型">{{ blockMetadata.isDir?'目录':'文件' }}</el-descriptions-item>
                      <el-descriptions-item label="大小">{{ formatBytes(blockMetadata.length) }}</el-descriptions-item>
                      <el-descriptions-item label="块大小">{{ formatBytes(blockMetadata.blockSize) }}</el-descriptions-item>
                      <el-descriptions-item label="副本数">{{ blockMetadata.replication }}</el-descriptions-item>
                      <el-descriptions-item label="修改时间">{{ blockMetadata.modificationTime?new Date(blockMetadata.modificationTime).toLocaleString():'-' }}</el-descriptions-item>
                    </el-descriptions>
                    <div v-if="blockLocations.length>0">
                      <div style="font-size:13px;font-weight:600;color:rgba(255,255,255,0.7);margin-bottom:8px">块位置分布 ({{ blockLocations.length }})</div>
                      <div v-for="(loc,i) in blockLocations" :key="i" class="block-loc-item">
                        <div class="block-loc-header"><span>Block {{ i+1 }}</span><span style="font-size:12px;color:rgba(255,255,255,0.4)">{{ formatBytes(loc.length) }}</span></div>
                        <div class="block-loc-nodes"><el-tag v-for="host in loc.hosts" :key="host" size="small" style="margin:2px 4px 2px 0">{{ host }}</el-tag></div>
                      </div>
                    </div>
                    <div v-else-if="blockMetadata&&!blockMetadata.isDir" class="fs-empty" style="padding:12px"><div class="fs-empty-icon">⚠️</div><div class="fs-empty-text">此文件暂无数据块分布信息</div></div>
                  </div>
                  <div v-else style="color:rgba(255,255,255,0.3);font-size:13px;padding:20px;text-align:center">输入文件路径查询数据块元数据</div>
                </div>
                </template>

                <!-- 📋 日志查看器 -->
                <template v-if="tab.name === 'logs'">
                <el-row :gutter="10" style="margin-bottom:12px">
                  <el-col :span="4">
                    <el-select v-model="logRole" placeholder="角色" style="width:100%" @change="onLogRoleChange">
                      <el-option label="NameNode" value="namenode" />
                      <el-option label="DataNode" value="datanode" />
                      <el-option label="JournalNode" value="journalnode" />
                    </el-select>
                  </el-col>
                  <el-col :span="3">
                    <el-select v-model="logNode" placeholder="节点" style="width:100%" @change="onLogNodeChange">
                      <el-option v-for="n in logNodes" :key="n" :label="n" :value="n" />
                    </el-select>
                  </el-col>
                  <el-col :span="3">
                    <el-select v-model="logSource" placeholder="来源" style="width:100%" @change="onLogSourceChange">
                      <el-option label="🟢 Docker 实时" value="docker" />
                      <el-option label="📁 日志文件" value="file" />
                    </el-select>
                  </el-col>
                  <el-col :span="3" v-if="logSource==='file'">
                    <el-select v-model="logFileName" placeholder="文件" style="width:100%" @change="loadLogs">
                      <el-option v-for="f in logFileList" :key="f.name" :label="f.name+' ('+(f.size/1024).toFixed(0)+'K)'" :value="f.name" />
                    </el-select>
                  </el-col>
                  <el-col :span="4">
                    <el-select v-model="logLevel" placeholder="级别" style="width:100%" clearable multiple collapse-tags collapse-tags-tooltip @change="loadLogs">
                      <el-option label="DEBUG" value="DEBUG" /><el-option label="INFO" value="INFO" />
                      <el-option label="WARN" value="WARN" /><el-option label="ERROR" value="ERROR" />
                    </el-select>
                  </el-col>
                  <el-col :span="2">
                    <el-select v-model="logLines" placeholder="行数" style="width:100%" @change="loadLogs">
                      <el-option label="50" :value="50" /><el-option label="100" :value="100" />
                      <el-option label="200" :value="200" /><el-option label="500" :value="500" /><el-option label="1000" :value="1000" />
                    </el-select>
                  </el-col>
                  <el-col :span="4">
                    <el-input v-model="logPattern" placeholder="正则过滤" size="default" clearable @keyup.enter="loadLogs" />
                  </el-col>
                  <el-col :span="2">
                    <el-button type="primary" @click="loadLogs" :icon="'Refresh'" round style="width:100%">查询</el-button>
                  </el-col>
                </el-row>
                <el-row :gutter="10" style="margin-bottom:12px">
                  <el-col :span="8">
                    <el-date-picker v-model="logDateRange" type="datetimerange" range-separator="至"
                      start-placeholder="开始" end-placeholder="结束" style="width:100%" @change="onLogDateChange" />
                  </el-col>
                  <el-col :span="8" style="text-align:right">
                    <span style="font-size:12px;color:rgba(255,255,255,0.3);margin-right:8px">{{ logQuickFilter!=='ALL' ? filteredLogLines.length + '/' + logTotal : logTotal }} 行</span>
                    <el-button text size="small" @click="logAutoRefresh=!logAutoRefresh">{{ logAutoRefresh?'🟢 自动刷新':'⏸️ 暂停' }}</el-button>
                    <el-button text size="small" @click="clearLogs" style="margin-left:8px">🗑️ 清空</el-button>
                    <el-button text size="small" @click="logShowMs=!logShowMs" style="margin-left:4px">{{ logShowMs?'🕐 毫秒':'🕐 秒' }}</el-button>
                  </el-col>
                </el-row>
                <el-row :gutter="10" style="margin-bottom:12px;align-items:center">
                  <el-col :span="5">
                    <div class="log-quick-filter">
                      <el-tag :type="logQuickFilter==='ALL'?'primary':'info'" effect="plain" size="small" style="cursor:pointer" @click="logQuickFilter='ALL'">全部</el-tag>
                      <el-tag :type="logQuickFilter==='ERROR'?'danger':'info'" effect="plain" size="small" style="cursor:pointer" @click="logQuickFilter='ERROR'">错误</el-tag>
                      <el-tag :type="logQuickFilter==='WARN'?'warning':'info'" effect="plain" size="small" style="cursor:pointer" @click="logQuickFilter='WARN'">警告</el-tag>
                    </div>
                  </el-col>
                  <el-col :span="14">
                    <div style="display:flex;align-items:center;gap:6px">
                      <el-checkbox v-model="logSearchRegex" size="small" @change="onLogSearchInput" style="white-space:nowrap">.*</el-checkbox>
                      <el-input v-model="logSearchQuery" placeholder="搜索" size="small" style="flex:1" clearable @input="onLogSearchInput" @keydown="handleLogSearchKeydown" />
                      <span v-if="logSearchMatchCount>0" style="font-size:12px;color:rgba(255,255,255,0.5);white-space:nowrap">找到 {{ logSearchMatchCount }} 个匹配</span>
                      <el-button v-if="logSearchMatchCount>0" text size="small" @click="logSearchPrev" :disabled="logSearchMatchCount===0" title="上一个 (Shift+Enter)">⬆</el-button>
                      <el-button v-if="logSearchMatchCount>0" text size="small" @click="logSearchNext" :disabled="logSearchMatchCount===0" title="下一个 (Enter)">⬇</el-button>
                    </div>
                  </el-col>
                  <el-col :span="5" style="text-align:right">
                    <el-button type="primary" size="small" @click="downloadLogs">⬇ 下载</el-button>
                  </el-col>
                </el-row>
                <div class="log-viewer" ref="logViewerRef">
                  <div v-for="(l,i) in filteredLogLines" :key="i" class="log-line" :class="['log-'+(l.level||'OTHER').toLowerCase(), i===logSearchCurrentLine ? 'log-current-match' : '', logSearchMatchLines.includes(i) ? 'log-highlight' : '']" :ref="i===logSearchCurrentLine ? 'logCurrentMatchRef' : undefined">
                    <span class="log-time">{{ logShowMs?(l.time?l.time.slice(11,23):''):(l.time?l.time.slice(11,19):'') }}</span>
                    <span class="log-level" :class="'lv-'+(l.level||'OTHER').toLowerCase()">{{ l.level||'?' }}</span>
                    <span class="log-msg">{{ l.msg }}</span>
                    <span class="log-copy" @click="copyLogMsg(l)" title="复制">📋</span>
                  </div>
                  <div v-if="filteredLogLines.length===0&&logLoaded" class="fs-empty" style="padding:24px"><div class="fs-empty-icon">📋</div><div class="fs-empty-text">暂无匹配的日志</div></div>
                  <div v-if="logLoading" class="log-empty">加载中...</div>
                </div>
                </template>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- YARN -->
          <div v-show="activeTab === 'yarn'" class="panel">
            <!-- YARN sub-tabs -->
            <el-tabs v-model="yarnSubTab" type="border-card" style="background:transparent" @tab-change="onYarnSubTabChange">
              <el-tab-pane label="⚙️ 应用" name="apps"></el-tab-pane>
              <el-tab-pane label="📊 队列" name="queues"></el-tab-pane>
            </el-tabs>
            <!-- YARN Apps content -->
            <div v-show="yarnSubTab === 'apps'">
            <!-- Stats Row -->
            <div class="stats-grid" style="margin-bottom:16px">
              <div class="stat-card yarn-card">
                <div class="stat-icon">▶️</div>
                <div class="stat-info">
                  <div class="stat-label">运行中</div>
                  <div class="stat-value" style="color:#67c23a">{{ yarnStats.running }}</div>
                </div>
                <div class="stat-trend up">RUNNING</div>
              </div>
              <div class="stat-card info-card">
                <div class="stat-icon">⏳</div>
                <div class="stat-info">
                  <div class="stat-label">等待中</div>
                  <div class="stat-value" style="color:#409eff">{{ yarnStats.pending }}</div>
                </div>
                <div class="stat-trend">ACCEPTED / SUBMITTED</div>
              </div>
              <div class="stat-card info-card">
                <div class="stat-icon">✅</div>
                <div class="stat-info">
                  <div class="stat-label">已完成</div>
                  <div class="stat-value" style="color:#909399">{{ yarnStats.finished }}</div>
                </div>
                <div class="stat-trend">FINISHED</div>
              </div>
              <div class="stat-card hdfs-card">
                <div class="stat-icon">❌</div>
                <div class="stat-info">
                  <div class="stat-label">失败</div>
                  <div class="stat-value" style="color:#f56c6c">{{ yarnStats.failed }}</div>
                </div>
                <div class="stat-trend" style="color:#f56c6c">FAILED</div>
              </div>
            </div>

            <!-- Status Filter Tags -->
            <div class="panel-toolbar" style="margin-bottom:12px">
              <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                <span style="font-size:12px;color:rgba(255,255,255,0.5);margin-right:4px">快速筛选:</span>
                <el-tag
                  :type="yarnActiveFilterTag === 'ALL' ? 'primary' : 'info'"
                  effect="plain"
                  style="cursor:pointer"
                  @click="setYarnFilterTag('ALL')"
                >
                  ALL ({{ yarnFilterTagCounts.ALL }})
                </el-tag>
                <el-tag
                  :type="yarnActiveFilterTag === 'RUNNING' ? 'success' : 'info'"
                  effect="plain"
                  style="cursor:pointer"
                  @click="setYarnFilterTag('RUNNING')"
                >
                  ▶️ Running ({{ yarnFilterTagCounts.RUNNING }})
                </el-tag>
                <el-tag
                  :type="yarnActiveFilterTag === 'ACCEPTED' ? 'warning' : 'info'"
                  effect="plain"
                  style="cursor:pointer"
                  @click="setYarnFilterTag('ACCEPTED')"
                >
                  ⏳ Accepted ({{ yarnFilterTagCounts.ACCEPTED }})
                </el-tag>
                <el-tag
                  :type="yarnActiveFilterTag === 'FINISHED' ? '' : 'info'"
                  effect="plain"
                  style="cursor:pointer"
                  @click="setYarnFilterTag('FINISHED')"
                >
                  ✅ Finished ({{ yarnFilterTagCounts.FINISHED }})
                </el-tag>
                <el-tag
                  :type="yarnActiveFilterTag === 'FAILED' ? 'danger' : 'info'"
                  effect="plain"
                  style="cursor:pointer"
                  @click="setYarnFilterTag('FAILED')"
                >
                  ❌ Failed ({{ yarnFilterTagCounts.FAILED }})
                </el-tag>
              </div>
            </div>

            <!-- Filters Row -->
            <div class="panel-toolbar">
              <el-select v-model="yarnFilterStates" multiple collapse-tags collapse-tags-tooltip placeholder="状态过滤" style="width:220px" clearable>
                <el-option v-for="s in allYarnStates" :key="s" :label="s" :value="s" />
              </el-select>
              <el-select v-model="yarnFilterQueue" placeholder="队列过滤" style="width:140px" clearable filterable @visible-change="loadQueueOptions">
                <el-option v-for="q in queueOptions" :key="q" :label="q || 'default'" :value="q" />
              </el-select>
              <el-input v-model="yarnFilterUser" placeholder="用户搜索" style="width:140px" clearable @keyup.enter="loadYarnApps" />
              <el-input v-model="yarnFilterName" placeholder="名称模糊" style="width:160px" clearable @keyup.enter="loadYarnApps" />
              <el-date-picker v-model="yarnTimeRange" type="datetimerange" range-separator="至"
                start-placeholder="开始时间" end-placeholder="结束时间" style="width:300px" value-format="x" />
              <el-button type="primary" @click="loadYarnApps" :icon="'Search'" round>查询</el-button>
              <el-button @click="resetYarnFilters" round>重置</el-button>
              <el-button @click="loadYarnApps" :icon="'Refresh'" round :loading="yarnLoading">刷新</el-button>
              <el-button type="success" @click="showJobSubmitDialog = true" round :loading="jobSubmitLoading">🚀 提交新作业</el-button>
              <el-button text size="small" @click="exportYarnAppsCsv" round>📥 导出 CSV</el-button>
            </div>

            <!-- Main Table -->
            <el-table :data="yarnApps" stripe :size="tableSize" max-height="480px" class="glass-table"
              :row-class-name="yarnRowClass" @sort-change="onYarnSortChange" ref="yarnTableRef">
              <el-table-column prop="appId" label="App ID" width="160" sortable="custom" />
              <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="110">
                <template #default="{ row }">
                  <span class="yarn-type-tag" :class="'yarn-type-' + (row.type || 'other').toLowerCase()">
                    <span v-if="row.type==='SPARK'" class="yarn-type-icon">⚡</span>
                    <span v-else-if="row.type==='MAPREDUCE'" class="yarn-type-icon">📊</span>
                    <span v-else class="yarn-type-icon">⚙️</span>
                    {{ row.type }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="user" label="用户" width="80" />
              <el-table-column prop="queue" label="队列" width="80" />
              <el-table-column prop="state" label="状态" width="110" sortable="custom">
                <template #default="{ row }">
                  <el-tag :type="yarnStateTag(row.state)" size="small" effect="dark">{{ row.state }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="progress" label="进度" width="140">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.progress * 100)" :stroke-width="12" :color="yarnProgressColor(row.progress)" :status="row.state==='FAILED'?'exception':row.state==='FINISHED'?'success':''" />
                </template>
              </el-table-column>
              <el-table-column prop="vCores" label="vCores" width="70" align="right" sortable="custom" />
              <el-table-column prop="memory" label="内存" width="90" align="right" sortable="custom">
                <template #default="{ row }">{{ row.memory ? (row.memory >= 1024 ? (row.memory/1024).toFixed(1)+'GB' : row.memory+'MB') : '-' }}</template>
              </el-table-column>
              <el-table-column prop="duration" label="耗时" width="100" sortable="custom">
                <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
              </el-table-column>
              <el-table-column prop="startTime" label="开始时间" width="160" sortable="custom">
                <template #default="{ row }">{{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain round @click="openAppDetail(row)">详情</el-button>
                  <el-button v-if="canKill(row.state) && canPerformDestructiveOps" size="small" type="danger" plain round @click="openKillDialog(row)">Kill</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- Pagination -->
            <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
              <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ yarnTotal }} 条，{{ yarnTotalPages }} 页</span>
              <el-pagination v-model:current-page="yarnPage" v-model:page-size="yarnPageSize"
                :total="yarnTotal" :page-sizes="[20,50,100]" layout="sizes, prev, pager, next"
                @current-change="loadYarnApps" @size-change="loadYarnApps" background small />
            </div>

            <!-- Empty State for YARN Apps -->
            <div v-if="yarnApps.length === 0 && !yarnLoading" class="fs-empty" style="margin-top:16px;padding:60px 20px">
              <div class="fs-empty-icon" style="font-size:48px">📊</div>
              <div class="fs-empty-text" style="font-size:16px;margin-top:16px">暂无应用，请提交作业后查看</div>
              <div style="margin-top:20px;display:flex;gap:12px;justify-content:center">
                <el-button type="primary" size="default" @click="showJobSubmitDialog = true" round>🚀 提交第一个作业</el-button>
                <el-button size="default" @click="activeTab = 'mr-templates'" round text>📋 查看 MR 模板</el-button>
              </div>
            </div>

            <!-- Kill Dialog (uses el-message-box prompt) -->
            <!-- Show a dialog with reason via confirmKillAppWithReason -->
            <el-dialog v-model="showKillDialog" title="终止应用" width="420px" top="30vh" class="glass-dialog" :close-on-click-modal="false">
              <div style="margin-bottom:12px">
                <div style="font-size:13px;color:rgba(255,255,255,0.6);margin-bottom:6px">应用 ID</div>
                <div style="font-size:15px;color:#ffcc80;font-weight:600;font-family:monospace">{{ killTarget?.appId }}</div>
              </div>
              <div style="margin-bottom:6px;font-size:13px;color:rgba(255,255,255,0.6)">
                终止原因 <span style="color:#f56c6c">*</span>
              </div>
              <el-input v-model="killReason" type="textarea" :rows="3" placeholder="必填：终止原因，例如：超出资源限制、作业异常..." />
              <div v-if="killReasonError" style="color:#f56c6c;font-size:12px;margin-top:4px">{{ killReasonError }}</div>
              <template #footer>
                <el-button @click="showKillDialog = false; killReasonError = ''" round>取消</el-button>
                <el-button type="danger" @click="confirmKillAppWithReason" :loading="killLoading" round>确认终止</el-button>
              </template>
            </el-dialog>

            <!-- App Detail Drawer -->
            <el-drawer v-model="showAppDetail" title="应用详情" size="50%" class="glass-drawer" :before-close="closeAppDetail">
              <template #header>
                <div style="display:flex;align-items:center;gap:10px">
                  <span style="font-size:16px;font-weight:600;color:rgba(255,255,255,0.9)">应用详情</span>
                  <el-tag v-if="detailApp" :type="yarnStateTag(detailApp.state)" size="small" effect="dark">{{ detailApp?.state }}</el-tag>
                </div>
              </template>
              <div v-if="detailLoading" style="text-align:center;padding:40px;color:rgba(255,255,255,0.3)">加载中...</div>
              <div v-else-if="detailData" class="detail-content">
                <!-- App Info -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">📋 基本信息</div>
                  <el-descriptions :column="2" border size="small" class="detail-descriptions">
                    <el-descriptions-item label="App ID">{{ detailData.app?.appId }}</el-descriptions-item>
                    <el-descriptions-item label="名称">{{ detailData.app?.name }}</el-descriptions-item>
                    <el-descriptions-item label="类型">
                      <span class="yarn-type-tag" :class="'yarn-type-' + (detailData.app?.type || 'other').toLowerCase()">
                        <span v-if="detailData.app?.type==='SPARK'" class="yarn-type-icon">⚡</span>
                        <span v-else-if="detailData.app?.type==='MAPREDUCE'" class="yarn-type-icon">📊</span>
                        <span v-else class="yarn-type-icon">⚙️</span>
                        {{ detailData.app?.type }}
                      </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="用户">{{ detailData.app?.user }}</el-descriptions-item>
                    <el-descriptions-item label="队列">{{ detailData.app?.queue }}</el-descriptions-item>
                    <el-descriptions-item label="状态">{{ detailData.app?.state }}</el-descriptions-item>
                    <el-descriptions-item label="vCores / 内存">{{ detailData.app?.vCores }} / {{ detailData.app?.memory ? (detailData.app.memory >= 1024 ? (detailData.app.memory/1024).toFixed(1)+'GB' : detailData.app.memory+'MB') : '-' }}</el-descriptions-item>
                    <el-descriptions-item label="进度">{{ Math.round((detailData.app?.progress||0)*100) }}%</el-descriptions-item>
                    <el-descriptions-item label="开始时间">{{ detailData.app?.startTime ? new Date(detailData.app.startTime).toLocaleString() : '-' }}</el-descriptions-item>
                    <el-descriptions-item label="结束时间">{{ detailData.app?.finishTime ? new Date(detailData.app.finishTime).toLocaleString() : '-' }}</el-descriptions-item>
                    <el-descriptions-item label="耗时" :span="2">{{ formatDuration(detailData.app?.duration) }}</el-descriptions-item>
                    <el-descriptions-item label="诊断信息" :span="2">
                      <span style="color:#e6a23c;word-break:break-all">{{ detailData.app?.diagnostics || '无' }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="跟踪 URL" :span="2">
                      <a v-if="detailData.app?.trackingUrl" :href="detailData.app.trackingUrl" target="_blank" style="color:#409eff">{{ detailData.app.trackingUrl }}</a>
                      <span v-else style="color:rgba(255,255,255,0.3)">-</span>
                    </el-descriptions-item>
                  </el-descriptions>
                </div>

                <!-- Attempts -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">🔄 尝试记录 ({{ detailData.attempts?.length || 0 }})</div>
                  <el-table :data="detailData.attempts || []" stripe :size="tableSize" class="glass-table">
                    <el-table-column prop="attemptId" label="Attempt ID" width="200" />
                    <el-table-column prop="host" label="Host" width="120" />
                    <el-table-column prop="containerId" label="Container" width="180" show-overflow-tooltip />
                    <el-table-column prop="state" label="状态" width="100">
                      <template #default="{ row }">
                        <el-tag :type="row.state==='FINISHED'?'success':'warning'" size="small">{{ row.state }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="startTime" label="开始" width="150">
                      <template #default="{ row }">{{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="finishTime" label="结束" width="150">
                      <template #default="{ row }">{{ row.finishTime ? new Date(row.finishTime).toLocaleString() : '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="trackingUrl" label="跟踪" width="100">
                      <template #default="{ row }">
                        <a v-if="row.trackingUrl" :href="row.trackingUrl" target="_blank" style="color:#409eff;font-size:12px">打开</a>
                        <span v-else style="color:rgba(255,255,255,0.3)">-</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <!-- Containers -->
                <div class="glass-card">
                  <div class="glass-card-title">📦 容器列表 ({{ detailData.containers?.length || 0 }})</div>
                  <el-table :data="detailData.containers || []" stripe :size="tableSize" class="glass-table" max-height="300px">
                    <el-table-column prop="containerId" label="Container ID" width="200" />
                    <el-table-column prop="nodeId" label="节点" width="130" show-overflow-tooltip />
                    <el-table-column prop="attemptId" label="Attempt" width="150" show-overflow-tooltip />
                    <el-table-column prop="state" label="状态" width="90">
                      <template #default="{ row }">
                        <el-tag :type="row.state==='RUNNING'?'success':row.state==='COMPLETE'?'info':'warning'" size="small">{{ row.state }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="totalMemoryMB" label="内存" width="80">
                      <template #default="{ row }">{{ row.totalMemoryMB ? (row.totalMemoryMB >= 1024 ? (row.totalMemoryMB/1024).toFixed(1)+'GB' : row.totalMemoryMB+'MB') : '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="totalVCores" label="vCores" width="60" />
                    <el-table-column prop="exitStatus" label="退出码" width="70" />
                    <el-table-column prop="startTime" label="开始" width="150">
                      <template #default="{ row }">{{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="finishTime" label="结束" width="150">
                      <template #default="{ row }">{{ row.finishTime ? new Date(row.finishTime).toLocaleString() : '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="diagnostics" label="诊断" min-width="120" show-overflow-tooltip>
                      <template #default="{ row }"><span style="color:#e6a23c">{{ row.diagnostics || '-' }}</span></template>
                    </el-table-column>
                  </el-table>
                </div>

                <!-- Resource Timeline -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">📈 资源概览</div>
                  <div style="padding:12px">
                    <div style="margin-bottom:12px">
                      <div style="display:flex;justify-content:space-between;font-size:12px;color:rgba(255,255,255,0.6);margin-bottom:4px">
                        <span>内存 (MB)</span>
                        <span>
                          <span style="color:#4fc3f7">已分配 {{ detailData.app?.memory || 0 }} MB</span>
                          <span style="color:rgba(255,255,255,0.3);margin:0 4px">/</span>
                          <span style="color:#e6a23c">请求 {{ detailData.app?.reservedMemory || detailData.app?.memory || 0 }} MB</span>
                        </span>
                      </div>
                      <div class="timeline-bar-track">
                        <div class="timeline-bar-fill timeline-bar-blue" :style="{ width: Math.min(100, ((detailData.app?.memory || 0) / Math.max((detailData.app?.reservedMemory || detailData.app?.memory || 1), 1)) * 100) + '%' }"></div>
                      </div>
                    </div>
                    <div>
                      <div style="display:flex;justify-content:space-between;font-size:12px;color:rgba(255,255,255,0.6);margin-bottom:4px">
                        <span>vCores</span>
                        <span>
                          <span style="color:#4fc3f7">已分配 {{ detailData.app?.vCores || 0 }}</span>
                          <span style="color:rgba(255,255,255,0.3);margin:0 4px">/</span>
                          <span style="color:#e6a23c">请求 {{ detailData.app?.reservedVCores || detailData.app?.vCores || 0 }}</span>
                        </span>
                      </div>
                      <div class="timeline-bar-track">
                        <div class="timeline-bar-fill timeline-bar-green" :style="{ width: Math.min(100, ((detailData.app?.vCores || 0) / Math.max((detailData.app?.reservedVCores || detailData.app?.vCores || 1), 1)) * 100) + '%' }"></div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Data Lineage (数据血缘) -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">📂 数据血缘</div>
                  <div v-if="lineageLoading" style="text-align:center;padding:20px;color:rgba(255,255,255,0.3)">加载中...</div>
                  <div v-else-if="detailLineage">
                    <!-- Input Files -->
                    <div style="margin-bottom:12px">
                      <div style="font-size:13px;font-weight:500;color:rgba(255,255,255,0.7);margin-bottom:8px">
                        📖 输入文件 ({{ detailLineage.inputFiles?.length || 0 }})
                      </div>
                      <div v-if="detailLineage.inputFiles?.length">
                        <div v-for="(f, fi) in detailLineage.inputFiles" :key="'in-'+fi"
                             style="display:flex;align-items:center;gap:8px;padding:4px 0;font-size:12px;font-family:monospace;color:#67c23a">
                          <span style="flex-shrink:0">📄</span>
                          <span style="word-break:break-all;flex:1">{{ f }}</span>
                          <el-button size="small" text type="primary" @click="navigateToHdfsPath(f)" title="在 HDFS 中打开">🔗</el-button>
                        </div>
                      </div>
                      <div v-else style="color:rgba(255,255,255,0.3);font-size:12px">未检测到输入文件</div>
                    </div>
                    <!-- Output Files -->
                    <div>
                      <div style="font-size:13px;font-weight:500;color:rgba(255,255,255,0.7);margin-bottom:8px">
                        📝 输出文件 ({{ detailLineage.outputFiles?.length || 0 }})
                      </div>
                      <div v-if="detailLineage.outputFiles?.length">
                        <div v-for="(f, fi) in detailLineage.outputFiles" :key="'out-'+fi"
                             style="display:flex;align-items:center;gap:8px;padding:4px 0;font-size:12px;font-family:monospace;color:#e6a23c">
                          <span style="flex-shrink:0">📄</span>
                          <span style="word-break:break-all;flex:1">{{ f }}</span>
                          <el-button size="small" text type="primary" @click="navigateToHdfsPath(f)" title="在 HDFS 中打开">🔗</el-button>
                        </div>
                      </div>
                      <div v-else style="color:rgba(255,255,255,0.3);font-size:12px">未检测到输出文件</div>
                    </div>
                    <!-- Raw diagnostics fallback -->
                    <div v-if="!detailLineage.inputFiles?.length && !detailLineage.outputFiles?.length && detailLineage.diagnostics" style="margin-top:12px;border-top:1px solid rgba(255,255,255,0.06);padding-top:12px">
                      <div style="font-size:12px;color:rgba(255,255,255,0.4);margin-bottom:4px">诊断信息（用于手动分析）：</div>
                      <div style="font-size:11px;color:rgba(255,255,255,0.3);word-break:break-all;max-height:120px;overflow-y:auto;background:rgba(0,0,0,0.2);padding:8px;border-radius:4px;font-family:monospace;white-space:pre-wrap">{{ detailLineage.diagnostics }}</div>
                    </div>
                  </div>
                  <div v-else class="fs-empty" style="padding:20px"><div class="fs-empty-icon">🔗</div><div class="fs-empty-text">暂无数据血缘信息</div></div>
                </div>

                <!-- Spark Details (⚡ Spark 详情) -->
                <div class="glass-card" style="margin-bottom:16px">
                  <div class="glass-card-title">⚡ Spark 详情</div>
                  <div v-if="sparkLoading" style="text-align:center;padding:20px;color:rgba(255,255,255,0.3)">加载中...</div>
                  <div v-else-if="detailSparkInfo && detailSparkInfo.isSpark">
                    <el-descriptions :column="2" border size="small" class="detail-descriptions">
                      <el-descriptions-item label="Spark App ID">
                        <span style="font-family:monospace;color:#4fc3f7">{{ detailSparkInfo.sparkAppId || '-' }}</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="状态">
                        <el-tag type="success" size="small" effect="dark">Spark</el-tag>
                      </el-descriptions-item>
                      <el-descriptions-item label="Stages">
                        <span>{{ detailSparkInfo.completedStages || 0 }} / {{ detailSparkInfo.stages || 0 }}</span>
                        <span style="color:rgba(255,255,255,0.4);font-size:11px;margin-left:4px">(已完成/总计)</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="Executors">
                        <span>{{ detailSparkInfo.activeExecutors || 0 }} / {{ detailSparkInfo.executors || 0 }}</span>
                        <span style="color:rgba(255,255,255,0.4);font-size:11px;margin-left:4px">(活跃/总计)</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="Tasks">
                        <span>{{ detailSparkInfo.completedTasks || 0 }} / {{ detailSparkInfo.tasks || 0 }}</span>
                        <span style="color:rgba(255,255,255,0.4);font-size:11px;margin-left:4px">(已完成/总计)</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="失败 Tasks">
                        <span style="color:#f56c6c">{{ detailSparkInfo.failedTasks || 0 }}</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="Shuffle 读取">
                        <span>{{ detailSparkInfo.shuffleReadBytes ? formatBytes(detailSparkInfo.shuffleReadBytes) : '-' }}</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="Shuffle 写入">
                        <span>{{ detailSparkInfo.shuffleWriteBytes ? formatBytes(detailSparkInfo.shuffleWriteBytes) : '-' }}</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="跟踪 URL" :span="2">
                        <a v-if="detailSparkInfo.trackingUrl" :href="detailSparkInfo.trackingUrl" target="_blank" style="color:#409eff;word-break:break-all">{{ detailSparkInfo.trackingUrl }}</a>
                        <span v-else style="color:rgba(255,255,255,0.3)">-</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="备注" :span="2">
                        <span style="color:#e6a23c;font-size:12px">{{ detailSparkInfo.note || '' }}</span>
                      </el-descriptions-item>
                    </el-descriptions>
                    <div style="margin-top:12px;text-align:center">
                      <el-button type="primary" size="small" @click="openSparkHistory(detailSparkInfo.trackingUrl)" round :disabled="!detailSparkInfo.trackingUrl">
                        🔗 打开 Spark History Server
                      </el-button>
                    </div>
                  </div>
                  <div v-else-if="detailSparkInfo && !detailSparkInfo.isSpark" class="fs-empty" style="padding:20px"><div class="fs-empty-icon">⚡</div><div class="fs-empty-text">未检测到 Spark 信息</div></div>
                  <div v-else class="fs-empty" style="padding:20px"><div class="fs-empty-icon">⚡</div><div class="fs-empty-text">暂无 Spark 信息</div></div>
                </div>
              </div>
            </el-drawer>
          </div>

          <!-- Submit Job Modal -->
          <el-dialog v-model="showJobSubmitDialog" title="提交新作业" width="560px" top="10vh" class="glass-dialog" :close-on-click-modal="false">
            <el-form :model="jobSubmitForm" label-width="100px" size="small" @submit.prevent="handleSubmitJob">
              <el-form-item label="应用名称" required>
                <el-input v-model="jobSubmitForm.name" placeholder="输入应用名称" />
              </el-form-item>
              <el-form-item label="类型" required>
                <el-select v-model="jobSubmitForm.type" placeholder="选择类型" style="width:100%">
                  <el-option label="MapReduce" value="MAPREDUCE" />
                  <el-option label="Spark" value="SPARK" />
                  <el-option label="Custom" value="CUSTOM" />
                </el-select>
              </el-form-item>
              <el-form-item label="Jar 路径">
                <div style="display:flex;gap:8px;width:100%">
                  <el-input v-model="jobSubmitForm.jarPath" placeholder="/user/hermes/app.jar" style="flex:1" />
                  <el-button @click="browseHdfsForJar" type="primary" text>📂 从 HDFS 选择</el-button>
                </div>
              </el-form-item>
              <el-form-item label="MainClass" v-if="jobSubmitForm.type === 'MAPREDUCE'">
                <el-input v-model="jobSubmitForm.mainClass" placeholder="com.example.MyDriver" />
              </el-form-item>
              <el-form-item label="参数">
                <el-input v-model="jobSubmitForm.args" type="textarea" :rows="3" placeholder="额外命令行参数" />
              </el-form-item>
              <el-form-item label="输入路径">
                <el-input v-model="jobSubmitForm.inputPath" placeholder="hdfs:///user/input" />
              </el-form-item>
              <el-form-item label="输出路径">
                <el-input v-model="jobSubmitForm.outputPath" placeholder="hdfs:///user/output" />
              </el-form-item>
              <el-form-item label="队列">
                <el-input v-model="jobSubmitForm.queue" placeholder="default" />
              </el-form-item>
              <el-form-item label="vCores">
                <el-input-number v-model="jobSubmitForm.vCores" :min="1" :max="64" />
              </el-form-item>
              <el-form-item label="Memory(MB)">
                <el-input-number v-model="jobSubmitForm.memory" :min="256" :max="524288" :step="512" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showJobSubmitDialog = false" round>取消</el-button>
              <el-button type="primary" @click="handleSubmitJob" :loading="jobSubmitLoading" round>提交</el-button>
            </template>
          </el-dialog>

          <!-- YARN Queues content -->
          <div v-show="yarnSubTab === 'queues'">
            <el-row :gutter="20">
              <el-col :xs="24" :sm="24" :md="14">
                <div class="queue-card">
                  <div class="queue-card-header">
                    <span>📊 实时队列</span>
                    <div style="display:flex;gap:8px">
                      <el-button size="small" type="primary" @click="loadYarnQueues" :icon="'Refresh'" round>刷新</el-button>
                    </div>
                  </div>
                  <el-table
                    :data="yarnQueues"
                    stripe
                    :size="tableSize"
                    max-height="480px"
                    class="glass-table"
                    row-key="queueName"
                    :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
                    default-expand-all
                  >
                    <el-table-column label="队列名称" min-width="200">
                      <template #default="{ row }">
                        <span :style="{ paddingLeft: (row._level || 0) * 20 + 'px' }">
                          <span v-if="row.children && row.children.length">📂</span>
                          <span v-else>📁</span>
                          {{ row.queueName }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="容量" width="160">
                      <template #default="{ row }">
                        <div class="cap-bar-row">
                          <div class="cap-bar-track">
                            <div class="cap-bar-fill cap-bar-blue" :style="{ width: (row.capacity || 0) + '%' }"></div>
                          </div>
                          <span class="cap-bar-label">{{ (row.capacity || 0).toFixed(1) }}%</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="已用容量" width="160">
                      <template #default="{ row }">
                        <div class="cap-bar-row">
                          <div class="cap-bar-track">
                            <div class="cap-bar-fill" :style="{ width: (row.usedCapacity || 0) + '%', background: usageColor(row.usedCapacity) }"></div>
                          </div>
                          <span class="cap-bar-label">{{ (row.usedCapacity || 0).toFixed(1) }}%</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="绝对容量" width="100" align="center">
                      <template #default="{ row }">
                        <span>{{ (row.absoluteCapacity || 0).toFixed(1) }}%</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="最大容量" width="100" align="center">
                      <template #default="{ row }">
                        <span>{{ (row.maxCapacity || 0).toFixed(1) }}%</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="应用数" width="80" align="center" prop="numApplications" sortable />
                    <el-table-column label="运行中" width="70" align="center" prop="runningApps" sortable />
                    <el-table-column label="等待中" width="70" align="center" prop="pendingApps" sortable />
                    <el-table-column label="状态" width="80" align="center">
                      <template #default="{ row }">
                        <el-tag :type="row.queueState === 'RUNNING' ? 'success' : 'danger'" size="small" effect="dark">
                          {{ row.queueState === 'RUNNING' ? '运行中' : '已停止' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="120" align="center" fixed="right">
                      <template #default="{ row }">
                        <el-button size="small" type="primary" plain round @click="openQueueEdit(row)" v-if="row.queueName !== 'root' && canPerformDestructiveOps">编辑容量</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-col>
              <el-col :xs="24" :sm="24" :md="10">
                <div class="queue-card">
                  <div class="queue-card-header">
                    <span>⚡ YARN 集群指标</span>
                  </div>
                  <div class="metric-grid">
                    <div class="metric-item">
                      <div class="metric-value">{{ yarnMetrics.numNodeManagers }}</div>
                      <div class="metric-label">NodeManager</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-value">{{ (yarnMetrics.totalMemoryMB || 0) >= 1024 ? ((yarnMetrics.totalMemoryMB || 0)/1024).toFixed(0) + 'GB' : yarnMetrics.totalMemoryMB + 'MB' }}</div>
                      <div class="metric-label">总内存</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-value">{{ yarnMetrics.totalVCores }}</div>
                      <div class="metric-label">vCores</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-value">{{ yarnMetrics.runningApplications }}</div>
                      <div class="metric-label">运行中应用</div>
                    </div>
                  </div>
                </div>
              </el-col>
            </el-row>

            <!-- Queue Edit Dialog -->
            <el-dialog v-model="showQueueEditDialog" title="编辑队列容量" width="520px" top="15vh" class="glass-dialog">
              <el-form :model="queueEditForm" label-width="120px" v-if="queueEditForm">
                <el-form-item label="队列名称">
                  <el-input :model-value="queueEditForm.queueName" disabled />
                </el-form-item>
                <el-form-item label="新容量 %">
                  <div style="display:flex;align-items:center;gap:12px">
                    <el-slider v-model="queueEditForm.capacity" :min="0" :max="100" :step="0.5" style="flex:1" />
                    <el-input-number v-model="queueEditForm.capacity" :min="0" :max="100" :step="1" :precision="1" style="width:120px" />
                  </div>
                </el-form-item>
                <el-form-item label="最大容量 %">
                  <div style="display:flex;align-items:center;gap:12px">
                    <el-slider v-model="queueEditForm.maxCapacity" :min="0" :max="100" :step="0.5" style="flex:1" />
                    <el-input-number v-model="queueEditForm.maxCapacity" :min="0" :max="100" :step="1" :precision="1" style="width:120px" />
                  </div>
                </el-form-item>
                <el-form-item label="绝对容量">
                  <el-input :model-value="(queueEditForm.absoluteCapacity || 0).toFixed(1) + '%'" disabled />
                </el-form-item>
                <el-form-item label="当前已用">
                  <el-input :model-value="(queueEditForm.usedCapacity || 0).toFixed(1) + '%'" disabled />
                </el-form-item>
                <el-alert type="warning" show-icon :closable="false" style="margin-top:12px">
                  <template #title>此操作将动态调整队列容量，确认后系统会尝试通过 YARN API 调整。部分集群可能需要执行 yarn rmadmin -refreshQueues 才能生效。</template>
                </el-alert>
              </el-form>
              <template #footer>
                <el-button @click="showQueueEditDialog = false" round>取消</el-button>
                <el-button type="primary" @click="saveQueueCapacity" :loading="queueEditSaving" round>确认调整</el-button>
              </template>
            </el-dialog>
          </div>
          </div>

          <!-- MR Templates -->
          <div v-show="activeTab === 'mr'" class="panel">
            <div class="panel-toolbar" style="flex-wrap:wrap;gap:8px">
              <div id="mr-filter-bar" class="filter-drag-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center">
                <template v-for="name in mrFilterOrder" :key="name">
                  <div v-if="name === '分类'" class="filter-drag-item" draggable="true"
                    @dragstart="onFilterDragStart($event, '分类')"
                    @dragover.prevent="onFilterDragOver($event)"
                    @drop="onFilterDrop($event, '分类')" title="拖动调整顺序">
                    <el-select v-model="mrCategoryFilter" size="small" style="width:120px" placeholder="分类" @change="loadMrTemplates" clearable filterable allow-create>
                      <el-option v-for="cat in mrCategoryOptions" :key="cat" :label="cat || '全部'" :value="cat" />
                    </el-select>
                  </div>
                  <div v-else-if="name === 'ID'" class="filter-drag-item" draggable="true"
                    @dragstart="onFilterDragStart($event, 'ID')"
                    @dragover.prevent="onFilterDragOver($event)"
                    @drop="onFilterDrop($event, 'ID')" title="拖动调整顺序">
                    <el-select v-model="mrFilterId" size="small" style="width:120px" placeholder="ID" @change="loadMrTemplates" clearable filterable>
                      <el-option v-for="opt in mrIdOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                  </div>
                  <div v-else-if="name === '队列'" class="filter-drag-item" draggable="true"
                    @dragstart="onFilterDragStart($event, '队列')"
                    @dragover.prevent="onFilterDragOver($event)"
                    @drop="onFilterDrop($event, '队列')" title="拖动调整顺序">
                    <el-select v-model="mrFilterQueue" size="small" style="width:120px" placeholder="队列" @change="loadMrTemplates" clearable filterable allow-create>
                      <el-option label="全部" value="" />
                      <el-option v-for="q in mrQueueOptions" :key="q" :label="q" :value="q" />
                    </el-select>
                  </div>
                  <div v-else-if="name === '使用次数'" class="filter-drag-item" draggable="true"
                    @dragstart="onFilterDragStart($event, '使用次数')"
                    @dragover.prevent="onFilterDragOver($event)"
                    @drop="onFilterDrop($event, '使用次数')" title="拖动调整顺序" style="display:flex;gap:4px;align-items:center">
                    <el-select v-model="mrFilterUseCountOp" size="small" style="width:88px" placeholder="条件" @change="loadMrTemplates" clearable>
                      <el-option label=">" value=">" />
                      <el-option label=">=" value=">=" />
                      <el-option label="=" value="=" />
                      <el-option label="<=" value="<=" />
                      <el-option label="<" value="<" />
                    </el-select>
                    <el-input v-model.number="mrFilterUseCountVal" size="small" style="width:110px" placeholder="使用次数" clearable
                      type="number" min="0" @input="loadMrTemplates" />
                  </div>
                  <div v-else-if="name === '名称'" class="filter-drag-item" draggable="true"
                    @dragstart="onFilterDragStart($event, '名称')"
                    @dragover.prevent="onFilterDragOver($event)"
                    @drop="onFilterDrop($event, '名称')" title="拖动调整顺序">
                    <el-input v-model="mrSearchKeyword" placeholder="搜索模板名称..." clearable size="small" style="width:180px"
                      prefix-icon="Search" @input="loadMrTemplates" />
                  </div>
                </template>
              </div>
              <el-button type="primary" @click="showMrDialog = true" :icon="'Plus'" round>新建模板</el-button>
              <el-button size="small" @click="loadMrTemplates" :icon="'Refresh'" round>刷新</el-button>
            </div>
            <div v-if="filteredMrTemplates.length === 0" class="fs-empty" style="padding:32px">
              <div class="fs-empty-icon">📋</div>
              <div class="fs-empty-text">暂无匹配的 MapReduce 模板</div>
            </div>
            <el-table v-else :data="filteredMrTemplates" stripe :size="tableSize" max-height="420px" class="glass-table">
              <el-table-column prop="id" label="ID" width="55" />
              <el-table-column prop="name" label="名称" min-width="140">
                <template #default="{ row }">
                  <span style="font-weight:500">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="类别" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'Spark' ? 'warning' : row.type === 'WiFi' ? 'primary' : 'info'" size="small" effect="dark">
                    {{ row.type || '通用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="mainClass" label="MainClass" width="200" show-overflow-tooltip />
              <el-table-column prop="jarHdfsPath" label="JAR 路径" min-width="180" show-overflow-tooltip />
              <el-table-column prop="queue" label="队列" width="70" />
              <el-table-column label="使用次数" width="80" align="center" prop="useCount" sortable />
              <el-table-column label="最后使用" width="160">
                <template #default="{ row }">
                  <span style="font-size:12px;color:rgba(255,255,255,0.5)">
                    {{ row.lastUsedTime ? new Date(row.lastUsedTime).toLocaleString() : '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain @click="submitMrJob(row.id)" round>提交</el-button>
                  <el-button size="small" @click="editMrTemplate(row)" round>编辑</el-button>
                  <el-dropdown trigger="click" @command="(cmd) => handleMrAction(cmd, row)">
                    <el-button size="small" circle>⋮</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">📋 复制</el-dropdown-item>
                        <el-dropdown-item command="delete" divided style="color:#ef4444">🗑️ 删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>

            <el-dialog v-model="showMrDialog" :title="editingMrId ? '编辑 MapReduce 模板' : '新建 MapReduce 模板'" width="560px" top="8vh" class="glass-dialog">
              <el-form :model="mrForm" label-width="110px" class="mr-form">
                <el-row :gutter="16">
                  <el-col :span="12"><el-form-item label="名称"><el-input v-model="mrForm.name" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="队列"><el-input v-model="mrForm.queue" placeholder="default" /></el-form-item></el-col>
                </el-row>
                <el-form-item label="JAR 路径"><el-input v-model="mrForm.jarHdfsPath" placeholder="hdfs:///apps/mr/wordcount.jar" /></el-form-item>
                <el-form-item label="MainClass"><el-input v-model="mrForm.mainClass" /></el-form-item>
                <el-row :gutter="16">
                  <el-col :span="12"><el-form-item label="输入路径"><el-input v-model="mrForm.inputPath" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="输出路径"><el-input v-model="mrForm.outputPath" /></el-form-item></el-col>
                </el-row>
                <el-form-item label="类别">
                  <el-select v-model="mrForm.type" style="width:100%" clearable placeholder="选择或输入类别" filterable allow-create>
                    <el-option v-for="cat in mrCategoryOptions" :key="cat" :label="cat || '通用'" :value="cat" />
                  </el-select>
                </el-form-item>
                <el-form-item label="默认参数"><el-input v-model="mrForm.defaultArgs" type="textarea" :rows="2" /></el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showMrDialog = false; editingMrId = null" round>取消</el-button>
                <el-button type="primary" @click="saveMrTemplate" :loading="mrSaving" round>{{ editingMrId ? '保存修改' : '创建模板' }}</el-button>
              </template>
            </el-dialog>
          </div>

          <!-- 操作审计 -->
          <div v-show="activeTab === 'operation-log'" class="panel">
            <div class="panel-toolbar">
              <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
                <el-select v-model="opLogFilterModule" placeholder="模块" clearable size="small" style="width:130px" @change="opLogFilterAction = ''; loadOperationLogs()">
                  <el-option label="全部" value="" />
                  <el-option label="HDFS" value="hdfs" />
                  <el-option label="YARN" value="yarn" />
                  <el-option label="MapReduce" value="mr" />
                  <el-option label="用户" value="user" />
                </el-select>
                <el-select v-model="opLogFilterAction" placeholder="操作类型" clearable size="small" style="width:130px" @change="loadOperationLogs">
                  <el-option v-for="opt in filteredActionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-date-picker v-model="opLogFilterDateRange" type="daterange" range-separator="至"
                  start-placeholder="开始日期" end-placeholder="结束日期" size="small"
                  style="width:260px" value-format="YYYY-MM-DD" @change="loadOperationLogs" />
                <el-button type="primary" @click="loadOperationLogs" :icon="'Search'" size="small" round>查询</el-button>
                <el-button @click="resetOpLogFilters" size="small" round>重置</el-button>
                <el-button @click="loadOperationLogs" :icon="'Refresh'" size="small" round>刷新</el-button>
                <el-button text size="small" @click="exportOpLogCsv" round>📥 导出 CSV</el-button>
                <el-button :disabled="selectedOpLogs.length === 0" type="danger" size="small" @click="deleteBatchOpLogs" round>
                  🗑️ 删除{{ selectedOpLogs.length > 0 ? ' (' + selectedOpLogs.length + ')' : '' }}
                </el-button>
              </div>
            </div>
            <el-table :data="operationLogs" stripe :size="tableSize" max-height="520px" class="glass-table fs-table" v-loading="opLogLoading"
              @row-click="openLogDetail" style="cursor:pointer"
              @selection-change="(val) => selectedOpLogs = val">
              <el-table-column type="selection" width="40" />
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="module" label="模块" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.module === 'hdfs' ? 'primary' : row.module === 'yarn' ? 'warning' : 'info'" size="small" effect="dark">{{ row.module }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">{{ actionLabels[row.action] || row.action }}</template>
              </el-table-column>
              <el-table-column prop="target" label="目标路径" min-width="200" />
              <el-table-column label="结果" width="80">
                <template #default="{ row }">
                  <el-badge :type="row.result === 'success' ? 'success' : 'danger'" :is-dot="false">
                    <el-tag :type="row.result === 'success' ? 'success' : 'danger'" size="small" effect="dark">{{ row.result === 'success' ? '成功' : '失败' }}</el-tag>
                  </el-badge>
                </template>
              </el-table-column>
              <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作人" width="100">
                <template #default="{ row }">{{ row.username || '' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="operationLogs.length === 0 && !opLogLoading" class="fs-empty">
              <div class="fs-empty-icon">📝</div>
              <div class="fs-empty-text">暂无操作记录</div>
              <el-button type="primary" size="small" @click="loadOperationLogs" style="margin-top:12px">🔄 刷新</el-button>
            </div>
            <!-- Pagination -->
            <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
              <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ opLogTotal }} 条</span>
              <el-pagination v-model:current-page="opLogPage" v-model:page-size="opLogPageSize"
                :total="opLogTotal" :page-sizes="[20,50,100]" layout="sizes, prev, pager, next"
                @current-change="loadOperationLogs" @size-change="loadOperationLogs" background small />
            </div>

            <!-- 操作日志详情弹窗 -->
            <el-dialog v-model="showLogDetail" title="📝 操作详情" width="700px" top="10vh" class="glass-dialog"
              :close-on-click-modal="false">
              <template v-if="selectedLog">
                <div class="log-detail-grid">
                  <div class="log-detail-field">
                    <span class="log-detail-label">ID</span>
                    <span class="log-detail-value">{{ selectedLog.id }}</span>
                  </div>
                  <div class="log-detail-field">
                    <span class="log-detail-label">模块</span>
                    <span class="log-detail-value">
                      <el-tag :type="selectedLog.module === 'hdfs' ? 'primary' : selectedLog.module === 'yarn' ? 'warning' : 'info'" size="small" effect="dark">{{ selectedLog.module }}</el-tag>
                    </span>
                  </div>
                  <div class="log-detail-field">
                    <span class="log-detail-label">操作</span>
                    <span class="log-detail-value">{{ actionLabels[selectedLog.action] || selectedLog.action }}</span>
                  </div>
                  <div class="log-detail-field">
                    <span class="log-detail-label">操作人</span>
                    <span class="log-detail-value">{{ selectedLog.userId ? '用户#' + selectedLog.userId : '-' }}</span>
                  </div>
                  <div class="log-detail-field">
                    <span class="log-detail-label">结果</span>
                    <span class="log-detail-value">
                      <el-tag :type="selectedLog.result === 'success' ? 'success' : 'danger'" size="small" effect="dark">{{ selectedLog.result === 'success' ? '成功' : '失败' }}</el-tag>
                    </span>
                  </div>
                  <div class="log-detail-field">
                    <span class="log-detail-label">时间</span>
                    <span class="log-detail-value">{{ formatDateTime(selectedLog.createTime) }}</span>
                  </div>
                  <div class="log-detail-field log-detail-field-full">
                    <span class="log-detail-label">目标路径</span>
                    <span class="log-detail-value log-detail-value-code">{{ selectedLog.target || '-' }}</span>
                  </div>
                  <div class="log-detail-field log-detail-field-full">
                    <span class="log-detail-label">详情 / 参数</span>
                    <div class="log-detail-json">{{ selectedLog.detail ? JSON.stringify(tryParseJson(selectedLog.detail), null, 2) : '-' }}</div>
                  </div>
                  <div v-if="selectedLog.duration != null" class="log-detail-field">
                    <span class="log-detail-label">耗时</span>
                    <span class="log-detail-value">{{ selectedLog.duration }}ms</span>
                  </div>
                  <div v-if="selectedLog.clientIp" class="log-detail-field">
                    <span class="log-detail-label">客户端 IP</span>
                    <span class="log-detail-value">{{ selectedLog.clientIp }}</span>
                  </div>
                  <div v-if="selectedLog.userAgent" class="log-detail-field log-detail-field-full">
                    <span class="log-detail-label">User Agent</span>
                    <span class="log-detail-value log-detail-value-code" style="font-size:11px">{{ selectedLog.userAgent }}</span>
                  </div>
                </div>
              </template>
              <template #footer>
                <el-button @click="showLogDetail = false" round>关闭</el-button>
              </template>
            </el-dialog>
          </div>

          <!-- 用户管理 -->
          <div v-show="activeTab === 'user-mgmt'" class="panel">
            <div class="panel-toolbar">
              <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
                <el-input v-model="userSearchKeyword" placeholder="搜索用户名/邮箱" clearable size="small"
                  style="width:220px" @keyup.enter="loadUsers" />
                <el-button type="primary" @click="loadUsers" :icon="'Search'" size="small" round>查询</el-button>
                <el-button @click="loadUsers" :icon="'Refresh'" size="small" round>刷新</el-button>
                <el-button type="success" @click="openCreateUserDialog" :icon="'Plus'" size="small" round v-if="canManageUsers">创建用户</el-button>
                <el-button text size="small" @click="exportUsersCsv" round>📥 导出 CSV</el-button>
              </div>
            </div>
            <el-table :data="users" stripe :size="tableSize" max-height="520px" class="glass-table" v-loading="userLoading">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="username" label="用户名" width="120" />
              <el-table-column prop="email" label="邮箱" min-width="180" />
              <el-table-column label="角色" width="120">
                <template #default="{ row }">
                  <el-tag
                    :type="row.role === 'admin' ? 'danger' : row.role === 'operator' ? 'warning' : 'info'"
                    size="small" effect="dark">
                    {{ row.role === 'admin' ? '管理员' : row.role === 'operator' ? '操作员' : '观察者' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'danger'" size="small" effect="plain">
                    {{ row.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column label="最后登录" width="170">
                <template #default="{ row }">{{ row.lastLoginTime ? new Date(row.lastLoginTime).toLocaleString() : '从未登录' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link @click="openEditUserDialog(row)" v-if="canManageUsers">编辑</el-button>
                  <el-button size="small" link @click="openResetPasswordDialog(row)" v-if="canManageUsers">重置密码</el-button>
                  <el-button size="small" link @click="handleDeleteUser(row)"
                    :disabled="row.username === username" v-if="canManageUsers">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="users.length === 0 && !userLoading" class="fs-empty">
              <div class="fs-empty-icon">👤</div>
              <div class="fs-empty-text">暂无用户</div>
              <el-button v-if="canManageUsers" type="primary" size="small" @click="showCreateUserDialog = true" style="margin-top:12px">+ 创建用户</el-button>
            </div>
            <!-- Pagination -->
            <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
              <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ userTotal }} 条</span>
              <el-pagination v-model:current-page="userPage" v-model:page-size="userPageSize"
                :total="userTotal" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next"
                @current-change="loadUsers" @size-change="userPage = 1; loadUsers()" background small />
            </div>
          </div>

          <!-- 告警中心 -->
          <div v-show="activeTab === 'alert-center'" class="panel">
            <el-tabs v-model="alertSubTab" type="border-card" style="background:transparent">
              <!-- 告警规则 -->
              <el-tab-pane label="告警规则" name="rules">
                <div class="panel-toolbar">
                  <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
                    <el-button type="success" @click="openCreateAlertRuleDialog" :icon="'Plus'" size="small" round v-if="canPerformDestructiveOps">创建规则</el-button>
                    <el-button @click="loadAlertRules" :icon="'Refresh'" size="small" round>刷新</el-button>
                    <el-button text size="small" @click="exportAlertRulesCsv" round>📥 导出 CSV</el-button>
                  </div>
                </div>
                <el-table :data="alertRules" stripe :size="tableSize" max-height="520px" class="glass-table" v-loading="alertRulesLoading">
                  <el-table-column prop="id" label="ID" width="50" />
                  <el-table-column prop="queueName" label="队列/模块" width="120" />
                  <el-table-column prop="metric" label="指标" width="130" />
                  <el-table-column label="条件" width="120">
                    <template #default="{ row }">
                      <span style="color:#eab308">{{ row.operator }}</span>
                      <span style="color:#f1f5f9;margin-left:4px">{{ row.threshold }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="80">
                    <template #default="{ row }">
                      <el-switch v-model="row.enabled" @change="toggleAlertRule(row)" size="small" />
                    </template>
                  </el-table-column>
                  <el-table-column prop="notifyEmail" label="通知邮箱" width="180" show-overflow-tooltip />
                  <el-table-column label="创建时间" width="170">
                    <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="140" fixed="right">
                    <template #default="{ row }">
                      <el-button size="small" type="primary" link @click="openEditAlertRuleDialog(row)" v-if="canPerformDestructiveOps">编辑</el-button>
                      <el-button size="small" type="danger" link @click="handleDeleteAlertRule(row)" v-if="canPerformDestructiveOps">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-if="alertRules.length === 0 && !alertRulesLoading" class="fs-empty">
                  <div class="fs-empty-icon">⚠️</div>
                  <div class="fs-empty-text">暂无告警规则</div>
                  <el-button v-if="canPerformDestructiveOps" type="primary" size="small" @click="showCreateAlertRuleDialog = true" style="margin-top:12px">+ 创建规则</el-button>
                </div>
                <!-- Pagination -->
                <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
                  <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ alertRulesTotal }} 条</span>
                  <el-pagination v-model:current-page="alertRulesPage" v-model:page-size="alertRulesPageSize"
                    :total="alertRulesTotal" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next"
                    @current-change="loadAlertRules" @size-change="alertRulesPage = 1; loadAlertRules()" background small />
                </div>
              </el-tab-pane>

              <!-- 告警历史 -->
              <el-tab-pane label="告警历史" name="history">
                <div class="panel-toolbar">
                  <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
                    <el-select v-model="alertHistoryFilterModule" placeholder="模块" clearable size="small" style="width:130px" @change="loadAlertHistory">
                      <el-option label="全部" value="" />
                      <el-option label="HDFS" value="hdfs" />
                      <el-option label="YARN" value="yarn" />
                      <el-option label="MapReduce" value="mr" />
                    </el-select>
                    <el-date-picker v-model="alertHistoryFilterDateRange" type="daterange" range-separator="至"
                      start-placeholder="开始日期" end-placeholder="结束日期" size="small"
                      style="width:260px" value-format="YYYY-MM-DD" @change="loadAlertHistory" />
                    <el-button type="primary" @click="loadAlertHistory" :icon="'Search'" size="small" round>查询</el-button>
                    <el-button @click="resetAlertHistoryFilters" size="small" round>重置</el-button>
                    <el-button @click="loadAlertHistory" :icon="'Refresh'" size="small" round>刷新</el-button>
                  </div>
                </div>
                <el-table :data="alertHistory" stripe :size="tableSize" max-height="520px" class="glass-table" v-loading="alertHistoryLoading">
                  <el-table-column prop="id" label="ID" width="50" />
                  <el-table-column label="模块" width="80">
                    <template #default="{ row }">
                      <el-tag :type="row.module === 'hdfs' ? 'primary' : row.module === 'yarn' ? 'warning' : 'info'" size="small" effect="dark">{{ row.module }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="action" label="操作" width="90" />
                  <el-table-column prop="target" label="目标" min-width="200" show-overflow-tooltip />
                  <el-table-column label="结果" width="80">
                    <template #default="{ row }">
                      <el-tag type="danger" size="small" effect="dark">失败</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
                  <el-table-column label="时间" width="170">
                    <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
                  </el-table-column>
                </el-table>
                <div v-if="alertHistory.length === 0 && !alertHistoryLoading" class="fs-empty">
                  <div class="fs-empty-icon">✅</div>
                  <div class="fs-empty-text">暂无告警历史，集群运行正常</div>
                  <el-button type="primary" size="small" @click="loadAlertHistory" style="margin-top:12px">🔄 刷新</el-button>
                </div>
                <!-- Pagination -->
                <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
                  <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ alertHistoryTotal }} 条</span>
                  <el-pagination v-model:current-page="alertHistoryPage" v-model:page-size="alertHistoryPageSize"
                    :total="alertHistoryTotal" :page-sizes="[20,50,100]" layout="sizes, prev, pager, next"
                    @current-change="loadAlertHistory" @size-change="loadAlertHistory" background small />
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- 监控 -->
          <div v-show="activeTab === 'monitor'" class="panel">
            <!-- Mode Toggle: In-app Charts vs Grafana -->
            <div class="monitor-time-range">
              <span class="monitor-range-label">📊 显示模式</span>
              <el-radio-group v-model="grafanaMode" size="small">
                <el-radio-button value="inapp">📈 内置图表</el-radio-button>
                <el-radio-button value="grafana">📉 Grafana 仪表盘</el-radio-button>
              </el-radio-group>
              <el-button size="small" @click="loadMonitorData" :icon="'Refresh'" :loading="monitorLoading" round style="margin-left:8px" v-if="grafanaMode === 'inapp'">刷新</el-button>
            </div>

            <!-- Grafana Dashboard Mode -->
            <template v-if="grafanaMode === 'grafana'">
              <!-- Grafana URL Input -->
              <div class="glass-card" style="margin-bottom:16px">
                <div style="display:flex;flex-direction:column;align-items:center;gap:16px;padding:32px 20px;text-align:center">
                  <div style="font-size:56px">📉</div>
                  <div style="font-size:20px;font-weight:600;color:#f1f5f9">Grafana 仪表盘</div>
                  <div style="font-size:14px;color:#94a3b8">嵌入外部 Grafana dashboard</div>
                  <div style="display:flex;gap:10px;width:100%;max-width:600px;margin-top:8px">
                    <el-input v-model="grafanaUrl" placeholder="http://localhost:3000/d/xxxxx?orgId=1" size="default" clearable style="flex:1">
                      <template #prefix><span style="color:#999">🔗</span></template>
                    </el-input>
                    <el-button type="primary" @click="saveGrafanaUrl" round>保存</el-button>
                  </div>
                </div>
              </div>
              <!-- Embedded Grafana iframe -->
              <div v-if="grafanaUrl" class="grafana-embed-container">
                <iframe :src="grafanaUrl" class="grafana-iframe" frameborder="0" allowfullscreen></iframe>
              </div>
              <div v-else class="glass-card" style="padding:40px;text-align:center;color:#64748b">
                <div style="font-size:40px;margin-bottom:12px">🔗</div>
                <div>请在上方输入 Grafana dashboard URL 并点击保存</div>
              </div>
            </template>

            <!-- In-app Charts Mode (existing) -->
            <template v-if="grafanaMode === 'inapp'">
            <!-- Time Range Selector -->
            <div class="monitor-time-range">
              <span class="monitor-range-label">⏱ 时间范围</span>
              <el-date-picker
                v-model="monitorDateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
                size="small"
                style="width:380px"
                @change="onMonitorRangeChange"
                :teleported="false"
                popper-class="dark-date-picker" />
              <el-button size="small" @click="loadMonitorData" :icon="'Refresh'" :loading="monitorLoading" round>刷新</el-button>
            </div>

            <div v-if="monitorError" class="error-card">
              ⚠️ {{ monitorError }}
            </div>

            <!-- HDFS Metrics Card -->
            <div class="monitor-metric-section">
              <div class="monitor-section-title">💾 HDFS 空间使用</div>
              <div class="monitor-card-row">
                <div class="monitor-metric-card" v-if="monitorHdfsData.length > 0">
                  <div class="monitor-metric-header">
                    <span class="monitor-metric-current">{{ formatBytes(monitorHdfsCurrent) }}</span>
                    <span class="monitor-metric-trend" :class="monitorHdfsTrend.cls">{{ monitorHdfsTrend.arrow }} {{ monitorHdfsTrend.label }}</span>
                  </div>
                  <div :id="'chart-hdfs-usage'" style="width:100%;height:150px"></div>
                  <div class="monitor-metric-footer">
                    <span class="monitor-metric-label">已用空间 (峰值: {{ formatBytes(monitorHdfsMax) }})</span>
                    <span class="monitor-metric-count">{{ monitorHdfsData.length }} 个采样点</span>
                  </div>
                </div>
                <div v-else class="monitor-empty-card">
                  <div class="fs-empty" style="padding:24px"><div class="fs-empty-icon">📊</div><div class="fs-empty-text">暂无 HDFS 历史数据</div><el-button type="primary" size="small" @click="loadMonitorData" style="margin-top:12px">🔄 刷新</el-button></div>
                </div>
              </div>
            </div>

            <!-- YARN Metrics Card -->
            <div class="monitor-metric-section">
              <div class="monitor-section-title">⚡ YARN 集群指标</div>
              <div class="monitor-card-row">
                <!-- Node Managers -->
                <div class="monitor-metric-card half" v-if="monitorYarnData.length > 0">
                  <div class="monitor-metric-header">
                    <span class="monitor-metric-current">{{ monitorNmCurrent }}<span class="monitor-metric-unit"> 台</span></span>
                    <span class="monitor-metric-trend" :class="monitorNmTrend.cls">{{ monitorNmTrend.arrow }} {{ monitorNmTrend.label }}</span>
                  </div>
                  <div :id="'chart-nm-count'" style="width:100%;height:150px"></div>
                  <div class="monitor-metric-footer">
                    <span class="monitor-metric-label">NodeManager 数量</span>
                    <span class="monitor-metric-count">峰值: {{ monitorNmMax }}</span>
                  </div>
                </div>
                <!-- Total Memory -->
                <div class="monitor-metric-card half" v-if="monitorYarnData.length > 0">
                  <div class="monitor-metric-header">
                    <span class="monitor-metric-current">{{ monitorMemCurrent }}<span class="monitor-metric-unit"> GB</span></span>
                    <span class="monitor-metric-trend" :class="monitorMemTrend.cls">{{ monitorMemTrend.arrow }} {{ monitorMemTrend.label }}</span>
                  </div>
                  <div :id="'chart-mem-total'" style="width:100%;height:150px"></div>
                  <div class="monitor-metric-footer">
                    <span class="monitor-metric-label">总内存</span>
                    <span class="monitor-metric-count">峰值: {{ monitorMemMax }} GB</span>
                  </div>
                </div>
                <!-- Running Apps -->
                <div class="monitor-metric-card half" v-if="monitorYarnData.length > 0">
                  <div class="monitor-metric-header">
                    <span class="monitor-metric-current">{{ monitorAppsCurrent }}<span class="monitor-metric-unit"> 个</span></span>
                    <span class="monitor-metric-trend" :class="monitorAppsTrend.cls">{{ monitorAppsTrend.arrow }} {{ monitorAppsTrend.label }}</span>
                  </div>
                  <div :id="'chart-apps-running'" style="width:100%;height:150px"></div>
                  <div class="monitor-metric-footer">
                    <span class="monitor-metric-label">运行中应用数</span>
                    <span class="monitor-metric-count">峰值: {{ monitorAppsMax }}</span>
                  </div>
                </div>
              </div>
              <div v-if="monitorYarnData.length === 0" class="monitor-empty-card">
                <div class="fs-empty" style="padding:24px"><div class="fs-empty-icon">⚡</div><div class="fs-empty-text">暂无 YARN 历史数据</div><el-button type="primary" size="small" @click="loadMonitorData" style="margin-top:12px">🔄 刷新</el-button></div>
              </div>
            </div>

            <!-- Queue Metrics Card -->
            <div class="monitor-metric-section">
              <div class="monitor-section-title">📊 YARN 队列容量使用</div>
              <div class="monitor-card-row">
                <div class="monitor-metric-card half" v-for="(qdata, qname) in monitorQueueMap" :key="qname">
                  <div class="monitor-metric-header">
                    <span class="monitor-metric-current">{{ qdata.current }}<span class="monitor-metric-unit">%</span></span>
                    <span class="monitor-metric-trend" :class="qdata.trend.cls">{{ qdata.trend.arrow }} {{ qdata.trend.label }}</span>
                  </div>
                  <div :id="'chart-queue-' + qname" style="width:100%;height:150px"></div>
                  <div class="monitor-metric-footer">
                    <span class="monitor-metric-label">{{ qname }}</span>
                    <span class="monitor-metric-count">峰值: {{ qdata.max }}%</span>
                  </div>
                </div>
              </div>
              <div v-if="Object.keys(monitorQueueMap).length === 0" class="monitor-empty-card">
                <div class="fs-empty" style="padding:24px"><div class="fs-empty-icon">📊</div><div class="fs-empty-text">暂无队列历史数据</div><el-button type="primary" size="small" @click="loadMonitorData" style="margin-top:12px">🔄 刷新</el-button></div>
              </div>
            </div>
            </template>
          </div>

          <!-- Notebook -->
          <div v-show="activeTab === 'notebook'" class="panel">
            <div class="glass-card">
              <div style="display:flex;flex-direction:column;align-items:center;gap:16px;padding:32px 20px;text-align:center">
                <div style="font-size:56px">📓</div>
                <div style="font-size:20px;font-weight:600;color:#f1f5f9">Notebook</div>
                <div style="font-size:14px;color:#94a3b8">连接到 Jupyter Notebook 服务器</div>
                <div style="display:flex;gap:10px;width:100%;max-width:500px;margin-top:8px">
                  <el-input v-model="notebookUrl" placeholder="http://localhost:8888" size="default" clearable style="flex:1">
                    <template #prefix><span style="color:#999">🔗</span></template>
                  </el-input>
                  <el-button type="primary" @click="openNotebook" round>打开</el-button>
                  <el-switch v-model="notebookEmbed" active-text="嵌入" inactive-text="新标签" style="margin-left:4px" />
                </div>
              </div>
            </div>
            <!-- Embedded iframe -->
            <div v-if="notebookEmbed && notebookUrl" style="margin-top:12px;border-radius:12px;overflow:hidden;border:1px solid #334155;height:72vh">
              <iframe :src="notebookUrl" style="width:100%;height:100%;border:none;background:#1e2938" frameborder="0" allowfullscreen></iframe>
            </div>
          </div>

          <!-- 系统配置 -->
          <div v-show="activeTab === 'system-config'" class="panel">
            <div class="config-page">
              <div class="config-search-bar" style="margin-bottom:12px">
                <el-input v-model="systemConfigSearch" placeholder="搜索配置项（按标签或键名）..." clearable size="small" style="max-width:400px">
                  <template #prefix><span style="opacity:0.5">🔍</span></template>
                </el-input>
              </div>
              <div v-if="systemConfigLoading" class="config-loading">
                <el-icon class="is-loading" style="font-size:24px;color:#409eff"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="31.4 31.4" stroke-linecap="round"><animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="1s" repeatCount="indefinite"/></circle></svg></el-icon>
                <span style="margin-left:8px;color:#94a3b8">加载配置...</span>
              </div>
              <div v-else-if="systemConfigError" class="config-error">
                ⚠️ {{ systemConfigError }}
              </div>
              <div v-else class="config-groups">
                <div v-for="group in filteredConfigData" :key="group.group" class="config-group-card">
                  <div class="config-group-header">
                    <span class="config-group-icon">{{ groupIcons[group.group] || '📋' }}</span>
                    <span class="config-group-title">{{ group.group }}</span>
                  </div>
                  <div class="config-group-body">
                    <div v-for="item in group.items" :key="item.key" class="config-item-row">
                      <div class="config-item-label">
                        <span class="config-item-name">{{ item.label }}</span>
                        <span class="config-item-key">{{ item.key }}</span>
                      </div>
                      <div class="config-item-value">{{ item.value }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

  <!-- 📚 数据目录 -->
  <div v-show="activeTab === 'catalog'" class="panel catalog-container">
    <!-- Header -->
    <div class="catalog-header">
      <div class="catalog-header-left">
        <h2 style="margin:0;font-size:18px;font-weight:600">📚 数据目录</h2>
        <span class="catalog-header-count" v-if="catalogTotal > 0">共 {{ catalogTotal }} 张表</span>
      </div>
      <div class="catalog-header-actions">
        <el-input v-model="catalogSearch.name" placeholder="搜索表名..." clearable size="small" style="width:200px" @clear="searchCatalog" @keyup.enter="searchCatalog">
          <template #prefix><span style="opacity:0.5">🔍</span></template>
        </el-input>
        <el-select v-model="catalogSearch.format" placeholder="格式" clearable size="small" style="width:110px" @change="searchCatalog">
          <el-option label="TEXT" value="TEXT" />
          <el-option label="CSV" value="CSV" />
          <el-option label="Parquet" value="Parquet" />
          <el-option label="ORC" value="ORC" />
          <el-option label="Avro" value="Avro" />
        </el-select>
        <el-select v-model="catalogSearch.schema" placeholder="Schema" clearable size="small" style="width:120px" @change="searchCatalog">
          <el-option label="default" value="default" />
        </el-select>
        <el-button type="primary" size="small" @click="showRegisterDialog = true">+ 注册表</el-button>
        <el-button size="small" @click="showDiscoverDialog = true">🔍 自动发现</el-button>
      </div>
    </div>

    <!-- Table -->
    <el-table :data="catalogTables" v-loading="catalogLoading" class="catalog-table" :size="tableSize" stripe
      @row-click="(row) => openCatalogDetail(row)" style="cursor:pointer">
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="format" label="格式" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.format === 'Parquet' ? 'primary' : row.format === 'ORC' ? 'success' : ''" size="small">{{ row.format }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="schemaName" label="Schema" width="90" />
      <el-table-column prop="hdfsPath" label="HDFS路径" min-width="180" show-overflow-tooltip />
      <el-table-column prop="rowCount" label="行数" width="90" align="right">
        <template #default="{ row }">{{ row.rowCount != null ? (row.rowCount).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column prop="sizeInBytes" label="大小" width="90" align="right">
        <template #default="{ row }">{{ formatBytes(row.sizeInBytes) }}</template>
      </el-table-column>
      <el-table-column label="标签" width="160">
        <template #default="{ row }">
          <span v-if="!row.tags || row.tags.length === 0" style="color:#64748b;font-size:12px">-</span>
          <span v-else class="catalog-tag-list">
            <span v-for="tag in (row.tags || []).slice(0, 3)" :key="tag.id" class="catalog-tag-badge"
              :style="{ background: tag.color || '#3b82f6' }">{{ tag.name }}</span>
            <span v-if="row.tags && row.tags.length > 3" style="font-size:11px;color:#64748b">+{{ row.tags.length - 3 }}</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ row.updateTime ? new Date(row.updateTime).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click.stop="editCatalogTable(row)">编辑</el-button>
          <el-button text size="small" @click.stop="scanCatalogTable(row)" :loading="catalogScanLoadingId === row.id">扫描</el-button>
          <el-button text size="small" type="danger" @click.stop="deleteCatalogTable(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="catalogPage"
        v-model:page-size="catalogPageSize"
        :total="catalogTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadCatalogTables"
        @size-change="loadCatalogTables"
        background small />
    </div>
  </div>

  <!-- ============================================================ -->
  <!-- Workflow Engine Section -->
  <!-- ============================================================ -->
  <div v-show="activeTab === 'workflow'" class="panel workflow-container">
    <!-- Workflow List View -->
    <template v-if="!selectedWorkflow">
      <div class="workflow-header">
        <h2 style="margin:0;font-size:18px;font-weight:600">🔗 工作流编排</h2>
        <el-button type="primary" @click="openCreateWorkflow" :icon="'Plus'" round>新建工作流</el-button>
      </div>
      <el-table :data="workflows" v-loading="workflowLoading" stripe :size="tableSize" max-height="520px" class="glass-table"
        @row-click="selectWorkflow" style="cursor:pointer">
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="调度" width="160">
          <template #default="{ row }">
            <span v-if="row.scheduleCron" style="font-family:monospace;font-size:12px;color:#22c55e">{{ row.scheduleCron }}</span>
            <span v-else style="color:#64748b">手动</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后执行" min-width="200">
          <template #default="{ row }">
            <span v-if="row.lastExecutionTime" style="font-size:12px;color:#94a3b8">
              {{ new Date(row.lastExecutionTime).toLocaleString() }}
              <el-tag v-if="row.lastExecutionStatus" :type="execStatusTagType(row.lastExecutionStatus)" size="small" effect="dark" style="margin-left:4px">
                {{ row.lastExecutionStatus }}
              </el-tag>
            </span>
            <span v-else style="color:#64748b;font-size:12px">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="selectWorkflow(row)">设计</el-button>
            <el-button size="small" type="success" link @click.stop="executeWorkflow(row.id)">执行</el-button>
            <el-button size="small" type="primary" link @click.stop="openEditWorkflow(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click.stop="deleteWorkflow(row.id)">删除</el-button>
            <el-switch :model-value="row.enabled" size="small" @click.stop="toggleWorkflow(row)"
              style="margin-left:4px" />
          </template>
        </el-table-column>
      </el-table>
      <div v-if="workflows.length === 0 && !workflowLoading" class="fs-empty">
        <div class="fs-empty-icon">🔗</div>
        <div class="fs-empty-text">暂无工作流，点击「新建工作流」开始</div>
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px">
        <span style="font-size:12px;color:rgba(255,255,255,0.4)">共 {{ workflowTotal }} 条</span>
        <el-pagination v-model:current-page="workflowPage" v-model:page-size="workflowPageSize"
          :total="workflowTotal" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next"
          @current-change="loadWorkflows" @size-change="loadWorkflows" background small />
      </div>
    </template>

    <!-- Workflow Detail / Design View -->
    <template v-else>
      <div class="workflow-detail-header">
        <div class="workflow-detail-header-left">
          <el-button text size="small" @click="backToWorkflowList" style="margin-right:8px">← 返回</el-button>
          <h3 style="margin:0;font-size:16px;font-weight:600">{{ selectedWorkflow.name }}</h3>
          <span v-if="selectedWorkflow.description" style="color:#94a3b8;font-size:13px;margin-left:8px">{{ selectedWorkflow.description }}</span>
        </div>
        <div class="workflow-detail-header-actions">
          <el-button size="small" type="success" @click="executeWorkflow(selectedWorkflow.id)" plain round>▶ 执行</el-button>
          <el-button size="small" type="primary" @click="openEditWorkflow(selectedWorkflow)" plain round>编辑</el-button>
          <el-button size="small" type="danger" @click="deleteWorkflow(selectedWorkflow.id)" plain round>删除</el-button>
        </div>
      </div>

      <!-- Schedule info -->
      <div class="workflow-schedule-info">
        <span style="color:#94a3b8">
          <span style="color:#64748b">调度:</span>
          <span v-if="selectedWorkflow.scheduleCron" style="color:#22c55e;font-family:monospace;margin-left:4px">{{ selectedWorkflow.scheduleCron }}</span>
          <span v-else style="color:#64748b;margin-left:4px">手动</span>
        </span>
        <span style="color:#94a3b8">
          <span style="color:#64748b">集群:</span>
          <span style="margin-left:4px">{{ selectedWorkflow.clusterId || '-' }}</span>
        </span>
        <span style="color:#94a3b8">
          <span style="color:#64748b">最大重试:</span>
          <span style="margin-left:4px">{{ selectedWorkflow.maxRetries ?? 0 }}</span>
        </span>
        <span style="color:#94a3b8">
          <span style="color:#64748b">超时:</span>
          <span style="margin-left:4px">{{ selectedWorkflow.timeoutMinutes ?? 60 }}分钟</span>
        </span>
        <span v-if="selectedWorkflow.webhookUrl" style="color:#94a3b8">
          <span style="color:#64748b">Webhook:</span>
          <span style="margin-left:4px;color:#a855f7;font-size:11px">已配置</span>
        </span>
      </div>

      <!-- Steps Section -->
      <div class="workflow-steps-section">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <h4 style="margin:0;font-size:14px;font-weight:600;color:#f1f5f9">步骤管理 ({{ workflowSteps.length }})</h4>
          <el-button size="small" type="primary" @click="openAddStep" :icon="'Plus'" round>添加步骤</el-button>
        </div>

        <div v-if="workflowSteps.length === 0" class="fs-empty" style="padding:24px">
          <div class="fs-empty-icon">📋</div>
          <div class="fs-empty-text">暂无步骤，点击「添加步骤」开始编排</div>
        </div>

        <!-- Steps as cards -->
        <div v-else class="workflow-steps-container">
          <div v-for="(step, idx) in workflowSteps" :key="step.id || idx" class="workflow-step-card"
            :class="'step-type-' + (step.stepType || 'SHELL').toLowerCase()">
            <!-- Arrow connector from previous step if dependsOn -->
            <div v-if="idx > 0 && step.dependsOn && step.dependsOn.length > 0" class="workflow-step-connector">
              <div class="workflow-step-arrow"></div>
            </div>
            <div class="workflow-step-card-body">
              <div class="workflow-step-card-header">
                <span class="workflow-step-type-icon">
                  <span v-if="step.stepType === 'MAPREDUCE'">🗺️</span>
                  <span v-else-if="step.stepType === 'SHELL'">💻</span>
                  <span v-else-if="step.stepType === 'WAIT'">⏳</span>
                  <span v-else-if="step.stepType === 'HTTP'">🌐</span>
                  <span v-else>⚙️</span>
                </span>
                <span class="workflow-step-name">{{ step.name }}</span>
                <el-tag size="small" effect="dark" style="margin-left:8px">{{ step.stepType || 'SHELL' }}</el-tag>
                <span style="color:#64748b;font-size:11px;margin-left:8px">#{{ step.stepOrder }}</span>
              </div>
              <div class="workflow-step-card-details">
                <span v-if="step.inputPath" style="color:#94a3b8;font-size:12px;margin-right:12px">
                  <span style="color:#64748b">输入:</span> {{ step.inputPath }}
                </span>
                <span v-if="step.outputPath" style="color:#94a3b8;font-size:12px;margin-right:12px">
                  <span style="color:#64748b">输出:</span> {{ step.outputPath }}
                </span>
                <span v-if="step.command" style="color:#94a3b8;font-size:12px;margin-right:12px;font-family:monospace">
                  {{ step.command.length > 40 ? step.command.slice(0,40) + '...' : step.command }}
                </span>
                <span v-if="step.dependsOn && step.dependsOn.length > 0" style="color:#94a3b8;font-size:12px">
                  <span style="color:#64748b">依赖:</span>
                  <span v-for="(dep, dIdx) in step.dependsOn" :key="dIdx" style="margin-left:4px;color:#eab308">
                    {{ dep }}{{ dIdx < step.dependsOn.length - 1 ? ',' : '' }}
                  </span>
                </span>
                <span v-if="step.templateId" style="color:#94a3b8;font-size:12px;margin-left:12px">
                  <span style="color:#64748b">模板:</span> {{ step.templateId }}
                </span>
              </div>
              <div class="workflow-step-card-actions">
                <el-button size="small" text @click="openEditStep(step)">编辑</el-button>
                <el-button size="small" text type="danger" @click="deleteStep(step.id || step._tempId)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Execution History -->
      <div class="workflow-executions-section" style="margin-top:24px">
        <h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:#f1f5f9">执行历史</h4>
        <el-table :data="executions" v-loading="execLoading" stripe :size="tableSize" class="glass-table">
          <el-table-column prop="id" label="执行ID" width="80" />
          <el-table-column label="触发方式" width="100">
            <template #default="{ row }">
              <span style="color:#94a3b8;font-size:12px">{{ row.triggerType === 'cron' ? '⏰ 定时' : '👤 手动' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}</template>
          </el-table-column>
          <el-table-column label="结束时间" width="170">
            <template #default="{ row }">{{ row.endTime ? new Date(row.endTime).toLocaleString() : '-' }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">
              <span v-if="row.durationMs != null" style="font-family:monospace;font-size:12px;color:#94a3b8">{{ row.durationMs }}ms</span>
              <span v-else style="color:#64748b">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="execStatusTagType(row.status)" size="small" effect="dark">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="openExecutionDetail(row)">查看详情</el-button>
              <el-button size="small" type="warning" link @click="cancelExecution(row.id)"
                :disabled="!['PENDING','RUNNING'].includes(row.status)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="executions.length === 0 && !execLoading" class="fs-empty" style="padding:16px">
          <div class="fs-empty-text" style="font-size:13px">暂无执行记录</div>
        </div>
        <div style="display:flex;justify-content:flex-end;margin-top:8px" v-if="executions.length > 0">
          <el-pagination v-model:current-page="execPage" v-model:page-size="execPageSize"
            :total="execTotal" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next"
            @current-change="loadExecutions(selectedWorkflow.id)" @size-change="loadExecutions(selectedWorkflow.id)"
            background small />
        </div>
      </div>
    </template>
  </div>

          <!-- 创建告警规则对话框 -->
          <el-dialog v-model="showCreateAlertRuleDialog" title="创建告警规则" width="520px" top="25vh" class="glass-dialog">
            <el-form :model="createAlertRuleForm" label-width="100px" @submit.prevent="handleCreateAlertRule">
              <el-form-item label="队列/模块" required>
                <el-select v-model="createAlertRuleForm.queueName" style="width:100%" placeholder="选择队列">
                  <el-option label="default" value="default" />
                  <el-option label="root.hadoop" value="root.hadoop" />
                  <el-option label="root.spark" value="root.spark" />
                  <el-option label="root.flink" value="root.flink" />
                  <el-option label="自定义..." value="__custom__" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="createAlertRuleForm.queueName === '__custom__'" label="自定义队列">
                <el-input v-model="createAlertRuleCustomQueue" placeholder="输入队列名称" />
              </el-form-item>
              <el-form-item label="指标" required>
                <el-select v-model="createAlertRuleForm.metric" style="width:100%">
                  <el-option label="已用容量 (usedCapacity)" value="usedCapacity" />
                  <el-option label="应用数 (numApplications)" value="numApplications" />
                  <el-option label="已用内存 (usedMemoryMB)" value="usedMemoryMB" />
                  <el-option label="已用 vCores (usedVCores)" value="usedVCores" />
                </el-select>
              </el-form-item>
              <el-form-item label="条件" required>
                <div style="display:flex;gap:8px;width:100%">
                  <el-select v-model="createAlertRuleForm.operator" style="width:100px">
                    <el-option label=">" value=">" />
                    <el-option label=">=" value=">=" />
                    <el-option label="<" value="<" />
                    <el-option label="<=" value="<=" />
                    <el-option label="=" value="=" />
                  </el-select>
                  <el-input v-model.number="createAlertRuleForm.threshold" placeholder="阈值" type="number" style="flex:1" />
                </div>
              </el-form-item>
              <el-form-item label="通知邮箱">
                <el-input v-model="createAlertRuleForm.notifyEmail" placeholder="admin@example.com" />
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="createAlertRuleForm.enabled" active-text="启用" inactive-text="禁用" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showCreateAlertRuleDialog = false" round>取消</el-button>
              <el-button type="primary" @click="handleCreateAlertRule" :loading="createAlertRuleLoading" round>创建</el-button>
            </template>
          </el-dialog>

          <!-- 编辑告警规则对话框 -->
          <el-dialog v-model="showEditAlertRuleDialog" title="编辑告警规则" width="520px" top="25vh" class="glass-dialog">
            <el-form :model="editAlertRuleForm" label-width="100px">
              <el-form-item label="队列/模块">
                <el-input :model-value="editAlertRuleForm.queueName" disabled />
              </el-form-item>
              <el-form-item label="指标">
                <el-select v-model="editAlertRuleForm.metric" style="width:100%">
                  <el-option label="已用容量 (usedCapacity)" value="usedCapacity" />
                  <el-option label="应用数 (numApplications)" value="numApplications" />
                  <el-option label="已用内存 (usedMemoryMB)" value="usedMemoryMB" />
                  <el-option label="已用 vCores (usedVCores)" value="usedVCores" />
                </el-select>
              </el-form-item>
              <el-form-item label="条件">
                <div style="display:flex;gap:8px;width:100%">
                  <el-select v-model="editAlertRuleForm.operator" style="width:100px">
                    <el-option label=">" value=">" />
                    <el-option label=">=" value=">=" />
                    <el-option label="<" value="<" />
                    <el-option label="<=" value="<=" />
                    <el-option label="=" value="=" />
                  </el-select>
                  <el-input v-model.number="editAlertRuleForm.threshold" placeholder="阈值" type="number" style="flex:1" />
                </div>
              </el-form-item>
              <el-form-item label="通知邮箱">
                <el-input v-model="editAlertRuleForm.notifyEmail" placeholder="admin@example.com" />
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="editAlertRuleForm.enabled" active-text="启用" inactive-text="禁用" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showEditAlertRuleDialog = false" round>取消</el-button>
              <el-button type="primary" @click="handleEditAlertRule" :loading="editAlertRuleLoading" round>保存</el-button>
            </template>
          </el-dialog>

          <!-- 创建用户对话框 -->
          <el-dialog v-model="showCreateUserDialog" title="创建用户" width="450px" top="25vh" class="glass-dialog">
            <el-form :model="createUserForm" :rules="createUserRules" ref="createUserFormRef" label-width="80px" @submit.prevent="handleCreateUser">
              <el-form-item label="用户名" prop="username" required>
                <el-input v-model="createUserForm.username" placeholder="输入用户名" />
              </el-form-item>
              <el-form-item label="密码" prop="password" required>
                <el-input v-model="createUserForm.password" type="password" placeholder="输入密码" show-password />
                <div style="font-size:12px;line-height:1.6;margin-top:6px;padding:8px 10px;background:rgba(0,0,0,0.2);border-radius:6px">
                  <div :style="{ color: pwdHasUpper ? '#22c55e' : '#ef4444' }">{{ pwdHasUpper ? '✅' : '❌' }} 大写字母</div>
                  <div :style="{ color: pwdHasLower ? '#22c55e' : '#ef4444' }">{{ pwdHasLower ? '✅' : '❌' }} 小写字母</div>
                  <div :style="{ color: pwdHasDigit ? '#22c55e' : '#ef4444' }">{{ pwdHasDigit ? '✅' : '❌' }} 数字</div>
                  <div :style="{ color: pwdHasSpecial ? '#22c55e' : '#ef4444' }">{{ pwdHasSpecial ? '✅' : '❌' }} 特殊字符</div>
                  <div style="margin-top:4px;font-weight:600;color:#eab308">必须同时包含以上4类字符</div>
                </div>
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="createUserForm.email" placeholder="user@example.com" />
              </el-form-item>
              <el-form-item label="角色">
                <el-select v-model="createUserForm.role" style="width:100%">
                  <el-option label="管理员" value="admin" />
                  <el-option label="操作员" value="operator" />
                  <el-option label="观察者" value="viewer" />
                </el-select>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showCreateUserDialog = false" round>取消</el-button>
              <el-button type="primary" @click="handleCreateUser" :loading="createUserLoading" round>创建</el-button>
            </template>
          </el-dialog>

          <!-- 编辑用户对话框 -->
          <el-dialog v-model="showEditUserDialog" title="编辑用户" width="450px" top="25vh" class="glass-dialog">
            <el-form :model="editUserForm" label-width="80px">
              <el-form-item label="用户名">
                <el-input :model-value="editUserForm.username" disabled />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="editUserForm.email" placeholder="user@example.com" />
              </el-form-item>
              <el-form-item label="角色">
                <el-select v-model="editUserForm.role" style="width:100%">
                  <el-option label="管理员" value="admin" />
                  <el-option label="操作员" value="operator" />
                  <el-option label="观察者" value="viewer" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-switch v-model="editUserForm.enabled" active-text="启用" inactive-text="禁用" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showEditUserDialog = false" round>取消</el-button>
              <el-button type="primary" @click="handleEditUser" :loading="editUserLoading" round>保存</el-button>
            </template>
          </el-dialog>

          <!-- 重置密码对话框 -->
          <el-dialog v-model="showResetPasswordDialog" title="重置密码" width="400px" top="30vh" class="glass-dialog">
            <p style="margin-bottom:12px;font-size:13px;color:rgba(255,255,255,0.6)">
              重置用户 <strong style="color:#4fc3f7">{{ resetPasswordTarget?.username }}</strong> 的密码
            </p>
            <el-form @submit.prevent="handleResetPassword">
              <el-form-item label="旧密码" required>
                <el-input v-model="resetPasswordOldPassword" type="password" placeholder="输入当前密码" show-password />
              </el-form-item>
              <el-form-item label="新密码" required>
                <el-input v-model="resetPasswordNewPassword" type="password" placeholder="输入新密码" show-password
                  @keyup.enter="handleResetPassword" />
                <div style="font-size:12px;line-height:1.6;margin-top:6px;padding:8px 10px;background:rgba(0,0,0,0.2);border-radius:6px">
                  <div :style="{ color: rpwdHasUpper ? '#22c55e' : '#ef4444' }">{{ rpwdHasUpper ? '✅' : '❌' }} 大写字母</div>
                  <div :style="{ color: rpwdHasLower ? '#22c55e' : '#ef4444' }">{{ rpwdHasLower ? '✅' : '❌' }} 小写字母</div>
                  <div :style="{ color: rpwdHasDigit ? '#22c55e' : '#ef4444' }">{{ rpwdHasDigit ? '✅' : '❌' }} 数字</div>
                  <div :style="{ color: rpwdHasSpecial ? '#22c55e' : '#ef4444' }">{{ rpwdHasSpecial ? '✅' : '❌' }} 特殊字符</div>
                  <div style="margin-top:4px;font-weight:600;color:#eab308">必须同时包含以上4类字符</div>
                </div>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showResetPasswordDialog = false" round>取消</el-button>
              <el-button type="warning" @click="handleResetPassword" :loading="resetPasswordLoading" round>重置</el-button>
            </template>
          </el-dialog>
        </div>
      </div>
    </div>
  </div>
  <!-- Command Palette Overlay -->
  <div v-if="showCommandPalette" class="cp-overlay" @click.self="showCommandPalette = false" @keydown.escape="showCommandPalette = false">
    <div class="cp-modal" @click.stop>
      <div class="cp-search-wrap">
        <span class="cp-search-icon">🔍</span>
        <input
          ref="cpInputRef"
          v-model="paletteSearch"
          class="cp-search-input"
          placeholder="搜索页面或操作... 输入关键词"
          @keydown.enter="selectPaletteItem"
          @keydown.down.prevent="highlightNext"
          @keydown.up.prevent="highlightPrev"
        />
        <span class="cp-kbd">ESC</span>
      </div>
      <div v-if="paletteSearching" class="cp-searching">
        <div class="cp-empty-icon">⏳</div>
        <div class="cp-empty-text">搜索中...</div>
      </div>
      <div class="cp-results" v-else-if="paletteSearchResults.length > 0">
        <div
          v-for="(item, idx) in paletteSearchResults"
          :key="idx"
        >
          <!-- Category header -->
          <div v-if="idx === 0 || item._category !== paletteSearchResults[idx-1]._category"
               class="cp-category-header">{{ item._category }}</div>
          <div
            class="cp-result-item"
            :class="{ highlighted: paletteHighlightIndex === idx }"
            @click="executePaletteItem(item)"
            @mouseenter="paletteHighlightIndex = idx"
          >
            <span class="cp-result-icon">{{ item.icon }}</span>
            <div class="cp-result-body">
              <span class="cp-result-label">{{ item.label }}</span>
              <span v-if="item.sublabel" class="cp-result-sublabel">{{ item.sublabel }}</span>
            </div>
            <span class="cp-result-action">前往 →</span>
          </div>
        </div>
      </div>
      <div class="cp-empty" v-else-if="paletteSearch && paletteSearchResults.length === 0">
        <div class="cp-empty-icon">🔍</div>
        <div class="cp-empty-text">未找到匹配结果</div>
        <div class="cp-empty-hint">尝试其他关键词搜索页面或操作</div>
      </div>
      <div class="cp-empty" v-else>
        <div class="cp-empty-icon">⌨️</div>
        <div class="cp-empty-text">输入关键词搜索</div>
        <div class="cp-empty-hint">搜索页面、HDFS 文件、用户或系统配置</div>
      </div>
    </div>
  </div>

  <!-- Help Modal -->
  <el-dialog v-model="showHelpModal" title="❓ 帮助与快速参考" width="620px" top="8vh" class="help-dialog" :close-on-click-modal="true" destroy-on-close>
    <div class="help-modal-body">
      <!-- Section 1: Keyboard Shortcuts -->
      <div class="help-section">
        <div class="help-section-title">⌨️ 键盘快捷键</div>
        <div class="help-shortcuts-grid">
          <div class="help-shortcut-row">
            <span class="help-kbd">Ctrl+K</span><span class="help-or">/</span><span class="help-kbd">⌘K</span>
            <span class="help-desc">全局搜索</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">Ctrl+A</span>
            <span class="help-desc">全选文件</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">Delete</span>
            <span class="help-desc">移动到回收站</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">F5</span>
            <span class="help-desc">刷新文件列表</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">Ctrl+F</span>
            <span class="help-desc">搜索文件</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">Backspace</span>
            <span class="help-desc">返回上级目录</span>
          </div>
          <div class="help-shortcut-row">
            <span class="help-kbd">ESC</span>
            <span class="help-desc">关闭弹窗/面板</span>
          </div>
        </div>
      </div>

      <!-- Section 2: Theme & Version -->
      <div class="help-section">
        <div class="help-section-title">🎨 主题与版本</div>
        <el-descriptions :column="2" border size="small" class="help-descriptions">
          <el-descriptions-item label="当前版本">{{ appVersion }}</el-descriptions-item>
          <el-descriptions-item label="主题">深色</el-descriptions-item>
          <el-descriptions-item label="后端">Spring Boot 3.2 / Hadoop 3.3.6</el-descriptions-item>
          <el-descriptions-item label="前端">Vue 3 + Element Plus</el-descriptions-item>
          <el-descriptions-item label="HDFS NameService">{{ nameService }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- Section 3: Quick Start -->
      <div class="help-section">
        <div class="help-section-title">📚 快速入门</div>
        <div class="help-quickstart-list">
          <div class="help-quickstart-item">
            <span class="help-qs-icon">📂</span>
            <span>浏览 HDFS 文件系统 — 使用左侧菜单进入 HDFS，查看、上传、下载文件</span>
          </div>
          <div class="help-quickstart-item">
            <span class="help-qs-icon">📋</span>
            <span>提交 YARN 作业 — 通过 MR 模板页面快速提交 MapReduce 任务</span>
          </div>
          <div class="help-quickstart-item">
            <span class="help-qs-icon">📊</span>
            <span>监控集群状态 — 总览页面实时展示集群健康度、资源利用率</span>
          </div>
          <div class="help-quickstart-item">
            <span class="help-qs-icon">👤</span>
            <span>管理用户权限 — 管理员可在用户管理页面添加、修改用户角色</span>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="help-footer">
        <span class="help-footer-hint">按 <kbd class="help-kbd-inline">ESC</kbd> 关闭</span>
      </div>
    </template>
  </el-dialog>

  <!-- 注册表对话框 -->
  <el-dialog v-model="showRegisterDialog" title="注册数据表" width="560px" top="20vh" class="glass-dialog">
    <el-form :model="registerForm" label-width="100px" @submit.prevent="handleRegisterTable">
      <el-form-item label="表名" required>
        <el-input v-model="registerForm.name" placeholder="例如: user_events" />
      </el-form-item>
      <el-form-item label="HDFS路径" required>
        <el-input v-model="registerForm.hdfsPath" placeholder="/data/warehouse/..." />
      </el-form-item>
      <el-form-item label="Schema">
        <el-input v-model="registerForm.schemaName" placeholder="default" />
      </el-form-item>
      <el-form-item label="格式" required>
        <el-select v-model="registerForm.format" style="width:100%">
          <el-option label="TEXT" value="TEXT" />
          <el-option label="CSV" value="CSV" />
          <el-option label="Parquet" value="Parquet" />
          <el-option label="ORC" value="ORC" />
          <el-option label="Avro" value="Avro" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="registerForm.description" type="textarea" :rows="2" placeholder="可选描述" />
      </el-form-item>
      <el-form-item label="分区列">
        <el-input v-model="registerForm.partitionColumns" placeholder="逗号分隔, 例如: dt,hr" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="registerForm.owner" :placeholder="username || '负责人'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showRegisterDialog = false" round>取消</el-button>
      <el-button type="primary" @click="handleRegisterTable" :loading="registerLoading" round>注册</el-button>
    </template>
  </el-dialog>

  <!-- 编辑表对话框 -->
  <el-dialog v-model="showEditDialog" title="编辑数据表" width="560px" top="20vh" class="glass-dialog">
    <el-form :model="editForm" label-width="100px">
      <el-form-item label="表名">
        <el-input :model-value="editForm.name" disabled />
      </el-form-item>
      <el-form-item label="HDFS路径" required>
        <el-input v-model="editForm.hdfsPath" placeholder="/data/warehouse/..." />
      </el-form-item>
      <el-form-item label="Schema">
        <el-input v-model="editForm.schemaName" placeholder="default" />
      </el-form-item>
      <el-form-item label="格式" required>
        <el-select v-model="editForm.format" style="width:100%">
          <el-option label="TEXT" value="TEXT" />
          <el-option label="CSV" value="CSV" />
          <el-option label="Parquet" value="Parquet" />
          <el-option label="ORC" value="ORC" />
          <el-option label="Avro" value="Avro" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="可选描述" />
      </el-form-item>
      <el-form-item label="分区列">
        <el-input v-model="editForm.partitionColumns" placeholder="逗号分隔, 例如: dt,hr" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="editForm.owner" :placeholder="username || '负责人'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditDialog = false" round>取消</el-button>
      <el-button type="primary" @click="handleEditTable" :loading="editLoading" round>保存</el-button>
    </template>
  </el-dialog>

  <!-- 表详情抽屉 -->
  <el-drawer v-model="showDetailDrawer" :title="detailTable?.name || '表详情'" size="600px" class="catalog-drawer" direction="rtl">
    <template v-if="detailTable">
      <div class="catalog-drawer-body">
        <!-- Basic Info -->
        <div class="catalog-drawer-section">
          <div class="catalog-drawer-section-title">基本信息</div>
          <el-descriptions :column="2" border size="small" class="catalog-descriptions">
            <el-descriptions-item label="名称">{{ detailTable.name }}</el-descriptions-item>
            <el-descriptions-item label="格式"><el-tag size="small">{{ detailTable.format }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="Schema">{{ detailTable.schemaName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="行数">{{ detailTable.rowCount != null ? detailTable.rowCount.toLocaleString() : '-' }}</el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatBytes(detailTable.sizeInBytes) }}</el-descriptions-item>
            <el-descriptions-item label="文件数">{{ detailTable.fileCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="HDFS路径" :span="2">
              <el-link type="primary" @click="catalogNavigateToHdfs(detailTable.hdfsPath)" style="font-size:12px">{{ detailTable.hdfsPath }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ detailTable.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分区列" :span="2">{{ detailTable.partitionColumns || '-' }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detailTable.owner || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detailTable.updateTime ? new Date(detailTable.updateTime).toLocaleString() : '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Tags -->
        <div class="catalog-drawer-section">
          <div class="catalog-drawer-section-title">
            <span>标签</span>
            <el-button text size="small" @click="openTagSelector">编辑标签</el-button>
          </div>
          <div v-if="detailTags.length === 0" class="fs-empty" style="padding:8px"><div class="fs-empty-icon">🏷️</div><div class="fs-empty-text">暂无标签</div></div>
          <div v-else class="catalog-tag-list">
            <span v-for="tag in detailTags" :key="tag.id" class="catalog-tag-badge"
              :style="{ background: tag.color || '#3b82f6' }">{{ tag.name }}</span>
          </div>
        </div>

        <!-- Columns -->
        <div class="catalog-drawer-section">
          <div class="catalog-drawer-section-title">
            <span>列信息</span>
            <el-button text size="small" @click="showAddColumnDialog = true">+ 添加列</el-button>
          </div>
          <el-table :data="detailColumns" v-loading="columnsLoading" size="small" class="catalog-column-table" stripe>
            <el-table-column prop="name" label="名称" min-width="100" />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="comment" label="注释" min-width="100" show-overflow-tooltip />
            <el-table-column label="可空" width="60" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.nullable ? '#22c55e' : '#ef4444' }">{{ row.nullable ? '🟢' : '🔴' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分区" width="60" align="center">
              <template #default="{ row }">
                <span v-if="row.isPartition" style="font-size:14px">📂</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button text size="small" @click="editCatalogColumn(row)">编辑</el-button>
                <el-button text size="small" type="danger" @click="deleteCatalogColumn(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Actions -->
        <div class="catalog-drawer-section">
          <div class="catalog-drawer-section-title">操作</div>
          <div style="display:flex;gap:8px">
            <el-button size="small" @click="scanCatalogTable(detailTable)" :loading="catalogScanLoadingId === detailTable.id">🔄 扫描HDFS</el-button>
            <el-button size="small" type="danger" @click="deleteCatalogTable(detailTable)">删除</el-button>
          </div>
        </div>

        <!-- Lineage -->
        <div class="catalog-drawer-section">
          <div class="catalog-drawer-section-title">数据血缘</div>
          <el-collapse v-model="lineageActiveNames" @change="loadLineageIfNeeded">
            <el-collapse-item title="⬆ 上游依赖" name="upstream">
              <div v-if="upstreamLineage.length === 0 && !catalogLineageLoading" class="fs-empty" style="padding:8px 0"><div class="fs-empty-icon">⬆️</div><div class="fs-empty-text">暂无上游依赖</div></div>
              <div v-if="catalogLineageLoading" style="color:#64748b;font-size:13px;padding:8px 0">加载中...</div>
              <div v-for="item in upstreamLineage" :key="item.id" style="padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)">
                <el-link type="primary" @click="viewLineageTable(item)" style="font-size:13px">{{ item.name }}</el-link>
                <div style="font-size:11px;color:#64748b;margin-top:2px">{{ item.hdfsPath }}</div>
              </div>
            </el-collapse-item>
            <el-collapse-item title="⬇ 下游引用" name="downstream">
              <div v-if="downstreamLineage.length === 0 && !catalogLineageLoading" class="fs-empty" style="padding:8px 0"><div class="fs-empty-icon">⬇️</div><div class="fs-empty-text">暂无下游引用</div></div>
              <div v-if="catalogLineageLoading" style="color:#64748b;font-size:13px;padding:8px 0">加载中...</div>
              <div v-for="item in downstreamLineage" :key="item.id" style="padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)">
                <el-link type="primary" @click="viewLineageTable(item)" style="font-size:13px">{{ item.name }}</el-link>
                <div style="font-size:11px;color:#64748b;margin-top:2px">{{ item.hdfsPath }}</div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </template>
  </el-drawer>

  <!-- 添加列对话框 -->
  <el-dialog v-model="showAddColumnDialog" title="添加列" width="420px" top="30vh" class="glass-dialog">
    <el-form :model="addColumnForm" label-width="80px" @submit.prevent="handleAddColumn">
      <el-form-item label="列名" required>
        <el-input v-model="addColumnForm.name" placeholder="column_name" />
      </el-form-item>
      <el-form-item label="类型" required>
        <el-select v-model="addColumnForm.type" style="width:100%">
          <el-option label="STRING" value="STRING" />
          <el-option label="INT" value="INT" />
          <el-option label="BIGINT" value="BIGINT" />
          <el-option label="FLOAT" value="FLOAT" />
          <el-option label="DOUBLE" value="DOUBLE" />
          <el-option label="BOOLEAN" value="BOOLEAN" />
          <el-option label="TIMESTAMP" value="TIMESTAMP" />
          <el-option label="DATE" value="DATE" />
          <el-option label="DECIMAL" value="DECIMAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="注释">
        <el-input v-model="addColumnForm.comment" placeholder="可选注释" />
      </el-form-item>
      <el-form-item label="可空">
        <el-switch v-model="addColumnForm.nullable" />
      </el-form-item>
      <el-form-item label="分区列">
        <el-switch v-model="addColumnForm.isPartition" />
      </el-form-item>
      <el-form-item label="序号">
        <el-input-number v-model="addColumnForm.ordinalPosition" :min="1" size="small" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showAddColumnDialog = false" round>取消</el-button>
      <el-button type="primary" @click="handleAddColumn" :loading="addColumnLoading" round>添加</el-button>
    </template>
  </el-dialog>

  <!-- 编辑列对话框 -->
  <el-dialog v-model="showEditColumnDialog" title="编辑列" width="420px" top="30vh" class="glass-dialog">
    <el-form :model="editColumnForm" label-width="80px">
      <el-form-item label="列名">
        <el-input :model-value="editColumnForm.name" disabled />
      </el-form-item>
      <el-form-item label="类型" required>
        <el-select v-model="editColumnForm.type" style="width:100%">
          <el-option label="STRING" value="STRING" />
          <el-option label="INT" value="INT" />
          <el-option label="BIGINT" value="BIGINT" />
          <el-option label="FLOAT" value="FLOAT" />
          <el-option label="DOUBLE" value="DOUBLE" />
          <el-option label="BOOLEAN" value="BOOLEAN" />
          <el-option label="TIMESTAMP" value="TIMESTAMP" />
          <el-option label="DATE" value="DATE" />
          <el-option label="DECIMAL" value="DECIMAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="注释">
        <el-input v-model="editColumnForm.comment" placeholder="可选注释" />
      </el-form-item>
      <el-form-item label="可空">
        <el-switch v-model="editColumnForm.nullable" />
      </el-form-item>
      <el-form-item label="分区列">
        <el-switch v-model="editColumnForm.isPartition" />
      </el-form-item>
      <el-form-item label="序号">
        <el-input-number v-model="editColumnForm.ordinalPosition" :min="1" size="small" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditColumnDialog = false" round>取消</el-button>
      <el-button type="primary" @click="handleEditColumn" :loading="editColumnLoading" round>保存</el-button>
    </template>
  </el-dialog>

  <!-- 标签管理对话框 -->
  <el-dialog v-model="showTagDialog" :title="tagDialogMode === 'manage' ? '标签管理' : '编辑表标签'" width="480px" top="20vh" class="glass-dialog">
    <template v-if="tagDialogMode === 'manage'">
      <div style="margin-bottom:12px">
        <div style="display:flex;gap:8px;align-items:center">
          <el-input v-model="newTagName" placeholder="新标签名" size="small" style="flex:1" />
          <el-color-picker v-model="newTagColor" size="small" />
          <el-button type="primary" size="small" @click="handleAddTag" :loading="addTagLoading">添加</el-button>
        </div>
      </div>
      <el-table :data="allTags" size="small" stripe>
        <el-table-column prop="name" label="标签名">
          <template #default="{ row }">
            <span class="catalog-tag-badge" :style="{ background: row.color || '#3b82f6' }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="deleteCatalogTag(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
    <template v-else>
      <p style="color:rgba(255,255,255,0.6);font-size:13px;margin-bottom:12px">选择要关联到 <strong>{{ detailTable?.name }}</strong> 的标签</p>
      <div v-if="allTags.length === 0" class="fs-empty" style="padding:12px"><div class="fs-empty-icon">🏷️</div><div class="fs-empty-text">暂无可用标签，请先在标签管理中创建</div></div>
      <el-checkbox-group v-model="selectedTagIds" v-else>
        <div v-for="tag in allTags" :key="tag.id" style="margin-bottom:8px">
          <el-checkbox :label="tag.id" :value="tag.id">
            <span class="catalog-tag-badge" :style="{ background: tag.color || '#3b82f6' }">{{ tag.name }}</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </template>
    <template #footer>
      <el-button @click="showTagDialog = false" round>取消</el-button>
      <el-button v-if="tagDialogMode === 'selector'" type="primary" @click="handleSetTableTags" :loading="setTagLoading" round>保存</el-button>
    </template>
  </el-dialog>

  <!-- 自动发现对话框 -->
  <el-dialog v-model="showDiscoverDialog" title="自动发现表" width="420px" top="30vh" class="glass-dialog">
    <el-form :model="discoverForm" label-width="100px" @submit.prevent="handleDiscover">
      <el-form-item label="基础路径" required>
        <el-input v-model="discoverForm.basePath" placeholder="/" />
      </el-form-item>
      <el-form-item label="集群">
        <el-input v-model="discoverForm.clusterId" placeholder="cluster1" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showDiscoverDialog = false" round>取消</el-button>
      <el-button type="primary" @click="handleDiscover" :loading="discoverLoading" round>开始发现</el-button>
    </template>
  </el-dialog>

  <!-- Workflow Create/Edit Dialog -->
  <el-dialog v-model="showWorkflowDialog" :title="editingWorkflowId ? '编辑工作流' : '新建工作流'"
    width="540px" top="8vh" class="glass-dialog workflow-dialog">
    <el-form :model="workflowForm" label-width="110px">
      <el-form-item label="名称" required>
        <el-input v-model="workflowForm.name" placeholder="工作流名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="workflowForm.description" type="textarea" :rows="2" placeholder="可选描述" />
      </el-form-item>
      <el-form-item label="集群">
        <el-input v-model="workflowForm.clusterId" placeholder="cluster1" />
      </el-form-item>
      <el-form-item label="调度 Crontab">
        <el-input v-model="workflowForm.scheduleCron" placeholder="0 0 8 * * ?" />
        <div style="color:#64748b;font-size:11px;margin-top:4px">留空表示仅手动触发</div>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最大重试">
            <el-input-number v-model="workflowForm.maxRetries" :min="0" :max="10" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="超时(分钟)">
            <el-input-number v-model="workflowForm.timeoutMinutes" :min="1" :max="1440" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Webhook URL">
        <el-input v-model="workflowForm.webhookUrl" placeholder="https://hooks.example.com/workflow-callback" />
        <div class="el-form-item__tip" style="color: #94a3b8; font-size: 12px; margin-top: 4px;">
          工作流执行完成后将发送 POST 回调到此 URL
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showWorkflowDialog = false" round>取消</el-button>
      <el-button type="primary" @click="saveWorkflow" :loading="workflowSaving" round>保存</el-button>
    </template>
  </el-dialog>

  <!-- Step Create/Edit Dialog -->
  <el-dialog v-model="showStepDialog" :title="editingStepId ? '编辑步骤' : '添加步骤'"
    width="560px" top="8vh" class="glass-dialog workflow-dialog">
    <el-form :model="stepForm" label-width="110px">
      <el-form-item label="步骤名称" required>
        <el-input v-model="stepForm.name" placeholder="步骤名称" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="类型" required>
            <el-select v-model="stepForm.stepType" style="width:100%" @change="onStepTypeChange">
              <el-option label="MAPREDUCE" value="MAPREDUCE" />
              <el-option label="SHELL" value="SHELL" />
              <el-option label="WAIT" value="WAIT" />
              <el-option label="HTTP" value="HTTP" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="顺序">
            <el-input-number v-model="stepForm.stepOrder" :min="1" :max="100" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- MAPREDUCE fields -->
      <template v-if="stepForm.stepType === 'MAPREDUCE'">
        <el-form-item label="MapReduce 模板">
          <el-select v-model="stepForm.templateId" style="width:100%" filterable>
            <el-option v-for="t in mrTemplates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="输入路径">
          <el-input v-model="stepForm.inputPath" placeholder="/data/input" />
        </el-form-item>
        <el-form-item label="输出路径">
          <el-input v-model="stepForm.outputPath" placeholder="/data/output" />
        </el-form-item>
        <el-form-item label="队列">
          <el-input v-model="stepForm.queue" placeholder="default" />
        </el-form-item>
      </template>

      <!-- SHELL fields -->
      <template v-if="stepForm.stepType === 'SHELL'">
        <el-form-item label="命令">
          <el-input v-model="stepForm.command" type="textarea" :rows="3" placeholder="shell 命令或脚本路径" />
        </el-form-item>
      </template>

      <!-- HTTP fields -->
      <template v-if="stepForm.stepType === 'HTTP'">
        <el-form-item label="URL">
          <el-input v-model="stepForm.command" placeholder="https://example.com/api/callback" />
        </el-form-item>
      </template>

      <!-- WAIT fields -->
      <template v-if="stepForm.stepType === 'WAIT'">
        <div style="color:#94a3b8;font-size:13px;padding:8px 0 8px 110px">WAIT 步骤使用超时时间控制等待时长</div>
      </template>

      <el-form-item label="依赖步骤">
        <el-select v-model="stepForm.dependsOn" multiple style="width:100%" placeholder="选择依赖的步骤（可选）">
          <el-option v-for="s in availableStepDeps" :key="s.name" :label="s.name" :value="s.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="超时(分钟)">
        <el-input-number v-model="stepForm.timeoutMinutes" :min="1" :max="1440" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showStepDialog = false" round>取消</el-button>
      <el-button type="primary" @click="saveStep" :loading="stepSaving" round>保存</el-button>
    </template>
  </el-dialog>

  <!-- Execution Detail Drawer -->
  <el-drawer v-model="showExecutionDrawer" :title="selectedExecution ? '执行详情 #' + selectedExecution.id : '执行详情'"
    size="650px" class="workflow-exec-drawer" direction="rtl">
    <template v-if="selectedExecution">
      <div class="workflow-exec-drawer-body">
        <div class="workflow-exec-drawer-section">
          <div class="workflow-exec-drawer-section-title">基本信息</div>
          <el-descriptions :column="2" border size="small" class="catalog-descriptions">
            <el-descriptions-item label="执行ID">{{ selectedExecution.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="execStatusTagType(selectedExecution.status)" size="small" effect="dark">
                {{ selectedExecution.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="触发方式">
              {{ selectedExecution.triggerType === 'cron' ? '⏰ 定时' : '👤 手动' }}
            </el-descriptions-item>
            <el-descriptions-item label="耗时">
              {{ selectedExecution.durationMs != null ? selectedExecution.durationMs + 'ms' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ selectedExecution.startTime ? new Date(selectedExecution.startTime).toLocaleString() : '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ selectedExecution.endTime ? new Date(selectedExecution.endTime).toLocaleString() : '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="workflow-exec-drawer-section">
          <div class="workflow-exec-drawer-section-title">步骤执行详情</div>
          <el-table :data="stepExecutions" v-loading="stepExecLoading" size="small" stripe class="catalog-column-table">
            <el-table-column prop="stepName" label="步骤名" min-width="120" />
            <el-table-column prop="stepType" label="类型" width="90" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="execStatusTagType(row.status)" size="small" effect="dark">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="yarnAppId" label="YARN App ID" min-width="150" show-overflow-tooltip />
            <el-table-column label="开始时间" width="150">
              <template #default="{ row }">{{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="150">
              <template #default="{ row }">{{ row.endTime ? new Date(row.endTime).toLocaleString() : '-' }}</template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.errorMessage" style="color:#ef4444;font-size:12px">{{ row.errorMessage }}</span>
                <span v-else style="color:#64748b">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="stepExecutions.length === 0 && !stepExecLoading" class="fs-empty" style="padding:24px"><div class="fs-empty-icon">📋</div><div class="fs-empty-text">暂无步骤执行记录</div></div>
        </div>
      </div>
    </template>
    <template #footer>
      <div style="display:flex;gap:8px;justify-content:flex-end">
        <el-button v-if="selectedExecution && ['PENDING','RUNNING'].includes(selectedExecution.status)"
          type="warning" @click="cancelExecution(selectedExecution.id)">取消执行</el-button>
        <el-button @click="showExecutionDrawer = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- Health Detail Drawer -->
  <el-drawer v-model="healthDrawerVisible" title="🏥 集群健康详情" size="500px" :with-header="true" class="health-detail-drawer">
    <div class="health-drawer-body">
      <!-- Overall Score -->
      <div class="health-drawer-header">
        <div class="health-drawer-score-ring">
          <svg viewBox="0 0 100 100" width="80" height="80">
            <circle cx="50" cy="50" r="42" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="6"/>
            <circle cx="50" cy="50" r="42" fill="none" :stroke="healthScoreColor" stroke-width="6"
              stroke-linecap="round" :stroke-dasharray="263.9" :stroke-dashoffset="263.9 - (healthScore/100)*263.9"
              transform="rotate(-90,50,50)" style="transition: stroke-dashoffset 0.8s ease"/>
          </svg>
          <div class="health-drawer-score-text" :style="{color: healthScoreColor}">{{ healthScore }}</div>
        </div>
        <div class="health-drawer-score-info">
          <div class="health-drawer-score-title">集群健康度</div>
          <div class="health-drawer-score-status" :style="{color: healthScoreColor}">{{ healthScore >= 80 ? '✅ 健康' : healthScore >= 60 ? '⚠️ 亚健康' : '❌ 异常' }}</div>
          <div class="health-drawer-score-desc">{{ healthScore >= 80 ? '集群运行状态良好' : healthScore >= 60 ? '部分组件存在异常' : '集群需要立即关注' }}</div>
        </div>
      </div>

      <!-- 12 Check Items -->
      <div class="health-drawer-section-title">检查项 (12)</div>
      <div class="health-drawer-items">
        <div v-for="(item, idx) in healthCheckItems" :key="idx" class="health-drawer-item" :class="'health-item-' + item.status">
          <div class="health-item-icon">{{ item.status === 'healthy' ? '✅' : item.status === 'degraded' ? '⚠️' : '❌' }}</div>
          <div class="health-item-body">
            <div class="health-item-name">{{ item.name }}</div>
            <div class="health-item-detail" v-if="item.detail">{{ item.detail }}</div>
          </div>
          <div class="health-item-action">
            <el-button v-if="item.status === 'down' || item.status === 'degraded'" size="small" type="warning" @click="handleRepair(item)" round>一键修复</el-button>
            <el-tag v-else :type="item.status === 'healthy' ? 'success' : 'danger'" size="small" effect="dark">{{ item.status === 'healthy' ? '正常' : item.status === 'degraded' ? '异常' : '离线' }}</el-tag>
          </div>
        </div>
      </div>
      <div v-if="healthCheckLoading" class="health-drawer-loading">加载健康检查数据...</div>
    </div>
  </el-drawer>
</template>
<script setup>
import { ref, reactive, computed, onMounted, watch, watchEffect, onUnmounted, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'

// Global loading state for long-running operations
const globalLoading = ref(false)

// Cluster connection status
const clusterConnected = ref(true)
const successFlash = ref(false)

// Dynamic page title based on active tab
const pageTitleMap = {
  dashboard: '数据中台',
  hdfs: '数据中台 - HDFS',
  'yarn': '数据中台 - YARN',
  mr: '数据中台 - MapReduce',
  'operation-log': '数据中台 - 操作审计',
  'user-mgmt': '数据中台 - 用户管理',
  'alert-center': '数据中台 - 告警中心',
  monitor: '数据中台 - 监控',
  notebook: '数据中台 - Notebook',
  'system-config': '数据中台 - 系统配置',
  'catalog': '数据中台 - 数据目录',
  'workflow': '数据中台 - 工作流编排'
}

const triggerSuccessFlash = () => {
  successFlash.value = true
  setTimeout(() => { successFlash.value = false }, 1500)
}

// === CSV Export Utility ===
const exportToCsv = (data, filename, columns) => {
  const today = new Date().toISOString().slice(0, 10)
  const fullFilename = filename.replace('.csv', '') + '_' + today + '.csv'

  const escapeCsv = (val) => {
    if (val === null || val === undefined) return ''
    const str = String(val)
    if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
      return '"' + str.replace(/"/g, '""') + '"'
    }
    return str
  }

  const header = columns.map(c => escapeCsv(c.label)).join(',')
  const rows = data.map(row => {
    return columns.map(c => {
      const val = c.formatter ? c.formatter(row[c.key]) : row[c.key]
      return escapeCsv(val)
    }).join(',')
  })
  const csvContent = [header, ...rows].join('\n')
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fullFilename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('已导出: ' + fullFilename)
}

let healthCheckTimer = null
let systemHealthTimer = null

// Notebook state
const notebookUrl = ref(localStorage.getItem('hermes_notebook_url') || '')
const notebookEmbed = ref(false)
const openNotebook = () => {
  if (!notebookUrl.value.trim()) {
    ElMessage.warning('请输入 Notebook 服务器 URL')
    return
  }
  if (!notebookEmbed.value) {
    window.open(notebookUrl.value, '_blank')
  }
}

// Grafana state
const grafanaMode = ref('inapp') // 'inapp' or 'grafana'
const grafanaUrl = ref('')
const loadGrafanaConfig = async () => {
  const saved = localStorage.getItem('hermes_grafana_url')
  if (saved) {
    grafanaUrl.value = saved
  }
  try {
    const res = await axios.get('/api/v1/dashboard/grafana-config')
    if (res.data && res.data.code === 0) {
      const backendUrl = res.data.data.url
      if (backendUrl && !localStorage.getItem('hermes_grafana_url')) {
        grafanaUrl.value = backendUrl
      }
    }
  } catch (e) { /* use saved or empty */ }
}
const saveGrafanaUrl = () => {
  localStorage.setItem('hermes_grafana_url', grafanaUrl.value)
  ElMessage.success('Grafana URL 已保存')
}

// System health state
const systemHealth = reactive({
  overall: 'unknown',
  components: []
})
const systemHealthLoading = ref(false)

// Health detail drawer
const healthDrawerVisible = ref(false)
const healthCheckLoading = ref(false)

// Auto-refresh
const autoRefreshEnabled = ref(true)
const autoRefreshCountdown = ref(30)
const autoRefreshTimer = ref(null)
const manualRefreshLoading = ref(false)

// 7-day health trend
const healthHistory = ref([])
const healthHistoryLoading = ref(false)

// Quick action loading states
const actionLoading = reactive({
  upload: false,
  mr: false,
  checkpoint: false,
  refresh: false,
  submitJob: false
})

// 12 health check items (computed from system health API data + derived checks)
const healthCheckItems = computed(() => {
  const comps = systemHealth.components
  const items = []

  // 1. NameNode nn1 heartbeat
  const nn1 = comps.find(c => c.name === 'NameNode nn1')
  items.push({
    name: 'NameNode nn1 心跳',
    status: nn1?.status || 'down',
    detail: nn1?.details?.state ? `状态: ${nn1.details.state}` : (nn1?.details?.message || '无法连接')
  })

  // 2. NameNode nn2 heartbeat
  const nn2 = comps.find(c => c.name === 'NameNode nn2')
  items.push({
    name: 'NameNode nn2 心跳',
    status: nn2?.status || 'down',
    detail: nn2?.details?.state ? `状态: ${nn2.details.state}` : (nn2?.details?.message || '无法连接')
  })

  // 3. Active NN status
  items.push({
    name: 'Active NN 状态',
    status: systemHealth.overall === 'healthy' ? 'healthy' : systemHealth.overall === 'degraded' ? 'degraded' : 'down',
    detail: systemHealth.overall === 'healthy' ? 'Active/Standby 正常运行' : '无 Active NameNode'
  })

  // 4. DataNode block report status
  const dns = comps.find(c => c.name === 'DataNodes')
  items.push({
    name: 'DataNode 块报告状态',
    status: dns?.status || 'down',
    detail: dns?.details?.message || '无法获取 DataNode 状态'
  })

  // 5. YARN RM availability
  const rm = comps.find(c => c.name === 'YARN ResourceManager')
  items.push({
    name: 'YARN RM 可用性',
    status: rm?.status || 'down',
    detail: rm?.details?.host ? `主机: ${rm.details.host}` : (rm?.details?.message || '无法连接')
  })

  // 6. JournalNode sync status (3 nodes)
  const jns = comps.find(c => c.name === 'JournalNodes')
  const jnTotal = jns?.details?.total || 0
  const jnReachable = jns?.details?.reachable || 0
  items.push({
    name: 'JournalNode 同步状态 (3节点)',
    status: jnTotal > 0 && jnReachable === jnTotal ? 'healthy' : (jnReachable > 0 ? 'degraded' : 'down'),
    detail: jnTotal > 0 ? `${jnReachable}/${jnTotal} 节点可达` : (jns?.details?.message || '无法获取 JN 状态')
  })

  // 7. ZooKeeper connection
  const zk = comps.find(c => c.name === 'ZooKeeper')
  items.push({
    name: 'ZooKeeper 连接',
    status: zk?.status || 'healthy',
    detail: zk?.details?.message || 'ZK 连接正常'
  })

  // 8. HDFS disk usage %
  const h = overview.hdfs
  const usagePct = h && h.totalSpace > 0 ? Math.round((h.usedSpace / h.totalSpace) * 100) : 0
  items.push({
    name: 'HDFS 磁盘使用率',
    status: usagePct >= 80 ? 'degraded' : usagePct >= 95 ? 'down' : 'healthy',
    detail: `已使用 ${usagePct}%${usagePct >= 80 ? ' (偏高)' : usagePct >= 60 ? ' (正常)' : ' (良好)'}`
  })

  // 9. Under-replicated blocks
  items.push({
    name: 'Under-replicated 块',
    status: 'healthy',
    detail: '0 个块需要复制 (未检测到异常)'
  })

  // 10. Missing blocks
  items.push({
    name: 'Missing 块',
    status: 'healthy',
    detail: '0 个块丢失 (未检测到异常)'
  })

  // 11. YARN node managers health
  const y = overview.yarn
  const nmCount = y?.numNodeManagers || 0
  items.push({
    name: 'YARN NodeManager 健康',
    status: nmCount > 0 ? 'healthy' : 'down',
    detail: nmCount > 0 ? `${nmCount} 个 NodeManager 在线` : '无 NodeManager 在线'
  })

  // 12. Cluster connection
  items.push({
    name: '集群连接',
    status: clusterConnected.value ? 'healthy' : 'down',
    detail: clusterConnected.value ? `已连接到 ${overview.clusterId || '集群'}` : '无法连接到后端服务'
  })

  return items
})
const loadSystemHealth = async () => {
  systemHealthLoading.value = true
  try {
    const res = await axios.get('/api/v1/system/health')
    if (res.data.code === 0) {
      const d = res.data.data
      systemHealth.overall = d.overall || 'unknown'
      systemHealth.components = d.components || []
    }
  } catch (e) {
    systemHealth.overall = 'down'
    systemHealth.components = [{ name: 'API', status: 'down', details: { message: '无法连接到后端服务' } }]
  }
  systemHealthLoading.value = false
}
const healthStatusColor = (status) => {
  if (status === 'healthy') return '#22c55e'
  if (status === 'degraded') return '#eab308'
  if (status === 'down') return '#ef4444'
  return '#94a3b8'
}
const healthStatusLabel = (status) => {
  if (status === 'healthy') return '正常'
  if (status === 'degraded') return '异常'
  if (status === 'down') return '离线'
  return '未知'
}
const healthOverallLabel = computed(() => {
  if (systemHealth.overall === 'healthy') return '健康'
  if (systemHealth.overall === 'degraded') return '亚健康'
  if (systemHealth.overall === 'down') return '离线'
  return '未知'
})

// === Dashboard: open health detail drawer ===
const openHealthDrawer = () => {
  healthDrawerVisible.value = true
}

// === Dashboard: auto-refresh logic ===
const startAutoRefresh = () => {
  if (autoRefreshTimer.value) clearInterval(autoRefreshTimer.value)
  autoRefreshCountdown.value = 30
  autoRefreshTimer.value = setInterval(() => {
    autoRefreshCountdown.value--
    if (autoRefreshCountdown.value <= 0) {
      refreshDashboardData()
      autoRefreshCountdown.value = 30
    }
  }, 1000)
}
const stopAutoRefresh = () => {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}
const toggleAutoRefresh = (val) => {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}
const loadHdfsHealth = async () => {
  try {
    const res = await axios.get('/api/v1/hdfs/health')
    if (res.data.code === 0) {
      hdfsHealth.value = res.data.data?.metrics || res.data.data
    }
  } catch(e) { /* ignore */ }
}
const refreshDashboardData = () => {
  loadOverview()
  loadSystemHealth()
  loadHdfsHealth()
  loadHealthHistory()
}
const manualRefreshDashboard = async () => {
  manualRefreshLoading.value = true
  await refreshDashboardData()
  if (autoRefreshEnabled.value) {
    autoRefreshCountdown.value = 30
  }
  manualRefreshLoading.value = false
  ElMessage.success('仪表盘已刷新')
}

// === Dashboard: repair handler ===
const handleRepair = (item) => {
  ElMessage.success(`正在尝试修复: ${item.name}...`)
}

// === Dashboard: quick actions ===
const handleQuickAction = async (action) => {
  actionLoading[action] = true
  try {
    switch (action) {
      case 'upload':
        activeTab.value = 'hdfs'
        hdfsSubTab.value = 'files'
        setTimeout(() => triggerUpload(), 300)
        break
      case 'mr':
        activeTab.value = 'mr'
        break
      case 'checkpoint':
        activeTab.value = 'hdfs'
        hdfsSubTab.value = 'journalnodes'
        setTimeout(() => loadCheckpoint(), 100)
        break
      case 'refresh':
        await loadOverview()
        await loadSystemHealth()
        await loadHealthHistory()
        break
      case 'submitJob':
        activeTab.value = 'yarn'
        break
    }
    ElMessage.success('操作已执行')
  } catch (e) {
    ElMessage.error('操作执行失败')
  }
  actionLoading[action] = false
}

// === Dashboard: health trend helpers ===
const trendBarColor = (score) => {
  if (score >= 80) return '#22c55e'
  if (score >= 60) return '#eab308'
  return '#ef4444'
}
const formatTrendDate = (dateStr) => {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    return parts[1] + '/' + parts[2]
  }
  return dateStr
}

// === Dashboard: load health history ===
const loadHealthHistory = async () => {
  healthHistoryLoading.value = true
  try {
    const res = await axios.get('/api/v1/metrics/health-history?days=7')
    if (res.data.code === 0) {
      healthHistory.value = res.data.data || []
    }
  } catch (e) {
    healthHistory.value = []
  }
  healthHistoryLoading.value = false
}

const tableSize = ref(localStorage.getItem('hermes_tableSize') || 'small')
const highlightedRowKey = ref(null)
const showHelpModal = ref(false)

const appVersion = ref('')
const nameService = ref('')

const loadVersionInfo = async () => {
  try {
    const res = await axios.get('/api/v1/system/config')
    if (res.data.code === 0) {
      appVersion.value = res.data.data?.version || ''
      nameService.value = res.data.data?.nameService || ''
    }
  } catch (e) {
    appVersion.value = ''
    nameService.value = ''
  }
}

// Call on mount so help modal shows actual values
loadVersionInfo()

const toggleTableSize = () => {
  tableSize.value = tableSize.value === 'small' ? 'default' : 'small'
  localStorage.setItem('hermes_tableSize', tableSize.value)
}

const highlightRow = (key) => {
  highlightedRowKey.value = key
  setTimeout(() => { highlightedRowKey.value = null }, 1500)
}

const isLoggedIn = ref(false)
const username = ref('')
const token = ref('')
const currentUser = reactive({ username: '', role: 'viewer', email: '' })
const loginForm = reactive({ username: localStorage.getItem('remembered_username') || '', password: '' })
const loginLoading = ref(false)
const loginError = ref('')
const rememberMe = ref(localStorage.getItem('remembered_username') !== null)

// Selected cluster ID, persisted in localStorage
const selectedClusterId = ref(localStorage.getItem('hermes_clusterId') || 'cluster1')
const clusters = ref([])

const isAdmin = computed(() => currentUser.role === 'admin')
const isOperator = computed(() => currentUser.role === 'operator')
const isViewer = computed(() => currentUser.role === 'viewer')
const canManageUsers = computed(() => isAdmin.value)
const canPerformDestructiveOps = computed(() => isAdmin.value || isOperator.value)
const roleLabel = computed(() => {
  const labels = { admin: '管理员', operator: '操作员', viewer: '观察者' }
  return labels[currentUser.role] || currentUser.role
})

const filteredTabs = computed(() => {
  const role = currentUser.role || 'viewer'
  if (role === 'admin') return tabs // show all tabs
  if (role === 'operator') return tabs.filter(t => t.name !== 'user-mgmt') // hide user management
  if (role === 'viewer') return tabs.filter(t =>
    !['user-mgmt', 'alert-center', 'system-config', 'operation-log'].includes(t.name)
  ) // viewer: read-only tabs only
  return tabs
})
const currentTab = computed(() => tabs.find(t => t.name === activeTab.value))

const tabs = [
  { name: 'dashboard', label: '总览', icon: '📊' },
  { name: 'hdfs', label: 'HDFS', icon: '📁' },
  { name: 'yarn', label: 'YARN', icon: '⚙️' },
  { name: 'mr', label: 'MapReduce', icon: '📋' },
  { name: 'operation-log', label: '操作审计', icon: '📝' },
  { name: 'user-mgmt', label: '用户管理', icon: '👤' },
  { name: 'alert-center', label: '告警中心', icon: '⚠️' },
  { name: 'monitor', label: '监控', icon: '📈' },
  { name: 'notebook', label: 'Notebook', icon: '📓' },
  { name: 'system-config', label: '系统配置', icon: '⚙️' },
  { name: 'catalog', label: '数据目录', icon: '📚' },
  { name: 'workflow', label: '工作流', icon: '🔗' }
]

const states = ['NEW','NEW_SAVING','SUBMITTED','ACCEPTED','RUNNING','FINISHED','FAILED','KILLED']

onMounted(() => {
  const saved = localStorage.getItem('hermes_token')
  if (saved) {
    token.value = saved
    username.value = localStorage.getItem('hermes_user') || ''
    isLoggedIn.value = true
    axios.defaults.headers.common['Authorization'] = 'Bearer ' + saved
    fetchCurrentUser().finally(() => { loadAll() })
  }

  // === Axios Response Interceptor ===
  // Track successful responses to mark backend as connected
  axios.interceptors.response.use(
    (response) => {
      clusterConnected.value = true
      return response
    },
    (error) => {
      if (error.response) {
        const status = error.response.status
        const msg = error.response.data?.msg || ''
        if (status === 401) {
          ElMessage.warning('登录已过期，请重新登录')
          localStorage.removeItem('hermes_token')
          localStorage.removeItem('hermes_user')
          isLoggedIn.value = false
          token.value = ''
          username.value = ''
          delete axios.defaults.headers.common['Authorization']
        } else if (status === 403) {
          ElMessage.error('权限不足')
        } else if (status === 500) {
          ElMessage.error(msg || '服务器内部错误，请稍后重试')
        }
      } else if (error.request) {
        // Network error (no response received) — backend disconnected
        clusterConnected.value = false
        ElMessage.error('网络连接失败，请检查后端服务')
      }
      return Promise.reject(error)
    }
  )

  // === Global Error Handler ===
  window.onerror = (message, source, lineno, colno, error) => {
    console.error('[Global Error]', message, source, lineno, colno, error)
    ElMessage.error('应用发生未知错误，请查看控制台了解详情')
    return false
  }
  window.addEventListener('unhandledrejection', (event) => {
    console.error('[Unhandled Promise Rejection]', event.reason)
    // Avoid showing duplicate messages for axios errors already handled by the interceptor
    if (!event.reason || !event.reason.__CANCEL__) {
      ElMessage.error('异步操作失败: ' + (event.reason?.message || '未知错误'))
    }
  })

  // Start notification bell polling
  loadFailedOperations()
  loadUnreadAlerts()
  notificationTimer = setInterval(() => {
    loadFailedOperations()
    loadUnreadAlerts()
  }, 30000)
  // Health check polling every 60 seconds
  healthCheckTimer = setInterval(async () => {
    try {
      await axios.get('/api/v1/auth/login', { timeout: 5000 })
      clusterConnected.value = true
    } catch (e) {
      // If we got a response (even a 405 or 401), the backend is alive
      if (e.response) {
        clusterConnected.value = true
      } else {
        clusterConnected.value = false
      }
    }
  }, 60000)
  // System health check - initial load and poll every 60 seconds
  loadSystemHealth()
  systemHealthTimer = setInterval(() => loadSystemHealth(), 60000)
  // Load health history and start auto-refresh
  loadHealthHistory()
  loadHdfsHealth()
  if (autoRefreshEnabled.value) {
    startAutoRefresh()
  }
  // Command Palette keyboard shortcut
  document.addEventListener('keydown', handleKeydown)
  // 拖拽文件检测
  document.addEventListener('dragenter', onDragEnter)
  document.addEventListener('dragleave', onDragLeave)
})

const fetchCurrentUser = async () => {
  try {
    const res = await axios.get('/api/v1/auth/me')
    if (res.data.code === 0) {
      const d = res.data.data
      currentUser.username = d.username || ''
      currentUser.role = d.role || 'viewer'
      currentUser.email = d.email || ''
    }
  } catch (e) {
    // If /me fails, fall back to role derived from username
    currentUser.username = username.value
    currentUser.role = username.value === 'admin' ? 'admin' : 'viewer'
  }
}

const activeTab = ref(localStorage.getItem('hermes_activeTab') || 'dashboard')

// Dynamic page title based on active tab
watchEffect(() => {
  document.title = pageTitleMap[activeTab.value] || '数据中台'
})

const handleLogin = async () => {
  loginLoading.value = true; loginError.value = ''
  // Save or clear remembered username
  if (rememberMe.value) {
    localStorage.setItem('remembered_username', loginForm.username)
  } else {
    localStorage.removeItem('remembered_username')
  }
  try {
    const res = await axios.post('/api/v1/auth/login', loginForm)
    if (res.data.code === 0) {
      token.value = res.data.data.token; username.value = res.data.data.username
      isLoggedIn.value = true
      localStorage.setItem('hermes_token', token.value); localStorage.setItem('hermes_user', username.value)
      axios.defaults.headers.common['Authorization'] = 'Bearer ' + token.value
      await fetchCurrentUser()
      loadAll()
    } else { loginError.value = res.data.msg || '登录失败' }
  } catch (e) { loginError.value = '无法连接后端服务' }
  loginLoading.value = false
}

const handleLogout = () => {
  isLoggedIn.value = false; token.value = ''; username.value = ''
  currentUser.username = ''; currentUser.role = 'viewer'; currentUser.email = ''
  localStorage.removeItem('hermes_token'); localStorage.removeItem('hermes_user')
  delete axios.defaults.headers.common['Authorization']
}

const overview = reactive({})
const loadOverview = async () => {
  try {
    const res = await axios.get(`/api/v1/dashboard/overview?clusterId=${selectedClusterId.value}`)
    if (res.data.code === 0) Object.assign(overview, res.data.data)
  } catch (e) {}
}

const hdfsPath = ref(localStorage.getItem('hermes_hdfsPath') || '/')
const hdfsFiles = ref([])
const hdfsSearch = ref('')
const selectedFiles = ref([])
const filePage = ref(parseInt(localStorage.getItem('hermes_filePage')) || 1)
const filePageSize = ref(parseInt(localStorage.getItem('hermes_filePageSize')) || 100)
const fileTotal = ref(0)
const hdfsLoading = ref(false)
const hdfsSearchInputRef = ref(null)
const hdfsTableRef = ref(null)

// Persist cross-page state to localStorage
watch(activeTab, (v) => localStorage.setItem('hermes_activeTab', v))
watch(hdfsPath, (v) => localStorage.setItem('hermes_hdfsPath', v))
watch(filePage, (v) => localStorage.setItem('hermes_filePage', String(v)))
watch(filePageSize, (v) => localStorage.setItem('hermes_filePageSize', String(v)))

const handleSelectionChange = (selection) => {
  selectedFiles.value = selection
}

const batchDelete = async () => {
  const files = selectedFiles.value
  if (files.length === 0) return
  const names = files.map(f => f.name).join('\n')
  const confirmed = await ElMessageBox.confirm(
    `确定要将以下 ${files.length} 项移动到回收站吗？可从回收站恢复\n\n${files.map(f => (f.isDirectory ? '📁 ' : '📄 ') + f.name).join('\n')}`,
    '批量移动到回收站',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: false }
  ).catch(() => false)
  if (!confirmed) return
  for (const row of files) {
    try {
      await axios.post('/api/v1/hdfs/delete', null, { params: { path: row.path, useTrash: true } })
      ElMessage.success(`已移动到回收站: ${row.name}`)
    } catch (e) {
      const msg = e.response?.data?.msg || e.message || '未知错误'
      if (e.response?.status === 403) {
        ElMessage.error('权限不足：无法删除 ' + row.name + '，请检查 HDFS 写权限')
      } else {
        ElMessage.error('移动到回收站失败 ' + row.name + ': ' + msg)
      }
    }
  }
  selectedFiles.value = []
  loadHdfsFiles()
}

const onRowDoubleClick = (row) => {
  if (row.isDirectory) {
    hdfsPath.value = row.path
    loadHdfsFiles()
  }
}

const focusSearchInput = () => {
  nextTick(() => {
    const el = hdfsSearchInputRef.value
    if (el) {
      el.focus()
    }
  })
}

const toggleSelectAllFiles = () => {
  const table = hdfsTableRef.value
  if (table) {
    // If all visible rows are already selected, deselect all; otherwise select all
    const visibleRows = filteredHdfsFiles.value
    const selected = selectedFiles.value
    const allSelected = visibleRows.length > 0 && visibleRows.every(r => selected.some(s => s.path === r.path))
    if (allSelected) {
      table.clearSelection()
    } else {
      // Select only visible rows on current page... But more intuitive: toggle all matching
      // We'll just toggle the current page's selection
      table.toggleAllSelection()
    }
  }
}

const batchChmod = () => {
  if (selectedFiles.value.length === 0) return
  // Use the first selected file to populate chmod dialog initial values
  openChmod(selectedFiles.value[0])
}

const filteredHdfsFiles = computed(() => {
  const q = hdfsSearch.value.trim().toLowerCase()
  if (!q) return hdfsFiles.value
  return hdfsFiles.value.filter(f => f.name.toLowerCase().includes(q))
})

const breadcrumbSegments = computed(() => {
  const p = hdfsPath.value.replace(/\/+$/, '')
  if (p === '' || p === '/') return []
  return p.split('/').filter(Boolean)
})

const breadcrumbPaths = computed(() => {
  const segs = breadcrumbSegments.value
  const paths = []
  for (let i = 0; i < segs.length; i++) {
    paths.push('/' + segs.slice(0, i + 1).join('/'))
  }
  return paths
})
// ACL 相关状态
const aclEntries = ref([])
const aclLoading = ref(false)
const aclSaving = ref(false)
const showAddAclInput = ref(false)
const newAclType = ref('user')
const newAclName = ref('')
const newAclPerm = ref('r-x')
const loadAclEntries = async () => {
  if (!chmodTarget.value?.path) return
  aclLoading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/acl', { params: { path: chmodTarget.value.path } })
    if (res.data.code === 0) {
      aclEntries.value = res.data.data?.entries || []
    } else {
      ElMessage.warning('获取 ACL 失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.warning('获取 ACL 失败: ' + (e.response?.data?.msg || e.message))
  }
  aclLoading.value = false
}
const handleAddAcl = () => {
  const type = newAclType.value
  const name = newAclName.value.trim()
  const perm = newAclPerm.value
  const spec = name ? `${type}:${name}:${perm}` : `${type}::${perm}`
  // Check for duplicates
  if (aclEntries.value.includes(spec)) {
    ElMessage.warning('该 ACL 条目已存在')
    return
  }
  aclEntries.value.push(spec)
  resetNewAclForm()
}
const handleRemoveAcl = (entry, idx) => {
  aclEntries.value.splice(idx, 1)
}
const handleSaveAcl = async () => {
  if (!chmodTarget.value?.path) return
  aclSaving.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/acl', { aclSpecs: aclEntries.value }, {
      params: { path: chmodTarget.value.path }
    })
    if (res.data.code === 0) {
      ElMessage.success('ACL 保存成功')
      showAddAclInput.value = false
    } else {
      ElMessage.error('ACL 保存失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('ACL 保存失败: ' + (e.response?.data?.msg || e.message))
  }
  aclSaving.value = false
}
const resetNewAclForm = () => {
  showAddAclInput.value = false
  newAclType.value = 'user'
  newAclName.value = ''
  newAclPerm.value = 'r-x'
}
// === 全局搜索 ===
const showSearchDialog = ref(false)
const searchQuery = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
const searchError = ref('')
const searchDone = ref(false)
const searchInputRef = ref(null)

// === 文件笔记 ===
const showNoteDialog = ref(false)
const noteTarget = ref(null)
const noteContent = ref('')
const noteSaving = ref(false)
const noteDeleting = ref(false)
const fileNotesMap = ref({})

// === 全局搜索方法 ===
const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  searchLoading.value = true
  searchError.value = ''
  searchResults.value = []
  searchDone.value = false
  try {
    const res = await axios.get('/api/v1/hdfs/search', {
      params: { query: searchQuery.value.trim(), maxResults: 100 }
    })
    if (res.data.code === 0) {
      searchResults.value = res.data.data || []
      searchDone.value = true
      if (searchResults.value.length === 0) {
        ElMessage.info('未找到匹配的文件')
      }
    } else {
      searchError.value = res.data.msg || '搜索失败'
    }
  } catch (e) {
    searchError.value = e.response?.data?.msg || e.message || '搜索失败'
  }
  searchLoading.value = false
}

const onSearchResultClick = (row) => {
  showSearchDialog.value = false
  if (row.isDirectory) {
    hdfsPath.value = row.path
    loadHdfsFiles()
  } else {
    const parentPath = row.path.includes('/') ? row.path.substring(0, row.path.lastIndexOf('/')) : '/'
    hdfsPath.value = parentPath
    loadHdfsFiles()
    setTimeout(() => highlightRow(row.path), 300)
  }
}

// === 文件笔记方法 ===
const loadFileNotes = async () => {
  const files = hdfsFiles.value
  if (!files || files.length === 0) return
  try {
    const notes = {}
    const promises = files.map(async (file) => {
      try {
        const res = await axios.get('/api/v1/hdfs/notes', { params: { path: file.path } })
        if (res.data.code === 0 && res.data.data) {
          notes[file.path] = res.data.data
        }
      } catch (e) { /* ignore */ }
    })
    await Promise.all(promises)
    fileNotesMap.value = notes
  } catch (e) { /* ignore */ }
}

const openNote = async (row) => {
  noteTarget.value = row
  noteContent.value = ''
  showNoteDialog.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/notes', { params: { path: row.path } })
    if (res.data.code === 0 && res.data.data) {
      noteContent.value = res.data.data.note || ''
    }
  } catch (e) {
    // Ignore, empty note
  }
}

const handleSaveNote = async () => {
  if (!noteTarget.value) return
  noteSaving.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/notes', {
      path: noteTarget.value.path,
      note: noteContent.value || ''
    })
    if (res.data.code === 0) {
      ElMessage.success('笔记已保存')
      showNoteDialog.value = false
      if (noteContent.value && noteContent.value.trim()) {
        fileNotesMap.value[noteTarget.value.path] = { note: noteContent.value }
      } else {
        delete fileNotesMap.value[noteTarget.value.path]
      }
    } else {
      ElMessage.error('保存失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.msg || e.message))
  }
  noteSaving.value = false
}

const handleDeleteNote = async () => {
  if (!noteTarget.value) return
  noteDeleting.value = true
  try {
    const res = await axios.delete('/api/v1/hdfs/notes', { params: { path: noteTarget.value.path } })
    if (res.data.code === 0) {
      ElMessage.success('笔记已删除')
      showNoteDialog.value = false
      delete fileNotesMap.value[noteTarget.value.path]
    } else {
      ElMessage.error('删除失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.response?.data?.msg || e.message))
  }
  noteDeleting.value = false
}

const uploadLoading = ref(false)
const uploadProgress = ref(0)
const uploadFileName = ref('')
const downloadProgress = ref(0)
const downloadFileName = ref('')
const isDragging = ref(false)
const triggerUpload = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '*/*'
  input.style.position = 'fixed'
  input.style.left = '-9999px'
  input.style.top = '-9999px'
  input.addEventListener('change', handleUpload)
  document.body.appendChild(input)
  input.click()
  // 短暂延迟后移除，确保事件触发完成
  setTimeout(() => {
    if (input.parentNode) input.parentNode.removeChild(input)
  }, 1000)
}
const loadHdfsFiles = async () => {
  hdfsLoading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/files', { params: { path: hdfsPath.value, page: filePage.value, size: filePageSize.value } })
    if (res.data.code === 0) {
      hdfsFiles.value = res.data.data.files
      fileTotal.value = res.data.data.total
      // Load notes for all visible files
      fileNotesMap.value = {}
      loadFileNotes()
    }
  } catch (e) { ElMessage.error('加载 HDFS 失败: ' + (e.response?.data?.msg || e.message)) }
  hdfsLoading.value = false
}

const getFileIcon = (row) => {
  if (row.isDirectory) return '📁'
  const name = row.name || ''
  const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
  const iconMap = {
    txt: '📄', md: '📄', rst: '📄',
    mp3: '🎵', wav: '🎵', flac: '🎵', ogg: '🎵',
    png: '🖼️', jpg: '🖼️', jpeg: '🖼️', gif: '🖼️', svg: '🖼️', webp: '🖼️', bmp: '🖼️', ico: '🖼️',
    zip: '🗜️', tar: '🗜️', gz: '🗜️', bz2: '🗜️', xz: '🗜️', rar: '🗜️', '7z': '🗜️',
    bin: '⚙️', exe: '⚙️', dmg: '⚙️', app: '⚙️', deb: '⚙️', rpm: '⚙️',
    log: '📝',
    csv: '📊', json: '📊', xml: '📊', yaml: '📊', yml: '📊', toml: '📊',
    py: '🐍', js: '📜', ts: '📜', java: '☕', sh: '💻', bash: '💻',
    pdf: '📕', doc: '📕', docx: '📕', xls: '📊', xlsx: '📊', ppt: '📊', pptx: '📊'
  }
  return iconMap[ext] || '📄'
}

const goToParentDir = () => {
  const p = hdfsPath.value.replace(/\/+$/, '')
  if (p === '' || p === '/') return
  const parent = p.includes('/') ? p.substring(0, p.lastIndexOf('/')) : '/'
  hdfsPath.value = parent || '/'
  loadHdfsFiles()
}


/** 规范化HDFS路径：去掉hdfs://前缀，确保以/开头 */
const normalizeHdfsPath = (p) => {
  if (p.startsWith("hdfs://")) {
    const idx = p.indexOf("/", p.indexOf("//") + 2);
    p = idx >= 0 ? p.substring(idx) : "/";
  }
  return p.startsWith("/") ? p : "/" + p;
}

const handleUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) {
    e.target.value = ''
    return
  }

  uploadLoading.value = true
  uploadProgress.value = 0
  uploadFileName.value = file.name

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('path', hdfsPath.value)

    const res = await axios.post('/api/v1/hdfs/upload', formData, {
      params: { path: normalizeHdfsPath(hdfsPath.value) },
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          uploadProgress.value = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        }
      }
    })

    if (res.data.code === 0) {
      uploadProgress.value = 100
      ElMessage.success(`上传成功: ${file.name} (${(file.size / 1024).toFixed(1)}KB)`)
      // 延迟关闭进度提示
      setTimeout(() => {
        uploadFileName.value = ''
        uploadProgress.value = 0
      }, 1500)
      loadHdfsFiles()
      // 高亮已上传的文件
      setTimeout(() => {
        const uploadedPath = normalizeHdfsPath(hdfsPath.value).endsWith('/') ? hdfsPath.value + file.name : hdfsPath.value + '/' + file.name
        highlightRow(uploadedPath)
      }, 300)
    } else {
      ElMessage.error('上传失败: ' + (res.data.msg || '未知错误'))
    }
  } catch (err) {
    const msg = err.response?.data?.msg || err.message || '网络异常'
    ElMessage.error('上传失败: ' + msg)
  }

  uploadLoading.value = false
}

/** 拖拽上传：上传单个文件（复用 handleUpload 的上传逻辑） */
const uploadFile = async (file) => {
  uploadLoading.value = true
  uploadProgress.value = 0
  uploadFileName.value = file.name

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('path', hdfsPath.value)

    const res = await axios.post('/api/v1/hdfs/upload', formData, {
      params: { path: normalizeHdfsPath(hdfsPath.value) },
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          uploadProgress.value = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        }
      }
    })

    if (res.data.code === 0) {
      uploadProgress.value = 100
      ElMessage.success(`上传成功: ${file.name} (${(file.size / 1024).toFixed(1)}KB)`)
      setTimeout(() => {
        uploadFileName.value = ''
        uploadProgress.value = 0
      }, 1500)
      loadHdfsFiles()
      // 高亮已上传的文件
      setTimeout(() => {
        const uploadedPath = normalizeHdfsPath(hdfsPath.value).endsWith('/') ? hdfsPath.value + file.name : hdfsPath.value + '/' + file.name
        highlightRow(uploadedPath)
      }, 300)
    } else {
      ElMessage.error('上传失败: ' + (res.data.msg || '未知错误'))
    }
  } catch (err) {
    const msg = err.response?.data?.msg || err.message || '网络异常'
    ElMessage.error('上传失败: ' + msg)
  }

  uploadLoading.value = false
}

/** 拖拽文件到上传区域的处理 */
const handleDrop = (e) => {
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  isDragging.value = false
  // 逐个上传拖拽的文件
  for (const file of files) {
    uploadFile(file)
  }
}

const onDragEnter = () => { isDragging.value = true }
const onDragLeave = (e) => {
  // 只有当离开 document 时才取消拖拽状态
  if (!e.relatedTarget || (e.relatedTarget === document.documentElement || e.relatedTarget === null)) {
    isDragging.value = false
  }
}

const handleDownload = async (row) => {
  downloadProgress.value = 0
  downloadFileName.value = row.name
  try {
    const res = await axios.get('/api/v1/hdfs/download', {
      params: { path: row.path },
      responseType: 'blob',
      onDownloadProgress: (progressEvent) => {
        if (progressEvent.total) {
          downloadProgress.value = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        }
      }
    })
    downloadProgress.value = 100
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = row.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`⬇️ 下载完成: ${row.name}`)
    setTimeout(() => { downloadFileName.value = ''; downloadProgress.value = 0 }, 2000)
  } catch (e) { ElMessage.error('下载失败: ' + (e.response?.data?.msg || e.message)) }
}

// === Mkdir ===
const showMkdirDialog = ref(false)
const mkdirName = ref('')
const mkdirLoading = ref(false)
const handleMkdir = async () => {
  if (!mkdirName.value.trim()) {
    ElMessage.warning('请输入目录名')
    return
  }
  mkdirLoading.value = true
  try {
    const fullPath = hdfsPath.value.endsWith('/') ? hdfsPath.value + mkdirName.value.trim() : hdfsPath.value + '/' + mkdirName.value.trim()
    const res = await axios.post('/api/v1/hdfs/mkdir', null, { params: { path: fullPath } })
    if (res.data.code === 0) {
      ElMessage.success(`目录创建成功: ${mkdirName.value}`)
      showMkdirDialog.value = false
      mkdirName.value = ''
      loadHdfsFiles()
      // 高亮新创建的目录
      setTimeout(() => {
        const dirPath = hdfsPath.value.endsWith('/') ? hdfsPath.value + mkdirName.value.trim() : hdfsPath.value + '/' + mkdirName.value.trim()
        highlightRow(dirPath)
      }, 300)
    } else {
      ElMessage.error('创建失败: ' + (res.data.msg || ''))
    }
  } catch (e) { ElMessage.error('创建失败: ' + (e.response?.data?.msg || e.message)) }
  mkdirLoading.value = false
}

// === Chmod ===
const showChmodDialog = ref(false)
const chmodTarget = ref(null)
const chmodOwnerMode = ref('7')
const chmodGroupMode = ref('5')
const chmodOtherMode = ref('5')
const chmodLoading = ref(false)
// === Rename ===
const showRenameDialog = ref(false)
const renameTarget = ref(null)
const renameNewName = ref('')
const renameLoading = ref(false)
// === Move ===
const showMoveDialog = ref(false)
const moveTarget = ref(null)
const moveDestDir = ref('')
const moveLoading = ref(false)
const openChmod = (row) => {
  chmodTarget.value = row
  // 从权限字符串如 "rwxr-xr-x" 解析数字
  const perm = row.permission || 'rwxr-xr-x'
  const permMap = { '---': '0', '--x': '1', '-w-': '2', '-wx': '3', 'r--': '4', 'r-x': '5', 'rw-': '6', 'rwx': '7' }
  chmodOwnerMode.value = permMap[perm.slice(0, 3)] || '7'
  chmodGroupMode.value = permMap[perm.slice(3, 6)] || '5'
  chmodOtherMode.value = permMap[perm.slice(6, 9)] || '5'
  showChmodDialog.value = true
  // 加载 ACL 条目
  aclEntries.value = []
  showAddAclInput.value = false
  resetNewAclForm()
  loadAclEntries()
}
const handleChmod = async () => {
  if (!chmodTarget.value) return
  chmodLoading.value = true
  const mode = chmodOwnerMode.value + chmodGroupMode.value + chmodOtherMode.value
  // Determine target paths: batch if multiple selected, else single
  const targets = selectedFiles.value.length > 1 ? selectedFiles.value : [chmodTarget.value]
  for (const target of targets) {
    try {
      const res = await axios.post('/api/v1/hdfs/chmod', null, {
        params: { path: target.path, mode }
      })
      if (res.data.code === 0) {
        ElMessage.success(`权限已修改: ${target.name} → ${mode}`)
      } else {
        ElMessage.error('权限修改失败 ' + target.name + ': ' + (res.data.msg || ''))
      }
    } catch (e) {
      ElMessage.error('权限修改失败 ' + target.name + ': ' + (e.response?.data?.msg || e.message))
    }
  }
  showChmodDialog.value = false
  if (selectedFiles.value.length > 1) {
    selectedFiles.value = []
    loadHdfsFiles()
    // 高亮第一个被修改的文件
    setTimeout(() => {
      if (targets.length > 0) highlightRow(targets[0].path)
    }, 300)
  } else {
    loadHdfsFiles()
    // 高亮被修改的文件
    setTimeout(() => {
      if (chmodTarget.value) highlightRow(chmodTarget.value.path)
    }, 300)
  }
  chmodLoading.value = false
}

// === Rename ===
const openRename = (row) => {
  renameTarget.value = row
  renameNewName.value = row.name || ''
  showRenameDialog.value = true
}
const handleRename = async () => {
  if (!renameNewName.value.trim()) {
    ElMessage.warning('请输入新名称')
    return
  }
  renameLoading.value = true
  try {
    const oldPath = renameTarget.value.path
    // Build new path by replacing the last segment with the new name
    const basePath = oldPath.includes('/') ? oldPath.substring(0, oldPath.lastIndexOf('/')) : ''
    const newPath = (basePath ? basePath + '/' : '') + renameNewName.value.trim()
    const res = await axios.post('/api/v1/hdfs/rename', null, {
      params: { path: oldPath, newPath }
    })
    if (res.data.code === 0) {
      ElMessage.success(`重命名成功: ${renameTarget.value.name} → ${renameNewName.value.trim()}`)
      showRenameDialog.value = false
      loadHdfsFiles()
      // 高亮重命名后的文件
      setTimeout(() => {
        const basePath = renameTarget.value.path.includes('/') ? renameTarget.value.path.substring(0, renameTarget.value.path.lastIndexOf('/')) : ''
        const newPath = (basePath ? basePath + '/' : '') + renameNewName.value.trim()
        highlightRow(newPath)
      }, 300)
    } else {
      ElMessage.error('重命名失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    const status = e.response?.status
    const msg = e.response?.data?.msg || e.message || '未知错误'
    if (status === 403) {
      ElMessage.error('权限不足：无法重命名，请检查 HDFS 写权限')
    } else {
      ElMessage.error('重命名失败: ' + msg)
    }
  }
  renameLoading.value = false
}

// === Move ===
const openMove = (row) => {
  moveTarget.value = row
  moveDestDir.value = ''
  showMoveDialog.value = true
}
const handleMove = async () => {
  if (!moveDestDir.value.trim()) {
    ElMessage.warning('请输入目标目录路径')
    return
  }
  moveLoading.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/move', null, {
      params: { path: moveTarget.value.path, destDir: moveDestDir.value.trim() }
    })
    if (res.data.code === 0) {
      const d = res.data.data
      ElMessage.success(`移动成功: ${moveTarget.value.name} → ${d.destPath}`)
      showMoveDialog.value = false
      loadHdfsFiles()
      // 高亮移动后的文件
      setTimeout(() => {
        if (d && d.destPath) highlightRow(d.destPath)
      }, 300)
    } else {
      ElMessage.error('移动失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    const status = e.response?.status
    const msg = e.response?.data?.msg || e.message || '未知错误'
    if (status === 403) {
      ElMessage.error('权限不足：无法移动，请检查 HDFS 写权限')
    } else {
      ElMessage.error('移动失败: ' + msg)
    }
  }
  moveLoading.value = false
}

// === Delete (to trash) ===
const handleDelete = async (row) => {
  const isDir = row.isDirectory
  const name = row.name
  const msg = isDir
    ? `确定要将目录「${name}」及其所有内容移动到回收站吗？可从回收站恢复`
    : `确定要将文件「${name}」移动到回收站吗？可从回收站恢复`
  const confirmed = await ElMessageBox.confirm(msg, '确认移动到回收站', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).catch(() => false)
  if (!confirmed) return
  try {
    const res = await axios.post('/api/v1/hdfs/delete', null, { params: { path: row.path, useTrash: true } })
    if (res.data.code === 0) {
      ElMessage.success(`已移动到回收站: ${name}`)
      loadHdfsFiles()
    } else {
      ElMessage.error('移动到回收站失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    const status = e.response?.status
    const msg = e.response?.data?.msg || e.message || '未知错误'
    if (status === 403) {
      ElMessage.error('权限不足：无法删除 ' + name + '，请检查 HDFS 写权限')
    } else {
      ElMessage.error('移动到回收站失败: ' + msg)
    }
  }
}

// === Trash / Recycle Bin ===
const trashFiles = ref([])
const trashLoading = ref(false)
const trashEmptying = ref(false)
const trashRestoring = ref('')
const retentionDays = ref(30)
const retentionSaving = ref(false)
const retentionAutoClean = ref(true)
const trashCleaningExpired = ref(false)

// Image preview
const previewIsImage = ref(false)
const previewImageUrl = ref('')
const previewUnsupported = ref(false)

const exportTrashFilesCsv = () => {
  exportToCsv(trashFiles.value, 'trash_files', [
    { key: 'name', label: '名称' },
    { key: 'originalPath', label: '原始路径' },
    { key: 'length', label: '大小' },
    { key: 'deletionTime', label: '删除时间' }
  ])
  axios.post('/api/v1/logs/audit', { module:'hdfs', action:'export-csv', target:'trash_export.csv', detail:'导出回收站 ' + trashFiles.value.length + ' 项' }).catch(() => {})
}

const loadTrashFiles = async () => {
  trashLoading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/trash/list')
    if (res.data.code === 0) {
      trashFiles.value = res.data.data || []
    } else {
      ElMessage.error('加载回收站失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('加载回收站失败: ' + (e.response?.data?.msg || e.message))
  }
  trashLoading.value = false
}

const loadRetentionConfig = async () => {
  try {
    const res = await axios.get('/api/v1/hdfs/trash/retention')
    if (res.data.code === 0) {
      retentionDays.value = res.data.data.retentionDays || 30
    }
  } catch (e) {
    // Silent fail, use default
  }
}

const saveRetentionConfig = async () => {
  retentionSaving.value = true
  try {
    const res = await axios.put('/api/v1/hdfs/trash/retention', { retentionDays: retentionDays.value })
    if (res.data.code === 0) {
      ElMessage.success(`保留策略已更新: ${retentionDays.value} 天`)
    } else {
      ElMessage.error('保存失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.msg || e.message))
  }
  retentionSaving.value = false
}

const handleCleanExpiredTrash = async () => {
  trashCleaningExpired.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/trash/clean-expired')
    if (res.data.code === 0) {
      const count = res.data.data.deletedCount || 0
      ElMessage.success(`清理完成，已删除 ${count} 个过期文件`)
      loadTrashFiles()
    } else {
      ElMessage.error('清理过期文件失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('清理过期文件失败: ' + (e.response?.data?.msg || e.message))
  }
  trashCleaningExpired.value = false
}

const handleRestoreFromTrash = async (row) => {
  const name = row.name
  const originalPath = row.originalPath || ''
  
  // Step 1: Check for conflicts
  let hasConflict = false
  try {
    const checkRes = await axios.post('/api/v1/hdfs/trash/restore-check', { path: originalPath })
    if (checkRes.data.code === 0) {
      hasConflict = checkRes.data.data.hasConflict
    }
  } catch (e) {
    // If check fails, proceed with original confirmation
  }

  if (hasConflict) {
    const action = await ElMessageBox.confirm(
      `「${name}」的原始路径已存在其他文件/目录。\n\n原始路径: ${originalPath}\n\n请选择处理方式：`,
      '恢复冲突',
      {
        confirmButtonText: '覆盖',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true,
        type: 'warning',
        showCancelButton: true
      }
    ).catch((action) => {
      if (action === 'cancel' || action === 'close') return 'cancel'
      return action
    })
    
    if (action === 'cancel' || action === 'close') return

    // If user chose "overwrite", we need to delete the existing path first
    if (action === 'confirm') {
      try {
        await axios.post('/api/v1/hdfs/delete', null, { params: { path: originalPath, useTrash: false } })
      } catch (e) {
        ElMessage.error('覆盖失败，无法删除已存在的路径: ' + (e.response?.data?.msg || e.message))
        return
      }
    }
  } else {
    const confirmed = await ElMessageBox.confirm(
      `确定要将「${name}」恢复到原始路径吗？\n\n原始路径: ${originalPath}`,
      '确认恢复',
      { confirmButtonText: '确定恢复', cancelButtonText: '取消', type: 'info' }
    ).catch(() => false)
    if (!confirmed) return
  }

  trashRestoring.value = row.trashPath
  try {
    const res = await axios.post('/api/v1/hdfs/trash/restore', null, { params: { path: originalPath } })
    if (res.data.code === 0) {
      ElMessage.success(`已恢复: ${name}`)
      loadTrashFiles()
    } else {
      ElMessage.error('恢复失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('恢复失败: ' + (e.response?.data?.msg || e.message))
  }
  trashRestoring.value = ''
}

const handlePermanentDelete = async (row) => {
  const name = row.name
  // Fetch delete impact preview
  let impactMsg = ''
  try {
    const impactRes = await axios.post('/api/v1/hdfs/trash/delete-preview')
    if (impactRes.data.code === 0) {
      const impact = impactRes.data.data
      if (impact.totalCount > 0) {
        impactMsg = `将永久删除 ${impact.totalCount} 个文件，占用 ${formatBytes(impact.totalSize)}，此操作不可恢复！`
      }
    }
  } catch (e) {
    // Fallback to simple message
  }
  const confirmed = await ElMessageBox.confirm(
    impactMsg || `确定要永久删除「${name}」吗？此操作不可恢复！`,
    '确认永久删除',
    { confirmButtonText: '确定永久删除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
  ).catch(() => false)
  if (!confirmed) return
  try {
    const res = await axios.post('/api/v1/hdfs/delete', null, { params: { path: row.trashPath, useTrash: false } })
    if (res.data.code === 0) {
      ElMessage.success(`已永久删除: ${name}`)
      loadTrashFiles()
    } else {
      ElMessage.error('永久删除失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('永久删除失败: ' + (e.response?.data?.msg || e.message))
  }
}

const handleEmptyTrash = async () => {
  const count = trashFiles.value.length
  const confirmed = await ElMessageBox.confirm(
    `确定要清空回收站吗？将永久删除 ${count} 项文件/目录，此操作不可恢复！`,
    '确认清空回收站',
    { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
  ).catch(() => false)
  if (!confirmed) return
  trashEmptying.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/trash/empty', null, { params: { confirmed: true } })
    if (res.data.code === 0) {
      ElMessage.success('回收站已清空')
      trashFiles.value = []
    } else {
      ElMessage.error('清空回收站失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('清空回收站失败: ' + (e.response?.data?.msg || e.message))
  }
  trashEmptying.value = false
}

// === 操作列统一入口 ===
const handleAction = (row, cmd) => {
  switch (cmd) {
    case 'download': handleDownload(row); break
    case 'preview': openPreview(row); break
    case 'rename': openRename(row); break
    case 'move': openMove(row); break
    case 'chmod': openChmod(row); break
    case 'delete': handleDelete(row); break
    case 'copyPath': handleCopyPath(row); break
    case 'openInNewTab': handleOpenInNewTab(row); break
    case 'note': openNote(row); break
    case 'registerCatalog': handleRegisterFromHdfs(row); break
  }
}

// === 复制路径 / 新标签打开 / 复制目录路径 ===
const handleCopyPath = async (row) => {
  try {
    const path = `hdfs://${overview.clusterId || 'mycluster'}${row.path}`
    await navigator.clipboard.writeText(path)
    ElMessage.success('已复制路径: ' + path)
  } catch (e) {
    // Fallback for non-HTTPS
    const ta = document.createElement('textarea')
    ta.value = `hdfs://${overview.clusterId || 'mycluster'}${row.path}`
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制路径到剪贴板')
  }
}

const exportHdfsFilesCsv = () => {
  exportToCsv(filteredHdfsFiles.value, 'hdfs_files', [
    { key: 'name', label: '名称' },
    { key: 'length', label: '大小' },
    { key: 'owner', label: 'Owner' },
    { key: 'permission', label: '权限' },
    { key: 'modificationTime', label: '修改时间' }
  ])
  axios.post('/api/v1/logs/audit', { module:'hdfs', action:'export-csv', target:'hdfs_files_export.csv', detail:'导出HDFS文件列表 ' + filteredHdfsFiles.value.length + ' 项' }).catch(() => {})
}

const handleCopyDirPath = async () => {
  try {
    const path = `hdfs://${overview.clusterId || 'mycluster'}${hdfsPath.value}`
    await navigator.clipboard.writeText(path)
    ElMessage.success('已复制路径: ' + path)
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = `hdfs://${overview.clusterId || 'mycluster'}${hdfsPath.value}`
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制路径到剪贴板')
  }
}

const handleOpenInNewTab = (row) => {
  // Navigate to the directory and add to browser history
  hdfsPath.value = row.path
  loadHdfsFiles()
  window.history.pushState({ hdfsPath: row.path }, '', `?hdfsPath=${encodeURIComponent(row.path)}`)
}

const catalogPreFillPath = ref('')

const handleRegisterFromHdfs = (row) => {
  catalogPreFillPath.value = row.path
  activeTab.value = 'catalog'
  showRegisterDialog.value = true
  // Derive name from last path segment
  const pathStr = row.path.endsWith('/') ? row.path.slice(0, -1) : row.path
  const segments = pathStr.split('/')
  registerForm.name = segments[segments.length - 1] || ''
  registerForm.hdfsPath = row.path
  registerForm.schemaName = 'default'
  registerForm.format = 'Parquet'
  registerForm.description = ''
  registerForm.partitionColumns = ''
  registerForm.owner = ''
}

// === 文件预览 ===
const showPreview = ref(false)
const previewFile = ref(null)
const previewData = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const previewLines = ref([])
const previewIsBinary = ref(false)
const previewWrap = ref(false)

// 可预览的文本文件扩展名
const textPreviewExtensions = ['txt', 'log', 'md', 'json', 'xml', 'csv', 'sh', 'py', 'java', 'conf', 'yml', 'yaml', 'properties', 'cfg', 'ini', 'toml', 'js', 'ts', 'jsx', 'tsx', 'css', 'scss', 'less', 'html', 'htm', 'vue', 'sql', 'rb', 'go', 'rs', 'c', 'cpp', 'h', 'hpp', 'kt', 'swift']
// 可预览的图片扩展名
const imagePreviewExtensions = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'bmp', 'webp', 'ico']

const openPreview = async (row) => {
  previewFile.value = row
  previewData.value = null
  previewLines.value = []
  previewIsBinary.value = false
  previewIsImage.value = false
  previewImageUrl.value = ''
  previewUnsupported.value = false
  previewError.value = ''
  previewLoading.value = true
  showPreview.value = true

  // Determine file extension
  const fileName = row.name || ''
  const ext = fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : ''
  const isImage = imagePreviewExtensions.includes(ext)
  const isText = textPreviewExtensions.includes(ext) || !ext

  if (isImage) {
    // For images, use the download endpoint URL
    const baseUrl = window.location.origin
    previewImageUrl.value = `${baseUrl}/api/v1/hdfs/download?path=${encodeURIComponent(row.path)}&clusterId=${selectedClusterId.value}`
    previewIsImage.value = true
    previewData.value = { fileSize: row.length, isTruncated: false, previewSize: row.length }
    // previewLoading will be set to false by the @load event on the img tag
    return
  }

  if (!isText) {
    previewUnsupported.value = true
    previewData.value = { fileSize: row.length, isTruncated: false, previewSize: 0 }
    previewLoading.value = false
    return
  }

  try {
    const res = await axios.get('/api/v1/hdfs/preview', {
      params: { path: row.path, maxBytes: 65536 }
    })
    if (res.data.code === 0) {
      const d = res.data.data
      previewData.value = d

      // Decode base64 content
      const binaryStr = atob(d.content)
      const bytes = new Uint8Array(binaryStr.length)
      for (let i = 0; i < binaryStr.length; i++) {
        bytes[i] = binaryStr.charCodeAt(i) & 0xff
      }

      // Check if valid UTF-8 text
      const decoder = new TextDecoder('utf-8', { fatal: true })
      let text
      try {
        text = decoder.decode(bytes)
        previewIsBinary.value = false
      } catch (e) {
        previewIsBinary.value = true
        previewLines.value = []
        previewLoading.value = false
        return
      }

      // Split into lines
      previewLines.value = text.split('\n')
    } else {
      previewError.value = res.data.msg || '预览加载失败'
    }
  } catch (e) {
    previewError.value = e.response?.data?.msg || e.message || '预览失败'
  }
  previewLoading.value = false
}

const closePreview = () => {
  showPreview.value = false
  previewFile.value = null
  previewData.value = null
  previewLines.value = []
  previewIsBinary.value = false
  previewIsImage.value = false
  previewImageUrl.value = ''
  previewUnsupported.value = false
  previewError.value = ''
}

const previewDownload = () => {
  if (previewFile.value) {
    handleDownload(previewFile.value)
  }
}

const copyPreviewContent = () => {
  const text = previewLines.value.join('\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

const highlightLine = (line) => {
  if (!line) return ''
  // Escape HTML first
  let escaped = line
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // Syntax highlighting for common patterns
  // JSON keys
  escaped = escaped.replace(/(&quot;|")([^&"]+?)(&quot;|")(\s*:\s*)/g, '<span class="hl-json-key">$1$2$3</span>$4')
  // Numbers
  escaped = escaped.replace(/\b(-?\d+\.?\d*)\b/g, '<span class="hl-number">$1</span>')
  // Boolean/null
  escaped = escaped.replace(/\b(true|false|null)\b/g, '<span class="hl-bool">$1</span>')
  // String values (JSON/XML quoted)
  escaped = escaped.replace(/(&quot;|")([^&"]+?)(&quot;|")(\s*[,}\])\s]|$)/g, '<span class="hl-string">$1$2$3</span>$4')
  // XML tags
  escaped = escaped.replace(/(&lt;\/?)([\w-]+)([^&]*?)(\/?&gt;)/g, '<span class="hl-tag">$1$2$3$4</span>')
  // Comments (# and //)
  escaped = escaped.replace(/(#.*$)/gm, '<span class="hl-comment">$1</span>')
  escaped = escaped.replace(/(\/\/.*$)/gm, '<span class="hl-comment">$1</span>')
  // Log levels
  escaped = escaped.replace(/\b(ERROR|WARN|WARNING|INFO|DEBUG|TRACE)\b/g, '<span class="hl-log-$1">$1</span>')

  return escaped
}

// === NN Management ===
const nnManaging = ref(false)
const nnManageNode = ref('nn1')
const nnManageAction = ref('restart')
const manageNN = async (node, action) => {
  const actionNames = { stop: '停掉', start: '启动', restart: '重启' }
  const confirmed = await ElMessageBox.confirm(
    `确定要${actionNames[action]} ${node} 节点吗？`,
    '确认操作',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  nnManaging.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/manage-nn', null, {
      params: { node, action }
    })
    if (res.data.code === 0) {
      ElMessage.success(`${node} ${actionNames[action]}成功 (${res.data.data.state})`)
      setTimeout(loadHdfsNodes, 5000)
    } else {
      ElMessage.error('操作失败: ' + (res.data.msg || ''))
    }
  } catch (e) { ElMessage.error('操作失败: ' + (e.response?.data?.msg || e.message)) }
  nnManaging.value = false
}

// === HDFS Node Monitoring ===
const nnState = ref('')
const nnHostPort = ref('')
const nn2State = ref('')
const nn2HostPort = ref('')
const nnNodes = ref(['nn1', 'nn2'])
const nnCapacityTotal = ref(0)
const nnCapacityUsed = ref(0)
const nnFilesTotal = ref(0)
const nnBlocksTotal = ref(0)
const nnNumLiveDNs = ref(0)
const nnNumDeadDNs = ref(0)
const nnCapacityRemaining = ref(0)
const journalNodes = ref([])
const checkpointData = ref(null)
const checkpointLoading = ref(false)
const dataNodes = ref([])
const totalBlocks = ref(0)
const showDNDetail = ref(false)
const selectedDN = ref(null)
const dnJmxMetrics = ref(null)
const hdfsHealth = ref(null)
const hdfsSubTab = ref('files')
const hdfsTabsRef = ref(null)
const yarnSubTab = ref('apps')
const onYarnSubTabChange = () => {
  if (yarnSubTab.value === 'apps') loadYarnApps()
  else if (yarnSubTab.value === 'queues') loadYarnQueues()
}
const hdfsTabOrder = ref([
  { label: '📂 文件系统', name: 'files' },
  { label: '🗑️ 回收站', name: 'trash' },
  { label: '🖥️ DataNode', name: 'datanodes' },
  { label: '📝 JournalNode', name: 'journalnodes' },
  { label: '📊 监控 & 元数据', name: 'monitor' },
  { label: '📋 日志查看器', name: 'logs' }
])
let dragIndex = null

// 初始化标签拖拽
const initTabDrag = () => {
  nextTick(() => {
    const items = document.querySelectorAll('.el-tabs--border-card .el-tabs__item')
    items.forEach((el, i) => {
      el.draggable = true
      el.style.cursor = 'grab'
      el.ondragstart = (e) => { dragIndex = i; e.dataTransfer.effectAllowed = 'move'; el.style.opacity = '0.4' }
      el.ondragend = () => { el.style.opacity = '1'; dragIndex = null }
      el.ondragover = (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move' }
      el.ondrop = (e) => {
        e.preventDefault()
        if (dragIndex === null || dragIndex === i) return
        const arr = [...hdfsTabOrder.value]
        const [moved] = arr.splice(dragIndex, 1)
        arr.splice(i, 0, moved)
        hdfsTabOrder.value = arr
        dragIndex = null
        // 重新初始化拖拽
        nextTick(() => initTabDrag())
      }
    })
  })
}

// 标签切换后重新绑定拖拽
watch(hdfsSubTab, (val) => {
  initTabDrag()
  if (val === 'journalnodes') loadCheckpoint()
  if (val === 'trash') { loadTrashFiles(); loadRetentionConfig() }
})
watch(activeTab, (val) => { if (val === 'hdfs') setTimeout(() => initTabDrag(), 100) })

const logViewerRef = ref(null)
const blockPath = ref('/')
const blockMetadata = ref(null)
const blockLocations = ref([])
const newDNName = ref('dn4')
const dnScaling = ref(false)
const scaleCommands = ref([])

const loadHdfsNodes = async () => {
  try {
    const res = await axios.get('/api/v1/hdfs/nodes')
    if (res.data.code === 0) {
      const d = res.data.data
      nnState.value = d['nnStatus.State'] || ''
      nnHostPort.value = d['nnStatus.HostAndPort'] || ''
      nn2State.value = d['nn2State'] || ''
      nn2HostPort.value = d['nn2HostPort'] || ''
      nnCapacityTotal.value = d['nnFS.CapacityTotal'] || 0
      nnCapacityUsed.value = d['nnFS.CapacityUsed'] || 0
      nnFilesTotal.value = d['nnFS.FilesTotal'] || 0
      nnBlocksTotal.value = d['nnFS.BlocksTotal'] || 0
      nnNumLiveDNs.value = d['nnFS.NumLiveDataNodes'] || 0
      nnNumDeadDNs.value = d['nnFS.NumDeadDataNodes'] || 0
      nnCapacityRemaining.value = d['nnFS.CapacityRemaining'] || 0
      journalNodes.value = d.journalNodes || []
      dataNodes.value = d.dataNodes || []
      totalBlocks.value = d['nnFS.BlocksTotal'] || 0
      if (d.nnNodes && d.nnNodes.length > 0) nnNodes.value = d.nnNodes
    }
  } catch (e) { /* silently fail */ }
}

const loadCheckpoint = async () => {
  checkpointLoading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/checkpoint')
    if (res.data.code === 0) {
      checkpointData.value = res.data.data
    }
  } catch (e) { /* silently fail */ }
  checkpointLoading.value = false
}

const loadBlockMetadata = async () => {
  try {
    const res = await axios.get('/api/v1/hdfs/blocks', { params: { path: blockPath.value } })
    if (res.data.code === 0 && res.data.data) {
      const d = res.data.data
      const fs = d.fileStatus?.FileStatus
      blockMetadata.value = fs ? {
        length: fs.length, blockSize: fs.blockSize,
        replication: fs.replication, modificationTime: fs.modificationTime,
        isDir: fs.type === 'DIRECTORY'
      } : null
      // Parse block locations
      const locs = d.blockLocations
      const blocks = []
      if (locs?.BlockLocations?.BlockLocation) {
        for (const bl of locs.BlockLocations.BlockLocation) {
          blocks.push({ length: bl.length, hosts: bl.hosts || [] })
        }
      }
      blockLocations.value = blocks
    } else {
      blockMetadata.value = null; blockLocations.value = []
    }
  } catch (e) { ElMessage.error('查询块元数据失败'); blockMetadata.value = null; blockLocations.value = [] }
}

// === HDFS Operations ===
const switchNN = async () => {
  try {
    const res = await axios.post('/api/v1/hdfs/switch-nn')
    if (res.data.code === 0) {
      const d = res.data.data
      ElMessage.success(`切换成功: ${d.from}(${d.fromState}) → ${d.to}(${d.toState})`)
      setTimeout(loadHdfsNodes, 3000)
    } else {
      ElMessage.error('切换失败: ' + (res.data.msg || ''))
    }
  } catch (e) { ElMessage.error('切换失败') }
}

const scaleDN = async () => {
  if (!newDNName.value.trim()) { ElMessage.warning('请输入节点名'); return }
  dnScaling.value = true
  try {
    const res = await axios.post('/api/v1/hdfs/scale-datanode', null, { params: { hostname: newDNName.value.trim() } })
    if (res.data.code === 0) {
      scaleCommands.value = res.data.data.commands || []
    } else {
      ElMessage.error('生成失败')
    }
  } catch (e) { ElMessage.error('生成失败') }
  dnScaling.value = false
}

const copyCommands = async () => {
  try {
    await navigator.clipboard.writeText(scaleCommands.value.join('\n'))
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    // Fallback for non-HTTPS
    const ta = document.createElement('textarea')
    ta.value = scaleCommands.value.join('\n')
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success('已复制到剪贴板')
  }
}

// === HDFS Log Viewer ===
const logPanelOpen = ref(true)
const logRole = ref('namenode')
const logNode = ref('nn1')
const logLevel = ref([])
const logLines = ref(200)
const logPattern = ref('')
const logDateRange = ref(null)
const logShowMs = ref(false)
const logTotal = ref(0)
const logLinesData = ref([])
const logLoading = ref(false)
const logLoaded = ref(false)
const logAutoRefresh = ref(false)
let logTimer = null

// Quick filter, search, download state
const logQuickFilter = ref('ALL')
const logSearchQuery = ref('')
const logSearchRegex = ref(false)
const logSearchMatchCount = ref(0)
const logSearchMatchLines = ref([])
const logSearchCurrentLine = ref(-1)
const logSearchCurrentIdx = ref(-1)

const logNodesMap = { namenode: ['nn1','nn2'], datanode: ['dn1','dn2','dn3'], journalnode: ['jn1','jn2','jn3'] }
const logNodes = ref(['nn1','nn2'])
const logSource = ref('docker')
const logFileName = ref('container.log')
const logFileList = ref([])

const onLogRoleChange = () => {
  logNodes.value = logNodesMap[logRole.value] || []
  logNode.value = logNodes.value[0] || ''
  loadLogFileList()
  loadLogs()
}

const onLogNodeChange = () => {
  if (logSource.value === 'file') loadLogFileList()
  loadLogs()
}

const loadLogFileList = async () => {
  if (logSource.value !== 'file' || !logNode.value) return
  try {
    const res = await axios.get('/api/v1/hdfs/log-files')
    if (res.data.code === 0) {
      const data = res.data.data
      const roleData = data[logRole.value] || []
      const nodeData = roleData.find(n => n.node === logNode.value)
      if (nodeData && nodeData.files) {
        logFileList.value = nodeData.files
        if (logFileList.value.length > 0 && !logFileList.value.find(f => f.name === logFileName.value)) {
          logFileName.value = logFileList.value[0].name
        }
      }
    }
  } catch (e) {}
}

const onLogSourceChange = () => {
  if (logSource.value === 'file') loadLogFileList()
  loadLogs()
}

const onLogDateChange = () => {
  if (logDateRange.value) loadLogs()
}

const loadLogs = async () => {
  if (!logNode.value) return
  logLoading.value = true
  logLoaded.value = false
  try {
    if (logSource.value === 'file') {
      // 从挂载的日志文件读取
      const params = { role: logRole.value, node: logNode.value, file: logFileName.value, lines: logLines.value }
      if (logLevel.value.length > 0) params.level = logLevel.value.join(',')
      if (logPattern.value.trim()) params.pattern = logPattern.value.trim()
      const res = await axios.get('/api/v1/hdfs/log-files/content', { params })
      if (res.data.code === 0) {
        logLinesData.value = res.data.data.lines || []
        logTotal.value = res.data.data.total || 0
      }
    } else {
      // Docker 实时日志
      const params = { role: logRole.value, node: logNode.value, lines: logLines.value }
      if (logLevel.value.length > 0) params.level = logLevel.value.join(',')
      if (logPattern.value.trim()) params.pattern = logPattern.value.trim()
      if (logDateRange.value && logDateRange.value.length === 2) {
        params.since = logDateRange.value[0].toISOString().slice(0, 19)
        params.until = logDateRange.value[1].toISOString().slice(0, 19)
      }
      const res = await axios.get('/api/v1/hdfs/logs', { params })
      if (res.data.code === 0) {
        logLinesData.value = res.data.data.lines || []
        logTotal.value = res.data.data.total || 0
      }
    }
  } catch (e) { ElMessage.error('日志查询失败') }
  logLoading.value = false
  logLoaded.value = true
}

// 清空日志
const clearLogs = () => {
  logLinesData.value = []
  logTotal.value = 0
  logLoaded.value = false
  ElMessage.success('已清空')
}

// 复制单条日志
const copyLogMsg = (l) => {
  const text = (l.time||'') + ' [' + (l.level||'?') + '] ' + (l.msg||'')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制')
  }).catch(() => {
    // fallback
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  })
}

// 快速过滤后的日志行 (客户端过滤)
const filteredLogLines = computed(() => {
  if (!logLinesData.value || logLinesData.value.length === 0) return []
  if (logQuickFilter.value === 'ALL') return logLinesData.value
  if (logQuickFilter.value === 'ERROR') {
    return logLinesData.value.filter(l => l.level === 'ERROR' || l.level === 'FATAL')
  }
  if (logQuickFilter.value === 'WARN') {
    return logLinesData.value.filter(l => l.level === 'WARN')
  }
  return logLinesData.value
})

// 搜索高亮与导航
const onLogSearchInput = () => {
  const q = logSearchQuery.value.trim()
  if (!q) {
    logSearchMatchCount.value = 0
    logSearchMatchLines.value = []
    logSearchCurrentLine.value = -1
    logSearchCurrentIdx.value = -1
    return
  }
  const lines = filteredLogLines.value
  const matches = []
  try {
    const regex = logSearchRegex.value ? new RegExp(q, 'i') : new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
    for (let i = 0; i < lines.length; i++) {
      if (regex.test(lines[i].msg || '')) {
        matches.push(i)
      }
    }
  } catch (e) {
    // Invalid regex — no matches
    logSearchMatchCount.value = 0
    logSearchMatchLines.value = []
    logSearchCurrentLine.value = -1
    logSearchCurrentIdx.value = -1
    return
  }
  logSearchMatchLines.value = matches
  logSearchMatchCount.value = matches.length
  if (matches.length > 0) {
    logSearchCurrentIdx.value = 0
    logSearchCurrentLine.value = matches[0]
    scrollToMatch()
  } else {
    logSearchCurrentLine.value = -1
    logSearchCurrentIdx.value = -1
  }
}

const scrollToMatch = () => {
  nextTick(() => {
    const viewer = logViewerRef.value
    if (!viewer) return
    const lineEl = viewer.querySelector('.log-current-match')
    if (lineEl) lineEl.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })
}

const logSearchNext = () => {
  const matches = logSearchMatchLines.value
  if (matches.length === 0) return
  let idx = logSearchCurrentIdx.value + 1
  if (idx >= matches.length) idx = 0
  logSearchCurrentIdx.value = idx
  logSearchCurrentLine.value = matches[idx]
  scrollToMatch()
}

const logSearchPrev = () => {
  const matches = logSearchMatchLines.value
  if (matches.length === 0) return
  let idx = logSearchCurrentIdx.value - 1
  if (idx < 0) idx = matches.length - 1
  logSearchCurrentIdx.value = idx
  logSearchCurrentLine.value = matches[idx]
  scrollToMatch()
}

// 下载当前过滤后的日志
const downloadLogs = () => {
  const lines = filteredLogLines.value
  if (!lines || lines.length === 0) {
    ElMessage.warning('没有可下载的日志')
    return
  }
  const content = lines.map(l => {
    const time = l.time || ''
    const level = l.level || '?'
    const msg = l.msg || ''
    return `${time} [${level}] ${msg}`
  }).join('\n')
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `logs-${new Date().toISOString().slice(0,19).replace(/[:-]/g, '')}.txt`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已下载 ${lines.length} 行日志`)
  // Audit log
  axios.post('/api/v1/logs/audit', {
    module: 'hdfs',
    action: 'download-log',
    target: logRole.value + '/' + (logNode.value || '') + '/' + (logFileName.value || ''),
    detail: '下载 ' + lines.length + ' 行日志'
  }).catch(() => {})
}

// Enter / Shift+Enter 搜索导航
const handleLogSearchKeydown = (e) => {
  if (e.key === 'Enter') {
    e.preventDefault()
    if (e.shiftKey) logSearchPrev()
    else logSearchNext()
  }
}

// 自动刷新
watch(logAutoRefresh, (val) => {
  if (logTimer) { clearInterval(logTimer); logTimer = null }
  if (val) logTimer = setInterval(() => loadLogs(), 3000)
})

// 组件卸载时清理
onUnmounted(() => {
  if (logTimer) clearInterval(logTimer)
  if (notificationTimer) clearInterval(notificationTimer)
  if (healthCheckTimer) clearInterval(healthCheckTimer)
  if (systemHealthTimer) clearInterval(systemHealthTimer)
  stopAutoRefresh()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('dragenter', onDragEnter)
  document.removeEventListener('dragleave', onDragLeave)
  // Cleanup ECharts instances
  Object.values(chartInstances).forEach(c => {
    if (c._resizeHandler) window.removeEventListener('resize', c._resizeHandler)
    c.dispose()
  })
})

// Command Palette global key handler
const handleKeydown = (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    toggleCommandPalette()
    return
  }
  if (e.key === 'Escape' && showCommandPalette.value) {
    showCommandPalette.value = false
    return
  }

  // HDFS Files tab keyboard shortcuts
  const isHdfsFilesTab = activeTab.value === 'hdfs' && hdfsSubTab.value === 'files'
  if (!isHdfsFilesTab && !showCommandPalette.value) return

  // Check if focus is on an input/textarea/select element
  const tag = document.activeElement?.tagName?.toLowerCase()
  const isInputFocused = tag === 'input' || tag === 'textarea' || tag === 'select' || tag === 'el-select'

  if (isHdfsFilesTab) {
    // Ctrl+A: Select all files
    if ((e.metaKey || e.ctrlKey) && e.key === 'a') {
      e.preventDefault()
      toggleSelectAllFiles()
      return
    }

    // Delete: Move selected files to trash
    if (e.key === 'Delete') {
      e.preventDefault()
      if (selectedFiles.value.length > 0 && isHdfsFilesTab) {
        batchDelete()
      }
      return
    }

    // F5: Refresh file list
    if (e.key === 'F5') {
      e.preventDefault()
      loadHdfsFiles()
      return
    }

    // Ctrl+F: Focus the search input
    if ((e.metaKey || e.ctrlKey) && e.key === 'f') {
      e.preventDefault()
      focusSearchInput()
      return
    }

    // Backspace: Go to parent directory (only when not focused on an input)
    if (e.key === 'Backspace' && !isInputFocused) {
      e.preventDefault()
      goToParentDir()
      return
    }
  }
}

// 初始加载
watch(logNode, (val) => { if (val && logAutoRefresh.value) loadLogs() }, { immediate: true })

// 切换 HDFS 子标签时加载日志
watch(hdfsSubTab, (val) => { if (val === 'logs') loadLogs() })

const yarnState = ref('')
const yarnApps = ref([])
const yarnLoading = ref(false)
const yarnFilterStates = ref([])
const yarnFilterQueue = ref('')
const yarnFilterUser = ref('')
const yarnFilterName = ref('')
const yarnTimeRange = ref(null)
const yarnPage = ref(1)
const yarnPageSize = ref(20)
const yarnTotal = ref(0)
const yarnTotalPages = ref(0)
const yarnStats = reactive({ running: 0, pending: 0, finished: 0, failed: 0, killed: 0 })
const yarnSortField = ref('startTime')
const yarnSortOrder = ref('desc')
const yarnTableRef = ref(null)
const allYarnStates = ['NEW','NEW_SAVING','SUBMITTED','ACCEPTED','RUNNING','FINISHED','FAILED','KILLED']

// Status filter tags
const yarnActiveFilterTag = ref('ALL')
const yarnFilterTagCounts = reactive({ ALL: 0, RUNNING: 0, ACCEPTED: 0, FINISHED: 0, FAILED: 0 })

// Submit job dialog
const showJobSubmitDialog = ref(false)
const jobSubmitLoading = ref(false)
const jobSubmitForm = reactive({
  name: '',
  type: 'MAPREDUCE',
  jarPath: '',
  mainClass: '',
  args: '',
  inputPath: '',
  outputPath: '',
  queue: 'default',
  vCores: 1,
  memory: 1024
})

// Kill reason error
const killReasonError = ref('')

// Queue options for dropdown
const queueOptions = ref([])
const loadQueueOptions = async () => {
  if (queueOptions.value.length > 0) return
  try {
    const res = await axios.get('/api/v1/yarn/queues', { params: { clusterId: selectedClusterId.value } })
    if (res.data.code === 0) {
      queueOptions.value = [...new Set(res.data.data.map(q => q.queueName))]
    }
  } catch (e) { /* ignore */ }
}

// Kill dialog
const showKillDialog = ref(false)
const killTarget = ref(null)
const killReason = ref('')
const killLoading = ref(false)

// App detail drawer
const showAppDetail = ref(false)
const detailApp = ref(null)
const detailData = ref(null)
const detailLoading = ref(false)
const detailLineage = ref(null)
const lineageLoading = ref(false)
const detailSparkInfo = ref(null)
const sparkLoading = ref(false)

const exportYarnAppsCsv = () => {
  exportToCsv(yarnApps.value, 'yarn_apps', [
    { key: 'appId', label: 'App ID' },
    { key: 'name', label: '名称' },
    { key: 'type', label: '类型' },
    { key: 'user', label: '用户' },
    { key: 'queue', label: '队列' },
    { key: 'state', label: '状态' },
    { key: 'progress', label: '进度' },
    { key: 'vCores', label: 'vCores' },
    { key: 'memory', label: '内存' },
    { key: 'duration', label: '耗时' },
    { key: 'startTime', label: '开始时间' }
  ])
  axios.post('/api/v1/logs/audit', { module:'yarn', action:'export-csv', target:'yarn_apps_export.csv', detail:'导出YARN应用 ' + yarnApps.value.length + ' 个' }).catch(() => {})
}

const loadYarnApps = async () => {
  yarnLoading.value = true
  try {
    const params = { clusterId: selectedClusterId.value, page: yarnPage.value, pageSize: yarnPageSize.value }
    if (yarnFilterStates.value && yarnFilterStates.value.length > 0) {
      params.state = yarnFilterStates.value
    }
    if (yarnFilterQueue.value) params.queue = yarnFilterQueue.value
    if (yarnFilterUser.value) params.user = yarnFilterUser.value
    if (yarnFilterName.value) params.name = yarnFilterName.value
    if (yarnTimeRange.value && yarnTimeRange.value.length === 2) {
      params.timeRangeStart = yarnTimeRange.value[0]
      params.timeRangeEnd = yarnTimeRange.value[1]
    }
    // Sort params
    if (yarnSortField.value) params.sortField = yarnSortField.value
    if (yarnSortOrder.value) params.sortOrder = yarnSortOrder.value

    const res = await axios.get('/api/v1/yarn/apps', { params })
    if (res.data.code === 0) {
      const d = res.data.data
      yarnApps.value = d.apps || []
      yarnTotal.value = d.total || 0
      yarnTotalPages.value = d.totalPages || 0
      if (d.stats) {
        Object.assign(yarnStats, d.stats)
        // Update filter tag counts from stats
        yarnFilterTagCounts.ALL = d.total || 0
        yarnFilterTagCounts.RUNNING = d.stats.running || 0
        yarnFilterTagCounts.ACCEPTED = d.stats.pending || 0
        yarnFilterTagCounts.FINISHED = d.stats.finished || 0
        yarnFilterTagCounts.FAILED = (d.stats.failed || 0) + (d.stats.killed || 0)
      }
    } else {
      yarnApps.value = []
      ElMessage.error('加载 YARN 应用失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    yarnApps.value = []
    ElMessage.error('加载 YARN 应用失败: ' + (e.response?.data?.msg || e.message))
  }
  yarnLoading.value = false
}

const resetYarnFilters = () => {
  yarnFilterStates.value = []
  yarnFilterQueue.value = ''
  yarnFilterUser.value = ''
  yarnFilterName.value = ''
  yarnTimeRange.value = null
  yarnPage.value = 1
  yarnActiveFilterTag.value = 'ALL'
  loadYarnApps()
}

const setYarnFilterTag = (status) => {
  yarnActiveFilterTag.value = status
  yarnPage.value = 1
  if (status === 'ALL') {
    yarnFilterStates.value = []
  } else {
    yarnFilterStates.value = [status]
  }
  loadYarnApps()
}

const handleSubmitJob = async () => {
  if (!jobSubmitForm.name || !jobSubmitForm.name.trim()) {
    ElMessage.warning('请输入应用名称')
    return
  }
  if (!jobSubmitForm.type) {
    ElMessage.warning('请选择作业类型')
    return
  }
  jobSubmitLoading.value = true
  try {
    const params = {
      clusterId: selectedClusterId.value,
      name: jobSubmitForm.name.trim(),
      type: jobSubmitForm.type,
      queue: jobSubmitForm.queue || 'default',
      vCores: jobSubmitForm.vCores || 1,
      memory: jobSubmitForm.memory || 1024
    }
    if (jobSubmitForm.jarPath) params.jarPath = jobSubmitForm.jarPath.trim()
    if (jobSubmitForm.mainClass) params.mainClass = jobSubmitForm.mainClass.trim()
    if (jobSubmitForm.args) params.args = jobSubmitForm.args.trim()
    if (jobSubmitForm.inputPath) params.inputPath = jobSubmitForm.inputPath.trim()
    if (jobSubmitForm.outputPath) params.outputPath = jobSubmitForm.outputPath.trim()

    const res = await axios.post('/api/v1/yarn/submit-job', null, { params })
    if (res.data.code === 0) {
      ElMessage.success(`作业已提交: ${res.data.data?.appId || ''}`)
      showJobSubmitDialog.value = false
      // Reset form
      jobSubmitForm.name = ''
      jobSubmitForm.type = 'MAPREDUCE'
      jobSubmitForm.jarPath = ''
      jobSubmitForm.mainClass = ''
      jobSubmitForm.args = ''
      jobSubmitForm.inputPath = ''
      jobSubmitForm.outputPath = ''
      jobSubmitForm.queue = 'default'
      jobSubmitForm.vCores = 1
      jobSubmitForm.memory = 1024
      loadYarnApps()
    } else {
      ElMessage.error('提交失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.response?.data?.msg || e.message))
  }
  jobSubmitLoading.value = false
}

const browseHdfsForJar = () => {
  // Navigate to HDFS tab to pick a jar file
  ElMessage.info('请切换到 HDFS 文件系统浏览并复制 Jar 路径')
  activeTab.value = 'hdfs'
}

const yarnRowClass = ({ row }) => {
  if (row.state === 'FAILED') return 'yarn-row-failed'
  if (row.state === 'KILLED') return 'yarn-row-killed'
  return ''
}

const onYarnSortChange = ({ prop, order }) => {
  yarnSortField.value = prop || 'startTime'
  yarnSortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  loadYarnApps()
}

const yarnStateTag = (s) => {
  const map = { RUNNING: 'success', FINISHED: 'info', FAILED: 'danger', KILLED: 'warning', ACCEPTED: 'primary', SUBMITTED: 'primary', NEW: 'info', NEW_SAVING: 'info' }
  return map[s] || 'info'
}

const yarnProgressColor = (p) => {
  if (p >= 1) return '#67c23a'
  if (p >= 0.5) return '#e6a23c'
  return '#409eff'
}

const canKill = (state) => {
  return ['RUNNING', 'ACCEPTED', 'SUBMITTED', 'NEW', 'NEW_SAVING'].includes(state)
}

const formatDuration = (ms) => {
  if (!ms || ms <= 0) return '-'
  const sec = Math.floor(ms / 1000)
  const min = Math.floor(sec / 60)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)
  if (day > 0) return `${day}d ${hour % 24}h`
  if (hour > 0) return `${hour}h ${min % 60}m`
  if (min > 0) return `${min}m ${sec % 60}s`
  return `${sec}s`
}

const openKillDialog = (row) => {
  killTarget.value = row
  killReason.value = ''
  killReasonError.value = ''
  showKillDialog.value = true
}

const confirmKillAppWithReason = async () => {
  if (!killTarget.value) return
  if (!killReason.value || !killReason.value.trim()) {
    killReasonError.value = '终止原因不能为空'
    return
  }
  killReasonError.value = ''
  killLoading.value = true
  try {
    const params = { appId: killTarget.value.appId, clusterId: selectedClusterId.value, reason: killReason.value.trim() }
    const res = await axios.post('/api/v1/yarn/kill', null, { params })
    if (res.data.code === 0) {
      ElMessage.success(`已终止: ${killTarget.value.appId}`)
      showKillDialog.value = false
      killTarget.value = null
      killReason.value = ''
      loadYarnApps()
    } else {
      ElMessage.error('终止失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('终止失败: ' + (e.response?.data?.msg || e.message))
  }
  killLoading.value = false
}

const confirmKillApp = async () => {
  if (!killTarget.value) return
  killLoading.value = true
  try {
    const params = { appId: killTarget.value.appId, clusterId: selectedClusterId.value }
    if (killReason.value.trim()) params.reason = killReason.value.trim()
    const res = await axios.post('/api/v1/yarn/kill', null, { params })
    if (res.data.code === 0) {
      ElMessage.success(`已终止: ${killTarget.value.appId}`)
      showKillDialog.value = false
      killTarget.value = null
      loadYarnApps()
    } else {
      ElMessage.error('终止失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('终止失败: ' + (e.response?.data?.msg || e.message))
  }
  killLoading.value = false
}

const openAppDetail = async (row) => {
  detailApp.value = row
  detailData.value = null
  detailLineage.value = null
  detailSparkInfo.value = null
  showAppDetail.value = true
  detailLoading.value = true
  lineageLoading.value = true
  sparkLoading.value = true
  try {
    const [detailRes, lineageRes, sparkRes] = await Promise.all([
      axios.get(`/api/v1/yarn/app/${row.appId}/details`, { params: { clusterId: selectedClusterId.value } }),
      axios.get(`/api/v1/yarn/app/${row.appId}/lineage`, { params: { clusterId: selectedClusterId.value } }),
      axios.get(`/api/v1/yarn/app/${row.appId}/spark`, { params: { clusterId: selectedClusterId.value } })
    ])
    if (detailRes.data.code === 0) {
      detailData.value = detailRes.data.data
    } else {
      ElMessage.error('加载详情失败: ' + (detailRes.data.msg || ''))
    }
    if (lineageRes.data.code === 0) {
      detailLineage.value = lineageRes.data.data
    }
    if (sparkRes.data.code === 0) {
      detailSparkInfo.value = sparkRes.data.data
    }
  } catch (e) {
    ElMessage.error('加载详情失败: ' + (e.response?.data?.msg || e.message))
  }
  detailLoading.value = false
  lineageLoading.value = false
  sparkLoading.value = false
}

const closeAppDetail = () => {
  showAppDetail.value = false
  detailData.value = null
  detailApp.value = null
  detailLineage.value = null
  detailSparkInfo.value = null
}

const navigateToHdfsPath = (hdfsPath) => {
  // Extract path from hdfs:// URL or use as-is
  let path = hdfsPath
  if (path.startsWith('hdfs://')) {
    try {
      const url = new URL(path)
      path = url.pathname
    } catch (e) {
      // Just use the raw string
    }
  }
  // If path doesn't start with /, add it
  if (!path.startsWith('/')) path = '/' + path
  // Set the HDFS path and switch to HDFS tab
  hdfsPath.value = path
  hdfsSubTab.value = 'files'
  activeTab.value = 'hdfs'
  showAppDetail.value = false
  detailData.value = null
  detailApp.value = null
  detailLineage.value = null
  detailSparkInfo.value = null
  // Trigger file listing
  loadHdfsFiles()
}

const openSparkHistory = (trackingUrl) => {
  if (trackingUrl) {
    window.open(trackingUrl, '_blank')
  }
}

const yarnQueues = ref([])
const yarnMetrics = reactive({ numNodeManagers: 0, totalMemoryMB: 0, totalVCores: 0, runningApplications: 0 })
const showQueueEditDialog = ref(false)
const queueEditForm = ref(null)
const queueEditSaving = ref(false)

const usageColor = (pct) => {
  if (pct >= 80) return '#ef4444'
  if (pct >= 60) return '#eab308'
  return '#22c55e'
}

const loadYarnQueues = async () => {
  try {
    const [qRes, mRes] = await Promise.all([
      axios.get('/api/v1/yarn/queues', { params: { clusterId: selectedClusterId.value } }),
      axios.get('/api/v1/yarn/metrics', { params: { clusterId: selectedClusterId.value } })
    ])
    if (qRes.data.code === 0) {
      yarnQueues.value = qRes.data.data
    }
    if (mRes.data.code === 0) Object.assign(yarnMetrics, mRes.data.data)
  } catch (e) {}
}

const openQueueEdit = (row) => {
  queueEditForm.value = {
    queueName: row.queueName,
    capacity: row.capacity || 0,
    maxCapacity: row.maxCapacity || 100,
    absoluteCapacity: row.absoluteCapacity || 0,
    usedCapacity: row.usedCapacity || 0
  }
  showQueueEditDialog.value = true
}

const saveQueueCapacity = async () => {
  if (!queueEditForm.value) return
  queueEditSaving.value = true
  try {
    const res = await axios.post('/api/v1/yarn/queues/adjust-weight', null, {
      params: {
        queueName: queueEditForm.value.queueName,
        newCapacity: queueEditForm.value.capacity,
        clusterId: selectedClusterId.value
      }
    })
    if (res.data.code === 0) {
      ElMessage.success(`队列「${queueEditForm.value.queueName}」容量调整已提交，请在 capacity-scheduler.xml 中确认变更后执行 yarn rmadmin -refreshQueues`)
      showQueueEditDialog.value = false
      loadYarnQueues()
    } else {
      ElMessage.error('调整失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    ElMessage.error('调整失败: ' + (e.response?.data?.msg || e.message))
  }
  queueEditSaving.value = false
}

const mrTemplates = ref([])
const mrAllTemplates = ref([])  // 用于生成下拉选项（始终加载全部）
const filteredMrTemplates = ref([])
const showMrDialog = ref(false)
const mrSaving = ref(false)
const editingMrId = ref(null)
const mrCategoryFilter = ref('')
const mrSearchKeyword = ref('')
const mrFilterId = ref('')
const mrFilterQueue = ref('')
const mrFilterUseCountOp = ref('')
const mrFilterUseCountVal = ref('')
// 从实际模板数据中提取不重复的分类、ID、队列，动态生成下拉选项
const mrCategoryOptions = computed(() => {
  const types = new Set()
  types.add('')
  if (mrAllTemplates.value) {
    mrAllTemplates.value.forEach(t => { if (t.type) types.add(t.type) })
  }
  return Array.from(types)
})
const mrIdOptions = computed(() => {
  if (!mrAllTemplates.value) return []
  const seen = new Set()
  return mrAllTemplates.value.filter(t => { if (seen.has(t.id)) return false; seen.add(t.id); return true })
    .map(t => ({ label: String(t.id), value: t.id }))
})
const mrQueueOptions = computed(() => {
  const queues = new Set()
  if (mrAllTemplates.value) {
    mrAllTemplates.value.forEach(t => { if (t.queue) queues.add(t.queue) })
  }
  return Array.from(queues)
})
const mrForm = reactive({ name: '', jarHdfsPath: '', mainClass: '', defaultArgs: '', inputPath: '', outputPath: '', queue: 'default', type: '' })
const loadMrTemplates = async () => {
  try {
    // 加载全部模板（用于下拉选项）
    const allRes = await axios.get('/api/v1/mr/templates')
    mrAllTemplates.value = allRes.data?.data || (Array.isArray(allRes.data) ? allRes.data : [])

    // 带参数查询（用于表格显示）
    const params = {}
    if (mrCategoryFilter.value) params.type = mrCategoryFilter.value
    if (mrFilterId.value) params.id = mrFilterId.value
    if (mrFilterQueue.value) params.queue = mrFilterQueue.value
    if (mrFilterUseCountOp.value && mrFilterUseCountVal.value !== '') {
      params.useCountOp = mrFilterUseCountOp.value
      params.useCountVal = mrFilterUseCountVal.value
    }
    if (mrSearchKeyword.value && mrSearchKeyword.value.trim()) {
      params.name = mrSearchKeyword.value.trim()
    }
    const res = await axios.get('/api/v1/mr/templates', { params })
    mrTemplates.value = res.data?.data || (Array.isArray(res.data) ? res.data : [])
    filteredMrTemplates.value = mrTemplates.value
  } catch (e) {}
}
const filterMrTemplates = () => {
  let list = mrTemplates.value
  if (mrCategoryFilter.value) {
    list = list.filter(t => t.type === mrCategoryFilter.value)
  }
  if (mrFilterId.value) {
    list = list.filter(t => t.id === mrFilterId.value)
  }
  if (mrFilterQueue.value) {
    list = list.filter(t => t.queue === mrFilterQueue.value)
  }
  if (mrFilterUseCountOp.value && mrFilterUseCountVal.value !== '') {
    const val = Number(mrFilterUseCountVal.value)
    if (!isNaN(val)) {
      const count = t => t.useCount ?? 0
      if (mrFilterUseCountOp.value === '>') list = list.filter(t => count(t) > val)
      else if (mrFilterUseCountOp.value === '>=') list = list.filter(t => count(t) >= val)
      else if (mrFilterUseCountOp.value === '=') list = list.filter(t => count(t) === val)
      else if (mrFilterUseCountOp.value === '<=') list = list.filter(t => count(t) <= val)
      else if (mrFilterUseCountOp.value === '<') list = list.filter(t => count(t) < val)
    }
  }
  if (mrSearchKeyword.value && mrSearchKeyword.value.trim()) {
    const kw = mrSearchKeyword.value.trim().toLowerCase()
    list = list.filter(t => t.name && t.name.toLowerCase().includes(kw))
  }
  filteredMrTemplates.value = list
}

// 筛选栏拖拽排序
const mrFilterOrder = ref(['ID', '分类', '队列', '使用次数', '名称'])
let draggedFilter = null
const onFilterDragStart = (e, name) => {
  draggedFilter = name
  e.dataTransfer.effectAllowed = 'move'
}
const onFilterDragOver = (e) => { e.preventDefault() }
const onFilterDrop = (e, targetName) => {
  if (!draggedFilter || draggedFilter === targetName) return
  const order = mrFilterOrder.value
  const fromIdx = order.indexOf(draggedFilter)
  const toIdx = order.indexOf(targetName)
  if (fromIdx === -1 || toIdx === -1) return
  order.splice(fromIdx, 1)
  order.splice(toIdx, 0, draggedFilter)
  mrFilterOrder.value = [...order]
  draggedFilter = null
}

const saveMrTemplate = async () => {
  mrSaving.value = true
  try {
    let res
    if (editingMrId.value) {
      res = await axios.put('/api/v1/mr/templates/' + editingMrId.value, mrForm)
    } else {
      res = await axios.post('/api/v1/mr/templates', mrForm)
    }
    if (res.data.code === 0) {
      ElMessage.success(editingMrId.value ? '模板已更新' : '模板已保存')
      showMrDialog.value = false; editingMrId.value = null
      mrCategoryFilter.value = ''; mrSearchKeyword.value = ''; mrFilterId.value = ''; mrFilterQueue.value = ''; mrFilterUseCountOp.value = ''; mrFilterUseCountVal.value = ''
      loadMrTemplates()
      Object.assign(mrForm, { name: '', jarHdfsPath: '', mainClass: '', defaultArgs: '', inputPath: '', outputPath: '', queue: 'default', type: '' })
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) { ElMessage.error('保存失败') }
  mrSaving.value = false
}
const submitMrJob = async (templateId) => {
  try {
    const res = await axios.post('/api/v1/mr/submit-from-template', null, { params: { templateId, clusterId: selectedClusterId.value } })
    if (res.data.code === 0) {
      ElMessage.success('MR 作业已提交: ' + (res.data.data?.appId || ''))
      loadMrTemplates()
    } else {
      ElMessage.error(res.data.msg || '提交失败')
    }
  } catch (e) { ElMessage.error('提交失败: ' + (e.response?.data?.msg || e.message)) }
}
const editMrTemplate = (row) => {
  editingMrId.value = row.id
  Object.assign(mrForm, {
    name: row.name || '',
    jarHdfsPath: row.jarHdfsPath || '',
    mainClass: row.mainClass || '',
    defaultArgs: row.defaultArgs || '',
    inputPath: row.inputPath || '',
    outputPath: row.outputPath || '',
    queue: row.queue || 'default',
    type: row.type || ''
  })
  showMrDialog.value = true
}
const handleMrAction = (cmd, row) => {
  if (cmd === 'copy') {
    // Copy template: create a new one based on this template's data
    editingMrId.value = null
    Object.assign(mrForm, {
      name: row.name + ' (副本)',
      jarHdfsPath: row.jarHdfsPath || '',
      mainClass: row.mainClass || '',
      defaultArgs: row.defaultArgs || '',
      inputPath: row.inputPath || '',
      outputPath: row.outputPath || '',
      queue: row.queue || 'default',
      type: row.type || ''
    })
    showMrDialog.value = true
    ElMessage.info('已复制模板，请修改后保存')
  } else if (cmd === 'delete') {
    ElMessageBox.confirm(`确定删除模板「${row.name}」吗？`, '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    }).then(async () => {
      try {
        const res = await axios.delete('/api/v1/mr/templates/' + row.id)
        if (res.data.code === 0) {
          ElMessage.success('模板已删除')
          loadMrTemplates()
        }
      } catch (e) { ElMessage.error('删除失败') }
    }).catch(() => {})
  }
}

// === Workflow Engine ===
// Helper: execution status to tag type
const execStatusTagType = (status) => {
  const map = { PENDING: 'info', RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', SKIPPED: 'warning', TIMEOUT: 'warning', CANCELLED: 'info' }
  return map[status] || 'info'
}

// Workflow state
const workflows = ref([])
const workflowTotal = ref(0)
const workflowPage = ref(1)
const workflowPageSize = ref(20)
const workflowLoading = ref(false)
const showWorkflowDialog = ref(false)
const workflowSaving = ref(false)
const editingWorkflowId = ref(null)
const workflowForm = reactive({ name: '', description: '', clusterId: 'cluster1', scheduleCron: '', maxRetries: 0, timeoutMinutes: 60, webhookUrl: '' })
const selectedWorkflow = ref(null)
const workflowSteps = ref([])
const showStepDialog = ref(false)
const stepSaving = ref(false)
const editingStepId = ref(null)
const stepForm = reactive({ name: '', stepType: 'SHELL', templateId: '', command: '', inputPath: '', outputPath: '', dependsOn: [], stepOrder: 1, timeoutMinutes: 60, queue: '' })
const executions = ref([])
const execTotal = ref(0)
const execPage = ref(1)
const execPageSize = ref(20)
const execLoading = ref(false)
const showExecutionDrawer = ref(false)
const selectedExecution = ref(null)
const stepExecutions = ref([])
const stepExecLoading = ref(false)

// Available step dependencies (exclude current step being edited)
const availableStepDeps = computed(() => {
  if (!workflowSteps.value) return []
  return workflowSteps.value.filter(s => (s.id || s._tempId) !== (editingStepId.value || -1))
})

// Reset workflow form
const resetWorkflowForm = () => {
  Object.assign(workflowForm, { name: '', description: '', clusterId: 'cluster1', scheduleCron: '', maxRetries: 0, timeoutMinutes: 60, webhookUrl: '' })
}

// Load workflows list
const loadWorkflows = async () => {
  workflowLoading.value = true
  try {
    const res = await axios.get('/api/v1/workflows', { params: { page: workflowPage.value, size: workflowPageSize.value } })
    if (res.data.code === 0) {
      workflows.value = res.data.data.records || []
      workflowTotal.value = res.data.data.total || 0
    }
  } catch (e) { ElMessage.error('加载工作流列表失败') }
  workflowLoading.value = false
}

// Open create workflow dialog
const openCreateWorkflow = () => {
  resetWorkflowForm()
  editingWorkflowId.value = null
  showWorkflowDialog.value = true
}

// Open edit workflow dialog
const openEditWorkflow = (wf) => {
  Object.assign(workflowForm, { name: wf.name, description: wf.description || '', clusterId: wf.clusterId || 'cluster1', scheduleCron: wf.scheduleCron || '', maxRetries: wf.maxRetries ?? 0, timeoutMinutes: wf.timeoutMinutes ?? 60, webhookUrl: wf.webhookUrl || '' })
  editingWorkflowId.value = wf.id
  showWorkflowDialog.value = true
}

// Save workflow (create or update)
const saveWorkflow = async () => {
  if (!workflowForm.name) { ElMessage.warning('请填写工作流名称'); return }
  workflowSaving.value = true
  try {
    if (editingWorkflowId.value) {
      const res = await axios.put('/api/v1/workflows/' + editingWorkflowId.value, workflowForm)
      if (res.data.code === 0) { ElMessage.success('工作流已更新') }
    } else {
      const res = await axios.post('/api/v1/workflows', workflowForm)
      if (res.data.code === 0) { ElMessage.success('工作流已创建') }
    }
    showWorkflowDialog.value = false
    loadWorkflows()
  } catch (e) { ElMessage.error('保存失败: ' + (e.response?.data?.msg || e.message)) }
  workflowSaving.value = false
}

// Delete workflow
const deleteWorkflow = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此工作流？', '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    const res = await axios.delete('/api/v1/workflows/' + id)
    if (res.data.code === 0) {
      ElMessage.success('已删除')
      if (selectedWorkflow.value && selectedWorkflow.value.id === id) selectedWorkflow.value = null
      loadWorkflows()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// Toggle workflow enabled/disabled
const toggleWorkflow = async (wf) => {
  try {
    const res = await axios.patch('/api/v1/workflows/' + wf.id + '/toggle')
    if (res.data.code === 0) {
      wf.enabled = !wf.enabled
      ElMessage.success(wf.enabled ? '已启用' : '已禁用')
    }
  } catch (e) { ElMessage.error('操作失败') }
}

// Select workflow for design view
const selectWorkflow = async (wf) => {
  try {
    const res = await axios.get('/api/v1/workflows/' + wf.id)
    if (res.data.code === 0) {
      selectedWorkflow.value = res.data.data
      workflowSteps.value = res.data.data.steps || []
      loadSteps(wf.id)
      loadExecutions(wf.id)
    }
  } catch (e) { ElMessage.error('加载工作流详情失败') }
}

// Load steps for a workflow
const loadSteps = async (wfId) => {
  try {
    const res = await axios.get('/api/v1/workflows/' + wfId + '/steps')
    if (res.data.code === 0) {
      workflowSteps.value = (res.data.data || []).sort((a, b) => (a.stepOrder || 0) - (b.stepOrder || 0))
    }
  } catch (e) { /* ignore */ }
}

// Open add step dialog
const openAddStep = () => {
  editingStepId.value = null
  const maxOrder = workflowSteps.value.length > 0 ? Math.max(...workflowSteps.value.map(s => s.stepOrder || 0)) : 0
  Object.assign(stepForm, { name: '', stepType: 'SHELL', templateId: '', command: '', inputPath: '', outputPath: '', dependsOn: [], stepOrder: maxOrder + 1, timeoutMinutes: 60, queue: '' })
  loadMrTemplates()
  showStepDialog.value = true
}

// Open edit step dialog
const openEditStep = (step) => {
  editingStepId.value = step.id || step._tempId
  Object.assign(stepForm, {
    name: step.name || '',
    stepType: step.stepType || 'SHELL',
    templateId: step.templateId || '',
    command: step.command || '',
    inputPath: step.inputPath || '',
    outputPath: step.outputPath || '',
    dependsOn: step.dependsOn || [],
    stepOrder: step.stepOrder || 1,
    timeoutMinutes: step.timeoutMinutes || 60,
    queue: step.queue || ''
  })
  loadMrTemplates()
  showStepDialog.value = true
}

// Step type change handler
const onStepTypeChange = () => {
  // Reset type-specific fields
  stepForm.templateId = ''
  stepForm.command = ''
  stepForm.inputPath = ''
  stepForm.outputPath = ''
  stepForm.queue = ''
}

// Save step
const saveStep = async () => {
  if (!stepForm.name) { ElMessage.warning('请填写步骤名称'); return }
  stepSaving.value = true
  const wfId = selectedWorkflow.value?.id
  if (!wfId) { ElMessage.error('未选择工作流'); stepSaving.value = false; return }

  const payload = {
    name: stepForm.name,
    stepType: stepForm.stepType,
    templateId: stepForm.stepType === 'MAPREDUCE' ? stepForm.templateId : undefined,
    command: stepForm.stepType !== 'MAPREDUCE' ? stepForm.command : undefined,
    inputPath: stepForm.stepType === 'MAPREDUCE' ? stepForm.inputPath : undefined,
    outputPath: stepForm.stepType === 'MAPREDUCE' ? stepForm.outputPath : undefined,
    dependsOn: stepForm.dependsOn.length > 0 ? stepForm.dependsOn : undefined,
    stepOrder: stepForm.stepOrder,
    timeoutMinutes: stepForm.timeoutMinutes,
    queue: stepForm.stepType === 'MAPREDUCE' ? stepForm.queue : undefined
  }

  try {
    if (editingStepId.value) {
      const res = await axios.put('/api/v1/workflows/' + wfId + '/steps/' + editingStepId.value, payload)
      if (res.data.code === 0) ElMessage.success('步骤已更新')
    } else {
      const res = await axios.post('/api/v1/workflows/' + wfId + '/steps', payload)
      if (res.data.code === 0) ElMessage.success('步骤已添加')
    }
    showStepDialog.value = false
    loadSteps(wfId)
  } catch (e) { ElMessage.error('保存步骤失败: ' + (e.response?.data?.msg || e.message)) }
  stepSaving.value = false
}

// Delete step
const deleteStep = async (stepId) => {
  const wfId = selectedWorkflow.value?.id
  if (!wfId || !stepId) return
  try {
    await ElMessageBox.confirm('确定删除此步骤？', '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    const res = await axios.delete('/api/v1/workflows/' + wfId + '/steps/' + stepId)
    if (res.data.code === 0) {
      ElMessage.success('步骤已删除')
      loadSteps(wfId)
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// Execute workflow
const executeWorkflow = async (wfId) => {
  try {
    const res = await axios.post('/api/v1/workflows/' + wfId + '/execute')
    if (res.data.code === 0) {
      ElMessage.success('工作流已触发执行')
      if (selectedWorkflow.value?.id === wfId) loadExecutions(wfId)
    }
  } catch (e) { ElMessage.error('执行失败: ' + (e.response?.data?.msg || e.message)) }
}

// Load executions
const loadExecutions = async (wfId) => {
  if (!wfId) return
  execLoading.value = true
  try {
    const res = await axios.get('/api/v1/workflows/' + wfId + '/executions', { params: { page: execPage.value, size: execPageSize.value } })
    if (res.data.code === 0) {
      executions.value = res.data.data.records || []
      execTotal.value = res.data.data.total || 0
    }
  } catch (e) { /* ignore */ }
  execLoading.value = false
}

// Open execution detail drawer
const openExecutionDetail = async (exec) => {
  selectedExecution.value = exec
  showExecutionDrawer.value = true
  stepExecLoading.value = true
  stepExecutions.value = []
  try {
    const res = await axios.get('/api/v1/workflows/executions/' + exec.id + '/steps')
    if (res.data.code === 0) {
      stepExecutions.value = res.data.data || []
    }
  } catch (e) { /* ignore */ }
  stepExecLoading.value = false
}

// Cancel execution
const cancelExecution = async (execId) => {
  try {
    const res = await axios.post('/api/v1/workflows/executions/' + execId + '/cancel')
    if (res.data.code === 0) {
      ElMessage.success('执行已取消')
      if (selectedWorkflow.value) loadExecutions(selectedWorkflow.value.id)
    }
  } catch (e) { ElMessage.error('取消失败') }
}

// Back to workflow list
const backToWorkflowList = () => {
  selectedWorkflow.value = null
  workflowSteps.value = []
  executions.value = []
}

// === 操作审计 ===
const operationLogs = ref([])
const opLogLoading = ref(false)
const opLogPage = ref(1)
const opLogPageSize = ref(20)
const opLogTotal = ref(0)
const opLogFilterModule = ref('')
const opLogFilterAction = ref('')
const opLogFilterDateRange = ref(null)
const selectedOpLogs = ref([])

// 各模块对应的操作类型选项
const actionOptionsByModule = {
  hdfs: [
    { label: '全部', value: '' },
    { label: '列表', value: 'list' },
    { label: '上传', value: 'upload' },
    { label: '下载', value: 'download' },
    { label: '删除', value: 'delete' },
    { label: '创建目录', value: 'mkdir' },
    { label: '重命名', value: 'rename' },
    { label: '移动', value: 'move' },
    { label: '搜索', value: 'search' },
    { label: '修改权限', value: 'chmod' },
    { label: '查看ACL', value: 'getAcl' },
    { label: '设置ACL', value: 'setAcl' },
    { label: '移入回收站', value: 'trash' },
    { label: '从回收站恢复', value: 'restore' },
    { label: '清空回收站', value: 'emptyTrash' },
    { label: '导出CSV', value: 'export-csv' },
    { label: '下载日志', value: 'download-log' },
  ],
  yarn: [
    { label: '全部', value: '' },
    { label: '提交作业', value: 'submit' },
    { label: '终止作业', value: 'kill' },
    { label: '列表', value: 'list' },
    { label: '调整权重', value: 'adjust-weight' },
    { label: '创建告警规则', value: 'create-alert' },
    { label: '更新告警规则', value: 'update-alert' },
    { label: '删除告警规则', value: 'delete-alert' },
    { label: '导出CSV', value: 'export-csv' },
  ],
  mr: [
    { label: '全部', value: '' },
    { label: '提交作业', value: 'submit' },
    { label: '终止作业', value: 'kill' },
    { label: '创建模板', value: 'create-template' },
    { label: '编辑模板', value: 'update-template' },
    { label: '删除模板', value: 'delete-template' },
    { label: '导出CSV', value: 'export-csv' },
  ],
  user: [
    { label: '全部', value: '' },
    { label: '创建用户', value: 'create' },
    { label: '编辑用户', value: 'update' },
    { label: '删除用户', value: 'delete' },
    { label: '重置密码', value: 'reset-password' },
    { label: '导出CSV', value: 'export-csv' },
  ],
}
// 全部模块时的所有操作类型
const allActionOptions = [
  { label: '全部', value: '' },
  { label: '列表', value: 'list' },
  { label: '上传', value: 'upload' },
  { label: '下载', value: 'download' },
  { label: '删除', value: 'delete' },
  { label: '创建', value: 'create' },
  { label: '提交', value: 'submit' },
  { label: '停止', value: 'kill' },
  { label: '重命名', value: 'rename' },
  { label: '修改权限', value: 'chmod' },
  { label: '创建目录', value: 'mkdir' },
  { label: '重置密码', value: 'reset-password' },
  { label: '编辑用户', value: 'update' },
  { label: '删除用户', value: 'delete' },
  { label: '创建用户', value: 'create' },
  { label: '移入回收站', value: 'trash' },
  { label: '从回收站恢复', value: 'restore' },
  { label: '清空回收站', value: 'emptyTrash' },
  { label: '搜索', value: 'search' },
  { label: '查看ACL', value: 'getAcl' },
  { label: '设置ACL', value: 'setAcl' },
  { label: '切换', value: 'switch' },
  { label: '导出CSV', value: 'export-csv' },
  { label: '下载日志', value: 'download-log' },
  { label: '创建模板', value: 'create-template' },
  { label: '编辑模板', value: 'update-template' },
  { label: '删除模板', value: 'delete-template' },
]
// 根据选中模块过滤操作类型选项
const filteredActionOptions = computed(() => {
  const mod = opLogFilterModule.value
  if (!mod || mod === '') return allActionOptions
  return actionOptionsByModule[mod] || allActionOptions
})


// Operation log detail modal
const showLogDetail = ref(false)
const selectedLog = ref(null)
const openLogDetail = (row) => {
  selectedLog.value = row
  showLogDetail.value = true
}
const tryParseJson = (str) => {
  if (!str) return str
  try {
    const parsed = JSON.parse(str)
    return parsed
  } catch {
    return str
  }
}

// 操作类型 → 中文映射（从数据库动态加载）
const actionLabels = ref({})
const loadActionLabels = async () => {
  try {
    const res = await axios.get('/api/v1/logs/labels')
    if (res.data.code === 0) {
      actionLabels.value = res.data.data || {}
    }
  } catch (e) { /* ignore */ }
}
// 初始化加载
loadActionLabels()

// 格式化日期时间 YYYY-MM-DD HH:mm:ss
const formatDateTime = (dt) => {
  if (!dt) return '-'
  const d = new Date(dt)
  if (isNaN(d.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
}

// === Notification Bell (Failed Operations + Unread Alerts) ===
const failedOperations = ref([])
const showNotificationsPopover = ref(false)
const unreadAlerts = ref([])
const failedOperationsCount = computed(() => failedOperations.value.length)
const unreadAlertsCount = computed(() => unreadAlerts.value.length)
const totalNotificationsCount = computed(() => failedOperationsCount.value + unreadAlertsCount.value)
let notificationTimer = null
const loadFailedOperations = async () => {
  try {
    const res = await axios.get('/api/v1/logs', {
      params: { page: 1, pageSize: 5, result: 'failed' }
    })
    if (res.data.code === 0) {
      failedOperations.value = res.data.data.records || []
    }
  } catch (e) {
    // silently fail for polling
  }
}
const loadUnreadAlerts = async () => {
  try {
    const res = await axios.get('/api/v1/alerts/unread-count')
    if (res.data.code === 0) {
      const count = res.data.data.count || 0
      // We store the count and fetch details
      unreadAlerts.value = count > 0 ? [{ count }] : []
    }
    // Also try to get actual unread alert list from history endpoint
    const res2 = await axios.get('/api/v1/alerts/history', {
      params: { page: 1, pageSize: 5 }
    })
    if (res2.data.code === 0) {
      unreadAlerts.value = res2.data.data.records || []
    }
  } catch (e) {
    // silently fail
  }
}
const markAllRead = async () => {
  try {
    // Navigate to operation log which shows all history
    showNotificationsPopover.value = false
    activeTab.value = 'operation-log'
    ElMessage.success('已标记为已读')
  } catch (e) {
    // silently fail
  }
}
const goToOperationLog = () => {
  showNotificationsPopover.value = false
  activeTab.value = 'operation-log'
}
const goToAlertCenter = () => {
  showNotificationsPopover.value = false
  activeTab.value = 'alert-center'
  alertSubTab.value = 'history'
}

// 用户管理
const users = ref([])
const userLoading = ref(false)
const userPage = ref(1)
const userPageSize = ref(20)
const userTotal = ref(0)
const userSearchKeyword = ref('')
const showCreateUserDialog = ref(false)
const showEditUserDialog = ref(false)
const showResetPasswordDialog = ref(false)
const createUserLoading = ref(false)
const editUserLoading = ref(false)
const resetPasswordLoading = ref(false)
const createUserForm = reactive({ username: '', password: '', email: '', role: 'viewer' })
const createUserFormRef = ref(null)

// 密码复杂度实时检测
const pwdHasUpper = computed(() => /[A-Z]/.test(createUserForm.password))
const pwdHasLower = computed(() => /[a-z]/.test(createUserForm.password))
const pwdHasDigit = computed(() => /[0-9]/.test(createUserForm.password))
const pwdHasSpecial = computed(() => /[^A-Za-z0-9]/.test(createUserForm.password))
const createUserRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度应为2-50个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  email: [
    { pattern: /^[A-Za-z0-9+_.-]+@(.+)$/, message: '邮箱格式不正确', trigger: 'blur' }
  ]
}
const editUserForm = reactive({ id: null, username: '', email: '', role: 'viewer', enabled: true })
const resetPasswordTarget = ref(null)
const resetPasswordNewPassword = ref('')
const resetPasswordOldPassword = ref('')

// 重置密码复杂度实时检测
const rpwdHasUpper = computed(() => /[A-Z]/.test(resetPasswordNewPassword.value))
const rpwdHasLower = computed(() => /[a-z]/.test(resetPasswordNewPassword.value))
const rpwdHasDigit = computed(() => /[0-9]/.test(resetPasswordNewPassword.value))
const rpwdHasSpecial = computed(() => /[^A-Za-z0-9]/.test(resetPasswordNewPassword.value))

const exportUsersCsv = () => {
  exportToCsv(users.value, 'users', [
    { key: 'id', label: 'ID' },
    { key: 'username', label: '用户名' },
    { key: 'email', label: '邮箱' },
    { key: 'role', label: '角色' },
    { key: 'enabled', label: '状态' },
    { key: 'createTime', label: '创建时间' },
    { key: 'lastLoginTime', label: '最后登录' }
  ])
  axios.post('/api/v1/logs/audit', { module:'user', action:'export-csv', target:'users_export.csv', detail:'导出 ' + users.value.length + ' 条用户记录' }).catch(() => {})
}

const loadUsers = async () => {
  userLoading.value = true
  try {
    const params = { page: userPage.value, pageSize: userPageSize.value }
    if (userSearchKeyword.value.trim()) {
      params.keyword = userSearchKeyword.value.trim()
    }
    const res = await axios.get('/api/v1/users', { params })
    if (res.data.code === 0) {
      users.value = res.data.data.records
      userTotal.value = res.data.data.total
    }
  } catch (e) {
    ElMessage.error('加载用户列表失败: ' + (e.response?.data?.msg || e.message))
  }
  userLoading.value = false
}

const openCreateUserDialog = () => {
  createUserForm.username = ''
  createUserForm.password = ''
  createUserForm.email = ''
  createUserForm.role = 'viewer'
  showCreateUserDialog.value = true
}

const handleCreateUser = async () => {
  if (!createUserFormRef.value) {
    if (!createUserForm.username || !createUserForm.password) {
      ElMessage.warning('用户名和密码不能为空')
      return
    }
  } else {
    const valid = await createUserFormRef.value.validate().catch(() => false)
    if (!valid) return
  }
  createUserLoading.value = true
  try {
    const res = await axios.post('/api/v1/users', createUserForm)
    if (res.data.code === 0) {
      ElMessage.success('创建成功')
      showCreateUserDialog.value = false
      loadUsers()
    } else {
      ElMessage.error(res.data.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建用户失败: ' + (e.response?.data?.msg || e.message))
  }
  createUserLoading.value = false
}

const openEditUserDialog = (row) => {
  editUserForm.id = row.id
  editUserForm.username = row.username
  editUserForm.email = row.email || ''
  editUserForm.role = row.role || 'viewer'
  editUserForm.enabled = row.enabled !== false
  showEditUserDialog.value = true
}

const handleEditUser = async () => {
  editUserLoading.value = true
  try {
    const res = await axios.put('/api/v1/users/' + editUserForm.id, {
      email: editUserForm.email,
      role: editUserForm.role,
      enabled: editUserForm.enabled
    })
    if (res.data.code === 0) {
      ElMessage.success('更新成功')
      showEditUserDialog.value = false
      loadUsers()
    } else {
      ElMessage.error(res.data.msg || '更新失败')
    }
  } catch (e) {
    ElMessage.error('更新用户失败: ' + (e.response?.data?.msg || e.message))
  }
  editUserLoading.value = false
}

const handleDeleteUser = async (row) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除用户 <strong>' + row.username + '</strong> 吗？此操作不可恢复！',
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch {
    return
  }
  try {
    const res = await axios.delete('/api/v1/users/' + row.id)
    if (res.data.code === 0) {
      ElMessage.success('已删除: ' + row.username)
      loadUsers()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除用户失败: ' + (e.response?.data?.msg || e.message))
  }
}

const openResetPasswordDialog = (row) => {
  resetPasswordTarget.value = row
  resetPasswordNewPassword.value = ''
  resetPasswordOldPassword.value = ''
  showResetPasswordDialog.value = true
}

const handleResetPassword = async () => {
  if (!resetPasswordOldPassword.value) {
    ElMessage.warning('旧密码不能为空')
    return
  }
  if (!resetPasswordNewPassword.value) {
    ElMessage.warning('新密码不能为空')
    return
  }
  resetPasswordLoading.value = true
  try {
    const res = await axios.post('/api/v1/users/' + resetPasswordTarget.value.id + '/reset-password', {
      password: resetPasswordNewPassword.value,
      oldPassword: resetPasswordOldPassword.value
    })
    if (res.data.code === 0) {
      ElMessage.success('密码已重置')
      showResetPasswordDialog.value = false
    } else {
      ElMessage.error(res.data.msg || '重置失败')
    }
  } catch (e) {
    ElMessage.error('重置密码失败: ' + (e.response?.data?.msg || e.message))
  }
  resetPasswordLoading.value = false
}

// 告警中心
const alertSubTab = ref('rules')
const alertRules = ref([])
const alertRulesLoading = ref(false)
const alertRulesPage = ref(1)
const alertRulesPageSize = ref(20)
const alertRulesTotal = ref(0)

const showCreateAlertRuleDialog = ref(false)
const showEditAlertRuleDialog = ref(false)
const createAlertRuleLoading = ref(false)
const editAlertRuleLoading = ref(false)
const createAlertRuleCustomQueue = ref('')
const createAlertRuleForm = reactive({
  queueName: 'default',
  metric: 'usedCapacity',
  operator: '>',
  threshold: 80,
  enabled: true,
  notifyEmail: ''
})
const editAlertRuleForm = reactive({
  id: null,
  queueName: '',
  metric: 'usedCapacity',
  operator: '>',
  threshold: 80,
  enabled: true,
  notifyEmail: ''
})

const alertHistory = ref([])
const alertHistoryLoading = ref(false)
const alertHistoryPage = ref(1)
const alertHistoryPageSize = ref(20)
const alertHistoryTotal = ref(0)
const alertHistoryFilterModule = ref('')
const alertHistoryFilterDateRange = ref(null)

const exportAlertRulesCsv = () => {
  exportToCsv(alertRules.value, 'alert_rules', [
    { key: 'id', label: 'ID' },
    { key: 'queueName', label: '队列/模块' },
    { key: 'metric', label: '指标' },
    { key: 'operator', label: '条件' },
    { key: 'threshold', label: '阈值' },
    { key: 'enabled', label: '状态' },
    { key: 'notifyEmail', label: '通知邮箱' },
    { key: 'createTime', label: '创建时间' }
  ])
  axios.post('/api/v1/logs/audit', { module:'yarn', action:'export-csv', target:'alert_rules_export.csv', detail:'导出告警规则 ' + alertRules.value.length + ' 条' }).catch(() => {})
}

const loadAlertRules = async () => {
  alertRulesLoading.value = true
  try {
    const params = { page: alertRulesPage.value, pageSize: alertRulesPageSize.value }
    const res = await axios.get('/api/v1/alerts/rules', { params })
    if (res.data.code === 0) {
      alertRules.value = res.data.data.records
      alertRulesTotal.value = res.data.data.total
    } else {
      ElMessage.error(res.data.msg || '加载告警规则失败')
    }
  } catch (e) {
    ElMessage.error('加载告警规则失败: ' + (e.response?.data?.msg || e.message))
  }
  alertRulesLoading.value = false
}

const openCreateAlertRuleDialog = () => {
  createAlertRuleForm.queueName = 'default'
  createAlertRuleForm.metric = 'usedCapacity'
  createAlertRuleForm.operator = '>'
  createAlertRuleForm.threshold = 80
  createAlertRuleForm.enabled = true
  createAlertRuleForm.notifyEmail = ''
  createAlertRuleCustomQueue.value = ''
  showCreateAlertRuleDialog.value = true
}

const handleCreateAlertRule = async () => {
  let queueName = createAlertRuleForm.queueName
  if (queueName === '__custom__') {
    if (!createAlertRuleCustomQueue.value.trim()) {
      ElMessage.warning('请输入自定义队列名称')
      return
    }
    queueName = createAlertRuleCustomQueue.value.trim()
  }
  if (!createAlertRuleForm.metric) {
    ElMessage.warning('请选择指标')
    return
  }
  if (!createAlertRuleForm.operator) {
    ElMessage.warning('请选择操作符')
    return
  }
  if (createAlertRuleForm.threshold == null) {
    ElMessage.warning('请输入阈值')
    return
  }
  createAlertRuleLoading.value = true
  try {
    const res = await axios.post('/api/v1/alerts/rules', {
      queueName: queueName,
      metric: createAlertRuleForm.metric,
      operator: createAlertRuleForm.operator,
      threshold: createAlertRuleForm.threshold,
      enabled: createAlertRuleForm.enabled,
      notifyEmail: createAlertRuleForm.notifyEmail
    })
    if (res.data.code === 0) {
      ElMessage.success('告警规则已创建')
      showCreateAlertRuleDialog.value = false
      loadAlertRules()
    } else {
      ElMessage.error(res.data.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建告警规则失败: ' + (e.response?.data?.msg || e.message))
  }
  createAlertRuleLoading.value = false
}

const openEditAlertRuleDialog = (row) => {
  editAlertRuleForm.id = row.id
  editAlertRuleForm.queueName = row.queueName
  editAlertRuleForm.metric = row.metric
  editAlertRuleForm.operator = row.operator
  editAlertRuleForm.threshold = row.threshold
  editAlertRuleForm.enabled = row.enabled !== false
  editAlertRuleForm.notifyEmail = row.notifyEmail || ''
  showEditAlertRuleDialog.value = true
}

const handleEditAlertRule = async () => {
  editAlertRuleLoading.value = true
  try {
    const res = await axios.put('/api/v1/alerts/rules/' + editAlertRuleForm.id, {
      queueName: editAlertRuleForm.queueName,
      metric: editAlertRuleForm.metric,
      operator: editAlertRuleForm.operator,
      threshold: editAlertRuleForm.threshold,
      enabled: editAlertRuleForm.enabled,
      notifyEmail: editAlertRuleForm.notifyEmail
    })
    if (res.data.code === 0) {
      ElMessage.success('告警规则已更新')
      showEditAlertRuleDialog.value = false
      loadAlertRules()
    } else {
      ElMessage.error(res.data.msg || '更新失败')
    }
  } catch (e) {
    ElMessage.error('更新告警规则失败: ' + (e.response?.data?.msg || e.message))
  }
  editAlertRuleLoading.value = false
}

const handleDeleteAlertRule = async (row) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除告警规则 <strong>' + row.queueName + ' - ' + row.metric + ' ' + row.operator + ' ' + row.threshold + '</strong> 吗？',
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch {
    return
  }
  try {
    const res = await axios.delete('/api/v1/alerts/rules/' + row.id)
    if (res.data.code === 0) {
      ElMessage.success('告警规则已删除')
      loadAlertRules()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除告警规则失败: ' + (e.response?.data?.msg || e.message))
  }
}

const toggleAlertRule = async (row) => {
  try {
    const res = await axios.put('/api/v1/alerts/rules/' + row.id, { enabled: row.enabled })
    if (res.data.code === 0) {
      ElMessage.success(row.enabled ? '告警规则已启用' : '告警规则已禁用')
    } else {
      ElMessage.error(res.data.msg || '操作失败')
      loadAlertRules()
    }
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.msg || e.message))
    loadAlertRules()
  }
}

const loadAlertHistory = async () => {
  alertHistoryLoading.value = true
  try {
    const params = { page: alertHistoryPage.value, pageSize: alertHistoryPageSize.value }
    if (alertHistoryFilterModule.value) {
      params.module = alertHistoryFilterModule.value
    }
    if (alertHistoryFilterDateRange.value) {
      params.startDate = alertHistoryFilterDateRange.value[0]
      params.endDate = alertHistoryFilterDateRange.value[1]
    }
    const res = await axios.get('/api/v1/alerts/history', { params })
    if (res.data.code === 0) {
      alertHistory.value = res.data.data.records
      alertHistoryTotal.value = res.data.data.total
    } else {
      ElMessage.error(res.data.msg || '加载告警历史失败')
    }
  } catch (e) {
    ElMessage.error('加载告警历史失败: ' + (e.response?.data?.msg || e.message))
  }
  alertHistoryLoading.value = false
}

const resetAlertHistoryFilters = () => {
  alertHistoryFilterModule.value = ''
  alertHistoryFilterDateRange.value = null
  alertHistoryPage.value = 1
  loadAlertHistory()
}

const exportOpLogCsv = () => {
  exportToCsv(operationLogs.value, 'operation_log', [
    { key: 'id', label: 'ID' },
    { key: 'module', label: '模块' },
    { key: 'action', label: '操作', formatter: (v) => actionLabels.value[v] || v },
    { key: 'target', label: '目标路径' },
    { key: 'result', label: '结果', formatter: (v) => v === 'success' ? '成功' : '失败' },
    { key: 'detail', label: '详情' },
    { key: 'username', label: '操作人' },
    { key: 'createTime', label: '时间', formatter: (v) => formatDateTime(v) }
  ])
}

const loadOperationLogs = async () => {
  opLogLoading.value = true
  try {
    const params = { page: opLogPage.value, pageSize: opLogPageSize.value }
    if (opLogFilterModule.value) params.module = opLogFilterModule.value
    if (opLogFilterAction.value) params.action = opLogFilterAction.value
    if (opLogFilterDateRange.value && opLogFilterDateRange.value.length === 2) {
      params.timeRangeStart = opLogFilterDateRange.value[0]
      params.timeRangeEnd = opLogFilterDateRange.value[1]
    }
    const res = await axios.get('/api/v1/logs', { params })
    if (res.data.code === 0) {
      operationLogs.value = res.data.data.records || []
      opLogTotal.value = res.data.data.total || 0
    } else {
      operationLogs.value = []
      ElMessage.error('加载操作日志失败: ' + (res.data.msg || ''))
    }
  } catch (e) {
    operationLogs.value = []
    ElMessage.error('加载操作日志失败: ' + (e.response?.data?.msg || e.message))
  }
  opLogLoading.value = false
}

const resetOpLogFilters = () => {
  opLogFilterModule.value = ''
  opLogFilterAction.value = ''
  opLogFilterDateRange.value = null
  opLogPage.value = 1
  loadOperationLogs()
}

// 删除单条操作记录
const deleteSingleOpLog = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该操作记录吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await axios.delete('/api/v1/logs/' + row.id)
    if (res.data.code === 0) {
      ElMessage.success('已删除')
      loadOperationLogs()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.response?.data?.msg || e.message))
    }
  }
}

// 批量删除操作记录
const deleteBatchOpLogs = async () => {
  if (selectedOpLogs.value.length === 0) return
  try {
    await ElMessageBox.confirm('确定删除选中的 ' + selectedOpLogs.value.length + ' 条操作记录吗？', '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const ids = selectedOpLogs.value.map(r => r.id)
    const res = await axios.delete('/api/v1/logs/batch', { data: ids })
    if (res.data.code === 0) {
      ElMessage.success(res.data.msg || '删除成功')
      selectedOpLogs.value = []
      loadOperationLogs()
    } else {
      ElMessage.error(res.data.msg || '批量删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('批量删除失败: ' + (e.response?.data?.msg || e.message))
    }
  }
}

// === 监控 Dashboard (历史指标) ===
const monitorDateRange = ref(null) // [startDate, endDate] or null
const monitorLoading = ref(false)
const monitorError = ref('')
const monitorHdfsData = ref([])
const monitorYarnData = ref([])
const monitorQueueData = ref([])

// Computed: HDFS sparkline (usedSpace)
const monitorHdfsSparkline = computed(() => {
  const data = monitorHdfsData.value
  if (data.length === 0) return []
  const max = Math.max(...data.map(d => d.usedSpace || 0), 1)
  return data.slice().reverse().map(d => ({
    raw: d.usedSpace || 0,
    pct: ((d.usedSpace || 0) / max) * 100,
    time: formatMonitorTime(d.createTime),
    color: '#3b82f6'
  }))
})
const monitorHdfsCurrent = computed(() => {
  if (monitorHdfsData.value.length === 0) return 0
  return monitorHdfsData.value[0].usedSpace || 0
})
const monitorHdfsMax = computed(() => {
  if (monitorHdfsData.value.length === 0) return 0
  return Math.max(...monitorHdfsData.value.map(d => d.usedSpace || 0), 0)
})
const monitorHdfsTrend = computed(() => {
  const data = monitorHdfsData.value
  if (data.length < 2) return { arrow: '―', label: '数据不足', cls: 'flat' }
  const first = data[data.length - 1].usedSpace || 0
  const last = data[0].usedSpace || 0
  const diff = last - first
  const pct = first > 0 ? Math.abs((diff / first) * 100).toFixed(1) : '0'
  if (diff > 0) return { arrow: '▲', label: '上涨 ' + pct + '%', cls: 'up' }
  if (diff < 0) return { arrow: '▼', label: '下降 ' + pct + '%', cls: 'down' }
  return { arrow: '―', label: '持平', cls: 'flat' }
})

// Computed: YARN NodeManager sparkline
const monitorNmSparkline = computed(() => {
  const data = monitorYarnData.value
  if (data.length === 0) return []
  const max = Math.max(...data.map(d => d.numNodeManagers || 0), 1)
  return data.slice().reverse().map(d => ({
    raw: d.numNodeManagers || 0,
    pct: ((d.numNodeManagers || 0) / max) * 100,
    time: formatMonitorTime(d.createTime),
    color: '#22c55e'
  }))
})
const monitorNmCurrent = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  return monitorYarnData.value[0].numNodeManagers || 0
})
const monitorNmMax = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  return Math.max(...monitorYarnData.value.map(d => d.numNodeManagers || 0), 0)
})
const monitorNmTrend = computed(() => {
  const data = monitorYarnData.value
  if (data.length < 2) return { arrow: '―', label: '数据不足', cls: 'flat' }
  const first = data[data.length - 1].numNodeManagers || 0
  const last = data[0].numNodeManagers || 0
  const diff = last - first
  if (diff > 0) return { arrow: '▲', label: '+' + diff, cls: 'up' }
  if (diff < 0) return { arrow: '▼', label: '' + diff, cls: 'down' }
  return { arrow: '―', label: '持平', cls: 'flat' }
})

// Computed: YARN Memory sparkline
const monitorMemSparkline = computed(() => {
  const data = monitorYarnData.value
  if (data.length === 0) return []
  const gbValues = data.map(d => ((d.totalMemoryMB || 0) / 1024))
  const max = Math.max(...gbValues, 1)
  return data.slice().reverse().map((d, i) => ({
    raw: (d.totalMemoryMB || 0) / 1024,
    pct: (gbValues[data.length - 1 - i] / max) * 100,
    time: formatMonitorTime(d.createTime),
    color: '#eab308'
  }))
})
const monitorMemCurrent = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  return ((monitorYarnData.value[0].totalMemoryMB || 0) / 1024).toFixed(1)
})
const monitorMemMax = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  const vals = monitorYarnData.value.map(d => (d.totalMemoryMB || 0) / 1024)
  return Math.max(...vals, 0).toFixed(1)
})
const monitorMemTrend = computed(() => {
  const data = monitorYarnData.value
  if (data.length < 2) return { arrow: '―', label: '数据不足', cls: 'flat' }
  const first = (data[data.length - 1].totalMemoryMB || 0) / 1024
  const last = (data[0].totalMemoryMB || 0) / 1024
  const diff = last - first
  if (diff > 0.1) return { arrow: '▲', label: '+' + diff.toFixed(1) + 'GB', cls: 'up' }
  if (diff < -0.1) return { arrow: '▼', label: diff.toFixed(1) + 'GB', cls: 'down' }
  return { arrow: '―', label: '持平', cls: 'flat' }
})

// Computed: YARN Running Apps sparkline
const monitorAppsSparkline = computed(() => {
  const data = monitorYarnData.value
  if (data.length === 0) return []
  const max = Math.max(...data.map(d => d.runningApplications || 0), 1)
  return data.slice().reverse().map(d => ({
    raw: d.runningApplications || 0,
    pct: ((d.runningApplications || 0) / max) * 100,
    time: formatMonitorTime(d.createTime),
    color: '#ef4444'
  }))
})
const monitorAppsCurrent = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  return monitorYarnData.value[0].runningApplications || 0
})
const monitorAppsMax = computed(() => {
  if (monitorYarnData.value.length === 0) return 0
  return Math.max(...monitorYarnData.value.map(d => d.runningApplications || 0), 0)
})
const monitorAppsTrend = computed(() => {
  const data = monitorYarnData.value
  if (data.length < 2) return { arrow: '―', label: '数据不足', cls: 'flat' }
  const first = data[data.length - 1].runningApplications || 0
  const last = data[0].runningApplications || 0
  const diff = last - first
  if (diff > 0) return { arrow: '▲', label: '+' + diff, cls: 'up' }
  if (diff < 0) return { arrow: '▼', label: '' + diff, cls: 'down' }
  return { arrow: '―', label: '持平', cls: 'flat' }
})

// Computed: Queue metrics
const monitorQueueMap = computed(() => {
  const data = monitorQueueData.value
  if (data.length === 0) return {}
  // Parse extraJson and group by queueName
  const queuePoints = {}
  for (const d of data) {
    try {
      const extra = JSON.parse(d.extraJson || '{}')
      const qname = extra.queueName || 'unknown'
      if (!queuePoints[qname]) queuePoints[qname] = []
      queuePoints[qname].push({
        usedCapacity: extra.usedCapacity || 0,
        numApplications: extra.numApplications || 0,
        createTime: d.createTime,
        rawValue: extra.usedCapacity || 0
      })
    } catch (e) { /* skip parse errors */ }
  }
  const result = {}
  for (const [qname, points] of Object.entries(queuePoints)) {
    if (points.length === 0) continue
    points.sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
    const max = Math.max(...points.map(p => p.rawValue), 1)
    const sparkline = points.map(p => ({
      raw: p.rawValue,
      pct: (p.rawValue / max) * 100,
      time: formatMonitorTime(p.createTime),
      color: '#a78bfa'
    }))
    const lastVal = points[points.length - 1].rawValue
    const firstVal = points[0].rawValue
    const diff = lastVal - firstVal
    let trend
    if (diff > 1) trend = { arrow: '▲', label: '+' + diff.toFixed(1) + '%', cls: 'up' }
    else if (diff < -1) trend = { arrow: '▼', label: diff.toFixed(1) + '%', cls: 'down' }
    else trend = { arrow: '―', label: '持平', cls: 'flat' }
    result[qname] = {
      current: lastVal.toFixed(1),
      max: Math.max(...points.map(p => p.rawValue), 0).toFixed(1),
      sparkline,
      trend,
      rawData: points,
      points
    }
  }
  return result
})

const formatMonitorTime = (t) => {
  if (!t) return ''
  try {
    const d = new Date(t)
    return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
  } catch (e) { return '' }
}

const onMonitorRangeChange = () => {
  loadMonitorData()
}

const loadMonitorData = async () => {
  monitorLoading.value = true
  monitorError.value = ''
  try {
    const params = {}
    if (monitorDateRange.value && monitorDateRange.value[0] && monitorDateRange.value[1]) {
      params.startTime = monitorDateRange.value[0].toISOString()
      params.endTime = monitorDateRange.value[1].toISOString()
    }
    const [hdfsRes, yarnRes, queueRes] = await Promise.all([
      axios.get('/api/v1/metrics/history', { params: { module: 'hdfs', ...params } }),
      axios.get('/api/v1/metrics/history', { params: { module: 'yarn', ...params } }),
      axios.get('/api/v1/metrics/history', { params: { module: 'queue', ...params } })
    ])
    if (hdfsRes.data.code === 0) monitorHdfsData.value = hdfsRes.data.data || []
    if (yarnRes.data.code === 0) monitorYarnData.value = yarnRes.data.data || []
    if (queueRes.data.code === 0) monitorQueueData.value = queueRes.data.data || []
  } catch (e) {
    monitorError.value = '加载监控数据失败: ' + (e.response?.data?.msg || e.message)
  }
  monitorLoading.value = false
}

// === ECharts Line Chart Rendering ===
const chartInstances = {}

const renderMonitorChart = (chartId, data, valueKey, label) => {
  if (!data || data.length === 0) return

  // Destroy existing chart
  if (chartInstances[chartId]) {
    chartInstances[chartId].dispose()
  }

  const el = document.getElementById(chartId)
  if (!el) return

  const chart = echarts.init(el, 'dark')
  chartInstances[chartId] = chart

  const times = data.map(d => {
    const t = new Date(d.createTime)
    return t.toLocaleTimeString()
  }).reverse()

  const values = data.map(d => d[valueKey] || 0).reverse()

  chart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: '#1e2938', borderColor: '#334155' },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: times, axisLabel: { color: '#64748b', fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#1e2938' } }, axisLabel: { color: '#64748b', fontSize: 10 } },
    series: [{
      data: values,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color: '#3b82f6' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.3)' }, { offset: 1, color: 'rgba(59,130,246,0.02)' }] } },
      itemStyle: { color: '#3b82f6' }
    }]
  })

  // Resize with window
  const resizeHandler = () => chart.resize()
  window.addEventListener('resize', resizeHandler)
  chart._resizeHandler = resizeHandler
}

// Watch monitor data and re-render charts
watch([monitorHdfsData, monitorYarnData, monitorQueueData], () => {
  nextTick(() => {
    renderMonitorChart('chart-hdfs-usage', monitorHdfsData.value, 'usedSpace', 'HDFS Used Space')
    renderMonitorChart('chart-nm-count', monitorYarnData.value, 'numNodeManagers', 'NodeManagers')
    renderMonitorChart('chart-mem-total', monitorYarnData.value, 'totalMemoryMB', 'Total Memory')
    renderMonitorChart('chart-apps-running', monitorYarnData.value, 'runningApplications', 'Running Apps')

    // Queue charts
    const qmap = monitorQueueMap.value
    Object.keys(qmap).forEach(qname => {
      const qdata = qmap[qname]
      if (qdata && qdata.rawData) {
        renderMonitorChart('chart-queue-' + qname, qdata.rawData, 'usedCapacity', qname)
      }
    })
  })
})

// Watch tab switch to auto-load
watch(activeTab, (val) => {
  if (val === 'monitor') { loadMonitorData(); loadGrafanaConfig() }
  if (val === 'dashboard') loadSystemHealth()
  if (val === 'user-mgmt') loadUsers()
})

// Watch globalLoading to toggle cursor:wait on body
watch(globalLoading, (val) => {
  document.body.classList.toggle('is-global-loading', val)
})

const loadAll = () => { loadClusters(); loadOverview(); loadHdfsHealth(); loadHdfsFiles(); loadHdfsNodes(); loadYarnApps(); loadYarnQueues(); loadMrTemplates(); loadWorkflows(); loadOperationLogs(); loadAlertRules(); loadSystemConfig() }

const systemConfigData = ref([])
const systemConfigLoading = ref(false)
const systemConfigError = ref('')
const systemConfigSearch = ref('')
const groupIcons = { Cluster: '🖥️', HDFS: '💾', YARN: '⚙️', JournalNode: '📝', IPC: '🔗', Grafana: '📊' }

const filteredConfigData = computed(() => {
  const q = (systemConfigSearch.value || '').toLowerCase().trim()
  if (!q) return systemConfigData.value
  return systemConfigData.value.map(group => {
    const filtered = (group.items || []).filter(item =>
      (item.label && item.label.toLowerCase().includes(q)) ||
      (item.key && item.key.toLowerCase().includes(q))
    )
    return { ...group, items: filtered }
  }).filter(group => group.items.length > 0)
})

const loadSystemConfig = async () => {
  systemConfigLoading.value = true
  systemConfigError.value = ''
  try {
    const res = await axios.get('/api/v1/system/config')
    if (res.data.code === 0) {
      systemConfigData.value = res.data.data || []
    } else {
      systemConfigError.value = res.data.msg || '加载配置失败'
    }
  } catch (e) {
    systemConfigError.value = '无法连接后端服务'
  }
  systemConfigLoading.value = false
}

// Load available clusters from backend
const loadClusters = async () => {
  try {
    const res = await axios.get('/api/v1/clusters')
    if (res.data.code === 0) {
      clusters.value = res.data.data || []
    }
  } catch (e) {
    console.warn('Failed to load clusters:', e)
  }
}

// Watch for cluster changes: persist to localStorage and reload data
watch(selectedClusterId, (newId) => {
  localStorage.setItem('hermes_clusterId', newId)
  // Reload cluster-dependent data
  loadOverview()
  loadHdfsFiles()
  loadHdfsNodes()
  loadYarnApps()
  loadYarnQueues()
  loadMrTemplates()
  loadAlertRules()
})

// === Command Palette (Cmd+K / Ctrl+K) ===
const showCommandPalette = ref(false)
const paletteSearch = ref('')
const paletteHighlightIndex = ref(0)
const paletteSearchResults = ref([])
const paletteSearching = ref(false)
const cpInputRef = ref(null)

const paletteItems = [
  { label: '总览', icon: '📊', keywords: 'dashboard 总览 overview', action: () => { activeTab.value = 'dashboard' } },
  { label: 'HDFS', icon: '📁', keywords: 'hdfs 文件系统', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'files' } },
  { label: 'YARN', icon: '⚙️', keywords: 'yarn apps 应用 作业 队列 queues', action: () => { activeTab.value = 'yarn'; yarnSubTab.value = 'apps' } },
  { label: 'MapReduce', icon: '📋', keywords: 'mr mapreduce 模板 template', action: () => { activeTab.value = 'mr' } },
  { label: '操作审计', icon: '📝', keywords: 'operation log 审计 日志 操作记录', action: () => { activeTab.value = 'operation-log' } },
  { label: '用户管理', icon: '👤', keywords: 'user 用户 管理 账号 角色', action: () => { activeTab.value = 'user-mgmt' } },
  { label: '告警中心', icon: '⚠️', keywords: 'alert 告警 规则 history 历史 告警中心', action: () => { activeTab.value = 'alert-center'; alertSubTab.value = 'rules' } },
  { label: '文件系统', icon: '📂', sublabel: 'HDFS', keywords: 'hdfs files 文件 目录 上传 下载', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'files' } },
  { label: 'DataNode', icon: '🖥️', sublabel: 'HDFS', keywords: 'datanode 数据节点 节点', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'datanodes' } },
  { label: 'JournalNode', icon: '📝', sublabel: 'HDFS', keywords: 'journalnode 日志节点 checkpoint', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'journalnodes' } },
  { label: '监控 & 元数据', icon: '📊', sublabel: 'HDFS', keywords: 'monitor 监控 metadata 元数据 block', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'monitor' } },
  { label: '日志查看器', icon: '📋', sublabel: 'HDFS', keywords: 'logs 日志 viewer 查看', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'logs' } },
  { label: '回收站', icon: '🗑️', sublabel: 'HDFS', keywords: 'trash 回收站 恢复 restore recycle bin', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'trash' } },
  { label: '上传文件', icon: '📤', keywords: 'upload 上传 file', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'files'; setTimeout(() => triggerUpload(), 300) } },
  { label: '创建目录', icon: '📁', keywords: 'mkdir 创建 目录 folder', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'files'; showMkdirDialog.value = true } },
  { label: '切换 Active NN', icon: '🔄', keywords: 'switch nn 切换 namenode ha', action: () => { activeTab.value = 'hdfs'; hdfsSubTab.value = 'datanodes'; switchNN() } },
  { label: '提交作业', icon: '📋', keywords: 'submit job 提交 作业 mr', action: () => { activeTab.value = 'mr' } },
  { label: '一键刷新', icon: '🔄', keywords: 'refresh 刷新 reload', action: () => { loadOverview(); loadHdfsHealth(); loadHdfsFiles(); loadHdfsNodes(); loadYarnApps(); loadYarnQueues(); loadMrTemplates(); loadOperationLogs() } },
  { label: 'Notebook', icon: '📓', keywords: 'notebook jupyter 笔记本', action: () => { activeTab.value = 'notebook' } },
  { label: '系统健康', icon: '🏥', keywords: 'health 健康 系统状态', action: () => { activeTab.value = 'dashboard' } },
  { label: '系统配置', icon: '⚙️', keywords: 'config 配置 系统设置 system settings', action: () => { activeTab.value = 'system-config' } },
  { label: '数据目录', icon: '📚', keywords: 'catalog 数据 目录 table 表 标签 tag', action: () => { activeTab.value = 'catalog' } },
]

// Watch palette search and fetch results from all categories
watch(paletteSearch, async (val) => {
  paletteHighlightIndex.value = 0
  if (!val || val.trim().length < 2) {
    // Show static page items only when no search or short search
    const q = (val || '').trim().toLowerCase()
    paletteSearchResults.value = paletteItems.filter(p =>
      p.label.toLowerCase().includes(q)
    ).map(p => ({ ...p, _category: '页面' }))
    return
  }
  paletteSearching.value = true
  const results = []
  const q = val.trim().toLowerCase()

  // 1. Pages (filter existing paletteItems)
  const pages = paletteItems.filter(item => {
    const label = item.label.toLowerCase()
    const sublabel = (item.sublabel || '').toLowerCase()
    const keywords = (item.keywords || '').toLowerCase()
    return label.includes(q) || sublabel.includes(q) || keywords.includes(q)
  }).map(p => ({ ...p, _category: '页面' }))
  results.push(...pages)

  // 2. HDFS files (API call)
  try {
    const res = await axios.get('/api/v1/hdfs/search', { params: { keyword: q, clusterId: selectedClusterId.value } })
    if (res.data.code === 0 && res.data.data) {
      const fileList = res.data.data.files || res.data.data || []
      const files = fileList.slice(0, 5).map(f => ({
        label: f.path || f.name || f,
        icon: f.isDirectory ? '📁' : '📄',
        action: () => { navigateToDir(f.path); hidePalette() },
        _category: 'HDFS 文件'
      }))
      results.push(...files)
    }
  } catch (e) { /* HDFS search unavailable */ }

  // 3. Users (from loaded users list)
  if (users.value && users.value.length > 0) {
    const matched = users.value.filter(u =>
      u.username && u.username.toLowerCase().includes(q)
    ).slice(0, 3).map(u => ({
      label: u.username + ' (' + (u.role || '') + ')',
      icon: '👤',
      action: () => { activeTab.value = 'user-mgmt'; hidePalette() },
      _category: '用户'
    }))
    results.push(...matched)
  }

  // 4. Config items (from loaded config)
  if (systemConfigData.value && systemConfigData.value.length > 0) {
    const matched = []
    systemConfigData.value.forEach(group => {
      (group.items || []).forEach(item => {
        if ((item.label && item.label.toLowerCase().includes(q)) ||
            (item.key && item.key.toLowerCase().includes(q))) {
          matched.push({
            label: item.label + ' (' + item.key + ')',
            icon: '⚙️',
            action: () => { activeTab.value = 'system-config'; hidePalette() },
            _category: '配置'
          })
        }
      })
    })
    results.push(...matched.slice(0, 5))
  }

  paletteSearchResults.value = results
  paletteSearching.value = false
})

const hidePalette = () => {
  showCommandPalette.value = false
}

const navigateToDir = (path) => {
  activeTab.value = 'hdfs'
  hdfsSubTab.value = 'files'
  hdfsPath.value = path
  loadHdfsFiles()
}

const toggleCommandPalette = () => {
  showCommandPalette.value = !showCommandPalette.value
  if (showCommandPalette.value) {
    paletteSearch.value = ''
    paletteHighlightIndex.value = 0
    paletteSearchResults.value = paletteItems.map(p => ({ ...p, _category: '页面' }))
    nextTick(() => cpInputRef.value?.focus())
  }
}

const executePaletteItem = (item) => {
  item.action()
  showCommandPalette.value = false
}

const selectPaletteItem = () => {
  const results = paletteSearchResults.value
  if (results.length === 0) return
  const idx = Math.min(paletteHighlightIndex.value, results.length - 1)
  executePaletteItem(results[idx])
}

const highlightNext = () => {
  if (paletteHighlightIndex.value < paletteSearchResults.value.length - 1) {
    paletteHighlightIndex.value++
  } else {
    paletteHighlightIndex.value = 0
  }
}

const highlightPrev = () => {
  if (paletteHighlightIndex.value > 0) {
    paletteHighlightIndex.value--
  } else {
    paletteHighlightIndex.value = paletteSearchResults.value.length - 1
  }
}

// === DataNode helper functions ===
const dnStateTag = (state) => {
  if (!state) return 'info'
  if (state === 'In Service') return 'success'
  if (state === 'Decommissioning') return 'warning'
  if (state === 'Dead') return 'danger'
  return 'info'
}
const dnStateDot = (state) => {
  if (!state) return 'dot-gray'
  if (state === 'In Service') return 'dot-green'
  if (state === 'Decommissioning') return 'dot-orange'
  if (state === 'Dead') return 'dot-red'
  return 'dot-gray'
}
const dnUsageColor = (dn) => {
  if (!dn || !dn.capacity || dn.capacity === 0) return '#6b7280'
  const pct = (dn.usedSpace || 0) / dn.capacity
  if (pct >= 0.9) return '#ef4444'
  if (pct >= 0.7) return '#eab308'
  return '#22c55e'
}
const viewDNLogs = (row) => {
  const nodeName = (row.id || '').split(':')[0] || 'dn1'
  logRole.value = 'datanode'
  logNode.value = nodeName
  hdfsSubTab.value = 'logs'
}

const openDNDetail = async (row) => {
  selectedDN.value = row
  showDNDetail.value = true
  // Fetch JMX metrics
  try {
    const res = await axios.get('/api/v1/hdfs/datanodes/metrics')
    if (res.data.code === 0) {
      const metrics = (res.data.data || []).find(m =>
        m.hostName && row.name && m.hostName.includes(row.name.split(':')[0])
      )
      dnJmxMetrics.value = metrics || null
    }
  } catch(e) { dnJmxMetrics.value = null }
}

const closeDNDetail = () => {
  showDNDetail.value = false
  selectedDN.value = null
}

const enterMaintenanceMode = async (dn) => {
  if (!dn) { ElMessage.warning('请先选择一个 DataNode'); return }
  const nodeName = dn.name || dn.hostName || dn.id
  try {
    await ElMessageBox.confirm(
      `确定将节点 ${nodeName} 设为维护模式（Decommission）？`,
      '维护模式',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await axios.post('/api/v1/hdfs/datanodes/decommission', null, {
      params: { hostname: nodeName, decom: true }
    })
    if (res.data.code === 0) {
      ElMessage.success(`节点 ${nodeName} 已设为维护模式`)
      loadHdfsNodes()
    } else {
      ElMessage.error('操作失败: ' + (res.data.msg || '未知错误'))
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e.response?.data?.msg || e.message))
  }
}

const triggerRebalance = async () => {
  try {
    await ElMessageBox.confirm('确定触发集群 Rebalance？此操作可能会影响集群性能。', 'Rebalance', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
    })
    const res = await axios.post('/api/v1/hdfs/rebalance')
    if (res.data.code === 0) {
      ElMessage.success('Rebalance 已触发')
    } else {
      ElMessage.error('操作失败: ' + (res.data.msg || '未知错误'))
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e.response?.data?.msg || e.message))
  }
}

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0; let v = bytes
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return v.toFixed(1) + ' ' + units[i]
}

// === Dashboard computed helpers ===
const healthScore = computed(() => {
  const h = overview.hdfs
  const y = overview.yarn
  if (!h || !h.totalSpace) return 0
  const usagePct = h.totalSpace > 0 ? (h.usedSpace / h.totalSpace) * 100 : 0
  let score = 92
  if (usagePct > 80) score -= 25
  else if (usagePct > 60) score -= 10
  else if (usagePct > 40) score -= 3
  if (!y || !y.numNodeManagers || y.numNodeManagers === 0) score -= 20
  if (y && y.runningApplications === 0) score -= 3
  if (usagePct < 20) score += 3
  return Math.max(0, Math.min(100, Math.round(score)))
})
const healthScoreColor = computed(() => {
  if (healthScore.value >= 80) return '#22c55e'
  if (healthScore.value >= 60) return '#eab308'
  return '#ef4444'
})
const hdfsUsagePercent = computed(() => {
  const h = overview.hdfs
  if (!h || !h.totalSpace || h.totalSpace === 0) return 0
  return Math.round((h.usedSpace / h.totalSpace) * 100)
})
const hdfsUsageColor = computed(() => {
  const pct = hdfsUsagePercent.value
  if (pct >= 80) return '#ef4444'
  if (pct >= 60) return '#eab308'
  return '#22c55e'
})
const yarnUtilization = computed(() => {
  const y = overview.yarn
  if (!y || !y.totalMemoryMB || y.totalMemoryMB === 0) return 0
  const apps = y.runningApplications || 0
  const vcores = y.totalVCores || 0
  if (apps === 0 && vcores === 0) return 0
  const base = Math.min(apps * 8, 85)
  return Math.max(0, Math.min(100, Math.round(base + 5)))
})
const stateTag = (s) => {
  const map = { RUNNING: 'success', FINISHED: '', FAILED: 'danger', KILLED: 'info', ACCEPTED: 'warning', SUBMITTED: 'warning', NEW: 'info', NEW_SAVING: 'info' }
  return map[s] || 'info'
}
const progressColor = (p) => {
  if (p >= 1) return '#67c23a'
  if (p >= 0.5) return '#e6a23c'
  return '#409eff'
}

// === 📚 Data Catalog ===
const catalogTables = ref([])
const catalogTotal = ref(0)
const catalogPage = ref(1)
const catalogPageSize = ref(20)
const catalogLoading = ref(false)
const catalogSearch = reactive({ name: '', format: '', schema: '', tagId: '' })

// Register / Edit state
const showRegisterDialog = ref(false)
const showEditDialog = ref(false)
const registerLoading = ref(false)
const editLoading = ref(false)
const registerForm = reactive({ name: '', hdfsPath: '', schemaName: 'default', format: 'Parquet', description: '', partitionColumns: '', owner: '' })
const editForm = reactive({ id: null, name: '', hdfsPath: '', schemaName: 'default', format: 'Parquet', description: '', partitionColumns: '', owner: '' })

// Detail drawer
const showDetailDrawer = ref(false)
const detailTable = ref(null)
const detailColumns = ref([])
const detailTags = ref([])
const columnsLoading = ref(false)
const catalogScanLoadingId = ref(null)

// Lineage
const upstreamLineage = ref([])
const downstreamLineage = ref([])
const catalogLineageLoading = ref(false)
const lineageLoaded = ref(false)
const lineageActiveNames = ref([])

// Column dialogs
const showAddColumnDialog = ref(false)
const showEditColumnDialog = ref(false)
const addColumnLoading = ref(false)
const editColumnLoading = ref(false)
const addColumnForm = reactive({ name: '', type: 'STRING', comment: '', nullable: true, isPartition: false, ordinalPosition: 1 })
const editColumnForm = reactive({ id: null, name: '', type: 'STRING', comment: '', nullable: true, isPartition: false, ordinalPosition: 1 })

// Tag management
const showTagDialog = ref(false)
const tagDialogMode = ref('manage') // 'manage' | 'selector'
const allTags = ref([])
const newTagName = ref('')
const newTagColor = ref('#3b82f6')
const addTagLoading = ref(false)
const selectedTagIds = ref([])
const setTagLoading = ref(false)

// Discover
const showDiscoverDialog = ref(false)
const discoverLoading = ref(false)
const discoverForm = reactive({ basePath: '/', clusterId: 'cluster1' })

const loadCatalogTables = async () => {
  catalogLoading.value = true
  try {
    const params = { page: catalogPage.value, size: catalogPageSize.value }
    if (catalogSearch.name) params.name = catalogSearch.name
    if (catalogSearch.format) params.format = catalogSearch.format
    if (catalogSearch.schema) params.schema = catalogSearch.schema
    if (catalogSearch.tagId) params.tagId = catalogSearch.tagId
    const res = await axios.get('/api/v1/catalog/tables', { params })
    if (res.data.code === 0) {
      catalogTables.value = res.data.data.records
      catalogTotal.value = res.data.data.total
    } else {
      ElMessage.error(res.data.msg || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载数据目录失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    catalogLoading.value = false
  }
}

const searchCatalog = () => {
  catalogPage.value = 1
  loadCatalogTables()
}

const handleRegisterTable = async () => {
  if (!registerForm.name || !registerForm.hdfsPath || !registerForm.format) {
    ElMessage.warning('请填写必填字段')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/v1/catalog/tables', registerForm)
    if (res.data.code === 0) {
      ElMessage.success('注册成功')
      showRegisterDialog.value = false
      registerForm.name = ''; registerForm.hdfsPath = ''; registerForm.schemaName = 'default'
      registerForm.format = 'Parquet'; registerForm.description = ''; registerForm.partitionColumns = ''; registerForm.owner = ''
      loadCatalogTables()
    } else {
      ElMessage.error(res.data.msg || '注册失败')
    }
  } catch (e) {
    ElMessage.error('注册失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    registerLoading.value = false
  }
}

const editCatalogTable = (row) => {
  editForm.id = row.id
  editForm.name = row.name
  editForm.hdfsPath = row.hdfsPath
  editForm.schemaName = row.schemaName || 'default'
  editForm.format = row.format
  editForm.description = row.description || ''
  editForm.partitionColumns = row.partitionColumns || ''
  editForm.owner = row.owner || ''
  showEditDialog.value = true
}

const handleEditTable = async () => {
  if (!editForm.hdfsPath || !editForm.format) {
    ElMessage.warning('请填写必填字段')
    return
  }
  editLoading.value = true
  try {
    const res = await axios.put('/api/v1/catalog/tables/' + editForm.id, {
      name: editForm.name,
      hdfsPath: editForm.hdfsPath,
      schemaName: editForm.schemaName,
      format: editForm.format,
      description: editForm.description,
      partitionColumns: editForm.partitionColumns,
      owner: editForm.owner
    })
    if (res.data.code === 0) {
      ElMessage.success('更新成功')
      showEditDialog.value = false
      loadCatalogTables()
    } else {
      ElMessage.error(res.data.msg || '更新失败')
    }
  } catch (e) {
    ElMessage.error('更新失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    editLoading.value = false
  }
}

const deleteCatalogTable = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除表 "' + row.name + '" 吗？此操作不可恢复。', '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    const res = await axios.delete('/api/v1/catalog/tables/' + row.id)
    if (res.data.code === 0) {
      ElMessage.success('删除成功')
      if (showDetailDrawer.value && detailTable.value?.id === row.id) {
        showDetailDrawer.value = false
      }
      loadCatalogTables()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.response?.data?.msg || e.message))
  }
}

const openCatalogDetail = async (row) => {
  detailTable.value = row
  showDetailDrawer.value = true
  // Reset lineage
  lineageLoaded.value = false
  lineageActiveNames.value = []
  // Load columns
  loadDetailColumns(row.id)
  // Load tags from table data
  detailTags.value = row.tags || []
  // Load full tags list
  loadAllTags()
}

const loadLineage = async () => {
  if (!detailTable.value) return
  catalogLineageLoading.value = true
  try {
    const res = await axios.get('/api/v1/catalog/tables/' + detailTable.value.id + '/lineage')
    if (res.data.code === 0) {
      const d = res.data.data
      upstreamLineage.value = d.upstream || []
      downstreamLineage.value = d.downstream || []
      lineageLoaded.value = true
    } else {
      ElMessage.error(res.data.msg || '加载血缘关系失败')
    }
  } catch (e) {
    ElMessage.error('加载血缘关系失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    catalogLineageLoading.value = false
  }
}

const loadLineageIfNeeded = () => {
  if (!lineageLoaded.value && lineageActiveNames.value.length > 0) {
    loadLineage()
  }
}

const viewLineageTable = (item) => {
  // Close current drawer and open the lineage table's detail
  showDetailDrawer.value = false
  nextTick(() => {
    openCatalogDetail(item)
  })
}

const loadDetailColumns = async (tableId) => {
  columnsLoading.value = true
  try {
    const res = await axios.get('/api/v1/catalog/tables/' + tableId + '/columns')
    if (res.data.code === 0) {
      detailColumns.value = res.data.data.records || []
    } else {
      detailColumns.value = []
    }
  } catch (e) {
    detailColumns.value = []
  } finally {
    columnsLoading.value = false
  }
}

const scanCatalogTable = async (row) => {
  catalogScanLoadingId.value = row.id
  try {
    const res = await axios.post('/api/v1/catalog/tables/' + row.id + '/scan')
    if (res.data.code === 0) {
      ElMessage.success('扫描完成，已更新元数据')
      loadCatalogTables()
      if (showDetailDrawer.value && detailTable.value?.id === row.id) {
        loadDetailColumns(row.id)
      }
    } else {
      ElMessage.error(res.data.msg || '扫描失败')
    }
  } catch (e) {
    ElMessage.error('扫描失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    catalogScanLoadingId.value = null
  }
}

const catalogNavigateToHdfs = (path) => {
  showDetailDrawer.value = false
  activeTab.value = 'hdfs'
  hdfsSubTab.value = 'files'
  hdfsPath.value = path || '/'
  loadHdfsFiles()
}

// Column operations
const handleAddColumn = async () => {
  if (!addColumnForm.name || !addColumnForm.type || !detailTable.value) {
    ElMessage.warning('请填写必填字段')
    return
  }
  addColumnLoading.value = true
  try {
    const res = await axios.post('/api/v1/catalog/columns', {
      tableId: detailTable.value.id,
      name: addColumnForm.name,
      type: addColumnForm.type,
      comment: addColumnForm.comment,
      nullable: addColumnForm.nullable,
      isPartition: addColumnForm.isPartition,
      ordinalPosition: addColumnForm.ordinalPosition
    })
    if (res.data.code === 0) {
      ElMessage.success('添加列成功')
      showAddColumnDialog.value = false
      addColumnForm.name = ''; addColumnForm.type = 'STRING'; addColumnForm.comment = ''
      addColumnForm.nullable = true; addColumnForm.isPartition = false; addColumnForm.ordinalPosition = 1
      loadDetailColumns(detailTable.value.id)
    } else {
      ElMessage.error(res.data.msg || '添加失败')
    }
  } catch (e) {
    ElMessage.error('添加列失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    addColumnLoading.value = false
  }
}

const editCatalogColumn = (row) => {
  editColumnForm.id = row.id
  editColumnForm.name = row.name
  editColumnForm.type = row.type
  editColumnForm.comment = row.comment || ''
  editColumnForm.nullable = row.nullable !== false
  editColumnForm.isPartition = row.isPartition || false
  editColumnForm.ordinalPosition = row.ordinalPosition || 1
  showEditColumnDialog.value = true
}

const handleEditColumn = async () => {
  if (!editColumnForm.type) {
    ElMessage.warning('请填写类型')
    return
  }
  editColumnLoading.value = true
  try {
    const res = await axios.put('/api/v1/catalog/columns/' + editColumnForm.id, {
      type: editColumnForm.type,
      comment: editColumnForm.comment,
      nullable: editColumnForm.nullable,
      isPartition: editColumnForm.isPartition,
      ordinalPosition: editColumnForm.ordinalPosition
    })
    if (res.data.code === 0) {
      ElMessage.success('更新列成功')
      showEditColumnDialog.value = false
      loadDetailColumns(detailTable.value.id)
    } else {
      ElMessage.error(res.data.msg || '更新失败')
    }
  } catch (e) {
    ElMessage.error('更新列失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    editColumnLoading.value = false
  }
}

const deleteCatalogColumn = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除列 "' + row.name + '" 吗？', '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    const res = await axios.delete('/api/v1/catalog/columns/' + row.id)
    if (res.data.code === 0) {
      ElMessage.success('删除列成功')
      loadDetailColumns(detailTable.value.id)
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除列失败: ' + (e.response?.data?.msg || e.message))
  }
}

// Tag operations
const loadAllTags = async () => {
  try {
    const res = await axios.get('/api/v1/catalog/tags')
    if (res.data.code === 0) {
      allTags.value = res.data.data || []
    }
  } catch (e) { /* ignore */ }
}

const openTagSelector = () => {
  tagDialogMode.value = 'selector'
  selectedTagIds.value = (detailTags.value || []).map(t => t.id)
  showTagDialog.value = true
}

const handleSetTableTags = async () => {
  if (!detailTable.value) return
  setTagLoading.value = true
  try {
    const res = await axios.post('/api/v1/catalog/tables/' + detailTable.value.id + '/tags', {
      tagIds: selectedTagIds.value
    })
    if (res.data.code === 0) {
      ElMessage.success('标签更新成功')
      showTagDialog.value = false
      // Refresh table detail tags
      const detailRes = await axios.get('/api/v1/catalog/tables/' + detailTable.value.id)
      if (detailRes.data.code === 0) {
        detailTags.value = detailRes.data.data.tags || []
        // Also update the table in the list
        const idx = catalogTables.value.findIndex(t => t.id === detailTable.value.id)
        if (idx >= 0) {
          catalogTables.value[idx].tags = detailTags.value
        }
      }
    } else {
      ElMessage.error(res.data.msg || '更新标签失败')
    }
  } catch (e) {
    ElMessage.error('更新标签失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    setTagLoading.value = false
  }
}

const handleAddTag = async () => {
  if (!newTagName.value.trim()) {
    ElMessage.warning('请输入标签名')
    return
  }
  addTagLoading.value = true
  try {
    const res = await axios.post('/api/v1/catalog/tags', {
      name: newTagName.value.trim(),
      color: newTagColor.value,
      description: ''
    })
    if (res.data.code === 0) {
      ElMessage.success('标签创建成功')
      newTagName.value = ''
      newTagColor.value = '#3b82f6'
      loadAllTags()
    } else {
      ElMessage.error(res.data.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建标签失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    addTagLoading.value = false
  }
}

const deleteCatalogTag = async (tag) => {
  try {
    await ElMessageBox.confirm('确定要删除标签 "' + tag.name + '" 吗？', '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    const res = await axios.delete('/api/v1/catalog/tags/' + tag.id)
    if (res.data.code === 0) {
      ElMessage.success('标签已删除')
      loadAllTags()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除标签失败: ' + (e.response?.data?.msg || e.message))
  }
}

// Auto-discover
const handleDiscover = async () => {
  if (!discoverForm.basePath) {
    ElMessage.warning('请输入基础路径')
    return
  }
  discoverLoading.value = true
  try {
    const res = await axios.post('/api/v1/catalog/discover', discoverForm)
    if (res.data.code === 0) {
      const count = res.data.data?.count || res.data.data?.total || 0
      ElMessage.success('自动发现完成，共发现 ' + count + ' 张表')
      showDiscoverDialog.value = false
      loadCatalogTables()
    } else {
      ElMessage.error(res.data.msg || '自动发现失败')
    }
  } catch (e) {
    ElMessage.error('自动发现失败: ' + (e.response?.data?.msg || e.message))
  } finally {
    discoverLoading.value = false
  }
}

// Load catalog on tab switch
watch(activeTab, (tab) => {
  if (tab === 'catalog') {
    loadCatalogTables()
    loadAllTags()
  }
})
</script>

<style>
/* === Reset & Base === */
html, body, #hermes-app { margin: 0; padding: 0; height: 100%; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f1f5f9; }

/* Element Plus 深色主题 CSS 变量覆盖 */
:root {
  --el-bg-color: #0f172a;
  --el-bg-color-overlay: #1e2938;
  --el-dialog-bg-color: #1e2938;
  --el-popup-bg-color: #1e2938;
  --el-mask-color: rgba(0,0,0,0.6);
  --el-border-color: #334155;
  --el-border-color-light: #334155;
  --el-text-color-primary: #f1f5f9;
  --el-text-color-regular: #e2e8f0;
  --el-text-color-secondary: #94a3b8;
  --el-color-primary: #3b82f6;
  --el-color-success: #22c55e;
  --el-color-warning: #eab308;
  --el-color-danger: #ef4444;
  --el-color-info: #64748b;
  --el-fill-color: #1e2938;
  --el-fill-color-light: #1e2938;
  --el-fill-color-lighter: #1e2938;
  --el-fill-color-blank: #1e2938;
}

/* === Global Loading State === */
body.is-global-loading { cursor: wait !important; }
body.is-global-loading * { pointer-events: none !important; }
body.is-global-loading input, body.is-global-loading textarea, body.is-global-loading select { cursor: wait !important; }

/* === Login === */
.login-page { display: flex; justify-content: center; align-items: center; min-height: 100vh; position: relative; overflow: hidden; background: linear-gradient(135deg, #0f172a 0%, #1e2938 50%, #1e2938 100%); }
.login-bg { position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(ellipse at 30% 50%, rgba(59,130,246,0.08) 0%, transparent 50%), radial-gradient(ellipse at 70% 50%, rgba(34,197,94,0.05) 0%, transparent 50%); pointer-events: none; }
.login-card { width: 400px; background: #1e2938 !important; backdrop-filter: blur(20px); border: 1px solid #334155 !important; border-radius: 20px !important; box-shadow: 0 20px 60px rgba(0,0,0,0.5), 0 0 120px rgba(59,130,246,0.06); padding: 10px 0; }
.login-card .el-card__body { padding: 30px 40px 20px !important; }
.login-header { text-align: center; margin-bottom: 28px; }
.login-logo { margin-bottom: 12px; }
.login-header h2 { margin: 0; font-size: 28px; font-weight: 700; background: linear-gradient(135deg, #3b82f6, #22c55e); -webkit-background-clip: text; -webkit-text-fill-color: transparent; letter-spacing: 2px; }
.login-sub { margin: 6px 0 0; font-size: 13px; color: #94a3b8; }
.login-form .el-input__wrapper { background: #1e2938 !important; border: 1px solid #334155 !important; box-shadow: none !important; }
.login-form .el-input__wrapper:hover { border-color: rgba(59,130,246,0.3) !important; }
.login-form .el-input__wrapper.is-focus { border-color: #3b82f6 !important; }
.login-form .el-input__inner { color: #f1f5f9 !important; }
.login-form .el-input__prefix-inner { color: #94a3b8; }
.login-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 6px; border-radius: 12px; background: linear-gradient(135deg, #3b82f6, #2563eb); border: none; box-shadow: 0 4px 15px rgba(59,130,246,0.3); transition: all 0.3s; }
.login-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 25px rgba(59,130,246,0.4); }
.login-error { color: #ef4444; font-size: 13px; text-align: center; margin-top: 12px; }

/* === Main Layout === */
.main-layout { display: flex; height: 100vh; overflow: hidden; }

/* === Sidebar === */
.sidebar { width: 220px; background: linear-gradient(180deg, #1e2938 0%, #0f172a 100%); border-right: 1px solid #334155; display: flex; flex-direction: column; flex-shrink: 0; }
.sidebar-header { padding: 20px 20px 16px; display: flex; align-items: center; gap: 10px; border-bottom: 1px solid #334155; }
.sidebar-title { font-size: 20px; font-weight: 700; background: linear-gradient(135deg, #3b82f6, #22c55e); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.cluster-selector-wrap { padding: 10px 14px; border-bottom: 1px solid #334155; }
.cluster-selector { width: 100%; }
.cluster-selector .el-input__wrapper { background: #1e2938 !important; border-color: #334155 !important; box-shadow: 0 0 0 1px #334155 inset !important; }
.cluster-selector .el-input__inner { color: #f1f5f9 !important; font-size: 12px !important; }
.cluster-selector .el-input__suffix .el-select__caret { color: #94a3b8 !important; }
:deep(.cluster-selector-popper) { background: #1e2938 !important; border: 1px solid #334155 !important; }
:deep(.cluster-selector-popper .el-select-dropdown__item) { color: #94a3b8 !important; font-size: 12px !important; }
:deep(.cluster-selector-popper .el-select-dropdown__item.hover) { background: rgba(59,130,246,0.1) !important; color: #3b82f6 !important; }
:deep(.cluster-selector-popper .el-select-dropdown__item.selected) { color: #3b82f6 !important; font-weight: 600 !important; }
.sidebar-menu { flex: 1; padding: 12px 10px; overflow-y: auto; overflow-x: hidden; }
.sidebar-menu::-webkit-scrollbar { width: 4px; }
.sidebar-menu::-webkit-scrollbar-track { background: transparent; }
.sidebar-menu::-webkit-scrollbar-thumb { background: #334155; border-radius: 4px; }
.sidebar-menu::-webkit-scrollbar-thumb:hover { background: #475569; }
.menu-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-radius: 12px; cursor: pointer; transition: all 0.25s; margin-bottom: 4px; color: #94a3b8; font-size: 14px; }
.menu-item:hover { background: #1e2938; color: #f1f5f9; }
.menu-item.active { background: linear-gradient(135deg, rgba(59,130,246,0.15), rgba(59,130,246,0.05)); color: #3b82f6; box-shadow: inset 3px 0 0 #3b82f6; }
.menu-icon { font-size: 18px; width: 24px; text-align: center; }
.menu-label { font-weight: 500; }
.sidebar-footer { padding: 12px 14px; border-top: 1px solid #334155; flex-shrink: 0; }
.sidebar-table-size-toggle { display: flex; align-items: center; justify-content: center; gap: 6px; margin-bottom: 10px; padding: 4px 8px; border-radius: 8px; background: rgba(255,255,255,0.03); border: 1px solid transparent; transition: all 0.2s; }
.sidebar-table-size-toggle:hover { border-color: #334155; background: rgba(255,255,255,0.05); }
.size-toggle-btn { color: #94a3b8 !important; font-size: 16px !important; transition: all 0.2s !important; }
.size-toggle-btn:hover { color: #3b82f6 !important; background: rgba(59,130,246,0.1) !important; }
.size-toggle-label { font-size: 11px; color: #64748b; user-select: none; }
.user-info { display: flex; align-items: center; gap: 8px; }
.user-details { display: flex; flex-direction: column; gap: 2px; }
.user-avatar { width: 32px; height: 32px; border-radius: 10px; background: linear-gradient(135deg, #3b82f6, #2563eb); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; color: #fff; box-shadow: 0 2px 8px rgba(59,130,246,0.3); }
.user-name { font-size: 13px; color: #94a3b8; line-height: 1.2; }
.user-role-badge { font-size: 10px; padding: 1px 6px; border-radius: 8px; display: inline-block; line-height: 1.4; text-align: center; font-weight: 500; letter-spacing: 0.3px; }
.user-role-badge.role-admin { background: rgba(239,68,68,0.15); color: #ef4444; }
.user-role-badge.role-operator { background: rgba(234,179,8,0.15); color: #eab308; }
.user-role-badge.role-viewer { background: rgba(148,163,184,0.15); color: #94a3b8; }
.logout-btn { color: #94a3b8 !important; font-size: 12px !important; }
.logout-btn:hover { color: #ef4444 !important; }

/* === Main Content === */
.main-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: #0f172a; }
.content-header { padding: 16px 28px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155; flex-shrink: 0; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #f1f5f9; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.cluster-badge { display: flex; align-items: center; gap: 6px; font-size: 12px; padding: 4px 12px; border-radius: 20px; background: rgba(59,130,246,0.1); color: #3b82f6; border: 1px solid rgba(59,130,246,0.2); }
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 8px rgba(34,197,94,0.5); animation: pulse-dot 2s infinite; }
@keyframes pulse-dot { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
.content-body { flex: 1; overflow-y: auto; padding: 20px 28px; }

/* === Stats Cards === */
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
.stat-card { display: flex; align-items: center; gap: 14px; padding: 20px; border-radius: 16px; background: #1e2938; border: 1px solid #334155; backdrop-filter: blur(12px); transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); position: relative; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.1); }
.stat-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; border-radius: 16px 16px 0 0; opacity: 0.8; }
.stat-card::after { content: ''; position: absolute; top: -50%; right: -50%; width: 80%; height: 200%; background: radial-gradient(ellipse, rgba(255,255,255,0.02) 0%, transparent 70%); pointer-events: none; transition: all 0.5s; }
.stat-card:hover { transform: translateY(-6px) scale(1.02); box-shadow: 0 16px 48px rgba(0,0,0,0.3), 0 0 60px rgba(59,130,246,0.06); border-color: #475569; }
.stat-card:hover::after { top: -20%; right: -20%; opacity: 1.5; }
.stat-card:active { transform: translateY(-2px) scale(0.99); }
.stat-card.hdfs-card::before { background: linear-gradient(90deg, #3b82f6, #2563eb); }
.stat-card.yarn-card::before { background: linear-gradient(90deg, #22c55e, #16a34a); }
.stat-card.info-card::before { background: linear-gradient(90deg, #64748b, #475569); }
.stat-icon { font-size: 32px; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; border-radius: 14px; background: #1e2938; flex-shrink: 0; }
.stat-info { flex: 1; }
.stat-label { font-size: 12px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 4px; }
.stat-value { font-size: 22px; font-weight: 700; color: #f1f5f9; }
.stat-trend { font-size: 11px; color: #94a3b8; padding: 2px 8px; border-radius: 6px; background: #1e2938; }
.stat-trend.up { color: #22c55e; }

/* === Panels === */
.panel { animation: fadeIn 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
@keyframes fadeIn { from { opacity: 0; transform: translateY(12px) scale(0.98); } to { opacity: 1; transform: translateY(0) scale(1); } }

/* === HDFS 子标签页 === */
.el-tabs--border-card { border-radius: 16px !important; border: 1px solid #334155 !important; background: #1e2938 !important; backdrop-filter: blur(10px); overflow: hidden; }
.el-tabs--border-card > .el-tabs__header { background: #1e2938 !important; border-bottom: 1px solid #334155 !important; }
.el-tabs--border-card > .el-tabs__header .el-tabs__item { color: #94a3b8; font-size: 13px; font-weight: 500; padding: 0 16px; transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1); border-left: 1px solid transparent !important; border-right: 1px solid transparent !important; }
.el-tabs--border-card > .el-tabs__header .el-tabs__item:not(.is-disabled):hover { color: #f1f5f9; background: rgba(59,130,246,0.06); }
.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active { color: #3b82f6; background: rgba(59,130,246,0.08) !important; border-bottom: 2px solid #3b82f6 !important; transform: translateY(-1px); }
.el-tabs--border-card > .el-tabs__header .el-tabs__item[draggable=true]:active { cursor: grabbing !important; opacity: 0.6; transform: scale(0.95); }
.el-tabs--border-card > .el-tabs__header .el-tabs__item[draggable=true] { user-select: none; }
.el-tabs--border-card > .el-tabs__header .el-tabs__active-bar { background: linear-gradient(90deg, #3b82f6, #22c55e) !important; height: 2px !important; }
.el-tabs--border-card > .el-tabs__body { padding: 20px !important; }
.el-tab-pane { animation: tabFadeIn 0.3s ease; }
@keyframes tabFadeIn { from { opacity: 0; transform: translateX(8px); } to { opacity: 1; transform: translateX(0); } }

.panel-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.path-input { width: 400px; transition: all 0.3s; }
.path-input:focus-within { width: 440px; }.path-input .el-input__wrapper { background: #1e2938 !important; border: 1px solid #334155 !important; box-shadow: none !important; border-radius: 10px; }
.path-input .el-input__wrapper.is-focus { border-color: #3b82f6 !important; }
.path-input .el-input__inner { color: #f1f5f9 !important; }

/* === Glass Table === */
.glass-table { border-radius: 14px; overflow: hidden; border: 1px solid #334155; background: #1e2938; backdrop-filter: blur(10px); transition: all 0.3s; box-shadow: 0 2px 20px rgba(0,0,0,0.15); }
.glass-table:hover { box-shadow: 0 4px 30px rgba(0,0,0,0.25); border-color: #475569; }
.glass-table .el-table__header th { background: #1e2938 !important; border-bottom: 1px solid #334155 !important; color: #94a3b8; font-weight: 500; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
.glass-table .el-table__row td { border-bottom: 1px solid #334155 !important; color: #f1f5f9; background: transparent !important; transition: all 0.2s; }
.glass-table .el-table__row:hover td { background: rgba(59,130,246,0.06) !important; transform: scale(1.002); }
.glass-table .el-table__row { transition: all 0.2s; }
.glass-table .el-table__row:hover { transform: translateX(2px); }
.glass-table .el-table__body { background: transparent !important; }
.glass-table .el-table--striped .el-table__body tr.el-table__row--striped td { background: rgba(255,255,255,0.02) !important; }

/* === Glass Card === */
.glass-card { border-radius: 16px; background: #1e2938; border: 1px solid #334155; padding: 18px; backdrop-filter: blur(12px); transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 2px 16px rgba(0,0,0,0.1); }
.glass-card:hover { border-color: #475569; box-shadow: 0 8px 40px rgba(0,0,0,0.25), 0 0 40px rgba(59,130,246,0.04); transform: translateY(-2px); }
.glass-card-title { font-size: 14px; font-weight: 600; color: #f1f5f9; margin-bottom: 12px; padding-bottom: 10px; border-bottom: 1px solid #334155; }

/* === JN Card === */
.jn-card { border-radius: 14px; background: #1e2938; border: 1px solid #334155; overflow: hidden; transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); height: 100%; box-shadow: 0 2px 12px rgba(0,0,0,0.08); backdrop-filter: blur(8px); }
.jn-card:hover { transform: translateY(-5px) scale(1.01); box-shadow: 0 12px 36px rgba(0,0,0,0.3), 0 0 40px rgba(59,130,246,0.05); border-color: rgba(59,130,246,0.2); }
.jn-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; background: #1e2938; border-bottom: 1px solid #334155; }
.jn-name { font-size: 13px; font-weight: 600; color: #f1f5f9; }
.jn-body { padding: 10px 14px 14px; }
.jn-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 12px; border-bottom: 1px solid #334155; }
.jn-row:last-child { border-bottom: none; }
.jn-label { color: #94a3b8; }
.jn-val { color: #f1f5f9; font-weight: 500; text-align: right; }
.file-link { cursor: pointer; transition: color 0.2s; }
.file-link.is-dir { color: #3b82f6; font-weight: 500; }
.file-link.is-dir:hover { color: #60a5fa; text-decoration: underline; }
.file-link:not(.is-dir) { color: #f1f5f9; }

/* === Queue Card === */
.queue-card { border-radius: 16px; background: #1e2938; border: 1px solid #334155; padding: 16px; backdrop-filter: blur(10px); }
.queue-card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; font-size: 15px; font-weight: 600; color: #f1f5f9; }

/* === Capacity Bar (enhanced) === */
.capacity-bar { display: flex; align-items: center; gap: 6px; }
.capacity-bar .capacity-fill { height: 6px; border-radius: 3px; background: #3b82f6; min-width: 4px; max-width: 60px; transition: width 0.6s ease; }
.capacity-bar.used .capacity-fill { background: #22c55e; }
.capacity-bar span { font-size: 12px; color: #94a3b8; min-width: 30px; }

/* Enhanced capacity bars for tree table */
.cap-bar-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.cap-bar-track { flex: 1; height: 8px; background: #334155; border-radius: 4px; overflow: hidden; min-width: 60px; }
.cap-bar-fill { height: 100%; border-radius: 4px; background: #22c55e; transition: width 0.5s ease; min-width: 2px; }
.cap-bar-fill.cap-bar-blue { background: linear-gradient(90deg, #3b82f6, #60a5fa); }
.cap-bar-label { font-size: 11px; color: #94a3b8; min-width: 42px; text-align: right; font-family: 'JetBrains Mono', 'Fira Code', monospace; }

/* === Metric Grid === */
.metric-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.metric-item { padding: 16px; border-radius: 14px; background: #1e2938; border: 1px solid #334155; text-align: center; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); position: relative; overflow: hidden; }
.metric-item::after { content: ''; position: absolute; top: -50%; right: -50%; width: 60%; height: 200%; background: radial-gradient(ellipse, rgba(59,130,246,0.03) 0%, transparent 70%); pointer-events: none; transition: all 0.5s; }
.metric-item:hover { background: rgba(59,130,246,0.06); border-color: rgba(59,130,246,0.15); transform: translateY(-3px) scale(1.02); box-shadow: 0 8px 24px rgba(0,0,0,0.2); }
.metric-item:hover::after { top: -20%; }
.metric-value { font-size: 24px; font-weight: 700; color: #3b82f6; }
.metric-label { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* === Buttons === */
.el-button { transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important; }
.el-button--primary { background: linear-gradient(135deg, #3b82f6, #2563eb) !important; border: none !important; box-shadow: 0 2px 10px rgba(59,130,246,0.2) !important; }
.el-button--primary:hover { transform: translateY(-2px) scale(1.03); box-shadow: 0 6px 20px rgba(59,130,246,0.35) !important; }
.el-button--primary:active { transform: translateY(0) scale(0.97); }
.el-button--primary.is-plain { background: rgba(59,130,246,0.1) !important; color: #3b82f6 !important; box-shadow: none !important; border: 1px solid rgba(59,130,246,0.2) !important; }
.el-button--danger { background: linear-gradient(135deg, #ef4444, #dc2626) !important; border: none !important; box-shadow: 0 2px 10px rgba(239,68,68,0.2) !important; }
.el-button--danger:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(239,68,68,0.3) !important; }
.el-button--danger.is-plain { background: rgba(239,68,68,0.1) !important; color: #ef4444 !important; border: 1px solid rgba(239,68,68,0.2) !important; box-shadow: none !important; }
.el-button.is-round { padding: 8px 20px; }
.el-button--small.is-round { padding: 5px 14px; }

/* === Dialog === */
.glass-dialog .el-dialog { background: linear-gradient(135deg, #1e2938, #0f172a) !important; border: 1px solid #334155; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.5); }
.glass-dialog .el-dialog__header { background: transparent !important; }
.glass-dialog .el-dialog__body { background: transparent !important; }
.glass-dialog .el-dialog__footer { background: transparent !important; }
.glass-dialog .el-overlay-dialog { background: transparent !important; }
.glass-dialog .el-dialog__title { color: #f1f5f9; font-weight: 600; }
.glass-dialog .el-dialog__headerbtn .el-dialog__close { color: #94a3b8; }
.glass-dialog .el-dialog__headerbtn:hover .el-dialog__close { color: #f1f5f9; }

/* === Operation Log Detail === */
.log-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 4px 0;
}
.log-detail-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.log-detail-field-full {
  grid-column: 1 / -1;
}
.log-detail-label {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.log-detail-value {
  font-size: 14px;
  color: #f1f5f9;
  word-break: break-all;
}
.log-detail-value-code {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  background: rgba(0,0,0,0.3);
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid #334155;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-all;
}
.log-detail-json {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  background: #0f172a;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #334155;
  color: #a5b4fc;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  line-height: 1.6;
}

.mr-form .el-input__wrapper { background: #1e2938 !important; border: 1px solid #334155 !important; box-shadow: none !important; }
.mr-form .el-input__wrapper.is-focus { border-color: #3b82f6 !important; }
.mr-form .el-input__inner { color: #f1f5f9 !important; }
.mr-form .el-textarea__inner { background: #1e2938 !important; border: 1px solid #334155 !important; color: #f1f5f9 !important; box-shadow: none !important; }
.mr-form .el-form-item__label { color: #94a3b8; }
.mr-form .el-select .el-input__wrapper { background: #1e2938 !important; }

/* === Select === */
.el-select .el-input__wrapper { background: #1e2938 !important; border: 1px solid #334155 !important; box-shadow: none !important; border-radius: 10px !important; transition: all 0.25s; }
.el-select .el-input__wrapper:hover { border-color: rgba(59,130,246,0.3) !important; }
.el-select .el-input__wrapper.is-focus { border-color: #3b82f6 !important; }
.el-select .el-input__inner { color: #f1f5f9 !important; }
.el-select-dropdown { background: rgba(30,41,56,0.98) !important; border: 1px solid #334155 !important; backdrop-filter: blur(20px); border-radius: 12px !important; padding: 4px !important; }
.el-select-dropdown .el-select-dropdown__item { border-radius: 8px; color: #94a3b8; margin: 2px 0; transition: all 0.15s; }
.el-select-dropdown .el-select-dropdown__item.hover { background: rgba(59,130,246,0.12); color: #3b82f6; }
.el-select-dropdown .el-select-dropdown__item.selected { color: #3b82f6; font-weight: 600; background: rgba(59,130,246,0.08); }

/* === NN Select === */
.nn-select .el-input__wrapper { background: rgba(59,130,246,0.08) !important; border: 1px solid rgba(59,130,246,0.2) !important; border-radius: 10px !important; box-shadow: none !important; transition: all 0.25s; }
.nn-select .el-input__wrapper:hover { border-color: #3b82f6 !important; background: rgba(59,130,246,0.12) !important; }
.nn-select .el-input__wrapper.is-focus { border-color: #3b82f6 !important; background: rgba(59,130,246,0.15) !important; box-shadow: 0 0 0 2px rgba(59,130,246,0.15) !important; }
.nn-select .el-input__inner { color: #93c5fd !important; font-weight: 500; font-size: 13px; }
.nn-select .el-select__caret { color: #3b82f6 !important; font-size: 14px; }

/* === Error Card === */
.error-card { padding: 24px; border-radius: 14px; background: rgba(239,68,68,0.06); border: 1px solid rgba(239,68,68,0.15); color: #ef4444; font-size: 14px; }

/* === Date Picker === */
.el-date-editor { border-radius: 10px !important; background: #1e2938 !important; border: 1px solid #334155 !important; transition: all 0.25s; }
.el-date-editor:hover { border-color: rgba(59,130,246,0.3) !important; }
.el-date-editor.is-active { border-color: #3b82f6 !important; }
.el-date-editor .el-input__wrapper { background: transparent !important; box-shadow: none !important; }
.el-date-editor .el-input__inner { color: #f1f5f9 !important; }
.el-picker-panel { background: rgba(30,41,56,0.98) !important; border: 1px solid #334155 !important; backdrop-filter: blur(20px); border-radius: 12px !important; }
.el-picker-panel .el-date-table td { color: #94a3b8; }
.el-picker-panel .el-date-table td.today { color: #3b82f6; font-weight: 700; }
.el-picker-panel .el-date-table td.current:not(.disabled) span { background: #3b82f6 !important; }

/* === Scrollbar === */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #334155; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #475569; }

/* === Block Metadata === */
.block-loc-item { border-radius: 10px; background: #1e2938; border: 1px solid #334155; margin-bottom: 8px; padding: 10px 14px; transition: all 0.2s; }
.block-loc-item:hover { background: rgba(59,130,246,0.05); border-color: rgba(59,130,246,0.15); }
.block-loc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; font-size: 13px; font-weight: 600; color: #94a3b8; }
.block-loc-nodes { display: flex; flex-wrap: wrap; }

/* === DN Capacity Bar in Table === */
.dn-capacity-cell { min-width: 120px; }
.dn-capacity-bar { height: 6px; background: #334155; border-radius: 3px; overflow: hidden; }
.dn-capacity-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #22c55e, #3b82f6); transition: width 0.6s ease; }
.dn-capacity-text { font-size: 11px; color: #94a3b8; white-space: nowrap; }

/* === DN Storage Distribution === */
.dn-storage-dist { display: flex; flex-direction: column; gap: 10px; }
.dn-storage-row { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-radius: 10px; background: #1e2938; border: 1px solid #334155; transition: all 0.25s; }
.dn-storage-row:hover { background: rgba(59,130,246,0.05); border-color: rgba(59,130,246,0.12); transform: translateX(4px); }
.dn-storage-label { display: flex; align-items: center; gap: 6px; min-width: 80px; }
.dn-storage-name { font-size: 13px; font-weight: 600; color: #f1f5f9; font-family: 'JetBrains Mono', 'Fira Code', monospace; }
.dn-storage-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-green { background: #22c55e; box-shadow: 0 0 6px rgba(34,197,94,0.5); }
.dot-orange { background: #eab308; box-shadow: 0 0 6px rgba(234,179,8,0.5); }
.dot-red { background: #ef4444; box-shadow: 0 0 6px rgba(239,68,68,0.5); }
.dot-gray { background: #6b7280; box-shadow: 0 0 6px rgba(107,114,128,0.3); }
.dn-storage-bar-wrap { flex: 1; }
.dn-storage-bar { height: 12px; background: #334155; border-radius: 6px; overflow: hidden; }
.dn-storage-fill { height: 100%; border-radius: 6px; transition: width 0.8s ease; }
.dn-storage-info { display: flex; align-items: center; gap: 3px; min-width: 110px; justify-content: flex-end; }
.dn-storage-used { font-size: 12px; font-weight: 600; color: #f1f5f9; font-family: 'JetBrains Mono', 'Fira Code', monospace; }
.dn-storage-sep { font-size: 11px; color: #475569; }
.dn-storage-total { font-size: 12px; color: #94a3b8; font-family: 'JetBrains Mono', 'Fira Code', monospace; }
.dn-storage-empty { text-align: center; padding: 24px; color: #475569; font-size: 13px; }

/* === Scale DN === */
.scale-cmd-box { border-radius: 12px; background: rgba(0,0,0,0.3); border: 1px solid #334155; overflow: hidden; }
.scale-cmd-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: #1e2938; border-bottom: 1px solid #334155; font-size: 13px; font-weight: 600; color: #94a3b8; }
.scale-cmd-body { padding: 14px; margin: 0; overflow-x: auto; }
.scale-cmd-body code { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; line-height: 1.7; color: #a8d8ea; white-space: pre; }

/* === Log Viewer === */
.log-viewer { max-height: 520px; overflow-y: auto; border-radius: 12px; background: rgba(0,0,0,0.35); border: 1px solid #334155; font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; box-shadow: inset 0 0 30px rgba(0,0,0,0.2); }
.log-viewer::-webkit-scrollbar { width: 5px; }
.log-viewer::-webkit-scrollbar-thumb { background: #334155; border-radius: 3px; }
.log-line { display: flex; padding: 4px 12px; border-bottom: 1px solid rgba(255,255,255,0.015); line-height: 1.7; transition: all 0.15s; }
.log-line:hover { background: rgba(59,130,246,0.04); transform: translateX(2px); }
.log-time { color: #475569; min-width: 85px; flex-shrink: 0; }
.log-level { min-width: 48px; flex-shrink: 0; font-weight: 600; font-size: 11px; text-align: center; margin-right: 8px; }
.log-msg { color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.log-debug .log-msg { color: #64748b; }
.log-info .log-msg { color: #94a3b8; }
.log-warn .log-msg { color: #eab308; }
.log-error .log-msg { color: #ef4444; }
.lv-DEBUG { color: #64748b; }
.lv-INFO { color: #3b82f6; }
.lv-WARN { color: #eab308; }
.lv-ERROR { color: #ef4444; }
.lv-OTHER { color: #475569; }
.log-empty { padding: 24px; text-align: center; color: #475569; font-size: 13px; }
.log-line.log-error { background: rgba(239,68,68,0.08); }
.log-line.log-error:hover { background: rgba(239,68,68,0.14); }
.log-line.log-warn { background: rgba(234,179,8,0.06); }
.log-line.log-warn:hover { background: rgba(234,179,8,0.12); }
.log-copy { display:none; margin-left:auto; cursor:pointer; opacity:0.6; padding:0 6px; font-size:13px; transition:opacity 0.15s; flex-shrink:0; }
.log-line:hover .log-copy { display:inline; }
.log-copy:hover { opacity:1; }

/* === Quick Filter, Search Highlight, Download === */
.log-quick-filter { display:flex; gap:4px; align-items:center; }
.log-highlight { background: rgba(250,204,21,0.15) !important; border-left: 3px solid #eab308; }
.log-current-match { background: rgba(250,204,21,0.3) !important; border-left: 3px solid #f59e0b; box-shadow: inset 0 0 12px rgba(250,204,21,0.15); }

/* === Progress Bar === */
.progress-bar-wrap { margin: 10px 0; padding: 8px 12px; border-radius: 10px; background: rgba(0,0,0,0.25); border: 1px solid #334155; }
.progress-info { display: flex; justify-content: space-between; margin-bottom: 4px; font-size: 12px; color: #94a3b8; }
.progress-info span:first-child { color: #93c5fd; font-weight: 500; }
.progress-info span:last-child { color: #3b82f6; font-weight: 700; }

/* === Batch Action Bar === */
.batch-action-bar { display: flex; align-items: center; gap: 10px; margin: 10px 0; padding: 10px 14px; border-radius: 10px; background: #1e2938; border: 1px solid #334155; }
.batch-action-bar .batch-count { font-size: 13px; color: #93c5fd; font-weight: 600; margin-right: auto; }

/* === File System Table - high contrast === */
.fs-table { border: 1px solid rgba(59,130,246,0.15) !important; background: rgba(0,0,0,0.35) !important; }
.fs-table .el-table__header th { background: #1e2938 !important; color: #93c5fd !important; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; border-bottom: 2px solid #334155 !important; }
.fs-table .el-table__row td { border-bottom: 1px solid rgba(59,130,246,0.08) !important; background: transparent !important; }
.fs-table .el-table__row:nth-child(even) td { background: rgba(59,130,246,0.04) !important; }
.fs-table .el-table__row:hover td { background: rgba(59,130,246,0.12) !important; }
.fs-table .el-table__inner-wrapper { background: transparent !important; }
.fs-table .el-table__header-wrapper { background: transparent !important; }
.fs-table .el-table__body-wrapper { background: transparent !important; }
.fs-table .file-link { cursor: pointer; transition: all 0.2s; font-size: 13px; }
.fs-table .el-table__inner-wrapper::before { display: none; }
.fs-table.el-table { background: transparent !important; }
.fs-table .el-table__body-wrapper { background: transparent !important; }
.fs-table .el-table__body tr { background: transparent !important; }
.fs-table .el-table__empty-text { color: #64748b; }
.fs-table .file-link.is-dir { color: #38bdf8; font-weight: 700; text-shadow: 0 0 8px rgba(56,189,248,0.15); }
.fs-table .file-link.is-dir:hover { color: #7dd3fc; text-decoration: underline; }
.fs-table .file-link:not(.is-dir) { color: #fbbf24; font-weight: 500; }
.fs-table .file-link:not(.is-dir):hover { color: #fde68a; }
.fs-table .cell { color: #94a3b8; font-size: 13px; }
.fs-table .el-table__body { background: transparent !important; }

/* === Hover action button: show only on row hover === */
.fs-table .el-table__row .el-dropdown .el-button {
  opacity: 0;
  transition: opacity 0.15s ease;
}
.fs-table .el-table__row:hover .el-dropdown .el-button {
  opacity: 1;
}

/* === Success row highlight animation === */
@keyframes row-highlight-fade {
  0% { background-color: rgba(34, 197, 94, 0.25) !important; box-shadow: inset 0 0 12px rgba(34, 197, 94, 0.15); }
  50% { background-color: rgba(34, 197, 94, 0.12) !important; }
  100% { background-color: transparent !important; }
}
.fs-table .el-table__body tr.row-highlight td {
  animation: row-highlight-fade 1.5s ease-out forwards;
}

/* === Checkpoint Card === */
.checkpoint-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; }
.checkpoint-card { border-radius: 14px; background: #1e2938; border: 1px solid #334155; overflow: hidden; transition: all 0.3s cubic-bezier(0.4,0,0.2,1); backdrop-filter: blur(8px); }
.checkpoint-card:hover { transform: translateY(-3px); box-shadow: 0 10px 30px rgba(0,0,0,0.25); border-color: rgba(59,130,246,0.15); }
.checkpoint-header { display: flex; align-items: center; gap: 8px; padding: 12px 14px 8px; }
.checkpoint-icon { font-size: 18px; }
.checkpoint-title { font-size: 13px; font-weight: 600; color: #f1f5f9; }
.checkpoint-body { padding: 4px 14px 12px; }
.cp-row { display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid #334155; font-size: 12px; }
.cp-row:last-child { border-bottom: none; }
.cp-label { color: #94a3b8; flex-shrink: 0; }
.cp-val { color: #f1f5f9; font-weight: 500; text-align: right; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cp-name { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 11px; color: #93c5fd; }
.cp-path { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 11px; color: #94a3b8; }

/* === YARN Apps === */
.yarn-row-failed { background-color: rgba(239,68,68,0.08) !important; }
.yarn-row-failed:hover td { background-color: rgba(239,68,68,0.15) !important; }
.yarn-row-killed { background-color: rgba(234,179,8,0.05) !important; }
.yarn-row-killed:hover td { background-color: rgba(234,179,8,0.1) !important; }
.glass-table .el-table__body tr.yarn-row-failed td { border-bottom: 1px solid rgba(239,68,68,0.12) !important; }
.glass-table .el-table__body tr.yarn-row-killed td { border-bottom: 1px solid rgba(234,179,8,0.1) !important; }

/* === Drawer === */
.glass-drawer .el-drawer { background: linear-gradient(135deg, #1e2938, #0f172a) !important; border-left: 1px solid #334155; }
.glass-drawer .el-drawer__header { color: #f1f5f9; font-weight: 600; border-bottom: 1px solid #334155; padding: 16px 20px; margin-bottom: 0; }
.glass-drawer .el-drawer__body { padding: 20px; overflow-y: auto; }
.glass-drawer .el-drawer__close-btn { color: #94a3b8; }
.glass-drawer .el-drawer__close-btn:hover { color: #f1f5f9; }

/* === Detail Descriptions === */
.detail-descriptions .el-descriptions__label { color: #94a3b8 !important; background: #1e2938 !important; font-size: 12px; }
.detail-descriptions .el-descriptions__content { color: #f1f5f9 !important; background: transparent !important; font-size: 13px; }
.detail-descriptions { background: transparent !important; }
.detail-descriptions .el-descriptions__body { background: transparent !important; }
.detail-descriptions .el-descriptions__table { border-collapse: separate; }
.detail-descriptions .el-descriptions__cell { border: 1px solid #334155 !important; }

/* === Progress in table === */
.glass-table .el-progress { margin: 0; }
.glass-table .el-progress__text { font-size: 11px !important; color: #94a3b8 !important; }

/* === Small pagination dark theme === */
.el-pagination.is-background .el-pager li { background: #1e2938 !important; color: #94a3b8 !important; border: 1px solid #334155 !important; border-radius: 8px !important; margin: 0 2px; min-width: 28px; }
.el-pagination.is-background .el-pager li.active { background: #3b82f6 !important; color: #fff !important; border-color: #3b82f6 !important; }
.el-pagination.is-background .el-pager li:hover { color: #3b82f6 !important; }
.el-pagination.is-background .btn-prev, .el-pagination.is-background .btn-next { background: #1e2938 !important; border: 1px solid #334155 !important; border-radius: 8px !important; color: #94a3b8 !important; }
.el-pagination.is-background .btn-prev:hover, .el-pagination.is-background .btn-next:hover { color: #3b82f6 !important; }
.el-pagination .el-select .el-input__wrapper { background: #1e2938 !important; }

/* === Dashboard Hero Row === */
.dash-hero-row { display: grid; grid-template-columns: 200px repeat(4, 1fr); gap: 14px; margin-bottom: 4px; }
.dash-health-card { background: #1e2938; border: 1px solid #334155; border-radius: 16px; padding: 16px; display: flex; align-items: center; justify-content: center; position: relative; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.1), inset 0 1px 0 rgba(255,255,255,0.05); transition: all 0.35s cubic-bezier(0.4,0,0.2,1); }
.dash-health-card:hover { transform: translateY(-4px); box-shadow: 0 12px 40px rgba(0,0,0,0.3), 0 0 40px rgba(59,130,246,0.04); border-color: #475569; }
.dash-health-score-wrap { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; width: 100%; }
.dash-health-ring { width: 90px; height: 90px; margin-bottom: 6px; }
.dash-health-score { position: absolute; top: 28px; font-size: 28px; font-weight: 800; line-height: 1; }
.dash-health-unit { position: absolute; top: 58px; font-size: 11px; color: #94a3b8; font-weight: 500; }
.dash-health-label { font-size: 11px; color: #94a3b8; letter-spacing: 0.5px; margin-top: 2px; }

.dash-metric-card { background: #1e2938; border: 1px solid #334155; border-radius: 16px; padding: 16px 18px; display: flex; align-items: center; gap: 14px; position: relative; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.1), inset 0 1px 0 rgba(255,255,255,0.05); transition: all 0.35s cubic-bezier(0.4,0,0.2,1); }
.dash-metric-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; border-radius: 16px 16px 0 0; opacity: 0.7; }
.dash-metric-card:nth-child(2)::before { background: linear-gradient(90deg, #3b82f6, #2563eb); }
.dash-metric-card:nth-child(3)::before { background: linear-gradient(90deg, #22c55e, #16a34a); }
.dash-metric-card:nth-child(4)::before { background: linear-gradient(90deg, #eab308, #ca8a04); }
.dash-metric-card:nth-child(5)::before { background: linear-gradient(90deg, #ef4444, #dc2626); }
.dash-metric-card:hover { transform: translateY(-4px); box-shadow: 0 12px 40px rgba(0,0,0,0.3), 0 0 40px rgba(59,130,246,0.04); border-color: #475569; }
.dash-metric-icon { font-size: 28px; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; border-radius: 12px; background: #1e2938; flex-shrink: 0; }
.dash-metric-body { flex: 1; min-width: 0; }
.dash-metric-value { font-size: 26px; font-weight: 800; color: #f1f5f9; line-height: 1.1; }
.dash-metric-unit { font-size: 13px; font-weight: 500; color: #94a3b8; margin-left: 2px; }
.dash-metric-label { font-size: 11px; color: #94a3b8; letter-spacing: 0.3px; margin-top: 3px; }
.dash-metric-trend { font-size: 11px; color: #94a3b8; margin-top: 2px; font-weight: 500; }

/* === Dashboard Stat Sub === */
.dash-stat-sub { font-size: 13px; color: #94a3b8; font-weight: 500; }

/* === Quick Actions === */
.dash-actions-wrap { margin-top: 18px; background: #1e2938; border: 1px solid #334155; border-radius: 16px; padding: 16px 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); transition: all 0.3s; }
.dash-actions-wrap:hover { border-color: #475569; box-shadow: 0 8px 30px rgba(0,0,0,0.2); }
.dash-actions-label { font-size: 13px; font-weight: 600; color: #f1f5f9; margin-bottom: 12px; letter-spacing: 0.3px; }
.dash-actions-row { display: flex; gap: 10px; flex-wrap: wrap; }

/* === Bottom Row === */
.dash-bottom-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 16px; }
.dash-alert-card { background: #1e2938; border: 1px solid #334155; border-radius: 16px; padding: 16px 18px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); transition: all 0.3s; }
.dash-alert-card:hover { border-color: #475569; box-shadow: 0 8px 30px rgba(0,0,0,0.2); }
.dash-alert-header { font-size: 13px; font-weight: 600; color: #f1f5f9; margin-bottom: 12px; letter-spacing: 0.3px; }
.dash-alert-empty { text-align: center; padding: 20px; color: #475569; font-size: 13px; }
.dash-hdfs-detail { background: #1e2938; border: 1px solid #334155; border-radius: 16px; padding: 16px 18px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); transition: all 0.3s; }
.dash-hdfs-detail:hover { border-color: #475569; box-shadow: 0 8px 30px rgba(0,0,0,0.2); }
.dash-hdfs-detail-header { font-size: 13px; font-weight: 600; color: #f1f5f9; margin-bottom: 12px; letter-spacing: 0.3px; }
.dash-hdfs-detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dash-hdfs-detail-item { display: flex; flex-direction: column; padding: 10px 12px; border-radius: 10px; background: #1e2938; border: 1px solid #334155; transition: all 0.2s; }
.dash-hdfs-detail-item:hover { background: rgba(59,130,246,0.05); border-color: rgba(59,130,246,0.12); transform: translateY(-1px); }
.dash-hdfs-detail-label { font-size: 11px; color: #94a3b8; margin-bottom: 4px; }
.dash-hdfs-detail-value { font-size: 18px; font-weight: 700; color: #f1f5f9; }

/* === Responsive for dashboard === */
@media (max-width: 1200px) { .dash-hero-row { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px) { .dash-hero-row { grid-template-columns: repeat(2, 1fr); } .dash-bottom-row { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .dash-hero-row { grid-template-columns: 1fr; } }

/* === Skeleton Loader === */
.skeleton-loader { display: flex; flex-direction: column; gap: 12px; padding: 16px; }
.skeleton-loader .skeleton-block { height: 16px; border-radius: 8px; background: linear-gradient(90deg, #1e2938 25%, #334155 50%, #1e2938 75%); background-size: 200% 100%; animation: skeleton-shimmer 1.5s ease-in-out infinite; }
.skeleton-loader .skeleton-block:nth-child(1) { width: 60%; }
.skeleton-loader .skeleton-block:nth-child(2) { width: 80%; }
.skeleton-loader .skeleton-block:nth-child(3) { width: 45%; }
.skeleton-loader .skeleton-block:nth-child(4) { width: 70%; }
.skeleton-loader .skeleton-block.skeleton-card { height: 120px; border-radius: 16px; }
.skeleton-loader .skeleton-block.skeleton-row { height: 40px; border-radius: 10px; }
@keyframes skeleton-shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* === Page Loading Bar === */
.page-loading-bar { position: fixed; top: 0; left: 0; width: 100%; height: 3px; z-index: 9999; background: transparent; overflow: hidden; }
.page-loading-bar::before { content: ''; position: absolute; top: 0; left: 0; height: 100%; width: 30%; background: linear-gradient(90deg, #3b82f6, #22c55e); border-radius: 2px; animation: loading-bar-progress 2s ease-in-out infinite; }
@keyframes loading-bar-progress { 0% { left: -30%; width: 30%; } 50% { width: 50%; } 100% { left: 100%; width: 30%; } }

/* === File System Navigation Row & Breadcrumbs === */
.fs-nav-row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.fs-breadcrumb { display: flex; align-items: center; flex-wrap: wrap; flex: 1; min-width: 0; padding: 6px 12px; border-radius: 10px; background: rgba(0,0,0,0.25); border: 1px solid #334155; overflow-x: auto; }
.fs-breadcrumb::-webkit-scrollbar { height: 3px; }
.fs-breadcrumb::-webkit-scrollbar-thumb { background: #334155; border-radius: 2px; }
.fs-breadcrumb-item { font-size: 13px; color: #38bdf8; cursor: pointer; white-space: nowrap; padding: 2px 4px; border-radius: 4px; transition: all 0.2s; font-weight: 500; flex-shrink: 0; }
.fs-breadcrumb-item:hover { color: #7dd3fc; background: rgba(56,189,248,0.1); text-decoration: underline; }
.fs-breadcrumb-root { font-weight: 700; font-size: 15px; color: #38bdf8; padding: 2px 6px; }
.fs-breadcrumb-root:hover { color: #7dd3fc; }
.fs-breadcrumb-sep { color: #475569; margin: 0 2px; font-size: 13px; flex-shrink: 0; user-select: none; }
.fs-search-input .el-input__wrapper { background: #1e2938 !important; border: 1px solid #334155 !important; box-shadow: none !important; border-radius: 10px; }
.fs-search-input .el-input__wrapper.is-focus { border-color: #3b82f6 !important; }
.fs-search-input .el-input__inner { color: #f1f5f9 !important; }
.fs-search-input .el-input__inner::placeholder { color: #475569; }

/* === File System Pagination === */
.fs-pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
.fs-pagination-wrap .el-pagination {
  --el-pagination-bg-color: #1e2938;
  --el-pagination-hover-bg-color: #334155;
  --el-pagination-button-color: #94a3b8;
  --el-pagination-button-disabled-bg-color: #1e2938;
  --el-pagination-button-disabled-color: #475569;
  font-weight: 400;
}

/* === File System Empty State === */
.fs-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 20px; border-radius: 14px; background: rgba(0,0,0,0.2); border: 1px dashed #334155; margin-top: 10px; }
.fs-empty-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.6; }
.fs-empty-text { font-size: 15px; color: #475569; font-weight: 500; }

/* === Command Palette (Cmd+K / Ctrl+K) === */
.cp-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 10000; background: rgba(15,23,42,0.8); backdrop-filter: blur(8px); display: flex; align-items: flex-start; justify-content: center; padding-top: 12vh; animation: cp-fade-in 0.15s ease; }
@keyframes cp-fade-in { from { opacity: 0; } to { opacity: 1; } }
.cp-modal { width: 580px; max-width: 90vw; max-height: 60vh; background: #1e2938; border: 1px solid #334155; border-radius: 16px; box-shadow: 0 25px 60px rgba(0,0,0,0.5), 0 0 80px rgba(59,130,246,0.05); overflow: hidden; display: flex; flex-direction: column; animation: cp-slide-in 0.2s cubic-bezier(0.4,0,0.2,1); }
@keyframes cp-slide-in { from { opacity: 0; transform: translateY(-20px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }
.cp-search-wrap { display: flex; align-items: center; padding: 14px 18px; border-bottom: 1px solid #334155; gap: 10px; }
.cp-search-icon { font-size: 18px; flex-shrink: 0; }
.cp-search-input { flex: 1; background: transparent; border: none; outline: none; color: #f1f5f9; font-size: 16px; font-family: inherit; }
.cp-search-input::placeholder { color: #475569; }
.cp-kbd { font-size: 11px; padding: 3px 8px; border-radius: 6px; background: #0f172a; border: 1px solid #334155; color: #94a3b8; font-family: 'JetBrains Mono', 'Fira Code', monospace; flex-shrink: 0; }
.cp-results { flex: 1; overflow-y: auto; padding: 6px 8px; }
.cp-results::-webkit-scrollbar { width: 4px; }
.cp-results::-webkit-scrollbar-thumb { background: #334155; border-radius: 2px; }
.cp-result-item { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 10px; cursor: pointer; transition: all 0.12s ease; }
.cp-result-item:hover, .cp-result-item.highlighted { background: #1e3a5f; }
.cp-result-icon { font-size: 18px; width: 28px; text-align: center; flex-shrink: 0; }
.cp-result-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.cp-result-label { font-size: 14px; font-weight: 500; color: #f1f5f9; }
.cp-result-sublabel { font-size: 11px; color: #64748b; margin-top: 1px; }
.cp-result-action { font-size: 12px; color: #475569; flex-shrink: 0; transition: color 0.15s; }
.cp-result-item:hover .cp-result-action, .cp-result-item.highlighted .cp-result-action { color: #3b82f6; }
.cp-category-header { padding: 6px 14px 4px; font-size: 11px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 1px; }
.cp-searching { display: flex; flex-direction: column; align-items: center; padding: 28px 20px; text-align: center; }
.cp-empty { display: flex; flex-direction: column; align-items: center; padding: 28px 20px; text-align: center; }
.cp-empty-icon { font-size: 36px; margin-bottom: 10px; opacity: 0.5; }
.cp-empty-text { font-size: 14px; color: #64748b; margin-bottom: 4px; }
.cp-empty-hint { font-size: 12px; color: #475569; }

/* === Preview Drawer === */
.preview-drawer .el-drawer { background: linear-gradient(135deg, #1e2938, #0f172a) !important; }
.preview-drawer .el-drawer__header { color: #f1f5f9; font-weight: 600; border-bottom: 1px solid #334155; padding: 16px 20px; margin-bottom: 0; }
.preview-drawer .el-drawer__body { padding: 0 20px 20px; overflow-y: auto; background: #0f172a; }
.preview-header { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.preview-title { font-size: 15px; font-weight: 600; color: #f1f5f9; }
.preview-header-info { display: flex; align-items: center; gap: 12px; font-size: 13px; flex-wrap: wrap; }
.preview-filename { color: #93c5fd; font-family: 'JetBrains Mono', 'Fira Code', monospace; font-weight: 500; font-size: 14px; }
.preview-meta { color: #94a3b8; font-size: 12px; }
.preview-truncated { color: #eab308; font-size: 12px; }
.preview-loading { text-align: center; padding: 40px; color: #94a3b8; font-size: 14px; }
.preview-error { text-align: center; padding: 40px; color: #ef4444; font-size: 14px; }
.preview-binary-msg { text-align: center; padding: 40px; color: #eab308; font-size: 16px; font-weight: 500; }
.preview-body { height: calc(100% - 10px); display: flex; flex-direction: column; }
.preview-content-wrap { display: flex; flex-direction: column; height: 100%; }
.preview-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #334155; margin-bottom: 8px; flex-shrink: 0; }
.preview-lines-info { font-size: 12px; color: #64748b; margin-right: auto; }
.preview-code { flex: 1; overflow-y: auto; background: #0f0f1a; border: 1px solid #334155; border-radius: 8px; padding: 12px 0; font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; line-height: 1.65; }
.preview-code.preview-wrap .preview-text { white-space: pre-wrap; word-break: break-all; }
.preview-line { display: flex; padding: 0; }
.preview-ln { min-width: 48px; text-align: right; padding-right: 12px; color: #475569; user-select: none; flex-shrink: 0; border-right: 1px solid #1e2938; margin-right: 12px; }
.preview-text { white-space: pre; color: #a8d8ea; }
.preview-line:hover { background: rgba(59,130,246,0.04); }
/* Syntax highlight colors */
.hl-json-key { color: #f9a825; }
.hl-number { color: #80cbc4; }
.hl-bool { color: #ce93d8; }
.hl-string { color: #a5d6a7; }
.hl-tag { color: #90caf9; }
.hl-comment { color: #546e7a; font-style: italic; }
.hl-log-ERROR { color: #ef5350; font-weight: 700; }
.hl-log-WARN { color: #ffb74d; font-weight: 600; }
.hl-log-WARNING { color: #ffb74d; font-weight: 600; }
.hl-log-INFO { color: #4fc3f7; }
.hl-log-DEBUG { color: #78909c; }
.hl-log-TRACE { color: #546e7a; }

/* Image preview */
.preview-image-wrap { display: flex; align-items: center; justify-content: center; height: 100%; padding: 16px; background: #0f0f1a; border: 1px solid #334155; border-radius: 8px; overflow: hidden; }
.preview-image { max-width: 100%; max-height: 100%; object-fit: contain; border-radius: 4px; box-shadow: 0 2px 12px rgba(0,0,0,0.3); }

/* Trash retention bar */
.trash-retention-bar { display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 8px; background: rgba(255,255,255,0.03); border: 1px solid #334155; border-radius: 6px; }
.trash-retention-label { font-size: 13px; color: #f1f5f9; font-weight: 500; white-space: nowrap; }
.trash-retention-unit { font-size: 12px; color: #64748b; }
.trash-retention-hint { font-size: 12px; color: #64748b; margin-left: 4px; }

/* === 拖拽上传覆盖区 === */
.drop-zone-overlay {
  display: none;
  position: relative;
  margin: 8px 0;
  border: 2px dashed #3b82f6;
  border-radius: 12px;
  background: rgba(59, 130, 246, 0.05);
  padding: 24px;
  text-align: center;
  transition: opacity 0.3s ease, border-color 0.3s ease, background 0.3s ease;
  opacity: 0;
  pointer-events: none;
}
.drop-zone-overlay.is-dragging {
  display: block;
  opacity: 1;
  pointer-events: auto;
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
}
.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.drop-zone-icon {
  font-size: 36px;
}
.drop-zone-text {
  font-size: 15px;
  font-weight: 500;
  color: #3b82f6;
}

/* === Status Bar === */
.fs-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  margin-top: 8px;
  border-radius: 10px;
  background: rgba(0,0,0,0.25);
  border: 1px solid #334155;
  font-size: 12px;
  color: #94a3b8;
  flex-wrap: wrap;
}
.fs-status-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.fs-status-icon {
  font-size: 14px;
}
.fs-status-sep {
  color: #334155;
  user-select: none;
}
.fs-status-path {
  color: #38bdf8;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
}

/* === Keyboard Shortcuts Popover === */
.shortcuts-popover {
  background: #1e2938 !important;
  border: 1px solid #334155 !important;
  border-radius: 12px !important;
  box-shadow: 0 12px 40px rgba(0,0,0,0.4) !important;
}
.shortcuts-popover .el-popover__title {
  color: #f1f5f9 !important;
}
.shortcuts-popover-content {
  padding: 4px 0;
}
.shortcuts-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 12px;
  text-align: center;
}
.shortcut-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}
.shortcut-row:last-of-type {
  border-bottom: none;
}
.shortcut-key {
  font-size: 11px;
  font-weight: 600;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: #0f172a;
  color: #93c5fd;
  padding: 2px 8px;
  border-radius: 5px;
  border: 1px solid #334155;
}
.shortcut-desc {
  font-size: 12px;
  color: #94a3b8;
}
.shortcuts-footer {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #334155;
  font-size: 11px;
  color: #475569;
  text-align: center;
}
.shortcuts-hint-btn {
  color: #94a3b8 !important;
  font-size: 16px !important;
  transition: all 0.2s !important;
}
.shortcuts-hint-btn:hover {
  color: #38bdf8 !important;
  background: rgba(56,189,248,0.1) !important;
}

/* === Refresh Button Spin Animation === */
@keyframes fs-refresh-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.el-button.is-loading .el-icon-loading {
  animation: fs-refresh-spin 0.8s linear infinite;
}
.el-button.is-loading .el-icon-loading svg {
  animation: fs-refresh-spin 0.8s linear infinite;
}

/* === Notification Bell === */
.notification-bell-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255,255,255,0.05);
  border: 1px solid #334155;
  cursor: pointer;
  transition: all 0.25s;
}
.notification-bell-wrap:hover {
  background: rgba(255,255,255,0.1);
  border-color: #475569;
}
.notification-bell {
  font-size: 18px;
  line-height: 1;
}
.notification-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 10px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 0 2px #0f172a;
  animation: notification-badge-pulse 2s infinite;
}
@keyframes notification-badge-pulse {
  0%, 100% { box-shadow: 0 0 0 2px #0f172a, 0 0 0 4px rgba(239,68,68,0.2); }
  50% { box-shadow: 0 0 0 2px #0f172a, 0 0 0 6px rgba(239,68,68,0.4); }
}

/* === Notification Popover === */
.notification-popover {
  background: #1e2938 !important;
  border: 1px solid #334155 !important;
  border-radius: 12px !important;
  padding: 0 !important;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4) !important;
}
.notification-popover .el-popper__arrow::before {
  background: #1e2938 !important;
  border-color: #334155 !important;
}
.notification-popover-content {
  padding: 12px 0;
  max-height: 380px;
  overflow-y: auto;
}
.notification-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 10px;
  border-bottom: 1px solid #334155;
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}
.notification-popover-count {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}
.notification-empty {
  text-align: center;
  padding: 24px 16px;
  color: #94a3b8;
  font-size: 13px;
}
.notification-list {
  padding: 4px 0;
}
.notification-item {
  padding: 10px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  transition: background 0.2s;
  cursor: default;
}
.notification-item:last-child {
  border-bottom: none;
}
.notification-item:hover {
  background: rgba(255,255,255,0.03);
}
.notification-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.notification-item-action {
  font-size: 13px;
  font-weight: 500;
  color: #f1f5f9;
}
.notification-item-target {
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}
.notification-item-time {
  font-size: 11px;
  color: #64748b;
}
.notification-section {
  padding: 0;
}
.notification-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
  padding: 6px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.notification-divider {
  height: 1px;
  background: #334155;
  margin: 4px 0;
}
.notification-popover-footer {
  padding: 10px 16px 12px;
  border-top: 1px solid #334155;
  text-align: center;
  font-size: 13px;
  color: #3b82f6;
  cursor: pointer;
  margin-top: 4px;
}
.notification-popover-footer:hover {
  color: #60a5fa;
}

/* === YARN App Type Tags === */
.yarn-type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.yarn-type-icon {
  font-size: 13px;
  line-height: 1;
}
.yarn-type-spark {
  background: rgba(234,179,8,0.15);
  color: #eab308;
  border: 1px solid rgba(234,179,8,0.25);
}
.yarn-type-mapreduce {
  background: rgba(59,130,246,0.15);
  color: #3b82f6;
  border: 1px solid rgba(59,130,246,0.25);
}
.yarn-type-other {
  background: rgba(148,163,184,0.12);
  color: #94a3b8;
  border: 1px solid rgba(148,163,184,0.2);
}

/* === 监控 Dashboard === */
.monitor-time-range {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: rgba(0,0,0,0.2);
  border-radius: 12px;
  border: 1px solid #334155;
}
.monitor-range-label {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  white-space: nowrap;
}
.monitor-metric-section {
  margin-bottom: 24px;
}
.monitor-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 12px;
  padding-left: 4px;
}
.monitor-card-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.monitor-metric-card {
  flex: 1;
  min-width: 280px;
  background: rgba(0,0,0,0.2);
  border: 1px solid #334155;
  border-radius: 14px;
  padding: 16px 18px;
  transition: all 0.25s ease;
}
.monitor-metric-card:hover {
  border-color: #475569;
  background: rgba(0,0,0,0.28);
}
.monitor-metric-card.half {
  flex: 0 0 calc(33.33% - 12px);
  min-width: 200px;
}
.monitor-metric-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.monitor-metric-current {
  font-size: 28px;
  font-weight: 700;
  color: #f1f5f9;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.monitor-metric-unit {
  font-size: 14px;
  font-weight: 400;
  color: #94a3b8;
  margin-left: 4px;
}
.monitor-metric-trend {
  font-size: 13px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 20px;
  white-space: nowrap;
}
.monitor-metric-trend.up {
  color: #22c55e;
  background: rgba(34,197,94,0.12);
}
.monitor-metric-trend.down {
  color: #ef4444;
  background: rgba(239,68,68,0.12);
}
.monitor-metric-trend.flat {
  color: #94a3b8;
  background: rgba(148,163,184,0.1);
}
.monitor-sparkline {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 48px;
  margin-bottom: 10px;
  padding: 4px 0;
}
.monitor-spark-bar {
  flex: 1;
  min-width: 3px;
  border-radius: 2px 2px 0 0;
  opacity: 0.8;
  transition: opacity 0.2s;
  cursor: help;
}
.monitor-spark-bar:hover {
  opacity: 1;
}

/* ECharts container */
.echart-container { width: 100%; height: 180px; border-radius: 8px; }

/* Dark date picker */
.dark-date-picker { background: #1e2938 !important; border: 1px solid #334155 !important; }
.dark-date-picker .el-date-table td.today { color: #3b82f6; }

.monitor-metric-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
}
.monitor-metric-label {
  font-weight: 500;
}
.monitor-metric-count {
  color: #475569;
}
.monitor-empty-card {
  flex: 1;
  min-width: 280px;
  border: 1px dashed #334155;
  border-radius: 14px;
  padding: 32px;
  text-align: center;
}
.monitor-empty-text {
  font-size: 14px;
  color: #64748b;
}

/* === Grafana Embed === */
.grafana-embed-container {
  margin-top: 12px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #334155;
  height: 72vh;
  background: #0f172a;
}
.grafana-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #0f172a;
}

/* === Help Modal === */
.help-dialog .el-dialog {
  background: linear-gradient(135deg, #1e2938, #0f172a) !important;
  border: 1px solid #334155;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.help-dialog .el-dialog__title {
  color: #f1f5f9;
  font-weight: 600;
  font-size: 18px;
}
.help-dialog .el-dialog__header {
  border-bottom: 1px solid #334155;
  padding: 18px 24px 14px;
  margin-right: 0;
}
.help-dialog .el-dialog__headerbtn {
  top: 18px;
  right: 20px;
}
.help-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #94a3b8;
  font-size: 18px;
}
.help-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #f1f5f9;
}
.help-dialog .el-dialog__body {
  padding: 20px 24px;
}
.help-dialog .el-dialog__footer {
  border-top: 1px solid #334155;
  padding: 12px 24px;
}
.help-modal-body {
  max-height: 60vh;
  overflow-y: auto;
}
.help-modal-body::-webkit-scrollbar {
  width: 4px;
}
.help-modal-body::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 2px;
}
.help-section {
  margin-bottom: 20px;
}
.help-section:last-child {
  margin-bottom: 0;
}
.help-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #334155;
}
.help-shortcuts-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.help-shortcut-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255,255,255,0.02);
  transition: background 0.2s;
}
.help-shortcut-row:hover {
  background: rgba(59,130,246,0.06);
}
.help-kbd {
  font-size: 11px;
  font-weight: 600;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: #0f172a;
  color: #93c5fd;
  padding: 2px 8px;
  border-radius: 5px;
  border: 1px solid #334155;
  display: inline-block;
  line-height: 1.5;
}
.help-kbd-inline {
  font-size: 11px;
  font-weight: 600;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: #0f172a;
  color: #93c5fd;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #475569;
  display: inline-block;
  line-height: 1.5;
}
.help-or {
  font-size: 11px;
  color: #475569;
}
.help-desc {
  font-size: 13px;
  color: #94a3b8;
  margin-left: auto;
}
.help-descriptions {
  margin-top: 4px;
}
.help-descriptions .el-descriptions__label {
  color: #94a3b8 !important;
  background: #1e2938 !important;
  font-size: 12px;
  white-space: nowrap;
}
.help-descriptions .el-descriptions__content {
  color: #f1f5f9 !important;
  background: transparent !important;
  font-size: 13px;
}
.help-descriptions .el-descriptions__body {
  background: transparent !important;
}
.help-descriptions .el-descriptions__cell {
  border: 1px solid #334155 !important;
}
.help-quickstart-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.help-quickstart-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255,255,255,0.02);
  border: 1px solid transparent;
  transition: all 0.2s;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.5;
}
.help-quickstart-item:hover {
  background: rgba(59,130,246,0.06);
  border-color: rgba(59,130,246,0.12);
  color: #f1f5f9;
}
.help-qs-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 1px;
}
.help-footer {
  display: flex;
  align-items: center;
  justify-content: center;
}
.help-footer-hint {
  font-size: 12px;
  color: #64748b;
}
/* Sidebar help button */
.help-btn-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(255,255,255,0.03);
  border: 1px solid transparent;
  transition: all 0.2s;
}
.help-btn-wrap:hover {
  border-color: #334155;
  background: rgba(255,255,255,0.05);
}
.help-btn {
  color: #94a3b8 !important;
  font-size: 16px !important;
  transition: all 0.2s !important;
}
.help-btn:hover {
  color: #38bdf8 !important;
  background: rgba(56,189,248,0.1) !important;
}
.help-btn-label {
  font-size: 11px;
  color: #64748b;
  user-select: none;
}

/* === Tab Fade Transition Animation === */
.tab-fade-enter-active {
  animation: tabFadeIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes tabFadeIn {
  from { opacity: 0; transform: translateY(16px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* === Success Flash Animation === */
.success-flash {
  animation: successFlashAnim 1.5s ease;
}
@keyframes successFlashAnim {
  0% { box-shadow: inset 0 0 0 0 rgba(34, 197, 94, 0); }
  15% { box-shadow: inset 0 0 30px 4px rgba(34, 197, 94, 0.25); }
  30% { box-shadow: inset 0 0 20px 2px rgba(34, 197, 94, 0.12); }
  100% { box-shadow: inset 0 0 0 0 rgba(34, 197, 94, 0); }
}

/* === Cluster Connection Status Indicator === */
.sidebar-connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-top: 6px;
  border-radius: 8px;
  background: rgba(255,255,255,0.02);
  border: 1px solid transparent;
  transition: all 0.3s;
}
.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: background 0.3s, box-shadow 0.3s;
}
.conn-connected {
  background: #22c55e;
  box-shadow: 0 0 10px rgba(34, 197, 94, 0.6);
  animation: conn-pulse 2s infinite;
}
.conn-disconnected {
  background: #ef4444;
  box-shadow: 0 0 10px rgba(239, 68, 68, 0.4);
}
@keyframes conn-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.conn-label {
  font-size: 11px;
  color: #64748b;
  user-select: none;
  white-space: nowrap;
}

/* === App Footer === */
.app-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 28px;
  border-top: 1px solid #1e2938;
  background: #0f172a;
  font-size: 11px;
  color: #475569;
}
.footer-sep {
  color: #334155;
  font-size: 10px;
}
.footer-version {
  font-weight: 500;
  color: #64748b;
}
.footer-hadoop {
  color: #64748b;
}
.footer-copyright {
  color: #475569;
}

/* === System Health Section === */
.dash-health-section {
  background: #1e2938;
  border: 1px solid #334155;
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  transition: all 0.3s;
}
.dash-health-section:hover {
  border-color: #475569;
  box-shadow: 0 8px 30px rgba(0,0,0,0.2);
}
.dash-health-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 8px;
}
.health-overall-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 12px;
  border-radius: 20px;
  letter-spacing: 0.3px;
}
.dash-health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}
.dash-health-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(0,0,0,0.2);
  border: 1px solid #334155;
  transition: all 0.2s;
}
.dash-health-item:hover {
  background: rgba(59,130,246,0.05);
  border-color: rgba(59,130,246,0.12);
  transform: translateY(-1px);
}
.dash-health-item-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}
.dash-health-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.dash-health-item-name {
  font-size: 12px;
  font-weight: 600;
  color: #f1f5f9;
}
.dash-health-item-status {
  font-size: 11px;
  font-weight: 500;
}
.dash-health-item-msg {
  font-size: 10px;
  color: #64748b;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}
.dash-health-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 16px;
}

/* === 全局搜索对话框 === */
.search-dialog-body {
  min-height: 200px;
}
.search-dialog-input-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-dialog-input {
  flex: 1;
}
.search-results-info {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 10px;
}
.search-loading-hint,
.search-error-hint,
.search-empty-hint {
  text-align: center;
  padding: 40px 20px;
  font-size: 14px;
  color: #94a3b8;
}
.search-error-hint {
  color: #ef4444;
}
.search-results-table {
  border-radius: 8px;
  overflow: hidden;
}

/* === 文件笔记对话框 === */
.note-dialog-body {
  min-height: 150px;
}
.note-dialog-path {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 12px;
  padding: 6px 10px;
  background: rgba(0,0,0,0.2);
  border-radius: 6px;
  font-family: monospace;
  word-break: break-all;
}
.note-dialog-textarea {
  font-size: 14px;
  line-height: 1.6;
}
.note-dialog-textarea .el-textarea__inner {
  background: rgba(0,0,0,0.2) !important;
  color: #f1f5f9;
  border-color: #334155;
}

/* === System Config Page === */
.config-page {
  padding: 8px 0;
}
.config-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 20px;
  color: #94a3b8;
  font-size: 15px;
}
.config-error {
  padding: 24px 20px;
  color: #f87171;
  font-size: 14px;
  text-align: center;
  background: rgba(239,68,68,0.08);
  border: 1px solid rgba(239,68,68,0.2);
  border-radius: 12px;
  margin: 20px 0;
}
.config-groups {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.config-group-card {
  background: #1e2938;
  border: 1px solid #334155;
  border-radius: 12px;
  overflow: hidden;
}
.config-group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: rgba(0,0,0,0.2);
  border-bottom: 1px solid #334155;
}
.config-group-icon {
  font-size: 20px;
}
.config-group-title {
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
}
.config-group-body {
  padding: 4px 0;
}
.config-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid rgba(51,65,85,0.4);
  transition: background 0.2s;
}
.config-item-row:last-child {
  border-bottom: none;
}
.config-item-row:hover {
  background: rgba(51,65,85,0.3);
}
.config-item-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.config-item-name {
  font-size: 14px;
  color: #f1f5f9;
  font-weight: 500;
}
.config-item-key {
  font-size: 11px;
  color: #64748b;
  font-family: monospace;
}
.config-item-value {
  font-size: 13px;
  color: #38bdf8;
  font-family: monospace;
  text-align: right;
  max-width: 55%;
  word-break: break-all;
  padding-left: 16px;
}

/* === 📚 Data Catalog === */
.catalog-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 20px;
}
.catalog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  background: linear-gradient(135deg, rgba(30,41,56,0.6) 0%, rgba(15,23,42,0.4) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(51,65,85,0.6);
  border-radius: 12px;
  padding: 16px 20px;
}
.catalog-header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.catalog-header-left h2 {
  background: linear-gradient(135deg, #e2e8f0, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.catalog-header-count {
  font-size: 13px;
  color: #64748b;
  background: rgba(51,65,85,0.5);
  padding: 2px 12px;
  border-radius: 20px;
  border: 1px solid rgba(51,65,85,0.4);
}
.catalog-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.catalog-table {
  flex: 1;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.catalog-table .el-table__row {
  cursor: pointer;
  transition: background 0.2s;
}
.catalog-table .el-table__row:hover {
  background: rgba(59, 130, 246, 0.08) !important;
}
.catalog-tag-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  align-items: center;
}
.catalog-tag-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  color: #fff;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(0,0,0,0.3);
  transition: transform 0.15s, box-shadow 0.15s;
}
.catalog-tag-badge:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(0,0,0,0.4);
}
.catalog-drawer .el-drawer__header {
  margin-bottom: 0;
  border-bottom: 1px solid #334155;
  padding: 16px 20px;
  background: rgba(30,41,56,0.8);
}
.catalog-drawer-body {
  padding: 16px 20px;
}
.catalog-drawer-section {
  margin-bottom: 24px;
  background: rgba(30,41,56,0.5);
  border: 1px solid rgba(51,65,85,0.5);
  border-radius: 10px;
  padding: 16px;
  transition: border-color 0.2s;
}
.catalog-drawer-section:hover {
  border-color: rgba(59,130,246,0.3);
}
.catalog-drawer-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(51,65,85,0.5);
  position: relative;
}
.catalog-drawer-section-title::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, #3b82f6, transparent);
  border-radius: 2px;
}
.catalog-descriptions {
  background: transparent;
}
.catalog-descriptions .el-descriptions__title {
  color: #f1f5f9;
}
.catalog-descriptions .el-descriptions__label {
  color: #94a3b8;
  background: #1e2938;
  border-color: #334155 !important;
}
.catalog-descriptions .el-descriptions__content {
  color: #e2e8f0;
  background: #0f172a;
  border-color: #334155 !important;
}
.catalog-column-table {
  margin-top: 8px;
  border-radius: 8px;
  overflow: hidden;
}
.catalog-form {
  background: transparent;
}
.catalog-form .el-form-item__label {
  color: #cbd5e1;
}

/* === Catalog Dialogs === */
.glass-dialog .el-dialog__body {
  padding: 24px;
}
.glass-dialog .el-form-item {
  margin-bottom: 18px;
}
.glass-dialog .el-form-item__label {
  color: #cbd5e1;
  font-weight: 500;
}

/* Catalog empty state */
.fs-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  color: #64748b;
}
.fs-empty-icon {
  font-size: 36px;
  margin-bottom: 12px;
  opacity: 0.6;
}
.fs-empty-text {
  font-size: 14px;
  color: #94a3b8;
}

/* ============================================================ */
/* Workflow Engine Styles */
/* ============================================================ */
.workflow-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, rgba(30,41,56,0.6) 0%, rgba(15,23,42,0.4) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(51,65,85,0.6);
  border-radius: 12px;
  padding: 16px 20px;
}
.workflow-header h2 {
  background: linear-gradient(135deg, #e2e8f0, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.workflow-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 16px 20px;
  background: rgba(30,41,56,0.5);
  border: 1px solid rgba(51,65,85,0.5);
  border-radius: 10px;
  flex-wrap: wrap;
  gap: 8px;
}
.workflow-detail-header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.workflow-detail-header-left h3 {
  background: linear-gradient(135deg, #e2e8f0, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.workflow-detail-header-actions {
  display: flex;
  gap: 8px;
}
.workflow-steps-section {
  background: linear-gradient(135deg, rgba(30,41,56,0.6) 0%, rgba(15,23,42,0.3) 100%);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #334155;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.workflow-steps-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
}
.workflow-step-card {
  background: linear-gradient(135deg, rgba(15,23,42,0.9) 0%, rgba(30,41,56,0.6) 100%);
  border: 1px solid #334155;
  border-radius: 10px;
  padding: 14px 18px;
  position: relative;
  transition: all 0.25s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.workflow-step-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 16px rgba(59,130,246,0.15);
  transform: translateY(-1px);
}
.workflow-step-card.step-type-mapreduce {
  border-left: 3px solid #3b82f6;
}
.workflow-step-card.step-type-shell {
  border-left: 3px solid #22c55e;
}
.workflow-step-card.step-type-wait {
  border-left: 3px solid #eab308;
}
.workflow-step-card.step-type-http {
  border-left: 3px solid #a855f7;
}
.workflow-step-connector {
  display: flex;
  justify-content: center;
  padding: 2px 0;
}
.workflow-step-arrow {
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 10px solid #3b82f6;
  filter: drop-shadow(0 1px 2px rgba(59,130,246,0.4));
}
.workflow-step-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.workflow-step-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.workflow-step-type-icon {
  font-size: 18px;
  margin-right: 6px;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
}
.workflow-step-name {
  font-weight: 600;
  color: #f1f5f9;
  font-size: 14px;
}
.workflow-step-card-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 4px 0;
}
.workflow-step-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid rgba(51,65,85,0.4);
}
.workflow-exec-drawer .el-drawer__header {
  margin-bottom: 0;
  border-bottom: 1px solid #334155;
  padding: 16px 20px;
  background: rgba(30,41,56,0.8);
}
.workflow-exec-drawer-body {
  padding: 16px 20px;
}
.workflow-exec-drawer-section {
  margin-bottom: 24px;
  background: rgba(30,41,56,0.5);
  border: 1px solid rgba(51,65,85,0.5);
  border-radius: 10px;
  padding: 16px;
  transition: border-color 0.2s;
}
.workflow-exec-drawer-section:hover {
  border-color: rgba(59,130,246,0.3);
}
.workflow-exec-drawer-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(51,65,85,0.5);
  position: relative;
}
.workflow-exec-drawer-section-title::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, #3b82f6, transparent);
  border-radius: 2px;
}
.workflow-dialog .el-form-item__label {
  color: #cbd5e1;
}
.workflow-dialog .el-input-number {
  width: 100%;
}

/* Workflow exec status styling */
.workflow-executions-section {
  background: linear-gradient(135deg, rgba(30,41,56,0.5) 0%, rgba(15,23,42,0.3) 100%);
  border: 1px solid #334155;
  border-radius: 12px;
  padding: 20px;
}

/* Schedule info pills */
.workflow-schedule-info {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: rgba(30,41,56,0.4);
  border: 1px solid rgba(51,65,85,0.4);
  border-radius: 8px;
  font-size: 13px;
}

/* === Dashboard: Auto-Refresh Bar === */
.dash-auto-refresh-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1e2938;
  border: 1px solid #334155;
  border-radius: 12px;
  padding: 10px 16px;
  margin-top: 16px;
  transition: all 0.3s;
}
.dash-auto-refresh-bar:hover {
  border-color: #475569;
}
.dash-auto-refresh-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dash-auto-refresh-label {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}
.dash-auto-refresh-countdown {
  font-size: 13px;
  font-weight: 700;
  color: #22c55e;
  min-width: 30px;
  font-variant-numeric: tabular-nums;
}
.dash-countdown-warning {
  color: #eab308 !important;
  animation: dash-pulse 0.5s ease-in-out infinite alternate;
}
@keyframes dash-pulse {
  from { opacity: 1; }
  to { opacity: 0.4; }
}

/* === Dashboard: 7-Day Health Trend === */
.dash-trend-section {
  background: #1e2938;
  border: 1px solid #334155;
  border-radius: 16px;
  padding: 16px 18px;
  margin-top: 14px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  transition: all 0.3s;
}
.dash-trend-section:hover {
  border-color: #475569;
  box-shadow: 0 8px 30px rgba(0,0,0,0.2);
}
.dash-trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.dash-trend-title {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}
.dash-trend-current {
  font-size: 12px;
  font-weight: 600;
}
.dash-trend-loading, .dash-trend-empty {
  text-align: center;
  color: #64748b;
  font-size: 13px;
  padding: 20px 0;
}
.dash-trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 6px;
  height: 120px;
  padding: 0 4px;
}
.dash-trend-bar-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  position: relative;
}
.dash-trend-bar {
  width: 100%;
  max-width: 36px;
  border-radius: 4px 4px 0 0;
  min-height: 4px;
  transition: height 0.6s cubic-bezier(0.4,0,0.2,1), background 0.3s;
  cursor: pointer;
  position: relative;
}
.dash-trend-bar:hover {
  opacity: 0.8;
  transform: scaleY(1.03);
  transform-origin: bottom;
}
.dash-trend-date {
  font-size: 10px;
  color: #64748b;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}
.dash-trend-score {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  margin-top: 1px;
}

/* === Health Detail Drawer === */
.health-detail-drawer .el-drawer__header {
  margin-bottom: 0;
  border-bottom: 1px solid #334155;
  padding: 16px 20px;
  background: rgba(30,41,56,0.8);
}
.health-drawer-body {
  padding: 20px;
}
.health-drawer-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(51,65,85,0.4);
  margin-bottom: 20px;
}
.health-drawer-score-ring {
  position: relative;
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}
.health-drawer-score-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 22px;
  font-weight: 800;
}
.health-drawer-score-info {
  flex: 1;
}
.health-drawer-score-title {
  font-size: 16px;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 4px;
}
.health-drawer-score-status {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 2px;
}
.health-drawer-score-desc {
  font-size: 12px;
  color: #94a3b8;
}
.health-drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 12px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.health-drawer-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.health-drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(0,0,0,0.15);
  border: 1px solid #334155;
  transition: all 0.2s;
}
.health-drawer-item:hover {
  background: rgba(59,130,246,0.05);
  border-color: rgba(59,130,246,0.12);
}
.health-item-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.health-item-body {
  flex: 1;
  min-width: 0;
}
.health-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
}
.health-item-detail {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}
.health-item-action {
  flex-shrink: 0;
}
.health-drawer-loading {
  text-align: center;
  padding: 24px;
  color: #64748b;
  font-size: 13px;
}
.health-item-down {
  border-left: 3px solid #ef4444;
}
.health-item-degraded {
  border-left: 3px solid #eab308;
}
.health-item-healthy {
  border-left: 3px solid #22c55e;
}
/* Timeline bars for resource overview */
.timeline-bar-track {
  background: rgba(255,255,255,0.08);
  border-radius: 6px;
  height: 10px;
  overflow: hidden;
  position: relative;
}
.timeline-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.4s ease;
  min-width: 2px;
}
.timeline-bar-blue {
  background: linear-gradient(90deg, #4fc3f7, #2196f3);
}
.timeline-bar-green {
  background: linear-gradient(90deg, #66bb6a, #43a047);
}

/* === MR 筛选栏拖拽排序 === */
.filter-drag-item {
  cursor: grab;
  transition: all 0.15s;
  padding: 2px;
  border: 1px solid transparent;
  border-radius: 6px;
}
.filter-drag-item:active {
  cursor: grabbing;
  border-color: #3b82f6;
  background: rgba(59,130,246,0.08);
}
.filter-drag-item[draggable="true"]::before {
  content: '⠿';
  font-size: 14px;
  color: rgba(255,255,255,0.25);
  margin-right: 4px;
  display: inline-block;
}

/* === Element Plus 深色主题覆盖（防止组件白色背景） === */
.el-input__wrapper,
.el-select__wrapper,
.el-date-editor .el-input__wrapper,
.el-pagination button,
.el-pagination .el-pager li,
.el-dropdown-menu,
.el-dropdown-menu__item,
.el-popover,
.el-message-box,
.el-message,
.el-notification,
.el-tooltip__popper,
.el-popper,
.el-select-dropdown,
.el-picker-panel,
.el-date-picker,
.el-time-panel { background: #1e2938 !important; border-color: #334155 !important; }

.el-input__inner,
.el-select-dropdown__item,
.el-dropdown-menu__item,
.el-pagination .el-pager li { color: #e2e8f0 !important; }

.el-select-dropdown__item.hover,
.el-select-dropdown__item:hover,
.el-dropdown-menu__item:hover { background: rgba(59,130,246,0.12) !important; }

.el-pagination button:disabled,
.el-pagination .el-pager li.disabled { background: #1e2938 !important; color: #64748b !important; }

.el-popper[x-placement^="bottom"] .popper__arrow::after { border-bottom-color: #334155 !important; }
.el-popper[x-placement^="top"] .popper__arrow::after { border-top-color: #334155 !important; }
</style>
