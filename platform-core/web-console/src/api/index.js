// API 工具層：封裝所有後端 API 呼叫

const BASE = ''

function getToken() {
  return localStorage.getItem('token')
}

function authHeaders() {
  const token = getToken()
  return token ? { 'Authorization': 'Bearer ' + token } : {}
}

async function request(method, path, body = null) {
  const opts = {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    }
  }
  if (body !== null) {
    opts.body = JSON.stringify(body)
  }
  const res = await fetch(BASE + path, opts)
  const data = await res.json().catch(() => ({}))
  return { ok: res.ok, status: res.status, data }
}

export const api = {
  // 認證
  login(password) {
    return request('POST', '/api/auth/login', { password })
  },
  logout() {
    return request('POST', '/api/auth/logout')
  },
  changePassword(oldPassword, newPassword) {
    return request('POST', '/api/auth/change-password', { oldPassword, newPassword })
  },

  // App 管理
  listApps() {
    return request('GET', '/api/apps')
  },
  reloadApp(appId) {
    return request('POST', `/api/apps/${appId}/reload`)
  },
  unloadApp(appId) {
    return request('POST', `/api/apps/${appId}/unload`)
  },
  getAppLogs(appId, maxLines = 200) {
    return request('GET', `/api/apps/${appId}/logs?maxLines=${maxLines}`)
  },

  // 健康檢查
  health() {
    return request('GET', '/api/health')
  }
}
