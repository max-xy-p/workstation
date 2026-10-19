from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.note_revision_page_response import NoteRevisionPageResponse
from ...types import UNSET, Response, Unset


def _get_kwargs(
    workspace_id: int,
    id: int,
    *,
    page: int | Unset = 0,
    size: int | Unset = 20,
    up_to_version: int | Unset = UNSET,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["page"] = page

    params["size"] = size

    params["upToVersion"] = up_to_version

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/notes/{id}/revisions".format(
            workspace_id=quote(str(workspace_id), safe=""),
            id=quote(str(id), safe=""),
        ),
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | NoteRevisionPageResponse | None:
    if response.status_code == 200:
        response_200 = NoteRevisionPageResponse.from_dict(response.json())

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
) -> Response[ApiError | NoteRevisionPageResponse]:
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
    page: int | Unset = 0,
    size: int | Unset = 20,
    up_to_version: int | Unset = UNSET,
) -> Response[ApiError | NoteRevisionPageResponse]:
    """分页查看笔记标题与正文历史

    Args:
        workspace_id (int):
        id (int):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        up_to_version (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NoteRevisionPageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        page=page,
        size=size,
        up_to_version=up_to_version,
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
    page: int | Unset = 0,
    size: int | Unset = 20,
    up_to_version: int | Unset = UNSET,
) -> ApiError | NoteRevisionPageResponse | None:
    """分页查看笔记标题与正文历史

    Args:
        workspace_id (int):
        id (int):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        up_to_version (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NoteRevisionPageResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        id=id,
        client=client,
        page=page,
        size=size,
        up_to_version=up_to_version,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    up_to_version: int | Unset = UNSET,
) -> Response[ApiError | NoteRevisionPageResponse]:
    """分页查看笔记标题与正文历史

    Args:
        workspace_id (int):
        id (int):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        up_to_version (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NoteRevisionPageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        page=page,
        size=size,
        up_to_version=up_to_version,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    up_to_version: int | Unset = UNSET,
) -> ApiError | NoteRevisionPageResponse | None:
    """分页查看笔记标题与正文历史

    Args:
        workspace_id (int):
        id (int):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        up_to_version (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NoteRevisionPageResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            id=id,
            client=client,
            page=page,
            size=size,
            up_to_version=up_to_version,
        )
    ).parsed
