const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error('VITE_API_BASE_URL is required');
}

let csrfToken = null;
let csrfHeaderName = 'X-CSRF-TOKEN';
let csrfParameterName = '_csrf';
// 多个并发写请求共用同一次 CSRF 初始化请求，避免重复访问 Gateway。
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
        csrfParameterName = data.parameterName;
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

export async function submitWithCsrf(path) {
  await loadCsrfToken();
  // OIDC 退出需要浏览器跟随页面跳转，因此使用原生表单，而不是普通 fetch。
  const form = document.createElement('form');
  form.method = 'post';
  form.action = buildUrl(path).toString();
  const csrfField = document.createElement('input');
  csrfField.type = 'hidden';
  csrfField.name = csrfParameterName;
  csrfField.value = csrfToken;
  form.appendChild(csrfField);
  document.body.appendChild(form);
  form.submit();
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
    // 所有可能修改数据的请求都先取得 Gateway 签发的 CSRF Token。
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
