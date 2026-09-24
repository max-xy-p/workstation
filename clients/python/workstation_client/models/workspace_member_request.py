from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.workspace_role import WorkspaceRole

T = TypeVar("T", bound="WorkspaceMemberRequest")


@_attrs_define
class WorkspaceMemberRequest:
    """
    Attributes:
        subject_id (str):
        role (WorkspaceRole):
    """

    subject_id: str
    role: WorkspaceRole

    def to_dict(self) -> dict[str, Any]:
        subject_id = self.subject_id

        role = self.role.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "subjectId": subject_id,
                "role": role,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject_id = d.pop("subjectId")

        role = WorkspaceRole(d.pop("role"))

        workspace_member_request = cls(
            subject_id=subject_id,
            role=role,
        )

        return workspace_member_request
