from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..models.invitation_request_role import InvitationRequestRole

T = TypeVar("T", bound="InvitationRequest")


@_attrs_define
class InvitationRequest:
    """
    Attributes:
        request_id (UUID):
        email (str):
        role (InvitationRequestRole):
    """

    request_id: UUID
    email: str
    role: InvitationRequestRole

    def to_dict(self) -> dict[str, Any]:
        request_id = str(self.request_id)

        email = self.email

        role = self.role.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "requestId": request_id,
                "email": email,
                "role": role,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        request_id = UUID(d.pop("requestId"))

        email = d.pop("email")

        role = InvitationRequestRole(d.pop("role"))

        invitation_request = cls(
            request_id=request_id,
            email=email,
            role=role,
        )

        return invitation_request
