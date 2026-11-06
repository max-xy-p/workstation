from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.note_presence_state import NotePresenceState

T = TypeVar("T", bound="NotePresenceParticipant")


@_attrs_define
class NotePresenceParticipant:
    """
    Attributes:
        subject_id (str):
        username (None | str):
        display_name (None | str):
        state (NotePresenceState):
    """

    subject_id: str
    username: None | str
    display_name: None | str
    state: NotePresenceState
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        subject_id = self.subject_id

        username: None | str
        username = self.username

        display_name: None | str
        display_name = self.display_name

        state = self.state.value

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "subjectId": subject_id,
                "username": username,
                "displayName": display_name,
                "state": state,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        subject_id = d.pop("subjectId")

        def _parse_username(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        username = _parse_username(d.pop("username"))

        def _parse_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        display_name = _parse_display_name(d.pop("displayName"))

        state = NotePresenceState(d.pop("state"))

        note_presence_participant = cls(
            subject_id=subject_id,
            username=username,
            display_name=display_name,
            state=state,
        )

        note_presence_participant.additional_properties = d
        return note_presence_participant

    @property
    def additional_keys(self) -> list[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
