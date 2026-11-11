// 所有组件共用一个运行时；SSE 是刷新提示，HTTP 状态与列表负责恢复。
export function createNotificationRuntime({ state, api, openStream, setIntervalFn = setInterval,
  clearIntervalFn = clearInterval, setTimeoutFn = setTimeout, clearTimeoutFn = clearTimeout }) {
  let generation = 0;
  let subject = null;
  let stream = null;
  let timer = null;
  let running = null;
  let pending = false;
  let scheduledRefresh = null;

  function scheduleRefresh() {
    if (scheduledRefresh !== null) return;
    scheduledRefresh = setTimeoutFn(() => { scheduledRefresh = null; void refresh(); }, 500);
  }

  function closeStream() {
    stream?.close();
    stream = null;
  }
  function reset() {
    Object.assign(state, { enabled: false, unreadCount: 0, latestSequence: 0, revision: 0,
      refreshVersion: 0, loading: false, error: '', connection: 'stopped' });
  }
  function stop() {
    generation++;
    subject = null;
    pending = false;
    running = null;
    closeStream();
    if (timer !== null) clearIntervalFn(timer);
    timer = null;
    if (scheduledRefresh !== null) clearTimeoutFn(scheduledRefresh);
    scheduledRefresh = null;
    reset();
  }
  function updateStream(enabled, current) {
    if (!enabled) {
      closeStream();
      state.connection = 'stopped';
      return;
    }
    if (stream) return;
    state.connection = 'polling';
    try {
      stream = openStream(() => {
        if (current === generation) { state.connection = 'live'; scheduleRefresh(); }
      }, () => {
        if (current === generation) { state.connection = 'polling'; scheduleRefresh(); }
      });
    } catch {
      state.connection = 'polling';
    }
  }
  async function refresh() {
    if (!subject) return;
    if (running) { pending = true; return running; }
    const current = generation;
    const task = (async () => {
      do {
        pending = false;
        state.loading = true;
        try {
          const result = await api.state();
          if (current !== generation) return;
          Object.assign(state, result, { error: '', refreshVersion: state.refreshVersion + 1 });
          updateStream(result.enabled, current);
        } catch (error) {
          if (current === generation) {
            state.error = error.message || '通知暂不可用，请稍后刷新。';
            state.connection = 'polling';
          }
        } finally {
          if (current === generation) state.loading = false;
        }
      } while (pending && current === generation);
    })();
    running = task;
    try { await task; }
    finally { if (current === generation) running = null; }
  }
  function start(nextSubject) {
    if (nextSubject === subject) return;
    stop();
    if (!nextSubject) return;
    subject = nextSubject;
    timer = setIntervalFn(() => { void refresh(); }, 30000);
    void refresh();
  }
  reset();
  return { start, stop, refresh };
}
