import test from 'node:test';
import assert from 'node:assert/strict';
import { reactive } from 'vue';
import { useFormPanel } from '../src/composables/useFormPanel.mjs';

test('表单默认收起，打开后追踪修改并可识别恢复原值', () => {
  const form = reactive({ name: '原名称' });
  const panel = useFormPanel(form);
  assert.equal(panel.opened.value, false);
  panel.openPanel();
  assert.equal(panel.dirty.value, false);
  form.name = '新名称';
  assert.equal(panel.dirty.value, true);
  form.name = '原名称';
  assert.equal(panel.dirty.value, false);
});

test('提交失败保留输入和打开状态，重试成功关闭面板', async () => {
  const form = reactive({ name: '项目' });
  const panel = useFormPanel(form);
  panel.openPanel();
  form.name = '我的修改';
  assert.equal(await panel.submit(async () => { throw new Error('权限不足'); }), false);
  assert.equal(form.name, '我的修改');
  assert.equal(panel.opened.value, true);
  assert.equal(panel.formError.value, '权限不足');
  assert.equal(panel.saving.value, false);
  assert.equal(await panel.submit(async () => {}), true);
  assert.equal(panel.opened.value, false);
  assert.equal(panel.formError.value, '');
});

test('请求进行时拒绝重复提交，结束后解除锁定', async () => {
  const panel = useFormPanel(reactive({ name: '' }));
  panel.openPanel();
  let finish;
  let calls = 0;
  const pending = panel.submit(() => { calls++; return new Promise(resolve => { finish = resolve; }); });
  assert.equal(panel.saving.value, true);
  assert.equal(await panel.submit(async () => { calls++; }), false);
  assert.equal(calls, 1);
  finish();
  assert.equal(await pending, true);
  assert.equal(panel.saving.value, false);
});
