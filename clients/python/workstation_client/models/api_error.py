from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="ApiError")


@_attrs_define
class ApiError:
    """
    Attributes:
        code (str):
        message (str):
        timestamp (datetime.datetime):
    """

    code: str
    message: str
    timestamp: datetime.datetime

    def to_dict(self) -> dict[str, Any]:
        code = self.code

        message = self.message

        timestamp = self.timestamp.isoformat()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "code": code,
                "message": message,
                "timestamp": timestamp,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        code = d.pop("code")

        message = d.pop("message")

        timestamp = datetime.datetime.fromisoformat(d.pop("timestamp"))

        api_error = cls(
            code=code,
            message=message,
            timestamp=timestamp,
        )

        return api_error
