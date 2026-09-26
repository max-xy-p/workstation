from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.deleted_note_response import DeletedNoteResponse


T = TypeVar("T", bound="DeletedNotePageResponse")


@_attrs_define
class DeletedNotePageResponse:
    """
    Attributes:
        items (list[DeletedNoteResponse]):
        page (int):
        size (int):
        total (int):
        has_more (bool):
    """

    items: list[DeletedNoteResponse]
    page: int
    size: int
    total: int
    has_more: bool

    def to_dict(self) -> dict[str, Any]:
        items = []
        for items_item_data in self.items:
            items_item = items_item_data.to_dict()
            items.append(items_item)

        page = self.page

        size = self.size

        total = self.total

        has_more = self.has_more

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "items": items,
                "page": page,
                "size": size,
                "total": total,
                "hasMore": has_more,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.deleted_note_response import DeletedNoteResponse  # noqa: PLC0415

        d = dict(src_dict)
        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = DeletedNoteResponse.from_dict(items_item_data)

            items.append(items_item)

        page = d.pop("page")

        size = d.pop("size")

        total = d.pop("total")

        has_more = d.pop("hasMore")

        deleted_note_page_response = cls(
            items=items,
            page=page,
            size=size,
            total=total,
            has_more=has_more,
        )

        return deleted_note_page_response
