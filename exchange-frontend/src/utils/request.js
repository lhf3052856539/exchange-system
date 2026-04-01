// src/utils/request.js
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores'

const service = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/apis', // 修改为/apis，与后端一致
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json' // 统一设置Content-Type
    }
})

service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`
        }

        // 移除Content-Type的特殊处理，由axios自动处理
        return config
    },
    error => {
        console.error('Request error:', error)
        return Promise.reject(error)
    }
)


service.interceptors.response.use(
    response => {
        const res = response.data

        // 修改：不再检查 code，直接返回数据
        // 因为后端返回格式是 {success: true, data: ...} 或 {success: false, message: ...}
        return res
    },
    error => {
        console.error('Response error:', error)

        let message = error.message || '网络错误'

        if (error.response) {
            switch (error.response.status) {
                case 400:
                    message = '请求参数错误'
                    break
                case 401:
                    message = '未授权，请重新登录'
                    break
                case 403:
                    message = '拒绝访问'
                    break
                case 404:
                    message = '请求地址不存在'
                    break
                case 500:
                    message = '服务器内部错误'
                    break
                case 502:
                    message = '网关错误'
                    break
                case 503:
                    message = '服务不可用'
                    break
                case 504:
                    message = '网关超时'
                    break
                default:
                    message = `连接错误${error.response.status}`
            }
        } else if (error.request) {
            message = '无法连接到服务器，请检查网络'
        }

        ElMessage({
            message,
            type: 'error',
            duration: 5000
        })

        return Promise.reject(error)
    }
)

export default service
