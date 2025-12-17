import { createRouter, createWebHistory } from 'vue-router'

// 定义路由规则
const routes = [
  {
    path: '/',
    name: 'Home',
    // 路由懒加载：访问时才加载该组件，提升首屏加载速度
    component: () => import('@/views/user/BusQuery.vue'),
    meta: {
      title: '公交查询系统'
    }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/AdminDashboard.vue'),
    meta: {
      title: '后台管理系统'
    }
  },
  // 捕获所有未定义路由，重定向到首页（可选）
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

// 创建路由实例
const router = createRouter({
  // 使用 HTML5 History 模式 (URL 中不带 #)
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 全局前置守卫：动态设置网页标题
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router