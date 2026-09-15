from enum import StrEnum


class NoteImportDuplicateStrategy(StrEnum):
    CREATE_NEW = "CREATE_NEW"
    SKIP_TITLE = "SKIP_TITLE"

    def __str__(self) -> str:
        return str(self.value)
