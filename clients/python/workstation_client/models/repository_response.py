from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.integration_provider import IntegrationProvider

T = TypeVar("T", bound="RepositoryResponse")


@_attrs_define
class RepositoryResponse:
    """
    Attributes:
        id (int):
        provider (IntegrationProvider):
        namespace (str):
        name (str):
        url (str):
        description (None | str):
        default_branch (None | str):
        language (None | str):
        visibility (str):
        archived (bool):
        project_id (int | None):
        connection_id (int | None):
        last_pushed_at (datetime.datetime | None):
        last_synced_at (datetime.datetime | None):
        last_error (None | str):
        collected_open_issues (int | None):
        collected_open_change_requests (int | None):
        latest_pipeline_status (None | str):
    """

    id: int
    provider: IntegrationProvider
    namespace: str
    name: str
    url: str
    description: None | str
    default_branch: None | str
    language: None | str
    visibility: str
    archived: bool
    project_id: int | None
    connection_id: int | None
    last_pushed_at: datetime.datetime | None
    last_synced_at: datetime.datetime | None
    last_error: None | str
    collected_open_issues: int | None
    collected_open_change_requests: int | None
    latest_pipeline_status: None | str

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        provider = self.provider.value

        namespace = self.namespace

        name = self.name

        url = self.url

        description: None | str
        description = self.description

        default_branch: None | str
        default_branch = self.default_branch

        language: None | str
        language = self.language

        visibility = self.visibility

        archived = self.archived

        project_id: int | None
        project_id = self.project_id

        connection_id: int | None
        connection_id = self.connection_id

        last_pushed_at: None | str
        if isinstance(self.last_pushed_at, datetime.datetime):
            last_pushed_at = self.last_pushed_at.isoformat()
        else:
            last_pushed_at = self.last_pushed_at

        last_synced_at: None | str
        if isinstance(self.last_synced_at, datetime.datetime):
            last_synced_at = self.last_synced_at.isoformat()
        else:
            last_synced_at = self.last_synced_at

        last_error: None | str
        last_error = self.last_error

        collected_open_issues: int | None
        collected_open_issues = self.collected_open_issues

        collected_open_change_requests: int | None
        collected_open_change_requests = self.collected_open_change_requests

        latest_pipeline_status: None | str
        latest_pipeline_status = self.latest_pipeline_status

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "provider": provider,
                "namespace": namespace,
                "name": name,
                "url": url,
                "description": description,
                "defaultBranch": default_branch,
                "language": language,
                "visibility": visibility,
                "archived": archived,
                "projectId": project_id,
                "connectionId": connection_id,
                "lastPushedAt": last_pushed_at,
                "lastSyncedAt": last_synced_at,
                "lastError": last_error,
                "collectedOpenIssues": collected_open_issues,
                "collectedOpenChangeRequests": collected_open_change_requests,
                "latestPipelineStatus": latest_pipeline_status,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        provider = IntegrationProvider(d.pop("provider"))

        namespace = d.pop("namespace")

        name = d.pop("name")

        url = d.pop("url")

        def _parse_description(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        description = _parse_description(d.pop("description"))

        def _parse_default_branch(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        default_branch = _parse_default_branch(d.pop("defaultBranch"))

        def _parse_language(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        language = _parse_language(d.pop("language"))

        visibility = d.pop("visibility")

        archived = d.pop("archived")

        def _parse_project_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        project_id = _parse_project_id(d.pop("projectId"))

        def _parse_connection_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        connection_id = _parse_connection_id(d.pop("connectionId"))

        def _parse_last_pushed_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                last_pushed_at_type_0 = datetime.datetime.fromisoformat(data)

                return last_pushed_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        last_pushed_at = _parse_last_pushed_at(d.pop("lastPushedAt"))

        def _parse_last_synced_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                last_synced_at_type_0 = datetime.datetime.fromisoformat(data)

                return last_synced_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        last_synced_at = _parse_last_synced_at(d.pop("lastSyncedAt"))

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

        def _parse_collected_open_issues(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        collected_open_issues = _parse_collected_open_issues(d.pop("collectedOpenIssues"))

        def _parse_collected_open_change_requests(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        collected_open_change_requests = _parse_collected_open_change_requests(d.pop("collectedOpenChangeRequests"))

        def _parse_latest_pipeline_status(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        latest_pipeline_status = _parse_latest_pipeline_status(d.pop("latestPipelineStatus"))

        repository_response = cls(
            id=id,
            provider=provider,
            namespace=namespace,
            name=name,
            url=url,
            description=description,
            default_branch=default_branch,
            language=language,
            visibility=visibility,
            archived=archived,
            project_id=project_id,
            connection_id=connection_id,
            last_pushed_at=last_pushed_at,
            last_synced_at=last_synced_at,
            last_error=last_error,
            collected_open_issues=collected_open_issues,
            collected_open_change_requests=collected_open_change_requests,
            latest_pipeline_status=latest_pipeline_status,
        )

        return repository_response
