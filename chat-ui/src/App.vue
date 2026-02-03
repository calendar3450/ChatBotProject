

<!-- // const message = ref('안녕하세요')
// const button_message = ref('클릭')

// function changeMessage() {
//   if (message.value === '안녕하세요') {
//     message.value = '버튼을 눌렀어요!'
//     button_message.value = '클릭했어요!'

//   } else {
//     message.value = '안녕하세요'
//     button_message.value = '클릭!'
//   }
// }
/**
 * documents: Spring GET /documents 결과를 저장
 * selected: 체크된 문서 id Set
 */ -->

<script setup>
import { ref, onMounted, computed, nextTick, reactive } from 'vue'

const documents = ref([])
const selected = ref(new Set())

const messages = ref([
  { role: 'assistant', text: '왼쪽에서 문서를 업로드 하고 문서를 체크하고 질문해보세요.', citations: [] },
])

const input = ref('')
const sending = ref(false)
const loadingDocs = ref(false)
const docError = ref('')
const chatRef = ref(null)
const useGemini = ref(false) // 모델 선택 토글 (false=Ollama, true=Gemini)

//값을 자동으로 갱신하여 set집합에 데이터를 보냄.
const selectedDocIds = computed(() => Array.from(selected.value))

// 답변이 오면 스크롤이 내려감.
async function scrollToBottom() {
  await nextTick() //Promise 체이닝 기법
  const curScroll = chatRef.value
  if (!curScroll) return
  curScroll.scrollTo({ top: curScroll.scrollHeight + 100, behavior: 'smooth' })
}


/** 문서 목록 로딩 */
async function loadDocuments() {
  loadingDocs.value = true
  docError.value = ''
  try {
    const res = await fetch('/documents')
    if (!res.ok) {
      docError.value = `문서 목록 에러: ${res.status} ${await res.text()}`
      documents.value = []
      return
    }
    const data = await res.json()
    // createdAt 내림차순(있을 때만)
    data.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
    documents.value = data
  } catch (e) {
    docError.value = `문서 목록 요청 실패: ${String(e)}`
    documents.value = []
  } finally {
    loadingDocs.value = false
  }
}

// /* 채팅 로딩 */
// async function loadChat() {
//   loadingChat.value = true
//   chatError.value = ''
//   try {
//     const res = await fetch('/chat/stream')
//     if (!res.ok) {
//       chatError.value = `채팅 에러: ${res.status} ${await res.text()}`
//       return
//     }
//     const data = await res.json()
//     // createdAt 내림차순(있을 때만)
//     data.sort((a,b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
//     }
  
// }


function isSelectable(doc) {
  // 상태가 DONE일 때만 선택 가능하게 (status가 없다면 filePath 존재 여부로 대충 판단)
  if (doc.status) return doc.status === 'DONE'
  // return !!doc.filePath
  return true
}

function toggleSelect(docId) {
  const s = new Set(selected.value)
  if (s.has(docId)) s.delete(docId)
  else s.add(docId)
  selected.value = s
}

function clearSelection() {
  selected.value = new Set()
}

/** Enter 전송 / Shift+Enter 줄바꿈 */
function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendStream()
  }
}

let es = null

