from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.audit_result import AuditResult
from ..models.principal_type import PrincipalType

T = TypeVar("T", bound="AuditEventResponse")


@_attrs_define
class AuditEventResponse:
    """
    Attributes:
        id (int):
        actor_subject (str):
        actor_type (PrincipalType):
        action (str):
        resource_type (str):
        resource_id (int | None):
        result (AuditResult):
        occurred_at (datetime.datetime):
        trace_id (None | str):
    """

    id: int
    actor_subject: str
    actor_type: PrincipalType
    action: str
    resource_type: str
    resource_id: int | None
    result: AuditResult
    occurred_at: datetime.datetime
    trace_id: None | str

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        actor_subject = self.actor_subject

        actor_type = self.actor_type.value

        action = self.action

        resource_type = self.resource_type

        resource_id: int | None
        resource_id = self.resource_id

        result = self.result.value

        occurred_at = self.occurred_at.isoformat()

        trace_id: None | str
        trace_id = self.trace_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "actorSubject": actor_subject,
                "actorType": actor_type,
                "action": action,
                "resourceType": resource_type,
                "resourceId": resource_id,
                "result": result,
                "occurredAt": occurred_at,
                "traceId": trace_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        actor_subject = d.pop("actorSubject")

        actor_type = PrincipalType(d.pop("actorType"))

        action = d.pop("action")

        resource_type = d.pop("resourceType")

        def _parse_resource_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        resource_id = _parse_resource_id(d.pop("resourceId"))

        result = AuditResult(d.pop("result"))

        occurred_at = datetime.datetime.fromisoformat(d.pop("occurredAt"))

        def _parse_trace_id(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        trace_id = _parse_trace_id(d.pop("traceId"))

        audit_event_response = cls(
            id=id,
            actor_subject=actor_subject,
            actor_type=actor_type,
            action=action,
            resource_type=resource_type,
            resource_id=resource_id,
            result=result,
            occurred_at=occurred_at,
            trace_id=trace_id,
        )

        return audit_event_response
