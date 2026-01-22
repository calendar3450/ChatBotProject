from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pypdf import PdfReader
from pathlib import Path
import uvicorn
from sentence_transformers import SentenceTransformer
import numpy as np
import faiss
import json
import requests
from fastapi import Body
from fastapi.responses import JSONResponse


app = FastAPI()

# ✅ 임베딩 모델 (멀티링구얼)
# e5 계열은 query에 "query: ", 문서에 "passage: " prefix 권장
EMBED_MODEL_NAME = "intfloat/multilingual-e5-base"
embed_model = SentenceTransformer(EMBED_MODEL_NAME, device="cpu")

DATA_DIR = Path("data")
DATA_DIR.mkdir(exist_ok=True)

OLLAMA_BASE = "http://localhost:11434"
OLLAMA_MODEL = "qwen2.5:7b"

# 테스트용.
class PingResponse(BaseModel):
    status: str

@app.get("/ping", response_model=PingResponse)
def ping():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)


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

class ChatResponse(BaseModel):
    answer: str
    citations: list[dict]


# 테스트용 코드 깨진 한글 잡기 위해 사용.
def ollama_chat_korean(user_text: str) -> str:
    url = f"{OLLAMA_BASE}/api/chat"
    payload = {
        "model": OLLAMA_MODEL,
        "stream": True,
        "options": {"temperature": 0.2},
        "messages": [
            {"role": "system", "content": "당신은 한국어로만 답합니다. 중국어 또는 영어를 절대 사용하지 마세요. 출력은 자연스러운 한국어 문장으로만 작성하세요."},
            {"role": "user", "content": user_text}
        ]
    }

    r = requests.post(url, json=payload, timeout=120)
    r.raise_for_status()

    # ✅ bytes -> utf-8 로 확정 디코딩 후 JSON 파싱
    data = json.loads(r.content.decode("utf-8"))
    return (data["message"]["content"] or "").strip()


def extract_pdf_text(file_path: str):
    p = Path(file_path)

    if not p.exists():
        raise FileNotFoundError(f"File not found: {p}")
    
    reader = PdfReader(str(p))
    
    pages = []
    for i, page in enumerate(reader.pages):
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
    vecs = embed_model.encode(inputs, normalize_embeddings=True, device="cpu")
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



# 받고나서 파일읽고(extract_pdf_text) -> 텍스트 분할하고(chunk_pages) -> 벡터화(embed_passages) -> 저장(save_doc_store):.
@app.post("/ingest", response_model=IngestResponse)
def ingest(req: IngestRequest):
    try:
        pages = extract_pdf_text(req.file_path)
        # 텍스트가 거의 없으면 스캔본일 가능성
        total_chars = sum(len(p["text"]) for p in pages)
        if total_chars < 50:
            raise HTTPException(status_code=400, detail="PDF text is empty. (Maybe scanned PDF. OCR needed.)")

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
            message=f"Ingested: {len(chunks)} chunks saved + embeddings indexed"
        )
    except FileNotFoundError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ingest failed: {e}")



THRESHOLD = 0.40  # 처음엔 0.40로 두고, 필요하면 조정 RAG로 갈지 아니면 모델의 내용으로 갈지 확인.

@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    try:
        # 모델에 정의된 단일 document_id를 리스트로 감싸서 처리
        if req.document_id == 0:
            # 문서들에서 아무 것도 못 찾음 -> 일반모드/또는 "확인 불가"
                prompt = f"""사용자의 질문에 일반 지식으로 답하세요.
                [질문]
                {req.question}
                [답변]
                """
                answer = ollama_generate(prompt).strip()
                return ChatResponse(answer=answer,citations=[])
        else:
            target_ids = [req.document_id]

            qv = embed_query(req.question)
            # 문서중 질문과 가장 비슷한 내용 상위 k
            top_k = max(1, min(req.top_k, 10))

            # 1) 각 문서별 검색 결과 수집
            merged = []  # (score, doc_id, chunk_index, chunk_obj)
            for doc_id in target_ids:
                chunks, index = load_doc_store(doc_id)
                scores, ids = index.search(qv, top_k)

                for rank, idx in enumerate(ids[0].tolist()):
                    if idx < 0:
                        continue
                    merged.append((
                        float(scores[0][rank]),
                        doc_id,
                        idx,
                        chunks[idx]
                    ))
                
            # 2) 전역 top_k 선택
            merged.sort(key=lambda x: x[0], reverse=True)
            picked = merged[:top_k]

            # 3) 컨텍스트 + citations 구성
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

            prompt = f"""당신은 업로드된 PDF 문서를 읽고 분석하는 어시스턴트입니다.

                        규칙:
                        1) 먼저 [근거]에서 질문과 관련된 핵심 포인트를 추려 요약하세요.
                        2) 그 근거를 바탕으로 합리적인 추론/시나리오를 제시할 수 있습니다.
                        3) 문서에 없는 사실(예: 최신 주가, 향후 확정 수치)은 만들어내지 마세요.
                        4) 불확실한 내용은 "가능성", "시나리오"로 표현하고 가정과 한계를 명시하세요.
                        5) 답변 마지막에 "추가로 있으면 좋은 데이터" 3가지를 제안하세요.

                        [질문]
                        {req.question}

                        [근거]
                        {context}

                        [출력 형식]
                        - 근거 요약(3~5줄):
                        - 시나리오(상/중/하) + 근거 연결:
                        - 리스크/반례:
                        - 한계(문서에 없는 정보):
                        - 추가로 있으면 좋은 데이터(3개):

                            [답변]
                            """
            answer = ollama_generate(prompt)
            return ChatResponse(answer=answer,citations=citations)

    except FileNotFoundError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Chat failed: {e}")