<template>
  <section class="page note-trash-page">
    <div class="section-heading">
      <div><span class="eyebrow">数据恢复</span><h2>笔记回收站</h2></div>
      <RouterLink class="secondary button" :to="{ name: 'notes', params: workspaceParams }">返回笔记</RouterLink>
    </div>
    <p class="meta">删除的笔记不会出现在搜索、项目或最近记录中。永久删除后无法恢复。</p>

    <form class="quick-search" @submit.prevent="applySearch">
      <div class="search-field"><Search /><input v-model="input" aria-label="搜索回收站" placeholder="搜索已删除笔记…" maxlength="200" /></div>
      <button type="submit">搜索</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <output v-if="loading" class="trash-loading">正在读取回收站…</output>
    <div v-else-if="items.length" class="collection-panel panel">
      <article v-for="note in items" :key="note.id" class="record-row">
        <div class="record-main">
          <h3>{{ note.title }}</h3>
          <p class="meta">
            删除于 {{ formatDate(note.deletedAt) }}
            · {{ note.deletedByDisplayName || note.deletedBySubject }}
            · {{ note.folderName || '未归档' }}
          </p>
          <p v-if="note.excerpt" class="trash-excerpt">{{ note.excerpt }}</p>
          <div v-if="note.tags.length" class="tag-list">
            <span v-for="tag in note.tags" :key="tag.id" class="tag-chip">{{ tag.name }}</span>
          </div>
        </div>
        <div v-if="canWrite" class="actions">
          <button type="button" class="secondary" :disabled="busyId === note.id" @click="restore(note)"><RotateCcw />恢复</button>
          <button type="button" class="danger" :disabled="busyId === note.id" @click="removePermanently(note)"><Trash2 />永久删除</button>
        </div>
      </article>
    </div>
    <p v-else class="empty">回收站为空</p>

    <div v-if="total > size" class="actions pagination-actions">
      <button type="button" class="secondary" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page + 1 }} 页 · 共 {{ total }} 篇</span>
      <button type="button" class="secondary" :disabled="!hasMore || loading" @click="changePage(page + 1)">下一页</button>
    </div>
  </section>
</template>

<script setup>
import { RotateCcw, Search, Trash2 } from '@lucide/vue';
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { noteApi } from '../api/workstation';
import { workspace } from '../workspace';
import { workspaceIdFromRoute, workspaceRouteParams } from '../utils/workspaceRoute';

const route = useRoute();
const router = useRouter();
const workspaceId = workspaceIdFromRoute(route);
const workspaceParams = workspaceRouteParams(route);
const canWrite = computed(() => workspace.hasPermission('NOTE_WRITE'));
const input = ref(String(route.query.keyword || ''));
const keyword = ref(input.value.trim());
const items = ref([]);
const page = ref(0);
const size = 20;
const total = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const busyId = ref(null);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const response = await noteApi.listDeleted(workspaceId, { keyword: keyword.value, page: page.value, size });
    items.value = response.items;
    total.value = response.total;
    hasMore.value = response.hasMore;
    if (!items.value.length && page.value > 0) {
      page.value -= 1;
      await load();
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function applySearch() {
  keyword.value = input.value.trim();
  page.value = 0;
  await router.replace({ name: 'note-trash', params: workspaceParams, query: keyword.value ? { keyword: keyword.value } : {} });
  await load();
}

async function changePage(nextPage) {
  page.value = nextPage;
  await load();
}

async function restore(note) {
  if (!window.confirm(`确认恢复“${note.title}”？`)) return;
  busyId.value = note.id;
  error.value = '';
  try {
    const restored = await noteApi.restore(workspaceId, note.id);
    await router.push({ name: 'note-edit', params: { ...workspaceParams, id: restored.id } });
  } catch (err) {
    error.value = err.message;
  } finally {
    busyId.value = null;
  }
}

async function removePermanently(note) {
  if (!window.confirm(`永久删除“${note.title}”？此操作无法撤销。`)) return;
  busyId.value = note.id;
  error.value = '';
  try {
    await noteApi.permanentlyDelete(workspaceId, note.id);
    await load();
  } catch (err) {
    error.value = err.message;
  } finally {
    busyId.value = null;
  }
}

function formatDate(value) {
  return new Date(value).toLocaleString();
}

onMounted(load);
</script>

<style scoped>
.trash-loading { display: block; margin: 1em 0; }
.note-trash-page { max-width: 980px; margin: 0 auto; }
.note-trash-page .record-row { align-items: flex-start; }
.note-trash-page .record-main { flex: 1; min-width: 0; }
.note-trash-page .record-row > .actions { flex-wrap: wrap; justify-content: flex-end; }
.note-trash-page .actions button { display: inline-flex; align-items: center; gap: 0.35rem; }
.note-trash-page .actions svg { width: 1rem; height: 1rem; }
.trash-excerpt { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
.pagination-actions { justify-content: center; align-items: center; margin-top: 1rem; }
</style>
