// src/stores/index.js
import { createPinia } from 'pinia'
import { useUserStore } from './modules/user'
import { useWalletStore } from './modules/wallet'
import { useTradeStore } from './modules/trade'
import { useAirdropStore } from './modules/airdrop'
import { useDaoStore, ProposalState, ProposalStateText, ProposalStateTag } from './modules/dao'

const pinia = createPinia()

export {
  useUserStore,
  useWalletStore,
  useTradeStore,
  useAirdropStore,
  useDaoStore,
  ProposalState,
  ProposalStateText,
  ProposalStateTag
}

export default pinia
