from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.project_response import ProjectResponse
from ...models.project_status_request import ProjectStatusRequest
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    id: int,
    *,
    body: ProjectStatusRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "patch",
        "url": "/api/workspaces/{workspace_id}/projects/{id}/status".format(
            workspace_id=quote(str(workspace_id), safe=""),
            id=quote(str(id), safe=""),
        ),
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | ProjectResponse | None:
    if response.status_code == 200:
        response_200 = ProjectResponse.from_dict(response.json())

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
) -> Response[ApiError | ProjectResponse]:
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
    body: ProjectStatusRequest,
) -> Response[ApiError | ProjectResponse]:
    """更新项目状态

    Args:
        workspace_id (int):
        id (int):
        body (ProjectStatusRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | ProjectResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        body=body,
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
    body: ProjectStatusRequest,
) -> ApiError | ProjectResponse | None:
    """更新项目状态

    Args:
        workspace_id (int):
        id (int):
        body (ProjectStatusRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | ProjectResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        id=id,
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
    body: ProjectStatusRequest,
) -> Response[ApiError | ProjectResponse]:
    """更新项目状态

    Args:
        workspace_id (int):
        id (int):
        body (ProjectStatusRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | ProjectResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    id: int,
    *,
    client: AuthenticatedClient | Client,
    body: ProjectStatusRequest,
) -> ApiError | ProjectResponse | None:
    """更新项目状态

    Args:
        workspace_id (int):
        id (int):
        body (ProjectStatusRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | ProjectResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            id=id,
            client=client,
            body=body,
        )
    ).parsed
