import unittest
from unittest.mock import patch

import httpx

from workstation_auth import ClientCredentialsProvider, TokenResponse


class ClientCredentialsProviderTests(unittest.TestCase):
    """验证 Token 获取、缓存和 Gateway Client 创建。"""

    def test_requests_and_caches_client_credentials_token(self) -> None:
        calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal calls
            calls += 1
            self.assertEqual(request.url.path, "/auth/realms/workstation/protocol/openid-connect/token")
            self.assertIn(b"grant_type=client_credentials", request.content)
            self.assertNotIn(b"client_secret", request.content)
            self.assertTrue(request.headers["Authorization"].startswith("Basic "))
            return httpx.Response(
                200,
                json={"access_token": "signed-token", "expires_in": 300, "token_type": "Bearer"},
            )

        provider = ClientCredentialsProvider(
            token_url="https://example.test/auth/realms/workstation/protocol/openid-connect/token",
            client_id="workstation-python",
            client_secret="secret",
        )
        transport = httpx.MockTransport(handler)
        token_client = httpx.Client(transport=transport)
        self.addCleanup(token_client.close)

        with patch("workstation_auth.client_credentials.httpx.Client") as client_factory:
            client_factory.return_value.__enter__.return_value = token_client
            first = provider.get_access_token()
            second = provider.get_access_token()

        self.assertEqual(first, "signed-token")
        self.assertEqual(second, "signed-token")
        self.assertEqual(calls, 1)
        self.assertNotIn("secret", repr(provider))
        self.assertNotIn("signed-token", repr(provider))

        token_response = TokenResponse(access_token="signed-token", expires_in=300)
        self.assertNotIn("signed-token", repr(token_response))

    def test_creates_generated_authenticated_client_for_gateway(self) -> None:
        provider = ClientCredentialsProvider(
            token_url="https://example.test/token",
            client_id="workstation-python",
            client_secret="secret",
        )
        provider._access_token = "signed-token"
        provider._expires_at = float("inf")

        client = provider.create_client("https://workstation.example.test/")

        request = client.get_httpx_client().build_request("GET", "/api/projects")
        self.assertEqual(str(request.url), "https://workstation.example.test/api/projects")
        self.assertEqual(request.headers["Authorization"], "Bearer signed-token")


if __name__ == "__main__":
    unittest.main()
