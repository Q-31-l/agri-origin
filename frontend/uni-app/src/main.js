import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { setupStore } from '@/store'
import perm from '@/directives/perm'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = Pinia.createPinia()
  app.use(pinia)
  setupStore(pinia)
  app.directive('perm', perm)
  return { app, Pinia }
}
