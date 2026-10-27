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
        expected_version (int):
    """

    status: TaskStatus
    expected_version: int

    def to_dict(self) -> dict[str, Any]:
        status = self.status.value

        expected_version = self.expected_version

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "status": status,
                "expectedVersion": expected_version,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        status = TaskStatus(d.pop("status"))

        expected_version = d.pop("expectedVersion")

        task_status_request = cls(
            status=status,
            expected_version=expected_version,
        )

        return task_status_request
