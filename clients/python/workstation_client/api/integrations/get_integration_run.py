from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.integration_run_response import IntegrationRunResponse
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    connection_id: int,
    run_id: int,
) -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/integrations/{connection_id}/sync-runs/{run_id}".format(
            workspace_id=quote(str(workspace_id), safe=""),
            connection_id=quote(str(connection_id), safe=""),
            run_id=quote(str(run_id), safe=""),
        ),
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | IntegrationRunResponse | None:
    if response.status_code == 200:
        response_200 = IntegrationRunResponse.from_dict(response.json())

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
) -> Response[ApiError | IntegrationRunResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    connection_id: int,
    run_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | IntegrationRunResponse]:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        run_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | IntegrationRunResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        connection_id=connection_id,
        run_id=run_id,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    connection_id: int,
    run_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | IntegrationRunResponse | None:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        run_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | IntegrationRunResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        connection_id=connection_id,
        run_id=run_id,
        client=client,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    connection_id: int,
    run_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | IntegrationRunResponse]:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        run_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | IntegrationRunResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        connection_id=connection_id,
        run_id=run_id,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    connection_id: int,
    run_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | IntegrationRunResponse | None:
    """外部数据源与仓库同步

    Args:
        workspace_id (int):
        connection_id (int):
        run_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | IntegrationRunResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            connection_id=connection_id,
            run_id=run_id,
            client=client,
        )
    ).parsed
