import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { compileScript, parse } from '@vue/compiler-sfc';
import ts from 'typescript';
import { reactive } from 'vue';

const require = createRequire(import.meta.url);
const source = await readFile(new URL('../src/components/IntegrationConnectionFields.vue', import.meta.url), 'utf8');
const { descriptor } = parse(source);
const script = compileScript(descriptor, { id: 'connection-fields-test' });
const { outputText } = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
});
const directory = await mkdtemp(join(tmpdir(), 'workstation-connection-fields-'));
const modulePath = join(directory, 'component.mjs');
const vueUrl = pathToFileURL(require.resolve('vue')).href;
let component;
try {
  await writeFile(modulePath, outputText.replaceAll("from 'vue'", `from '${vueUrl}'`));
  ({ default: component } = await import(pathToFileURL(modulePath).href));
} finally {
  await unlink(modulePath);
  await rmdir(directory);
}

function form() {
  return { name: '连接', provider: 'GITHUB', baseUrl: 'https://github.com', token: '', scope: 'OWNED',
    includeForks: false, includeArchived: false, autoImport: false, scheduleMinutes: 0,
    expectedVersion: null, sharingConfirmed: false };
}

test('连接字段通过事件提交修改，保持父组件传入的对象不变', () => {
  const original = Object.freeze(form());
  const events = [];
  const fields = component.setup({ form: original, editing: false, shared: true }, {
    emit: (...args) => events.push(args), expose() {}
  });
  for (const [key, value] of Object.entries({ name: '团队', provider: 'GITLAB', baseUrl: 'https://gitlab.com',
    token: '示例凭证', scope: 'ACCESSIBLE', includeForks: true, includeArchived: true, autoImport: true,
    scheduleMinutes: 60, sharingConfirmed: true })) {
    fields[key].value = value;
    assert.deepEqual(events.at(-1), ['update:form', { ...original, [key]: value }]);
  }
  assert.deepEqual(original, form());
});

test('父组件接收修改后，后续编辑基于最新值并跟随重置', () => {
  const props = reactive({ form: form(), editing: false, shared: true });
  const fields = component.setup(props, {
    emit: (event, value) => { assert.equal(event, 'update:form'); props.form = value; }, expose() {}
  });
  fields.name.value = '新名称';
  fields.includeForks.value = true;
  fields.scheduleMinutes.value = 60;
  assert.equal(props.form.name, '新名称');
  assert.equal(props.form.includeForks, true);
  assert.equal(props.form.scheduleMinutes, 60);
  props.form = form();
  assert.equal(fields.name.value, '连接');
  assert.equal(fields.includeForks.value, false);
  assert.equal(fields.scheduleMinutes.value, 0);
});
