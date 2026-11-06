from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

if TYPE_CHECKING:
    from ..models.note_presence_participant import NotePresenceParticipant


T = TypeVar("T", bound="NotePresenceResponse")


@_attrs_define
class NotePresenceResponse:
    """
    Attributes:
        enabled (bool):
        available (bool):
        participants (list[NotePresenceParticipant]):
    """

    enabled: bool
    available: bool
    participants: list[NotePresenceParticipant]
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        enabled = self.enabled

        available = self.available

        participants = []
        for participants_item_data in self.participants:
            participants_item = participants_item_data.to_dict()
            participants.append(participants_item)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "enabled": enabled,
                "available": available,
                "participants": participants,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.note_presence_participant import NotePresenceParticipant  # noqa: PLC0415

        d = dict(src_dict)
        enabled = d.pop("enabled")

        available = d.pop("available")

        participants = []
        _participants = d.pop("participants")
        for participants_item_data in _participants:
            participants_item = NotePresenceParticipant.from_dict(participants_item_data)

            participants.append(participants_item)

        note_presence_response = cls(
            enabled=enabled,
            available=available,
            participants=participants,
        )

        note_presence_response.additional_properties = d
        return note_presence_response

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
