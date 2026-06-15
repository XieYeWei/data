<template>
  <!-- Keep the existing full template structure -->
  <!-- In the YARN tab's queue section, use this enhanced version -->

  <el-col :span="12">
    <el-card header="YARN 队列监控 & 历史趋势">
      <!-- Time Filter -->
      <div style="margin-bottom:10px; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
        <el-date-picker
          v-model="historyDateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 320px"
          @change="loadQueueHistory"
        />
        <el-button type="primary" size="small" @click="loadQueueMetrics">刷新实时</el-button>
        <el-button type="info" size="small" @click="loadQueueHistory">加载历史</el-button>
      </div>

      <!-- Beautiful History Trend Line Chart -->
      <div ref="queueHistoryChart" style="height: 280px; margin-bottom: 15px;"></div>

      <!-- Real-time Charts -->
      <div ref="queueCapacityChart" style="height: 200px"></div>
      <div ref="queueAppsChart" style="height: 200px; margin-top: 10px"></div>

      <!-- Alert Rules Management with Edit -->
      <div style="margin-top:15px; border-top:1px solid #eee; padding-top:10px">
        <div style="font-weight:bold; margin-bottom:8px">告警规则管理</div>

        <el-form :model="newRule" inline size="small">
          <el-form-item><el-input v-model="newRule.queueName" placeholder="Queue" style="width:90px"/></el-form-item>
          <el-form-item>
            <el-select v-model="newRule.metric" placeholder="Metric" style="width:110px">
              <el-option label="usedCapacity" value="usedCapacity"/>
              <el-option label="numApplications" value="numApplications"/>
            </el-select>
          </el-form-item>
          <el-form-item><el-input v-model.number="newRule.threshold" placeholder="阈值" style="width:70px"/></el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" @click="addAlertRule">添加</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="alertRules" size="small" max-height="160px">
          <el-table-column prop="queueName" label="Queue" width="80"/>
          <el-table-column prop="metric" label="Metric" width="100"/>
          <el-table-column prop="threshold" label="Threshold" width="80"/>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button size="small" @click="editAlertRule(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteAlertRule(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-button type="warning" size="small" @click="checkAlerts" style="margin-top:8px">检查当前告警</el-button>
      </div>
    </el-card>
  </el-col>
</template>

<script setup>
// ... existing imports and state ...

// New/Enhanced State
const historyDateRange = ref([]);
let queueHistoryInstance = null;

const alertRules = ref([]);
const newRule = ref({ queueName: 'default', metric: 'usedCapacity', threshold: 80 });

// Beautiful History Chart with Multi-Queue Support
const renderQueueHistoryChart = (historyData) => {
  if (queueHistoryInstance) queueHistoryInstance.dispose();
  queueHistoryInstance = echarts.init(queueHistoryChart.value);

  const queueMap = {};
  historyData.forEach(item => {
    try {
      const extra = JSON.parse(item.extraJson || '{}');
      const qName = extra.queueName || 'unknown';
      if (!queueMap[qName]) queueMap[qName] = [];
      queueMap[qName].push({
        time: new Date(item.createTime).toLocaleTimeString(),
        value: parseFloat(extra.usedCapacity) || 0
      });
    } catch(e) {}
  });

  const series = Object.keys(queueMap).map(qName => ({
    name: qName,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 3 },
    areaStyle: { opacity: 0.15 },
    emphasis: { focus: 'series' },
    data: queueMap[qName].map(d => d.value)
  }));

  const times = historyData.length > 0 ? historyData.map(h => new Date(h.createTime).toLocaleTimeString()) : [];

  queueHistoryInstance.setOption({
    title: { text: '队列容量使用历史趋势 (%)', left: 'center', top: 8 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 32, data: Object.keys(queueMap) },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: times },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: series.length > 0 ? series : [{ name: 'No Data', type: 'line', data: [] }]
  });
};

// Load history with time filter support
const loadQueueHistory = async () => {
  try {
    const params = { clusterId: currentCluster.value, module: 'queue', limit: 100 };
    if (historyDateRange.value && historyDateRange.value.length === 2) {
      params.startTime = historyDateRange.value[0].toISOString().slice(0, 19);
      params.endTime = historyDateRange.value[1].toISOString().slice(0, 19);
    }
    const res = await axios.get('/api/v1/metrics/history', { params });
    if (res.data.code === 0) {
      renderQueueHistoryChart(res.data.data || []);
    }
  } catch (e) {
    ElMessage.error('加载历史数据失败');
  }
};

// Alert Rule Edit (simple prompt-based for demo, can be replaced with dialog)
const editAlertRule = (rule) => {
  const newThreshold = prompt(`Edit threshold for ${rule.queueName} (${rule.metric}):`, rule.threshold);
  if (newThreshold !== null) {
    const updatedRule = { ...rule, threshold: parseFloat(newThreshold) };
    updateAlertRule(updatedRule);
  }
};

const updateAlertRule = async (rule) => {
  try {
    const res = await axios.put(`/api/v1/yarn/alert-rules/${rule.id}`, rule);
    if (res.data.code === 0) {
      ElMessage.success('规则已更新');
      await loadAlertRules();
    }
  } catch (e) {
    ElMessage.error('编辑失败');
  }
};

// Make sure to call loadAlertRules() when needed
// ... existing code ...
</script>