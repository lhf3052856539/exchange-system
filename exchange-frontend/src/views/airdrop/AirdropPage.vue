<template>
  <div class="airdrop-page">
    <el-row :gutter="20">
      <el-col :span="12" :offset="6">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>空投领取</span>
            </div>
          </template>

          <div v-loading="loading" class="content">
            <el-result
                v-if="!hasActiveAirdrop"
                icon="info"
                title="暂无空投活动"
                sub-title="当前没有活跃的空投活动，敬请期待"
            >
              <template #extra>
                <el-button class="home-btn" @click="$router.push('/home')">
                  返回首页
                </el-button>
              </template>
            </el-result>

            <el-result
                v-else-if="hasClaimedStatus"
                icon="success"
                title="已领取空投"
                sub-title="您已经领取过空投奖励了"
            >
              <template #extra>
                <el-button type="primary" @click="$router.push('/trade/list')">
                  查看交易
                </el-button>
              </template>
            </el-result>

            <div v-else>
              <el-statistic title="每个地址可领取" :value="fixedAmount" suffix="EXTH" />
              <el-statistic
                  title="剩余可领取总量"
                  :value="remainingAmount"
                  :suffix="EXTH"
                  style="margin-top: 20px"
              />

              <el-button
                  type="primary"
                  class="claim-btn"
                  :loading="processing"
                  style="width: 100%; margin-top: 30px"
                  @click="handleClaim"
              >
                立即领取
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
// ... existing code ...
</script>

<style lang="scss" scoped>
.airdrop-page {
  padding: 40px 20px;

  .home-btn {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
    border: none !important;
    color: #fff !important;
    font-weight: 600;
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);

    &:hover {
      background: linear-gradient(135deg, #764ba2 0%, #667eea 100%) !important;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
    }
  }

  .claim-btn {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
    border: none !important;
    color: #fff !important;
    font-weight: 600;
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);

    &:hover {
      background: linear-gradient(135deg, #764ba2 0%, #667eea 100%) !important;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
    }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
</style>
