import { createNoteWorkerRunner } from './noteWorker.mjs';
import { manualNoteMerge, mergeInputReason } from './noteMerge.mjs';
import { noteSnapshot } from './noteSnapshot.mjs';

export function createNoteMergeRunner({
  createWorker = () => new Worker(new URL('./noteMerge.worker.mjs', import.meta.url), { type: 'module' }),
  timeoutMs = 1000
} = {}) {
  const runner = createNoteWorkerRunner({ createWorker, timeoutMs, validate: mergeInputReason });
  return { ...runner, async run(input) {
    // Vue 响应式代理不能通过 structured clone，跨线程只传递规范快照。
    const payload = { base: input.base ? noteSnapshot(input.base) : null,
      local: noteSnapshot(input.local), remote: noteSnapshot(input.remote) };
    const result = await runner.run(payload);
    return result.available ? result : manualNoteMerge(payload, result.reason);
  } };
}