// 메세지를 챗봇에게 보냄.
async function sendStream() {
  const q = input.value.trim()
  if (!q || sending.value) return

  messages.value.push({ role: 'user', text: q })
  input.value = ''

  const botMsg = reactive({ role: 'assistant', text: '', loading: true, citations: [] })
  messages.value.push(botMsg)
  sending.value = true
  await scrollToBottom()

  // 기존 연결 있으면 닫기
  if (es) { es.close(); es = null }

  // 선택된 문서가 없으면 [0] (일반 채팅)으로 설정
  const targetIds = selectedDocIds.value.length > 0 ? selectedDocIds.value : [0]
  const docIdsParam = targetIds.join(',')
  const model = useGemini.value ? 'gemini' : 'ollama'
  const url = `/chat/stream?docIds=${encodeURIComponent(docIdsParam)}&q=${encodeURIComponent(q)}&topK=5&model=${model}`

  // Java 서버의 /chat/stream 엔드포인트로 GET 요청을 보내 연결
  es = new EventSource(url)

  es.addEventListener('delta', (e) => {
    // Spring에서 data는 JSON 문자열로 오므로 파싱
    const obj = JSON.parse(e.data)
    if (obj.type === 'delta') {
      botMsg.text += obj.text
      scrollToBottom()
    }
  })

  es.addEventListener('meta', (e) => {
    const obj = JSON.parse(e.data)
    if (obj.type === 'end') {
      botMsg.loading = false
      botMsg.citations = obj.citations ?? []
      sending.value = false
      es.close()
      es = null
    }
  })

  es.onerror = () => {
    botMsg.text = botMsg.text || '스트리밍 오류가 발생했습니다.@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@2'
    botMsg.loading = false
    sending.value = false
    if (es) es.close()
    es = null
  }
}

// 300초마다 문서 목록 갱신
let timer = null
onMounted(() => {
  loadDocuments()
  timer = setInterval(loadDocuments, 300000) // 300초마다 갱신
})

const uploadBusy = ref(false)
const uploadError = ref('')

// 파일 업로드
async function uploadFiles(fileList) {
  uploadError.value = ''
  if (!fileList || fileList.length === 0) return

  const formData = new FormData()
  for (const f of fileList) {
    formData.append('files', f)   // ✅ key 이름: "files"
  }

  uploadBusy.value = true
  try {
    const res = await fetch('/documents/uploads', {
      method: 'POST',
      body: formData,
    })

    if (!res.ok) {
      uploadError.value = `업로드 에러: ${res.status}\n${await res.text()}`
      return
    }

    const data = await res.json() // 업로드된 문서 리스트
    // 업로드된 문서들을 자동 선택
    const s = new Set(selected.value)
    for (const d of data) s.add(d.id)
    selected.value = s

    // 문서 목록 갱신
    await loadDocuments()
  } catch (e) {
    uploadError.value = `업로드 요청 실패: ${String(e)}`
  } finally {
    uploadBusy.value = false
  }
}

// 파일 삭제
async function deleteDocument(id) {
  if (!confirm('정말 삭제하시겠습니까?')) return

  try {
    // deleteMapping이므로 method는 DELETE로 설정.
    const res = await fetch(`/documents/${id}/delete`, { method: 'DELETE' })
    if (!res.ok) {
      alert(`삭제 실패: ${res.status}\n${await res.text()}`)
      return
    }
    // 목록 갱신 및 선택 해제
    await loadDocuments()
    if (selected.value.has(id)) toggleSelect(id)
  } catch(e) {
    alert(`삭제 요청 실패. 이유: ${String(e)}`)
  }
}


function onPickFiles(e) {
  const files = e.target.files
  uploadFiles(files)
  // 같은 파일 다시 선택 가능하도록 input reset
  e.target.value = ''
}

async function reingestDoc(id) {
  try {
    const res = await fetch(`/documents/${id}/reingest`, { method: 'POST' })
    if (!res.ok) {
      alert(`재시도 실패: ${res.status}\n${await res.text()}`)
      return
    }
    await loadDocuments()
  } catch (e) {
    alert(`재시도 요청 실패: ${String(e)}`)
  }
}

// 상태별 텍스트
function statusLabel(s) {
  if (s === 'DONE') return '완료'
  if (s === 'PROCESSING') return '인덱싱 중'
  if (s === 'FAILED') return '실패'
  if (s === 'UPLOADED') return '업로드됨'
  return s || '오류입니다.'
}

</script>

