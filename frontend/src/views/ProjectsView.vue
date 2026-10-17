<template>
  <section class="page projects-page">
    <PageActions><button v-if="canWrite" type="button" @click="createProject">新建项目</button></PageActions>
    <p v-if="error" class="error">{{ error }}</p>
    <div>
      <section class="panel collection-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">项目列表</span>
            <h2>正在维护的项目</h2>
          </div>
          <span class="section-count">{{ pagination.total }} 个</span>
        </div>
        <div class="toolbar">
          <input v-model="filters.keyword" aria-label="搜索项目" placeholder="搜索项目名称…" @keyup.enter="load(0)" />
          <select v-model="filters.status" aria-label="筛选项目状态">
            <option value="">全部状态</option>
            <option v-for="status in PROJECT_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
          </select>
          <button @click="load(0)">筛选</button>
          <button class="secondary" @click="resetFilters">重置</button>
        </div>

        <div v-if="projects.length" class="entity-collection">
          <article v-for="project in projects" :key="project.id" class="card entity-card">
            <div class="card-title">
              <RouterLink :to="{ name: 'project-detail', params: { ...workspaceParams, id: project.id } }"><h3>{{ project.name }}</h3></RouterLink>
              <span class="badge">{{ statusLabel(project.status) }}</span>
            </div>
            <p class="card-description">{{ project.description || '还没有补充项目描述。' }}</p>
            <p class="meta">{{ project.techStack || '未填写技术栈' }} · {{ formatDate(project.updatedAt) }}</p>
            <div class="actions">
              <RouterLink class="button secondary" :to="{ name: 'project-detail', params: { ...workspaceParams, id: project.id } }">查看详情</RouterLink>
              <button v-if="canWrite" class="secondary" :disabled="saving" @click="edit(project)">编辑</button>
              <button v-if="canWrite" class="danger" :disabled="saving" @click="remove(project.id)">删除</button>
            </div>
          </article>
        </div>
        <p v-else class="empty">暂无项目</p>
        <nav v-if="pagination.total > pagination.size" class="pagination" aria-label="项目分页">
          <button class="secondary" :disabled="pagination.page === 0" @click="load(pagination.page - 1)">上一页</button>
          <span>第 {{ pagination.page + 1 }} 页，共 {{ Math.ceil(pagination.total / pagination.size) }} 页</span>
          <button class="secondary" :disabled="!pagination.hasMore" @click="load(pagination.page + 1)">下一页</button>
        </nav>
      </section>

      <AppDrawer v-model="opened" :title="form.id ? '编辑项目' : '新建项目'" description="维护项目资料，任务与笔记在项目详情中查看。" :busy="saving" :dirty="dirty">
        <p v-if="formError" class="error form-error" role="alert">{{ formError }}</p>
        <form id="project-form" class="form-grid" @submit.prevent="save">
          <label>名称<input v-model="form.name" required /></label>
          <label>状态
            <select v-model="form.status" required>
              <option value="" disabled>请选择</option>
              <option v-for="status in PROJECT_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <label class="wide">描述<textarea v-model="form.description" /></label>
          <label>项目入口<input v-model="form.homepageUrl" type="url" maxlength="500" /></label>
          <label>仓库地址<input v-model="form.repositoryUrl" /></label>
          <label>技术栈<input v-model="form.techStack" /></label>
          <label class="wide">运行说明<textarea v-model="form.runGuide" /></label>
        </form>
        <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" form="project-form" :disabled="saving">{{ saving ? '保存中…' : '保存项目' }}</button></template>
      </AppDrawer>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { projectApi } from '../api/workstation';
import { PROJECT_STATUSES, optionLabel } from '../constants/options';
import { workspaceIdFromRoute, workspaceRouteParams } from '../utils/workspaceRoute';
import { workspace } from '../workspace';
import AppDrawer from '../components/AppDrawer.vue';
import PageActions from '../components/PageActions.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';

const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const workspaceParams = workspaceRouteParams(route);
const error = ref('');
const projects = ref([]);
const pagination = reactive({ page: 0, size: 20, total: 0, hasMore: false });
const filters = reactive({ keyword: route.query.keyword || '', status: '' });
const form = reactive(emptyForm());
const canWrite = computed(() => workspace.hasPermission('PROJECT_WRITE'));
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(form);
function createProject() { resetForm(); openPanel(); }

function emptyForm() {
  return { id: null, name: '', description: '', status: '', repositoryUrl: '', techStack: '', runGuide: '', homepageUrl: '' };
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
async function edit(project) {
  try {
    error.value = '';
    const detail = await projectApi.detail(workspaceId.value, project.id);
    project = detail.project;
  } catch (err) {
    error.value = err.message;
    return;
  }
  Object.assign(form, {
    id: project.id,
    name: project.name,
    description: project.description || '',
    status: project.status,
    repositoryUrl: project.repositoryUrl || '',
    homepageUrl: project.homepageUrl || '',
    techStack: project.techStack || '',
    runGuide: project.runGuide || ''
  });
  openPanel();
}

// 把表单状态转换为后端 ProjectRequest。
function payload() {
  return {
    name: form.name,
    description: form.description || null,
    status: form.status,
    repositoryUrl: form.repositoryUrl || null,
    homepageUrl: form.homepageUrl || null,
    techStack: form.techStack || null,
    runGuide: form.runGuide || null
  };
}

// 根据筛选条件加载项目列表。
async function load(page = 0) {
  try {
    error.value = '';
    const result = await projectApi.list(
      workspaceId.value,
      { ...filters, page, size: pagination.size }
    );
    if (!result.items.length && result.total > 0 && page > 0) {
      await load(page - 1);
      return;
    }
    projects.value = result.items;
    Object.assign(pagination, {
      page: result.page,
      size: result.size,
      total: result.total,
      hasMore: result.hasMore
    });
  } catch (err) {
    error.value = err.message;
  }
}

// 创建或更新项目。
async function save() {
  if (!canWrite.value) return;
  const saved = await submit(async () => {
    if (form.id) {
      await projectApi.update(workspaceId.value, form.id, payload());
    } else {
      await projectApi.create(workspaceId.value, payload());
    }
  });
  if (saved) { resetForm(); await load(pagination.page); }
}

// 删除项目；如果项目下存在任务，后端会拒绝并返回明确提示。
async function remove(id) {
  if (!window.confirm('确认删除项目？')) return;
  try {
    error.value = '';
    await projectApi.delete(workspaceId.value, id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(() => load(0));
</script>
