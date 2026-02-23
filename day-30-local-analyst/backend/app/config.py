from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.1:8b"

    backend_host: str = "0.0.0.0"
    backend_port: int = 8000
    max_upload_size_mb: int = 1000

    upload_dir: str = "./uploads"
    app_db_path: str = "./data/app.db"

    model_config = {"env_file": ".env", "extra": "ignore"}

    @property
    def max_upload_bytes(self) -> int:
        return self.max_upload_size_mb * 1024 * 1024

    @property
    def upload_path(self) -> Path:
        p = Path(self.upload_dir)
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def db_path(self) -> Path:
        p = Path(self.app_db_path)
        p.parent.mkdir(parents=True, exist_ok=True)
        return p


settings = Settings()
