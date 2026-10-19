from enum import StrEnum


class NoteRevisionSource(StrEnum):
    BASELINE = "BASELINE"
    CREATE = "CREATE"
    IMPORT = "IMPORT"
    RESTORE = "RESTORE"
    UPDATE = "UPDATE"

    def __str__(self) -> str:
        return str(self.value)
