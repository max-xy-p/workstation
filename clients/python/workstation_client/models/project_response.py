from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.project_status import ProjectStatus

T = TypeVar("T", bound="ProjectResponse")


@_attrs_define
class ProjectResponse:
    """
    Attributes:
        id (int):
        name (str):
        description (None | str):
        status (ProjectStatus):
        repository_url (None | str):
        tech_stack (None | str):
        run_guide (None | str):
        created_at (datetime.datetime):
        updated_at (datetime.datetime):
    """

    id: int
    name: str
    description: None | str
    status: ProjectStatus
    repository_url: None | str
    tech_stack: None | str
    run_guide: None | str
    created_at: datetime.datetime
    updated_at: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        name = self.name

        description: None | str
        description = self.description

        status = self.status.value

        repository_url: None | str
        repository_url = self.repository_url

        tech_stack: None | str
        tech_stack = self.tech_stack

        run_guide: None | str
        run_guide = self.run_guide

        created_at = self.created_at.isoformat()

        updated_at = self.updated_at.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "name": name,
                "description": description,
                "status": status,
                "repositoryUrl": repository_url,
                "techStack": tech_stack,
                "runGuide": run_guide,
                "createdAt": created_at,
                "updatedAt": updated_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        name = d.pop("name")

        def _parse_description(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        description = _parse_description(d.pop("description"))

        status = ProjectStatus(d.pop("status"))

        def _parse_repository_url(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        repository_url = _parse_repository_url(d.pop("repositoryUrl"))

        def _parse_tech_stack(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        tech_stack = _parse_tech_stack(d.pop("techStack"))

        def _parse_run_guide(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        run_guide = _parse_run_guide(d.pop("runGuide"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        updated_at = datetime.datetime.fromisoformat(d.pop("updatedAt"))

        project_response = cls(
            id=id,
            name=name,
            description=description,
            status=status,
            repository_url=repository_url,
            tech_stack=tech_stack,
            run_guide=run_guide,
            created_at=created_at,
            updated_at=updated_at,
        )

        return project_response
