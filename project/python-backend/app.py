from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import pdfplumber
from pathlib import Path
import uvicorn
from sentence_transformers import SentenceTransformer
import numpy as np
import faiss
import json
import requests
import traceback
from fastapi import Body
from fastapi.responses import JSONResponse
from fastapi.responses import StreamingResponse
import json, time


import gemini_service

app = FastAPI()


# ✅ 임베딩 모델 (멀티링구얼)
# e5 계열은 query에 "query: ", 문서에 "passage: " prefix 권장
EMBED_MODEL_NAME = "intfloat/multilingual-e5-base"
# RTX 5070 호환성 문제가 해결될 때까지 임시로 CPU 사용 (임베딩은 CPU로도 충분히 빠름)
embed_model = SentenceTransformer(EMBED_MODEL_NAME, device="cpu")

DATA_DIR = Path("data")
DATA_DIR.mkdir(exist_ok=True)

OLLAMA_BASE = "http://localhost:11434"
OLLAMA_MODEL = "qwen3-vl:8b"
SPRING_BOOT_URL = "http://localhost:8080"

# # 테스트용.
# class PingResponse(BaseModel):
#     status: str

# @app.get("/ping", response_model=PingResponse)
# def ping():
#     return {"status": "ok"}


class IngestRequest(BaseModel):
    document_id: list[int]
    file_path: str
    title: str | None = None

class IngestResponse(BaseModel):
    document_id: int
    received: bool
    message: str
    page_count: int | None = None
    extracted_chars : int


class ChatRequest(BaseModel):
    document_id: int
    question: str
    top_k : int = 3
    per_doc : int = 3
    model: str = "ollama" # "ollama" or "gemini"
    

class ChatResponse(BaseModel):
    answer: str
    citations: list[dict]

class HistoryMessage(BaseModel):
    role: str
    content: str

class StreamChatRequest(BaseModel):
    document_ids: list[int] = [0]
    question: str
    top_k: int = 3
    model: str = "ollama"  # "ollama" or "gemini"
    history: list[HistoryMessage] = []


def extract_pdf_text(file_path: str):
    p = Path(file_path)

    if not p.exists():
        raise FileNotFoundError(f"File not found: {p}")
    
    pages = []
    with pdfplumber.open(str(p)) as pdf:
        for i, page in enumerate(pdf.pages):
            # pdfplumber는 pypdf보다 'bbox' 관련 에러에 훨씬 강합니다.
            text = page.extract_text() or ""
            pages.append({"page": i + 1, "text": text})
            
    return pages

def chunk_pages(pages, chunk_chars,overlap):
    chunks =[]
    buffer = ''
    start_page = None
    current_page = None

    def flush(end_page):
        nonlocal buffer, start_page
        text = buffer.strip()
        if text:
            chunks.append({
                'text': text,
                'page_from': start_page,
                'page_to': end_page,
            })

        buffer = buffer[-overlap:] if overlap > 0 else ''
        start_page = end_page

    for item in pages:
        current_page = item["page"]
        if start_page is None:
            start_page = current_page

        buffer += "\n" + item["text"]

        while len(buffer) >= chunk_chars:
            flush(current_page)

    # 남은 것 처리
    if buffer.strip():
        chunks.append({
            "text": buffer.strip(),
            "page_from": start_page,
            "page_to": current_page
        })

    return chunks

# 질문과 문서의 특성이 다르기 때문에, 들어온 텍스트가 검색 대상을 위한 문서인지 혹은 질문읹지 확인.
# 문제: 일반 임베딩 방식 사용 시, 질문과 문서의 형식이 달라 검색 정확도가 낮게 측정됨.
# 해결: E5 모델의 Asymmetric Embedding 특성을 활용하여 query: 및 passage: Prefix 전략 도입.

def embed_passages(texts):
    # e5는 passage prefix 권장
    inputs = [f"passage: {t}" for t in texts]
    vecs = embed_model.encode(inputs, normalize_embeddings=True)
    return np.array(vecs, dtype="float32")

def embed_query(q):
    v = embed_model.encode([f"query: {q}"], normalize_embeddings=True)
    return np.array(v, dtype="float32")

# 결과: 검색 결과의 상위 K개 문서에 대한 재현율(Recall) 20% 향상 및 벡터 정규화를 통한 검색 일관성 확보.

