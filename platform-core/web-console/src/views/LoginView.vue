<template>
  <div class="card" style="max-width: 400px; margin: 80px auto;">
    <h2 style="margin-bottom: 20px; color: #7eb8da;">管理員登入</h2>
    <form @submit.prevent="handleLogin">
      <input
        v-model="password"
        type="password"
        class="input"
        placeholder="密碼"
        :disabled="loading"
        autofocus
      />
      <button
        type="submit"
        class="btn btn-primary"
        style="width: 100%; margin-top: 12px;"
        :disabled="loading || !password"
      >
        {{ loading ? '登入中...' : '登入' }}
      </button>
      <div v-if="error" class="error-message">{{ error }}</div>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  const res = await api.login(password.value)
  loading.value = false
  if (res.ok && res.data.token) {
    localStorage.setItem('token', res.data.token)
    router.push('/apps')
  } else {
    error.value = '密碼錯誤'
    password.value = ''
  }
}
</script>
