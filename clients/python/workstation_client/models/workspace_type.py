from enum import StrEnum


class WorkspaceType(StrEnum):
    PERSONAL = "PERSONAL"
    SHARED = "SHARED"

    def __str__(self) -> str:
        return str(self.value)
