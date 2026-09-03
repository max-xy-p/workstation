const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error('VITE_API_BASE_URL is required');
}

let csrfToken = null;
let csrfHeaderName = 'X-CSRF-TOKEN';
let csrfRequest = null;

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

function buildUrl(path, params = {}) {
  const baseUrl = API_BASE_URL.startsWith('http')
    ? API_BASE_URL
    : `${window.location.origin}${API_BASE_URL}`;
  const url = new URL(`${baseUrl}${path}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, value);
    }
  });
  return url;
}

async function parseResponse(response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    const message = response.ok
      ? 'API 未返回 JSON，请确认 /api/ 已代理到后端'
      : `请求失败 (${response.status})`;
    throw new ApiError(message, response.status);
  }

  const data = await response.json();
  if (!response.ok) {
    const reason = data.message || data.error || response.statusText || '请求失败';
    throw new ApiError(`${reason} (${response.status})`, response.status, data.code);
  }
  return data;
}

async function loadCsrfToken() {
  if (csrfToken) {
    return;
  }
  if (!csrfRequest) {
    csrfRequest = fetch(buildUrl('/auth/csrf'), { credentials: 'include' })
      .then(parseResponse)
      .then((data) => {
        csrfHeaderName = data.headerName;
        csrfToken = data.token;
      })
      .finally(() => {
        csrfRequest = null;
      });
  }
  await csrfRequest;
}

export function resetCsrfToken() {
  csrfToken = null;
  csrfRequest = null;
}

async function request(path, options = {}) {
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
    await loadCsrfToken();
  }

  const headers = { ...(fetchOptions.headers || {}) };
  let requestBody = rawBody;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    requestBody = JSON.stringify(body);
  }
  if (isUnsafeMethod) {
    headers[csrfHeaderName] = csrfToken;
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

  return parseResponse(response);
}

export const api = {
  get: (path, params, options = {}) => request(path, { ...options, method: 'GET', params }),
  post: (path, body, options = {}) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options = {}) => request(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options = {}) => request(path, { ...options, method: 'PATCH', body }),
  delete: (path, options = {}) => request(path, { ...options, method: 'DELETE' }),
  form: (path, fields, options = {}) => request(path, {
    ...options,
    method: 'POST',
    rawBody: new URLSearchParams(fields),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }
  })
};
