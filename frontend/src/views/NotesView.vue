<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="panel">
      <div class="toolbar">
        <input v-model="filters.keyword" placeholder="笔记标题" @keyup.enter="load" />
        <select v-model="filters.categoryId">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
        <select v-model="filters.tagId">
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
        </select>
        <button @click="load">查询</button>
        <RouterLink :to="{ name: 'note-new' }"><button>新建笔记</button></RouterLink>
      </div>
    </div>

    <section class="panel import-panel">
      <h2>导入 Markdown 笔记</h2>
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
            <option value="" disabled>请选择</option>
            <option value="SKIP_TITLE">跳过同标题</option>
            <option value="CREATE_NEW">创建新笔记</option>
          </select>
        </label>
        <label>标签
          <select v-model="importForm.tagIds" multiple>
            <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
          </select>
        </label>
        <div class="actions wide">
          <button type="submit">开始导入</button>
        </div>
      </form>

      <div v-if="importResult" class="import-result">
        <p class="meta">
          导入 {{ importResult.importedCount }} 条，跳过 {{ importResult.skippedCount }} 条，失败 {{ importResult.failedCount }} 条
        </p>
        <div class="list">
          <div v-for="item in importResult.items" :key="item.filePath" class="card">
            <div class="card-title">
              <h3>{{ item.title || item.filePath }}</h3>
              <span class="badge">{{ item.status }}</span>
            </div>
            <p class="meta">{{ item.message }}</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="notes.length" class="list">
      <div v-for="note in notes" :key="note.id" class="card">
        <div class="card-title">
          <RouterLink :to="`/notes/${note.id}/edit`"><h3>{{ note.title }}</h3></RouterLink>
          <span class="badge">{{ note.categoryName || '未分类' }}</span>
        </div>
        <p class="meta">{{ note.projectName || '未关联项目' }} · {{ formatDate(note.updatedAt) }}</p>
        <div class="badges">
          <span v-for="tag in note.tags" :key="tag.id" class="badge">{{ tag.name }}</span>
        </div>
        <div class="actions">
          <RouterLink :to="`/notes/${note.id}/edit`"><button class="secondary">编辑</button></RouterLink>
          <button class="danger" @click="remove(note.id)">删除</button>
        </div>
      </div>
    </div>
    <p v-else class="empty">暂无笔记</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { categoryApi, noteApi, projectApi, tagApi } from '../api/workstation';

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
  return value ? new Date(value).toLocaleString() : '';
}

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

async function load() {
  try {
    error.value = '';
    notes.value = await noteApi.list(filters);
  } catch (err) {
    error.value = err.message;
  }
}

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