def save_doc_store(doc_id, chunks, vectors):
    # 각각의 문서를 위한 파일 정리.
    doc_dir = DATA_DIR / f"doc_{doc_id}"
    doc_dir.mkdir(exist_ok=True)

    # chunks 저장
    (doc_dir / "chunks.json").write_text(json.dumps(chunks, ensure_ascii=False, indent=2), encoding="utf-8")

    # faiss 인덱스 저장 (cosine 유사도 = normalize + inner product)
    # faiss는 키워드와 의미의 거리를 검색하는것.
    dim = vectors.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(vectors)
    faiss.write_index(index, str(doc_dir / "index.faiss"))


def load_doc_store(doc_id):
    doc_dir = DATA_DIR / f"doc_{doc_id}"
    chunks_path = doc_dir / "chunks.json"
    index_path = doc_dir / "index.faiss"

    if not chunks_path.exists() or not index_path.exists():
        raise FileNotFoundError(f"Index not found for document_id={doc_id}. Did you ingest?")

    chunks = json.loads(chunks_path.read_text(encoding="utf-8"))
    index = faiss.read_index(str(index_path))
    return chunks, index



def ollama_generate(prompt: str) -> str:
    url = f"{OLLAMA_BASE}/api/generate"
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False
    }

    r = requests.post(url, json=payload, timeout=120)
    r.raise_for_status()

    # ✅ 핵심: bytes -> utf-8 로 확정 디코딩 후 JSON 파싱
    data = json.loads(r.content.decode("utf-8"))
    return (data.get("response") or "").strip()

# RAG 검색 및 컨텍스트 생성 함수
def build_rag_context(question: str, document_ids: list[int], top_k: int):
    qv = embed_query(question)
    real_top_k = max(1, min(top_k, 5))

    merged = []
    for doc_id in document_ids:
        try:
            chunks, index = load_doc_store(doc_id)
            scores, ids = index.search(qv, real_top_k)
            for rank, idx in enumerate(ids[0].tolist()):
                if idx < 0: continue
                merged.append((float(scores[0][rank]), doc_id, idx, chunks[idx]))
        except FileNotFoundError:
            continue

    merged.sort(key=lambda x: x[0], reverse=True)
    picked = merged[:real_top_k]

    context_parts = []
    citations = []
    for r, (score, doc_id, chunk_idx, chunk) in enumerate(picked, start=1):
        context_parts.append(chunk["text"])
        citations.append({
            "rank": r,
            "score": score,
            "document_id": doc_id,
            "page_from": chunk.get("page_from"),
            "page_to": chunk.get("page_to"),
            "chunk_index": chunk_idx
        })

    context = "\n\n---\n\n".join(context_parts)
    return context, citations

# ✅ RAG 프롬프트 생성 함수 (공통 사용)
def build_rag_prompt(question: str, context: str, history_text: str = ""):
    return f"""당신은 업로드된 PDF 문서의 내용에 근거해 답하는 어시스턴트입니다.

규칙:
0) 답변은 한국어로만 작성하세요.
1) 문서 [근거]가 질문에 대한 분석적 추론을 시작하기에 '적절한 정보'를 담고 있는지 판정하세요.
2 마지막에 "추가로 있으면 좋은 데이터" 3가지를 제안하세요.
3) 이전의 채팅 기록으로 보고 답변 해주셔도 상관없습니다.

[이전 대화]
{history_text}

[질문]
{question}

[근거]
{context}

[출력 형식 - 충분/부분일 때]
- 결론:
- 근거 요약(3~5줄):
- 추가로 있으면 좋은 데이터(3개):


[답변]
"""

# Spring Boot에서 채팅 기록을 가져오는 헬퍼 함수 (내부 사용)
def fetch_chat_history_text(limit: int = 20) -> str:
    history_text = ""
    try:
        # Spring Boot의 /chats/history API 호출
        resp = requests.get(f"{SPRING_BOOT_URL}/chats/history", params={"limit": limit}, timeout=2)
        if resp.status_code == 200:
            for msg in resp.json():
                role = "User" if msg.get("role") == "user" else "Assistant"
                content = msg.get("content", "")
                history_text += f"{role}: {content}\n"
       
    except Exception as e:
        print(f"History fetch failed: {e}")

    return history_text


