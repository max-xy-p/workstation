from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="IntegrationTriggerRequest")


@_attrs_define
class IntegrationTriggerRequest:
    """
    Attributes:
        request_key (str):
    """

    request_key: str

    def to_dict(self) -> dict[str, Any]:
        request_key = self.request_key

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "requestKey": request_key,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        request_key = d.pop("requestKey")

        integration_trigger_request = cls(
            request_key=request_key,
        )

        return integration_trigger_request
