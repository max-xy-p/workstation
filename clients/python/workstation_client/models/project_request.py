from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.project_status import ProjectStatus
from ..types import UNSET, Unset

T = TypeVar("T", bound="ProjectRequest")


@_attrs_define
class ProjectRequest:
    """
    Attributes:
        name (str):
        status (ProjectStatus):
        homepage_url (None | str):
        description (None | str | Unset):
        repository_url (None | str | Unset):
        tech_stack (None | str | Unset):
        run_guide (None | str | Unset):
    """

    name: str
    status: ProjectStatus
    homepage_url: None | str
    description: None | str | Unset = UNSET
    repository_url: None | str | Unset = UNSET
    tech_stack: None | str | Unset = UNSET
    run_guide: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        name = self.name

        status = self.status.value

        homepage_url: None | str
        homepage_url = self.homepage_url

        description: None | str | Unset
        if isinstance(self.description, Unset):
            description = UNSET
        else:
            description = self.description

        repository_url: None | str | Unset
        if isinstance(self.repository_url, Unset):
            repository_url = UNSET
        else:
            repository_url = self.repository_url

        tech_stack: None | str | Unset
        if isinstance(self.tech_stack, Unset):
            tech_stack = UNSET
        else:
            tech_stack = self.tech_stack

        run_guide: None | str | Unset
        if isinstance(self.run_guide, Unset):
            run_guide = UNSET
        else:
            run_guide = self.run_guide

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "name": name,
                "status": status,
                "homepageUrl": homepage_url,
            }
        )
        if description is not UNSET:
            field_dict["description"] = description
        if repository_url is not UNSET:
            field_dict["repositoryUrl"] = repository_url
        if tech_stack is not UNSET:
            field_dict["techStack"] = tech_stack
        if run_guide is not UNSET:
            field_dict["runGuide"] = run_guide

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        name = d.pop("name")

        status = ProjectStatus(d.pop("status"))

        def _parse_homepage_url(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        homepage_url = _parse_homepage_url(d.pop("homepageUrl"))

        def _parse_description(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        description = _parse_description(d.pop("description", UNSET))

        def _parse_repository_url(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        repository_url = _parse_repository_url(d.pop("repositoryUrl", UNSET))

        def _parse_tech_stack(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        tech_stack = _parse_tech_stack(d.pop("techStack", UNSET))

        def _parse_run_guide(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        run_guide = _parse_run_guide(d.pop("runGuide", UNSET))

        project_request = cls(
            name=name,
            status=status,
            homepage_url=homepage_url,
            description=description,
            repository_url=repository_url,
            tech_stack=tech_stack,
            run_guide=run_guide,
        )

        return project_request
