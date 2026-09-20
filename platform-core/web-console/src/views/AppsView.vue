<template>
  <div>
    <h2 style="margin-bottom: 16px; color: #7eb8da;">應用列表</h2>

    <div v-if="loading" class="loading">載入中...</div>

    <div v-else-if="apps.length === 0" class="empty-state">
      目前沒有已載入的 App。放置 .jar 檔到 apps/ 目錄後重新啟動伺服器即可。
    </div>

    <div v-else class="app-grid">
      <div v-for="app in apps" :key="app.id" class="card">
        <div class="card-header">
          <h2>{{ app.name || app.id }}</h2>
          <span
            class="status-badge"
            :class="app.state === 'RUNNING' ? 'status-running' : 'status-failed'"
          >
            {{ app.state }}
          </span>
        </div>
        <div style="margin-bottom: 12px; color: #999; font-size: 0.85rem;">
          <div>ID: {{ app.id }}</div>
          <div>版本: {{ app.version }}</div>
          <div v-if="app.jar">來源: {{ app.jar }}</div>
          <div v-if="app.error" style="color: #f44336;">錯誤: {{ app.error }}</div>
        </div>
        <div class="actions">
          <button
            class="btn btn-warning"
            @click="reloadApp(app.id)"
            :disabled="reloading[app.id]"
          >
            {{ reloading[app.id] ? '重載中...' : '重載' }}
          </button>
          <button
            class="btn btn-danger"
            @click="unloadApp(app.id)"
            :disabled="unloading[app.id]"
          >
            {{ unloading[app.id] ? '卸載中...' : '卸載' }}
          </button>
          <RouterLink
            v-if="app.state === 'RUNNING'"
            :to="`/apps/${app.id}/logs`"
            class="btn"
            style="background: #333; color: #ccc; text-decoration: none; display: inline-block;"
          >
            查看日誌
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api'

const apps = ref([])
const loading = ref(true)
const reloading = reactive({})
const unloading = reactive({})

async function loadApps() {
  loading.value = true
  const res = await api.listApps()
  loading.value = false
  if (res.ok) {
    apps.value = res.data
  }
}

async function reloadApp(appId) {
  reloading[appId] = true
  await api.reloadApp(appId)
  reloading[appId] = false
  await loadApps()
}

async function unloadApp(appId) {
  unloading[appId] = true
  await api.unloadApp(appId)
  unloading[appId] = false
  await loadApps()
}

onMounted(() => {
  loadApps()
})
</script>
