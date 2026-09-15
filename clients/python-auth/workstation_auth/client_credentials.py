"""为 OpenAPI 生成的 Python Client 提供 Client Credentials 认证适配。"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from threading import Lock
from typing import Any

import httpx

from workstation_client import AuthenticatedClient


@dataclass(frozen=True)
class TokenResponse:
    """经过必要校验的 Keycloak Token 响应。"""

    # Token 不参与 repr，避免对象被记录日志时泄露凭证。
    access_token: str = field(repr=False)
    expires_in: int
    token_type: str = "Bearer"


@dataclass
class ClientCredentialsProvider:
    """获取并缓存机器身份 Token，再创建已认证的生成客户端。

    `client_secret` 不参与对象的字符串表示。业务代码应从环境变量或密钥服务
    读取它，不能将其写入源码、OpenAPI 或日志。
    """

    token_url: str
    client_id: str
    client_secret: str = field(repr=False)
    scope: str | None = None
    timeout: float = 10.0
    expiry_leeway: float = 30.0
    verify_ssl: bool | str = True
    _access_token: str | None = field(default=None, init=False, repr=False)
    _expires_at: float = field(default=0.0, init=False, repr=False)
    _lock: Lock = field(default_factory=Lock, init=False, repr=False)

    def get_access_token(self) -> str:
        """返回仍然有效的缓存 Token；必要时以同步方式重新申请。"""

        if self._token_is_valid():
            return self._access_token or ""
        # 双重检查避免多个同步线程同时发现 Token 过期后重复申请。
        with self._lock:
            if not self._token_is_valid():
                token = self._request_token()
                self._store(token)
        return self._access_token or ""

    async def get_access_token_async(self) -> str:
        """返回仍然有效的缓存 Token；必要时以异步方式重新申请。"""

        if self._token_is_valid():
            return self._access_token or ""
        token = await self._request_token_async()
        self._store(token)
        return token.access_token

    def create_client(
        self,
        base_url: str,
        *,
        api_timeout: float = 30.0,
        raise_on_unexpected_status: bool = True,
    ) -> AuthenticatedClient:
        """创建通过 Gateway 调用业务 API 的同步生成客户端。"""

        return AuthenticatedClient(
            # 生成客户端会在 base_url 后拼接 `/api/...`，这里只传 Gateway 根地址。
            base_url=base_url.rstrip("/"),
            token=self.get_access_token(),
            timeout=httpx.Timeout(api_timeout),
            verify_ssl=self.verify_ssl,
            raise_on_unexpected_status=raise_on_unexpected_status,
        )

    async def create_client_async(
        self,
        base_url: str,
        *,
        api_timeout: float = 30.0,
        raise_on_unexpected_status: bool = True,
    ) -> AuthenticatedClient:
        """创建通过 Gateway 调用业务 API 的异步生成客户端。"""

        return AuthenticatedClient(
            base_url=base_url.rstrip("/"),
            token=await self.get_access_token_async(),
            timeout=httpx.Timeout(api_timeout),
            verify_ssl=self.verify_ssl,
            raise_on_unexpected_status=raise_on_unexpected_status,
        )

    def _request_token(self) -> TokenResponse:
        with httpx.Client(timeout=self.timeout, verify=self.verify_ssl) as client:
            response = client.post(
                self.token_url,
                data=self._token_request_data(),
                # Secret 放在 HTTP Basic Auth 中，不重复放入表单正文。
                auth=httpx.BasicAuth(self.client_id, self.client_secret),
            )
        return self._parse_token_response(response)

    async def _request_token_async(self) -> TokenResponse:
        async with httpx.AsyncClient(timeout=self.timeout, verify=self.verify_ssl) as client:
            response = await client.post(
                self.token_url,
                data=self._token_request_data(),
                auth=httpx.BasicAuth(self.client_id, self.client_secret),
            )
        return self._parse_token_response(response)

    def _token_request_data(self) -> dict[str, str]:
        data = {
            "grant_type": "client_credentials",
        }
        if self.scope:
            data["scope"] = self.scope
        return data

    def _parse_token_response(self, response: httpx.Response) -> TokenResponse:
        response.raise_for_status()
        payload: dict[str, Any] = response.json()
        access_token = payload.get("access_token")
        token_type = payload.get("token_type", "Bearer")
        expires_in = payload.get("expires_in")
        if not isinstance(access_token, str) or not access_token:
            raise ValueError("Keycloak Token 响应缺少 access_token")
        if not isinstance(expires_in, int) or expires_in <= 0:
            raise ValueError("Keycloak Token 响应缺少有效的 expires_in")
        if not isinstance(token_type, str) or token_type.lower() != "bearer":
            raise ValueError("Keycloak Token 响应包含不支持的 token_type")
        return TokenResponse(access_token=access_token, expires_in=expires_in, token_type=token_type)

    def _store(self, token: TokenResponse) -> None:
        self._access_token = token.access_token
        # 单调时钟不受系统时间校准影响，适合计算进程内 Token 剩余有效期。
        self._expires_at = time.monotonic() + token.expires_in

    def _token_is_valid(self) -> bool:
        # 提前 expiry_leeway 秒视为失效，避免请求途中 Token 刚好过期。
        return (
            self._access_token is not None
            and time.monotonic() < self._expires_at - self.expiry_leeway
        )
