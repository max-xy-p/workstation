<template>
  <AppDrawer :model-value="modelValue" title="处理笔记冲突" description="合并双方修改，核对结果后保存为待确认草稿。" panel-class="note-merge-drawer" :busy="busy" :dirty="dirty" @update:model-value="emit('update:modelValue', $event)">
    <p v-if="error || applyError" class="error" role="alert">{{ error || applyError }}</p>
    <output v-if="loading" class="merge-loading">正在读取最新版本并比较三份内容…</output>
    <template v-else-if="merge && remote">
      <div class="merge-overview"><p>编辑基准 {{ baseVersion ? 'v' + baseVersion : '未知' }}<span> / </span>服务端 v{{ remote.version }}</p><strong>{{ remaining ? '还有 ' + remaining + ' 处冲突' : '全部冲突已处理' }}</strong></div>
      <output v-if="merge.reason" class="merge-notice">{{ merge.reason }}</output>
      <p class="merge-explanation">{{ merge.reason ? '请对照双方原文选择或手动编辑正文。' : '不同位置的正文修改已自动合并，重叠修改需逐项选择。' }}标题和关联属性也会检查，标签按整个集合选择。</p>
      <template v-if="!preview">
        <div v-for="item in merge.fields" :key="item.field">
          <NoteMergeChoice v-if="item.kind === 'conflict'" :item="item" :choice="choices[item.field]" :choice-key="item.field" :label="fieldLabel(item.field) + '冲突'" :allow-manual="item.field === 'title'" :format="value => display(item.field, value)" :disabled="busy" @choose="choose(item.field, $event)" />
          <p v-else class="merge-field-result"><strong>{{ fieldLabel(item.field) }}</strong><span>{{ display(item.field, item.value) }}</span><small>已自动处理</small></p>
        </div>
        <h3 class="merge-section-title">正文合并</h3>
        <template v-for="(item, index) in merge.content" :key="item.id || index">
          <NoteMergeChoice v-if="item.kind === 'conflict'" :item="item" :choice="choices[item.id]" :choice-key="item.id" :label="'正文冲突 · 基准第 ' + item.startLine + '—' + item.endLine + ' 行'" multiline allow-manual :disabled="busy" @choose="choose(item.id, $event)" />
          <details v-else class="merge-context"><summary>已合并的正文片段</summary><NoteRevisionText :content="item.text" label="合并片段" /></details>
        </template>
      </template>
      <section v-else class="merge-preview">
        <h3>核对合并结果</h3><p>应用后不会立即写入服务端，也不会自动保存。返回编辑器后需点击“确认保存合并结果”。</p>
        <label>合并标题<input v-model="preview.title" maxlength="200" :disabled="busy" /></label>
        <label>合并正文（Markdown）<textarea v-model="preview.content" rows="16" :disabled="busy" /></label>
        <dl><template v-for="field in ['folderId', 'projectId', 'tagIds']" :key="field"><dt>{{ fieldLabel(field) }}</dt><dd>{{ display(field, preview[field]) }}</dd></template></dl>
      </section>
    </template>
    <template #footer="{ close }">
      <button type="button" class="secondary" :disabled="busy || loading" @click="refresh">重新读取最新版本</button>
      <button type="button" class="secondary" :disabled="busy" @click="close">返回编辑</button>
      <button v-if="preview" type="button" class="secondary" :disabled="busy" @click="preview = null">返回冲突选择</button>
      <button v-if="merge && !preview" type="button" :disabled="busy || loading || remaining > 0" @click="buildPreview">预览合并结果</button>
      <button v-if="preview" type="button" :disabled="busy || !validPreview" @click="emit('apply', { result: preview, remote })">{{ busy ? '正在保留草稿…' : '应用为待确认草稿' }}</button>
    </template>
  </AppDrawer>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import AppDrawer from './AppDrawer.vue';
import NoteMergeChoice from './NoteMergeChoice.vue';
import NoteRevisionText from './NoteRevisionText.vue';
import { noteApi } from '../api/workstation';
import { noteSnapshot } from '../utils/noteSnapshot.mjs';
import { createNoteMergeRunner } from '../utils/noteMergeClient.mjs';
import { resolveNoteMerge } from '../utils/noteMerge.mjs';

const props = defineProps({ modelValue: Boolean, workspaceId: { type: Number, required: true }, noteId: { type: Number, required: true },
  base: Object, baseVersion: Number, local: { type: Object, required: true }, folders: { type: Array, required: true }, projects: { type: Array, required: true },
  tags: { type: Array, required: true }, busy: Boolean, applyError: { type: String, default: '' } });
