from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment / .env."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    ollama_base_url: str = "http://localhost:11434"
    llm_chat_model: str = "llama3.2:3b"
    llm_vision_model: str = "qwen2.5vl:7b"
    embedding_model: str = "nomic-embed-text"
    core_base_url: str = "http://localhost:8080"


settings = Settings()
