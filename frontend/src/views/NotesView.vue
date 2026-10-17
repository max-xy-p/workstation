<template>
  <div class="note-space-shell">
  <PageActions>
    <button v-if="isEditorRoute" class="secondary" type="button" :aria-pressed="focused" @click="focused = !focused">{{ focused ? '退出专注' : '专注写作' }}</button>
    <RouterLink v-if="canWrite" class="button" :to="{ name: 'note-new', params: workspaceParams }">新建笔记</RouterLink>
  </PageActions>
  <section class="notes-workspace" :class="{ 'has-open-note': hasWorkspaceContent }">
    <aside class="note-explorer">
      <header class="note-explorer-header">
        <div class="note-workspace-identity">
          <h2>知识库</h2>
          <span>{{ notes.length }} 篇</span>
        </div>
        <div class="note-explorer-actions">
          <RouterLink v-if="canWrite" :to="{ name: 'note-new', params: workspaceParams }" title="新建笔记" aria-label="新建笔记"><SquarePen /></RouterLink>
          <details class="explorer-action-menu" @focusout="closeExplorerMenu">
            <summary title="更多笔记操作" aria-label="更多笔记操作"><Ellipsis /></summary>
            <div class="explorer-action-popover">
              <button v-if="canWriteFolder" type="button" @click="openFolderFromMenu"><FolderPlus /><span>新建文件夹</span></button>
              <RouterLink v-if="canImport" :to="{ name: 'note-import', params: workspaceParams }"><Import /><span>导入 Markdown</span></RouterLink>
              <RouterLink :to="{ name: 'note-trash', params: workspaceParams }"><Trash2 /><span>回收站</span></RouterLink>
            </div>
          </details>
        </div>
      </header>

      <div class="note-explorer-filters">
        <label class="explorer-search">
          <Search />
          <input v-model="filters.keyword" aria-label="搜索笔记" placeholder="搜索笔记" @input="scheduleKeywordSearch" @keyup.enter="loadNotes" />
          <span class="explorer-search-actions">
            <button v-if="filters.keyword" type="button" title="清除搜索" aria-label="清除搜索" @click="clearSearch"><X /></button>
            <button
              type="button"
              title="按标签筛选"
              aria-label="按标签筛选"
              :aria-expanded="showFilters"
              :class="{ active: showFilters || filters.tagId }"
              @click="showFilters = !showFilters"
            ><ListFilter /></button>
          </span>
        </label>
        <select v-if="showFilters" v-model="filters.tagId" class="explorer-tag-filter" aria-label="按标签筛选" @change="loadNotes">
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
        </select>
      </div>

      <AppDrawer v-model="folderForm.open" :title="folderForm.id ? '编辑文件夹' : '新建文件夹'" :busy="folderSaving" :dirty="folderDirty">
      <p v-if="folderError" class="error" role="alert">{{ folderError }}</p>
      <form id="folder-form" class="form-grid" @submit.prevent="saveFolder">
        <label class="wide">文件夹名称<input ref="folderNameInput" v-model="folderForm.name" placeholder="文件夹名称" required maxlength="120" /></label>
        <label class="wide">上级文件夹<select v-model="folderForm.parentId">
          <option value="">根目录</option>
          <option v-for="folder in availableParents" :key="folder.id" :value="folder.id">{{ folder.path }}</option>
        </select></label>
      </form>
      <template #footer="{ close }"><button type="button" class="secondary" :disabled="folderSaving" @click="close">取消</button><button type="submit" form="folder-form" :disabled="folderSaving">{{ folderSaving ? '保存中…' : '保存文件夹' }}</button></template>
      </AppDrawer>

      <p v-if="error" class="explorer-error">{{ error }}</p>
      <nav class="note-tree" aria-label="笔记文件夹">
        <NoteTreeItem
          v-for="folder in folderTree"
          :key="folder.id"
          :folder="folder"
          :expanded-ids="expandedIds"
          :selected-note-id="selectedNoteId"
          @toggle="toggleFolder"
          @new-note="newNoteInFolder"
          @new-child="openFolderForm(null, $event)"
          @edit="openFolderForm($event)"
          @delete="removeFolder"
        />

        <div v-if="unfiledNotes.length" class="unfiled-notes">
          <p>未归档</p>
          <RouterLink
            v-for="note in unfiledNotes"
            :key="note.id"
            class="note-tree-note-row"
            :class="{ selected: selectedNoteId === note.id }"
            :to="{ name: 'note-edit', params: { ...workspaceParams, id: note.id } }"
          ><FileText /><span>{{ note.title }}</span></RouterLink>
        </div>
        <p v-if="!loading && !folders.length && !unfiledNotes.length" class="tree-empty">暂无文件夹或笔记</p>
      </nav>
    </aside>

    <section class="note-workspace-editor" aria-label="笔记内容">
      <NoteImportView v-if="isImportRoute" />
      <NoteTrashView v-else-if="isTrashRoute" />
      <NoteEditorView v-else-if="isEditorRoute" :key="route.fullPath" @saved="refreshTree" @deleted="refreshTree" />
      <div v-else class="note-workspace-empty">
        <FileText />
        <h2>选择一篇笔记</h2>
        <p>从左侧文件夹中打开笔记，或新建一篇。</p>
        <RouterLink v-if="canWrite" class="button" :to="{ name: 'note-new', params: workspaceParams }">新建笔记</RouterLink>
      </div>
    </section>
  </section>
  </div>
