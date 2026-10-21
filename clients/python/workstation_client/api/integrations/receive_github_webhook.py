from http import HTTPStatus
from typing import Any, cast
from urllib.parse import quote
from uuid import UUID

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.receive_github_webhook_body import ReceiveGithubWebhookBody
from ...types import Response


def _get_kwargs(
    endpoint_id: UUID,
    *,
    body: ReceiveGithubWebhookBody,
    x_hub_signature_256: str,
    x_git_hub_delivery: str,
    x_git_hub_event: str,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}
    headers["X-Hub-Signature-256"] = x_hub_signature_256

    headers["X-GitHub-Delivery"] = x_git_hub_delivery

    headers["X-GitHub-Event"] = x_git_hub_event

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/api/hooks/github/{endpoint_id}".format(
            endpoint_id=quote(str(endpoint_id), safe=""),
        ),
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(*, client: AuthenticatedClient | Client, response: httpx.Response) -> Any | ApiError | None:
    if response.status_code == 202:
        response_202 = cast(Any, None)
        return response_202

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

    if response.status_code == 413:
        response_413 = cast(Any, None)
        return response_413

    if response.status_code == 503:
        response_503 = cast(Any, None)
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
    endpoint_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: ReceiveGithubWebhookBody,
    x_hub_signature_256: str,
    x_git_hub_delivery: str,
    x_git_hub_event: str,
) -> Response[Any | ApiError]:
    """GitHub 专用原文 HMAC-SHA256 验签回调，不使用 Session 或 Bearer 认证

    Args:
        endpoint_id (UUID):
        x_hub_signature_256 (str):
        x_git_hub_delivery (str):
        x_git_hub_event (str):
        body (ReceiveGithubWebhookBody):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Any | ApiError]
    """

    kwargs = _get_kwargs(
        endpoint_id=endpoint_id,
        body=body,
        x_hub_signature_256=x_hub_signature_256,
        x_git_hub_delivery=x_git_hub_delivery,
        x_git_hub_event=x_git_hub_event,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    endpoint_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: ReceiveGithubWebhookBody,
    x_hub_signature_256: str,
    x_git_hub_delivery: str,
    x_git_hub_event: str,
) -> Any | ApiError | None:
    """GitHub 专用原文 HMAC-SHA256 验签回调，不使用 Session 或 Bearer 认证

    Args:
        endpoint_id (UUID):
        x_hub_signature_256 (str):
        x_git_hub_delivery (str):
        x_git_hub_event (str):
        body (ReceiveGithubWebhookBody):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Any | ApiError
    """

    return sync_detailed(
        endpoint_id=endpoint_id,
        client=client,
        body=body,
        x_hub_signature_256=x_hub_signature_256,
        x_git_hub_delivery=x_git_hub_delivery,
        x_git_hub_event=x_git_hub_event,
    ).parsed


async def asyncio_detailed(
    endpoint_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: ReceiveGithubWebhookBody,
    x_hub_signature_256: str,
    x_git_hub_delivery: str,
    x_git_hub_event: str,
) -> Response[Any | ApiError]:
    """GitHub 专用原文 HMAC-SHA256 验签回调，不使用 Session 或 Bearer 认证

    Args:
        endpoint_id (UUID):
        x_hub_signature_256 (str):
        x_git_hub_delivery (str):
        x_git_hub_event (str):
        body (ReceiveGithubWebhookBody):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Any | ApiError]
    """

    kwargs = _get_kwargs(
        endpoint_id=endpoint_id,
        body=body,
        x_hub_signature_256=x_hub_signature_256,
        x_git_hub_delivery=x_git_hub_delivery,
        x_git_hub_event=x_git_hub_event,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    endpoint_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    body: ReceiveGithubWebhookBody,
    x_hub_signature_256: str,
    x_git_hub_delivery: str,
    x_git_hub_event: str,
) -> Any | ApiError | None:
    """GitHub 专用原文 HMAC-SHA256 验签回调，不使用 Session 或 Bearer 认证

    Args:
        endpoint_id (UUID):
        x_hub_signature_256 (str):
        x_git_hub_delivery (str):
        x_git_hub_event (str):
        body (ReceiveGithubWebhookBody):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Any | ApiError
    """

    return (
        await asyncio_detailed(
            endpoint_id=endpoint_id,
            client=client,
            body=body,
            x_hub_signature_256=x_hub_signature_256,
            x_git_hub_delivery=x_git_hub_delivery,
            x_git_hub_event=x_git_hub_event,
        )
    ).parsed
