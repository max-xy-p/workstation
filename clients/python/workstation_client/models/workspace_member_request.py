from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.principal_type import PrincipalType
from ..models.workspace_role import WorkspaceRole
from ..types import UNSET, Unset

T = TypeVar("T", bound="WorkspaceMemberRequest")


@_attrs_define
class WorkspaceMemberRequest:
    """
    Attributes:
        subject_id (str):
        principal_type (PrincipalType):
        role (WorkspaceRole):
        username (None | str | Unset):
        display_name (None | str | Unset):
    """

    subject_id: str
    principal_type: PrincipalType
    role: WorkspaceRole
    username: None | str | Unset = UNSET
    display_name: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        subject_id = self.subject_id

        principal_type = self.principal_type.value

        role = self.role.value

        username: None | str | Unset
        if isinstance(self.username, Unset):
            username = UNSET
        else:
            username = self.username

        display_name: None | str | Unset
        if isinstance(self.display_name, Unset):
            display_name = UNSET
        else:
            display_name = self.display_name

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "subjectId": subject_id,
                "principalType": principal_type,
                "role": role,
            }
        )
        if username is not UNSET:
            field_dict["username"] = username
        if display_name is not UNSET:
            field_dict["displayName"] = display_name

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject_id = d.pop("subjectId")

        principal_type = PrincipalType(d.pop("principalType"))

        role = WorkspaceRole(d.pop("role"))

        def _parse_username(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        username = _parse_username(d.pop("username", UNSET))

        def _parse_display_name(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        display_name = _parse_display_name(d.pop("displayName", UNSET))

        workspace_member_request = cls(
            subject_id=subject_id,
            principal_type=principal_type,
            role=role,
            username=username,
            display_name=display_name,
        )

        return workspace_member_request
