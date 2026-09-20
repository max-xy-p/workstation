from enum import StrEnum


class PrincipalType(StrEnum):
    SERVICE = "SERVICE"
    USER = "USER"

    def __str__(self) -> str:
        return str(self.value)
