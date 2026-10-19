import { DIFF_TIMEOUT_MS, diffInputReason } from './noteDiffLimits.mjs';
import { createNoteWorkerRunner } from './noteWorker.mjs';

export function createNoteDiffRunner({
  createWorker = () => new Worker(new URL('./noteDiff.worker.mjs', import.meta.url), { type: 'module' }),
  timeoutMs = DIFF_TIMEOUT_MS
} = {}) {
  const runner = createNoteWorkerRunner({ createWorker, timeoutMs,
    validate: ({ before, after }) => diffInputReason(before, after) });
  return { ...runner, run: (before, after) => runner.run({ before, after }) };
}
