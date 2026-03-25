import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src')
        },
        // 确保.vue文件可以被正确解析
        extensions: ['.mjs', '.js', '.mts', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    server: {
        port: 3000,
        host: true,
        proxy: {
            '/apis': {
                target: 'http://localhost:8093',
                changeOrigin: true,
            },
            '/ws': {
                target: 'ws://localhost:8093',
                ws: true
            }
        }
    }
})
