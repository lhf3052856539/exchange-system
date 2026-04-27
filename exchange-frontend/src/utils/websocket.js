// src/utils/websocket.js
import { ElMessage } from 'element-plus'

class WebSocketClient {
    constructor() {
        this.ws = null
        this.reconnectTimer = null
        this.heartbeatTimer = null
        this.reconnectCount = 0
        this.maxReconnectCount = 5
        this.reconnectInterval = 3000
        this.heartbeatInterval = 30000
    }

    connect(address) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            console.log('WebSocket already connected')
            return
        }

        try {
            const token = localStorage.getItem('token')
            const wsUrl = `ws://localhost:8096/ws?token=${token}`

            this.ws = new WebSocket(wsUrl)

            this.ws.onopen = () => {
                console.log('✅ WebSocket connected')
                this.reconnectCount = 0
                this.startHeartbeat()
            }

            this.ws.onmessage = (event) => {
                console.log('📩 Received message:', event.data)
                try {
                    const data = JSON.parse(event.data)
                    this.handleMessage(data, address)
                } catch (error) {
                    console.error('Failed to parse message:', error)
                }
            }

            this.ws.onerror = (event) => {
                console.error('WebSocket error:', event)
            }

            this.ws.onclose = () => {
                console.log('WebSocket disconnected')
                this.stopHeartbeat()
                this.attemptReconnect(address)
            }

        } catch (error) {
            console.error('Failed to create WebSocket connection:', error)
        }
    }

    handleMessage(data, address) {
        // 根据消息类型处理
        if (data.type === 'notification') {
            this.handleNotification(data)
        } else if (data.type === 'trade') {
            this.handleTradeUpdate(data)
        }
    }

    handleNotification(data) {
        console.log('🔔 Notification received:', data)

        ElMessage({
            message: data.content || data.message || '收到新通知',
            type: data.level || 'info',
            duration: 5000,
            offset: 80
        })

        // 触发自定义事件
        window.dispatchEvent(new CustomEvent('notification-received', {
            detail: data
        }))
    }

    handleTradeUpdate(data) {
        console.log('Trade update received:', data)

        window.dispatchEvent(new CustomEvent('trade-updated', { detail: data }))

        ElMessage({
            message: `交易 ${data.tradeId} 状态更新：${data.status}`,
            type: 'success',
            duration: 3000
        })
    }

    send(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message))
        } else {
            console.warn('WebSocket is not connected, message not sent')
        }
    }

    startHeartbeat() {
        this.heartbeatTimer = setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({ type: 'ping' }))
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

        if (this.ws) {
            this.ws.close()
            this.ws = null
        }
    }
}

// 创建单例实例
export const wsClient = new WebSocketClient()

export default wsClient
