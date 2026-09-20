import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
// build 輸出到 dist/，由 Gradle 打包進 plugin.jar 的 resources/web-console/
export default defineConfig({
  plugins: [vue()],
  base: '/console/',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/apps': 'http://localhost:8080'
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
