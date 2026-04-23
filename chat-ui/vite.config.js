import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  // Spring Boot `static`으로 직접 출력 → index.html과 해시된 JS/CSS가 항상 한 세트 (404로 인한 흰 화면 방지)
  build: {
    outDir: resolve(__dirname, '../project/src/main/resources/static'),
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/chats': 'http://localhost:8080', // 채팅 기록들 DB연동은 Spring으로
      '/documents': 'http://localhost:8080', // 문서 업로드/목록은 Java 서버로
      '/chat': 'http://localhost:8000',      // 채팅 및 스트리밍은 Python 서버로
      
      
    }
}
})
