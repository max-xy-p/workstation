from enum import StrEnum


class ProjectStatus(StrEnum):
    ACTIVE = "ACTIVE"
    ARCHIVED = "ARCHIVED"
    COMPLETED = "COMPLETED"
    PAUSED = "PAUSED"
    PLANNING = "PLANNING"

    def __str__(self) -> str:
        return str(self.value)
