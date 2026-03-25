<!-- src/views/dao/CreateProposal.vue -->
<template>
  <div class="create-proposal-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button @click="$router.back()">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span>创建新提案</span>
        </div>
      </template>

      <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="140px"
      >
        <el-form-item label="提案描述" prop="description">
          <el-input
              v-model="form.description"
              type="textarea"
              :rows="6"
              placeholder="请详细描述您的提案内容..."
          />
        </el-form-item>

        <el-form-item label="目标合约地址" prop="targetContract">
          <el-input
              v-model="form.targetContract"
              placeholder="0x..."
          />
        </el-form-item>

        <el-form-item label="ETH 数量" prop="value">
          <el-input-number
              v-model="form.value"
              :min="0"
              :precision="18"
              :step="0.1"
              placeholder="0"
          />
          <span class="form-tip">随调用发送的 ETH 数量（可选）</span>
        </el-form-item>

        <el-form-item label="调用数据" prop="callData">
          <el-input
              v-model="form.callData"
              type="textarea"
              :rows="4"
              placeholder="0x..."
          />
          <span class="form-tip">编码后的函数调用数据</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="submit-btn" @click="handleSubmit" :loading="processing">
            提交提案
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
<script setup>// src/views/dao/CreateProposal.vue
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useDao } from '@/composables/useDao'
import { useWalletStore } from '@/stores'

const router = useRouter()
const walletStore = useWalletStore()
const { submitProposal, processing } = useDao()
const formRef = ref(null)

const form = reactive({
  description: '',
  targetContract: '',
  value: 0,
  callData: '0x'
})

const rules = {
  description: {
    required: true,
    message: '请输入提案描述',
    trigger: 'blur'
  },
  targetContract: {
    required: true,
    message: '请输入目标合约地址',
    trigger: 'blur',
    pattern: /^0x[a-fA-F0-9]{40}$/
  },
  callData: {
    pattern: /^0x[a-fA-F0-9]*$/,
    message: '请输入有效的调用数据（0x 开头）',
    trigger: 'blur'
  }
}

async function handleSubmit() {
  if (!walletStore.isConnected) {
    ElMessage.warning('请先连接钱包')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      await submitProposal({
        description: form.description,
        targetContract: form.targetContract,
        value: form.value,
        callData: form.callData
      })
      router.push('/dao')
    } catch (error) {
      console.error('Failed to create proposal:', error)
    }
  })
}
</script>


<style lang="scss" scoped>.create-proposal-page {
  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
  }

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

  .form-tip {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}
</style>