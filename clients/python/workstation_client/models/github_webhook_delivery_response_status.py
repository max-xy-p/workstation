from enum import StrEnum


class GithubWebhookDeliveryResponseStatus(StrEnum):
    CANCELLED = "CANCELLED"
    FAILED = "FAILED"
    IGNORED = "IGNORED"
    QUEUED = "QUEUED"
    RECEIVED = "RECEIVED"
    RETRY = "RETRY"
    SUCCEEDED = "SUCCEEDED"

    def __str__(self) -> str:
        return str(self.value)
