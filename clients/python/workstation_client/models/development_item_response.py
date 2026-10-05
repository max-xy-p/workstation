from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.development_kind import DevelopmentKind

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
        )

        return development_item_response
