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
        <h2>Markdown 预览</h2>
        <article class="markdown-body markdown-rendered" v-html="renderedContent"></article>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { categoryApi, noteApi, projectApi, tagApi } from '../api/workstation';
import { renderMarkdown } from '../utils/markdown';

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
const renderedContent = computed(() => renderMarkdown(savedContent.value));

// 把表单状态转换成后端 NoteRequest 需要的 JSON 结构。
function payload() {
  return {
    title: form.title,
    content: form.content,
    categoryId: form.categoryId ? Number(form.categoryId) : null,
    projectId: form.projectId ? Number(form.projectId) : null,
    tagIds: form.tagIds.map(Number)
  };
}

// 加载分类、项目、标签选项，供笔记元数据选择使用。
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

// 编辑模式下加载已有笔记，并把后端数据回填到表单。
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

// 保存笔记：新建成功后切换到编辑路由，后续保存走更新接口。
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
