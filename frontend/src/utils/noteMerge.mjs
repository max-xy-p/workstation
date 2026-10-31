import { diffArrays } from 'diff';
import { diffInputReason } from './noteDiffLimits.mjs';

const FIELDS = ['title', 'folderId', 'projectId', 'tagIds'];
const equal = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const lines = text => text ? text.split(/(?<=\n)/) : [];

function mergeValue(base, local, remote, hasBase) {
  if (equal(local, remote)) return { kind: 'resolved', value: local };
  if (hasBase && equal(local, base)) return { kind: 'resolved', value: remote };
  if (hasBase && equal(remote, base)) return { kind: 'resolved', value: local };
  return { kind: 'conflict', hasBase, base: hasBase ? base : null, local, remote };
}

export function mergeInputReason({ base, local, remote }) {
  return diffInputReason(local.content, remote.content) || (base ? diffInputReason(base.content, '') : '缺少可信的编辑基准，正文需手动核对。');
}

function edits(base, changed, side) {
  const parts = diffArrays(base, changed, { timeout: 350, maxEditLength: 10000 });
  if (!parts) throw new Error('比较复杂');
  let position = 0, edit = null;
  const result = [];
  const flush = () => {
    if (edit) result.push(edit);
    edit = null;
  };
  for (const part of parts) {
    if (!part.added && !part.removed) { flush(); position += part.value.length; continue; }
    edit ||= { start: position, end: position, lines: [], side };
    if (part.removed) { position += part.value.length; edit.end = position; }
    else edit.lines.push(...part.value);
  }
  flush();
  if (result.length > 2000) throw new Error('变更过多');
  return result;
}

// 插入落在另一处修改的边界时采取保守策略，交由用户决定插入顺序。
function overlaps(a, b) {
  if (a.start === a.end) return a.start >= b.start && a.start <= b.end;
  if (b.start === b.end) return b.start >= a.start && b.start <= a.end;
  return a.start < b.end && b.start < a.end;
}

function applyEdits(base, start, end, changes) {
  const result = [];
  let cursor = start;
  for (const edit of changes) {
    result.push(...base.slice(cursor, edit.start), ...edit.lines);
    cursor = edit.end;
  }
  result.push(...base.slice(cursor, end));
  return result.join('');
}

function mergeContent(base, local, remote) {
  const simple = mergeValue(base, local, remote, true);
  if (simple.kind === 'resolved') return [{ kind: 'resolved', text: simple.value }];
  const original = lines(base);
  const changes = [...edits(original, lines(local), 'local'), ...edits(original, lines(remote), 'remote')]
    .sort((a, b) => a.start - b.start || a.end - b.end);
  const result = [];
  let cursor = 0, index = 0;
  while (index < changes.length) {
    const group = [changes[index++]];
    while (index < changes.length && group.some(edit => overlaps(edit, changes[index]))) group.push(changes[index++]);
    const start = group[0].start, end = Math.max(...group.map(edit => edit.end));
    if (start > cursor) result.push({ kind: 'resolved', text: original.slice(cursor, start).join('') });
    const before = original.slice(start, end).join('');
    const left = applyEdits(original, start, end, group.filter(edit => edit.side === 'local'));
    const right = applyEdits(original, start, end, group.filter(edit => edit.side === 'remote'));
    const merged = mergeValue(before, left, right, true);
    result.push(merged.kind === 'resolved' ? { kind: 'resolved', text: merged.value }
      : { ...merged, id: 'content-' + result.length, startLine: start + 1, endLine: Math.max(start + 1, end) });
    cursor = end;
  }
  if (cursor < original.length) result.push({ kind: 'resolved', text: original.slice(cursor).join('') });
  return result;
}

/** 标量字段和关联集合按三方值比较，标签作为一个集合整体选择。 */
export function manualNoteMerge({ base, local, remote }, reason = '') {
  const fields = FIELDS.map(field => ({ field, ...mergeValue(base?.[field], local[field], remote[field], Boolean(base)) }));
  const content = mergeValue(base?.content, local.content, remote.content, Boolean(base));
  return { available: true, reason, fields, content: [content.kind === 'resolved'
    ? { kind: 'resolved', text: content.value }
    : { ...content, id: 'content-0', startLine: 1, endLine: Math.max(1, lines(base?.content || '').length) }] };
}

export function mergeNote(input) {
  const reason = mergeInputReason(input);
  const result = manualNoteMerge(input, reason);
  if (reason) return result;
  try { result.content = mergeContent(input.base.content, input.local.content, input.remote.content); }
  catch { result.reason = '正文变更较复杂，请手动核对并选择内容。'; }
  return result;
}

/** 未完成的冲突不能被空字符串或默认选项悄然消除。 */
export function resolveNoteMerge(merge, choices) {
  function resolve(item, key) {
    if (item.kind === 'resolved') return Object.hasOwn(item, 'value') ? item.value : item.text;
    const choice = choices[key];
    if (!choice || !['local', 'remote', 'manual'].includes(choice.side)) throw new Error('请先处理全部冲突');
    if (choice.side === 'manual') {
      if (typeof choice.value !== 'string' || !['title', undefined].includes(item.field)) throw new Error('手动内容无效');
      return choice.value;
    }
    return item[choice.side];
  }
  const result = Object.fromEntries(merge.fields.map(item => [item.field, resolve(item, item.field)]));
  result.content = merge.content.map(item => resolve(item, item.id)).join('');
  return result;
}
