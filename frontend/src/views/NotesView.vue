<template>
  <section class="notes-workspace" :class="{ 'has-open-note': hasWorkspaceContent }">
    <aside class="note-explorer">
      <header class="note-explorer-header">
        <div class="note-workspace-identity">
          <RouterLink class="workspace-home-link" :to="{ name: 'dashboard' }" title="返回工作台" aria-label="返回工作台">知</RouterLink>
          <span class="workspace-identity-divider">/</span>
          <h1>笔记</h1>
          <span>{{ notes.length }} 篇</span>
        </div>
        <div class="note-explorer-actions">
          <RouterLink :to="{ name: 'note-new' }" title="新建笔记" aria-label="新建笔记"><SquarePen /></RouterLink>
          <details class="explorer-action-menu" @focusout="closeExplorerMenu">
            <summary title="更多笔记操作" aria-label="更多笔记操作"><Ellipsis /></summary>
            <div class="explorer-action-popover">
              <button type="button" @click="openFolderFromMenu"><FolderPlus /><span>新建文件夹</span></button>
              <RouterLink :to="{ name: 'note-import' }"><Import /><span>导入 Markdown</span></RouterLink>
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

      <form v-if="folderForm.open" class="folder-editor" @submit.prevent="saveFolder">
        <input ref="folderNameInput" v-model="folderForm.name" aria-label="文件夹名称" placeholder="文件夹名称" required maxlength="120" />
        <select v-model="folderForm.parentId" aria-label="上级文件夹">
          <option value="">根目录</option>
          <option v-for="folder in availableParents" :key="folder.id" :value="folder.id">{{ folder.path }}</option>
        </select>
        <div>
          <button type="submit" title="保存文件夹" aria-label="保存文件夹"><Check /></button>
          <button type="button" title="取消" aria-label="取消" @click="closeFolderForm"><X /></button>
        </div>
      </form>

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
            :to="{ name: 'note-edit', params: { id: note.id } }"
          ><FileText /><span>{{ note.title }}</span></RouterLink>
        </div>
        <p v-if="!loading && !folders.length && !unfiledNotes.length" class="tree-empty">暂无文件夹或笔记</p>
      </nav>
    </aside>

    <main class="note-workspace-editor">
      <NoteImportView v-if="isImportRoute" />
      <NoteEditorView v-else-if="isEditorRoute" :key="route.fullPath" @saved="refreshTree" @deleted="refreshTree" />
      <div v-else class="note-workspace-empty">
        <FileText />
        <h2>选择一篇笔记</h2>
        <p>从左侧文件夹中打开笔记，或新建一篇。</p>
        <RouterLink class="button" :to="{ name: 'note-new' }">新建笔记</RouterLink>
      </div>
    </main>
  </section>
</template>

<script setup>
import { Check, Ellipsis, FileText, FolderPlus, Import, ListFilter, Search, SquarePen, X } from '@lucide/vue';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { noteApi, noteFolderApi, tagApi } from '../api/workstation';
import NoteTreeItem from '../components/NoteTreeItem.vue';
import { buildFolderTree, descendantFolderIds, flattenFolderOptions } from '../utils/noteFolders';
import NoteEditorView from './NoteEditorView.vue';
import NoteImportView from './NoteImportView.vue';

const route = useRoute();
const router = useRouter();
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
const isEditorRoute = computed(() => ['note-new', 'note-edit'].includes(route.name));
const isImportRoute = computed(() => route.name === 'note-import');
const hasWorkspaceContent = computed(() => isEditorRoute.value || isImportRoute.value);
const selectedNoteId = computed(() => route.name === 'note-edit' ? Number(route.params.id) : null);
const folderTree = computed(() => buildFolderTree(folders.value, notes.value));
const unfiledNotes = computed(() => notes.value.filter((note) => !note.folderId));
const unavailableParentIds = computed(() => folderForm.id ? descendantFolderIds(folders.value, folderForm.id) : new Set());
const availableParents = computed(() => flattenFolderOptions(folders.value).filter((folder) => !unavailableParentIds.value.has(folder.id)));
let keywordSearchTimer;

async function loadFolders() {
  folders.value = await noteFolderApi.list();
  if (filters.keyword || filters.tagId) folders.value.forEach((folder) => expandedIds.add(folder.id));
  else if (!expandedIds.size) folders.value.filter((folder) => folder.parentId === null).forEach((folder) => expandedIds.add(folder.id));
}

async function loadNotes() {
  try {
    error.value = '';
    notes.value = await noteApi.list({ keyword: filters.keyword, tagId: filters.tagId });
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
  router.push({ name: 'note-new', query: { folderId } });
}

async function openFolderForm(folder = null, parentId = null) {
  Object.assign(folderForm, {
    open: true,
    id: folder?.id || null,
    name: folder?.name || '',
    parentId: folder ? (folder.parentId || '') : (parentId || '')
  });
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
  try {
    error.value = '';
    const payload = { name: folderForm.name, parentId: folderForm.parentId ? Number(folderForm.parentId) : null };
    const saved = folderForm.id ? await noteFolderApi.update(folderForm.id, payload) : await noteFolderApi.create(payload);
    if (saved.parentId) expandedIds.add(saved.parentId);
    expandedIds.add(saved.id);
    closeFolderForm();
    await loadFolders();
  } catch (err) {
    error.value = err.message;
  }
}

async function removeFolder(folder) {
  if (!window.confirm(`确认删除文件夹“${folder.name}”？只能删除空文件夹。`)) return;
  try {
    error.value = '';
    await noteFolderApi.delete(folder.id);
    expandedIds.delete(folder.id);
    await loadFolders();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    [tags.value] = await Promise.all([tagApi.list(), loadFolders(), loadNotes()]);
  } catch (err) {
    error.value = err.message;
    loading.value = false;
  }
});

onBeforeUnmount(() => window.clearTimeout(keywordSearchTimer));
</script>
