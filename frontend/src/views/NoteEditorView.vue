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
          <div class="pane-heading">
            <span>Markdown</span>
            <div class="markdown-tools" aria-label="Markdown 格式工具">
              <button type="button" title="二级标题" aria-label="二级标题" @click="insertMarkdown('heading')">H</button>
              <button type="button" title="加粗" aria-label="加粗" @click="insertMarkdown('bold')"><strong>B</strong></button>
              <button type="button" title="链接" aria-label="链接" @click="insertMarkdown('link')">↗</button>
              <button type="button" title="行内代码" aria-label="行内代码" @click="insertMarkdown('code')">&lt;/&gt;</button>
              <button type="button" title="代码块" aria-label="代码块" @click="insertMarkdown('codeBlock')">{ }</button>
              <button type="button" title="引用" aria-label="引用" @click="insertMarkdown('quote')">❝</button>
              <button type="button" title="无序列表" aria-label="无序列表" @click="insertMarkdown('list')">≡</button>
            </div>
            <small>纯文本</small>
          </div>
          <textarea ref="editor" v-model="form.content" required class="note-editor" aria-label="Markdown 正文" placeholder="从一个想法开始…" />
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
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
const editor = ref(null);
const form = reactive({ title: '', content: '', categoryId: '', projectId: route.query.projectId || '', tagIds: [] });
const isEdit = computed(() => Boolean(route.params.id));
const renderedContent = computed(() => renderMarkdown(form.content));
const characterCount = computed(() => form.content.replace(/\s/g, '').length);
const draftKey = computed(() => `workstation:note-draft:${route.params.id || 'new'}`);
const canSave = computed(() => form.title.trim().length > 0 && form.content.trim().length > 0);
let autoSaveTimer;
let draftTimer;
let saving = false;
let saveQueued = false;
let hydrating = true;
let skipDraftPersistOnUnmount = false;

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
  await persist(true);
}

async function persist(manual = false) {
  if (!canSave.value || saving) {
    if (!manual && saving) saveQueued = true;
    return;
  }

  const snapshot = formSnapshot();
  try {
    error.value = '';
    saving = true;
    saveState.value = manual ? '保存中…' : '自动保存中…';
    if (isEdit.value) {
      saved.value = await noteApi.update(route.params.id, payload());
    } else {
      saved.value = await noteApi.create(payload());
      router.replace({ name: 'note-edit', params: { id: saved.value.id } });
    }
    if (snapshot === formSnapshot()) {
      clearDraft();
      skipDraftPersistOnUnmount = true;
    }
    saveState.value = manual ? '已保存' : '已自动保存';
  } catch (err) {
    error.value = err.message;
    saveState.value = '';
  } finally {
    saving = false;
    if (saveQueued) {
      saveQueued = false;
      scheduleAutoSave();
    }
  }
}

function formSnapshot() {
  return JSON.stringify({
    title: form.title,
    content: form.content,
    categoryId: form.categoryId,
    projectId: form.projectId,
    tagIds: [...form.tagIds]
  });
}

function saveDraft() {
  try {
    if (!form.title.trim() && !form.content.trim() && !form.categoryId && !form.projectId && form.tagIds.length === 0) {
      clearDraft();
      return;
    }
    window.localStorage.setItem(draftKey.value, formSnapshot());
  } catch {
    // 本地存储不可用时，正常的手动保存流程仍然可用。
  }
}

function clearDraft() {
  try {
    window.localStorage.removeItem(draftKey.value);
  } catch {
    // 本地存储不可用时，不影响服务端保存。
  }
}

function restoreDraft() {
  let draft;
  try {
    draft = window.localStorage.getItem(draftKey.value);
  } catch {
    return;
  }
  if (!draft) return;
  if (!window.confirm('发现未保存的本地草稿，是否恢复？')) {
    clearDraft();
    return;
  }

  try {
    const parsed = JSON.parse(draft);
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.tagIds)) throw new Error('草稿格式无效');
    Object.assign(form, {
      title: typeof parsed.title === 'string' ? parsed.title : '',
      content: typeof parsed.content === 'string' ? parsed.content : '',
      categoryId: parsed.categoryId || '',
      projectId: parsed.projectId || '',
      tagIds: parsed.tagIds
    });
    saveState.value = '已恢复本地草稿';
  } catch {
    clearDraft();
  }
}

function scheduleDraft() {
  window.clearTimeout(draftTimer);
  draftTimer = window.setTimeout(saveDraft, 350);
}

function scheduleAutoSave() {
  if (!isEdit.value || !canSave.value) return;
  window.clearTimeout(autoSaveTimer);
  autoSaveTimer = window.setTimeout(() => persist(false), 900);
}

function insertMarkdown(type) {
  const textarea = editor.value;
  const start = textarea?.selectionStart ?? form.content.length;
  const end = textarea?.selectionEnd ?? start;
  const selection = form.content.slice(start, end);
  const selectedLines = selection || '列表项';
  const formats = {
    heading: { value: `## ${selection || '标题'}`, selectionStart: 3, selectionEnd: 3 + (selection || '标题').length },
    bold: { value: `**${selection || '粗体文本'}**`, selectionStart: 2, selectionEnd: 2 + (selection || '粗体文本').length },
    link: { value: `[${selection || '链接文字'}](https://)`, selectionStart: 1, selectionEnd: 1 + (selection || '链接文字').length },
    code: { value: `\`${selection || '代码'}\``, selectionStart: 1, selectionEnd: 1 + (selection || '代码').length },
    codeBlock: { value: `\n\`\`\`\n${selection || '代码'}\n\`\`\`\n`, selectionStart: 5, selectionEnd: 5 + (selection || '代码').length },
    quote: { value: `> ${selection || '引用内容'}`, selectionStart: 2, selectionEnd: 2 + (selection || '引用内容').length },
    list: { value: selectedLines.split('\n').map((line) => `- ${line}`).join('\n'), selectionStart: 0, selectionEnd: selectedLines.split('\n').map((line) => `- ${line}`).join('\n').length }
  };
  const format = formats[type];
  const content = `${form.content.slice(0, start)}${format.value}${form.content.slice(end)}`;
  form.content = content;

  nextTick(() => {
    const selectionStart = start + format.selectionStart;
    editor.value?.focus();
    editor.value?.setSelectionRange(selectionStart, start + format.selectionEnd);
  });
}

watch(form, () => {
  if (hydrating) return;
  skipDraftPersistOnUnmount = false;
  scheduleDraft();
  scheduleAutoSave();
}, { deep: true });

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
    restoreDraft();
  } catch (err) {
    error.value = err.message;
  } finally {
    hydrating = false;
  }
});

onBeforeUnmount(() => {
  window.clearTimeout(autoSaveTimer);
  window.clearTimeout(draftTimer);
  if (!hydrating && !skipDraftPersistOnUnmount) saveDraft();
  window.removeEventListener('keydown', handleShortcut);
});
</script>
