from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.workspace_member_candidate import WorkspaceMemberCandidate
from ...types import UNSET, Response


def _get_kwargs(
    workspace_id: int,
    *,
    username: str,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["username"] = username

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/member-candidates".format(
            workspace_id=quote(str(workspace_id), safe=""),
        ),
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | list[WorkspaceMemberCandidate] | None:
    if response.status_code == 200:
        response_200 = []
        _response_200 = response.json()
        for response_200_item_data in _response_200:
            response_200_item = WorkspaceMemberCandidate.from_dict(response_200_item_data)

            response_200.append(response_200_item)

        return response_200

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

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ApiError | list[WorkspaceMemberCandidate]]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    username: str,
) -> Response[ApiError | list[WorkspaceMemberCandidate]]:
    """按精确用户名查询已登录过的用户

    Args:
        workspace_id (int):
        username (str):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | list[WorkspaceMemberCandidate]]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        username=username,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    username: str,
) -> ApiError | list[WorkspaceMemberCandidate] | None:
    """按精确用户名查询已登录过的用户

    Args:
        workspace_id (int):
        username (str):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | list[WorkspaceMemberCandidate]
    """

    return sync_detailed(
        workspace_id=workspace_id,
        client=client,
        username=username,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    username: str,
) -> Response[ApiError | list[WorkspaceMemberCandidate]]:
    """按精确用户名查询已登录过的用户

    Args:
        workspace_id (int):
        username (str):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | list[WorkspaceMemberCandidate]]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        username=username,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    username: str,
) -> ApiError | list[WorkspaceMemberCandidate] | None:
    """按精确用户名查询已登录过的用户

    Args:
        workspace_id (int):
        username (str):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | list[WorkspaceMemberCandidate]
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            client=client,
            username=username,
        )
    ).parsed
