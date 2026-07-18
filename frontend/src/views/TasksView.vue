<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="split">
      <section>
        <div class="panel">
          <div class="toolbar">
            <input v-model="filters.keyword" placeholder="任务标题" @keyup.enter="load" />
            <select v-model="filters.projectId">
              <option value="">全部项目</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
            <select v-model="filters.priority">
              <option value="">全部优先级</option>
              <option v-for="priority in TASK_PRIORITIES" :key="priority.value" :value="priority.value">{{ priority.label }}</option>
            </select>
            <button @click="load">查询</button>
          </div>
        </div>

        <div class="kanban">
          <section v-for="column in KANBAN_COLUMNS" :key="column.value" class="column">
            <h2>{{ column.label }}</h2>
            <div v-if="tasksByStatus(column.value).length" class="list">
              <div v-for="task in tasksByStatus(column.value)" :key="task.id" class="card">
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
              </div>
            </div>
            <p v-else class="empty">暂无{{ column.label }}任务</p>
          </section>
        </div>
      </section>

      <section class="panel">
        <h2>{{ form.id ? '编辑任务' : '新建任务' }}</h2>
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
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { projectApi, taskApi } from '../api/workstation';
import { KANBAN_COLUMNS, TASK_PRIORITIES, TASK_STATUSES, optionLabel } from '../constants/options';

const route = useRoute();
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

function tasksByStatus(status) {
  return tasks.value.filter((task) => task.status === status);
}

function resetForm() {
  Object.assign(form, emptyForm());
}

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

async function loadProjects() {
  projects.value = await projectApi.list({});
}

async function load() {
  try {
    error.value = '';
    tasks.value = await taskApi.list(filters);
  } catch (err) {
    error.value = err.message;
  }
}

async function save() {
  try {
    error.value = '';
    if (form.id) {
      await taskApi.update(form.id, payload());
    } else {
      await taskApi.create(payload());
    }
    resetForm();
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

async function move(task, status) {
  try {
    error.value = '';
    await taskApi.updateStatus(task.id, status);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

async function remove(id) {
  if (!window.confirm('确认删除任务？')) return;
  try {
    error.value = '';
    await taskApi.delete(id);
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
