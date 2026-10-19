import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeNote, manualNoteMerge, resolveNoteMerge } from '../src/utils/noteMerge.mjs';
import { createNoteMergeRunner } from '../src/utils/noteMergeClient.mjs';
import { noteSnapshot, parseMergeBase } from '../src/utils/noteSnapshot.mjs';
import { reactive } from 'vue';

const note = content => ({ title: '标题', content, folderId: null, projectId: null, tagIds: [] });
const input = (base, local, remote) => ({ base: note(base), local: note(local), remote: note(remote) });
const contentConflicts = merge => merge.content.filter(item => item.kind === 'conflict');
function resolved(merge, side = 'local') {
  const choices = Object.fromEntries([...merge.fields, ...merge.content].filter(item => item.kind === 'conflict')
    .map(item => [item.field || item.id, { side }]));
  return resolveNoteMerge(merge, choices);
}

test('不同段落、相邻替换及独立插入自动合并，双方顺序互换结果一致', () => {
  for (const [base, local, remote, expected] of [
    ['第一段\n中间\n第三段\n', '本地第一段\n中间\n第三段\n', '第一段\n中间\n远端第三段\n', '本地第一段\n中间\n远端第三段\n'],
    ['a\nb\n', 'A\nb\n', 'a\nB\n', 'A\nB\n'],
    ['a\nb\nc\n', '前言\na\nb\nc\n', 'a\nb\nc\n尾声', '前言\na\nb\nc\n尾声']
  ]) {
    const merge = mergeNote(input(base, local, remote));
    assert.equal(contentConflicts(merge).length, 0);
    assert.equal(resolved(merge).content, expected);
    assert.equal(resolved(mergeNote(input(base, remote, local))).content, expected);
  }
});

test('单方修改、删除、空正文和相同修改原样保留，不重复插入', () => {
  for (const base of ['', 'A', 'A\n', 'A\r\nB\r\n']) {
    for (const changed of ['', 'B', '\n', '😀\n<img src=x>\n']) {
      assert.equal(resolved(mergeNote(input(base, changed, base))).content, changed);
      assert.equal(resolved(mergeNote(input(base, base, changed))).content, changed);
      assert.equal(resolved(mergeNote(input(base, changed, changed))).content, changed);
    }
  }
});

test('同一位置不同修改、同点插入和删除修改冲突，未选择时拒绝生成结果', () => {
  for (const [base, local, remote] of [
    ['a\nb\n', 'a\n本地\n', 'a\n远端\n'],
    ['', '本地', '远端'],
    ['a\nb\nc\n', 'a\nc\n', 'a\nB\nc\n'],
    ['a\nb\n', '插入\na\nb\n', 'A\nb\n']
  ]) {
    const merge = mergeNote(input(base, local, remote));
    assert.ok(contentConflicts(merge).length > 0);
    assert.throws(() => resolveNoteMerge(merge, {}), /全部冲突/);
    assert.equal(resolved(merge, 'local').content, local);
    assert.equal(resolved(merge, 'remote').content, remote);
  }
});

test('多个重叠变更形成一个冲突区域，区域外的双方修改仍保留', () => {
  const base = '头\na\nb\nc\nd\n尾\n';
  const local = '本地头\na\n本地b\n本地c\nd\n尾\n';
  const remote = '头\n远端a\n远端b\nc\nd\n远端尾\n';
  const merge = mergeNote(input(base, local, remote));
  assert.equal(contentConflicts(merge).length, 1);
  assert.equal(resolved(merge, 'local').content, '本地头\na\n本地b\n本地c\nd\n远端尾\n');
  assert.equal(resolved(merge, 'remote').content, '本地头\n远端a\n远端b\nc\nd\n远端尾\n');
});

test('空白、代码缩进、CRLF 和末尾换行不会被合并算法规范化', () => {
  const merge = mergeNote(input('标题\r\n```js\r\n  a();\r\n```\r\n末尾',
    '标题\r\n```js\r\n    a();\r\n```\r\n末尾',
    '标题\r\n```js\r\n  a();\r\n```\r\n末尾\r\n'));
  assert.equal(contentConflicts(merge).length, 0);
  assert.equal(resolved(merge).content, '标题\r\n```js\r\n    a();\r\n```\r\n末尾\r\n');
});

test('标题与关联属性三方比较，双方修改同一标签集合必须明确选择', () => {
  const data = input('正文', '正文', '正文');
  data.local.title = '本地标题'; data.remote.folderId = 4;
  data.local.tagIds = [1]; data.remote.tagIds = [2];
  const merge = mergeNote(data);
  assert.throws(() => resolveNoteMerge(merge, {}), /全部冲突/);
  const result = resolveNoteMerge(merge, { tagIds: { side: 'remote' } });
  assert.equal(result.title, '本地标题'); assert.equal(result.folderId, 4); assert.deepEqual(result.tagIds, [2]);
  assert.equal(result.projectId, null);
  data.remote.title = '远端标题';
  assert.equal(mergeNote(data).fields.find(item => item.field === 'title').kind, 'conflict');
  data.local.projectId = 3; data.remote.projectId = 4;
  const project = mergeNote(data).fields.find(item => item.field === 'projectId');
  assert.equal(project.hasBase, true); assert.equal(project.base, null);
});

