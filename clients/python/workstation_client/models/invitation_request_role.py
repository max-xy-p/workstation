from enum import StrEnum


class InvitationRequestRole(StrEnum):
    ADMIN = "ADMIN"
    EDITOR = "EDITOR"
    VIEWER = "VIEWER"

    def __str__(self) -> str:
        return str(self.value)
