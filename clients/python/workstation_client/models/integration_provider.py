from enum import StrEnum


class IntegrationProvider(StrEnum):
    GITHUB = "GITHUB"
    GITLAB = "GITLAB"

    def __str__(self) -> str:
        return str(self.value)
