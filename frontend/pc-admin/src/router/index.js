import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import Layout from '@/layout/index.vue'

export const menuRoutes = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '数据看板', icon: 'DataLine' }
  },
  {
    path: '/batch',
    name: 'Batch',
    component: () => import('@/views/batch/list.vue'),
    meta: { title: '批次管理', icon: 'Box', perm: 'batch:list' }
  },
  {
    path: '/planting',
    name: 'Planting',
    component: () => import('@/views/planting/index.vue'),
    meta: { title: '农事记录', icon: 'Cherry', perm: 'planting:list' }
  },
  {
    path: '/harvest',
    name: 'Harvest',
    component: () => import('@/views/harvest/index.vue'),
    meta: { title: '采收登记', icon: 'Grape', perm: 'harvest:list' }
  },
  {
    path: '/quality',
    name: 'Quality',
    component: () => import('@/views/quality/index.vue'),
    meta: { title: '质量检测', icon: 'DocumentChecked', perm: 'quality:list' }
  },
  {
    path: '/logistics',
    name: 'Logistics',
    component: () => import('@/views/logistics/index.vue'),
    meta: { title: '物流运输', icon: 'Van', perm: 'logistics:list' }
  },
  {
    path: '/sales',
    name: 'Sales',
    component: () => import('@/views/sales/index.vue'),
    meta: { title: '销售管理', icon: 'Shop', perm: 'sales:list' }
  },
  {
    path: '/abnormal',
    name: 'Abnormal',
    component: () => import('@/views/abnormal/index.vue'),
    meta: { title: '异常管控', icon: 'Warning', perm: 'abnormal:list' }
  },
  {
    path: '/trace',
    name: 'Trace',
    component: () => import('@/views/trace/index.vue'),
    meta: { title: '溯源查询', icon: 'Search' }
  },
  {
    path: '/chain',
    name: 'Chain',
    component: () => import('@/views/chain/index.vue'),
    meta: { title: '链上存证', icon: 'Link', perm: 'chain:list' }
  },
  {
    path: '/system',
    name: 'System',
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting', perm: 'user:list' },
    children: [
      {
        path: '/system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '用户与角色', perm: 'user:list' }
      },
      {
        path: '/system/farm',
        name: 'SystemFarm',
        component: () => import('@/views/system/farm.vue'),
        meta: { title: '农场档案', perm: 'farm:list' }
      },
      {
        path: '/system/product',
        name: 'SystemProduct',
        component: () => import('@/views/system/product.vue'),
        meta: { title: '产品品类', perm: 'product:list' }
      },
      {
        path: '/system/log',
        name: 'SystemLog',
        component: () => import('@/views/system/log.vue'),
        meta: { title: '操作日志', perm: 'log:list' }
      }
    ]
  }
]

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      ...menuRoutes,
      {
        path: '/batch/:id',
        name: 'BatchDetail',
        component: () => import('@/views/batch/detail.vue'),
        meta: { title: '批次详情', hidden: true, perm: 'batch:detail' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.meta.public) {
    if (to.path === '/login' && store.token) {
      return next('/dashboard')
    }
    return next()
  }
  if (!store.token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (to.meta.perm && !store.hasPerm(to.meta.perm)) {
    ElMessage.error('无权访问该页面，已阻止越权跳转')
    return next('/403')
  }
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '农产品质量安全溯源系统'
  next()
})

export default router
