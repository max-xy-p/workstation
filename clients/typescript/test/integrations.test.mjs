import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';

test('数据源导入保留 Workspace、幂等键和项目关联选项', async () => {
  const calls = [];
  const transport = Object.fromEntries(['get', 'post', 'put', 'patch', 'delete'].map(method =>
    [method, async (...args) => { calls.push({ method, args }); return { id: 3 }; }]));
  const api = createWorkstationApi(transport).integrationApi;
  const body = { requestKey: 'unique', items: [{ repositoryId: 4, projectId: 20, status: 'ACTIVE' }], sharingConfirmed: true };
  await api.imports(9, 2, body);
  await api.developmentItems(9, 20, 'PIPELINE', 1, 30);
  await api.enabled(9, 2, { enabled: false, expectedVersion: 3, sharingConfirmed: false });
  assert.deepEqual(calls[0], { method: 'post', args: ['/workspaces/9/integrations/2/imports', body] });
  assert.deepEqual(calls[1], { method: 'get', args: ['/workspaces/9/projects/20/development/items', { kind: 'PIPELINE', page: 1, size: 30 }] });
  assert.equal(calls[2].args[1].expectedVersion, 3);
});
