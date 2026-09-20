from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.principal_type import PrincipalType
from ..models.workspace_role import WorkspaceRole

T = TypeVar("T", bound="WorkspaceMemberResponse")


@_attrs_define
class WorkspaceMemberResponse:
    """
    Attributes:
        subject_id (str):
        principal_type (PrincipalType):
        username (None | str):
        display_name (None | str):
        role (WorkspaceRole):
        joined_at (datetime.datetime):
    """

    subject_id: str
    principal_type: PrincipalType
    username: None | str
    display_name: None | str
    role: WorkspaceRole
    joined_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        subject_id = self.subject_id

        principal_type = self.principal_type.value

        username: None | str
        username = self.username

        display_name: None | str
        display_name = self.display_name

        role = self.role.value

        joined_at = self.joined_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "subjectId": subject_id,
                "principalType": principal_type,
                "username": username,
                "displayName": display_name,
                "role": role,
                "joinedAt": joined_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject_id = d.pop("subjectId")

        principal_type = PrincipalType(d.pop("principalType"))

        def _parse_username(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        username = _parse_username(d.pop("username"))

        def _parse_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        display_name = _parse_display_name(d.pop("displayName"))

        role = WorkspaceRole(d.pop("role"))

        joined_at = datetime.datetime.fromisoformat(d.pop("joinedAt"))

        workspace_member_response = cls(
            subject_id=subject_id,
            principal_type=principal_type,
            username=username,
            display_name=display_name,
            role=role,
            joined_at=joined_at,
        )

        return workspace_member_response
