from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.note_page_response import NotePageResponse
from ...types import UNSET, Response, Unset


def _get_kwargs(
    workspace_id: int,
    *,
    keyword: str | Unset = UNSET,
    folder_id: int | Unset = UNSET,
    tag_id: int | Unset = UNSET,
    project_id: int | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["keyword"] = keyword

    params["folderId"] = folder_id

    params["tagId"] = tag_id

    params["projectId"] = project_id

    params["page"] = page

    params["size"] = size

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/notes".format(
            workspace_id=quote(str(workspace_id), safe=""),
        ),
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | NotePageResponse | None:
    if response.status_code == 200:
        response_200 = NotePageResponse.from_dict(response.json())

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

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ApiError | NotePageResponse]:
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
    keyword: str | Unset = UNSET,
    folder_id: int | Unset = UNSET,
    tag_id: int | Unset = UNSET,
    project_id: int | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | NotePageResponse]:
    """分页查询笔记

    Args:
        workspace_id (int):
        keyword (str | Unset):
        folder_id (int | Unset):
        tag_id (int | Unset):
        project_id (int | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NotePageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        keyword=keyword,
        folder_id=folder_id,
        tag_id=tag_id,
        project_id=project_id,
        page=page,
        size=size,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    folder_id: int | Unset = UNSET,
    tag_id: int | Unset = UNSET,
    project_id: int | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | NotePageResponse | None:
    """分页查询笔记

    Args:
        workspace_id (int):
        keyword (str | Unset):
        folder_id (int | Unset):
        tag_id (int | Unset):
        project_id (int | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NotePageResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        client=client,
        keyword=keyword,
        folder_id=folder_id,
        tag_id=tag_id,
        project_id=project_id,
        page=page,
        size=size,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    folder_id: int | Unset = UNSET,
    tag_id: int | Unset = UNSET,
    project_id: int | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | NotePageResponse]:
    """分页查询笔记

    Args:
        workspace_id (int):
        keyword (str | Unset):
        folder_id (int | Unset):
        tag_id (int | Unset):
        project_id (int | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NotePageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        keyword=keyword,
        folder_id=folder_id,
        tag_id=tag_id,
        project_id=project_id,
        page=page,
        size=size,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    folder_id: int | Unset = UNSET,
    tag_id: int | Unset = UNSET,
    project_id: int | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | NotePageResponse | None:
    """分页查询笔记

    Args:
        workspace_id (int):
        keyword (str | Unset):
        folder_id (int | Unset):
        tag_id (int | Unset):
        project_id (int | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NotePageResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            client=client,
            keyword=keyword,
            folder_id=folder_id,
            tag_id=tag_id,
            project_id=project_id,
            page=page,
            size=size,
        )
    ).parsed
