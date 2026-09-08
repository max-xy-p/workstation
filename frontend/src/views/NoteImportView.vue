<template>
  <section class="page note-import-page">
    <p v-if="error" class="error">{{ error }}</p>

    <div class="focused-page-heading">
      <div>
        <h2>导入 Markdown</h2>
        <p>将已有文档加入知识库。</p>
      </div>
      <RouterLink class="button secondary" :to="{ name: 'notes' }">返回笔记</RouterLink>
    </div>

    <section class="import-workspace">
      <form class="form-grid" @submit.prevent="importNotes">
        <label class="wide">导入路径
          <input v-model="form.sourcePath" placeholder="例如 /data/notes 或 D:\notes" required />
        </label>
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
        <label>重复标题
          <select v-model="form.duplicateStrategy" required>
            <option value="" disabled>请选择处理方式</option>
            <option value="SKIP_TITLE">跳过同标题</option>
            <option value="CREATE_NEW">创建新笔记</option>
          </select>
        </label>
        <label>标签
          <select v-model="form.tagIds" multiple>
            <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
          </select>
        </label>
        <div class="actions wide">
          <button type="submit" :disabled="importing">{{ importing ? '导入中…' : '开始导入' }}</button>
        </div>
      </form>
    </section>

    <section v-if="result" class="import-report">
      <div class="import-report-summary">
        <span>导入 {{ result.importedCount }}</span>
        <span>跳过 {{ result.skippedCount }}</span>
        <span>失败 {{ result.failedCount }}</span>
      </div>
      <div class="record-list compact">
        <div v-for="item in result.items" :key="item.filePath" class="record-row">
          <div class="record-main"><h3>{{ item.title || item.filePath }}</h3><p>{{ item.message }}</p></div>
          <span class="badge">{{ item.status }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { categoryApi, noteApi, projectApi, tagApi } from '../api/workstation';

const error = ref('');
const importing = ref(false);
const result = ref(null);
const categories = ref([]);
const projects = ref([]);
const tags = ref([]);
const form = reactive({ sourcePath: '', categoryId: '', projectId: '', tagIds: [], duplicateStrategy: '' });

async function importNotes() {
  try {
    error.value = '';
    importing.value = true;
    result.value = await noteApi.importMarkdown({
      sourcePath: form.sourcePath,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      projectId: form.projectId ? Number(form.projectId) : null,
      tagIds: form.tagIds.map(Number),
      duplicateStrategy: form.duplicateStrategy
    });
  } catch (err) {
    error.value = err.message;
  } finally {
    importing.value = false;
  }
}

onMounted(async () => {
  try {
    [categories.value, projects.value, tags.value] = await Promise.all([
      categoryApi.list(),
      projectApi.list({}),
      tagApi.list()
    ]);
  } catch (err) {
    error.value = err.message;
  }
});
</script>
