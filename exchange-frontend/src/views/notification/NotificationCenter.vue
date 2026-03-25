<!-- src/views/notification/NotificationCenter.vue -->
<template>
  <div class="notification-center">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <!-- ✅ 添加返回按钮 -->
            <el-button
                size="small"
                @click="handleBack"
                class="back-btn"
            >
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span class="page-title">📬 消息中心</span>
          </div>
          <div class="actions">
            <el-button
                size="small"
                @click="handleRefresh"
                :loading="loading"
            >
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button
                size="small"
                @click="handleMarkAllRead"
                :disabled="unreadCount === 0"
            >
              全部已读
            </el-button>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0">
              <el-button size="small" @click="filterUnread = !filterUnread">
                {{ filterUnread ? '查看全部' : '只看未读' }}
              </el-button>
            </el-badge>
          </div>
        </div>
      </template>

      <!-- 通知列表 -->
      <el-table
          :data="notifications"
          v-loading="loading"
          :empty-text="filterUnread ? '暂无未读消息' : '暂无消息'"
          style="width: 100%"
      >
        <el-table-column
            prop="title"
            label="标题"
            min-width="200"
        >
          <template #default="{ row }">
            <div class="notification-title" :class="{ 'unread': !row.isRead }">
              {{ row.title }}
            </div>
          </template>
        </el-table-column>

        <el-table-column
            prop="content"
            label="内容"
            min-width="300"
            show-overflow-tooltip
        />

        <el-table-column
            label="类型"
            width="100"
        >
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.type)" size="small">
              {{ getTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
            label="时间"
            width="180"
        >
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column
            label="操作"
            width="150"
            fixed="right"
        >
          <template #default="{ row }">
            <el-button
                v-if="!row.isRead"
                type="primary"
                size="small"
                link
                @click="handleMarkAsRead(row)"
            >
              标记已读
            </el-button>
            <el-button
                type="danger"
                size="small"
                link
                @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { getNotifications, markAsRead, markAllAsRead, deleteNotification } from '@/api/notification'
import { useWalletStore } from '@/stores'

const router = useRouter()
const walletStore = useWalletStore()
const loading = ref(false)
const notifications = ref([])
const filterUnread = ref(false)
const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

const unreadCount = computed(() => {
  return notifications.value.filter(n => !n.isRead).length
})

// 获取通知列表
async function loadNotifications() {
  try {
    loading.value = true

    console.log('🔍 开始加载通知列表...')
    console.log('当前钱包地址:', walletStore.address)
    console.log('筛选条件:', filterUnread.value ? '只看未读' : '查看全部')

    const res = await getNotifications({
      unreadOnly: filterUnread.value || null
    })

    console.log('📬 API 返回数据:', res)
    console.log('📬 数据类型:', Array.isArray(res) ? 'Array' : typeof res)

    // ✅ res 已经是数组了（因为 axios 拦截器返回了 response.data.data）
    const notificationsArray = Array.isArray(res) ? res : []

    console.log('📬 通知数量:', notificationsArray.length)

    if (notificationsArray.length > 0) {
      console.log('📬 第一条通知:', notificationsArray[0])
    }

    notifications.value = notificationsArray
    pagination.value.total = notificationsArray.length

    console.log('✅ 通知加载完成，共', notifications.value.length, '条')
  } catch (error) {
    console.error('❌ Failed to load notifications:', error)
    ElMessage.error('加载通知失败')
  } finally {
    loading.value = false
  }
}

// 刷新
function handleRefresh() {
  loadNotifications()
}

// 标记为已读
async function handleMarkAsRead(row) {
  try {
    await markAsRead(row.id)
    row.isRead = true
    row.readTime = new Date()
    ElMessage.success('已标记为已读')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 全部已读
async function handleMarkAllRead() {
  try {
    await markAllAsRead()
    notifications.value.forEach(n => n.isRead = true)
    ElMessage.success('全部标记为已读')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除通知
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除这条消息吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteNotification(row.id)
    notifications.value = notifications.value.filter(n => n.id !== row.id)
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 切换筛选
function filterUnreadChanged() {
  loadNotifications()
}

// 分页
function handleSizeChange() {
  loadNotifications()
}

function handlePageChange() {
  loadNotifications()
}

// 工具函数
function getTypeTag(type) {
  const tags = {
    1: 'success',    // 交易通知
    2: 'info',       // 系统通知
    3: 'warning'     // 争议通知
  }
  return tags[type] || 'info'
}

function getTypeText(type) {
  const texts = {
    1: '交易',
    2: '系统',
    3: '争议'
  }
  return texts[type] || '其他'
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 监听 WebSocket 消息
function setupWebSocketListener() {
  // 这里可以监听全局的 WebSocket 消息
  // 当收到新通知时自动刷新列表
  window.addEventListener('notification-received', () => {
    loadNotifications()
  })
}

onMounted(() => {
  loadNotifications()
  setupWebSocketListener()
})

// 监听筛选变化
watch(() => filterUnread.value, () => {
  loadNotifications()
})

// ✅ 返回上一页
function handleBack() {
  router.back()
}
</script>

<style lang="scss" scoped>
.notification-center {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .back-btn {
        padding: 8px 12px;
      }
    }

    .page-title {
      font-size: 18px;
      font-weight: bold;
    }

    .actions {
      display: flex;
      gap: 8px;
    }
  }

  .notification-title {
    color: #606266;

    &.unread {
      font-weight: bold;
      color: #409EFF;
    }
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
