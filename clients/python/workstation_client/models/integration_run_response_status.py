from enum import StrEnum


class IntegrationRunResponseStatus(StrEnum):
    CANCELLED = "CANCELLED"
    FAILED = "FAILED"
    PARTIAL = "PARTIAL"
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"

    def __str__(self) -> str:
        return str(self.value)
