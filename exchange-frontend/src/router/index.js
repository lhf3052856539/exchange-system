// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import { setupRouterGuards } from './guards'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
     return savedPosition
    } else {
     return { top: 0 }
    }
  }
})

setupRouterGuards(router)

export default router
