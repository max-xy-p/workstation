from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.project_status import ProjectStatus

T = TypeVar("T", bound="IntegrationRunItemResponse")


@_attrs_define
class IntegrationRunItemResponse:
    """
    Attributes:
        id (int):
        repository_id (int):
        project_id (int | None):
        status (str):
        last_error (None | str):
        project_status (ProjectStatus):
    """

    id: int
    repository_id: int
    project_id: int | None
    status: str
    last_error: None | str
    project_status: ProjectStatus

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        repository_id = self.repository_id

        project_id: int | None
        project_id = self.project_id

        status = self.status

        last_error: None | str
        last_error = self.last_error

        project_status = self.project_status.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "repositoryId": repository_id,
                "projectId": project_id,
                "status": status,
                "lastError": last_error,
                "projectStatus": project_status,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        repository_id = d.pop("repositoryId")

        def _parse_project_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        project_id = _parse_project_id(d.pop("projectId"))

        status = d.pop("status")

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

        project_status = ProjectStatus(d.pop("projectStatus"))

        integration_run_item_response = cls(
            id=id,
            repository_id=repository_id,
            project_id=project_id,
            status=status,
            last_error=last_error,
            project_status=project_status,
        )

        return integration_run_item_response
