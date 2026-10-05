from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="IntegrationEnableRequest")


@_attrs_define
class IntegrationEnableRequest:
    """
    Attributes:
        enabled (bool):
        expected_version (int):
        sharing_confirmed (bool):
    """

    enabled: bool
    expected_version: int
    sharing_confirmed: bool

    def to_dict(self) -> dict[str, Any]:
        enabled = self.enabled

        expected_version = self.expected_version

        sharing_confirmed = self.sharing_confirmed

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "enabled": enabled,
                "expectedVersion": expected_version,
                "sharingConfirmed": sharing_confirmed,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        enabled = d.pop("enabled")

        expected_version = d.pop("expectedVersion")

        sharing_confirmed = d.pop("sharingConfirmed")

        integration_enable_request = cls(
            enabled=enabled,
            expected_version=expected_version,
            sharing_confirmed=sharing_confirmed,
        )

        return integration_enable_request
