from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.task_priority import TaskPriority
from ..models.task_status import TaskStatus
from ..types import UNSET, Unset

T = TypeVar("T", bound="TaskRequest")


@_attrs_define
class TaskRequest:
    """
    Attributes:
        title (str):
        project_id (int):
        status (TaskStatus):
        priority (TaskPriority):
        description (None | str | Unset):
        due_date (datetime.date | None | Unset):
        evidence_note_id (int | None | Unset):
    """

    title: str
    project_id: int
    status: TaskStatus
    priority: TaskPriority
    description: None | str | Unset = UNSET
    due_date: datetime.date | None | Unset = UNSET
    evidence_note_id: int | None | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        title = self.title

        project_id = self.project_id

        status = self.status.value

        priority = self.priority.value

        description: None | str | Unset
        if isinstance(self.description, Unset):
            description = UNSET
        else:
            description = self.description

        due_date: None | str | Unset
        if isinstance(self.due_date, Unset):
            due_date = UNSET
        elif isinstance(self.due_date, datetime.date):
            due_date = self.due_date.isoformat()
        else:
            due_date = self.due_date

        evidence_note_id: int | None | Unset
        if isinstance(self.evidence_note_id, Unset):
            evidence_note_id = UNSET
        else:
            evidence_note_id = self.evidence_note_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "title": title,
                "projectId": project_id,
                "status": status,
                "priority": priority,
            }
        )
        if description is not UNSET:
            field_dict["description"] = description
        if due_date is not UNSET:
            field_dict["dueDate"] = due_date
        if evidence_note_id is not UNSET:
            field_dict["evidenceNoteId"] = evidence_note_id

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        title = d.pop("title")

        project_id = d.pop("projectId")

        status = TaskStatus(d.pop("status"))

        priority = TaskPriority(d.pop("priority"))

        def _parse_description(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        description = _parse_description(d.pop("description", UNSET))

        def _parse_due_date(data: object) -> datetime.date | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                due_date_type_0 = datetime.date.fromisoformat(data)

                return due_date_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.date | None | Unset, data)

        due_date = _parse_due_date(d.pop("dueDate", UNSET))

        def _parse_evidence_note_id(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        evidence_note_id = _parse_evidence_note_id(d.pop("evidenceNoteId", UNSET))

        task_request = cls(
            title=title,
            project_id=project_id,
            status=status,
            priority=priority,
            description=description,
            due_date=due_date,
            evidence_note_id=evidence_note_id,
        )

        return task_request
