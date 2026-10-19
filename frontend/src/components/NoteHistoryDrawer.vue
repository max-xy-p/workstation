<template>
  <AppDrawer :model-value="modelValue" title="版本历史" description="查看已经保存的标题与正文，或将历史内容恢复为新版本。" panel-class="note-history-drawer" :busy="busy" @update:model-value="emit('update:modelValue', $event)">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="history-topline"><span v-if="server">历史截至笔记 v{{ server.version }}</span><button type="button" class="secondary" :disabled="busy || loading" @click="refresh">刷新历史</button></div>
    <div class="history-tabs" aria-label="历史查看方式">
      <button class="history-list-tab secondary" type="button" :aria-pressed="pane === 'list'" @click="pane = 'list'">历史列表</button>
      <button type="button" class="secondary" :aria-pressed="pane === 'preview'" :disabled="!selected" @click="pane = 'preview'">预览</button>
      <button type="button" class="secondary" :aria-pressed="pane === 'diff'" :disabled="!selected" @click="pane = 'diff'">差异对比</button>
    </div>
    <div class="history-layout" :data-pane="pane">
      <aside class="history-list" aria-label="已保存的内容历史" :aria-busy="loading">
        <output v-if="loading" class="history-state">正在读取历史…</output>
        <p v-else-if="!history.items.length" class="history-state">还没有内容历史。下一次成功保存后会显示在这里。</p>
        <button v-for="item in history.items" :key="item.id" type="button" class="history-entry" :class="{ selected: selected?.id === item.id }" :aria-pressed="selected?.id === item.id" :disabled="busy" @click="select(item)">
          <span class="history-entry-heading"><strong>笔记 v{{ item.noteVersion }}</strong><span>{{ sourceLabel(item.source) }}</span></span>
          <span class="history-entry-title">{{ item.title }}</span>
          <span class="history-entry-author">{{ item.actorDisplayName || (item.source === 'BASELINE' ? '迁移基线' : '未知作者') }}</span>
          <time :datetime="item.createdAt">{{ date(item.createdAt) }}</time>
        </button>
        <nav v-if="history.total > history.size" class="history-pagination" aria-label="历史分页">
          <button type="button" class="secondary" :disabled="busy || loading || history.page === 0" @click="loadPage(history.page - 1)">上一页</button>
          <span>{{ history.page + 1 }} / {{ Math.ceil(history.total / history.size) }}</span>
          <button type="button" class="secondary" :disabled="busy || loading || !history.hasMore" @click="loadPage(history.page + 1)">下一页</button>
        </nav>
      </aside>
      <section class="history-content" aria-label="历史内容" :aria-busy="detailLoading">
        <output v-if="detailLoading" class="history-state">正在打开快照…</output>
        <template v-else-if="selected">
          <header class="history-content-heading"><h3>{{ selected.title }}</h3><p>历史记录 #{{ selected.id }}，对应笔记 v{{ selected.noteVersion }}</p></header>
          <label class="history-compare-target">对比对象<select v-model="target" :disabled="busy"><option value="server">已保存版本 v{{ server?.version }}</option><option value="local">当前编辑内容（含未保存修改）</option></select></label>
          <template v-if="pane === 'preview'">
            <NoteRevisionText :content="selected.content" :label="'历史正文 · 笔记 v' + selected.noteVersion" />
            <details class="history-target-preview"><summary>查看对比对象原文</summary><h4>{{ comparison.title }}</h4><NoteRevisionText :content="comparison.content" :label="comparisonLabel" /></details>
          </template>
          <template v-else>
            <p class="history-comparison-label">笔记 v{{ selected.noteVersion }} → {{ comparisonLabel }}</p>
            <div v-if="selected.title !== comparison.title" class="history-title-diff"><span>标题变化</span><del>{{ selected.title }}</del><ins>{{ comparison.title }}</ins></div>
            <output v-if="diffLoading" class="history-state">正在比较正文…</output>
            <div v-else-if="!diff.available" class="history-fallback"><p>{{ diff.reason }}</p><button type="button" class="secondary" @click="pane = 'preview'">查看原文</button></div>
            <template v-else>
              <p v-if="!hasChanges" class="history-state">两个版本的正文相同。</p>
              <div v-else class="history-diff" aria-label="正文差异，减号表示历史内容被删除，加号表示对比内容新增">
                <div v-for="(change, index) in diff.changes" :key="index" :data-kind="change.kind"><span aria-hidden="true">{{ changeMark(change.kind) }}</span><pre>{{ change.value }}</pre></div>
              </div>
            </template>
          </template>
        </template>
        <p v-else class="history-state">选择一条历史，查看保存时的内容。</p>
      </section>
    </div>
    <template #footer="{ close }">
      <div v-if="confirming" class="history-restore-confirm" role="alert"><strong>恢复笔记 v{{ selected?.noteVersion }} 的标题与正文？</strong><p>{{ dirty ? '当前未保存内容将先保留为本地草稿。' : '' }}恢复后会生成新版本，当前文件夹、项目和标签保持不变。</p></div>
      <p v-if="restoreError" class="error history-restore-error" role="alert">{{ restoreError }}</p>
      <button type="button" class="secondary" :disabled="busy" @click="close">{{ confirming ? '返回编辑' : '关闭' }}</button>
      <button v-if="canRestore && selected && server" type="button" :disabled="busy || detailLoading || loading" @click="requestRestore">{{ restoreLabel }}</button>
    </template>
  </AppDrawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import AppDrawer from './AppDrawer.vue';
