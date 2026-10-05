from enum import StrEnum


class IntegrationRunResponseKind(StrEnum):
    DISCOVER = "DISCOVER"
    IMPORT = "IMPORT"
    SYNC = "SYNC"

    def __str__(self) -> str:
        return str(self.value)
