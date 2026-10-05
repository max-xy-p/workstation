from enum import StrEnum


class DevelopmentKind(StrEnum):
    CHANGE_REQUEST = "CHANGE_REQUEST"
    COMMIT = "COMMIT"
    ISSUE = "ISSUE"
    PIPELINE = "PIPELINE"

    def __str__(self) -> str:
        return str(self.value)
