from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

T = TypeVar("T", bound="GithubWebhookResponse")


@_attrs_define
class GithubWebhookResponse:
    """
    Attributes:
        configured (bool):
        enabled (bool):
        version (int):
        endpoint_path (None | str):
    """

    configured: bool
    enabled: bool
    version: int
    endpoint_path: None | str

    def to_dict(self) -> dict[str, Any]:
        configured = self.configured

        enabled = self.enabled

        version = self.version

        endpoint_path: None | str
        endpoint_path = self.endpoint_path

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "configured": configured,
                "enabled": enabled,
                "version": version,
                "endpointPath": endpoint_path,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        configured = d.pop("configured")

        enabled = d.pop("enabled")

        version = d.pop("version")

        def _parse_endpoint_path(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        endpoint_path = _parse_endpoint_path(d.pop("endpointPath"))

        github_webhook_response = cls(
            configured=configured,
            enabled=enabled,
            version=version,
            endpoint_path=endpoint_path,
        )

        return github_webhook_response
