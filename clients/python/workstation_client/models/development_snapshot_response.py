from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.development_kind import DevelopmentKind

T = TypeVar("T", bound="DevelopmentSnapshotResponse")


@_attrs_define
class DevelopmentSnapshotResponse:
    """
    Attributes:
        repository_id (int):
        kind (DevelopmentKind):
        available (bool):
        truncated (bool):
        message (None | str):
        since (datetime.datetime | None):
        synced_at (datetime.datetime | None):
        collected (int):
        open_count (int):
    """

    repository_id: int
    kind: DevelopmentKind
    available: bool
    truncated: bool
    message: None | str
    since: datetime.datetime | None
    synced_at: datetime.datetime | None
    collected: int
    open_count: int

    def to_dict(self) -> dict[str, Any]:
        repository_id = self.repository_id

        kind = self.kind.value

        available = self.available

        truncated = self.truncated

        message: None | str
        message = self.message

        since: None | str
        if isinstance(self.since, datetime.datetime):
            since = self.since.isoformat()
        else:
            since = self.since

        synced_at: None | str
        if isinstance(self.synced_at, datetime.datetime):
            synced_at = self.synced_at.isoformat()
        else:
            synced_at = self.synced_at

        collected = self.collected

        open_count = self.open_count

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "repositoryId": repository_id,
                "kind": kind,
                "available": available,
                "truncated": truncated,
                "message": message,
                "since": since,
                "syncedAt": synced_at,
                "collected": collected,
                "openCount": open_count,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        repository_id = d.pop("repositoryId")

        kind = DevelopmentKind(d.pop("kind"))

        available = d.pop("available")

        truncated = d.pop("truncated")

        def _parse_message(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        message = _parse_message(d.pop("message"))

        def _parse_since(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                since_type_0 = datetime.datetime.fromisoformat(data)

                return since_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        since = _parse_since(d.pop("since"))

        def _parse_synced_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                synced_at_type_0 = datetime.datetime.fromisoformat(data)

                return synced_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        synced_at = _parse_synced_at(d.pop("syncedAt"))

        collected = d.pop("collected")

        open_count = d.pop("openCount")

        development_snapshot_response = cls(
            repository_id=repository_id,
            kind=kind,
            available=available,
            truncated=truncated,
            message=message,
            since=since,
            synced_at=synced_at,
            collected=collected,
            open_count=open_count,
        )

        return development_snapshot_response
