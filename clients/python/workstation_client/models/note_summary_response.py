from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.tag_response import TagResponse


T = TypeVar("T", bound="NoteSummaryResponse")


@_attrs_define
class NoteSummaryResponse:
    """
    Attributes:
        id (int):
        title (str):
        folder_id (int | None):
        folder_name (None | str):
        project_id (int | None):
        project_name (None | str):
        version (int):
        tags (list[TagResponse]):
        created_at (datetime.datetime):
        updated_at (datetime.datetime):
    """

    id: int
    title: str
    folder_id: int | None
    folder_name: None | str
    project_id: int | None
    project_name: None | str
    version: int
    tags: list[TagResponse]
    created_at: datetime.datetime
    updated_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        title = self.title

        folder_id: int | None
        folder_id = self.folder_id

        folder_name: None | str
        folder_name = self.folder_name

        project_id: int | None
        project_id = self.project_id

        project_name: None | str
        project_name = self.project_name

        version = self.version

        tags = []
        for tags_item_data in self.tags:
            tags_item = tags_item_data.to_dict()
            tags.append(tags_item)

        created_at = self.created_at.isoformat()

        updated_at = self.updated_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "title": title,
                "folderId": folder_id,
                "folderName": folder_name,
                "projectId": project_id,
                "projectName": project_name,
                "version": version,
                "tags": tags,
                "createdAt": created_at,
                "updatedAt": updated_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.tag_response import TagResponse  # noqa: PLC0415

        d = dict(src_dict)
        id = d.pop("id")

        title = d.pop("title")

        def _parse_folder_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        folder_id = _parse_folder_id(d.pop("folderId"))

        def _parse_folder_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        folder_name = _parse_folder_name(d.pop("folderName"))

        def _parse_project_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        project_id = _parse_project_id(d.pop("projectId"))

        def _parse_project_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        project_name = _parse_project_name(d.pop("projectName"))

        version = d.pop("version")

        tags = []
        _tags = d.pop("tags")
        for tags_item_data in _tags:
            tags_item = TagResponse.from_dict(tags_item_data)

            tags.append(tags_item)

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        updated_at = datetime.datetime.fromisoformat(d.pop("updatedAt"))

        note_summary_response = cls(
            id=id,
            title=title,
            folder_id=folder_id,
            folder_name=folder_name,
            project_id=project_id,
            project_name=project_name,
            version=version,
            tags=tags,
            created_at=created_at,
            updated_at=updated_at,
        )

        return note_summary_response
