from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.integration_connection_request import IntegrationConnectionRequest
from ...models.integration_connection_response import IntegrationConnectionResponse
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    connection_id: int,
    *,
    body: IntegrationConnectionRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "patch",
        "url": "/api/workspaces/{workspace_id}/integrations/{connection_id}".format(
            workspace_id=quote(str(workspace_id), safe=""),
            connection_id=quote(str(connection_id), safe=""),
        ),
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | IntegrationConnectionResponse | None:
    if response.status_code == 200:
        response_200 = IntegrationConnectionResponse.from_dict(response.json())

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

    if response.status_code == 409:
        response_409 = ApiError.from_dict(response.json())

        return response_409

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ApiError | IntegrationConnectionResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    connection_id: int,
    *,
    client: AuthenticatedClient | Client,
    body: IntegrationConnectionRequest,
) -> Response[ApiError | IntegrationConnectionResponse]:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        body (IntegrationConnectionRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | IntegrationConnectionResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        connection_id=connection_id,
        body=body,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    connection_id: int,
    *,
    client: AuthenticatedClient | Client,
    body: IntegrationConnectionRequest,
) -> ApiError | IntegrationConnectionResponse | None:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        body (IntegrationConnectionRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | IntegrationConnectionResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        connection_id=connection_id,
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    connection_id: int,
    *,
    client: AuthenticatedClient | Client,
    body: IntegrationConnectionRequest,
) -> Response[ApiError | IntegrationConnectionResponse]:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        body (IntegrationConnectionRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | IntegrationConnectionResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        connection_id=connection_id,
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    connection_id: int,
    *,
    client: AuthenticatedClient | Client,
    body: IntegrationConnectionRequest,
) -> ApiError | IntegrationConnectionResponse | None:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        body (IntegrationConnectionRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | IntegrationConnectionResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            connection_id=connection_id,
            client=client,
            body=body,
        )
    ).parsed
