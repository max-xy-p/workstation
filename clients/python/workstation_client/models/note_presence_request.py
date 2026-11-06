from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.note_presence_state import NotePresenceState

T = TypeVar("T", bound="NotePresenceRequest")


@_attrs_define
class NotePresenceRequest:
    """
    Attributes:
        state (NotePresenceState):
        sequence (int):
    """

    state: NotePresenceState
    sequence: int

    def to_dict(self) -> dict[str, Any]:
        state = self.state.value

        sequence = self.sequence

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "state": state,
                "sequence": sequence,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        state = NotePresenceState(d.pop("state"))

        sequence = d.pop("sequence")

        note_presence_request = cls(
            state=state,
            sequence=sequence,
        )

        return note_presence_request
