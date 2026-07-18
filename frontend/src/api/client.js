const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error('VITE_API_BASE_URL is required');
}

function buildUrl(path, params = {}) {
  // nginx 模式下 API 地址是 /api；直接联调时也兼容完整的 http://host/api。
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

async function request(path, options = {}) {
  const { params, body, ...fetchOptions } = options;
  const response = await fetch(buildUrl(path, params), {
    headers: {
      'Content-Type': 'application/json',
      ...(fetchOptions.headers || {})
    },
    ...fetchOptions,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    // 最常见情况是 /api 没走到后端，而是被前端 dev server 返回了 index.html。
    throw new Error('API 未返回 JSON，请确认当前页面通过 nginx 访问，且 /api/ 已代理到后端');
  }

  const data = await response.json();
  if (!response.ok) {
    const reason = data.message || data.error || response.statusText || '请求失败';
    throw new Error(`${reason} (${response.status})`);
  }
  return data;
}

export const api = {
  get: (path, params) => request(path, { method: 'GET', params }),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: (path) => request(path, { method: 'DELETE' })
};
