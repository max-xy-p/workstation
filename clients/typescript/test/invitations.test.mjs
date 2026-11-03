import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';

test('邀请通过请求体传递凭据，创建保留请求标识，撤销限定工作空间', async () => {
  const calls = [];
  const transport = Object.fromEntries(['get', 'post', 'put', 'patch', 'delete'].map(method =>
    [method, async (...args) => { calls.push({ method, args }); return {}; }]));
  const api = createWorkstationApi(transport).invitationApi;
  const body = { requestId: 'request-id', email: 'g@example.com', role: 'EDITOR' };
  await api.create(9, body);
  await api.list(9);
  await api.revoke(9, 'invite-id');
  await api.preview('private-token');
  await api.accept('private-token');
  assert.deepEqual(calls.map(call => call.args[0]), [
    '/workspaces/9/invitations', '/workspaces/9/invitations',
    '/workspaces/9/invitations/invite-id/revoke', '/invitations/preview', '/invitations/accept']);
  assert.deepEqual(calls[0].args[1], body);
  assert.deepEqual(calls[3].args[1], { token: 'private-token' });
  assert.deepEqual(calls[4].args[1], { token: 'private-token' });
});
