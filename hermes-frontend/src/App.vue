<template>
  <el-container class="layout">
    <!-- Header -->
    <el-header class="header">
      <div class="header-left">
        <h2>Hermes</h2>
        <span class="subtitle">企业级 Hadoop 集群管理平台</span>
      </div>
      <div class="header-right">
        <el-tag v-if="token" type="success" size="small">已登录: {{ currentUser }}</el-tag>
        <el-button v-if="!token" type="primary" @click="showLogin = true">登录</el-button>
        <el-button v-else type="danger" @click="logout">退出</el-button>
      </div>
    </el-header>

    <el-main>
      <!-- Login Dialog -->
      <el-dialog v-model="showLogin" title="用户登录" width="400px" :close-on-click-modal="false">
        <el-form :model="loginForm" label-width="80px">
          <el-form-item label="用户名">
            <el-input v-model="loginForm.username" placeholder="admin" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" placeholder="admin" show-password />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showLogin = false">取消</el-button>
          <el-button type="primary" @click="handleLogin" :loading="loginLoading">登录</el-button>
        </template>
      </el-dialog>

      <!-- Main Tabs -->
      <el-tabs v-model="activeTab" type="card" @tab-click="handleTabChange">
        <!-- Dashboard Tab -->
        <el-tab-pane label="集群概览 Dashboard" name="dashboard">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-card header="HDFS 存储概览">
                <div ref="hdfsChart" style="height: 280px;"></div>
                <el-descriptions :column="1" size="small" style="margin-top: 10px;">
                  <el-descriptions-item label="已使用">
                    {{ formatSize(hdfsData.usedSpace) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="文件数">{{ hdfsData.fileCount }}</el-descriptions-item>
                  <el-descriptions-item label="目录数">{{ hdfsData.dirCount }}</el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>

            <el-col :span="8">
              <el-card header="YARN 资源概览">
                <div ref="yarnChart" style="height: 280px;"></div>
              </el-card>
            </el-col>

            <el-col :span="8">
              <el-card header="集群状态">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="集群ID">{{ currentCluster }}</el-descriptions-item>
                  <el-descriptions-item label="NodeManager 数量">{{ yarnData.numNodeManagers || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="Running Apps">{{ yarnData.runningApplications || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="总内存 (MB)">{{ yarnData.totalMemoryMB || 0 }}</el-descriptions-item>
                </el-descriptions>
                <el-button type="primary" size="small" @click="refreshDashboard" style="margin-top: 15px; width: 100%">
                  刷新指标
                </el-button>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- HDFS Tab -->
        <el-tab-pane label="HDFS 文件管理" name="hdfs">
          <div style="margin-bottom: 15px;">
            <el-input v-model="currentPath" placeholder="路径" style="width: 300px;" @keyup.enter="loadHdfsFiles" />
            <el-button type="primary" @click="loadHdfsFiles" style="margin-left: 10px;">进入</el-button>
            <el-button @click="goParentDir">上级</el-button>
            <el-button @click="loadHdfsFiles">刷新</el-button>
          </div>

          <el-table :data="hdfsFileList" v-loading="hdfsLoading" stripe style="width: 100%">
            <el-table-column prop="name" label="名称" min-width="220">
              <template #default="scope">
                <el-link v-if="scope.row.isDirectory" type="primary" @click="enterHdfsDir(scope.row.name)">
                  📁 {{ scope.row.name }}
                </el-link>
                <span v-else>📄 {{ scope.row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="length" label="大小" width="120">
              <template #default="scope">{{ formatSize(scope.row.length) }}</template>
            </el-table-column>
            <el-table-column prop="owner" label="所有者" width="100" />
            <el-table-column prop="permission" label="权限" width="100" />
            <el-table-column prop="modificationTime" label="修改时间" width="180">
              <template #default="scope">{{ formatTime(scope.row.modificationTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- YARN Tab -->
        <el-tab-pane label="YARN 应用管理" name="yarn">
          <el-row :gutter="20">
            <!-- Existing YARN Apps Section -->
            <el-col :span="14">
              <el-card header="YARN 应用列表">
                <div style="margin-bottom: 15px; display: flex; gap: 10px; align-items: center;">
                  <el-input v-model="yarnStateFilter" placeholder="状态筛选" style="width: 200px;" />
                  <el-button type="primary" @click="loadYarnApps">查询应用</el-button>
                  <el-button type="success" @click="showSubmitDialog = true">提交新应用</el-button>
                </div>

                <el-table :data="yarnAppList" v-loading="yarnLoading" stripe style="max-height: 400px; overflow: auto;">
                  <el-table-column prop="appId" label="App ID" width="160" />
                  <el-table-column prop="name" label="名称" min-width="140" />
                  <el-table-column prop="queue" label="队列" width="100" />
                  <el-table-column prop="state" label="状态" width="110">
                    <template #default="scope">
                      <el-tag :type="scope.row.state === 'RUNNING' ? 'success' : 'info'">{{ scope.row.state }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="120">
                    <template #default="scope">
                      <el-button v-if="scope.row.state === 'RUNNING'" size="small" type="danger" @click="killYarnApp(scope.row.appId)">杀掉</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>

            <!-- NEW: YARN Queue Monitoring Charts -->
            <el-col :span="10">
              <el-card header="YARN 队列监控图表">
                <el-button type="primary" size="small" @click="loadQueueMetrics" style="margin-bottom: 10px; width: 100%">
                  刷新队列状态
                </el-button>

                <div ref="queueCapacityChart" style="height: 220px; margin-bottom: 15px;"></div>
                <div ref="queueAppsChart" style="height: 220px;"></div>

                <div style="margin-top: 10px; font-size: 12px; color: #909399;">
                  显示各队列容量使用率与 Running Apps 数量
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-main>

    <!-- Submit App Dialog -->
    <el-dialog v-model="showSubmitDialog" title="提交 YARN 应用" width="450px">
      <el-form :model="submitForm" label-width="100px">
        <el-form-item label="应用名称">
          <el-input v-model="submitForm.appName" />
        </el-form-item>
        <el-form-item label="队列">
          <el-input v-model="submitForm.queue" placeholder="default" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" @click="submitYarnApp" :loading="submitLoading">提交</el-button>
      </template>
    </el-dialog>

    <el-footer class="footer">
      <span>Powered by Grok + Apache Hadoop Official Client | 基于 FileSystem / YarnClient API</span>
    </el-footer>
  </el-container>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

// State
const token = ref(localStorage.getItem('hermes_token') || '')
const currentUser = ref(localStorage.getItem('hermes_user') || '')
const activeTab = ref('dashboard')
const currentCluster = ref('cluster1')

// Login
const showLogin = ref(false)
const loginLoading = ref(false)
const loginForm = ref({ username: 'admin', password: 'admin' })

// Dashboard
const hdfsData = ref({ usedSpace: 0, fileCount: 0, dirCount: 0 })
const yarnData = ref({})
const hdfsChart = ref(null)
const yarnChart = ref(null)
let hdfsEchartInstance = null
let yarnEchartInstance = null

// HDFS
const currentPath = ref('/')
const hdfsFileList = ref([])
const hdfsLoading = ref(false)

// YARN
const yarnAppList = ref([])
const yarnLoading = ref(false)
const yarnStateFilter = ref('')
const showSubmitDialog = ref(false)
const submitLoading = ref(false)
const submitForm = ref({ appName: 'Hermes-Test-App', queue: 'default' })

// NEW: Queue Monitoring
const queueCapacityChart = ref(null)
const queueAppsChart = ref(null)
let queueCapacityInstance = null
let queueAppsInstance = null

const queueData = ref([])

// Axios interceptor for JWT
axios.interceptors.request.use(config => {
  if (token.value) {
    config.headers.Authorization = `Bearer ${token.value}`
  }
  return config
})

// Login function
const handleLogin = async () => {
  loginLoading.value = true
  try {
    const res = await axios.post('/api/v1/auth/login', loginForm.value)
    if (res.data.code === 0) {
      token.value = res.data.data.token
      currentUser.value = res.data.data.username
      localStorage.setItem('hermes_token', token.value)
      localStorage.setItem('hermes_user', currentUser.value)
      showLogin.value = false
      ElMessage.success('登录成功')
      refreshDashboard()
    } else {
      ElMessage.error(res.data.msg || '登录失败')
    }
  } catch (e) {
    ElMessage.error('登录错误: ' + (e.response?.data?.msg || e.message))
  } finally {
    loginLoading.value = false
  }
}

const logout = () => {
  token.value = ''
  currentUser.value = ''
  localStorage.removeItem('hermes_token')
  localStorage.removeItem('hermes_user')
  ElMessage.info('已退出')
  hdfsFileList.value = []
  yarnAppList.value = []
}

// Dashboard
const refreshDashboard = async () => {
  try {
    const res = await axios.get('/api/v1/dashboard/overview', { params: { clusterId: currentCluster.value } })
    if (res.data.code === 0) {
      const data = res.data.data
      hdfsData.value = data.hdfs || {}
      yarnData.value = data.yarn || {}
      nextTick(() => {
        renderCharts()
      })
    }
  } catch (e) {
    ElMessage.error('获取 Dashboard 失败: ' + e.message)
  }
}

const renderCharts = () => {
  // HDFS Pie
  if (hdfsEchartInstance) hdfsEchartInstance.dispose()
  hdfsEchartInstance = echarts.init(hdfsChart.value)
  const used = hdfsData.value.usedSpace || 0
  const total = used * 1.5 || 100
  hdfsEchartInstance.setOption({
    title: { text: 'HDFS 存储使用率', left: 'center', top: 10 },
    tooltip: { trigger: 'item' },
    series: [{
      name: 'Storage',
      type: 'pie',
      radius: '70%',
      data: [
        { value: used, name: '已使用' },
        { value: total - used, name: '剩余' }
      ]
    }]
  })

  // YARN Bar
  if (yarnEchartInstance) yarnEchartInstance.dispose()
  yarnEchartInstance = echarts.init(yarnChart.value)
  yarnEchartInstance.setOption({
    title: { text: 'YARN 资源分配', left: 'center', top: 10 },
    tooltip: {},
    xAxis: { type: 'category', data: ['Memory (GB)', 'vCores'] },
    yAxis: { type: 'value' },
    series: [{
      name: 'Total',
      type: 'bar',
      data: [
        Math.round((yarnData.value.totalMemoryMB || 0) / 1024),
        yarnData.value.totalVCores || 0
      ],
      itemStyle: { color: '#409eff' }
    }]
  })
}

// HDFS functions
const loadHdfsFiles = async () => {
  if (!token.value) { ElMessage.warning('请先登录'); return }
  hdfsLoading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/list', {
      params: { clusterId: currentCluster.value, path: currentPath.value }
    })
    if (res.data.code === 0) {
      hdfsFileList.value = res.data.data
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('HDFS 加载失败: ' + e.message)
  } finally {
    hdfsLoading.value = false
  }
}

const enterHdfsDir = (name) => {
  currentPath.value = currentPath.value === '/' ? '/' + name : currentPath.value + '/' + name
  loadHdfsFiles()
}

const goParentDir = () => {
  if (currentPath.value === '/' || currentPath.value === '') return
  const parts = currentPath.value.split('/').filter(p => p)
  parts.pop()
  currentPath.value = parts.length ? '/' + parts.join('/') : '/'
  loadHdfsFiles()
}

// YARN functions
const loadYarnApps = async () => {
  if (!token.value) { ElMessage.warning('请先登录'); return }
  yarnLoading.value = true
  try {
    const res = await axios.get('/api/v1/yarn/apps', {
      params: { clusterId: currentCluster.value, state: yarnStateFilter.value || undefined }
    })
    if (res.data.code === 0) {
      yarnAppList.value = res.data.data
    }
  } catch (e) {
    ElMessage.error('YARN 加载失败')
  } finally {
    yarnLoading.value = false
  }
}

const killYarnApp = async (appId) => {
  try {
    await axios.post('/api/v1/yarn/kill', null, { params: { clusterId: currentCluster.value, appId } })
    ElMessage.success('已发送 Kill 命令')
    loadYarnApps()
  } catch (e) {
    ElMessage.error('Kill 失败')
  }
}

const submitYarnApp = async () => {
  submitLoading.value = true
  try {
    const res = await axios.post('/api/v1/yarn/submit', null, {
      params: {
        clusterId: currentCluster.value,
        appName: submitForm.value.appName,
        queue: submitForm.value.queue
      }
    })
    if (res.data.code === 0) {
      ElMessage.success('提交成功! AppID: ' + res.data.data.appId)
      showSubmitDialog.value = false
      loadYarnApps()
    }
  } catch (e) {
    ElMessage.error('提交失败: ' + e.message)
  } finally {
    submitLoading.value = false
  }
}

// NEW: Queue Monitoring Functions
const loadQueueMetrics = async () => {
  try {
    const res = await axios.get('/api/v1/yarn/queues', {
      params: { clusterId: currentCluster.value }
    })
    if (res.data.code === 0) {
      queueData.value = res.data.data
      nextTick(() => {
        renderQueueCharts()
      })
    }
  } catch (e) {
    ElMessage.error('获取队列数据失败: ' + e.message)
  }
}

const renderQueueCharts = () => {
  if (!queueData.value || queueData.value.length === 0) return

  const queueNames = queueData.value.map(q => q.queueName)
  const usedCapacities = queueData.value.map(q => q.usedCapacity || 0)
  const numApps = queueData.value.map(q => q.numApplications || 0)

  // Capacity Usage Chart
  if (queueCapacityInstance) queueCapacityInstance.dispose()
  queueCapacityInstance = echarts.init(queueCapacityChart.value)
  queueCapacityInstance.setOption({
    title: { text: '队列容量使用率 (%)', left: 'center', top: 5 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: queueNames, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', max: 100 },
    series: [{
      name: '已使用容量',
      type: 'bar',
      data: usedCapacities,
      itemStyle: { color: '#67c23a' }
    }]
  })

  // Running Apps per Queue
  if (queueAppsInstance) queueAppsInstance.dispose()
  queueAppsInstance = echarts.init(queueAppsChart.value)
  queueAppsInstance.setOption({
    title: { text: '每队列 Running Apps 数量', left: 'center', top: 5 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: queueNames, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: [{
      name: 'Running Apps',
      type: 'bar',
      data: numApps,
      itemStyle: { color: '#409eff' }
    }]
  })
}

// Utils
const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatTime = (ts) => ts ? new Date(ts).toLocaleString() : '-'

const handleTabChange = (tab) => {
  if (tab.paneName === 'dashboard') refreshDashboard()
  if (tab.paneName === 'hdfs') loadHdfsFiles()
  if (tab.paneName === 'yarn') {
    loadYarnApps()
    loadQueueMetrics()  // auto load queue charts
  }
}

// Init
onMounted(() => {
  if (token.value) {
    refreshDashboard()
    loadHdfsFiles()
  }
  window.addEventListener('resize', () => {
    hdfsEchartInstance?.resize()
    yarnEchartInstance?.resize()
    queueCapacityInstance?.resize()
    queueAppsInstance?.resize()
  })
})
</script>

<style scoped>
.layout { height: 100vh; }
.header { background: #409eff; color: white; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; }
.header-left { display: flex; align-items: center; gap: 15px; }
.subtitle { font-size: 14px; opacity: 0.9; }
.footer { text-align: center; font-size: 12px; color: #909399; background: #f5f7fa; }
</style>