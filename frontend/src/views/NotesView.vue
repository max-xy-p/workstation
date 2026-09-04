<template>
  <section class="page notes-page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="library-summary">
      <div>
        <span class="eyebrow">知识索引</span>
        <h2>所有笔记</h2>
        <p>按标题、分类和标签找到需要的内容。</p>
      </div>
      <span class="library-count"><strong>{{ notes.length }}</strong> 篇</span>
    </div>

    <section class="filter-panel">
      <div class="filter-search">
        <AppIcon name="search" />
        <input v-model="filters.keyword" aria-label="搜索笔记标题" placeholder="搜索笔记标题…" @keyup.enter="load" />
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

    <details class="import-drawer">
      <summary>
        <span class="import-symbol">↓</span>
        <span><strong>导入 Markdown</strong><small>从允许的本机目录批量加入知识库</small></span>
        <span class="summary-action">展开设置</span>
      </summary>
      <div class="import-content">
        <form class="form-grid" @submit.prevent="importNotes">
          <label class="wide">导入路径
            <input v-model="importForm.sourcePath" placeholder="例如 D:\notes\ExportBlock-..." required />
          </label>
          <label>分类
            <select v-model="importForm.categoryId">
              <option value="">未分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label>关联项目
            <select v-model="importForm.projectId">
              <option value="">未关联</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label>重复标题
            <select v-model="importForm.duplicateStrategy" required>
              <option value="" disabled>请选择处理方式</option>
              <option value="SKIP_TITLE">跳过同标题</option>
              <option value="CREATE_NEW">创建新笔记</option>
            </select>
          </label>
          <label>标签
            <select v-model="importForm.tagIds" multiple>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <div class="actions wide"><button type="submit">开始导入</button></div>
        </form>

        <div v-if="importResult" class="import-result">
          <p class="meta">导入 {{ importResult.importedCount }} 条，跳过 {{ importResult.skippedCount }} 条，失败 {{ importResult.failedCount }} 条</p>
          <div class="record-list compact">
            <div v-for="item in importResult.items" :key="item.filePath" class="record-row">
              <div class="record-main"><h3>{{ item.title || item.filePath }}</h3><p>{{ item.message }}</p></div>
              <span class="badge">{{ item.status }}</span>
            </div>
          </div>
        </div>
      </div>
    </details>

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
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { categoryApi, noteApi, projectApi, tagApi } from '../api/workstation';
import AppIcon from '../components/AppIcon.vue';

const route = useRoute();
const error = ref('');
const notes = ref([]);
const categories = ref([]);
const projects = ref([]);
const tags = ref([]);
const filters = reactive({ keyword: route.query.keyword || '', categoryId: '', tagId: '' });
const importResult = ref(null);
const importForm = reactive({
  sourcePath: '',
  categoryId: '',
  projectId: '',
  tagIds: [],
  duplicateStrategy: ''
});

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
  filters.keyword = '';
  filters.categoryId = '';
  filters.tagId = '';
  load();
}

// 加载筛选项：分类、项目和标签会同时用于列表筛选与导入表单。
async function loadOptions() {
  const [categoryList, projectList, tagList] = await Promise.all([
    categoryApi.list(),
    projectApi.list({}),
    tagApi.list()
  ]);
  categories.value = categoryList;
  projects.value = projectList;
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

// 导入本机 Markdown 文件或目录，导入路径必须在后端白名单配置内。
async function importNotes() {
  try {
    error.value = '';
    importResult.value = await noteApi.importMarkdown({
      sourcePath: importForm.sourcePath,
      categoryId: importForm.categoryId ? Number(importForm.categoryId) : null,
      projectId: importForm.projectId ? Number(importForm.projectId) : null,
      tagIds: importForm.tagIds.map(Number),
      duplicateStrategy: importForm.duplicateStrategy
    });
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
</script>
