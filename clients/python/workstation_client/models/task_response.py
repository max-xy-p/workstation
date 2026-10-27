from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.task_priority import TaskPriority
from ..models.task_status import TaskStatus

T = TypeVar("T", bound="TaskResponse")


@_attrs_define
class TaskResponse:
    """
    Attributes:
        id (int):
        title (str):
        description (None | str):
        project_id (int):
        project_name (str):
        status (TaskStatus):
        priority (TaskPriority):
        due_date (datetime.date | None):
        evidence_note_id (int | None):
        evidence_note_title (None | str):
        created_at (datetime.datetime):
        updated_at (datetime.datetime):
        version (int):
    """

    id: int
    title: str
    description: None | str
    project_id: int
    project_name: str
    status: TaskStatus
    priority: TaskPriority
    due_date: datetime.date | None
    evidence_note_id: int | None
    evidence_note_title: None | str
    created_at: datetime.datetime
    updated_at: datetime.datetime
    version: int

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        title = self.title

        description: None | str
        description = self.description

        project_id = self.project_id

        project_name = self.project_name

        status = self.status.value

        priority = self.priority.value

        due_date: None | str
        if isinstance(self.due_date, datetime.date):
            due_date = self.due_date.isoformat()
        else:
            due_date = self.due_date

        evidence_note_id: int | None
        evidence_note_id = self.evidence_note_id

        evidence_note_title: None | str
        evidence_note_title = self.evidence_note_title

        created_at = self.created_at.isoformat()

        updated_at = self.updated_at.isoformat()

        version = self.version

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "title": title,
                "description": description,
                "projectId": project_id,
                "projectName": project_name,
                "status": status,
                "priority": priority,
                "dueDate": due_date,
                "evidenceNoteId": evidence_note_id,
                "evidenceNoteTitle": evidence_note_title,
                "createdAt": created_at,
                "updatedAt": updated_at,
                "version": version,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        title = d.pop("title")

        def _parse_description(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        description = _parse_description(d.pop("description"))

        project_id = d.pop("projectId")

        project_name = d.pop("projectName")

        status = TaskStatus(d.pop("status"))

        priority = TaskPriority(d.pop("priority"))

        def _parse_due_date(data: object) -> datetime.date | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                due_date_type_0 = datetime.date.fromisoformat(data)

                return due_date_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.date | None, data)

        due_date = _parse_due_date(d.pop("dueDate"))

        def _parse_evidence_note_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        evidence_note_id = _parse_evidence_note_id(d.pop("evidenceNoteId"))

        def _parse_evidence_note_title(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        evidence_note_title = _parse_evidence_note_title(d.pop("evidenceNoteTitle"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        updated_at = datetime.datetime.fromisoformat(d.pop("updatedAt"))

        version = d.pop("version")

        task_response = cls(
            id=id,
            title=title,
            description=description,
            project_id=project_id,
            project_name=project_name,
            status=status,
            priority=priority,
            due_date=due_date,
            evidence_note_id=evidence_note_id,
            evidence_note_title=evidence_note_title,
            created_at=created_at,
            updated_at=updated_at,
            version=version,
        )

        return task_response
