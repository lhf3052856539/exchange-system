<template>
  <div class="trade-request">
    <el-card>
      <template #header>
        <span>发起交易</span>
      </template>

      <el-form :model="form" label-width="120px">
        <el-form-item label="支付币种">
          <el-select v-model="form.fromCurrency" placeholder="请选择">
            <el-option label="RNB" value="RNB" />
            <el-option label="GBP" value="GBP" />
          </el-select>
        </el-form-item>

        <el-form-item label="接收币种">
          <el-select v-model="form.toCurrency" placeholder="请选择">
            <el-option label="RNB" value="RNB" />
            <el-option label="GBP" value="GBP" />
          </el-select>
        </el-form-item>

        <el-form-item label="交易金额 (UT)">
          <el-input-number
              v-model="form.amount"
              :min="1"
              :max="userStore.tradeableUt"
              :step="1"
          />
          <div class="hint">最大可交易：{{ userStore.tradeableUt }} UT</div>
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              class="submit-btn"
              :loading="submitting"
              @click="handleSubmit"
          >
            提交交易
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { requestMatch } from '@/api/trade'

const router = useRouter()
const userStore = useUserStore()
const submitting = ref(false)

const form = reactive({
  fromCurrency: '',
  toCurrency: '',
  amount: 1
})

async function handleSubmit() {
  if (!form.fromCurrency || !form.toCurrency) {
    ElMessage.warning('请选择币种')
    return
  }

  submitting.value = true
  try {
    const requestData = {
      fromCurrency: form.fromCurrency,
      toCurrency: form.toCurrency,
      amount: form.amount
    }

    console.log('=== Trade Request ===')
    console.log('Request data:', requestData)
    console.log('Stringified:', JSON.stringify(requestData))

    const result = await requestMatch(requestData)
    ElMessage.success('交易请求已提交')

    if (result.queuePosition) {
      ElMessage.info(`当前队列位置：${result.queuePosition}`)
    }

    router.push('/trade/list')
  } catch (error) {
    console.error('Failed to submit trade:', error)
    if (error.response) {
      console.error('Response status:', error.response.status)
      console.error('Response data:', error.response.data)
      console.error('Response headers:', error.response.headers)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.trade-request {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;

  .submit-btn {
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

.hint {
  margin-top: 5px;
  font-size: 12px;
  color: #999;
}
</style>
