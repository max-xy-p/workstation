/** 差异和合并共用可取消、带超时的 Worker 生命周期。 */
export function createNoteWorkerRunner({ createWorker, validate = () => '', timeoutMs = 1000 }) {
  let active = null;
  function finish(job, result) {
    if (active !== job) return;
    active = null;
    clearTimeout(job.timer);
    job.worker.terminate();
    job.resolve(result);
  }
  function cancel() {
    if (active) finish(active, { available: false, reason: '比较已取消。' });
  }
  function run(payload) {
    cancel();
    const reason = validate(payload);
    if (reason) return Promise.resolve({ available: false, reason });
    return new Promise(resolve => {
      let worker;
      try { worker = createWorker(); }
      catch { resolve({ available: false, reason: '当前环境无法自动比较，请手动核对原文。' }); return; }
      const job = { worker, resolve, timer: null };
      active = job;
      job.timer = setTimeout(() => finish(job, { available: false, reason: '比较超过 1 秒，请手动核对原文。' }), timeoutMs);
      worker.onmessage = event => finish(job, event.data);
      worker.onerror = () => finish(job, { available: false, reason: '比较失败，请手动核对原文。' });
      try { worker.postMessage(payload); }
      catch { finish(job, { available: false, reason: '无法启动比较，请手动核对原文。' }); }
    });
  }
  return { run, cancel, dispose: cancel };
}
