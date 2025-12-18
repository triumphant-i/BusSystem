import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        // 重要修改：如果你是在同一台电脑上跑前后端，请必须使用 localhost
        target: 'http://localhost:8080', 
        changeOrigin: true,
      }
    }
  }
})