from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="NotificationState")


@_attrs_define
class NotificationState:
    """
    Attributes:
        enabled (bool):
        unread_count (int):
        latest_sequence (int):
        revision (int):
    """

    enabled: bool
    unread_count: int
    latest_sequence: int
    revision: int

    def to_dict(self) -> dict[str, Any]:
        enabled = self.enabled

        unread_count = self.unread_count

        latest_sequence = self.latest_sequence

        revision = self.revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "enabled": enabled,
                "unreadCount": unread_count,
                "latestSequence": latest_sequence,
                "revision": revision,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        enabled = d.pop("enabled")

        unread_count = d.pop("unreadCount")

        latest_sequence = d.pop("latestSequence")

        revision = d.pop("revision")

        notification_state = cls(
            enabled=enabled,
            unread_count=unread_count,
            latest_sequence=latest_sequence,
            revision=revision,
        )

        return notification_state
