<template>
  <!-- ... keep existing full template from previous versions ... -->

  <!-- Enhanced Alert Rules Section in YARN tab -->
  <div style="margin-top:15px; border-top:1px solid #eee; padding-top:10px">
    <div style="font-weight:bold; margin-bottom:8px">告警规则管理（完整持久化）</div>

    <el-form :model="newRule" inline size="small">
      <el-form-item><el-input v-model="newRule.queueName" placeholder="Queue" style="width:90px"/></el-form-item>
      <el-form-item>
        <el-select v-model="newRule.metric" placeholder="Metric" style="width:110px">
          <el-option label="usedCapacity" value="usedCapacity"/>
          <el-option label="numApplications" value="numApplications"/>
        </el-select>
      </el-form-item>
      <el-form-item><el-input v-model.number="newRule.threshold" placeholder="阈值" style="width:70px"/></el-form-item>
      <el-form-item><el-button type="primary" size="small" @click="addAlertRule">添加规则</el-button></el-form-item>
    </el-form>

    <el-table :data="alertRules" size="small" style="margin-top:8px" max-height="180px">
      <el-table-column prop="queueName" label="Queue" width="80"/>
      <el-table-column prop="metric" label="Metric" width="100"/>
      <el-table-column prop="threshold" label="Threshold" width="80"/>
      <el-table-column label="操作" width="80">
        <template #default="scope">
          <el-button size="small" type="danger" @click="deleteAlertRule(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-button type="warning" size="small" @click="checkAlerts" style="margin-top:8px">检查当前告警</el-button>
  </div>
</template>

<script setup>
// ... existing imports and state (including alertRules, newRule) ...

// Load rules from backend
const loadAlertRules = async () => {
  try {
    const res = await axios.get('/api/v1/yarn/alert-rules');
    if (res.data.code === 0) alertRules.value = res.data.data || [];
  } catch (e) { console.error(e); }
};

// Add rule with persistence
const addAlertRule = async () => {
  try {
    const res = await axios.post('/api/v1/yarn/alert-rules', newRule.value);
    if (res.data.code === 0) {
      ElMessage.success('规则已持久化保存');
      await loadAlertRules();
      newRule.value = { queueName: 'default', metric: 'usedCapacity', threshold: 80 };
    }
  } catch (e) { ElMessage.error('保存失败'); }
};

// Delete rule
const deleteAlertRule = async (id) => {
  try {
    await axios.delete(`/api/v1/yarn/alert-rules/${id}`);
    ElMessage.success('规则已删除');
    await loadAlertRules();
  } catch (e) { ElMessage.error('删除失败'); }
};

// Check alerts
const checkAlerts = async () => {
  try {
    const res = await axios.get('/api/v1/yarn/check-alerts', { params: { clusterId: currentCluster.value } });
    if (res.data.code === 0 && res.data.data?.length > 0) {
      ElMessage.warning(`触发 ${res.data.data.length} 条告警`);
    } else {
      ElMessage.success('当前无告警');
    }
  } catch (e) { ElMessage.error('检查失败'); }
};

// Make sure to call loadAlertRules() in onMounted and when switching to yarn tab
// ... existing code ...
</script>