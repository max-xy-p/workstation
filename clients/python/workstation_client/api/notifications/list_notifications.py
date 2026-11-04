from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.notification_page_response import NotificationPageResponse
from ...types import UNSET, Response, Unset


def _get_kwargs(
    *,
    page: int | Unset = 0,
    size: int | Unset = 20,
    unread_only: bool | Unset = False,
    up_to_sequence: int | Unset = UNSET,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["page"] = page

    params["size"] = size

    params["unreadOnly"] = unread_only

    params["upToSequence"] = up_to_sequence

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/notifications",
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | NotificationPageResponse | None:
    if response.status_code == 200:
        response_200 = NotificationPageResponse.from_dict(response.json())

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
) -> Response[ApiError | NotificationPageResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    unread_only: bool | Unset = False,
    up_to_sequence: int | Unset = UNSET,
) -> Response[ApiError | NotificationPageResponse]:
    """查询当前用户仍有 Workspace 访问权限的通知

    Args:
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        unread_only (bool | Unset):  Default: False.
        up_to_sequence (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NotificationPageResponse]
    """

    kwargs = _get_kwargs(
        page=page,
        size=size,
        unread_only=unread_only,
        up_to_sequence=up_to_sequence,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    unread_only: bool | Unset = False,
    up_to_sequence: int | Unset = UNSET,
) -> ApiError | NotificationPageResponse | None:
    """查询当前用户仍有 Workspace 访问权限的通知

    Args:
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        unread_only (bool | Unset):  Default: False.
        up_to_sequence (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NotificationPageResponse
    """

    return sync_detailed(
        client=client,
        page=page,
        size=size,
        unread_only=unread_only,
        up_to_sequence=up_to_sequence,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    unread_only: bool | Unset = False,
    up_to_sequence: int | Unset = UNSET,
) -> Response[ApiError | NotificationPageResponse]:
    """查询当前用户仍有 Workspace 访问权限的通知

    Args:
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        unread_only (bool | Unset):  Default: False.
        up_to_sequence (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | NotificationPageResponse]
    """

    kwargs = _get_kwargs(
        page=page,
        size=size,
        unread_only=unread_only,
        up_to_sequence=up_to_sequence,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    page: int | Unset = 0,
    size: int | Unset = 20,
    unread_only: bool | Unset = False,
    up_to_sequence: int | Unset = UNSET,
) -> ApiError | NotificationPageResponse | None:
    """查询当前用户仍有 Workspace 访问权限的通知

    Args:
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.
        unread_only (bool | Unset):  Default: False.
        up_to_sequence (int | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | NotificationPageResponse
    """

    return (
        await asyncio_detailed(
            client=client,
            page=page,
            size=size,
            unread_only=unread_only,
            up_to_sequence=up_to_sequence,
        )
    ).parsed
