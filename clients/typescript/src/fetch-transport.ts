import type { ApiErrorResponse, QueryParams, WorkstationTransport } from './index.js';

type QueryValue = string | number | boolean | null | undefined;
export type TokenProvider = () => string | undefined | Promise<string | undefined>;

export interface FetchTransportOptions {
  baseUrl: string;
  fetch?: typeof globalThis.fetch;
  headers?: HeadersInit;
  token?: string | TokenProvider;
}

export class WorkstationHttpError extends Error {
  readonly status: number;
  readonly response?: Partial<ApiErrorResponse>;

  constructor(message: string, status: number, response?: Partial<ApiErrorResponse>) {
    super(message);
    this.name = 'WorkstationHttpError';
    this.status = status;
    this.response = response;
  }
}

function buildUrl(baseUrl: string, path: string, params: QueryParams = {}): URL {
  const normalizedBaseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  const url = new URL(path.replace(/^\//, ''), normalizedBaseUrl);
  Object.entries(params as Record<string, QueryValue>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  });
  return url;
}

function isApiError(value: unknown): value is Partial<ApiErrorResponse> {
  return typeof value === 'object' && value !== null;
}

export function createFetchTransport(options: FetchTransportOptions): WorkstationTransport {
  const fetchImplementation = options.fetch ?? globalThis.fetch;
  if (!fetchImplementation) {
    throw new Error('A Fetch implementation is required');
  }

  async function request<T>(
    method: string,
    path: string,
    params?: QueryParams,
    body?: unknown
  ): Promise<T> {
    const headers = new Headers(options.headers);
    const token = typeof options.token === 'function' ? await options.token() : options.token;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    if (body !== undefined) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetchImplementation(buildUrl(options.baseUrl, path, params), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });

    if (response.status === 204) {
      return null as T;
    }

    const contentType = response.headers.get('content-type') ?? '';
    const data: unknown = contentType.includes('application/json')
      ? await response.json()
      : undefined;
    if (!response.ok) {
      const error = isApiError(data) ? data : undefined;
      const message = error?.message ?? response.statusText ?? `HTTP ${response.status}`;
      throw new WorkstationHttpError(message, response.status, error);
    }
    return data as T;
  }

  return {
    get: <T>(path: string, params?: QueryParams) => request<T>('GET', path, params),
    post: <T, B>(path: string, body: B) => request<T>('POST', path, undefined, body),
    put: <T, B>(path: string, body: B) => request<T>('PUT', path, undefined, body),
    patch: <T, B>(path: string, body: B) => request<T>('PATCH', path, undefined, body),
    delete: <T = null>(path: string) => request<T>('DELETE', path)
  };
}