# 받고나서 파일읽고(extract_pdf_text) -> 텍스트 분할하고(chunk_pages) -> 벡터화(embed_passages) -> 저장(save_doc_store):.
@app.post("/ingest", response_model=IngestResponse)
def ingest(req: IngestRequest):
    try:
        pages = extract_pdf_text(req.file_path)
        # 텍스트가 거의 없으면 스캔본일 가능성
        total_chars = sum(len(p["text"]) for p in pages)
        if total_chars < 50:
            raise HTTPException(status_code=400, detail="PDF파일의 텍스트를 읽을수가 없습니다.")

        chunks = chunk_pages(pages, chunk_chars=800, overlap=120)
        texts = [c["text"] for c in chunks]
        vecs = embed_passages(texts)

        for doc_id in req.document_id:
            save_doc_store(doc_id, chunks, vecs)

        return IngestResponse(
            document_id=req.document_id[0],
            received=True,
            extracted_chars=total_chars,
            page_count=len(pages),
            message=f"Ingested: {len(chunks)}의 청크들을 저장했습니다."
        )
    except FileNotFoundError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except HTTPException:
        raise
    except Exception as e:
        traceback.print_exc() # 서버 콘솔에 상세 에러 로그 출력
        raise HTTPException(status_code=500, detail=f"Ingest failed: {e}")



THRESHOLD = 0.90  # 필요하면 조정 RAG로 갈지 아니면 모델의 내용으로 갈지 확인.
    
# SSE 
def sse_event(data: dict, event: str = "message") -> str:
    # SSE 포맷: event: xxx \n data: yyy \n\n \n = 여기까지가 하나의 메시지 임을 나타냄.
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

@app.get("/chat/stream")
def chat_stream(docIds: str = "0", q: str = "", topK: int = 3, model: str = "ollama"):
    # 테스트용 시간 측정 시작
    start = time.time()

    # docIds는 "1,2" 처럼 콤마로 구분된 문자열로 옴. 우선 첫 번째 ID만 사용하도록 처리
    try:
        first_id = int(docIds.split(",")[0])
    except:
        first_id = 0

    req = ChatRequest(
        document_id=first_id,
        question=q,
        top_k=topK,
        model=model
    )

    def gen():
        # 1) (선택) 시작 이벤트
        yield sse_event({"type": "start"}, event="meta")
        citations = []
        prompt = ""

        try:
            # 0) Spring Boot에서 채팅 기록 가져오기 (Pull 방식)
            # 함수를 호출하여 깔끔하게 처리
            history_text = fetch_chat_history_text(limit=20)

            # --- 2) 프롬프트 및 컨텍스트 구성 ---
            if req.document_id == 0:
                # [일반 대화]
                if req.model == 'gemini':
                    prompt = f"""당신은 유용한 한국어 어시스턴트입니다.
                    반드시 한국어로만 답변하세요. 이전 대화 보고 답변을 추론 하셔도 됩니다.
                    
                    [이전 대화]
                    {history_text}

                    [질문]
                    {req.question}
                    """
                else:
                    prompt = f""" 당신은 유용한 한국어 어시스턴트입니다.
                        반드시 한국어로만 답변하세요. 중국어 언어는 절대 사용하지 마세요.
                        사용자의 질문에 정확하고 간결하게 답하세요. 모르면 모른다고 하세요.
                        이전 대화 보고 답변을 추론 하셔도 됩니다.
                    
                    [이전 대화]
                    {history_text}

                    [질문]
                    {req.question}
                    [답변]
                    """
            else:
                # [RAG 대화]
                context, citations = build_rag_context(req.question, [req.document_id], req.top_k)
                prompt = build_rag_prompt(req.question, context, history_text)
                print(prompt)

            # --- 3) 모델 호출 및 스트리밍 전송 ---
            if req.model == 'gemini':
                # Gemini 스트리밍
                response = gemini_service.generate_gemini(prompt, stream=True)
                for chunk in response:
                    if chunk.parts:
                        text_chunk = chunk.text
                        yield sse_event({"type": "delta", "text": text_chunk}, event="delta")
            else:
                # Ollama (현재 구현상 non-streaming이므로 한 번에 전송)
                answer = ollama_generate(prompt)
                yield sse_event({"type": "delta", "text": answer}, event="delta")
            
            # --- 4) 종료 이벤트 (citations 포함) ---
            yield sse_event({"type": "end", "citations": citations}, event="meta")
            
            print("걸린시간: ", time.time() - start)
        except FileNotFoundError as e:
            raise HTTPException(status_code=400, detail=str(e))
        except Exception as e:
            import traceback
            traceback.print_exc() # 서버 콘솔에 상세 에러 로그 출력
            raise HTTPException(status_code=500, detail=f"Chat failed: {e}")
    

    return StreamingResponse(gen(), media_type="text/event-stream")


if __name__ == "__main__":
    # 파일명을 변경해도 실행되도록 동적으로 모듈명 설정
    # 테스트
    module_name = Path(__file__).stem
    uvicorn.run(f"{module_name}:app", host="0.0.0.0", port=8000, reload=True)
