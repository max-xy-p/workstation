from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="NotificationReadAllRequest")


@_attrs_define
class NotificationReadAllRequest:
    """
    Attributes:
        through_sequence (int):
    """

    through_sequence: int

    def to_dict(self) -> dict[str, Any]:
        through_sequence = self.through_sequence

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "throughSequence": through_sequence,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        through_sequence = d.pop("throughSequence")

        notification_read_all_request = cls(
            through_sequence=through_sequence,
        )

        return notification_read_all_request
