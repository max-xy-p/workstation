import test from 'node:test';
import assert from 'node:assert/strict';
import { compareNoteText } from '../src/utils/noteDiff.mjs';
import { createNoteDiffRunner } from '../src/utils/noteDiffClient.mjs';
import { diffInputReason, MAX_DIFF_LENGTH, textWindow } from '../src/utils/noteDiffLimits.mjs';

test('行级差异可以完整还原两侧中文、空白、代码缩进及换行', () => {
  const before = '# 标题\n\n```js\n  old();\n```\n尾行';
  const after = '# 标题\n \n```js\n    next();\n```\n尾行\n';
  const result = compareNoteText(before, after);
  assert.equal(result.available, true);
  assert.equal(result.changes.filter(x => x.kind !== 'added').map(x => x.value).join(''), before);
  assert.equal(result.changes.filter(x => x.kind !== 'removed').map(x => x.value).join(''), after);
  assert.ok(result.changes.some(x => x.kind === 'added'));
  assert.ok(result.changes.some(x => x.kind === 'removed'));
});

test('原文中的 HTML 保持普通字符串，相同内容不产生变更', () => {
  const text = '<script>alert("示例")</script>\n';
  const result = compareNoteText(text, text);
  assert.equal(result.available, true);
  assert.deepEqual(result.changes, [{ kind: 'same', value: text }]);
});

test('超长或过多行的文本直接降级，保存接口容量不受影响', () => {
  assert.ok(diffInputReason('x'.repeat(MAX_DIFF_LENGTH + 1), '正文'));
  assert.ok(diffInputReason('\n'.repeat(5_000), '正文'));
  assert.ok(diffInputReason(null, '正文'));
  assert.equal(diffInputReason('x'.repeat(MAX_DIFF_LENGTH), '正文'), '');
  assert.equal(compareNoteText('x'.repeat(MAX_DIFF_LENGTH + 1), '正文').available, false);
});

test('分段原文预览不切断表情字符，也不丢失分段边界内容', () => {
  const text = '字'.repeat(11_999) + '😀结尾';
  assert.equal(textWindow(text, 0) + textWindow(text, 1), text);
});

function workerFactory() {
  const workers = [];
  return { workers, createWorker() {
    const worker = { terminated: false, postMessage(value) { this.sent = value; }, terminate() { this.terminated = true; } };
    workers.push(worker);
    return worker;
  } };
}

test('切换版本会终止旧计算，旧 Worker 的迟到结果不能覆盖新比较', async () => {
  const factory = workerFactory();
  const runner = createNoteDiffRunner({ createWorker: factory.createWorker });
  const old = runner.run('A', 'B');
  const current = runner.run('B', 'C');
  assert.equal((await old).available, false);
  assert.equal(factory.workers[0].terminated, true);
  factory.workers[0].onmessage({ data: { available: true, changes: [{ value: '错误的旧结果' }] } });
  const expected = { available: true, changes: [{ kind: 'added', value: 'C' }] };
  factory.workers[1].onmessage({ data: expected });
  assert.deepEqual(await current, expected);
  assert.equal(factory.workers[1].terminated, true);
});

test('计算超时终止 Worker，用户可以继续阅读原文', async () => {
  const factory = workerFactory();
  const runner = createNoteDiffRunner({ createWorker: factory.createWorker, timeoutMs: 15 });
  assert.equal((await runner.run('A', 'B')).available, false);
  assert.equal(factory.workers[0].terminated, true);
});

test('Worker 不可用或失败时返回可展示的降级结果', async () => {
  const unavailable = createNoteDiffRunner({ createWorker: () => { throw new Error('模拟不可用'); } });
  assert.equal((await unavailable.run('A', 'B')).available, false);
  const factory = workerFactory();
  const runner = createNoteDiffRunner({ createWorker: factory.createWorker });
  const pending = runner.run('A', 'B');
  factory.workers[0].onerror();
  assert.equal((await pending).available, false);
  assert.equal(factory.workers[0].terminated, true);
});

test('输入超限不启动 Worker，关闭页面会释放进行中的计算', async () => {
  const factory = workerFactory();
  const runner = createNoteDiffRunner({ createWorker: factory.createWorker });
  await runner.run('x'.repeat(MAX_DIFF_LENGTH + 1), 'B');
  assert.equal(factory.workers.length, 0);
  const pending = runner.run('A', 'B');
  runner.dispose();
  assert.equal((await pending).available, false);
  assert.equal(factory.workers[0].terminated, true);
});
