// src/utils/websocket.js
import { ElMessage } from 'element-plus'
import { Client } from '@stomp/stompjs'

class WebSocketClient {
    constructor() {
        this.client = null
        this.reconnectTimer = null
        this.heartbeatTimer = null
        this.reconnectCount = 0
        this.maxReconnectCount = 5
        this.reconnectInterval = 3000
        this.heartbeatInterval = 30000
    }

    connect(address) {
        if (this.client && this.client.connected) {
            console.log('STOMP already connected')
            return
        }

        try {
            const token = localStorage.getItem('token')
            const wsUrl = `http://localhost:8096/ws?token=${token}`

            this.client = new Client({
                brokerURL: wsUrl,
                reconnectDelay: this.reconnectInterval,
                heartbeatIncoming: this.heartbeatInterval,
                heartbeatOutgoing: this.heartbeatInterval,

                connectHeaders: {
                    'user-id': address
                },

                onConnect: (frame) => {
                    console.log('✅ STOMP connected:', frame)
                    this.reconnectCount = 0
                    this.startHeartbeat()

                    // 订阅个人通知主题
                    const notificationSub = this.client.subscribe(`/topic/notifications/${address}`, (message) => {
                        console.log('📩 [NOTIFICATION] Raw message:', message)
                        console.log('📩 [NOTIFICATION] Body:', message.body)
                        this.handleNotification(message)
                    })
                    console.log('✅ Subscribed to /topic/notifications/', address, 'subscription id:', notificationSub.id)

                    console.log('🎉 All subscriptions completed')
                },


                onStompError: (frame) => {
                    console.error('STOMP error:', frame)
                },

                onDisconnect: () => {
                    console.log('STOMP disconnected')
                    this.stopHeartbeat()
                    this.attemptReconnect(address)
                },

                onWebSocketError: (event) => {
                    console.error('WebSocket error:', event)
                }
            })

            this.client.activate()

        } catch (error) {
            console.error('Failed to create STOMP connection:', error)
        }
    }

    send(destination, body) {
        if (this.client && this.client.connected) {
            this.client.publish({
                destination: destination,
                body: JSON.stringify(body)
            })
        } else {
            console.warn('STOMP is not connected, message not sent')
        }
    }

    handleNotification(message) {
        try {
            console.log('🔍 Parsing notification body...')
            const data = JSON.parse(message.body)
            console.log('🔔 Notification parsed successfully:', data)
            console.log('   - Title:', data.title)
            console.log('   - Content:', data.content)
            console.log('   - Type:', data.type)

            ElMessage({
                message: data.content || data.message || '收到新通知',
                type: data.level || 'info',
                duration: 5000,
                offset: 80
            })

            // ✅ 触发自定义事件，让布局组件可以监听
            window.dispatchEvent(new CustomEvent('notification-received', {
                detail: data
            }))
        } catch (error) {
            console.error('❌ Failed to parse notification:', error)
            console.error('   - Raw body:', message.body)
        }
    }

    handleTradeUpdate(message) {
        try {
            const data = JSON.parse(message.body)
            console.log('Trade update received:', data)

            // 触发自定义事件，页面可以监听这个事件来刷新数据
            window.dispatchEvent(new CustomEvent('trade-updated', { detail: data }))

            ElMessage({
                message: `交易 ${data.tradeId} 状态更新：${data.status}`,
                type: 'success',
                duration: 3000
            })
        } catch (error) {
            console.error('Failed to parse trade update:', error)
        }
    }

    startHeartbeat() {
        this.heartbeatTimer = setInterval(() => {
            if (this.client && this.client.connected) {
                this.client.publish({
                    destination: '/app/heartbeat',
                    body: JSON.stringify({ type: 'ping' })
                })
            }
        }, this.heartbeatInterval)
    }

    stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer)
            this.heartbeatTimer = null
        }
    }

    attemptReconnect(address) {
        if (this.reconnectCount >= this.maxReconnectCount) {
            console.log('Max reconnection attempts reached')
            ElMessage.warning('WebSocket 连接失败，请刷新页面重试')
            return
        }

        this.reconnectCount++
        console.log(`Attempting to reconnect (${this.reconnectCount}/${this.maxReconnectCount})...`)

        this.reconnectTimer = setTimeout(() => {
            this.connect(address)
        }, this.reconnectInterval)
    }

    disconnect() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }

        this.stopHeartbeat()

        if (this.client) {
            this.client.deactivate()
            this.client = null
        }
    }
}

// 创建单例实例
export const wsClient = new WebSocketClient()

export default wsClient
