// src/router/guards.js
import { useUserStore, useWalletStore } from '@/stores'
import { ElMessage } from 'element-plus'

export function setupRouterGuards(router) {
  router.beforeEach(async (to, from, next) => {
   document.title = to.meta.title ? `${to.meta.title} - 去中心化交易所` : '去中心化交易所'

   const token = localStorage.getItem('token')
   const userStore = useUserStore()
   const walletStore = useWalletStore()

    if (to.meta.requiresAuth && !token) {
      ElMessage.warning('请先登录')
      next('/login')
     return
    }

      if (to.path === '/login' && token) {
          // 先验证 token 是否有效，无效则清除并留在登录页
          try {
              const address = localStorage.getItem('wallet_address') || token
              await userStore.fetchUserInfo(address)
              // token 有效，跳转到首页
              next('/home')
              return
          } catch (error) {
              console.error('Token validation failed:', error)
              // token 无效，清除并允许访问登录页
              localStorage.removeItem('token')
          }
      }

      if (to.meta.requiresAuth && token && !userStore.userInfo) {
          try {
              const address = localStorage.getItem('wallet_address') || token
              await userStore.fetchUserInfo(address)

              if (!walletStore.isConnected) {
                  if (address) {
                      walletStore.setWallet(address)
                  }
              }
          } catch (error) {
              console.error('Load user info failed:', error)
              // 如果是 401 错误，说明 token 无效，跳转到登录页
              if (error.response?.status === 401) {
                  localStorage.removeItem('token')
                  next('/login')
                  return
              }
              // 其他错误继续访问页面
          }
      }


      next()
  })

    router.afterEach((to, from) => {
        // scrollBehavior 已经处理了滚动，这里只做清理工作
        if (from.path !== to.path) {
            const loading = document.querySelector('.page-loading')
            if (loading) {
                loading.remove()
            }
        }
    })
}
