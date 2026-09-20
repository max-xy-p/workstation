<template>
  <section class="page tasks-page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="split workspace-split tasks-layout">
      <section class="task-board">
        <div class="panel compact-panel">
          <div class="toolbar">
            <input v-model="filters.keyword" aria-label="搜索任务" placeholder="搜索任务标题…" @keyup.enter="load" />
            <select v-model="filters.projectId">
              <option value="">全部项目</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
            <select v-model="filters.priority">
              <option value="">全部优先级</option>
              <option v-for="priority in TASK_PRIORITIES" :key="priority.value" :value="priority.value">{{ priority.label }}</option>
            </select>
            <button @click="load">筛选</button>
          </div>
        </div>

        <div class="kanban">
          <section v-for="column in KANBAN_COLUMNS" :key="column.value" class="column">
            <div class="column-heading">
              <h2>{{ column.label }}</h2>
              <span>{{ tasksByStatus(column.value).length }}</span>
            </div>
            <div v-if="tasksByStatus(column.value).length" class="list">
              <article v-for="task in tasksByStatus(column.value)" :key="task.id" class="card task-card">
                <div class="card-title">
                  <h3>{{ task.title }}</h3>
                  <span class="badge">{{ priorityLabel(task.priority) }}</span>
                </div>
                <p class="meta">{{ task.projectName }} · {{ task.dueDate || '无截止日期' }}</p>
                <p>{{ task.description || '未填写描述' }}</p>
                <div class="actions">
                  <button
                    v-for="status in KANBAN_COLUMNS"
                    :key="status.value"
                    class="secondary"
                    :disabled="status.value === task.status"
                    @click="move(task, status.value)"
                  >
                    {{ status.label }}
                  </button>
                  <button class="secondary" @click="edit(task)">编辑</button>
                  <button class="danger" @click="remove(task.id)">删除</button>
                </div>
              </article>
            </div>
            <p v-else class="empty">暂无{{ column.label }}任务</p>
          </section>
        </div>
      </section>

      <section class="panel form-panel sticky-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">{{ form.id ? '编辑模式' : '快速创建' }}</span>
            <h2>{{ form.id ? '编辑任务' : '新建任务' }}</h2>
          </div>
        </div>
        <form class="form-grid" @submit.prevent="save">
          <label class="wide">标题<input v-model="form.title" required /></label>
          <label class="wide">项目
            <select v-model="form.projectId" required>
              <option value="" disabled>请选择</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
          </label>
          <label>状态
            <select v-model="form.status" required>
              <option value="" disabled>请选择</option>
              <option v-for="status in TASK_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <label>优先级
            <select v-model="form.priority" required>
              <option value="" disabled>请选择</option>
              <option v-for="priority in TASK_PRIORITIES" :key="priority.value" :value="priority.value">{{ priority.label }}</option>
            </select>
          </label>
          <label class="wide">截止日期<input v-model="form.dueDate" type="date" /></label>
          <label class="wide">描述<textarea v-model="form.description" /></label>
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
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { projectApi, taskApi } from '../api/workstation';
import { KANBAN_COLUMNS, TASK_PRIORITIES, TASK_STATUSES, optionLabel } from '../constants/options';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';

const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const error = ref('');
const tasks = ref([]);
const projects = ref([]);
const filters = reactive({
  keyword: route.query.keyword || '',
  projectId: route.query.projectId || '',
  priority: ''
});
const form = reactive(emptyForm());

function emptyForm() {
  return { id: null, title: '', description: '', projectId: '', status: '', priority: '', dueDate: '' };
}

function priorityLabel(value) {
  return optionLabel(TASK_PRIORITIES, value);
}

// 根据看板列状态筛选任务，模板中每一列都会调用它。
function tasksByStatus(status) {
  return tasks.value.filter((task) => task.status === status);
}

// 重置任务表单，避免上一次编辑状态影响下一次新建。
function resetForm() {
  Object.assign(form, emptyForm());
}

// 把任务卡片数据填入表单，进入编辑状态。
function edit(task) {
  Object.assign(form, {
    id: task.id,
    title: task.title,
    description: task.description || '',
    projectId: task.projectId,
    status: task.status,
    priority: task.priority,
    dueDate: task.dueDate || ''
  });
}

// 把表单状态转换为后端 TaskRequest。
function payload() {
  return {
    title: form.title,
    description: form.description || null,
    projectId: Number(form.projectId),
    status: form.status,
    priority: form.priority,
    dueDate: form.dueDate || null
  };
}

// 加载项目选项，任务必须关联到一个项目。
async function loadProjects() {
  projects.value = await projectApi.listAll(workspaceId.value);
}

// 根据筛选条件重新加载看板任务。
async function load() {
  try {
    error.value = '';
    tasks.value = await taskApi.listAll(workspaceId.value, filters);
  } catch (err) {
    error.value = err.message;
  }
}

// 创建或更新任务。
async function save() {
  try {
    error.value = '';
    if (form.id) {
      await taskApi.update(workspaceId.value, form.id, payload());
    } else {
      await taskApi.create(workspaceId.value, payload());
    }
    resetForm();
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

// 快捷切换任务看板状态。
async function move(task, status) {
  try {
    error.value = '';
    await taskApi.updateStatus(workspaceId.value, task.id, status);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

// 删除任务；项目本身不会受影响。
async function remove(id) {
  if (!window.confirm('确认删除任务？')) return;
  try {
    error.value = '';
    await taskApi.delete(workspaceId.value, id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadProjects();
    await load();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
