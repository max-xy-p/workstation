from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.issue_source_confirm_request_action import IssueSourceConfirmRequestAction

T = TypeVar("T", bound="IssueSourceConfirmRequest")


@_attrs_define
class IssueSourceConfirmRequest:
    """
    Attributes:
        expected_link_token (str):
        expected_source_version (int):
        expected_confirmation_version (int):
        expected_task_version (int):
        action (IssueSourceConfirmRequestAction):
    """

    expected_link_token: str
    expected_source_version: int
    expected_confirmation_version: int
    expected_task_version: int
    action: IssueSourceConfirmRequestAction

    def to_dict(self) -> dict[str, Any]:
        expected_link_token = self.expected_link_token

        expected_source_version = self.expected_source_version

        expected_confirmation_version = self.expected_confirmation_version

        expected_task_version = self.expected_task_version

        action = self.action.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "expectedLinkToken": expected_link_token,
                "expectedSourceVersion": expected_source_version,
                "expectedConfirmationVersion": expected_confirmation_version,
                "expectedTaskVersion": expected_task_version,
                "action": action,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        expected_link_token = d.pop("expectedLinkToken")

        expected_source_version = d.pop("expectedSourceVersion")

        expected_confirmation_version = d.pop("expectedConfirmationVersion")

        expected_task_version = d.pop("expectedTaskVersion")

        action = IssueSourceConfirmRequestAction(d.pop("action"))

        issue_source_confirm_request = cls(
            expected_link_token=expected_link_token,
            expected_source_version=expected_source_version,
            expected_confirmation_version=expected_confirmation_version,
            expected_task_version=expected_task_version,
            action=action,
        )

        return issue_source_confirm_request
