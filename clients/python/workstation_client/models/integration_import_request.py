from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.integration_import_item_request import IntegrationImportItemRequest


T = TypeVar("T", bound="IntegrationImportRequest")


@_attrs_define
class IntegrationImportRequest:
    """
    Attributes:
        request_key (str):
        items (list[IntegrationImportItemRequest]):
        sharing_confirmed (bool):
    """

    request_key: str
    items: list[IntegrationImportItemRequest]
    sharing_confirmed: bool

    def to_dict(self) -> dict[str, Any]:
        request_key = self.request_key

        items = []
        for items_item_data in self.items:
            items_item = items_item_data.to_dict()
            items.append(items_item)

        sharing_confirmed = self.sharing_confirmed

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "requestKey": request_key,
                "items": items,
                "sharingConfirmed": sharing_confirmed,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.integration_import_item_request import IntegrationImportItemRequest  # noqa: PLC0415

        d = dict(src_dict)
        request_key = d.pop("requestKey")

        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = IntegrationImportItemRequest.from_dict(items_item_data)

            items.append(items_item)

        sharing_confirmed = d.pop("sharingConfirmed")

        integration_import_request = cls(
            request_key=request_key,
            items=items,
            sharing_confirmed=sharing_confirmed,
        )

        return integration_import_request
