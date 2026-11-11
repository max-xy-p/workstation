import test from 'node:test';
import assert from 'node:assert/strict';
import { createNotificationRuntime } from '../src/utils/notificationRuntime.mjs';
import { notificationTarget } from '../src/utils/notificationTarget.mjs';

const tick = () => new Promise(resolve => setImmediate(resolve));
function fixture(api, openStream = () => null) {
  const state = {};
  let interval;
  let timeout;
  const runtime = createNotificationRuntime({ state, api, openStream,
    setIntervalFn: cb => { interval = cb; return 1; }, clearIntervalFn: () => { interval = null; },
    setTimeoutFn: cb => { timeout = cb; return 2; }, clearTimeoutFn: () => { timeout = null; } });
  return { state, runtime, poll: () => interval?.(), flush: () => { const cb = timeout; timeout = null; cb?.(); } };
}
const snapshot = count => ({ enabled: true, unreadCount: count, latestSequence: count, revision: count });

test('通知运行时共用 SSE，合并突发提醒，并在断线时保留 HTTP 补查', async () => {
  let calls = 0;
  let refresh;
  let disconnected;
  let closed = 0;
  const f = fixture({ state: async () => snapshot(++calls) }, (a, b) => {
    refresh = a; disconnected = b; return { close: () => closed++ };
  });
  f.runtime.start('user');
  await tick();
  assert.equal(calls, 1);
  for (let i = 0; i < 100; i++) refresh();
  assert.equal(calls, 1);
  f.flush(); await tick();
  assert.equal(calls, 2);
  disconnected();
  assert.equal(f.state.connection, 'polling');
  f.flush(); await tick();
  assert.equal(calls, 3);
  f.poll(); await tick();
  assert.equal(calls, 4);
  f.runtime.stop();
  assert.equal(closed, 1);
  assert.equal(f.state.unreadCount, 0);
  assert.equal(f.state.connection, 'stopped');
});

test('切换账号或退出后，旧 HTTP 与 SSE 响应不能重新填入旧用户数据', async () => {
  let resolveOld;
  let calls = 0;
  const f = fixture({ state: () => ++calls === 1 ? new Promise(resolve => { resolveOld = resolve; }) : Promise.resolve(snapshot(2)) });
  f.runtime.start('old');
  f.runtime.start('new');
  await tick();
  assert.equal(f.state.unreadCount, 2);
  resolveOld(snapshot(99));
  await tick();
  assert.equal(f.state.unreadCount, 2);
  f.runtime.stop();
  assert.equal(f.state.unreadCount, 0);
});

test('关闭功能时不建立 SSE，临时失败后轮询可恢复', async () => {
  let fail = true;
  let streams = 0;
  const f = fixture({ state: async () => {
    if (fail) throw new Error('暂时离线');
    return { enabled: false, unreadCount: 0, latestSequence: 0, revision: 0 };
  } }, () => { streams++; return null; });
  f.runtime.start('user'); await tick();
  assert.equal(f.state.error, '暂时离线');
  fail = false;
  f.poll(); await tick();
  assert.equal(f.state.enabled, false);
  assert.equal(f.state.error, '');
  assert.equal(streams, 0);
  f.runtime.stop();
});

test('已读后的刷新不会被正在进行的旧状态请求吞掉', async () => {
  let resolve;
  let calls = 0;
  const f = fixture({ state: () => ++calls === 1 ? new Promise(done => { resolve = done; }) : Promise.resolve(snapshot(0)) });
  f.runtime.start('user');
  const waiting = f.runtime.refresh();
  resolve(snapshot(9));
  await waiting;
  assert.equal(calls, 2);
  assert.equal(f.state.unreadCount, 0);
  f.runtime.stop();
});

test('通知跳转只接受已知站内资源，拒绝脚本与不合法标识', () => {
  assert.deepEqual(notificationTarget({ workspaceId: 9, resourceType: 'WORKSPACE' }), { name: 'dashboard', params: { workspaceId: 9 } });
  assert.deepEqual(notificationTarget({ workspaceId: 9, resourceType: 'PROJECT', resourceId: 3 }), { name: 'project-detail', params: { workspaceId: 9, id: 3 } });
  assert.equal(notificationTarget({ workspaceId: 9, resourceType: 'NOTE', resourceId: 3 }).name, 'note-edit');
  assert.equal(notificationTarget({ workspaceId: 9, resourceType: 'TASK', resourceId: 3 }).name, 'tasks');
  assert.equal(notificationTarget({ workspaceId: 9, resourceType: 'javascript:alert(1)', resourceId: 3 }), null);
  assert.equal(notificationTarget({ workspaceId: 9, resourceType: 'NOTE', resourceId: -1 }), null);
});


test('SSE 建立失败保留轮询，并在后续刷新时重新建立连接', async () => {
  let attempts = 0;
  const f = fixture({ state: async () => snapshot(1) }, () => {
    if (++attempts === 1) throw new Error('模拟连接失败');
    return { close() {} };
  });
  f.runtime.start('user');
  await tick();
  assert.equal(f.state.connection, 'polling');
  assert.equal(f.state.unreadCount, 1);
  await f.runtime.refresh();
  assert.equal(attempts, 2);
  f.runtime.stop();
});

test('切换账号后旧 SSE 回调不能改变新连接状态或触发刷新', async () => {
  const callbacks = [];
  let calls = 0;
  const f = fixture({ state: async () => snapshot(++calls) }, (refresh, disconnected) => {
    callbacks.push({ refresh, disconnected });
    return { close() {} };
  });
  f.runtime.start('old');
  await tick();
  f.runtime.start('new');
  await tick();
  callbacks[1].refresh();
  f.flush();
  await tick();
  const previousCalls = calls;
  callbacks[0].disconnected();
  callbacks[0].refresh();
  assert.equal(f.state.connection, 'live');
  f.flush();
  await tick();
  assert.equal(calls, previousCalls);
  f.runtime.stop();
});
