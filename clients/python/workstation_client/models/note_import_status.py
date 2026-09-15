from enum import StrEnum


class NoteImportStatus(StrEnum):
    FAILED = "FAILED"
    IMPORTED = "IMPORTED"
    SKIPPED = "SKIPPED"

    def __str__(self) -> str:
        return str(self.value)
