from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="RepositoryBindRequest")


@_attrs_define
class RepositoryBindRequest:
    """
    Attributes:
        repository_id (int):
        sharing_confirmed (bool):
    """

    repository_id: int
    sharing_confirmed: bool

    def to_dict(self) -> dict[str, Any]:
        repository_id = self.repository_id

        sharing_confirmed = self.sharing_confirmed

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "repositoryId": repository_id,
                "sharingConfirmed": sharing_confirmed,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        repository_id = d.pop("repositoryId")

        sharing_confirmed = d.pop("sharingConfirmed")

        repository_bind_request = cls(
            repository_id=repository_id,
            sharing_confirmed=sharing_confirmed,
        )

        return repository_bind_request
