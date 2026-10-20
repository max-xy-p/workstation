from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.task_response import TaskResponse


T = TypeVar("T", bound="IssueTaskResult")


@_attrs_define
class IssueTaskResult:
    """
    Attributes:
        task (TaskResponse):
        created (bool):
    """

    task: TaskResponse
    created: bool

    def to_dict(self) -> dict[str, Any]:
        task = self.task.to_dict()

        created = self.created

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "task": task,
                "created": created,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.task_response import TaskResponse  # noqa: PLC0415

        d = dict(src_dict)
        task = TaskResponse.from_dict(d.pop("task"))

        created = d.pop("created")

        issue_task_result = cls(
            task=task,
            created=created,
        )

        return issue_task_result
