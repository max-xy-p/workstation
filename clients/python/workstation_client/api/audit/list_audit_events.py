from http import HTTPStatus
from typing import Any
from urllib.parse import quote

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.audit_event_page_response import AuditEventPageResponse
from ...models.audit_result import AuditResult
from ...types import UNSET, Response, Unset


def _get_kwargs(
    workspace_id: int,
    *,
    actor_subject: str | Unset = UNSET,
    action: str | Unset = UNSET,
    resource_type: str | Unset = UNSET,
    result: AuditResult | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["actorSubject"] = actor_subject

    params["action"] = action

    params["resourceType"] = resource_type

    json_result: str | Unset = UNSET
    if not isinstance(result, Unset):
        json_result = result.value

    params["result"] = json_result

    params["page"] = page

    params["size"] = size

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/workspaces/{workspace_id}/audit-events".format(
            workspace_id=quote(str(workspace_id), safe=""),
        ),
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | AuditEventPageResponse | None:
    if response.status_code == 200:
        response_200 = AuditEventPageResponse.from_dict(response.json())

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
) -> Response[ApiError | AuditEventPageResponse]:
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
    actor_subject: str | Unset = UNSET,
    action: str | Unset = UNSET,
    resource_type: str | Unset = UNSET,
    result: AuditResult | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | AuditEventPageResponse]:
    """分页查询工作空间审计事件

    Args:
        workspace_id (int):
        actor_subject (str | Unset):
        action (str | Unset):
        resource_type (str | Unset):
        result (AuditResult | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | AuditEventPageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        actor_subject=actor_subject,
        action=action,
        resource_type=resource_type,
        result=result,
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
    actor_subject: str | Unset = UNSET,
    action: str | Unset = UNSET,
    resource_type: str | Unset = UNSET,
    result: AuditResult | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | AuditEventPageResponse | None:
    """分页查询工作空间审计事件

    Args:
        workspace_id (int):
        actor_subject (str | Unset):
        action (str | Unset):
        resource_type (str | Unset):
        result (AuditResult | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | AuditEventPageResponse
    """

    return sync_detailed(
        workspace_id=workspace_id,
        client=client,
        actor_subject=actor_subject,
        action=action,
        resource_type=resource_type,
        result=result,
        page=page,
        size=size,
    ).parsed


async def asyncio_detailed(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    actor_subject: str | Unset = UNSET,
    action: str | Unset = UNSET,
    resource_type: str | Unset = UNSET,
    result: AuditResult | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | AuditEventPageResponse]:
    """分页查询工作空间审计事件

    Args:
        workspace_id (int):
        actor_subject (str | Unset):
        action (str | Unset):
        resource_type (str | Unset):
        result (AuditResult | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | AuditEventPageResponse]
    """

    kwargs = _get_kwargs(
        workspace_id=workspace_id,
        actor_subject=actor_subject,
        action=action,
        resource_type=resource_type,
        result=result,
        page=page,
        size=size,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    workspace_id: int,
    *,
    client: AuthenticatedClient | Client,
    actor_subject: str | Unset = UNSET,
    action: str | Unset = UNSET,
    resource_type: str | Unset = UNSET,
    result: AuditResult | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | AuditEventPageResponse | None:
    """分页查询工作空间审计事件

    Args:
        workspace_id (int):
        actor_subject (str | Unset):
        action (str | Unset):
        resource_type (str | Unset):
        result (AuditResult | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | AuditEventPageResponse
    """

    return (
        await asyncio_detailed(
            workspace_id=workspace_id,
            client=client,
            actor_subject=actor_subject,
            action=action,
            resource_type=resource_type,
            result=result,
            page=page,
            size=size,
        )
    ).parsed
