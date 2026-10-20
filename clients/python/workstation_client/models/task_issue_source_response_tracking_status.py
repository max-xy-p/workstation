from enum import StrEnum


class TaskIssueSourceResponseTrackingStatus(StrEnum):
    ACTIVE = "ACTIVE"
    ERROR = "ERROR"
    STOPPED = "STOPPED"
    WAITING = "WAITING"

    def __str__(self) -> str:
        return str(self.value)
