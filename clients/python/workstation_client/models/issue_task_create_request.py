from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.task_request import TaskRequest


T = TypeVar("T", bound="IssueTaskCreateRequest")


@_attrs_define
class IssueTaskCreateRequest:
    """
    Attributes:
        development_item_id (int):
        sharing_confirmed (bool):
        task (TaskRequest):
    """

    development_item_id: int
    sharing_confirmed: bool
    task: TaskRequest

    def to_dict(self) -> dict[str, Any]:
        development_item_id = self.development_item_id

        sharing_confirmed = self.sharing_confirmed

        task = self.task.to_dict()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "developmentItemId": development_item_id,
                "sharingConfirmed": sharing_confirmed,
                "task": task,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.task_request import TaskRequest  # noqa: PLC0415

        d = dict(src_dict)
        development_item_id = d.pop("developmentItemId")

        sharing_confirmed = d.pop("sharingConfirmed")

        task = TaskRequest.from_dict(d.pop("task"))

        issue_task_create_request = cls(
            development_item_id=development_item_id,
            sharing_confirmed=sharing_confirmed,
            task=task,
        )

        return issue_task_create_request
