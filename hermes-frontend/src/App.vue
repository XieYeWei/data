<template>
  <div class="common-layout">
    <el-container>
      <el-header>
        <div class="header">
          <h1>Hermes - 企业级 Hadoop 集群管理平台</h1>
          <el-tag type="success">v0.0.1 (Milestone 1)</el-tag>
        </div>
      </el-header>
      <el-main>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card header="集群切换">
              <el-select v-model="clusterId" placeholder="选择集群" style="width: 100%" @change="loadFiles">
                <el-option label="Cluster 1 (demo)" value="cluster1" />
                <!-- Add more from backend cluster API later -->
              </el-select>
              <p style="margin-top: 10px; color: #909399; font-size: 12px;">
                请在 backend application.yml 中配置实际 NameNode 地址
              </p>
            </el-card>

            <el-card header="快速操作" style="margin-top: 20px;">
              <el-button type="primary" @click="loadFiles"> 刷新文件列表 </el-button>
              <el-button @click="getSummary"> 查看存储摘要 </el-button>
            </el-card>
          </el-col>

          <el-col :span="18">
            <el-card header="HDFS 文件浏览器 (Read-Only Demo)">
              <el-input v-model="currentPath" placeholder="路径，如 /user" style="width: 300px; margin-right: 10px;" @keyup.enter="loadFiles" />
              <el-button type="primary" @click="loadFiles">进入</el-button>
              <el-button @click="goParent"> 上级目录 </el-button>

              <el-table :data="fileList" style="margin-top: 20px;" v-loading="loading" stripe>
                <el-table-column prop="name" label="名称" min-width="200">
                  <template #default="scope">
                    <el-link v-if="scope.row.isDirectory" type="primary" @click="enterDir(scope.row.name)">
                      📁 {{ scope.row.name }}
                    </el-link>
                    <span v-else>📄 {{ scope.row.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="isDirectory" label="类型" width="80">
                  <template #default="scope">{{ scope.row.isDirectory ? '目录' : '文件' }}</template>
                </el-table-column>
                <el-table-column prop="length" label="大小" width="120">
                  <template #default="scope">{{ formatSize(scope.row.length) }}</template>
                </el-table-column>
                <el-table-column prop="owner" label="所有者" width="100" />
                <el-table-column prop="permission" label="权限" width="100" />
                <el-table-column prop="modificationTime" label="修改时间" width="180">
                  <template #default="scope">{{ new Date(scope.row.modificationTime).toLocaleString() }}</template>
                </el-table-column>
              </el-table>

              <div style="margin-top: 20px; color: #606266;">
                当前路径: <strong>{{ currentPath }}</strong> | 来自集群: {{ clusterId }}
              </div>
            </el-card>

            <el-card v-if="summaryData" header="存储摘要" style="margin-top: 20px;">
              <pre>{{ JSON.stringify(summaryData, null, 2) }}</pre>
            </el-card>
          </el-col>
        </el-row>
      </el-main>
      <el-footer>
        <div style="text-align: center; color: #909399; font-size: 12px;">
          Powered by Grok + Apache Hadoop Official Client | 基于 HDFS FileSystem / DistributedFileSystem API
        </div>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const clusterId = ref('cluster1')
const currentPath = ref('/')
const fileList = ref([])
const loading = ref(false)
const summaryData = ref(null)

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/v1/hdfs/list', {
      params: { clusterId: clusterId.value, path: currentPath.value }
    })
    if (res.data.code === 0) {
      fileList.value = res.data.data
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('Backend error: ' + (e.response?.data?.msg || e.message) + ' - 请确保后端运行且配置正确 NameNode')
  } finally {
    loading.value = false
  }
}

const getSummary = async () => {
  try {
    const res = await axios.get('/api/v1/hdfs/summary', {
      params: { clusterId: clusterId.value, path: currentPath.value }
    })
    if (res.data.code === 0) {
      summaryData.value = res.data.data
    }
  } catch (e) {
    ElMessage.error('Summary error')
  }
}

const enterDir = (name) => {
  if (currentPath.value === '/') {
    currentPath.value = '/' + name
  } else {
    currentPath.value = currentPath.value + '/' + name
  }
  loadFiles()
}

const goParent = () => {
  if (currentPath.value === '/' || currentPath.value === '') return
  const parts = currentPath.value.split('/').filter(Boolean)
  parts.pop()
  currentPath.value = parts.length ? '/' + parts.join('/') : '/'
  loadFiles()
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

onMounted(() => {
  loadFiles()
})
</script>

<style>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>