// 每个笔记页面独立拥有租约；旧异步响应只能写回创建它的代次。
export function createNotePresenceRuntime({ state, api, openStream, newSession = () => crypto.randomUUID(),
  now = Date.now, setIntervalFn = setInterval, clearIntervalFn = clearInterval,
  setTimeoutFn = setTimeout, clearTimeoutFn = clearTimeout }) {
  let generation = 0, stopped = true, visible = true, focused = true, writable = false;
  let session = null, sequence = 0, stream = null, timer = null, refreshTimer = null;
  let reading = false, writing = false, pendingWrite = false;
  let lastEdit = -Infinity, lastWrite = -Infinity, lastState = null;

  function currentState() {
    return writable && focused && now() - lastEdit < 15000 ? 'EDITING' : 'VIEWING';
  }
  function closeStream() { stream?.close(); stream = null; }
  function unavailable() { state.available = false; state.participants = []; }
  function revoke() {
    stop();
    state.enabled = true;
    unavailable();
  }
  function requestRefresh() {
    if (refreshTimer !== null || stopped || !visible) return;
    refreshTimer = setTimeoutFn(() => { refreshTimer = null; void refresh(); }, 250);
  }
  function open() {
    if (!stream && session && !stopped) {
      const epoch = generation;
      try {
        stream = openStream(() => { if (epoch === generation) requestRefresh(); }, () => {
          // EventSource 自行重连；周期 HTTP 查询同时保持恢复能力。
        });
      } catch { closeStream(); }
    }
  }
  async function write() {
    if (stopped || !session || !visible || !state.enabled) return;
    if (writing) { pendingWrite = true; return; }
    const epoch = generation, lease = session;
    writing = true;
    try {
      do {
        pendingWrite = false;
        const status = currentState();
        const serial = ++sequence;
        await api.update(lease, { state: status, sequence: serial });
        if (epoch !== generation) return;
        lastWrite = now();
        lastState = status;
      } while (pendingWrite);
    } catch (error) {
      if (epoch !== generation) return;
      unavailable();
      if ([401, 403, 404].includes(error.status)) revoke();
    } finally {
      if (epoch === generation) writing = false;
    }
  }
  function join() {
    if (!session && state.enabled && visible && !stopped) {
      session = newSession();
      sequence = 0;
      lastWrite = -Infinity;
      lastState = null;
      open();
      void write();
    }
  }
  function leave() {
    const lease = session;
    const serial = ++sequence;
    session = null;
    closeStream();
    if (lease) void api.update(lease, { state: 'LEFT', sequence: serial }).catch(() => {});
  }
  async function refresh() {
    if (stopped || !visible || reading) return;
    const epoch = generation;
    reading = true;
    try {
      const result = await api.get();
      if (epoch !== generation) return;
      Object.assign(state, result);
      if (!result.available) state.participants = [];
      if (result.enabled) {
        join();
        open();
      } else {
        stop();
      }
    } catch (error) {
      if (epoch !== generation) return;
      unavailable();
      if ([401, 403, 404].includes(error.status)) revoke();
    } finally {
      if (epoch === generation) reading = false;
    }
  }
  function tick() {
    if (stopped || !visible) return;
    void refresh();
    if (session && (now() - lastWrite >= 10000 || currentState() !== lastState)) void write();
  }
  function start({ canWrite = false, active = true, hasFocus = true } = {}) {
    stop();
    stopped = false;
    writable = canWrite;
    visible = active;
    focused = hasFocus;
    timer = setIntervalFn(tick, 5000);
    void refresh();
  }
  function stop() {
    generation++;
    stopped = true;
    leave();
    if (timer !== null) clearIntervalFn(timer);
    if (refreshTimer !== null) clearTimeoutFn(refreshTimer);
    timer = null;
    refreshTimer = null;
    reading = false;
    writing = false;
    pendingWrite = false;
    lastEdit = -Infinity;
    Object.assign(state, { enabled: false, available: false, participants: [] });
  }
  function activity() {
    if (stopped || !visible || !writable || !focused) return;
    lastEdit = now();
    if (lastState !== 'EDITING') void write();
  }
  function visibility(active) {
    if (stopped || visible === active) return;
    visible = active;
    generation++;
    reading = false;
    writing = false;
    pendingWrite = false;
    lastEdit = -Infinity;
    if (!active) {
      leave();
      state.participants = [];
    } else {
      void refresh();
    }
  }
  function focus(active) {
    focused = active;
    if (!stopped && session && currentState() !== lastState) void write();
    if (active) void refresh();
  }
  function permission(canWrite) {
    writable = canWrite;
    if (!canWrite) lastEdit = -Infinity;
    if (session && currentState() !== lastState) void write();
  }
  return { start, stop, activity, visibility, focus, permission, refresh };
}
