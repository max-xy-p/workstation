import { DIFF_TIMEOUT_MS, diffInputReason } from './noteDiffLimits.mjs';

export function createNoteDiffRunner({
  createWorker = () => new Worker(new URL('./noteDiff.worker.mjs', import.meta.url), { type: 'module' }),
  timeoutMs = DIFF_TIMEOUT_MS
} = {}) {
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
  function run(before, after) {
    cancel();
    const reason = diffInputReason(before, after);
    if (reason) return Promise.resolve({ available: false, reason });
    return new Promise(resolve => {
      let worker;
      try { worker = createWorker(); }
      catch { resolve({ available: false, reason: '当前环境无法计算差异，请切换到预览查看原文。' }); return; }
      const job = { worker, resolve, timer: null };
      active = job;
      job.timer = setTimeout(() => finish(job, { available: false, reason: '比较超过 1 秒，请切换到预览查看原文。' }), timeoutMs);
      worker.onmessage = event => finish(job, event.data);
      worker.onerror = () => finish(job, { available: false, reason: '比较失败，请切换到预览查看原文。' });
      try { worker.postMessage({ before, after }); }
      catch { finish(job, { available: false, reason: '无法启动比较，请切换到预览查看原文。' }); }
    });
  }
  return { run, cancel, dispose: cancel };
}
