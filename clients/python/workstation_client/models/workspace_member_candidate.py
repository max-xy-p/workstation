from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

T = TypeVar("T", bound="WorkspaceMemberCandidate")


@_attrs_define
class WorkspaceMemberCandidate:
    """
    Attributes:
        subject_id (str):
        username (str):
        display_name (None | str):
    """

    subject_id: str
    username: str
    display_name: None | str

    def to_dict(self) -> dict[str, Any]:
        subject_id = self.subject_id

        username = self.username

        display_name: None | str
        display_name = self.display_name

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "subjectId": subject_id,
                "username": username,
                "displayName": display_name,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject_id = d.pop("subjectId")

        username = d.pop("username")

        def _parse_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        display_name = _parse_display_name(d.pop("displayName"))

        workspace_member_candidate = cls(
            subject_id=subject_id,
            username=username,
            display_name=display_name,
        )

        return workspace_member_candidate