</template>

<script setup>
import { Ellipsis, FileText, FolderPlus, Import, ListFilter, Search, SquarePen, Trash2, X } from '@lucide/vue';
import { computed, inject, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { noteApi, noteFolderApi, tagApi } from '../api/workstation';
import NoteTreeItem from '../components/NoteTreeItem.vue';
import AppDrawer from '../components/AppDrawer.vue';
import PageActions from '../components/PageActions.vue';
import { workspace } from '../workspace';
import { buildFolderTree, descendantFolderIds, flattenFolderOptions } from '../utils/noteFolders';
import { workspaceIdFromRoute, workspaceRouteParams } from '../utils/workspaceRoute';
import NoteEditorView from './NoteEditorView.vue';
import NoteImportView from './NoteImportView.vue';
import NoteTrashView from './NoteTrashView.vue';

const route = useRoute();
const router = useRouter();
const focused = inject('notesFocus', ref(false));
const canWrite = computed(() => workspace.hasPermission('NOTE_WRITE'));
const canWriteFolder = computed(() => workspace.hasPermission('FOLDER_WRITE'));
const canImport = computed(() => workspace.hasPermission('NOTE_IMPORT'));
const workspaceId = computed(() => workspaceIdFromRoute(route));
const workspaceParams = workspaceRouteParams(route);
const error = ref('');
const loading = ref(true);
const notes = ref([]);
const folders = ref([]);
const tags = ref([]);
const expandedIds = reactive(new Set());
const folderNameInput = ref(null);
const filters = reactive({ keyword: route.query.keyword || '', tagId: '' });
const showFilters = ref(false);
const folderForm = reactive({ open: false, id: null, name: '', parentId: '' });
const folderInitial = ref('');
const folderSaving = ref(false);
const folderError = ref('');
const folderDirty = computed(() => folderForm.open && JSON.stringify(folderForm) !== folderInitial.value);
const isEditorRoute = computed(() => ['note-new', 'note-edit'].includes(route.name));
const isImportRoute = computed(() => route.name === 'note-import');
const isTrashRoute = computed(() => route.name === 'note-trash');
const hasWorkspaceContent = computed(() => isEditorRoute.value || isImportRoute.value || isTrashRoute.value);
const selectedNoteId = computed(() => route.name === 'note-edit' ? Number(route.params.id) : null);
const folderTree = computed(() => buildFolderTree(folders.value, notes.value));
const unfiledNotes = computed(() => notes.value.filter((note) => !note.folderId));
const unavailableParentIds = computed(() => folderForm.id ? descendantFolderIds(folders.value, folderForm.id) : new Set());
const availableParents = computed(() => flattenFolderOptions(folders.value).filter((folder) => !unavailableParentIds.value.has(folder.id)));
let keywordSearchTimer;
watch(isEditorRoute, (editing) => { if (!editing) focused.value = false; });
watch(() => route.name, (name, previous) => { if (previous === 'note-import' || previous === 'note-trash') void refreshTree(); });

async function loadFolders() {
  folders.value = await noteFolderApi.list(workspaceId.value);
  if (filters.keyword || filters.tagId) folders.value.forEach((folder) => expandedIds.add(folder.id));
  else if (!expandedIds.size) folders.value.filter((folder) => folder.parentId === null).forEach((folder) => expandedIds.add(folder.id));
}

async function loadNotes() {
  try {
    error.value = '';
    notes.value = await noteApi.listAll(
      workspaceId.value,
      { keyword: filters.keyword, tagId: filters.tagId }
    );
    if (filters.keyword || filters.tagId) folders.value.forEach((folder) => expandedIds.add(folder.id));
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function refreshTree() {
  await Promise.all([loadFolders(), loadNotes()]);
}

function scheduleKeywordSearch() {
  window.clearTimeout(keywordSearchTimer);
  keywordSearchTimer = window.setTimeout(loadNotes, 280);
}

function clearSearch() {
  filters.keyword = '';
  loadNotes();
}

function toggleFolder(id) {
  if (expandedIds.has(id)) expandedIds.delete(id);
  else expandedIds.add(id);
}

function newNoteInFolder(folderId) {
  expandedIds.add(folderId);
  router.push({ name: 'note-new', params: workspaceParams, query: { folderId } });
}

async function openFolderForm(folder = null, parentId = null) {
  Object.assign(folderForm, {
    open: true,
    id: folder?.id || null,
    name: folder?.name || '',
    parentId: folder ? (folder.parentId || '') : (parentId || '')
  });
  folderInitial.value = JSON.stringify(folderForm);
  folderError.value = '';
  if (parentId) expandedIds.add(parentId);
  await nextTick();
  folderNameInput.value?.focus();
}

function closeFolderForm() {
  Object.assign(folderForm, { open: false, id: null, name: '', parentId: '' });
}

function openFolderFromMenu(event) {
  event.currentTarget.closest('details')?.removeAttribute('open');
  openFolderForm();
}

function closeExplorerMenu(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.removeAttribute('open');
}

async function saveFolder() {
  if (folderSaving.value || !canWriteFolder.value) return;
  folderSaving.value = true;
  try {
    folderError.value = '';
    const payload = { name: folderForm.name, parentId: folderForm.parentId ? Number(folderForm.parentId) : null };
    const saved = folderForm.id
      ? await noteFolderApi.update(workspaceId.value, folderForm.id, payload)
      : await noteFolderApi.create(workspaceId.value, payload);
    if (saved.parentId) expandedIds.add(saved.parentId);
    expandedIds.add(saved.id);
    closeFolderForm();
    await loadFolders();
  } catch (err) {
    folderError.value = err.message;
  } finally {
    folderSaving.value = false;
  }
}

async function removeFolder(folder) {
  if (!window.confirm(`确认删除文件夹“${folder.name}”？只能删除空文件夹。`)) return;
  try {
    error.value = '';
    await noteFolderApi.delete(workspaceId.value, folder.id);
    expandedIds.delete(folder.id);
    await loadFolders();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    [tags.value] = await Promise.all([tagApi.list(workspaceId.value), loadFolders(), loadNotes()]);
  } catch (err) {
    error.value = err.message;
    loading.value = false;
  }
});

onBeforeUnmount(() => window.clearTimeout(keywordSearchTimer));
</script>

<style scoped>
.note-space-shell { display: flex; flex-direction: column; }
.note-space-shell > .notes-workspace { height: auto; min-height: 0; flex: 1; }
</style>
