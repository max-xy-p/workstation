from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..models.invitation_response_mail_status import InvitationResponseMailStatus
from ..models.invitation_response_status import InvitationResponseStatus
from ..models.workspace_role import WorkspaceRole

T = TypeVar("T", bound="InvitationResponse")


@_attrs_define
class InvitationResponse:
    """
    Attributes:
        id (UUID):
        workspace_id (int):
        workspace_name (str):
        email (str):
        role (WorkspaceRole):
        status (InvitationResponseStatus):
        mail_status (InvitationResponseMailStatus):
        created_at (datetime.datetime):
        expires_at (datetime.datetime):
    """

    id: UUID
    workspace_id: int
    workspace_name: str
    email: str
    role: WorkspaceRole
    status: InvitationResponseStatus
    mail_status: InvitationResponseMailStatus
    created_at: datetime.datetime
    expires_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = str(self.id)

        workspace_id = self.workspace_id

        workspace_name = self.workspace_name

        email = self.email

        role = self.role.value

        status = self.status.value

        mail_status = self.mail_status.value

        created_at = self.created_at.isoformat()

        expires_at = self.expires_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "workspaceId": workspace_id,
                "workspaceName": workspace_name,
                "email": email,
                "role": role,
                "status": status,
                "mailStatus": mail_status,
                "createdAt": created_at,
                "expiresAt": expires_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = UUID(d.pop("id"))

        workspace_id = d.pop("workspaceId")

        workspace_name = d.pop("workspaceName")

        email = d.pop("email")

        role = WorkspaceRole(d.pop("role"))

        status = InvitationResponseStatus(d.pop("status"))

        mail_status = InvitationResponseMailStatus(d.pop("mailStatus"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        expires_at = datetime.datetime.fromisoformat(d.pop("expiresAt"))

        invitation_response = cls(
            id=id,
            workspace_id=workspace_id,
            workspace_name=workspace_name,
            email=email,
            role=role,
            status=status,
            mail_status=mail_status,
            created_at=created_at,
            expires_at=expires_at,
        )

        return invitation_response
