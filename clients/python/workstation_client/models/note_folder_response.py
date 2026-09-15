from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

T = TypeVar("T", bound="NoteFolderResponse")


@_attrs_define
class NoteFolderResponse:
    """
    Attributes:
        id (int):
        name (str):
        parent_id (int | None):
        created_at (datetime.datetime):
        updated_at (datetime.datetime):
    """

    id: int
    name: str
    parent_id: int | None
    created_at: datetime.datetime
    updated_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        name = self.name

        parent_id: int | None
        parent_id = self.parent_id

        created_at = self.created_at.isoformat()

        updated_at = self.updated_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "name": name,
                "parentId": parent_id,
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

        def _parse_parent_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        parent_id = _parse_parent_id(d.pop("parentId"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        updated_at = datetime.datetime.fromisoformat(d.pop("updatedAt"))

        note_folder_response = cls(
            id=id,
            name=name,
            parent_id=parent_id,
            created_at=created_at,
            updated_at=updated_at,
        )

        return note_folder_response