import NoteRevisionText from './NoteRevisionText.vue';
import { noteApi } from '../api/workstation';
import type { NoteDetailResponse, NoteRevisionPageResponse, NoteRevisionResponse, NoteRevisionSummaryResponse, NoteRevisionSource } from '../api/workstation';
import { createNoteDiffRunner } from '../utils/noteDiffClient.mjs';

const props = defineProps<{ modelValue: boolean; workspaceId: number; noteId: number;
  localContent: Pick<NoteDetailResponse, 'title' | 'content'>; canRestore: boolean; dirty: boolean; busy: boolean; restoreError: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; restore: [revision: NoteRevisionResponse, expectedVersion: number] }>();
const history = reactive<NoteRevisionPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const server = ref<NoteDetailResponse | null>(null);
const selected = ref<NoteRevisionResponse | null>(null);
const pane = ref('list'), target = ref('server'), error = ref('');
const loading = ref(false), detailLoading = ref(false), confirming = ref(false), diffLoading = ref(false);
const diff = ref<{ available: boolean; reason?: string; changes?: { kind: string; value: string }[] }>({ available: false });
const comparison = computed(() => target.value === 'local' ? props.localContent : server.value || { title: '', content: '' });
const comparisonLabel = computed(() => target.value === 'local' ? '当前编辑内容（含未保存修改）' : '已保存版本 v' + server.value?.version);
const hasChanges = computed(() => diff.value.changes?.some(item => item.kind !== 'same'));
const restoreLabel = computed(() => {
  if (props.busy) return '正在处理…';
  if (!confirming.value) return '恢复此历史';
  return props.dirty ? '保留草稿并恢复' : '确认恢复此版本';
});
const runner = createNoteDiffRunner();
let disposed = false, pageRequest = 0, detailRequest = 0, refreshRequest = 0, comparisonRequest = 0;
const sourceLabel = (source: NoteRevisionSource) => ({ CREATE: '创建', IMPORT: '导入', UPDATE: '编辑', RESTORE: '恢复', BASELINE: '基线' }[source]);
const date = (value: string) => new Date(value).toLocaleString();
function changeMark(kind: string) { if (kind === 'added') return '+'; if (kind === 'removed') return '−'; return ' '; }

async function select(item: NoteRevisionSummaryResponse) {
  const request = ++detailRequest;
  selected.value = null; detailLoading.value = true; confirming.value = false; error.value = '';
  try {
    const result = await noteApi.revision(props.workspaceId, props.noteId, item.id);
    if (disposed || request !== detailRequest) return;
    selected.value = result;
    pane.value = 'diff';
  } catch (err) { if (!disposed && request === detailRequest) error.value = err instanceof Error ? err.message : '读取历史失败，请重试。'; }
  finally { if (!disposed && request === detailRequest) detailLoading.value = false; }
}
async function loadPage(page = 0) {
  if (!server.value) return;
  const request = ++pageRequest;
  ++detailRequest;
  selected.value = null; detailLoading.value = false; pane.value = 'list';
  loading.value = true; error.value = ''; confirming.value = false;
  try {
    const result = await noteApi.revisions(props.workspaceId, props.noteId, { page, size: 20, upToVersion: server.value.version });
    if (!disposed && request === pageRequest) Object.assign(history, result);
  } catch (err) { if (!disposed && request === pageRequest) error.value = err instanceof Error ? err.message : '读取历史失败，请刷新。'; }
  finally { if (!disposed && request === pageRequest) loading.value = false; }
}
async function refresh() {
  const request = ++refreshRequest;
  ++pageRequest; ++detailRequest;
  selected.value = null; confirming.value = false; detailLoading.value = false; loading.value = true; error.value = '';
  try {
    const result = await noteApi.get(props.workspaceId, props.noteId);
    if (disposed || request !== refreshRequest) return;
    server.value = result;
    await loadPage();
  } catch (err) { if (!disposed && request === refreshRequest) error.value = err instanceof Error ? err.message : '读取笔记失败，请刷新。'; }
  finally { if (!disposed && request === refreshRequest) loading.value = false; }
}
async function compare() {
  const request = ++comparisonRequest;
  runner.cancel(); diffLoading.value = false;
  if (!selected.value || pane.value !== 'diff') return;
  diffLoading.value = true;
  const result = await runner.run(selected.value.content, comparison.value.content);
  if (disposed || request !== comparisonRequest) return;
  diff.value = result; diffLoading.value = false;
}
function requestRestore() {
  if (!selected.value || !server.value || props.busy) return;
  if (!confirming.value) { confirming.value = true; return; }
  emit('restore', selected.value, server.value.version);
}
watch(() => [selected.value?.id, comparison.value.content, pane.value], () => { void compare(); });
watch(() => props.restoreError, () => { confirming.value = false; });
onMounted(() => { void refresh(); });
onBeforeUnmount(() => { disposed = true; runner.dispose(); });
</script>

