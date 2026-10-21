from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.github_webhook_delivery_response import GithubWebhookDeliveryResponse


T = TypeVar("T", bound="GithubWebhookDeliveryPageResponse")


@_attrs_define
class GithubWebhookDeliveryPageResponse:
    """
    Attributes:
        items (list[GithubWebhookDeliveryResponse]):
        page (int):
        size (int):
        total (int):
        has_more (bool):
    """

    items: list[GithubWebhookDeliveryResponse]
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
        from ..models.github_webhook_delivery_response import GithubWebhookDeliveryResponse  # noqa: PLC0415

        d = dict(src_dict)
        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = GithubWebhookDeliveryResponse.from_dict(items_item_data)

            items.append(items_item)

        page = d.pop("page")

        size = d.pop("size")

        total = d.pop("total")

        has_more = d.pop("hasMore")

        github_webhook_delivery_page_response = cls(
            items=items,
            page=page,
            size=size,
            total=total,
            has_more=has_more,
        )

        return github_webhook_delivery_page_response
