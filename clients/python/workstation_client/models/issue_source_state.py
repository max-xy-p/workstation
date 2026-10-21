from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

T = TypeVar("T", bound="IssueSourceState")


@_attrs_define
class IssueSourceState:
    """
    Attributes:
        title (str):
        status (str):
        state_reason (None | str):
    """

    title: str
    status: str
    state_reason: None | str

    def to_dict(self) -> dict[str, Any]:
        title = self.title

        status = self.status

        state_reason: None | str
        state_reason = self.state_reason

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "title": title,
                "status": status,
                "stateReason": state_reason,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        title = d.pop("title")

        status = d.pop("status")

        def _parse_state_reason(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        state_reason = _parse_state_reason(d.pop("stateReason"))

        issue_source_state = cls(
            title=title,
            status=status,
            state_reason=state_reason,
        )

        return issue_source_state
