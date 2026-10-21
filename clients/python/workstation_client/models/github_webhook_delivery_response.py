from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.github_webhook_delivery_response_status import GithubWebhookDeliveryResponseStatus

T = TypeVar("T", bound="GithubWebhookDeliveryResponse")


@_attrs_define
class GithubWebhookDeliveryResponse:
    """
    Attributes:
        id (int):
        delivery_key (str):
        event_type (str):
        action (None | str):
        status (GithubWebhookDeliveryResponseStatus):
        attempts (int):
        run_id (int | None):
        last_error (None | str):
        received_at (datetime.datetime):
    """

    id: int
    delivery_key: str
    event_type: str
    action: None | str
    status: GithubWebhookDeliveryResponseStatus
    attempts: int
    run_id: int | None
    last_error: None | str
    received_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        delivery_key = self.delivery_key

        event_type = self.event_type

        action: None | str
        action = self.action

        status = self.status.value

        attempts = self.attempts

        run_id: int | None
        run_id = self.run_id

        last_error: None | str
        last_error = self.last_error

        received_at = self.received_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "deliveryKey": delivery_key,
                "eventType": event_type,
                "action": action,
                "status": status,
                "attempts": attempts,
                "runId": run_id,
                "lastError": last_error,
                "receivedAt": received_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        delivery_key = d.pop("deliveryKey")

        event_type = d.pop("eventType")

        def _parse_action(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        action = _parse_action(d.pop("action"))

        status = GithubWebhookDeliveryResponseStatus(d.pop("status"))

        attempts = d.pop("attempts")

        def _parse_run_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        run_id = _parse_run_id(d.pop("runId"))

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

        received_at = datetime.datetime.fromisoformat(d.pop("receivedAt"))

        github_webhook_delivery_response = cls(
            id=id,
            delivery_key=delivery_key,
            event_type=event_type,
            action=action,
            status=status,
            attempts=attempts,
            run_id=run_id,
            last_error=last_error,
            received_at=received_at,
        )

        return github_webhook_delivery_response
