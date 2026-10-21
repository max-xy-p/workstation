from enum import StrEnum


class IssueSourceConfirmRequestAction(StrEnum):
    COMPLETE = "COMPLETE"
    KEEP = "KEEP"
    REOPEN = "REOPEN"

    def __str__(self) -> str:
        return str(self.value)
