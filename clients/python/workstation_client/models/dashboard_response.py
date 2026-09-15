from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.note_summary_response import NoteSummaryResponse
    from ..models.project_summary_response import ProjectSummaryResponse


T = TypeVar("T", bound="DashboardResponse")


@_attrs_define
class DashboardResponse:
    """
    Attributes:
        active_project_count (int):
        todo_task_count (int):
        in_progress_task_count (int):
        recent_projects (list[ProjectSummaryResponse]):
        recent_notes (list[NoteSummaryResponse]):
    """

    active_project_count: int
    todo_task_count: int
    in_progress_task_count: int
    recent_projects: list[ProjectSummaryResponse]
    recent_notes: list[NoteSummaryResponse]

    def to_dict(self) -> dict[str, Any]:
        active_project_count = self.active_project_count

        todo_task_count = self.todo_task_count

        in_progress_task_count = self.in_progress_task_count

        recent_projects = []
        for recent_projects_item_data in self.recent_projects:
            recent_projects_item = recent_projects_item_data.to_dict()
            recent_projects.append(recent_projects_item)

        recent_notes = []
        for recent_notes_item_data in self.recent_notes:
            recent_notes_item = recent_notes_item_data.to_dict()
            recent_notes.append(recent_notes_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "activeProjectCount": active_project_count,
                "todoTaskCount": todo_task_count,
                "inProgressTaskCount": in_progress_task_count,
                "recentProjects": recent_projects,
                "recentNotes": recent_notes,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.note_summary_response import NoteSummaryResponse  # noqa: PLC0415
        from ..models.project_summary_response import ProjectSummaryResponse  # noqa: PLC0415

        d = dict(src_dict)
        active_project_count = d.pop("activeProjectCount")

        todo_task_count = d.pop("todoTaskCount")

        in_progress_task_count = d.pop("inProgressTaskCount")

        recent_projects = []
        _recent_projects = d.pop("recentProjects")
        for recent_projects_item_data in _recent_projects:
            recent_projects_item = ProjectSummaryResponse.from_dict(recent_projects_item_data)

            recent_projects.append(recent_projects_item)

        recent_notes = []
        _recent_notes = d.pop("recentNotes")
        for recent_notes_item_data in _recent_notes:
            recent_notes_item = NoteSummaryResponse.from_dict(recent_notes_item_data)

            recent_notes.append(recent_notes_item)

        dashboard_response = cls(
            active_project_count=active_project_count,
            todo_task_count=todo_task_count,
            in_progress_task_count=in_progress_task_count,
            recent_projects=recent_projects,
            recent_notes=recent_notes,
        )

        return dashboard_response
