"""LLM provider abstraction.

The rest of the service only talks to `LLMProvider`. Ollama is the zero-cost default for
development; a Claude/Gemini provider can be added later without touching callers.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

import httpx

from app.config import settings


class LLMProvider(ABC):
    @abstractmethod
    async def chat(self, prompt: str, *, system: str | None = None) -> str: ...

    @abstractmethod
    async def describe_image(self, image_b64: str, prompt: str) -> str: ...

    @abstractmethod
    async def embed(self, text: str) -> list[float]: ...

    @abstractmethod
    async def health(self) -> dict: ...


class OllamaProvider(LLMProvider):
    def __init__(self, base_url: str | None = None) -> None:
        self._base = (base_url or settings.ollama_base_url).rstrip("/")

    async def _post(self, path: str, payload: dict, timeout: float = 120) -> dict:
        async with httpx.AsyncClient(timeout=timeout) as client:
            r = await client.post(f"{self._base}{path}", json=payload)
            r.raise_for_status()
            return r.json()

    async def chat(self, prompt: str, *, system: str | None = None) -> str:
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        data = await self._post(
            "/api/chat",
            {"model": settings.llm_chat_model, "messages": messages, "stream": False},
        )
        return data["message"]["content"]

    async def describe_image(self, image_b64: str, prompt: str) -> str:
        data = await self._post(
            "/api/chat",
            {
                "model": settings.llm_vision_model,
                "messages": [{"role": "user", "content": prompt, "images": [image_b64]}],
                "stream": False,
            },
            timeout=300,
        )
        return data["message"]["content"]

    async def embed(self, text: str) -> list[float]:
        data = await self._post("/api/embed", {"model": settings.embedding_model, "input": text})
        return data["embeddings"][0]

    async def health(self) -> dict:
        async with httpx.AsyncClient(timeout=5) as client:
            r = await client.get(f"{self._base}/api/tags")
            r.raise_for_status()
            models = [m["name"] for m in r.json().get("models", [])]

        def ready(name: str) -> bool:
            # Ollama lists untagged models as "<name>:latest".
            return name in models or (":" not in name and f"{name}:latest" in models)

        return {
            "provider": "ollama",
            "base_url": self._base,
            "available_models": models,
            "chat_model_ready": ready(settings.llm_chat_model),
            "vision_model_ready": ready(settings.llm_vision_model),
            "embedding_model_ready": ready(settings.embedding_model),
        }


def get_provider() -> LLMProvider:
    return OllamaProvider()
