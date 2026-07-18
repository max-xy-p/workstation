<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="split">
      <section class="panel">
        <form class="form-grid" @submit.prevent="save">
          <label class="wide">标题<input v-model="form.title" required /></label>
          <label>分类
            <select v-model="form.categoryId">
              <option value="">未分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label>关联项目
            <select v-model="form.projectId">
              <option value="">未关联</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label class="wide">标签
            <select v-model="form.tagIds" multiple>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <label class="wide">Markdown 正文<textarea v-model="form.content" required class="note-editor" /></label>
          <div class="actions wide">
            <button type="submit">保存</button>
            <RouterLink :to="{ name: 'notes' }"><button type="button" class="secondary">返回</button></RouterLink>
          </div>
        </form>
      </section>

      <section class="panel">
        <h2>保存内容</h2>
        <pre class="markdown-body">{{ savedContent }}</pre>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { categoryApi, noteApi, projectApi, tagApi } from '../api/workstation';

const route = useRoute();
const router = useRouter();
const error = ref('');
const categories = ref([]);
const projects = ref([]);
const tags = ref([]);
const saved = ref(null);
const form = reactive({ title: '', content: '', categoryId: '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = computed(() => Boolean(route.params.id));
const savedContent = computed(() => saved.value?.content || form.content || '');

function payload() {
  return {
    title: form.title,
    content: form.content,
    categoryId: form.categoryId ? Number(form.categoryId) : null,
    projectId: form.projectId ? Number(form.projectId) : null,
    tagIds: form.tagIds.map(Number)
  };
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

async function loadNote() {
  if (!isEdit.value) return;
  const note = await noteApi.get(route.params.id);
  saved.value = note;
  Object.assign(form, {
    title: note.title,
    content: note.content,
    categoryId: note.categoryId || '',
    projectId: note.projectId || '',
    tagIds: note.tags.map((tag) => tag.id)
  });
}

async function save() {
  try {
    error.value = '';
    if (isEdit.value) {
      saved.value = await noteApi.update(route.params.id, payload());
    } else {
      saved.value = await noteApi.create(payload());
      router.replace({ name: 'note-edit', params: { id: saved.value.id } });
    }
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadOptions();
    await loadNote();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
