from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.api_error import ApiError
from ...models.task_page_response import TaskPageResponse
from ...models.task_priority import TaskPriority
from ...models.task_status import TaskStatus
from ...types import UNSET, Response, Unset


def _get_kwargs(
    *,
    keyword: str | Unset = UNSET,
    project_id: int | Unset = UNSET,
    status: TaskStatus | Unset = UNSET,
    priority: TaskPriority | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["keyword"] = keyword

    params["projectId"] = project_id

    json_status: str | Unset = UNSET
    if not isinstance(status, Unset):
        json_status = status.value

    params["status"] = json_status

    json_priority: str | Unset = UNSET
    if not isinstance(priority, Unset):
        json_priority = priority.value

    params["priority"] = json_priority

    params["page"] = page

    params["size"] = size

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/api/tasks",
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ApiError | TaskPageResponse | None:
    if response.status_code == 200:
        response_200 = TaskPageResponse.from_dict(response.json())

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
) -> Response[ApiError | TaskPageResponse]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    project_id: int | Unset = UNSET,
    status: TaskStatus | Unset = UNSET,
    priority: TaskPriority | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | TaskPageResponse]:
    """分页查询任务

    Args:
        keyword (str | Unset):
        project_id (int | Unset):
        status (TaskStatus | Unset):
        priority (TaskPriority | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | TaskPageResponse]
    """

    kwargs = _get_kwargs(
        keyword=keyword,
        project_id=project_id,
        status=status,
        priority=priority,
        page=page,
        size=size,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    project_id: int | Unset = UNSET,
    status: TaskStatus | Unset = UNSET,
    priority: TaskPriority | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | TaskPageResponse | None:
    """分页查询任务

    Args:
        keyword (str | Unset):
        project_id (int | Unset):
        status (TaskStatus | Unset):
        priority (TaskPriority | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | TaskPageResponse
    """

    return sync_detailed(
        client=client,
        keyword=keyword,
        project_id=project_id,
        status=status,
        priority=priority,
        page=page,
        size=size,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    project_id: int | Unset = UNSET,
    status: TaskStatus | Unset = UNSET,
    priority: TaskPriority | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> Response[ApiError | TaskPageResponse]:
    """分页查询任务

    Args:
        keyword (str | Unset):
        project_id (int | Unset):
        status (TaskStatus | Unset):
        priority (TaskPriority | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ApiError | TaskPageResponse]
    """

    kwargs = _get_kwargs(
        keyword=keyword,
        project_id=project_id,
        status=status,
        priority=priority,
        page=page,
        size=size,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    keyword: str | Unset = UNSET,
    project_id: int | Unset = UNSET,
    status: TaskStatus | Unset = UNSET,
    priority: TaskPriority | Unset = UNSET,
    page: int | Unset = 0,
    size: int | Unset = 20,
) -> ApiError | TaskPageResponse | None:
    """分页查询任务

    Args:
        keyword (str | Unset):
        project_id (int | Unset):
        status (TaskStatus | Unset):
        priority (TaskPriority | Unset):
        page (int | Unset):  Default: 0.
        size (int | Unset):  Default: 20.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ApiError | TaskPageResponse
    """

    return (
        await asyncio_detailed(
            client=client,
            keyword=keyword,
            project_id=project_id,
            status=status,
            priority=priority,
            page=page,
            size=size,
        )
    ).parsed
