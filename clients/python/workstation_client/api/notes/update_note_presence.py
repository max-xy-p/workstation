from http import HTTPStatus
from typing import Any, cast
from urllib.parse import quote
from uuid import UUID

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.note_presence_request import NotePresenceRequest
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    id: int,
    session_id: UUID,
    *,
    body: NotePresenceRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "put",
        "url": "/api/workspaces/{workspace_id}/notes/{id}/presence/sessions/{session_id}".format(
            workspace_id=quote(str(workspace_id), safe=""),
            id=quote(str(id), safe=""),
            session_id=quote(str(session_id), safe=""),
        ),
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> Any | ApiError | None:
    if response.status_code == 204:
        response_204 = cast(Any, None)
        return response_204

    if response.status_code == 400:
        response_400 = ApiError.from_dict(response.json())

        return response_400

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

    if response.status_code == 503:
        response_503 = ApiError.from_dict(response.json())

        return response_503

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> Response[Any | ApiError]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    id: int,
    session_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: NotePresenceRequest,
) -> Response[Any | ApiError]:
    """续租当前用户的页面状态；LEFT 表示离开

     仅人类用户可调用。身份来自令牌；同一页面使用递增 sequence，旧请求不覆盖新状态。EDITING 需要 NOTE_WRITE，其余需要 NOTE_READ。租约最多四十五秒，不锁定正文。

    Args:
        workspace_id (int):
        id (int):
        session_id (UUID):
        body (NotePresenceRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Any | ApiError]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        session_id=session_id,
        body=body,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    id: int,
    session_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: NotePresenceRequest,
) -> Any | ApiError | None:
    """续租当前用户的页面状态；LEFT 表示离开

     仅人类用户可调用。身份来自令牌；同一页面使用递增 sequence，旧请求不覆盖新状态。EDITING 需要 NOTE_WRITE，其余需要 NOTE_READ。租约最多四十五秒，不锁定正文。

    Args:
        workspace_id (int):
        id (int):
        session_id (UUID):
        body (NotePresenceRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Any | ApiError
    """

    return sync_detailed(
        workspace_id=workspace_id,
        id=id,
        session_id=session_id,
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    id: int,
    session_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: NotePresenceRequest,
) -> Response[Any | ApiError]:
    """续租当前用户的页面状态；LEFT 表示离开

     仅人类用户可调用。身份来自令牌；同一页面使用递增 sequence，旧请求不覆盖新状态。EDITING 需要 NOTE_WRITE，其余需要 NOTE_READ。租约最多四十五秒，不锁定正文。

    Args:
        workspace_id (int):
        id (int):
        session_id (UUID):
        body (NotePresenceRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Any | ApiError]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        session_id=session_id,
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    id: int,
    session_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: NotePresenceRequest,
) -> Any | ApiError | None:
    """续租当前用户的页面状态；LEFT 表示离开

     仅人类用户可调用。身份来自令牌；同一页面使用递增 sequence，旧请求不覆盖新状态。EDITING 需要 NOTE_WRITE，其余需要 NOTE_READ。租约最多四十五秒，不锁定正文。

    Args:
        workspace_id (int):
        id (int):
        session_id (UUID):
        body (NotePresenceRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Any | ApiError
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            id=id,
            session_id=session_id,
            client=client,
            body=body,
        )
    ).parsed
