from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.task_status import TaskStatus

T = TypeVar("T", bound="TaskStatusRequest")


@_attrs_define
class TaskStatusRequest:
    """
    Attributes:
        status (TaskStatus):
    """

    status: TaskStatus

    def to_dict(self) -> dict[str, Any]:
        status = self.status.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "status": status,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        status = TaskStatus(d.pop("status"))

        task_status_request = cls(
            status=status,
        )

        return task_status_request
