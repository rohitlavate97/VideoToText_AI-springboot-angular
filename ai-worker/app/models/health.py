from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, Optional
from pydantic import BaseModel, Field


class ServiceStatus(str, Enum):
    UP = "UP"
    DOWN = "DOWN"
    DEGRADED = "DEGRADED"


class ComponentHealth(BaseModel):
    status: ServiceStatus
    latency_ms: Optional[float] = None
    details: Optional[Dict[str, Any]] = None


class WorkerHealthResponse(BaseModel):
    status: ServiceStatus
    worker_name: str
    worker_version: str
    active_jobs: int = 0
    components: Dict[str, ComponentHealth] = Field(default_factory=dict)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