test('手动编辑支持删除冲突片段，非法选择不能绕过冲突检查', () => {
  const merge = mergeNote(input('旧\n', '左\n', '右\n'));
  const key = contentConflicts(merge)[0].id;
  assert.equal(resolveNoteMerge(merge, { [key]: { side: 'manual', value: '' } }).content, '');
  assert.throws(() => resolveNoteMerge(merge, { [key]: { side: 'unknown' } }), /全部冲突/);
  assert.throws(() => resolveNoteMerge(merge, { [key]: { side: 'manual', value: null } }), /无效/);
});

test('缺少或损坏基准不猜测共同祖先，双方不同内容必须手动选择', () => {
  for (const value of ['', '{}', 'null', '{"title":"旧","content":"旧"}', JSON.stringify({ ...note('旧'), tagIds: [-1] })])
    assert.equal(parseMergeBase(value), null);
  const base = note('正文');
  assert.deepEqual(parseMergeBase(JSON.stringify(base)), base);
  const merge = mergeNote({ base: null, local: note('本地'), remote: note('最新') });
  assert.ok(merge.reason.includes('基准'));
  assert.equal(contentConflicts(merge).length, 1);
  assert.equal(resolved(merge, 'local').content, '本地');
});

test('超大正文不运行自动合并，原文保留且可选择任一侧', () => {
  const local = '字'.repeat(200001), remote = '服务端';
  const merge = mergeNote(input('基准', local, remote));
  assert.ok(merge.reason); assert.equal(resolved(merge).content, local);
  assert.equal(resolved(merge, 'remote').content, remote);
  assert.ok(mergeNote(input('\n'.repeat(5001), '本地', '远端')).reason);
});

test('规范快照统一标题、关联空值和标签集合，不把展示字段当作合并输入', () => {
  assert.deepEqual(noteSnapshot({ title: ' 标题 ', content: ' 正文\n', version: 5, folderId: '', projectId: '3', tags: [{ id: 2 }, { id: 1 }, { id: 2 }] }),
    { title: '标题', content: ' 正文\n', folderId: null, projectId: 3, tagIds: [1, 2] });
});

test('200 组分离行修改保留双方内容，重复比较不会修改输入', () => {
  for (let run = 0; run < 200; run++) {
    const base = Array.from({ length: 12 }, (_, i) => '原文-' + i + '\n');
    const local = [...base], remote = [...base], expected = [...base];
    const left = run % 5, right = 6 + run % 6;
    local[left] = expected[left] = '本地-' + run + '\n';
    remote[right] = expected[right] = '远端-' + run + '\n';
    const data = input(base.join(''), local.join(''), remote.join('')), original = JSON.stringify(data);
    const merge = mergeNote(data);
    assert.equal(contentConflicts(merge).length, 0);
    assert.equal(resolved(merge).content, expected.join(''));
    assert.equal(JSON.stringify(data), original);
  }
});

test('Worker 超时、异常或不可用时回退手动选择，不丢失任一侧', async () => {
  const data = input('基准', '本地', '远端');
  const unavailable = createNoteMergeRunner({ createWorker() { throw new Error('不可用'); } });
  assert.equal(resolved(await unavailable.run(data)).content, '本地');
  let terminated = false;
  const runner = createNoteMergeRunner({ timeoutMs: 10,
    createWorker: () => ({ postMessage() {}, terminate() { terminated = true; } }) });
  const result = await runner.run(data);
  assert.ok(terminated); assert.equal(resolved(result, 'remote').content, '远端');
});

test('Vue 响应式输入转换为可复制快照再传入 Worker，避免退化为手动冲突', async () => {
  const data = reactive(input('a\nb\n', 'A\nb\n', 'a\nB\n'));
  const runner = createNoteMergeRunner({ createWorker() {
    return { postMessage(payload) { this.onmessage({ data: mergeNote(structuredClone(payload)) }); }, terminate() {} };
  } });
  const result = await runner.run(data);
  assert.equal(result.reason, '');
  assert.equal(resolved(result).content, 'A\nB\n');
});

test('超限输入不创建 Worker，取消旧比较不会影响新结果', async () => {
  const workers = [];
  const runner = createNoteMergeRunner({ createWorker() {
    const worker = { postMessage() {}, terminate() {} }; workers.push(worker); return worker;
  } });
  await runner.run(input('', '字'.repeat(200001), '远端'));
  assert.equal(workers.length, 0);
  const data = input('基准', '本地', '远端'), old = runner.run(data), current = runner.run(data);
  assert.equal(resolved(await old).content, '本地');
  const expected = manualNoteMerge(data, '模拟结果');
  workers[0].onmessage({ data: { available: true, fields: [], content: [] } });
  workers[1].onmessage({ data: expected });
  assert.deepEqual(await current, expected);
  runner.dispose();
});
