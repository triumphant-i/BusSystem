import { createApp } from 'vue'
import App from './App.vue'
import router from './router' // 引入刚才创建的路由配置

// 引入 Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(router) // 挂载路由
app.use(ElementPlus) // 挂载 Element Plus

app.mount('#app')