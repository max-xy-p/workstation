from http import HTTPStatus
from typing import Any
from urllib.parse import quote
from uuid import UUID

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.invitation_response import InvitationResponse
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    invitation_id: UUID,
) -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/api/workspaces/{workspace_id}/invitations/{invitation_id}/revoke".format(
            workspace_id=quote(str(workspace_id), safe=""),
            invitation_id=quote(str(invitation_id), safe=""),
        ),
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | InvitationResponse | None:
    if response.status_code == 200:
        response_200 = InvitationResponse.from_dict(response.json())

        return response_200

    if response.status_code == 400:
        response_400 = ApiError.from_dict(response.json())

        return response_400

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
) -> Response[ApiError | InvitationResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    invitation_id: UUID,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | InvitationResponse]:
    """撤销尚未接受的邀请

    Args:
        workspace_id (int):
        invitation_id (UUID):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | InvitationResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        invitation_id=invitation_id,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    invitation_id: UUID,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | InvitationResponse | None:
    """撤销尚未接受的邀请

    Args:
        workspace_id (int):
        invitation_id (UUID):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | InvitationResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        invitation_id=invitation_id,
        client=client,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    invitation_id: UUID,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | InvitationResponse]:
    """撤销尚未接受的邀请

    Args:
        workspace_id (int):
        invitation_id (UUID):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | InvitationResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        invitation_id=invitation_id,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    invitation_id: UUID,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | InvitationResponse | None:
    """撤销尚未接受的邀请

    Args:
        workspace_id (int):
        invitation_id (UUID):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | InvitationResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            invitation_id=invitation_id,
            client=client,
        )
    ).parsed
