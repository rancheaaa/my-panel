import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react-swc'
import monacoEditorPluginModule from 'vite-plugin-monaco-editor'

const isObjectMode = typeof monacoEditorPluginModule === 'function';
const monacoEditorPlugin = isObjectMode ? monacoEditorPluginModule : monacoEditorPluginModule.default;

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  
  return {
    plugins: [
      react(),
      // monacoEditorPlugin({
      //   languageWorkers: ['editorWorkerService', 'yaml']
      // })
    ],
    server: {
      proxy: {
        // 配置代理，用于连接真实后端
        '/api': {
          target: 'http://localhost:8888', // 这里填写真实后端地址
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''), // 根据后端要求决定是否重写
        },
        '/admin': {
          target: 'http://localhost:8888',
          changeOrigin: true,
        },
        '/swagger-ui': {
          target: 'http://localhost:8888',
          changeOrigin: true,
        },
        '/v3': {
          target: 'http://localhost:8888',
          changeOrigin: true,
        },
      },
    },
  }
})
