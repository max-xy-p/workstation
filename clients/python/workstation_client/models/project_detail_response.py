from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.note_summary_response import NoteSummaryResponse
    from ..models.project_response import ProjectResponse
    from ..models.task_response import TaskResponse


T = TypeVar("T", bound="ProjectDetailResponse")


@_attrs_define
class ProjectDetailResponse:
    """
    Attributes:
        project (ProjectResponse):
        tasks (list[TaskResponse]):
        notes (list[NoteSummaryResponse]):
    """

    project: ProjectResponse
    tasks: list[TaskResponse]
    notes: list[NoteSummaryResponse]

    def to_dict(self) -> dict[str, Any]:
        project = self.project.to_dict()

        tasks = []
        for tasks_item_data in self.tasks:
            tasks_item = tasks_item_data.to_dict()
            tasks.append(tasks_item)

        notes = []
        for notes_item_data in self.notes:
            notes_item = notes_item_data.to_dict()
            notes.append(notes_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "project": project,
                "tasks": tasks,
                "notes": notes,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.note_summary_response import NoteSummaryResponse  # noqa: PLC0415
        from ..models.project_response import ProjectResponse  # noqa: PLC0415
        from ..models.task_response import TaskResponse  # noqa: PLC0415

        d = dict(src_dict)
        project = ProjectResponse.from_dict(d.pop("project"))

        tasks = []
        _tasks = d.pop("tasks")
        for tasks_item_data in _tasks:
            tasks_item = TaskResponse.from_dict(tasks_item_data)

            tasks.append(tasks_item)

        notes = []
        _notes = d.pop("notes")
        for notes_item_data in _notes:
            notes_item = NoteSummaryResponse.from_dict(notes_item_data)

            notes.append(notes_item)

        project_detail_response = cls(
            project=project,
            tasks=tasks,
            notes=notes,
        )

        return project_detail_response
