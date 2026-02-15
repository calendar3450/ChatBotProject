## 챗봇 만드는 1인 프로젝트 입니다. 

### RAG 채팅 스트리밍 시퀀스 다이어그램

```mermaid
sequenceDiagram
    actor User as 사용자 (Client)
    participant API as FastAPI Server (app.py)
    participant Spring as Spring Boot (DB)
    participant FAISS as Vector Store (File/Mem)
    participant LLM as Gemini / Ollama

    User->>API: GET /chat/stream (질문, 문서_id)
    activate API
    
    Note over API: 1. 대화 기록 조회
    API->>Spring: GET /chats/history
    Spring-->>API: 이전 대화 내역 (JSON)

    alt 문서_id == 0 (일반 대화)
        API->>API: 프롬프트 생성 (질문 + 대화 기록)
    else 문서_id > 0 (RAG 대화)
        Note over API: 2. 벡터 검색 (RAG)
        API->>API: embed_query(질문) -> 벡터 변환
        API->>FAISS: load_doc_store(문서_id)
        FAISS-->>API: 청크(Chunks) & 인덱스 반환
        API->>FAISS: index.search(질문 벡터)
        FAISS-->>API: 유사도 높은 문서 청크 Top-K
        API->>API: build_rag_context() -> 프롬프트 생성
    end

    Note over API: 3. LLM 생성 요청
    alt Model == Gemini
        API->>LLM: generate_gemini(프롬프트, stream=True)
        loop 스트리밍 청크 수신
            LLM-->>API: 텍스트 조각 (Chunk)
            API-->>User: SSE event: delta (텍스트)
        end
    else Model == Ollama
        API->>LLM: ollama_generate(프롬프트)
        LLM-->>API: 전체 답변 반환
        API-->>User: SSE event: delta (전체 텍스트)
    end

    Note over API: 4. 종료 및 출처 전송
    API-->>User: SSE event: end (citations 포함)
    deactivate API
```

### 문서 임베딩 시퀀스 다이어그램

```mermaid
sequenceDiagram
    actor User as 사용자 (Client)
    participant API as FastAPI Server
    participant PDF as PDF Parser (pdfplumber)
    participant Model as Embedding Model (E5)
    participant Disk as File System

    User->>API: POST /ingest (파일 경로, doc_id)
    activate API
    
    API->>PDF: extract_pdf_text(파일 경로)
    PDF-->>API: 텍스트 추출 완료
    
    API->>API: chunk_pages() -> 텍스트 분할
    
    loop 각 청크에 대해
        API->>Model: embed_passages(청크)
        Model-->>API: 벡터(Embedding) 반환
    end
    
    API->>Disk: save_doc_store()
    Note right of Disk: chunks.json 및<br/>index.faiss 저장
    
    API->>API: DOC_CACHE 초기화
    
    API-->>User: 200 OK (처리 결과)
    deactivate API


