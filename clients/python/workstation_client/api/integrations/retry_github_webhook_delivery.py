from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.github_webhook_delivery_response import GithubWebhookDeliveryResponse
from ...types import Response


def _get_kwargs(
    workspace_id: int,
    id: int,
    delivery_id: int,
) -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/api/workspaces/{workspace_id}/integrations/{id}/webhook/deliveries/{delivery_id}/retry".format(
            workspace_id=quote(str(workspace_id), safe=""),
            id=quote(str(id), safe=""),
            delivery_id=quote(str(delivery_id), safe=""),
        ),
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | GithubWebhookDeliveryResponse | None:
    if response.status_code == 200:
        response_200 = GithubWebhookDeliveryResponse.from_dict(response.json())

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

    if response.status_code == 409:
        response_409 = ApiError.from_dict(response.json())

        return response_409

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ApiError | GithubWebhookDeliveryResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    workspace_id: int,
    id: int,
    delivery_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | GithubWebhookDeliveryResponse]:
    """显式重试耗尽或取消的来源刷新，重新校验当前关联

    Args:
        workspace_id (int):
        id (int):
        delivery_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | GithubWebhookDeliveryResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        delivery_id=delivery_id,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    workspace_id: int,
    id: int,
    delivery_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | GithubWebhookDeliveryResponse | None:
    """显式重试耗尽或取消的来源刷新，重新校验当前关联

    Args:
        workspace_id (int):
        id (int):
        delivery_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | GithubWebhookDeliveryResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        id=id,
        delivery_id=delivery_id,
        client=client,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    id: int,
    delivery_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> Response[ApiError | GithubWebhookDeliveryResponse]:
    """显式重试耗尽或取消的来源刷新，重新校验当前关联

    Args:
        workspace_id (int):
        id (int):
        delivery_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | GithubWebhookDeliveryResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        id=id,
        delivery_id=delivery_id,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    id: int,
    delivery_id: int,
    *,
    client: AuthenticatedClient | Client,
) -> ApiError | GithubWebhookDeliveryResponse | None:
    """显式重试耗尽或取消的来源刷新，重新校验当前关联

    Args:
        workspace_id (int):
        id (int):
        delivery_id (int):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | GithubWebhookDeliveryResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            id=id,
            delivery_id=delivery_id,
            client=client,
        )
    ).parsed
