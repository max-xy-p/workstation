<template>
  <section class="page notes-page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="library-summary">
      <div>
        <h2>所有笔记</h2>
        <p>继续最近的记录，或者查找已经沉淀的内容。</p>
      </div>
      <div class="library-actions">
        <span class="library-count"><strong>{{ notes.length }}</strong> 篇</span>
        <RouterLink class="button secondary" :to="{ name: 'note-import' }">
          <AppIcon name="import" />
          <span>导入</span>
        </RouterLink>
      </div>
    </div>

    <section class="filter-panel">
      <div class="filter-search">
        <AppIcon name="search" />
        <input v-model="filters.keyword" aria-label="搜索笔记" placeholder="搜索笔记标题或内容…" @input="scheduleKeywordSearch" @keyup.enter="load" />
      </div>
      <div class="filter-selects">
        <select v-model="filters.categoryId">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
        <select v-model="filters.tagId">
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
        </select>
        <button class="secondary" @click="resetFilters">重置</button>
        <button @click="load">筛选</button>
      </div>
    </section>

    <div v-if="notes.length" class="note-list">
      <article v-for="(note, index) in notes" :key="note.id" class="note-row">
        <span class="note-index">{{ String(index + 1).padStart(2, '0') }}</span>
        <div class="note-content">
          <div class="note-title-line">
            <RouterLink :to="`/notes/${note.id}/edit`"><h3>{{ note.title }}</h3></RouterLink>
            <span class="category-label">{{ note.categoryName || '未分类' }}</span>
          </div>
          <p class="note-excerpt">{{ excerpt(note.content) }}</p>
          <div class="note-meta">
            <span>{{ formatDate(note.updatedAt) }}</span>
            <span>{{ note.projectName || '未关联项目' }}</span>
            <div class="badges">
              <span v-for="tag in note.tags" :key="tag.id" class="badge"># {{ tag.name }}</span>
            </div>
          </div>
        </div>
        <div class="note-actions">
          <RouterLink class="icon-action" :to="`/notes/${note.id}/edit`" aria-label="编辑笔记">编辑</RouterLink>
          <button class="icon-action danger-text" aria-label="删除笔记" @click="remove(note.id)">删除</button>
        </div>
      </article>
    </div>
    <p v-else class="empty">没有找到笔记。调整筛选条件，或者新建一篇。</p>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { categoryApi, noteApi, tagApi } from '../api/workstation';
import AppIcon from '../components/AppIcon.vue';

const route = useRoute();
const error = ref('');
const notes = ref([]);
const categories = ref([]);
const tags = ref([]);
const filters = reactive({ keyword: route.query.keyword || '', categoryId: '', tagId: '' });
let keywordSearchTimer;

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }) : '';
}

function excerpt(content) {
  if (!content) return '这篇笔记还没有摘要。';
  const plain = content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#>*_`\[\]()~-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > 110 ? `${plain.slice(0, 110)}…` : plain;
}

function resetFilters() {
  window.clearTimeout(keywordSearchTimer);
  filters.keyword = '';
  filters.categoryId = '';
  filters.tagId = '';
  load();
}

function scheduleKeywordSearch() {
  window.clearTimeout(keywordSearchTimer);
  keywordSearchTimer = window.setTimeout(load, 280);
}

// 加载列表所需的分类和标签筛选项。
async function loadOptions() {
  const [categoryList, tagList] = await Promise.all([
    categoryApi.list(),
    tagApi.list()
  ]);
  categories.value = categoryList;
  tags.value = tagList;
}

// 根据当前筛选条件加载笔记列表。
async function load() {
  try {
    error.value = '';
    notes.value = await noteApi.list(filters);
  } catch (err) {
    error.value = err.message;
  }
}

// 删除笔记；后端会同步清理笔记和标签的关联关系。
async function remove(id) {
  if (!window.confirm('确认删除笔记？')) return;
  try {
    error.value = '';
    await noteApi.delete(id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadOptions();
    await load();
  } catch (err) {
    error.value = err.message;
  }
});

onBeforeUnmount(() => window.clearTimeout(keywordSearchTimer));
</script>
