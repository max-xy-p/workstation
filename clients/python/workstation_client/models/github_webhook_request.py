from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..types import UNSET, Unset

T = TypeVar("T", bound="GithubWebhookRequest")


@_attrs_define
class GithubWebhookRequest:
    """
    Attributes:
        enabled (bool):
        expected_version (int):
        secret (None | str | Unset):
    """

    enabled: bool
    expected_version: int
    secret: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        enabled = self.enabled

        expected_version = self.expected_version

        secret: None | str | Unset
        if isinstance(self.secret, Unset):
            secret = UNSET
        else:
            secret = self.secret

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "enabled": enabled,
                "expectedVersion": expected_version,
            }
        )
        if secret is not UNSET:
            field_dict["secret"] = secret

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        enabled = d.pop("enabled")

        expected_version = d.pop("expectedVersion")

        def _parse_secret(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        secret = _parse_secret(d.pop("secret", UNSET))

        github_webhook_request = cls(
            enabled=enabled,
            expected_version=expected_version,
            secret=secret,
        )

        return github_webhook_request
