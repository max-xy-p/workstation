from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..types import UNSET, Unset

T = TypeVar("T", bound="NoteUpdateRequest")


@_attrs_define
class NoteUpdateRequest:
    """
    Attributes:
        title (str):
        content (str):
        expected_version (int):
        folder_id (int | None | Unset):
        project_id (int | None | Unset):
        tag_ids (list[int] | Unset):
    """

    title: str
    content: str
    expected_version: int
    folder_id: int | None | Unset = UNSET
    project_id: int | None | Unset = UNSET
    tag_ids: list[int] | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        title = self.title

        content = self.content

        expected_version = self.expected_version

        folder_id: int | None | Unset
        if isinstance(self.folder_id, Unset):
            folder_id = UNSET
        else:
            folder_id = self.folder_id

        project_id: int | None | Unset
        if isinstance(self.project_id, Unset):
            project_id = UNSET
        else:
            project_id = self.project_id

        tag_ids: list[int] | Unset = UNSET
        if not isinstance(self.tag_ids, Unset):
            tag_ids = self.tag_ids

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "title": title,
                "content": content,
                "expectedVersion": expected_version,
            }
        )
        if folder_id is not UNSET:
            field_dict["folderId"] = folder_id
        if project_id is not UNSET:
            field_dict["projectId"] = project_id
        if tag_ids is not UNSET:
            field_dict["tagIds"] = tag_ids

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        title = d.pop("title")

        content = d.pop("content")

        expected_version = d.pop("expectedVersion")

        def _parse_folder_id(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        folder_id = _parse_folder_id(d.pop("folderId", UNSET))

        def _parse_project_id(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        project_id = _parse_project_id(d.pop("projectId", UNSET))

        tag_ids = cast(list[int], d.pop("tagIds", UNSET))

        note_update_request = cls(
            title=title,
            content=content,
            expected_version=expected_version,
            folder_id=folder_id,
            project_id=project_id,
            tag_ids=tag_ids,
        )

        return note_update_request