<template>
  <div class="app">
    <!-- 좌측 사이드바 -->
    <aside class="sidebar">
      <div class="side-header">
        <div class="side-title">문서</div>
        <button class="side-btn" @click="loadDocuments" :disabled="loadingDocs">
          {{ loadingDocs ? '로딩중' : '새로고침' }}
        </button>
      </div>

      <div class="side-sub">
        선택: {{ selectedDocIds.length }}개
        <button class="link-btn" @click="clearSelection" :disabled="selectedDocIds.length === 0">선택 해제</button>
      </div>
      <div class="upload-box">
  <label class="upload-btn">
    <input
      type="file"
      accept="application/pdf"
      multiple
      :disabled="uploadBusy"
      @change="onPickFiles"
      class="file-input"
    />
    {{ uploadBusy ? '업로드중...' : 'PDF 여러 개 업로드' }}
  </label>

  <div v-if="uploadError" class="error">
    {{ uploadError }}
  </div>
</div>

      <div v-if="docError" class="error">
        {{ docError }}
      </div>

      <div v-if="!docError && documents.length === 0" class="empty">
        문서가 없습니다. 먼저 업로드하세요.
      </div>
      
      <div class="doc-list" v-else>
        <label
          v-for="d in documents"
          :key="d.id"
          class="doc-item"
          :class="{ disabled: !isSelectable(d) }"
        >
          <input
            type="checkbox"
            :checked="selected.has(d.id)"
            :disabled="!isSelectable(d)"
            @change="toggleSelect(d.id)"
          />

          <div class="doc-meta">
            <div class="doc-title">
              {{ d.title || ('문서 #' + d.id) }}
            </div>
            <div class="doc-sub">
              #{{ d.id }}
              <span v-if="d.status" class="badge" :class="'st-' + d.status">
                {{ statusLabel(d.status) }}
              </span>
              <span v-if="d.createdAt">· {{ d.createdAt }}</span>
            </div>
            <div class="doc-actions">
            <button
              v-if="d.status === 'FAILED'"
              class="retry"
              @click.prevent="reingestDoc(d.id)"
              title="다시 인덱싱"
            >
              재시도
            </button>
            <button class="retry delete" @click.prevent="deleteDocument(d.id)">삭제</button>
            </div>
          </div>
        </label>
      </div>
    </aside>

    <!-- 메인 채팅 영역 -->
    <section class="main">
      <header class="topbar">
        <div class="header-left">
          <div class="title">Doc Chat</div>
          <label class="model-toggle">
            <input type="checkbox" v-model="useGemini">
            <span class="toggle-text">{{ useGemini ? 'Gemini' : 'Ollama' }}</span>
          </label>
        </div>
        <div class="meta">선택 문서 IDs: {{ selectedDocIds.join(', ') || '없음' }}</div>
      </header>

      <main class="chat" ref="chatRef">
        <div v-for="(m, idx) in messages" :key="idx" class="row" :class="m.role">
          <div class="bubble">
            <div class="text">{{ m.text }}</div>

            <details v-if="m.role === 'assistant' && !m.loading && m.citations?.length" class="citations">
              <summary>근거 보기 ({{ m.citations.length }})</summary>
              <ul>
                <li v-for="c in m.citations" :key="c.rank">
                  문서 {{ c.document_id ?? '?' }},
                  p.{{ c.page_from }}~{{ c.page_to }}
                  <!-- score={{ fmtScore(c.score) }} -->
                </li>
              </ul>
            </details>
          </div>
        </div>
      </main>

      <footer class="composer">
        <textarea
          v-model="input"
          class="input"
          placeholder="메시지 입력 (Enter 전송, Shift+Enter 줄바꿈)"
          :disabled="sending"
          @keydown="onKeydown"
        />
        <button class="send" :disabled="sending || !input.trim()" @click="sendStream">
          {{ sending ? '전송중' : '전송' }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style>
body { margin: 0; padding: 0; overflow: hidden; }



</style>

<!-- <style scoped>

</style> -->
