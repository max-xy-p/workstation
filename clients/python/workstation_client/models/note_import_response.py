from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.note_import_item_response import NoteImportItemResponse


T = TypeVar("T", bound="NoteImportResponse")


@_attrs_define
class NoteImportResponse:
    """
    Attributes:
        imported_count (int):
        skipped_count (int):
        failed_count (int):
        items (list[NoteImportItemResponse]):
    """

    imported_count: int
    skipped_count: int
    failed_count: int
    items: list[NoteImportItemResponse]

    def to_dict(self) -> dict[str, Any]:
        imported_count = self.imported_count

        skipped_count = self.skipped_count

        failed_count = self.failed_count

        items = []
        for items_item_data in self.items:
            items_item = items_item_data.to_dict()
            items.append(items_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "importedCount": imported_count,
                "skippedCount": skipped_count,
                "failedCount": failed_count,
                "items": items,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.note_import_item_response import NoteImportItemResponse  # noqa: PLC0415

        d = dict(src_dict)
        imported_count = d.pop("importedCount")

        skipped_count = d.pop("skippedCount")

        failed_count = d.pop("failedCount")

        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = NoteImportItemResponse.from_dict(items_item_data)

            items.append(items_item)

        note_import_response = cls(
            imported_count=imported_count,
            skipped_count=skipped_count,
            failed_count=failed_count,
            items=items,
        )

        return note_import_response
