from enum import StrEnum


class AuditResult(StrEnum):
    DENIED = "DENIED"
    SUCCESS = "SUCCESS"

    def __str__(self) -> str:
        return str(self.value)
