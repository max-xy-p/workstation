import test from 'node:test';
import assert from 'node:assert/strict';
import { createNotePresenceRuntime } from '../src/utils/notePresenceRuntime.mjs';

const flush = async () => { for (let n = 0; n < 8; n++) await Promise.resolve(); };
function fixture(overrides = {}) {
  let time = 100000, tick, next = 0;
  const calls = [], streams = [], state = {};
  const api = { get: async () => ({ enabled: true, available: true, participants: [] }),
    update: async (...args) => { calls.push(args); }, ...overrides };
  const runtime = createNotePresenceRuntime({ state, api, now: () => time, newSession: () => 'tab-' + ++next,
    setIntervalFn: fn => { tick = fn; return 1; }, clearIntervalFn: () => { tick = null; },
    openStream: (refresh, disconnected) => { const s = { refresh, disconnected, closed: false, close() { this.closed = true; } }; streams.push(s); return s; } });
  return { runtime, state, calls, streams, async advance(ms) { time += ms; tick?.(); await flush(); } };
}
test('只有近期输入才广播编辑，空闲、失焦和权限降低恢复查看', async () => {
  const f = fixture();
  f.runtime.start({ canWrite: true }); await flush();
  assert.equal(f.calls[0][1].state, 'VIEWING');
  f.runtime.activity(); await flush();
  assert.equal(f.calls.at(-1)[1].state, 'EDITING');
  await f.advance(16000);
  assert.equal(f.calls.at(-1)[1].state, 'VIEWING');
  f.runtime.activity(); await flush();
  f.runtime.focus(false); await flush();
  assert.equal(f.calls.at(-1)[1].state, 'VIEWING');
  f.runtime.focus(true); f.runtime.activity(); await flush();
  f.runtime.permission(false); await flush();
  assert.equal(f.calls.at(-1)[1].state, 'VIEWING');
  f.runtime.stop();
});
test('隐藏、恢复和离开使用独立租约且不会复活旧标签页', async () => {
  const f = fixture();
  f.runtime.start(); await flush();
  f.runtime.visibility(false); await flush();
  assert.equal(f.calls.at(-1)[1].state, 'LEFT');
  assert.equal(f.streams[0].closed, true);
  f.runtime.visibility(true); await flush();
  assert.equal(f.calls.at(-1)[0], 'tab-2');
  f.runtime.stop(); await flush();
  assert.equal(f.calls.at(-1)[1].state, 'LEFT');
  const count = f.calls.length;
  await f.advance(20000);
  assert.equal(f.calls.length, count);
});
test('迟到请求和旧页面查询不能覆盖离开或新页面状态', async () => {
  let resolve;
  const f = fixture({ update: () => new Promise(r => { resolve = r; }) });
  f.runtime.start({ canWrite: true }); await flush();
  f.runtime.stop(); resolve(); await flush();
  assert.equal(f.state.enabled, false);
  assert.deepEqual(f.state.participants, []);
  let finish;
  const g = fixture({ get: () => new Promise(r => { finish = r; }) });
  g.runtime.start(); await flush(); g.runtime.stop();
  finish({ enabled: true, available: true, participants: [{ subjectId: 'old' }] }); await flush();
  assert.equal(g.state.enabled, false);
  assert.deepEqual(g.state.participants, []);
});
test('存储故障清空旧快照，HTTP 周期补查能恢复；鉴权失败终止连接', async () => {
  let mode = 'ok';
  const f = fixture({ get: async () => {
    if (mode === 'error') throw Object.assign(new Error(), { status: 503 });
    if (mode === 'denied') throw Object.assign(new Error(), { status: 403 });
    return { enabled: true, available: true, participants: [{ subjectId: 'other' }] };
  } });
  f.runtime.start(); await flush();
  mode = 'error'; await f.advance(5000);
  assert.equal(f.state.available, false);
  assert.deepEqual(f.state.participants, []);
  mode = 'ok'; await f.advance(5000);
  assert.equal(f.state.participants.length, 1);
  mode = 'denied'; await f.advance(5000);
  assert.equal(f.streams[0].closed, true);
  const count = f.calls.length; await f.advance(10000);
  assert.equal(f.calls.length, count);
});
test('只读用户输入不会发送编辑，功能关闭时不创建连接或租约', async () => {
  const f = fixture();
  f.runtime.start({ canWrite: false }); await flush();
  f.runtime.activity(); await flush();
  assert.equal(f.calls.length, 1);
  assert.equal(f.calls[0][1].state, 'VIEWING');
  f.runtime.stop();
  const g = fixture({ get: async () => ({ enabled: false, available: false, participants: [] }) });
  g.runtime.start(); await flush();
  assert.equal(g.calls.length, 0);
  assert.equal(g.streams.length, 0);
  g.runtime.stop();
});
