import assert from 'node:assert/strict';
import test from 'node:test';
import { createFetchTransport, createWorkstationApi } from '../dist/index.js';

test('API wrapper uses typed endpoint paths and aggregates pages', async () => {
  const calls = [];
  const transport = {
    async get(path, params) {
      calls.push({ method: 'GET', path, params });
      return params.page === 0
        ? { items: [{ id: 1 }], hasMore: true }
        : { items: [{ id: 2 }], hasMore: false };
    },
    async post() {},
    async put() {},
    async patch() {},
    async delete() {}
  };

  const api = createWorkstationApi(transport);
  const projects = await api.projectApi.listAll(17, { status: 'ACTIVE' });

  assert.deepEqual(projects, [{ id: 1 }, { id: 2 }]);
  assert.deepEqual(calls, [
    { method: 'GET', path: '/workspaces/17/projects', params: { status: 'ACTIVE', page: 0, size: 100 } },
    { method: 'GET', path: '/workspaces/17/projects', params: { status: 'ACTIVE', page: 1, size: 100 } }
  ]);
});

test('Fetch transport sends an injected Bearer token through the API base path', async () => {
  let capturedUrl;
  let capturedInit;
  const transport = createFetchTransport({
    baseUrl: 'https://workstation.example/api/',
    token: async () => 'machine-token',
    fetch: async (url, init) => {
      capturedUrl = url;
      capturedInit = init;
      return new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasMore: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }
  });

  const api = createWorkstationApi(transport);
  await api.taskApi.list(17, { status: 'TODO' });

  assert.equal(capturedUrl.toString(), 'https://workstation.example/api/workspaces/17/tasks?status=TODO');
  assert.equal(new Headers(capturedInit.headers).get('Authorization'), 'Bearer machine-token');
});

test('分页在响应确认存在下一页后才发起请求，并在失败时停止', async () => {
  const calls = [];
  let resolveFirst;
  const transport = {
    get(path, params) {
      calls.push(params.page);
      if (params.page === 0) return new Promise(resolve => { resolveFirst = resolve; });
      return Promise.reject(new Error('第二页读取失败'));
    }
  };
  const pending = createWorkstationApi(transport).taskApi.listAll(17);
  assert.deepEqual(calls, [0]);
  resolveFirst({ items: [{ id: 1 }], hasMore: true });
  await assert.rejects(pending, { message: '第二页读取失败' });
  assert.deepEqual(calls, [0, 1]);
});
