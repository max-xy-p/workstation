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
import { categoryApi, noteApi, tagApi } from '../api/workstation';

const route = useRoute();
const error = ref('');
const notes = ref([]);
const categories = ref([]);
const tags = ref([]);
const filters = reactive({ keyword: route.query.keyword || '', categoryId: '', tagId: '' });

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}

async function loadOptions() {
  categories.value = await categoryApi.list();
  tags.value = await tagApi.list();
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

onMounted(async () => {
  try {
    await loadOptions();
    await load();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
