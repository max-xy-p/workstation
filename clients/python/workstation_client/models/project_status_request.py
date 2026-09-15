from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.project_status import ProjectStatus

T = TypeVar("T", bound="ProjectStatusRequest")


@_attrs_define
class ProjectStatusRequest:
    """
    Attributes:
        status (ProjectStatus):
    """

    status: ProjectStatus

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
        status = ProjectStatus(d.pop("status"))

        project_status_request = cls(
            status=status,
        )

        return project_status_request
