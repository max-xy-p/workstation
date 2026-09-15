import datetime
import unittest

from workstation_client.models import ApiError, NoteRequest


class GeneratedContractTests(unittest.TestCase):
    """验证生成模型与 Workstation HTTP 契约的关键约定。"""

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
