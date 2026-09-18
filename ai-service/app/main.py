from fastapi import Depends, FastAPI, HTTPException, UploadFile
from pydantic import BaseModel

from app.llm.provider import LLMProvider, get_provider

app = FastAPI(
    title="Orthiva AI Service",
    version="0.1.0",
    description="Vision on dental photos, RAG assistant and 3D mesh processing. "
    "Phase 0: only /health and /v1/llm/* are functional; the rest are stubs.",
)


# --------------------------------------------------------------------------- health
@app.get("/health")
async def health() -> dict:
    return {"status": "UP", "service": "orthiva-ai"}


@app.get("/v1/llm/health")
async def llm_health(llm: LLMProvider = Depends(get_provider)) -> dict:
    try:
        return await llm.health()
    except Exception as exc:  # Ollama down or unreachable
        raise HTTPException(status_code=503, detail=f"LLM provider unavailable: {exc}") from exc


# --------------------------------------------------------------------------- llm
class PingRequest(BaseModel):
    prompt: str = "Responde en una frase: ¿qué es un alineador dental?"


class PingResponse(BaseModel):
    answer: str


@app.post("/v1/llm/ping", response_model=PingResponse)
async def llm_ping(body: PingRequest, llm: LLMProvider = Depends(get_provider)) -> PingResponse:
    """Real round-trip to the local LLM. Proves the provider wiring works."""
    try:
        return PingResponse(answer=await llm.chat(body.prompt))
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"LLM call failed: {exc}") from exc


# --------------------------------------------------------------------------- stubs (Phase 2)
@app.post("/v1/vision/analyze", status_code=501)
async def vision_analyze(file: UploadFile) -> dict:
    """Pre-diagnosis from intraoral / smile photos (tooth detection, crowding, midline...)."""
    raise HTTPException(status_code=501, detail="Not implemented yet (Phase 2)")


@app.post("/v1/rag/ask", status_code=501)
async def rag_ask() -> dict:
    """Clinical assistant over protocols, docs and anonymised cases (pgvector)."""
    raise HTTPException(status_code=501, detail="Not implemented yet (Phase 2)")


@app.post("/v1/mesh/segment", status_code=501)
async def mesh_segment(file: UploadFile) -> dict:
    """Tooth segmentation + FDI labeling on an intraoral STL scan."""
    raise HTTPException(status_code=501, detail="Not implemented yet (Phase 2)")