const emit = defineEmits(['update:modelValue', 'apply']);
const merge = ref(null), remote = ref(null), preview = ref(null), loading = ref(false), error = ref('');
const choices = reactive({});
const runner = createNoteMergeRunner();
let disposed = false, request = 0;
const dirty = computed(() => Object.keys(choices).length > 0 || Boolean(preview.value));
const conflicts = computed(() => merge.value ? [...merge.value.fields, ...merge.value.content].filter(item => item.kind === 'conflict') : []);
const remaining = computed(() => conflicts.value.filter(item => !choices[item.field || item.id]).length);
const validPreview = computed(() => preview.value?.title.trim().length > 0 && preview.value?.content.trim().length > 0);
const fieldLabel = field => ({ title: '标题', folderId: '文件夹', projectId: '项目', tagIds: '标签' }[field]);
function display(field, value) {
  if (field === 'title') return value;
  if (field === 'tagIds') return value.length ? value.map(id => props.tags.find(item => item.id === id)?.name || '标签 #' + id).join('、') : '无标签';
  if (!value) return field === 'folderId' ? '未归档' : '未关联';
  const items = field === 'folderId' ? props.folders : props.projects;
  return items.find(item => item.id === value)?.name || fieldLabel(field) + ' #' + value;
}
function choose(key, value) { choices[key] = value; }
function buildPreview() {
  try { preview.value = resolveNoteMerge(merge.value, choices); }
  catch (err) { error.value = err.message; }
}
async function refresh() {
  if (dirty.value && !window.confirm('重新读取会清除本面板中的选择与预览，原编辑内容仍保留。继续？')) return;
  const current = ++request;
  runner.cancel(); loading.value = true; error.value = ''; merge.value = null; preview.value = null;
  for (const key of Object.keys(choices)) delete choices[key];
  try {
    const note = await noteApi.get(props.workspaceId, props.noteId);
    if (disposed || current !== request) return;
    if (note.id !== props.noteId || !Number.isSafeInteger(note.version) || note.version < 1
        || (props.baseVersion && note.version < props.baseVersion)) throw new Error('服务端笔记版本无效，请保留本地内容后重试。');
    const result = await runner.run({ base: props.base || null, local: props.local, remote: noteSnapshot(note) });
    if (disposed || current !== request) return;
    remote.value = note; merge.value = result;
  } catch (err) { if (!disposed && current === request) error.value = '无法准备合并：' + err.message; }
  finally { if (!disposed && current === request) loading.value = false; }
}
onMounted(() => { void refresh(); });
onBeforeUnmount(() => { disposed = true; ++request; runner.dispose(); });
</script>

<style>
.app-drawer.note-merge-drawer { width: min(1120px, 100vw); }
.note-merge-drawer .drawer-body { min-width: 0; }
.merge-loading { display: block; padding: 24px 0; color: var(--ink-soft); }
.merge-overview { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; font-size: 13px; }
.merge-overview p { color: var(--ink-soft); }
.merge-overview strong { color: var(--moss-dark); }
.merge-explanation, .merge-preview > p { color: var(--ink-soft); font-size: 13px; line-height: 1.8; margin-bottom: 24px; }
.merge-notice { display: block; padding: 14px; background: #fff8e9; color: #78551f; border-radius: 6px; font-size: 13px; line-height: 1.8; }
.merge-field-result { display: flex; flex-wrap: wrap; align-items: baseline; gap: 12px; padding-block: 12px; margin: 0; border-bottom: 1px solid var(--line); font-size: 13px; }
.merge-field-result span { flex: 1; overflow-wrap: anywhere; min-width: 0; }
.merge-field-result small { color: var(--ink-soft); }
.merge-section-title { font-size: 16px; margin: 24px 0 16px; }
.merge-context { margin: 14px 0; font-size: 13px; }
.merge-context summary { color: var(--ink-soft); cursor: pointer; padding-block: 10px; }
.merge-preview label { display: grid; gap: 10px; margin-block: 20px; font-size: 13px; }
.merge-preview textarea { font: 13px/1.8 Consolas, "Microsoft YaHei UI", monospace; }
.merge-preview dl { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px 20px; font-size: 13px; }
.merge-preview dt { color: var(--ink-soft); }
.merge-preview dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 680px) { .note-merge-drawer .drawer-footer { gap: 8px; } .note-merge-drawer .drawer-footer button { padding-inline: 10px; font-size: 12px; } }
</style>
