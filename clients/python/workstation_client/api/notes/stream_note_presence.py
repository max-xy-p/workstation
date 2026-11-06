from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    id: int,
) -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/notes/{id}/presence/stream".format(
            workspace_id=quote(str(workspace_id), safe=""),
            id=quote(str(id), safe=""),
        ),
    }

    return _kwargs


def _parse_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> ApiError | str | None:
    if response.status_code == 200:
        response_200 = response.text
        return response_200

    if response.status_code == 401:
        response_401 = ApiError.from_dict(response.json())

        return response_401

    if response.status_code == 403:
        response_403 = ApiError.from_dict(response.json())

        return response_403

    if response.status_code == 404:
        response_404 = ApiError.from_dict(response.json())

        return response_404

    if response.status_code == 429:
        response_429 = ApiError.from_dict(response.json())

        return response_429

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> Response[ApiError | str]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | str]:
    """订阅笔记在线状态刷新提醒

     只发送 refresh 事件，不含用户资料或正文；收到后查询快照。十五秒心跳，连接最多五十五秒且不超过令牌有效期。此接口不创建租约，断线期间通过 HTTP 补查。

    Args:
        workspace_id (int):
        id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | str]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | str | None:
    """订阅笔记在线状态刷新提醒

     只发送 refresh 事件，不含用户资料或正文；收到后查询快照。十五秒心跳，连接最多五十五秒且不超过令牌有效期。此接口不创建租约，断线期间通过 HTTP 补查。

    Args:
        workspace_id (int):
        id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | str
    """

    return sync_detailed(
        workspace_id=workspace_id,
        id=id,
        client=client,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | str]:
    """订阅笔记在线状态刷新提醒

     只发送 refresh 事件，不含用户资料或正文；收到后查询快照。十五秒心跳，连接最多五十五秒且不超过令牌有效期。此接口不创建租约，断线期间通过 HTTP 补查。

    Args:
        workspace_id (int):
        id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | str]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | str | None:
    """订阅笔记在线状态刷新提醒

     只发送 refresh 事件，不含用户资料或正文；收到后查询快照。十五秒心跳，连接最多五十五秒且不超过令牌有效期。此接口不创建租约，断线期间通过 HTTP 补查。

    Args:
        workspace_id (int):
        id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | str
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            id=id,
            client=client,
        )
    ).parsed
