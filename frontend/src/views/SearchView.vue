<template>
  <section class="page search-page">
    <form class="quick-search" @submit.prevent="search">
      <div class="search-field"><AppIcon name="search" /><input v-model="input" aria-label="搜索当前工作空间" placeholder="搜索项目、任务、笔记标题或正文…" maxlength="200" /></div>
      <button type="submit">搜索</button>
    </form>
    <p v-if="!keyword" class="empty">输入关键词，在当前工作空间查找项目、任务与笔记。</p>
    <p v-else class="meta">“{{ keyword }}” · 当前工作空间</p>
    <div v-if="keyword" class="search-results">
      <section v-for="group in groups" :key="group.type" class="panel collection-panel" :aria-label="group.label + '搜索结果'">
        <div class="section-heading"><h2>{{ group.label }}</h2><span aria-live="polite">{{ group.total }} 条</span></div>
        <p v-if="group.error" class="error">{{ group.error }} <button class="secondary" type="button" @click="loadGroup(group)">重试</button></p>
        <RouterLink v-for="item in group.items" :key="item.id" class="record-row" :to="resultRoute(group.type, item)">
          <div class="record-main">
            <h3><SearchHighlight :text="item.title || item.name" :keyword="keyword" /></h3>
            <p class="meta">{{ item.projectName || item.folderName || group.label }} · {{ formatDate(item.updatedAt) }}</p>
            <p v-if="item.excerpt" class="search-excerpt"><SearchHighlight :text="item.excerpt" :keyword="keyword" /></p>
          </div>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <p v-if="!group.loading && !group.error && !group.items.length" class="empty">没有匹配的{{ group.label }}</p>
        <p v-if="group.loading" role="status">正在查找…</p>
        <button v-if="group.hasMore && !group.error" class="secondary" type="button" :disabled="group.loading" @click="loadGroup(group)">加载更多{{ group.label }}</button>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { noteApi, projectApi, taskApi } from '../api/workstation';
import AppIcon from '../components/AppIcon.vue';
import SearchHighlight from '../components/SearchHighlight.vue';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';

const route = useRoute();
const router = useRouter();
const workspaceId = workspaceIdFromRoute(route);
const keyword = String(route.query.keyword || '').trim();
const input = ref(keyword);
const groups = reactive([
  { type: 'notes', label: '笔记', api: noteApi },
  { type: 'projects', label: '项目', api: projectApi },
  { type: 'tasks', label: '任务', api: taskApi }
].map((group) => ({ ...group, items: [], page: 0, total: 0, hasMore: false, loading: false, error: '' })));

function search() {
  void router.push({ name: 'search', params: { workspaceId }, query: { keyword: input.value.trim() } });
}

async function loadGroup(group) {
  if (group.loading || !keyword) return;
  group.loading = true;
  group.error = '';
  try {
    const response = await group.api.list(workspaceId, { keyword, page: group.page, size: 10 });
    group.items.push(...response.items);
    group.total = response.total;
    group.hasMore = response.hasMore;
    group.page += 1;
  } catch (err) {
    group.error = err.message;
  } finally {
    group.loading = false;
  }
}

function resultRoute(type, item) {
  if (type === 'notes') return { name: 'note-edit', params: { workspaceId, id: item.id } };
  if (type === 'projects') return { name: 'project-detail', params: { workspaceId, id: item.id } };
  return { name: 'tasks', params: { workspaceId }, query: { projectId: item.projectId, taskId: item.id } };
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}

onMounted(() => { for (const group of groups) void loadGroup(group); });
</script>

<style scoped>
.search-results { display: grid; gap: 1.25rem; }
.search-excerpt { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; }
.search-page .record-main { min-width: 0; flex: 1; }
.search-page .record-main h3 { overflow-wrap: anywhere; }
</style>
