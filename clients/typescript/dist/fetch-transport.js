export class WorkstationHttpError extends Error {
    status;
    response;
    constructor(message, status, response) {
        super(message);
        this.name = 'WorkstationHttpError';
        this.status = status;
        this.response = response;
    }
}
function buildUrl(baseUrl, path, params = {}) {
    const normalizedBaseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
    const url = new URL(path.replace(/^\//, ''), normalizedBaseUrl);
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            url.searchParams.set(key, String(value));
        }
    });
    return url;
}
function isApiError(value) {
    return typeof value === 'object' && value !== null;
}
export function createFetchTransport(options) {
    const fetchImplementation = options.fetch ?? globalThis.fetch;
    if (!fetchImplementation) {
        throw new Error('A Fetch implementation is required');
    }
    async function request(method, path, params, body) {
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
            return null;
        }
        const contentType = response.headers.get('content-type') ?? '';
        const data = contentType.includes('application/json')
            ? await response.json()
            : undefined;
        if (!response.ok) {
            const error = isApiError(data) ? data : undefined;
            const message = error?.message ?? response.statusText ?? `HTTP ${response.status}`;
            throw new WorkstationHttpError(message, response.status, error);
        }
        return data;
    }
    return {
        get: (path, params) => request('GET', path, params),
        post: (path, body) => request('POST', path, undefined, body),
        put: (path, body) => request('PUT', path, undefined, body),
        patch: (path, body) => request('PATCH', path, undefined, body),
        delete: (path) => request('DELETE', path)
    };
}
