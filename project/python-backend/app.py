from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn

app = FastAPI()

class PingResponse(BaseModel):
    status: str

@app.get("/ping", response_model=PingResponse)
def ping():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
