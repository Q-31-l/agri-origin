import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: '/home',
        name: 'Home',
        component: () => import('@/pages/home.vue'),
        meta: { title: '工作台', tabbar: true }
      },
      {
        path: '/batches',
        name: 'Batches',
        component: () => import('@/pages/batches.vue'),
        meta: { title: '我的批次', tabbar: true }
      },
      {
        path: '/planting',
        name: 'Planting',
        component: () => import('@/pages/planting.vue'),
        meta: { title: '农事记录' }
      },
      {
        path: '/harvest',
        name: 'Harvest',
        component: () => import('@/pages/harvest.vue'),
        meta: { title: '采收提交' }
      },
      {
        path: '/mine',
        name: 'Mine',
        component: () => import('@/pages/mine.vue'),
        meta: { title: '我的', tabbar: true }
      },
      {
        path: '/scan',
        name: 'Scan',
        component: () => import('@/pages/trace.vue'),
        meta: { title: '扫码溯源', tabbar: true }
      }
    ]
  },
  {
    path: '/trace',
    name: 'Trace',
    component: () => import('@/pages/trace.vue'),
    meta: { public: true, title: '扫码溯源' }
  },
  {
    path: '/batch/:id',
    name: 'BatchDetail',
    component: () => import('@/pages/batch-detail.vue'),
    meta: { title: '批次详情' }
  },
  {
    path: '/planting/new/:batchId',
    name: 'PlantingForm',
    component: () => import('@/pages/planting-form.vue'),
    meta: { title: '新增农事记录' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/trace'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.meta.public) {
    return next()
  }
  if (!store.token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  next()
})

export default router
