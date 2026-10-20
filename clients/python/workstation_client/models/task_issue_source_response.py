from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.task_issue_source_response_tracking_status import TaskIssueSourceResponseTrackingStatus

T = TypeVar("T", bound="TaskIssueSourceResponse")


@_attrs_define
class TaskIssueSourceResponse:
    """
    Attributes:
        id (int):
        task_id (int):
        project_id (int):
        repository_id (int):
        repository_name (str):
        external_id (str):
        issue_number (int):
        title (str):
        url (None | str):
        status (str):
        state_reason (None | str):
        source_updated_at (datetime.datetime | None):
        last_attempt_at (datetime.datetime | None):
        last_success_at (datetime.datetime | None):
        last_error (None | str):
        tracking_status (TaskIssueSourceResponseTrackingStatus):
    """

    id: int
    task_id: int
    project_id: int
    repository_id: int
    repository_name: str
    external_id: str
    issue_number: int
    title: str
    url: None | str
    status: str
    state_reason: None | str
    source_updated_at: datetime.datetime | None
    last_attempt_at: datetime.datetime | None
    last_success_at: datetime.datetime | None
    last_error: None | str
    tracking_status: TaskIssueSourceResponseTrackingStatus

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        task_id = self.task_id

        project_id = self.project_id

        repository_id = self.repository_id

        repository_name = self.repository_name

        external_id = self.external_id

        issue_number = self.issue_number

        title = self.title

        url: None | str
        url = self.url

        status = self.status

        state_reason: None | str
        state_reason = self.state_reason

        source_updated_at: None | str
        if isinstance(self.source_updated_at, datetime.datetime):
            source_updated_at = self.source_updated_at.isoformat()
        else:
            source_updated_at = self.source_updated_at

        last_attempt_at: None | str
        if isinstance(self.last_attempt_at, datetime.datetime):
            last_attempt_at = self.last_attempt_at.isoformat()
        else:
            last_attempt_at = self.last_attempt_at

        last_success_at: None | str
        if isinstance(self.last_success_at, datetime.datetime):
            last_success_at = self.last_success_at.isoformat()
        else:
            last_success_at = self.last_success_at

        last_error: None | str
        last_error = self.last_error

        tracking_status = self.tracking_status.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "taskId": task_id,
                "projectId": project_id,
                "repositoryId": repository_id,
                "repositoryName": repository_name,
                "externalId": external_id,
                "issueNumber": issue_number,
                "title": title,
                "url": url,
                "status": status,
                "stateReason": state_reason,
                "sourceUpdatedAt": source_updated_at,
                "lastAttemptAt": last_attempt_at,
                "lastSuccessAt": last_success_at,
                "lastError": last_error,
                "trackingStatus": tracking_status,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        task_id = d.pop("taskId")

        project_id = d.pop("projectId")

        repository_id = d.pop("repositoryId")

        repository_name = d.pop("repositoryName")

        external_id = d.pop("externalId")

        issue_number = d.pop("issueNumber")

        title = d.pop("title")

        def _parse_url(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        url = _parse_url(d.pop("url"))

        status = d.pop("status")

        def _parse_state_reason(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        state_reason = _parse_state_reason(d.pop("stateReason"))

        def _parse_source_updated_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                source_updated_at_type_0 = datetime.datetime.fromisoformat(data)

                return source_updated_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        source_updated_at = _parse_source_updated_at(d.pop("sourceUpdatedAt"))

        def _parse_last_attempt_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                last_attempt_at_type_0 = datetime.datetime.fromisoformat(data)

                return last_attempt_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        last_attempt_at = _parse_last_attempt_at(d.pop("lastAttemptAt"))

        def _parse_last_success_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                last_success_at_type_0 = datetime.datetime.fromisoformat(data)

                return last_success_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        last_success_at = _parse_last_success_at(d.pop("lastSuccessAt"))

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

        tracking_status = TaskIssueSourceResponseTrackingStatus(d.pop("trackingStatus"))

        task_issue_source_response = cls(
            id=id,
            task_id=task_id,
            project_id=project_id,
            repository_id=repository_id,
            repository_name=repository_name,
            external_id=external_id,
            issue_number=issue_number,
            title=title,
            url=url,
            status=status,
            state_reason=state_reason,
            source_updated_at=source_updated_at,
            last_attempt_at=last_attempt_at,
            last_success_at=last_success_at,
            last_error=last_error,
            tracking_status=tracking_status,
        )

        return task_issue_source_response
