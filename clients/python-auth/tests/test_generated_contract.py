import datetime
import unittest

from workstation_client.models import ApiError, NoteRequest, NoteRevisionRestoreRequest, NoteRevisionResponse, NoteRevisionSource


class GeneratedContractTests(unittest.TestCase):
    """验证生成模型与 Workstation HTTP 契约的关键约定。"""

    def test_note_revision_preserves_baseline_author_and_restore_version(self) -> None:
        request = NoteRevisionRestoreRequest(expected_version=7)
        self.assertEqual(request.to_dict(), {"expectedVersion": 7})
        payload = {
            "id": 11, "noteId": 3, "noteVersion": 5, "title": "历史标题", "content": "# 历史正文",
            "actorSubject": None, "actorDisplayName": None, "createdAt": "2026-10-19T12:00:00+08:00",
            "source": "BASELINE", "restoredFromRevisionId": None,
        }
        revision = NoteRevisionResponse.from_dict(payload)
        self.assertEqual(revision.source, NoteRevisionSource.BASELINE)
        self.assertIsNone(revision.actor_subject)
        self.assertEqual(revision.content, "# 历史正文")
        self.assertEqual(revision.to_dict(), payload)

    def test_issue_task_source_preserves_identity_and_tracking_state(self) -> None:
        from workstation_client.models import IssueTaskLinkRequest, TaskIssueSourceResponse
        request = IssueTaskLinkRequest(development_item_id=1, sharing_confirmed=True, task_id=5)
        self.assertEqual(request.to_dict(), {"developmentItemId": 1, "sharingConfirmed": True, "taskId": 5})
        payload = {
            "id": 4, "taskId": 5, "projectId": 20, "repositoryId": 2, "repositoryName": "test/repo",
            "externalId": "100", "issueNumber": 1, "title": "来源", "url": None, "status": "CLOSED",
            "stateReason": "not_planned", "sourceUpdatedAt": None, "lastAttemptAt": None,
            "lastSuccessAt": None, "lastError": "权限不足", "trackingStatus": "ERROR",
        }
        self.assertEqual(TaskIssueSourceResponse.from_dict(payload).to_dict(), payload)

    def test_api_error_parses_offset_timestamp(self) -> None:
        error = ApiError.from_dict(
            {
                "code": "BAD_REQUEST",
                "message": "请求参数不合法",
                "timestamp": "2026-09-15T16:30:00+08:00",
            }
        )

        self.assertEqual(error.timestamp.utcoffset(), datetime.timedelta(hours=8))

    def test_note_request_uses_contract_json_names(self) -> None:
        request = NoteRequest(
            title="OpenAPI",
            content="# Contract",
            folder_id=None,
            project_id=None,
            tag_ids=[1, 2],
        )

        self.assertEqual(
            request.to_dict(),
            {
                "title": "OpenAPI",
                "content": "# Contract",
                "folderId": None,
                "projectId": None,
                "tagIds": [1, 2],
            },
        )


if __name__ == "__main__":
    unittest.main()
