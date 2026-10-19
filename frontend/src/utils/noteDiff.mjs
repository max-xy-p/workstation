import { diffLines } from 'diff';
import { diffInputReason } from './noteDiffLimits.mjs';

export function compareNoteText(before, after) {
  const reason = diffInputReason(before, after);
  if (reason) return { available: false, reason };
  const changes = diffLines(before, after, { timeout: 900, maxEditLength: 10_000 });
  if (!changes || changes.length > 2_000) return { available: false, reason: '差异较复杂，请切换到预览分别查看原文。' };
  return { available: true, changes: changes.map(change => {
    let kind = 'same';
    if (change.added) kind = 'added';
    else if (change.removed) kind = 'removed';
    return { kind, value: change.value };
  }) };
}
