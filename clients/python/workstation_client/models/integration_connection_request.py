from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.integration_provider import IntegrationProvider
from ..models.integration_scope import IntegrationScope

T = TypeVar("T", bound="IntegrationConnectionRequest")


@_attrs_define
class IntegrationConnectionRequest:
    """
    Attributes:
        name (str):
        provider (IntegrationProvider):
        base_url (str):
        token (None | str):
        scope (IntegrationScope):
        include_forks (bool):
        include_archived (bool):
        auto_import (bool):
        schedule_minutes (int):
        expected_version (int | None):
        sharing_confirmed (bool):
    """

    name: str
    provider: IntegrationProvider
    base_url: str
    token: None | str
    scope: IntegrationScope
    include_forks: bool
    include_archived: bool
    auto_import: bool
    schedule_minutes: int
    expected_version: int | None
    sharing_confirmed: bool

    def to_dict(self) -> dict[str, Any]:
        name = self.name

        provider = self.provider.value

        base_url = self.base_url

        token: None | str
        token = self.token

        scope = self.scope.value

        include_forks = self.include_forks

        include_archived = self.include_archived

        auto_import = self.auto_import

        schedule_minutes = self.schedule_minutes

        expected_version: int | None
        expected_version = self.expected_version

        sharing_confirmed = self.sharing_confirmed

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "name": name,
                "provider": provider,
                "baseUrl": base_url,
                "token": token,
                "scope": scope,
                "includeForks": include_forks,
                "includeArchived": include_archived,
                "autoImport": auto_import,
                "scheduleMinutes": schedule_minutes,
                "expectedVersion": expected_version,
                "sharingConfirmed": sharing_confirmed,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        name = d.pop("name")

        provider = IntegrationProvider(d.pop("provider"))

        base_url = d.pop("baseUrl")

        def _parse_token(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        token = _parse_token(d.pop("token"))

        scope = IntegrationScope(d.pop("scope"))

        include_forks = d.pop("includeForks")

        include_archived = d.pop("includeArchived")

        auto_import = d.pop("autoImport")

        schedule_minutes = d.pop("scheduleMinutes")

        def _parse_expected_version(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        expected_version = _parse_expected_version(d.pop("expectedVersion"))

        sharing_confirmed = d.pop("sharingConfirmed")

        integration_connection_request = cls(
            name=name,
            provider=provider,
            base_url=base_url,
            token=token,
            scope=scope,
            include_forks=include_forks,
            include_archived=include_archived,
            auto_import=auto_import,
            schedule_minutes=schedule_minutes,
            expected_version=expected_version,
            sharing_confirmed=sharing_confirmed,
        )

        return integration_connection_request
