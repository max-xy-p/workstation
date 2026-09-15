from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.note_import_status import NoteImportStatus

T = TypeVar("T", bound="NoteImportItemResponse")


@_attrs_define
class NoteImportItemResponse:
    """
    Attributes:
        file_path (str):
        title (None | str):
        status (NoteImportStatus):
        message (None | str):
        note_id (int | None):
    """

    file_path: str
    title: None | str
    status: NoteImportStatus
    message: None | str
    note_id: int | None

    def to_dict(self) -> dict[str, Any]:
        file_path = self.file_path

        title: None | str
        title = self.title

        status = self.status.value

        message: None | str
        message = self.message

        note_id: int | None
        note_id = self.note_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "filePath": file_path,
                "title": title,
                "status": status,
                "message": message,
                "noteId": note_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        file_path = d.pop("filePath")

        def _parse_title(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        title = _parse_title(d.pop("title"))

        status = NoteImportStatus(d.pop("status"))

        def _parse_message(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        message = _parse_message(d.pop("message"))

        def _parse_note_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        note_id = _parse_note_id(d.pop("noteId"))

        note_import_item_response = cls(
            file_path=file_path,
            title=title,
            status=status,
            message=message,
            note_id=note_id,
        )

        return note_import_item_response
