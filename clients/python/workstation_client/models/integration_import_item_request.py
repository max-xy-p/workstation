from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.project_status import ProjectStatus

T = TypeVar("T", bound="IntegrationImportItemRequest")


@_attrs_define
class IntegrationImportItemRequest:
    """
    Attributes:
        repository_id (int):
        project_id (int | None):
        status (ProjectStatus):
    """

    repository_id: int
    project_id: int | None
    status: ProjectStatus

    def to_dict(self) -> dict[str, Any]:
        repository_id = self.repository_id

        project_id: int | None
        project_id = self.project_id

        status = self.status.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "repositoryId": repository_id,
                "projectId": project_id,
                "status": status,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        repository_id = d.pop("repositoryId")

        def _parse_project_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        project_id = _parse_project_id(d.pop("projectId"))

        status = ProjectStatus(d.pop("status"))

        integration_import_item_request = cls(
            repository_id=repository_id,
            project_id=project_id,
            status=status,
        )

        return integration_import_item_request
