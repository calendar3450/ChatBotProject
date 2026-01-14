from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class PingResponse(BaseModel):
    status: str

@app.get("/ping", response_model=PingResponse)
def ping():
    return {"status": "ok"}
