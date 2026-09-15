import type {
  ApiErrorResponse,
  QueryParams,
  WorkstationTransport
} from '@workstation/api-client';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error('VITE_API_BASE_URL is required');
}

type QueryValue = string | number | boolean | null | undefined;

type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  params?: QueryParams;
  body?: unknown;
  rawBody?: BodyInit;
  suppressUnauthorized?: boolean;
};

interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

type ErrorResponse = Partial<ApiErrorResponse> & {
  error?: string;
};

let csrfToken: string | null = null;
let csrfHeaderName = 'X-CSRF-TOKEN';
let csrfParameterName = '_csrf';
// 多个并发写请求共用同一次 CSRF 初始化请求，避免重复访问 Gateway。
let csrfRequest: Promise<void> | null = null;

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

function buildUrl(path: string, params: QueryParams = {}): URL {
  const baseUrl = API_BASE_URL.startsWith('http')
    ? API_BASE_URL
    : `${window.location.origin}${API_BASE_URL}`;
  const url = new URL(`${baseUrl}${path}`);
  Object.entries(params as Record<string, QueryValue>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  });
  return url;
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return typeof value === 'object' && value !== null;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return null as T;
  }

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    const message = response.ok
      ? 'API 未返回 JSON，请确认 /api/ 已代理到后端'
      : `请求失败 (${response.status})`;
    throw new ApiError(message, response.status);
  }

  const data: unknown = await response.json();
  if (!response.ok) {
    const error = isErrorResponse(data) ? data : {};
    const reason = error.message || error.error || response.statusText || '请求失败';
    throw new ApiError(`${reason} (${response.status})`, response.status, error.code);
  }
  return data as T;
}

async function loadCsrfToken(): Promise<void> {
  if (csrfToken) {
    return;
  }
  if (!csrfRequest) {
    csrfRequest = fetch(buildUrl('/auth/csrf'), { credentials: 'include' })
      .then(parseResponse<CsrfResponse>)
      .then((data) => {
        csrfHeaderName = data.headerName;
        csrfParameterName = data.parameterName;
        csrfToken = data.token;
      })
      .finally(() => {
        csrfRequest = null;
      });
  }
  await csrfRequest;
}

export function resetCsrfToken(): void {
  csrfToken = null;
  csrfRequest = null;
}

export async function submitWithCsrf(path: string): Promise<void> {
  await loadCsrfToken();
  // OIDC 退出需要浏览器跟随页面跳转，因此使用原生表单，而不是普通 fetch。
  const form = document.createElement('form');
  form.method = 'post';
  form.action = buildUrl(path).toString();
  const csrfField = document.createElement('input');
  csrfField.type = 'hidden';
  csrfField.name = csrfParameterName;
  csrfField.value = csrfToken ?? '';
  form.appendChild(csrfField);
  document.body.appendChild(form);
  form.submit();
}

async function request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const {
    params,
    body,
    rawBody,
    suppressUnauthorized = false,
    ...fetchOptions
  } = options;
  const method = (fetchOptions.method || 'GET').toUpperCase();
  const isUnsafeMethod = !['GET', 'HEAD', 'OPTIONS'].includes(method);

  if (isUnsafeMethod) {
    // 所有可能修改数据的请求都先取得 Gateway 签发的 CSRF Token。
    await loadCsrfToken();
  }

  const headers = new Headers(fetchOptions.headers);
  let requestBody = rawBody;
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
    requestBody = JSON.stringify(body);
  }
  if (isUnsafeMethod && csrfToken) {
    headers.set(csrfHeaderName, csrfToken);
  }

  const response = await fetch(buildUrl(path, params), {
    ...fetchOptions,
    method,
    credentials: 'include',
    headers,
    body: requestBody
  });

  if (response.status === 401 && !suppressUnauthorized) {
    window.dispatchEvent(new CustomEvent('workstation:unauthorized'));
  }
  if (response.status === 403) {
    resetCsrfToken();
  }

  return parseResponse<T>(response);
}

export const api = {
  get: <T>(path: string, params?: QueryParams, options: ApiRequestOptions = {}) =>
    request<T>(path, { ...options, method: 'GET', params }),
  post: <T, B>(path: string, body: B, options: ApiRequestOptions = {}) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T, B>(path: string, body: B, options: ApiRequestOptions = {}) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T, B>(path: string, body: B, options: ApiRequestOptions = {}) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T = null>(path: string) => request<T>(path, { method: 'DELETE' })
} satisfies WorkstationTransport;
