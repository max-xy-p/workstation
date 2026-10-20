import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';
test('Issue 关联接口传递共享确认并通过独立接口保留旧任务请求语义', async () => {
  const calls = [];
  const transport = Object.fromEntries(['get', 'post', 'put', 'patch', 'delete'].map(method => [method, async (...args) => { calls.push({ method, args }); return { items: [], hasMore: false }; }]));
  const api = createWorkstationApi(transport).issueApi;
  const body = { developmentItemId: 1, sharingConfirmed: true, taskId: 5 };
  await api.link(9, 20, body);
  await api.create(9, 20, { developmentItemId: 1, sharingConfirmed: true, task: { title: '任务' } });
  await api.listAll(9, 20);
  await api.unlink(9, 5);
  assert.deepEqual(calls[0], { method: 'post', args: ['/workspaces/9/projects/20/issue-links', body] });
  assert.equal(calls[1].args[0], '/workspaces/9/projects/20/issue-tasks');
  assert.deepEqual(calls[2].args, ['/workspaces/9/issue-sources', { projectId: 20, page: 0, size: 100 }]);
  assert.deepEqual(calls[3], { method: 'delete', args: ['/workspaces/9/tasks/5/issue-source'] });
});