<style>
.app-drawer.note-history-drawer { width: min(1120px, 100vw); }
.note-history-drawer .drawer-body { padding: 0; }
.note-history-drawer .drawer-body > .error { margin: 18px 24px 0; }
.history-topline { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 24px; color: var(--ink-soft); font-size: 12px; }
.history-topline button { font-size: 12px; min-height: 32px; }
.history-tabs { display: flex; gap: 8px; padding: 0 24px 16px; border-bottom: 1px solid var(--line); }
.history-tabs button { font-size: 12px; min-height: 34px; }
.history-tabs button[aria-pressed="true"] { background: var(--moss-soft); color: var(--moss-dark); border-color: var(--moss); }
.history-tabs .history-list-tab { display: none; }
.history-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); min-height: 50dvh; }
.history-list { border-right: 1px solid var(--line); min-width: 0; }
.history-entry { display: grid; gap: 7px; width: 100%; text-align: left; padding: 18px 20px; background: #fff; color: var(--ink); border: 0; border-bottom: 1px solid var(--line); border-radius: 0; box-shadow: none; line-height: 1.5; font-weight: 400; }
.history-entry:hover, .history-entry.selected { background: var(--moss-soft); }
.history-entry.selected { box-shadow: inset 3px 0 var(--moss); }
.history-entry-heading { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; }
.history-entry-heading > span, .history-entry-author, .history-entry time { font-size: 11px; color: var(--ink-soft); }
.history-entry-title { overflow-wrap: anywhere; font-size: 13px; }
.history-content { min-width: 0; padding: 24px; }
.history-content-heading h3 { margin: 0; overflow-wrap: anywhere; font-size: 19px; }
.history-content-heading p { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.history-compare-target { display: grid; gap: 8px; margin: 20px 0; color: var(--ink-soft); font-size: 12px; }
.history-comparison-label { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.history-pagination { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 16px 10px; font-size: 11px; }
.history-pagination button { padding: 8px; min-height: 30px; font-size: 11px; }
.history-state { display: block; padding: 20px; color: var(--ink-soft); font-size: 13px; line-height: 1.8; }
.history-title-diff { display: grid; gap: 8px; margin-block: 16px; font-size: 13px; overflow-wrap: anywhere; }
.history-title-diff > span { color: var(--ink-soft); font-size: 12px; }
.history-title-diff del { color: #a32c37; background: #fff1f0; }
.history-title-diff ins { color: #146c43; background: #eff8f2; text-decoration: none; }
.history-diff { border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.history-diff > div { display: flex; align-items: stretch; color: var(--ink); }
.history-diff > div > span { width: 28px; flex-shrink: 0; padding-block: 4px; text-align: center; user-select: none; background: #00000003; }
.history-diff pre { margin: 0; padding: 4px 12px; min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.8 Consolas, "Microsoft YaHei UI", monospace; }
.history-diff [data-kind="added"] { color: #146c43; background: #eff8f2; }
.history-diff [data-kind="removed"] { color: #a32c37; background: #fff1f0; }
.history-fallback { padding: 20px; background: var(--canvas); border-radius: 8px; font-size: 13px; line-height: 1.8; }
.history-target-preview { margin-top: 20px; }
.history-target-preview summary { cursor: pointer; color: var(--moss-dark); font-size: 13px; }
.history-target-preview h4 { overflow-wrap: anywhere; }
.history-restore-confirm, .history-restore-error { flex-basis: 100%; font-size: 13px; line-height: 1.8; }
.history-restore-confirm p { margin: 4px 0; color: var(--ink-soft); }
@media (max-width: 680px) {
  .history-layout { grid-template-columns: minmax(0, 1fr); }
  .history-layout[data-pane="list"] .history-content { display: none; }
  .history-layout:not([data-pane="list"]) .history-list { display: none; }
  .history-tabs .history-list-tab { display: inline-flex; }
  .history-list { border-right: 0; }
  .history-content { padding: 20px; }
  .history-tabs, .history-topline { padding-inline: 20px; }
  .history-diff pre { font-size: 12px; padding-inline: 8px; }
}
</style>
