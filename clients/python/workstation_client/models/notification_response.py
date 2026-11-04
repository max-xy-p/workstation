from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast
from uuid import UUID

from attrs import define as _attrs_define

from ..models.notification_resource_type import NotificationResourceType

T = TypeVar("T", bound="NotificationResponse")


@_attrs_define
class NotificationResponse:
    """
    Attributes:
        id (UUID):
        sequence (int):
        workspace_id (int):
        type_ (str):
        title (str):
        body (str):
        resource_type (NotificationResourceType):
        resource_id (int | None):
        created_at (datetime.datetime):
        read_at (datetime.datetime | None):
    """

    id: UUID
    sequence: int
    workspace_id: int
    type_: str
    title: str
    body: str
    resource_type: NotificationResourceType
    resource_id: int | None
    created_at: datetime.datetime
    read_at: datetime.datetime | None

    def to_dict(self) -> dict[str, Any]:
        id = str(self.id)

        sequence = self.sequence

        workspace_id = self.workspace_id

        type_ = self.type_

        title = self.title

        body = self.body

        resource_type = self.resource_type.value

        resource_id: int | None
        resource_id = self.resource_id

        created_at = self.created_at.isoformat()

        read_at: None | str
        if isinstance(self.read_at, datetime.datetime):
            read_at = self.read_at.isoformat()
        else:
            read_at = self.read_at

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "sequence": sequence,
                "workspaceId": workspace_id,
                "type": type_,
                "title": title,
                "body": body,
                "resourceType": resource_type,
                "resourceId": resource_id,
                "createdAt": created_at,
                "readAt": read_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = UUID(d.pop("id"))

        sequence = d.pop("sequence")

        workspace_id = d.pop("workspaceId")

        type_ = d.pop("type")

        title = d.pop("title")

        body = d.pop("body")

        resource_type = NotificationResourceType(d.pop("resourceType"))

        def _parse_resource_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        resource_id = _parse_resource_id(d.pop("resourceId"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        def _parse_read_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                read_at_type_0 = datetime.datetime.fromisoformat(data)

                return read_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        read_at = _parse_read_at(d.pop("readAt"))

        notification_response = cls(
            id=id,
            sequence=sequence,
            workspace_id=workspace_id,
            type_=type_,
            title=title,
            body=body,
            resource_type=resource_type,
            resource_id=resource_id,
            created_at=created_at,
            read_at=read_at,
        )

        return notification_response
