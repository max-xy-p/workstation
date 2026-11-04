from enum import StrEnum


class NotificationResourceType(StrEnum):
    NOTE = "NOTE"
    PROJECT = "PROJECT"
    TASK = "TASK"
    WORKSPACE = "WORKSPACE"

    def __str__(self) -> str:
        return str(self.value)
