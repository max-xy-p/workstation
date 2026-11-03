from enum import StrEnum


class InvitationResponseMailStatus(StrEnum):
    ACCEPTED = "ACCEPTED"
    CANCELLED = "CANCELLED"
    EXPIRED = "EXPIRED"
    PENDING = "PENDING"
    PUBLISHED = "PUBLISHED"

    def __str__(self) -> str:
        return str(self.value)
