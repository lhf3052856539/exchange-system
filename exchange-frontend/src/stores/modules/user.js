// src/stores/modules/user.js
import { defineStore } from 'pinia'
import { getUserInfo, register, getTradeStats } from '@/api/user'

export const useUserStore = defineStore('user', {
    state: () => ({
        userInfo: null,
        tradeStats: null,
        loading: false
    }),

    getters: {
        userType: (state) => state.userInfo?.userType || 'NEW',
        newUserTradeCount: (state) => state.userInfo?.newUserTradeCount || 0,
        exthBalance: (state) => state.userInfo?.exthBalance || 0,
        tradeableUt: (state) => state.userInfo?.tradeableUT || 1,
        isBlacklisted: (state) => state.userInfo?.isBlacklisted || false,
        address: (state) => state.userInfo?.address || ''
    },

    actions: {
        async fetchUserInfo() {
            this.loading = true
            try {
                console.log('📡 Fetching user info')
                const res = await getUserInfo() // 不再传递address参数
                console.log('📡 User info API response:', res)
                this.userInfo = res
                console.log('✅ User info set:', this.userInfo)
                return this.userInfo
            } catch (error) {
                console.error('❌ Failed to fetch user info:', error)
                throw error
            } finally {
                this.loading = false
            }
        },
        async registerUser(address) {
            this.loading = true
            try {
                const res = await register(address)
                // 存储token到localStorage
                if (res.token) {
                    localStorage.setItem('token', res.token)
                }
                this.userInfo = res
            } catch (error) {
                console.error('Failed to register user:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        async fetchTradeStats() {
            this.loading = true
            try {
                console.log('📊 Fetching trade stats')
                const res = await getTradeStats() // 不再传递address参数
                console.log('📊 Trade stats API response:', res)
                this.tradeStats = res
                console.log('✅ Trade stats set:', this.tradeStats)
                return this.tradeStats
            } catch (error) {
                console.error('❌ Failed to fetch trade stats:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        clearUser() {
            this.userInfo = null
            this.tradeStats = null
        }
    }
})
