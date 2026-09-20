from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.workspace_permission import WorkspacePermission
from ..models.workspace_role import WorkspaceRole
from ..models.workspace_type import WorkspaceType

T = TypeVar("T", bound="WorkspaceResponse")


@_attrs_define
class WorkspaceResponse:
    """
    Attributes:
        id (int):
        name (str):
        type_ (WorkspaceType):
        role (WorkspaceRole):
        permissions (list[WorkspacePermission]):
        created_at (datetime.datetime):
        updated_at (datetime.datetime):
    """

    id: int
    name: str
    type_: WorkspaceType
    role: WorkspaceRole
    permissions: list[WorkspacePermission]
    created_at: datetime.datetime
    updated_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        name = self.name

        type_ = self.type_.value

        role = self.role.value

        permissions = []
        for permissions_item_data in self.permissions:
            permissions_item = permissions_item_data.value
            permissions.append(permissions_item)

        created_at = self.created_at.isoformat()

        updated_at = self.updated_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "name": name,
                "type": type_,
                "role": role,
                "permissions": permissions,
                "createdAt": created_at,
                "updatedAt": updated_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        name = d.pop("name")

        type_ = WorkspaceType(d.pop("type"))

        role = WorkspaceRole(d.pop("role"))

        permissions = []
        _permissions = d.pop("permissions")
        for permissions_item_data in _permissions:
            permissions_item = WorkspacePermission(permissions_item_data)

            permissions.append(permissions_item)

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        updated_at = datetime.datetime.fromisoformat(d.pop("updatedAt"))

        workspace_response = cls(
            id=id,
            name=name,
            type_=type_,
            role=role,
            permissions=permissions,
            created_at=created_at,
            updated_at=updated_at,
        )

        return workspace_response
