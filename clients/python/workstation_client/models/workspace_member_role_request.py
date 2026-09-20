from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.workspace_role import WorkspaceRole

T = TypeVar("T", bound="WorkspaceMemberRoleRequest")


@_attrs_define
class WorkspaceMemberRoleRequest:
    """
    Attributes:
        role (WorkspaceRole):
    """

    role: WorkspaceRole

    def to_dict(self) -> dict[str, Any]:
        role = self.role.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "role": role,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        role = WorkspaceRole(d.pop("role"))

        workspace_member_role_request = cls(
            role=role,
        )

        return workspace_member_role_request
