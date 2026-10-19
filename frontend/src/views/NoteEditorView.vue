<template>
  <section class="page editor-page focused-editor-page">
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="pendingDrafts.length" class="panel note-conflict-panel">
      <h2>发现未确认的本地草稿</h2>
      <div v-for="draft in pendingDrafts" :key="draft.key" class="actions">
        <span>{{ new Date(draft.updatedAt).toLocaleString() }}</span>
        <button type="button" @click="restorePendingDraft(draft)">恢复这份草稿</button>
        <button type="button" class="secondary" @click="discardPendingDraft(draft)">丢弃</button>
      </div>
    </section>
    <section v-if="conflictActive" class="panel note-conflict-panel" role="alert">
      <h2>笔记已由其他位置更新</h2>
      <p>请先比较服务端内容，再在下方编辑器合并修改。本地保存情况见编辑器状态。</p>
      <details v-if="conflictNote"><summary>查看服务端版本</summary>
        <h3>{{ conflictNote.title }}</h3><pre>{{ conflictNote.content }}</pre>
        <p>文件夹：{{ conflictNote.folderName || '未归档' }} · 项目：{{ conflictNote.projectName || '未关联' }}</p>
        <p>标签：{{ conflictNote.tags.map((tag) => tag.name).join('、') || '无' }}</p>
      </details>

      <div class="actions">
        <button type="button" class="secondary" @click="copyLocalContent">复制我的内容</button>
        <button type="button" class="secondary" @click="refreshConflict">刷新服务端版本</button>
        <button type="button" class="secondary" :disabled="!conflictNote" @click="openServerVersion">载入服务端版本</button>
        <button v-if="canWrite" type="button" :disabled="!conflictNote || saving" @click="retryWithLatestVersion">保存合并后的内容</button>
      </div>
    </section>

    <form class="editor-document" @submit.prevent="save">
      <div class="editor-toolbar">
        <RouterLink class="back-link" :to="{ name: 'notes', params: workspaceParams }">← 返回笔记</RouterLink>
        <div class="editor-status">
          <span>{{ characterCount }} 字</span>
          <span v-if="saveState" class="save-state" :data-sync-status="syncStatus" :title="persistenceReceipt">{{ saveState }}</span>
          <span v-if="!canWrite">只读</span>
          <button v-if="isEdit" type="button" class="secondary note-history-trigger" :disabled="!contentReady || restoringHistory" @click="openHistory">版本历史</button>
          <button v-if="isEdit && canWrite" class="editor-delete-button" :disabled="saving || restoringHistory || !contentReady" type="button" title="移入回收站" aria-label="移入回收站" @click="removeNote"><Trash2 /></button>
          <button v-if="canWrite" type="submit" :disabled="saving || restoringHistory || conflictActive || !contentReady">保存</button>
        </div>
      </div>

      <input v-model="form.title" :readonly="!canWrite || !contentReady || restoringHistory" class="note-title-input" aria-label="笔记标题" placeholder="无标题笔记" required />

      <details class="note-properties">
        <summary>
          <span>笔记属性</span>
          <small>{{ propertySummary }}</small>
        </summary>
        <div class="metadata-grid">
          <label><span>文件夹</span>
            <select v-model="form.folderId" :disabled="!canWrite || !contentReady || restoringHistory">
              <option value="">未归档</option>
              <option v-for="folder in folderOptions" :key="folder.id" :value="folder.id">{{ folder.path }}</option>
            </select>
          </label>
          <label><span>关联项目</span>
            <select v-model="form.projectId" :disabled="!canWrite || !contentReady || restoringHistory">
              <option value="">未关联</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label><span>标签</span>
            <select v-model="form.tagIds" :disabled="!canWrite || !contentReady || restoringHistory" multiple>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
        </div>
      </details>

      <div v-if="relatedTasks.length" class="note-related-tasks">
        <strong>关联任务</strong>
        <RouterLink v-for="task in relatedTasks" :key="task.id" :to="{ name: 'project-detail', params: { ...workspaceParams, id: task.projectId } }">
          {{ task.title }} · {{ task.status }}
        </RouterLink>
      </div>

      <div class="live-markdown-canvas">
        <MarkdownEditor
          v-if="contentReady"
          v-model="form.content"
          :editable="canWrite && !restoringHistory"
          @ready="editorReady = true"
          @error="handleEditorError"
        />
        <p v-if="!editorReady && !error" class="editor-loading">正在打开文稿…</p>
      </div>
    </form>
    <NoteHistoryDrawer v-if="isEdit && historyOpened" v-model="historyOpened" :workspace-id="workspaceId" :note-id="noteId"
      :local-content="{ title: form.title, content: form.content }" :can-restore="canWrite" :dirty="isDirty()"
      :busy="saving || restoringHistory" :restore-error="historyError" @restore="restoreHistory" />
  </section>
