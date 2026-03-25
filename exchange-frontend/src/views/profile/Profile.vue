<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <el-menu
              :default-active="activeMenu"
              mode="vertical"
              @select="handleSelect"
          >
            <el-menu-item index="/profile/assets">
              <el-icon><Coin /></el-icon>
              <span>我的资产</span>
            </el-menu-item>
            <el-menu-item index="/profile/settings">
              <el-icon><Setting /></el-icon>
              <span>设置</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <el-col :span="18">
        <router-view />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Coin, Setting } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

function handleSelect(index) {
  router.push(index)
}
</script>

<style lang="scss" scoped>
.profile-page {
  padding: 20px;
}
</style>
