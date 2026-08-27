<template>
  <section class="page editor-page">
    <p v-if="error" class="error">{{ error }}</p>
    <form class="editor-workspace" @submit.prevent="save">
      <div class="editor-toolbar">
        <RouterLink class="back-link" :to="{ name: 'notes' }">← 返回知识库</RouterLink>
        <div class="editor-status">
          <span>{{ characterCount }} 字</span>
          <span v-if="saveState" class="save-state">{{ saveState }}</span>
          <button type="submit">保存笔记</button>
        </div>
      </div>

      <section class="editor-meta">
        <input v-model="form.title" class="note-title-input" aria-label="笔记标题" placeholder="无标题笔记" required />
        <div class="metadata-grid">
          <label><span>分类</span>
            <select v-model="form.categoryId">
              <option value="">未分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label><span>关联项目</span>
            <select v-model="form.projectId">
              <option value="">未关联</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label><span>标签</span>
            <select v-model="form.tagIds" multiple>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
        </div>
      </section>

      <div class="editor-split">
        <section class="editor-pane source-pane">
          <div class="pane-heading"><span>Markdown</span><small>纯文本</small></div>
          <textarea v-model="form.content" required class="note-editor" aria-label="Markdown 正文" placeholder="从一个想法开始…" />
        </section>
        <section class="editor-pane preview-pane">
          <div class="pane-heading"><span>阅读预览</span><small>即时更新</small></div>
          <article v-if="form.content" class="markdown-body markdown-rendered" v-html="renderedContent"></article>
          <div v-else class="preview-empty"><span>Aa</span><p>开始输入后，排版效果会显示在这里。</p></div>
        </section>
      </div>
    </form>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
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
const saveState = ref('');
const form = reactive({ title: '', content: '', categoryId: '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = computed(() => Boolean(route.params.id));
const renderedContent = computed(() => renderMarkdown(form.content));
const characterCount = computed(() => form.content.replace(/\s/g, '').length);

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
    saveState.value = '保存中…';
    if (isEdit.value) {
      saved.value = await noteApi.update(route.params.id, payload());
    } else {
      saved.value = await noteApi.create(payload());
      router.replace({ name: 'note-edit', params: { id: saved.value.id } });
    }
    saveState.value = '已保存';
  } catch (err) {
    error.value = err.message;
    saveState.value = '';
  }
}

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    save();
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleShortcut);
  try {
    await loadOptions();
    await loadNote();
  } catch (err) {
    error.value = err.message;
  }
});

onBeforeUnmount(() => window.removeEventListener('keydown', handleShortcut));
</script>
