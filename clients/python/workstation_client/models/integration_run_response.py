from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.integration_run_response_kind import IntegrationRunResponseKind
from ..models.integration_run_response_status import IntegrationRunResponseStatus

T = TypeVar("T", bound="IntegrationRunResponse")


@_attrs_define
class IntegrationRunResponse:
    """
    Attributes:
        id (int):
        kind (IntegrationRunResponseKind):
        status (IntegrationRunResponseStatus):
        processed (int):
        failed (int):
        last_error (None | str):
        created_at (datetime.datetime | None):
        finished_at (datetime.datetime | None):
    """

    id: int
    kind: IntegrationRunResponseKind
    status: IntegrationRunResponseStatus
    processed: int
    failed: int
    last_error: None | str
    created_at: datetime.datetime | None
    finished_at: datetime.datetime | None

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        kind = self.kind.value

        status = self.status.value

        processed = self.processed

        failed = self.failed

        last_error: None | str
        last_error = self.last_error

        created_at: None | str
        if isinstance(self.created_at, datetime.datetime):
            created_at = self.created_at.isoformat()
        else:
            created_at = self.created_at

        finished_at: None | str
        if isinstance(self.finished_at, datetime.datetime):
            finished_at = self.finished_at.isoformat()
        else:
            finished_at = self.finished_at

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "kind": kind,
                "status": status,
                "processed": processed,
                "failed": failed,
                "lastError": last_error,
                "createdAt": created_at,
                "finishedAt": finished_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        kind = IntegrationRunResponseKind(d.pop("kind"))

        status = IntegrationRunResponseStatus(d.pop("status"))

        processed = d.pop("processed")

        failed = d.pop("failed")

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

        def _parse_created_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                created_at_type_0 = datetime.datetime.fromisoformat(data)

                return created_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        created_at = _parse_created_at(d.pop("createdAt"))

        def _parse_finished_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                finished_at_type_0 = datetime.datetime.fromisoformat(data)

                return finished_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        finished_at = _parse_finished_at(d.pop("finishedAt"))

        integration_run_response = cls(
            id=id,
            kind=kind,
            status=status,
            processed=processed,
            failed=failed,
            last_error=last_error,
            created_at=created_at,
            finished_at=finished_at,
        )

        return integration_run_response
