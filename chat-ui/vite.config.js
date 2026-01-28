import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/chat': 'http://localhost:8000',      // ✅ 채팅 및 스트리밍은 Python 서버로
      '/documents': 'http://localhost:8080', // ✅ 문서 업로드/목록은 Java 서버로
    }
}
})
