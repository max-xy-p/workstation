from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.development_kind import DevelopmentKind
from ..types import UNSET, Unset

T = TypeVar("T", bound="DevelopmentItemResponse")


@_attrs_define
class DevelopmentItemResponse:
    """
    Attributes:
        id (int):
        repository_id (int):
        kind (DevelopmentKind):
        external_id (str):
        title (str):
        url (None | str):
        status (str):
        raw_status (None | str):
        author (None | str):
        branch (None | str):
        source_updated_at (datetime.datetime | None):
        issue_number (int | None | Unset):
        state_reason (None | str | Unset):
        linked_task_id (int | None | Unset):
    """

    id: int
    repository_id: int
    kind: DevelopmentKind
    external_id: str
    title: str
    url: None | str
    status: str
    raw_status: None | str
    author: None | str
    branch: None | str
    source_updated_at: datetime.datetime | None
    issue_number: int | None | Unset = UNSET
    state_reason: None | str | Unset = UNSET
    linked_task_id: int | None | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        repository_id = self.repository_id

        kind = self.kind.value

        external_id = self.external_id

        title = self.title

        url: None | str
        url = self.url

        status = self.status

        raw_status: None | str
        raw_status = self.raw_status

        author: None | str
        author = self.author

        branch: None | str
        branch = self.branch

        source_updated_at: None | str
        if isinstance(self.source_updated_at, datetime.datetime):
            source_updated_at = self.source_updated_at.isoformat()
        else:
            source_updated_at = self.source_updated_at

        issue_number: int | None | Unset
        if isinstance(self.issue_number, Unset):
            issue_number = UNSET
        else:
            issue_number = self.issue_number

        state_reason: None | str | Unset
        if isinstance(self.state_reason, Unset):
            state_reason = UNSET
        else:
            state_reason = self.state_reason

        linked_task_id: int | None | Unset
        if isinstance(self.linked_task_id, Unset):
            linked_task_id = UNSET
        else:
            linked_task_id = self.linked_task_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "repositoryId": repository_id,
                "kind": kind,
                "externalId": external_id,
                "title": title,
                "url": url,
                "status": status,
                "rawStatus": raw_status,
                "author": author,
                "branch": branch,
                "sourceUpdatedAt": source_updated_at,
            }
        )
        if issue_number is not UNSET:
            field_dict["issueNumber"] = issue_number
        if state_reason is not UNSET:
            field_dict["stateReason"] = state_reason
        if linked_task_id is not UNSET:
            field_dict["linkedTaskId"] = linked_task_id

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        repository_id = d.pop("repositoryId")

        kind = DevelopmentKind(d.pop("kind"))

        external_id = d.pop("externalId")

        title = d.pop("title")

        def _parse_url(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        url = _parse_url(d.pop("url"))

        status = d.pop("status")

        def _parse_raw_status(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        raw_status = _parse_raw_status(d.pop("rawStatus"))

        def _parse_author(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        author = _parse_author(d.pop("author"))

        def _parse_branch(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        branch = _parse_branch(d.pop("branch"))

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

        def _parse_issue_number(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        issue_number = _parse_issue_number(d.pop("issueNumber", UNSET))

        def _parse_state_reason(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        state_reason = _parse_state_reason(d.pop("stateReason", UNSET))

        def _parse_linked_task_id(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        linked_task_id = _parse_linked_task_id(d.pop("linkedTaskId", UNSET))

        development_item_response = cls(
            id=id,
            repository_id=repository_id,
            kind=kind,
            external_id=external_id,
            title=title,
            url=url,
            status=status,
            raw_status=raw_status,
            author=author,
            branch=branch,
            source_updated_at=source_updated_at,
            issue_number=issue_number,
            state_reason=state_reason,
            linked_task_id=linked_task_id,
        )

        return development_item_response
