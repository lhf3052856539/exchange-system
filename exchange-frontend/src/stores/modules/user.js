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
        userType: (state) => state.userInfo?.userTypeDesc || '新用户',
        userTypeTag: (state) => {
            const type = state.userInfo?.userTypeDesc
            if (type === '种子用户') return 'success'
            if (type === '普通用户') return 'primary'
            return 'info'
        },
        newUserTradeCount: (state) => state.userInfo?.newUserTradeCount || 0,
        exthBalance: (state) => state.userInfo?.exthBalance || 0,
        tradeableUt: (state) => state.userInfo?.tradeableUt || 1,
        isBlacklisted: (state) => state.userInfo?.isBlacklisted || false,
        address: (state) => state.userInfo?.address || ''
    },

    actions: {
        async fetchUserInfo(address) {
            this.loading = true
            try {
                console.log('📡 Fetching user info for address:', address)
                const res = await getUserInfo()
                console.log('📡 User info API response:', res)
                // 后端返回的是 VO 包装类，需要解包获取 data
                this.userInfo = res.data || res
                console.log('✅ User info set:', this.userInfo)
                console.log('✅ User data - EXTH Balance:', this.userInfo.exthBalance, 'Tradeable UT:', this.userInfo.tradeableUt, 'User Type:', this.userInfo.userTypeDesc)
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

        async fetchTradeStats(address) {
            this.loading = true
            try {
                console.log('📊 Fetching trade stats for address:', address)
                const res = await getTradeStats()
                console.log('📊 Trade stats API response:', res)
                // 后端返回的是 VO 包装类，需要解包获取 data
                this.tradeStats = res.data || res
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