</template>

<script setup>
import { Trash2 } from '@lucide/vue';
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { noteApi, noteFolderApi, projectApi, tagApi, taskApi } from '../api/workstation';
import { auth } from '../auth';
import { workspace } from '../workspace';
import MarkdownEditor from '../components/MarkdownEditor.vue';
const NoteHistoryDrawer = defineAsyncComponent(() => import('../components/NoteHistoryDrawer.vue'));
import { flattenFolderOptions } from '../utils/noteFolders';
import { deleteConfirmedDraft, deleteNoteDraft, listNoteDrafts, putNoteDraft } from '../utils/noteDraftStore';

const route = useRoute();
const router = useRouter();
// 页面身份固定到本次编辑，路由切换后的异步响应不能写入另一个空间的草稿。
const workspaceId = Number(route.params.workspaceId);
const workspaceParams = { workspaceId };
const noteId = route.params.id ? Number(route.params.id) : null;
const subjectId = auth.state.user?.subjectId;
const newDraftId = route.query.draftId || window.crypto.randomUUID();
let editSessionId = window.crypto.randomUUID();
const baseDraftKey = ['note-draft', subjectId, workspaceId, noteId || 'new:' + newDraftId].join(':');
let draftKey = baseDraftKey + ':' + editSessionId;
const emit = defineEmits(['saved', 'deleted']);
const error = ref('');
const folders = ref([]);
const projects = ref([]);
const tags = ref([]);
const relatedTasks = ref([]);
const saved = ref(null);
const conflictNote = ref(null);
const conflictActive = ref(false);
const pendingDrafts = ref([]);
const saving = ref(false);
const historyOpened = ref(false);
const restoringHistory = ref(false);
const historyError = ref('');
const saveState = ref('');
const syncStatus = ref('SYNCED');
const contentReady = ref(false);
const editorReady = ref(false);
const form = reactive({ title: '', content: '', folderId: route.query.folderId || '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = Boolean(noteId);
const canWrite = computed(() => workspace.hasPermission('NOTE_WRITE'));
const folderOptions = computed(() => flattenFolderOptions(folders.value));
const characterCount = computed(() => form.content.replace(/\s/g, '').length);
const canSave = computed(() => canWrite.value && form.title.trim().length > 0 && form.content.trim().length > 0);
const persistenceReceipt = computed(() => syncStatus.value);
const propertySummary = computed(() => {
  const folder = folderOptions.value.find((item) => item.id === Number(form.folderId))?.path || '未归档';
  const project = projects.value.find((item) => item.id === Number(form.projectId))?.name;
  return [folder, project, form.tagIds.length ? form.tagIds.length + ' 个标签' : ''].filter(Boolean).join(' / ');
});
let autoSaveTimer;
let draftTimer;
let saveQueued = false;
let hydrating = true;
let disposed = false;
let saveBlocked = false;
let persistedSnapshot = '';
let persistedVersion = null;
let recoveredDraft = null;
let createdNoteId = null;
let draftQueue = Promise.resolve();

function snapshotOf(value) {
  return JSON.stringify({
    title: value.title.trim(),
    content: value.content,
    folderId: value.folderId ? Number(value.folderId) : null,
    projectId: value.projectId ? Number(value.projectId) : null,
    tagIds: [...new Set((value.tagIds || value.tags?.map((tag) => tag.id) || []).map(Number))].sort((a, b) => a - b)
  });
}

function applyNote(value) {
  Object.assign(form, {
    title: value.title,
    content: value.content,
    folderId: value.folderId || '',
    projectId: value.projectId || '',
    tagIds: value.tagIds || value.tags?.map((tag) => tag.id) || []
  });
}

function isDirty() {
  return snapshotOf(form) !== persistedSnapshot;
}

function requirePersistenceReceipt(note, previousVersion = null) {
  if (!Number.isSafeInteger(note?.version) || note.version < 1) {
    throw new Error('服务器未返回有效的持久化凭证');
  }
  if (previousVersion !== null && note.version <= previousVersion) {
    throw new Error('服务器返回的持久化版本没有递增');
  }
  return note.version;
}

function enqueueDraft(operation) {
  draftQueue = draftQueue.catch(() => {}).then(operation);
  return draftQueue;
}

async function saveDraft() {
  const snapshot = snapshotOf(form);
  const targetId = createdNoteId || noteId;
  const key = createdNoteId ? ['note-draft', subjectId, workspaceId, createdNoteId, editSessionId].join(':') : draftKey;
  const record = {
    key, subjectId, workspaceId, noteId: targetId,
    snapshot, baseSnapshot: persistedSnapshot, baseVersion: persistedVersion,
    updatedAt: new Date().toISOString()
  };
  try {
    await enqueueDraft(() => snapshot === record.baseSnapshot
      ? deleteNoteDraft(key)
      : putNoteDraft(record));
    if (!disposed && !saving.value && !conflictActive.value && isDirty()) {
      syncStatus.value = 'CACHED';
      saveState.value = '待同步，修改已缓存';
    }
    return true;
  } catch {
    if (!disposed) {
      syncStatus.value = 'ERROR';
      saveState.value = '本地草稿缓存失败';
    }
    return false;
  }
}

async function clearConfirmedDrafts(snapshot) {
  await enqueueDraft(async () => {
    await deleteConfirmedDraft(draftKey, snapshot);
    if (recoveredDraft?.snapshot === snapshot) {
      await deleteConfirmedDraft(recoveredDraft.key, recoveredDraft.snapshot);
      recoveredDraft = null;
    }
  });
}

async function loadRelatedTasks(note) {
  try {
    relatedTasks.value = note.projectId
      ? (await taskApi.listAll(workspaceId, { projectId: note.projectId }))
        .filter((task) => task.evidenceNoteId === note.id)
      : [];
  } catch {
    // 关联任务查询失败不阻止正文读取和草稿恢复。
    relatedTasks.value = [];
  }
}

async function load() {
  try {
    const [folderList, projectList, tagList] = await Promise.all([
      noteFolderApi.list(workspaceId), projectApi.listAll(workspaceId), tagApi.list(workspaceId)
    ]);
    folders.value = folderList;
    projects.value = projectList;
    tags.value = tagList;
    if (isEdit) {
      const note = await noteApi.get(workspaceId, noteId);
      persistedVersion = requirePersistenceReceipt(note);
      saved.value = note;
      applyNote(note);
      void loadRelatedTasks(note);
    }
    persistedSnapshot = snapshotOf(form);
    try {
      const drafts = await listNoteDrafts(baseDraftKey);
      pendingDrafts.value = drafts.filter((draft) => draft.snapshot !== persistedSnapshot);
      await Promise.all(drafts.filter((item) => item.snapshot === persistedSnapshot)
        .map((draft) => deleteConfirmedDraft(draft.key, draft.snapshot)));
    } catch {
      error.value = '无法读取本地草稿，服务端内容已打开';
    }
    contentReady.value = true;
  } catch (err) {
    error.value = err.message;
  } finally {
    hydrating = false;
  }
}

async function restorePendingDraft(record) {
  if (restoringHistory.value || saving.value) return;
  if (isDirty() && !window.confirm('用所选草稿替换当前编辑内容？当前修改会先保存在本地。')) return;
  if (isDirty() && !await saveDraft()) return;
  try {
    const parsed = JSON.parse(record.snapshot);
    if (!parsed || typeof parsed.title !== 'string' || typeof parsed.content !== 'string' || !Array.isArray(parsed.tagIds)) {
      throw new Error('草稿格式无效，请保留记录并联系管理员');
    }
    recoveredDraft = record;
    const remoteVersion = saved.value?.version ?? null;
    persistedSnapshot = record.baseSnapshot || persistedSnapshot;
    persistedVersion = Number.isSafeInteger(record.baseVersion) ? record.baseVersion : null;
    conflictActive.value = isEdit && persistedVersion !== remoteVersion;
    conflictNote.value = conflictActive.value ? saved.value : null;
    applyNote(parsed);
    pendingDrafts.value = pendingDrafts.value.filter((item) => item.key !== record.key);
    await saveDraft();
    syncStatus.value = conflictActive.value ? 'CONFLICT' : 'PENDING';
    saveState.value = conflictActive.value ? '草稿与服务端版本不同，请处理冲突' : '已恢复，等待保存';
    if (!conflictActive.value) scheduleAutoSave();
  } catch (err) {
    error.value = err.message;
  }
}

async function discardPendingDraft(record) {
  if (!window.confirm('确认丢弃这份本地草稿？')) return;
  try {
    await deleteConfirmedDraft(record.key, record.snapshot);
    pendingDrafts.value = pendingDrafts.value.filter((item) => item.key !== record.key);
  } catch (err) {
    error.value = err.message;
  }
}

async function refreshConflict() {
  try {
    conflictNote.value = await noteApi.get(workspaceId, noteId);
  } catch (err) {
    error.value = '无法读取最新版本，请保留本地内容后重试：' + err.message;
  }
}

async function recordSaveFailure(err) {
  error.value = err.message;
  saveBlocked = true;
  if (err.code === 'NOTE_VERSION_CONFLICT') {
    conflictActive.value = true;
    await refreshConflict();
  }
  const cached = await saveDraft();
  syncStatus.value = conflictActive.value ? 'CONFLICT' : 'ERROR';
  saveState.value = cached ? '保存未完成，修改已缓存' : '保存和本地缓存均失败';
}

async function completePersist(response, snapshot, previousVersion) {
  persistedVersion = requirePersistenceReceipt(response, previousVersion);
  if (!isEdit) createdNoteId = response.id;
  saved.value = response;
  persistedSnapshot = snapshot;
  await clearConfirmedDrafts(snapshot);
  if (isDirty()) await saveDraft();
  if (createdNoteId) await enqueueDraft(() => deleteNoteDraft(draftKey));
  if (disposed) return;
  emit('saved', response);
  syncStatus.value = isDirty() ? 'PENDING' : 'SYNCED';
  saveState.value = isDirty() ? '已有新修改待保存' : '已保存';
  if (!isEdit) {
    await router.replace({ name: 'note-edit', params: { ...workspaceParams, id: response.id } });
  } else {
    void loadRelatedTasks(response);
  }
}

async function persist(manual = false) {
  if (restoringHistory.value) return;
  if (conflictActive.value) {
    if (manual) error.value = '请先处理笔记版本冲突';
    return;
  }
  if (!canSave.value || !contentReady.value) {
    if (manual) error.value = canWrite.value ? '标题和正文不能为空' : '当前工作空间为只读';
    return;
  }
  if (saving.value) {
    saveQueued = true;
    return;
  }
  window.clearTimeout(autoSaveTimer);
  const snapshot = snapshotOf(form);
  const previousVersion = persistedVersion;
  try {
    error.value = '';
    saving.value = true;
    saveBlocked = false;
    syncStatus.value = 'SYNCING';
    saveState.value = '保存中…';
    const payload = JSON.parse(snapshot);
    const targetId = noteId || createdNoteId;
    const response = targetId
      ? await noteApi.update(workspaceId, targetId, { ...payload, expectedVersion: previousVersion })
      : await noteApi.create(workspaceId, payload);
    await completePersist(response, snapshot, previousVersion);
  } catch (err) {
    await recordSaveFailure(err);
  } finally {
    saving.value = false;
    if (!disposed && (saveQueued || isDirty()) && !saveBlocked) scheduleAutoSave();
    saveQueued = false;
  }
}

async function save() {
  await persist(true);
}

function openHistory() {
  historyError.value = '';
  historyOpened.value = true;
}

async function applyHistoryReceipt(response, expectedVersion) {
  const version = requirePersistenceReceipt(response, expectedVersion);
  if (response.id !== noteId) throw new Error('服务器返回的笔记不匹配，请刷新历史核对。');
  if (disposed) return;
  hydrating = true;
  // 原草稿留在旧会话下，后续编辑使用新键，避免自动保存覆盖保留的草稿。
  editSessionId = window.crypto.randomUUID();
  draftKey = baseDraftKey + ':' + editSessionId;
  recoveredDraft = null;
  persistedVersion = version;
  persistedSnapshot = snapshotOf(response);
  saved.value = response;
  applyNote(response);
  conflictActive.value = false;
  conflictNote.value = null;
  saveBlocked = false;
  saveQueued = false;
  syncStatus.value = 'SYNCED';
  saveState.value = '历史已恢复为新版本';
  error.value = '';
  historyOpened.value = false;
  emit('saved', response);
  await nextTick();
  hydrating = false;
  try {
    const drafts = await listNoteDrafts(baseDraftKey);
    if (!disposed) pendingDrafts.value = drafts.filter(draft => draft.snapshot !== persistedSnapshot);
  } catch {
    if (!disposed) error.value = '历史已恢复，原草稿保留在本地；读取草稿列表失败，请刷新页面。';
  }
}

async function restoreHistory(revision, expectedVersion) {
  if (!canWrite.value || !contentReady.value || saving.value || restoringHistory.value || disposed) return;
  restoringHistory.value = true;
  historyError.value = '';
  window.clearTimeout(autoSaveTimer);
  window.clearTimeout(draftTimer);
  try {
    if (isDirty() && !await saveDraft()) {
      historyError.value = '本地草稿未能保存，已停止恢复。请先备份当前内容后重试。';
      return;
    }
    await draftQueue;
    if (disposed) return;
    const response = await noteApi.restoreRevision(workspaceId, noteId, revision.id, expectedVersion);
    await applyHistoryReceipt(response, expectedVersion);
  } catch (err) {
    if (!disposed) {
      historyError.value = err.message + ' 请刷新历史核对服务端内容后再操作。';
      await recordSaveFailure(err);
    }
  } finally {
    restoringHistory.value = false;
  }
}

async function copyLocalContent() {
  try {
    await navigator.clipboard.writeText('# ' + form.title + '\n\n' + form.content);
    saveState.value = '我的内容已复制';
  } catch {
    error.value = '无法复制，请手动选中编辑器内容';
  }
}

async function openServerVersion() {
  const remote = conflictNote.value;
  if (!remote || !window.confirm('载入服务端版本会丢弃当前编辑内容，确认继续？')) return;
  try {
    persistedVersion = requirePersistenceReceipt(remote);
    persistedSnapshot = snapshotOf(remote);
    applyNote(remote);
    saved.value = remote;
    conflictActive.value = false;
    conflictNote.value = null;
    saveBlocked = false;
    await enqueueDraft(() => deleteNoteDraft(draftKey));
    if (recoveredDraft) await deleteConfirmedDraft(recoveredDraft.key, recoveredDraft.snapshot);
    recoveredDraft = null;
    syncStatus.value = 'SYNCED';
    saveState.value = '已载入服务端版本';
    error.value = '';
  } catch (err) {
    error.value = err.message;
  }
}

async function retryWithLatestVersion() {
  const remote = conflictNote.value;
  if (!remote || !window.confirm('确认已比较并合并内容，用当前编辑结果保存到最新版本？')) return;
  persistedVersion = requirePersistenceReceipt(remote);
  persistedSnapshot = snapshotOf(remote);
  conflictActive.value = false;
  conflictNote.value = null;
  saveBlocked = false;
  await saveDraft();
  await persist(true);
}

async function removeNote() {
  if (!canWrite.value || saving.value || restoringHistory.value || !window.confirm('确认将笔记移入回收站？之后可以恢复。')) return;
  try {
    await noteApi.delete(workspaceId, noteId);
    persistedSnapshot = snapshotOf(form);
    await enqueueDraft(() => deleteNoteDraft(draftKey));
    emit('deleted', noteId);
    await router.replace({ name: 'notes', params: workspaceParams });
  } catch (err) {
    error.value = err.message;
  }
}

function scheduleAutoSave() {
  window.clearTimeout(autoSaveTimer);
  if (!isEdit || !canSave.value || conflictActive.value || saveBlocked || disposed || restoringHistory.value) return;
  autoSaveTimer = window.setTimeout(() => { void persist(false); }, 8000);
}

function handleEditorError(err) {
  editorReady.value = true;
  error.value = err?.message || 'Markdown 编辑器加载失败';
}

watch(form, () => {
  if (hydrating || disposed || restoringHistory.value) return;
  window.clearTimeout(draftTimer);
  window.clearTimeout(autoSaveTimer);
  if (!isDirty()) {
    void saveDraft();
    syncStatus.value = conflictActive.value ? 'CONFLICT' : 'SYNCED';
    saveState.value = conflictActive.value ? '请处理版本冲突' : '已同步';
    return;
  }
  syncStatus.value = conflictActive.value ? 'CONFLICT' : 'PENDING';
  saveState.value = conflictActive.value ? '请处理版本冲突' : '待同步';
  draftTimer = window.setTimeout(() => { void saveDraft(); }, 350);
  scheduleAutoSave();
}, { deep: true });

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    void save();
  }
}

