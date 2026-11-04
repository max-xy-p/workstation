import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';

test('通知接口保留分页快照和全部已读边界，不提供公开发送接口', async () => {
  const calls = [];
  const transport = Object.fromEntries(['get', 'post', 'put', 'patch', 'delete'].map(method =>
    [method, async (...args) => { calls.push({ method, args }); return {}; }]));
  const api = createWorkstationApi(transport).notificationApi;
  const params = { page: 1, size: 20, unreadOnly: true, upToSequence: 8 };
  await api.state();
  await api.list(params);
  await api.get('id');
  await api.read('id');
  await api.readAll({ throughSequence: 8 });
  assert.deepEqual(calls.map(call => call.args[0]), [
    '/notifications/state', '/notifications', '/notifications/id',
    '/notifications/id/read', '/notifications/read-all']);
  assert.deepEqual(calls[1].args[1], params);
  assert.deepEqual(calls[4].args[1], { throughSequence: 8 });
  assert.equal(api.create, undefined);
});
