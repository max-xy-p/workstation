<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="split">
      <section class="panel">
        <h2>分类</h2>
        <form class="form-grid" @submit.prevent="saveCategory">
          <label>名称<input v-model="categoryForm.name" required /></label>
          <label>描述<input v-model="categoryForm.description" /></label>
          <div class="actions wide">
            <button type="submit">{{ categoryForm.id ? '保存分类' : '创建分类' }}</button>
            <button type="button" class="secondary" @click="resetCategory">清空</button>
          </div>
        </form>
        <div v-if="categories.length" class="list">
          <div v-for="category in categories" :key="category.id" class="card">
            <div class="card-title">
              <h3>{{ category.name }}</h3>
            </div>
            <p class="meta">{{ category.description || '未填写描述' }}</p>
            <div class="actions">
              <button class="secondary" @click="editCategory(category)">编辑</button>
              <button class="danger" @click="removeCategory(category.id)">删除</button>
            </div>
          </div>
        </div>
        <p v-else class="empty">暂无分类</p>
      </section>

      <section class="panel">
        <h2>标签</h2>
        <form class="form-grid" @submit.prevent="saveTag">
          <label class="wide">名称<input v-model="tagForm.name" required /></label>
          <div class="actions wide">
            <button type="submit">{{ tagForm.id ? '保存标签' : '创建标签' }}</button>
            <button type="button" class="secondary" @click="resetTag">清空</button>
          </div>
        </form>
        <div v-if="tags.length" class="list">
          <div v-for="tag in tags" :key="tag.id" class="card">
            <div class="card-title">
              <h3>{{ tag.name }}</h3>
            </div>
            <div class="actions">
              <button class="secondary" @click="editTag(tag)">编辑</button>
              <button class="danger" @click="removeTag(tag.id)">删除</button>
            </div>
          </div>
        </div>
        <p v-else class="empty">暂无标签</p>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { categoryApi, tagApi } from '../api/workstation';

const error = ref('');
const categories = ref([]);
const tags = ref([]);
const categoryForm = reactive({ id: null, name: '', description: '' });
const tagForm = reactive({ id: null, name: '' });

function resetCategory() {
  Object.assign(categoryForm, { id: null, name: '', description: '' });
}

function resetTag() {
  Object.assign(tagForm, { id: null, name: '' });
}

function editCategory(category) {
  Object.assign(categoryForm, {
    id: category.id,
    name: category.name,
    description: category.description || ''
  });
}

function editTag(tag) {
  Object.assign(tagForm, { id: tag.id, name: tag.name });
}

async function load() {
  const [categoryList, tagList] = await Promise.all([categoryApi.list(), tagApi.list()]);
  categories.value = categoryList;
  tags.value = tagList;
}

async function saveCategory() {
  try {
    error.value = '';
    const payload = { name: categoryForm.name, description: categoryForm.description || null };
    if (categoryForm.id) {
      await categoryApi.update(categoryForm.id, payload);
    } else {
      await categoryApi.create(payload);
    }
    resetCategory();
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

async function saveTag() {
  try {
    error.value = '';
    const payload = { name: tagForm.name };
    if (tagForm.id) {
      await tagApi.update(tagForm.id, payload);
    } else {
      await tagApi.create(payload);
    }
    resetTag();
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

async function removeCategory(id) {
  if (!window.confirm('确认删除分类？')) return;
  try {
    error.value = '';
    await categoryApi.delete(id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

async function removeTag(id) {
  if (!window.confirm('确认删除标签？')) return;
  try {
    error.value = '';
    await tagApi.delete(id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await load();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
