from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.note_revision_source import NoteRevisionSource

T = TypeVar("T", bound="NoteRevisionResponse")


@_attrs_define
class NoteRevisionResponse:
    """
    Attributes:
        id (int):
        note_id (int):
        note_version (int):
        title (str):
        actor_subject (None | str):
        actor_display_name (None | str):
        created_at (datetime.datetime):
        source (NoteRevisionSource):
        restored_from_revision_id (int | None):
        content (str): 历史 Markdown 正文
    """

    id: int
    note_id: int
    note_version: int
    title: str
    actor_subject: None | str
    actor_display_name: None | str
    created_at: datetime.datetime
    source: NoteRevisionSource
    restored_from_revision_id: int | None
    content: str

    def to_dict(self) -> dict[str, Any]:
        id = self.id

        note_id = self.note_id

        note_version = self.note_version

        title = self.title

        actor_subject: None | str
        actor_subject = self.actor_subject

        actor_display_name: None | str
        actor_display_name = self.actor_display_name

        created_at = self.created_at.isoformat()

        source = self.source.value

        restored_from_revision_id: int | None
        restored_from_revision_id = self.restored_from_revision_id

        content = self.content

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "id": id,
                "noteId": note_id,
                "noteVersion": note_version,
                "title": title,
                "actorSubject": actor_subject,
                "actorDisplayName": actor_display_name,
                "createdAt": created_at,
                "source": source,
                "restoredFromRevisionId": restored_from_revision_id,
                "content": content,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        id = d.pop("id")

        note_id = d.pop("noteId")

        note_version = d.pop("noteVersion")

        title = d.pop("title")

        def _parse_actor_subject(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        actor_subject = _parse_actor_subject(d.pop("actorSubject"))

        def _parse_actor_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        actor_display_name = _parse_actor_display_name(d.pop("actorDisplayName"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        source = NoteRevisionSource(d.pop("source"))

        def _parse_restored_from_revision_id(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        restored_from_revision_id = _parse_restored_from_revision_id(d.pop("restoredFromRevisionId"))

        content = d.pop("content")

        note_revision_response = cls(
            id=id,
            note_id=note_id,
            note_version=note_version,
            title=title,
            actor_subject=actor_subject,
            actor_display_name=actor_display_name,
            created_at=created_at,
            source=source,
            restored_from_revision_id=restored_from_revision_id,
            content=content,
        )

        return note_revision_response
