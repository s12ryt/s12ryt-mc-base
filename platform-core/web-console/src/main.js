import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import AppsView from './views/AppsView.vue'
import LogsView from './views/LogsView.vue'
import './assets/style.css'

const router = createRouter({
  history: createWebHistory('/console/'),
  routes: [
    { path: '/', redirect: '/apps' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/apps', name: 'apps', component: AppsView, meta: { requiresAuth: true } },
    { path: '/apps/:appId/logs', name: 'logs', component: LogsView, meta: { requiresAuth: true } }
  ]
})

// 路由守衛：未登入導向登入頁
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.name === 'login' && token) {
    next('/apps')
  } else {
    next()
  }
})

createApp(App).use(router).mount('#app')
