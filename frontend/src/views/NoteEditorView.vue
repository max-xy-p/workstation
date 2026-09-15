<template>
  <section class="page editor-page focused-editor-page">
    <p v-if="error" class="error">{{ error }}</p>

    <form class="editor-document" @submit.prevent="save">
      <div class="editor-toolbar">
        <RouterLink class="back-link" :to="{ name: 'notes' }">← 返回笔记</RouterLink>
        <div class="editor-status">
          <span>{{ characterCount }} 字</span>
          <span v-if="saveState" class="save-state" :data-sync-status="syncStatus" :title="persistenceReceipt">{{ saveState }}</span>
          <button v-if="isEdit" class="editor-delete-button" type="button" title="删除笔记" aria-label="删除笔记" @click="removeNote"><Trash2 /></button>
          <button type="submit">保存</button>
        </div>
      </div>

      <input v-model="form.title" class="note-title-input" aria-label="笔记标题" placeholder="无标题笔记" required />

      <details class="note-properties">
        <summary>
          <span>笔记属性</span>
          <small>{{ propertySummary }}</small>
        </summary>
        <div class="metadata-grid">
          <label><span>文件夹</span>
            <select v-model="form.folderId">
              <option value="">未归档</option>
              <option v-for="folder in folderOptions" :key="folder.id" :value="folder.id">{{ folder.path }}</option>
            </select>
          </label>
          <label><span>关联项目</span>
            <select v-model="form.projectId">
              <option value="">未关联</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label><span>标签</span>
            <select v-model="form.tagIds" multiple>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
        </div>
      </details>

      <div class="live-markdown-canvas">
        <MarkdownEditor
          v-if="contentReady"
          v-model="form.content"
          @ready="editorReady = true"
          @error="handleEditorError"
        />
        <p v-if="!editorReady" class="editor-loading">正在打开文稿…</p>
      </div>
    </form>
  </section>
</template>

<script setup>
import { Trash2 } from '@lucide/vue';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { noteApi, noteFolderApi, projectApi, tagApi } from '../api/workstation';
import { auth } from '../auth';
import MarkdownEditor from '../components/MarkdownEditor.vue';
import { flattenFolderOptions } from '../utils/noteFolders';
import { deleteNoteDraft, getNoteDraft, putNoteDraft } from '../utils/noteDraftStore';

