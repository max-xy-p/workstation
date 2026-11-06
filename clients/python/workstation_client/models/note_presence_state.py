from enum import StrEnum


class NotePresenceState(StrEnum):
    EDITING = "EDITING"
    LEFT = "LEFT"
    VIEWING = "VIEWING"

    def __str__(self) -> str:
        return str(self.value)
