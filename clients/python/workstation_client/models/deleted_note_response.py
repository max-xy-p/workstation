from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.tag_response import TagResponse


T = TypeVar("T", bound="DeletedNoteResponse")


@_attrs_define
class DeletedNoteResponse:
    """
    Attributes:
        id (int):
        title (str):
        excerpt (None | str): Markdown 正文开头或关键词附近的片段，最长 200 字符
        folder_id (int | None):
        folder_name (None | str):
        project_id (int | None):
        project_name (None | str):
        version (int):
        tags (list[TagResponse]):
        deleted_at (datetime.datetime):
        deleted_by_subject (str):
        deleted_by_display_name (None | str):
    """

    id: int
    title: str
    excerpt: None | str
    folder_id: int | None
    folder_name: None | str
    project_id: int | None
    project_name: None | str
    version: int
    tags: list[TagResponse]
    deleted_at: datetime.datetime
    deleted_by_subject: str
    deleted_by_display_name: None | str

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        title = self.title

        excerpt: None | str
        excerpt = self.excerpt

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

        deleted_at = self.deleted_at.isoformat()

        deleted_by_subject = self.deleted_by_subject

        deleted_by_display_name: None | str
        deleted_by_display_name = self.deleted_by_display_name

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "title": title,
                "excerpt": excerpt,
                "folderId": folder_id,
                "folderName": folder_name,
                "projectId": project_id,
                "projectName": project_name,
                "version": version,
                "tags": tags,
                "deletedAt": deleted_at,
                "deletedBySubject": deleted_by_subject,
                "deletedByDisplayName": deleted_by_display_name,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.tag_response import TagResponse  # noqa: PLC0415

        d = dict(src_dict)
        id = d.pop("id")

        title = d.pop("title")

        def _parse_excerpt(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        excerpt = _parse_excerpt(d.pop("excerpt"))

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

        deleted_at = datetime.datetime.fromisoformat(d.pop("deletedAt"))

        deleted_by_subject = d.pop("deletedBySubject")

        def _parse_deleted_by_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        deleted_by_display_name = _parse_deleted_by_display_name(d.pop("deletedByDisplayName"))

        deleted_note_response = cls(
            id=id,
            title=title,
            excerpt=excerpt,
            folder_id=folder_id,
            folder_name=folder_name,
            project_id=project_id,
            project_name=project_name,
            version=version,
            tags=tags,
            deleted_at=deleted_at,
            deleted_by_subject=deleted_by_subject,
            deleted_by_display_name=deleted_by_display_name,
        )

        return deleted_note_response
