<!-- src/layouts/DefaultLayout.vue -->
<template>
  <el-container class="layout-container">
    <el-header class="header">
      <!-- 通知图标 -->
      <el-badge
          :value="unreadCount"
          :hidden="unreadCount === 0"
          class="notification-badge"
      >
        <el-icon
            class="notification-icon"
            @click="$router.push('/notifications')"
        >
          <Bell />
        </el-icon>
      </el-badge>

      <div class="header-left">
        <div class="logo" @click="$router.push('/home')">
          <div class="logo-icon">🏦</div>
          <span>去中心化交易所</span>
        </div>

        <div class="nav-menu">
          <el-button
              text
              :type="$route.path === '/home' ? 'primary' : 'default'"
              @click="$router.push('/home')"
          >
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-button>

          <el-button
              text
              :type="$route.path.startsWith('/trade/request') ? 'primary' : 'default'"
              @click="$router.push('/trade/request')"
          >
            <el-icon><Connection /></el-icon>
            <span>交易</span>
          </el-button>

          <el-button
              text
              :type="$route.path.startsWith('/trade/list') ? 'primary' : 'default'"
              @click="$router.push('/trade/list')"
          >
            <el-icon><Document /></el-icon>
            <span>记录</span>
          </el-button>

          <el-button
              text
              :type="$route.path === '/airdrop' ? 'primary' : 'default'"
              @click="$router.push('/airdrop')"
          >
            <el-icon><Coin /></el-icon>
            <span>空投</span>
          </el-button>

          <el-button
              text
              :type="$route.path === '/dao' ? 'primary' : 'default'"
              @click="$router.push('/dao')"
          >
            <el-icon><Setting /></el-icon>
            <span>治理</span>
          </el-button>

          <el-button
              text
              :type="$route.path === '/arbitration' ? 'primary' : 'default'"
              @click="$router.push('/arbitration')"
          >
            <el-icon><Stamp /></el-icon>
            <span>仲裁</span>
          </el-button>
        </div>
      </div>

      <div class="header-right">
        <el-dropdown v-if="userStore.userInfo">
          <div class="user-info">
            <el-avatar :size="32" icon="User" />
            <span class="address">{{ formatAddress(walletStore.address) }}</span>
            <el-tag size="small" :type="userStore.userTypeTag">
              {{ userStore.userTypeText }}
            </el-tag>
          </div>

          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="$router.push('/profile')">
                <el-icon><User /></el-icon>
                个人中心
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button
            v-else
            type="primary"
            size="small"
            @click="$router.push('/login')"
        >
          登录
        </el-button>
      </div>
    </el-header>

    <el-main class="main-content">
      <el-scrollbar>
        <router-view />
      </el-scrollbar>
    </el-main>

    <el-footer class="footer">
      <div class="footer-content">
        <p>&copy; 2024 去中心化交易所。All rights reserved.</p>
        <div class="footer-links">
          <a href="#" target="_blank">关于我们</a>
          <a href="#" target="_blank">使用条款</a>
          <a href="#" target="_blank">隐私政策</a>
        </div>
      </div>
    </el-footer>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore, useWalletStore } from '@/stores'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  House, Connection, Document, Coin, User, SwitchButton, Setting, Bell, Stamp
} from '@element-plus/icons-vue'
import { wsClient } from '@/utils/websocket'
import { getNotifications } from '@/api/notification'

const router = useRouter()
const userStore = useUserStore()
const walletStore = useWalletStore()
const unreadCount = ref(0)

// 格式化地址显示（缩短中间部分）
function formatAddress(address) {
  if (!address) return ''
  return `${address.slice(0, 6)}...${address.slice(-4)}`
}

const activeMenu = computed(() => {
  const path = router.currentRoute.value.path

  if (path.startsWith('/trade')) {
    return '/trade'
  }

  return path
})

// ✅ 加载未读数
async function loadUnreadCount() {
  if (!walletStore.address) {
    unreadCount.value = 0
    return
  }

  try {
    const res = await getNotifications({ unreadOnly: true })
    unreadCount.value = (res.data || []).length
  } catch (error) {
    console.error('Failed to load unread count:', error)
  }
}

// ✅ 监听 WebSocket 通知
function setupNotificationListener() {
  // 监听全局自定义事件（当 websocket.js 收到通知时触发）
  window.addEventListener('notification-received', () => {
    console.log('🔔 New notification received')
    loadUnreadCount()

    // 显示提示
    ElMessage.success('收到新消息通知')
  })
}

onMounted(() => {
  // 连接 WebSocket
  if (walletStore.address) {
    wsClient.connect(walletStore.address)
  }

  // 加载未读数
  loadUnreadCount()

  // 设置通知监听
  setupNotificationListener()
})

onUnmounted(() => {
  // 组件卸载时断开连接
  wsClient.disconnect()

  // 移除事件监听
  window.removeEventListener('notification-received', handleNotification)
})

function handleNotification(event) {
  console.log('🔔 New notification received')
  loadUnreadCount()
}


// ✅ 监听钱包地址变化
watch(() => walletStore.address, (newAddress) => {
  if (newAddress) {
    wsClient.connect(newAddress)
    loadUnreadCount()
  } else {
    wsClient.disconnect()
    unreadCount.value = 0
  }
})

function handleLogout() {
  ElMessageBox.confirm(
      '确定要退出登录吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('wallet_address')
    userStore.reset()
    walletStore.disconnect()
    wsClient.disconnect()
    router.push('/login')
  })
}
</script>

<style lang="scss" scoped>.layout-container {
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 0 24px;

  // ✅ 添加通知图标样式
  .notification-badge {
    margin-right: 20px;
    cursor: pointer;

    .notification-icon {
      font-size: 20px;
      color: #606266;

      &:hover {
        color: #409EFF;
      }
    }
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .logo {
      display: flex;
      align-items: center;
      gap: 12px;
      cursor: pointer;
      flex-shrink: 0;

      .logo-icon {
        font-size: 32px;
      }

      span {
        font-size: 18px;
        font-weight: 600;
        color: #409EFF;
      }
    }

    .nav-menu {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
      margin-left: 16px;

      :deep(.el-button) {
        padding: 8px 12px;
        font-size: 14px;
        border: none;
        background: transparent;
        color: #606266;
        transition: all 0.3s;

        &:hover {
          background: #f5f7fa;
          color: #409EFF;
        }

        &.is-primary {
          color: #409EFF;
          background: #ecf5ff;
        }

        .el-icon {
          margin-right: 4px;
        }
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
    margin-left: auto;

    .user-info {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      max-width: 200px;

      .address {
        font-size: 13px;
        color: #606266;
        white-space: nowrap;
      }

      .el-tag {
        flex-shrink: 0;
      }
    }
  }
}

.main-content {
  background: #f5f7fa;
  padding: 20px;
  min-height: calc(100vh - 120px);
}

.footer {
  background: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 16px 24px;

  .footer-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    p {
      margin: 0;
      color: #909399;
      font-size: 14px;
    }

    .footer-links {
      display: flex;
      gap: 24px;

      a {
        color: #606266;
        text-decoration: none;
        font-size: 14px;

        &:hover {
          color: #409EFF;
        }
      }
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>