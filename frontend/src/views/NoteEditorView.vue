<template>
  <section class="page editor-page focused-editor-page">
    <p v-if="error" class="error">{{ error }}</p>

    <form class="editor-document" @submit.prevent="save">
      <div class="editor-toolbar">
        <RouterLink class="back-link" :to="{ name: 'notes' }">← 返回笔记</RouterLink>
        <div class="editor-status">
          <span>{{ characterCount }} 字</span>
          <span v-if="saveState" class="save-state">{{ saveState }}</span>
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
import MarkdownEditor from '../components/MarkdownEditor.vue';
import { flattenFolderOptions } from '../utils/noteFolders';

const route = useRoute();
const router = useRouter();
const error = ref('');
const folders = ref([]);
const projects = ref([]);
const tags = ref([]);
const saved = ref(null);
const saveState = ref('');
const contentReady = ref(false);
const editorReady = ref(false);
const emit = defineEmits(['saved', 'deleted']);
const form = reactive({ title: '', content: '', folderId: route.query.folderId || '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = computed(() => Boolean(route.params.id));
const folderOptions = computed(() => flattenFolderOptions(folders.value));
const characterCount = computed(() => form.content.replace(/\s/g, '').length);
const draftKey = computed(() => `workstation:note-draft:${route.params.id || 'new'}`);
const canSave = computed(() => form.title.trim().length > 0 && form.content.trim().length > 0);
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
let skipDraftPersistOnUnmount = false;

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
    projectApi.list({}),
    tagApi.list()
  ]);
  folders.value = folderList;
  projects.value = projectList;
  tags.value = tagList;
}

async function loadNote() {
  if (!isEdit.value) return;
  const note = await noteApi.get(route.params.id);
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
    clearDraft();
    skipDraftPersistOnUnmount = true;
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
  try {
    error.value = '';
    saving = true;
    saveState.value = manual ? '保存中…' : '自动保存中…';
    if (isEdit.value) {
      saved.value = await noteApi.update(route.params.id, payload());
    } else {
      saved.value = await noteApi.create(payload());
      router.replace({ name: 'note-edit', params: { id: saved.value.id } });
    }
    if (snapshot === formSnapshot()) {
      clearDraft();
      skipDraftPersistOnUnmount = true;
    }
    emit('saved', saved.value);
    saveState.value = manual ? '已保存' : '已自动保存';
  } catch (err) {
    error.value = err.message;
    saveState.value = '';
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

function saveDraft() {
  try {
    if (!form.title.trim() && !form.content.trim() && !form.folderId && !form.projectId && form.tagIds.length === 0) {
      clearDraft();
      return;
    }
    window.localStorage.setItem(draftKey.value, formSnapshot());
  } catch {
    // 浏览器存储不可用时，服务端保存仍然可用。
  }
}

function clearDraft() {
  try {
    window.localStorage.removeItem(draftKey.value);
  } catch {
    // 浏览器存储不可用时，不影响服务端保存。
  }
}

function restoreDraft() {
  let draft;
  try {
    draft = window.localStorage.getItem(draftKey.value);
  } catch {
    return;
  }
  if (!draft) return;
  if (!window.confirm('发现未保存的本地草稿，是否恢复？')) {
    clearDraft();
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
    saveState.value = '已恢复本地草稿';
  } catch {
    clearDraft();
  }
}

function scheduleDraft() {
  window.clearTimeout(draftTimer);
  draftTimer = window.setTimeout(saveDraft, 350);
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
  skipDraftPersistOnUnmount = false;
  scheduleDraft();
  scheduleAutoSave();
}, { deep: true });

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    save();
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleShortcut);
  try {
    await loadOptions();
    await loadNote();
    restoreDraft();
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
  if (!hydrating && !skipDraftPersistOnUnmount) saveDraft();
  window.removeEventListener('keydown', handleShortcut);
});
</script>