const route = useRoute();
const router = useRouter();
const error = ref('');
const folders = ref([]);
const projects = ref([]);
const tags = ref([]);
const saved = ref(null);
const saveState = ref('');
const syncStatus = ref('SYNCED');
const contentReady = ref(false);
const editorReady = ref(false);
const emit = defineEmits(['saved', 'deleted']);
const form = reactive({ title: '', content: '', folderId: route.query.folderId || '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = computed(() => Boolean(route.params.id));
const folderOptions = computed(() => flattenFolderOptions(folders.value));
const characterCount = computed(() => form.content.replace(/\s/g, '').length);
const draftKey = computed(() => `note-draft:${auth.state.user?.username || 'anonymous'}:${route.params.id || 'new'}`);
const canSave = computed(() => form.title.trim().length > 0 && form.content.trim().length > 0);
const persistenceReceipt = computed(() => persistedVersion === null ? syncStatus.value : `${syncStatus.value} · version ${persistedVersion}`);
const propertySummary = computed(() => {
  const folder = folderOptions.value.find((item) => item.id === Number(form.folderId))?.path || '未归档';
  const project = projects.value.find((item) => item.id === Number(form.projectId))?.name;
  const parts = [folder];
  if (project) parts.push(project);
  if (form.tagIds.length) parts.push(`${form.tagIds.length} 个标签`);
  return parts.join(' / ');
});
let autoSaveTimer;
let draftTimer;
let saving = false;
let saveQueued = false;
let hydrating = true;
let persistedSnapshot = '';
let persistedVersion = null;

function payload() {
  return {
    title: form.title,
    content: form.content,
    folderId: form.folderId ? Number(form.folderId) : null,
    projectId: form.projectId ? Number(form.projectId) : null,
    tagIds: form.tagIds.map(Number)
  };
}

async function loadOptions() {
  const [folderList, projectList, tagList] = await Promise.all([
    noteFolderApi.list(),
    projectApi.listAll(),
    tagApi.list()
  ]);
  folders.value = folderList;
  projects.value = projectList;
  tags.value = tagList;
}

async function loadNote() {
  if (!isEdit.value) return;
  const note = await noteApi.get(route.params.id);
  persistedVersion = requirePersistenceReceipt(note);
  saved.value = note;
  Object.assign(form, {
    title: note.title,
    content: note.content,
    folderId: note.folderId || '',
    projectId: note.projectId || '',
    tagIds: note.tags.map((tag) => tag.id)
  });
}

async function save() {
  await persist(true);
}

async function removeNote() {
  if (!window.confirm('确认删除笔记？')) return;
  try {
    error.value = '';
    await noteApi.delete(route.params.id);
    await clearDraft();
    emit('deleted', Number(route.params.id));
    await router.replace({ name: 'notes' });
  } catch (err) {
    error.value = err.message;
  }
}

async function persist(manual = false) {
  if (!canSave.value) {
    if (manual) error.value = '标题和正文不能为空';
    return;
  }
  if (saving) {
    if (!manual) saveQueued = true;
    return;
  }

  const snapshot = formSnapshot();
  const pendingDraftKey = draftKey.value;
  const previousVersion = persistedVersion;
  let createdNoteId = null;
  try {
    error.value = '';
    saving = true;
    syncStatus.value = 'SYNCING';
    saveState.value = manual ? '保存中…' : '自动保存中…';
    if (isEdit.value) {
      saved.value = await noteApi.update(route.params.id, payload());
    } else {
      saved.value = await noteApi.create(payload());
      createdNoteId = saved.value.id;
    }
    persistedVersion = requirePersistenceReceipt(saved.value, previousVersion);
    persistedSnapshot = snapshot;
    if (!isDirty()) {
      await clearDraft(pendingDraftKey);
    } else {
      scheduleDraft();
    }
    emit('saved', saved.value);
    syncStatus.value = 'SYNCED';
    saveState.value = manual ? '已保存' : '已自动保存';
    if (createdNoteId) {
      await router.replace({ name: 'note-edit', params: { id: createdNoteId } });
    }
  } catch (err) {
    error.value = err.message;
    const cached = await saveDraft();
    syncStatus.value = 'ERROR';
    saveState.value = cached ? '保存失败，修改已缓存' : '保存和本地缓存均失败';
  } finally {
    saving = false;
    if (saveQueued) {
      saveQueued = false;
      scheduleAutoSave();
    }
  }
}

function formSnapshot() {
  return JSON.stringify({
    title: form.title,
    content: form.content,
    folderId: form.folderId,
    projectId: form.projectId,
    tagIds: [...form.tagIds]
  });
}

function isDirty() {
  return formSnapshot() !== persistedSnapshot;
}

function requirePersistenceReceipt(note, previousVersion = null) {
  const version = Number(note?.version);
  if (!Number.isSafeInteger(version) || version < 1) {
    throw new Error('服务器未返回有效的持久化凭证');
  }
  if (previousVersion !== null && version <= previousVersion) {
    throw new Error('服务器返回的持久化版本没有递增');
  }
  return version;
}

async function saveDraft() {
  try {
    if (!isDirty()) {
      await clearDraft();
      return true;
    }
    await putNoteDraft({
      key: draftKey.value,
      noteId: isEdit.value ? Number(route.params.id) : null,
      snapshot: formSnapshot(),
      baseSnapshot: persistedSnapshot,
      baseVersion: persistedVersion,
      updatedAt: new Date().toISOString()
    });
    syncStatus.value = 'CACHED';
    saveState.value = '待同步，修改已缓存';
    return true;
  } catch {
    syncStatus.value = 'ERROR';
    saveState.value = '本地草稿缓存失败';
    return false;
  }
}

async function clearDraft(key = draftKey.value) {
  try {
    await deleteNoteDraft(key);
  } catch {
    // 清理失败只会留下可识别的旧快照，不影响服务端保存。
  }
}

async function restoreDraft() {
  let record;
  try {
    record = await getNoteDraft(draftKey.value);
  } catch {
    syncStatus.value = 'ERROR';
    saveState.value = '无法读取本地草稿';
    return;
  }
  const draft = record?.snapshot;
  if (!draft) return;
  if (draft === persistedSnapshot) {
    await clearDraft();
    return;
  }
  if (!window.confirm('发现未保存的本地草稿，是否恢复？')) {
    await clearDraft();
    return;
  }

  try {
    const parsed = JSON.parse(draft);
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.tagIds)) throw new Error('草稿格式无效');
    Object.assign(form, {
      title: typeof parsed.title === 'string' ? parsed.title : '',
      content: typeof parsed.content === 'string' ? parsed.content : '',
      folderId: parsed.folderId || '',
      projectId: parsed.projectId || '',
      tagIds: parsed.tagIds
    });
    syncStatus.value = 'PENDING';
    saveState.value = '已恢复，等待同步';
  } catch {
    await clearDraft();
  }
}

function scheduleDraft() {
  window.clearTimeout(draftTimer);
  draftTimer = window.setTimeout(() => void saveDraft(), 350);
}

function scheduleAutoSave() {
  if (!isEdit.value || !canSave.value) return;
  window.clearTimeout(autoSaveTimer);
  autoSaveTimer = window.setTimeout(() => persist(false), 8000);
}

function handleEditorError(err) {
  editorReady.value = true;
  error.value = err?.message || 'Markdown 编辑器加载失败';
}

watch(form, () => {
  if (hydrating) return;
  if (!isDirty()) {
    window.clearTimeout(autoSaveTimer);
    window.clearTimeout(draftTimer);
    void clearDraft();
    syncStatus.value = 'SYNCED';
    saveState.value = '已同步';
    return;
  }
  syncStatus.value = 'PENDING';
  saveState.value = '待同步';
  scheduleDraft();
  scheduleAutoSave();
}, { deep: true });

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    save();
  }
}

function persistDraftWhenHidden() {
  if (!hydrating && document.visibilityState === 'hidden' && isDirty()) {
    void saveDraft();
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleShortcut);
  document.addEventListener('visibilitychange', persistDraftWhenHidden);
  try {
    await loadOptions();
    await loadNote();
    persistedSnapshot = formSnapshot();
    await restoreDraft();
  } catch (err) {
    error.value = err.message;
  } finally {
    hydrating = false;
    contentReady.value = true;
  }
});

onBeforeUnmount(() => {
  window.clearTimeout(autoSaveTimer);
  window.clearTimeout(draftTimer);
  if (!hydrating && isDirty()) void saveDraft();
  window.removeEventListener('keydown', handleShortcut);
  document.removeEventListener('visibilitychange', persistDraftWhenHidden);
});
</script>
