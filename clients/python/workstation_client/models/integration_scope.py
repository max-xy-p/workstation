from enum import StrEnum


class IntegrationScope(StrEnum):
    ACCESSIBLE = "ACCESSIBLE"
    OWNED = "OWNED"

    def __str__(self) -> str:
        return str(self.value)
