<template>
  <div class="app-container">
    <header v-if="isLoggedIn" class="app-header">
      <h1>S12ryt 平台控制台</h1>
      <nav>
        <RouterLink to="/apps">App 列表</RouterLink>
        <button @click="logout" class="btn-logout">登出</button>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, RouterLink, RouterView } from 'vue-router'

const router = useRouter()

const isLoggedIn = computed(() => !!localStorage.getItem('token'))

function logout() {
  const token = localStorage.getItem('token')
  if (token) {
    fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token }
    }).catch(() => {}).finally(() => {
      localStorage.removeItem('token')
      router.push('/login')
    })
  }
}
</script>
