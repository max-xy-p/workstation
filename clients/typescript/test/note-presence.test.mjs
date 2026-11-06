import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';

test('笔记在线状态调用始终包含空间、笔记及页面标识', async () => {
  const calls = [];
  const api = createWorkstationApi({
    get: async (...args) => { calls.push(['get', ...args]); return { enabled: true, available: true, participants: [] }; },
    put: async (...args) => { calls.push(['put', ...args]); return null; }
  });
  await api.notePresenceApi.get(7, 8);
  await api.notePresenceApi.update(7, 8, 'page/id', { state: 'LEFT', sequence: 3 });
  assert.equal(calls[0][1], '/workspaces/7/notes/8/presence');
  assert.equal(calls[1][1], '/workspaces/7/notes/8/presence/sessions/page%2Fid');
  assert.deepEqual(calls[1][2], { state: 'LEFT', sequence: 3 });
});
