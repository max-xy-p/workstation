from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.integration_provider import IntegrationProvider
from ..models.integration_scope import IntegrationScope

T = TypeVar("T", bound="IntegrationConnectionResponse")


@_attrs_define
class IntegrationConnectionResponse:
    """
    Attributes:
        id (int):
        name (str):
        provider (IntegrationProvider):
        base_url (str):
        account (str):
        scope (IntegrationScope):
        include_forks (bool):
        include_archived (bool):
        auto_import (bool):
        schedule_minutes (int):
        enabled (bool):
        version (int):
        last_error (None | str):
        last_synced_at (datetime.datetime | None):
    """

    id: int
    name: str
    provider: IntegrationProvider
    base_url: str
    account: str
    scope: IntegrationScope
    include_forks: bool
    include_archived: bool
    auto_import: bool
    schedule_minutes: int
    enabled: bool
    version: int
    last_error: None | str
    last_synced_at: datetime.datetime | None

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        name = self.name

        provider = self.provider.value

        base_url = self.base_url

        account = self.account

        scope = self.scope.value

        include_forks = self.include_forks

        include_archived = self.include_archived

        auto_import = self.auto_import

        schedule_minutes = self.schedule_minutes

        enabled = self.enabled

        version = self.version

        last_error: None | str
        last_error = self.last_error

        last_synced_at: None | str
        if isinstance(self.last_synced_at, datetime.datetime):
            last_synced_at = self.last_synced_at.isoformat()
        else:
            last_synced_at = self.last_synced_at

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "name": name,
                "provider": provider,
                "baseUrl": base_url,
                "account": account,
                "scope": scope,
                "includeForks": include_forks,
                "includeArchived": include_archived,
                "autoImport": auto_import,
                "scheduleMinutes": schedule_minutes,
                "enabled": enabled,
                "version": version,
                "lastError": last_error,
                "lastSyncedAt": last_synced_at,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        name = d.pop("name")

        provider = IntegrationProvider(d.pop("provider"))

        base_url = d.pop("baseUrl")

        account = d.pop("account")

        scope = IntegrationScope(d.pop("scope"))

        include_forks = d.pop("includeForks")

        include_archived = d.pop("includeArchived")

        auto_import = d.pop("autoImport")

        schedule_minutes = d.pop("scheduleMinutes")

        enabled = d.pop("enabled")

        version = d.pop("version")

        def _parse_last_error(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        last_error = _parse_last_error(d.pop("lastError"))

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

        integration_connection_response = cls(
            id=id,
            name=name,
            provider=provider,
            base_url=base_url,
            account=account,
            scope=scope,
            include_forks=include_forks,
            include_archived=include_archived,
            auto_import=auto_import,
            schedule_minutes=schedule_minutes,
            enabled=enabled,
            version=version,
            last_error=last_error,
            last_synced_at=last_synced_at,
        )

        return integration_connection_response