function persistDraftWhenHidden() {
  if (!hydrating && document.visibilityState === 'hidden' && isDirty()) void saveDraft();
}

async function protectDraft() {
  if (restoringHistory.value) return false;
  if (hydrating || !isDirty()) return true;
  const cached = await saveDraft();
  return cached || window.confirm('本地草稿保存失败，继续离开可能丢失修改。仍然离开？');
}
onBeforeRouteLeave(protectDraft);
onBeforeRouteUpdate(protectDraft);

onMounted(() => {
  window.addEventListener('keydown', handleShortcut);
  document.addEventListener('visibilitychange', persistDraftWhenHidden);
  void load();
});

onBeforeUnmount(() => {
  disposed = true;
  window.clearTimeout(autoSaveTimer);
  window.clearTimeout(draftTimer);
  if (!hydrating && isDirty()) void saveDraft();
  window.removeEventListener('keydown', handleShortcut);
  document.removeEventListener('visibilitychange', persistDraftWhenHidden);
});
</script>

<style scoped>
.note-conflict-panel { margin-bottom: 1rem; }
.note-conflict-panel pre { max-height: 18rem; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
.note-conflict-panel .actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.75rem; }
.note-related-tasks { display: flex; flex-wrap: wrap; gap: 0.75rem; margin: 0.75rem 0; }
</style>
