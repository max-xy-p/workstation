from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="IssueTaskLinkRequest")


@_attrs_define
class IssueTaskLinkRequest:
    """
    Attributes:
        development_item_id (int):
        sharing_confirmed (bool):
        task_id (int):
    """

    development_item_id: int
    sharing_confirmed: bool
    task_id: int

    def to_dict(self) -> dict[str, Any]:
        development_item_id = self.development_item_id

        sharing_confirmed = self.sharing_confirmed

        task_id = self.task_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "developmentItemId": development_item_id,
                "sharingConfirmed": sharing_confirmed,
                "taskId": task_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        development_item_id = d.pop("developmentItemId")

        sharing_confirmed = d.pop("sharingConfirmed")

        task_id = d.pop("taskId")

        issue_task_link_request = cls(
            development_item_id=development_item_id,
            sharing_confirmed=sharing_confirmed,
            task_id=task_id,
        )

        return issue_task_link_request
