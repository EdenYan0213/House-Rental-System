<template>
  <div class="page-remind">
    <h2>催缴规则配置</h2>
    <div>
      <label>逾期天数</label>
      <input v-model.number="newRule.overdueDays" type="number" />
      <label>发送时间(HH:mm)</label>
      <input v-model="newRule.sendTime" />
      <label>模板</label>
      <input v-model="newRule.smsTemplate" style="width:60%" />
      <button @click="addRule">新增规则</button>
    </div>

    <ul>
      <li v-for="r in rules" :key="r.id">{{ r.overdueDays }}天 @{{ r.sendTime }} - {{ r.smsTemplate }}</li>
    </ul>

    <h2>短信通道配置</h2>
    <div>
      <label>AccessKey</label>
      <input v-model="smsConfig.accessKey" />
      <label>AccessSecret</label>
      <input v-model="smsConfig.accessSecret" />
      <label>签名</label>
      <input v-model="smsConfig.signature" />
      <label>模板CODE</label>
      <input v-model="smsConfig.templateCode" />
      <label>调试模式</label>
      <input type="checkbox" v-model="smsConfig.debugMode" />
      <button @click="saveSmsConfig">保存配置</button>
      <button @click="testSend">测试发送(仅管理员)</button>
      <button @click="triggerNow">手动触发催缴</button>
    </div>

    <h2>催缴记录</h2>
    <div style="margin-bottom:8px">
      <button @click="exportCsv">导出CSV</button>
    </div>
    <table class="remind-table">
      <thead>
        <tr><th>时间</th><th>租户</th><th>手机号</th><th>房间</th><th>逾期天数</th><th>金额</th><th>状态</th></tr>
      </thead>
      <tbody>
        <tr v-for="l in logs" :key="l.id">
          <td>{{ l.createdAt }}</td>
          <td>{{ l.tenantName }}</td>
          <td>{{ l.phone }}</td>
          <td>{{ l.roomNumber }}</td>
          <td>{{ l.overdueDays }}</td>
          <td>{{ l.totalAmount }}</td>
          <td>{{ l.status }} {{ l.isDebug ? '（调试）' : '' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { getRemindRules, saveRemindRule, getSmsConfig, saveSmsConfig, getRemindLogs, testSendSms, triggerRemindNow } from '../api/remind'

export default {
  data() {
    return {
      rules: [],
      newRule: { overdueDays: 1, sendTime: '09:00', smsTemplate: '' },
      smsConfig: { accessKey: '', accessSecret: '', signature: '', templateCode: '', debugMode: true },
      logs: []
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const r = await getRemindRules()
        this.rules = r.data
      } catch (e) {}
      try {
        const c = await getSmsConfig()
        if (c.data && c.data.length) this.smsConfig = c.data[0]
      } catch (e) {}
      try {
        const l = await getRemindLogs()
        this.logs = l.data
      } catch (e) {}
    },
    async addRule() {
      await saveRemindRule(this.newRule)
      this.newRule.smsTemplate = ''
      this.load()
    },
    async saveSmsConfig() {
      await saveSmsConfig(this.smsConfig)
      alert('保存成功')
      this.load()
    },
    async testSend() {
      const payload = {
        tenantName: '测试用户',
        phone: '13800000000',
        roomNumber: 'A-101',
        overdueDays: 1,
        billIds: JSON.stringify([1]),
        totalAmount: 100.00,
        content: '测试催缴短信',
      }
      try {
        const res = await testSendSms(payload)
        const isDebug = !!res?.data?.isDebug
        alert(isDebug ? '短信已发送（模拟）' : '短信已发送')
        this.load()
      } catch (e) { alert('发送失败：' + e.message) }
    }
    ,
    async triggerNow() {
      try {
        const res = await triggerRemindNow()
        const data = res?.data || {}
        const sentCount = data.sentCount ?? 0
        const latestTitle = data.latestMessageTitle || '-'
        const latestContent = data.latestMessageContent || '-'
        alert(`已触发催缴任务\n发送数量: ${sentCount}\n最新消息: ${latestTitle}\n${latestContent}`)
        this.load()
      } catch (e) {
        alert('触发失败：' + (e.response?.data?.message || e.message))
      }
    },
    exportCsv() {
      if (!this.logs || !this.logs.length) { alert('无数据可导出'); return }
      const headers = ['时间','租户','手机号','房间','逾期天数','金额','状态','调试'];
      const rows = this.logs.map(r => [r.createdAt, r.tenantName, r.phone, r.roomNumber, r.overdueDays, r.totalAmount, r.status, r.isDebug ? '是' : '否']);
      const csv = [headers, ...rows].map(r => r.map(c => '"'+String(c ?? '')+'"').join(',')).join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `remind_logs_${new Date().toISOString().slice(0,10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    }
  }
}
</script>

<style scoped>
.page-remind label { margin-right:8px }
.remind-table { width:100%; border-collapse:collapse }
.remind-table th, .remind-table td { border:1px solid #ddd; padding:8px }
.remind-table th { background:#f6f8fa }
</style>
