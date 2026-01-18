from fastapi import FastAPI
from pydantic import BaseModel
from pypdf import PdfReader
from pathlib import Path
import uvicorn

app = FastAPI()

class PingResponse(BaseModel):
    status: str

@app.get("/ping", response_model=PingResponse)
def ping():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)

class IngestRequset(BaseModel):
    document_id: int
    file_path: str
    title: str | None = None

class IngestResponse(BaseModel):
    document_id: int
    received: bool
    message: str
    page_count: int | None = None
    extracted_chars : int

def extract_pdf_text(file_path: str):
    p = Path(file_path)

    if not p.exists():
        raise FileNotFoundError(f"File not found: {p}")
    
    reader = PdfReader(str(p))
    page_count = len(reader.pages)
    
    texts =[]
    for i ,page in enumerate(reader.pages):
        text = page.extract_text() or ""
        if text:
            texts.append(text)
    
    full_text = "\n".join(texts)
    return full_text, page_count





@app.post("/ingest", response_model=IngestResponse)
def ingest(req: IngestRequset):
    print("📄 Ingest 요청 도착:")
    print(f"  - document_id: {req.document_id}")
    print(f"  - file_path  : {req.file_path}")
    print(f"  - title      : {req.title}")

    try:
        text, page_count = extract_pdf_text(req.file_path)

        # 너무 길면 콘솔에 다 찍지 말고 앞부분만
        preview = text[:800].replace("\n", " ")
        print(f"✅ PDF 로드 성공: pages={page_count}, chars={len(text)}")
        print(f"🔎 텍스트 미리보기(앞 800자): {preview}")

        return IngestResponse(
            document_id=req.document_id,
            received=True,
            message="PDF loaded & text extracted (preview logged)",
            page_count=page_count,
            extracted_chars=len(text),
        )

    except FileNotFoundError as e:
        print("❌ 파일 경로 문제:", str(e))
        raise HTTPException(status_code=400, detail=str(e))

    except Exception as e:
        print("❌ PDF 처리 실패:", str(e))
        raise HTTPException(status_code=500, detail=f"PDF processing failed: {e}")