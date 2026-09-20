<template>
  <div>
    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
      <RouterLink to="/apps" style="color: #7eb8da; text-decoration: none;">← 返回</RouterLink>
      <h2 style="color: #7eb8da;">{{ appId }} 日誌</h2>
      <label style="margin-left: auto; font-size: 0.85rem; color: #999;">
        自動更新
        <input type="checkbox" v-model="autoRefresh" />
      </label>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>

    <div v-if="loading" class="loading">載入中...</div>

    <div v-else-if="lines.length === 0" class="empty-state">
      沒有日誌
    </div>

    <div v-else class="log-viewer" ref="logContainer">
      <div v-for="(line, i) in lines" :key="i" class="log-line">{{ line }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api } from '../api'

const route = useRoute()
const appId = route.params.appId

const lines = ref([])
const loading = ref(true)
const error = ref('')
const autoRefresh = ref(true)
const logContainer = ref(null)

let pollTimer = null

async function loadLogs() {
  const res = await api.getAppLogs(appId)
  if (loading.value) loading.value = false
  if (res.ok) {
    lines.value = res.data.lines || []
    error.value = ''
  } else {
    error.value = `無法取得日誌 (${res.status})`
  }
  if (autoRefresh.value) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight
      }
    })
  }
}

watch(autoRefresh, (val) => {
  if (val) {
    startPolling()
  } else {
    stopPolling()
  }
})

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(loadLogs, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  loadLogs()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>
