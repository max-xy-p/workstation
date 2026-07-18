<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="split">
      <section class="panel">
        <div class="toolbar">
          <input v-model="filters.keyword" placeholder="项目标题" @keyup.enter="load" />
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option v-for="status in PROJECT_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
          <button @click="load">查询</button>
          <button class="secondary" @click="resetFilters">重置</button>
        </div>

        <div v-if="projects.length" class="list">
          <div v-for="project in projects" :key="project.id" class="card">
            <div class="card-title">
              <RouterLink :to="`/projects/${project.id}`"><h3>{{ project.name }}</h3></RouterLink>
              <span class="badge">{{ statusLabel(project.status) }}</span>
            </div>
            <p class="meta">{{ project.techStack || '未填写技术栈' }} · {{ formatDate(project.updatedAt) }}</p>
            <div class="actions">
              <button class="secondary" @click="edit(project)">编辑</button>
              <RouterLink :to="`/projects/${project.id}`"><button class="secondary">详情</button></RouterLink>
              <button class="danger" @click="remove(project.id)">删除</button>
            </div>
          </div>
        </div>
        <p v-else class="empty">暂无项目</p>
      </section>

      <section class="panel">
        <h2>{{ form.id ? '编辑项目' : '新建项目' }}</h2>
        <form class="form-grid" @submit.prevent="save">
          <label>名称<input v-model="form.name" required /></label>
          <label>状态
            <select v-model="form.status" required>
              <option value="" disabled>请选择</option>
              <option v-for="status in PROJECT_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <label class="wide">描述<textarea v-model="form.description" /></label>
          <label>仓库地址<input v-model="form.repositoryUrl" /></label>
          <label>技术栈<input v-model="form.techStack" /></label>
          <label class="wide">运行说明<textarea v-model="form.runGuide" /></label>
          <div class="actions wide">
            <button type="submit">{{ form.id ? '保存' : '创建' }}</button>
            <button type="button" class="secondary" @click="resetForm">清空</button>
          </div>
        </form>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { projectApi } from '../api/workstation';
import { PROJECT_STATUSES, optionLabel } from '../constants/options';

const route = useRoute();
const error = ref('');
const projects = ref([]);
const filters = reactive({ keyword: route.query.keyword || '', status: '' });
const form = reactive(emptyForm());

function emptyForm() {
  return { id: null, name: '', description: '', status: '', repositoryUrl: '', techStack: '', runGuide: '' };
}

function statusLabel(value) {
  return optionLabel(PROJECT_STATUSES, value);
}

// 格式化后端返回的 ISO 日期时间，给列表展示使用。
function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}

// 清空项目表单，退出编辑状态。
function resetForm() {
  Object.assign(form, emptyForm());
}

// 重置筛选条件并重新查询项目列表。
function resetFilters() {
  filters.keyword = '';
  filters.status = '';
  load();
}

// 把项目卡片数据填入表单，进入编辑状态。
function edit(project) {
  Object.assign(form, {
    id: project.id,
    name: project.name,
    description: project.description || '',
    status: project.status,
    repositoryUrl: project.repositoryUrl || '',
    techStack: project.techStack || '',
    runGuide: project.runGuide || ''
  });
}

// 把表单状态转换为后端 ProjectRequest。
function payload() {
  return {
    name: form.name,
    description: form.description || null,
    status: form.status,
    repositoryUrl: form.repositoryUrl || null,
    techStack: form.techStack || null,
    runGuide: form.runGuide || null
  };
}

// 根据筛选条件加载项目列表。
async function load() {
  try {
    error.value = '';
    projects.value = await projectApi.list(filters);
  } catch (err) {
    error.value = err.message;
  }
}

// 创建或更新项目。
async function save() {
  try {
    error.value = '';
    if (form.id) {
      await projectApi.update(form.id, payload());
    } else {
      await projectApi.create(payload());
    }
    resetForm();
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

// 删除项目；如果项目下存在任务，后端会拒绝并返回明确提示。
async function remove(id) {
  if (!window.confirm('确认删除项目？')) return;
  try {
    error.value = '';
    await projectApi.delete(id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
