import os
import shlex
from pathlib import Path
from typing import List


class Config:
    ROOT_DIR: Path = Path(__file__).resolve().parents[3]
    COMPOSE_CMD: List[str] = shlex.split(os.environ.get("COMPOSE", "docker compose"))
    _gradlew_raw = Path(os.environ.get("GRADLEW", str(ROOT_DIR / "backend" / "dsl-platform" / "gradlew")))
    GRADLEW: Path = (_gradlew_raw if _gradlew_raw.is_absolute() else ROOT_DIR / _gradlew_raw).resolve()
    BACKEND_BUILDS: List[str] = os.environ.get(
        "BACKEND_BUILDS", "dsl-platform dsl-starter dsl-plugins"
    ).split()
    SERVER_PORT: str = os.environ.get("SERVER_PORT", "8090")
    BACKEND_BASE_URL: str = os.environ.get(
        "BACKEND_BASE_URL", f"http://localhost:{SERVER_PORT}"
    )
    BFF_BASE_URL: str = os.environ.get("BFF_BASE_URL", "http://localhost:3000")
