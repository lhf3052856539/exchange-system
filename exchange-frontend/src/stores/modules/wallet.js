// stores/modules/wallet.js
import { defineStore } from 'pinia'

export const useWalletStore = defineStore('wallet', {
    state: () => ({
        address: '',
        isConnected: false,
    }),
    actions: {
        setWallet(addr) {
            this.address = addr
            this.isConnected = true
        },
        clearWallet() {
            this.address = ''
            this.isConnected = false
        }
    }
})
