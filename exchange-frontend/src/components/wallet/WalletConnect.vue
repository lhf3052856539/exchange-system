<!-- src/components/wallet/WalletConnect.vue -->
<template>
  <el-button
      :type="buttonType"
      :loading="connecting"
      @click="handleConnect"
  >
    <el-icon style="margin-right: 6px;"><Wallet /></el-icon>
    {{ buttonText }}
  </el-button>
</template>

<script setup>
import { computed } from 'vue'
import { useWalletStore } from '@/stores'
import { Wallet } from '@element-plus/icons-vue'

const props = defineProps({
  autoConnect: {
    type: Boolean,
    default: false
  },
  showAddress: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['connected', 'disconnected'])

const walletStore = useWalletStore()

const connecting = computed(() => walletStore.connecting)
const isConnected = computed(() => walletStore.isConnected)
const shortAddress= computed(() => walletStore.shortAddress)

const buttonType = computed(() => {
  return isConnected.value ? 'success' : 'primary'
})

const buttonText = computed(() => {
  if (connecting.value) {
    return '连接中...'
  }

  if (isConnected.value) {
    return props.showAddress ? shortAddress.value : '已连接'
  }

  return '连接钱包'
})

async function handleConnect() {
  if (isConnected.value) {
    emit('disconnected')
    return
  }

  const success= await walletStore.connect()

  if (success) {
    emit('connected', walletStore.address)
  }
}

if (props.autoConnect) {
  handleConnect()
}
</script>
