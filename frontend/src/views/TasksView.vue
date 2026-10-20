<template>
  <section class="page tasks-page">
    <PageActions><button v-if="canWrite" type="button" @click="createNewTask">新建任务</button></PageActions>
    <p v-if="error" class="error">{{ error }}</p>
    <div>
      <section class="task-board">
        <div class="panel compact-panel">
          <div class="toolbar">
            <input v-model="filters.keyword" aria-label="搜索任务" placeholder="搜索任务标题…" @keyup.enter="load" />
            <select v-model="filters.projectId" aria-label="筛选所属项目">
              <option value="">全部项目</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
            </select>
            <select v-model="filters.priority" aria-label="筛选任务优先级">
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
              <article v-for="task in tasksByStatus(column.value)" :key="task.id" :id="`task-${task.id}`" class="card task-card">
                <div class="card-title">
                  <h3>{{ task.title }}</h3>
                  <span class="badge">{{ priorityLabel(task.priority) }}</span>
                </div>
                <p class="meta">{{ task.projectName }} · {{ task.dueDate || '无截止日期' }}</p>
                <p>{{ task.description || '未填写描述' }}</p>
                <RouterLink v-if="task.evidenceNoteId" :to="{ name: 'note-edit', params: { workspaceId, id: task.evidenceNoteId } }">依据笔记：{{ task.evidenceNoteTitle || task.evidenceNoteId }}</RouterLink>
                <TaskIssueSource v-if="sourceForTask(task.id)" :source="sourceForTask(task.id)" :task-status="task.status" :can-write="canWrite" :busy="pendingIds.has(task.id)" @unlink="unlinkSource(task.id)" @complete="move(task, 'DONE')" />
                <div v-if="canWrite" class="actions">
                  <select :value="task.status" :aria-label="'修改任务状态：' + task.title" :disabled="pendingIds.has(task.id)" @change="move(task, $event.target.value)"><option v-for="status in KANBAN_COLUMNS" :key="status.value" :value="status.value">{{ status.label }}</option></select>
                  <button class="secondary" :disabled="pendingIds.has(task.id)" @click="edit(task)">编辑</button>
                  <button class="danger" :disabled="pendingIds.has(task.id)" @click="remove(task.id)">删除</button>
                </div>
              </article>
            </div>
            <p v-else class="empty">暂无{{ column.label }}任务</p>
          </section>
        </div>
      </section>

      <AppDrawer v-if="canWrite" v-model="opened" :title="form.id ? '编辑任务' : '新建任务'" description="设置任务的归属、优先级与依据笔记。" :busy="saving" :dirty="dirty">
        <p v-if="formError" class="error form-error" role="alert">{{ formError }}</p>
        <TaskIssueSource v-if="sourceForTask(form.id)" :source="sourceForTask(form.id)" :task-status="form.status" expanded />
        <form id="task-form" class="form-grid" @submit.prevent="save">
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
          <label class="wide">依据笔记
            <select v-model="form.evidenceNoteId">
              <option value="">未关联</option>
              <option v-for="note in evidenceNotes" :key="note.id" :value="note.id">{{ note.title }}</option>
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
        </form>
        <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" form="task-form" :disabled="saving">{{ saving ? '保存中…' : '保存任务' }}</button></template>
      </AppDrawer>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { issueApi, noteApi, projectApi, taskApi } from '../api/workstation';
import { KANBAN_COLUMNS, TASK_PRIORITIES, TASK_STATUSES, optionLabel } from '../constants/options';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';
import { workspace } from '../workspace';
import AppDrawer from '../components/AppDrawer.vue';
import PageActions from '../components/PageActions.vue';
import TaskIssueSource from '../components/TaskIssueSource.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';

const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const canWrite = computed(() => workspace.hasPermission('TASK_WRITE'));
const error = ref('');
const tasks = ref([]);
const issueSources = ref([]);
const sourceForTask = (id) => issueSources.value.find((source) => source.taskId === id);
const projects = ref([]);
const evidenceNotes = ref([]);
const filters = reactive({
  keyword: route.query.keyword || '',
  projectId: route.query.projectId || '',
  priority: ''
});
const form = reactive(emptyForm());
const pendingIds = reactive(new Set());
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(form);
function createNewTask() { resetForm(); form.projectId = filters.projectId; form.status = 'TODO'; form.priority = 'MEDIUM'; openPanel(); }

function emptyForm() {
  return { id: null, title: '', description: '', projectId: '', status: '', priority: '', dueDate: '', evidenceNoteId: '' };
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
    dueDate: task.dueDate || '',
    evidenceNoteId: task.evidenceNoteId || ''
  });
  openPanel();
}

// 把表单状态转换为后端 TaskRequest。
function payload() {
  return {
    title: form.title,
    description: form.description || null,
    projectId: Number(form.projectId),
    status: form.status,
    priority: form.priority,
    dueDate: form.dueDate || null,
    evidenceNoteId: form.evidenceNoteId ? Number(form.evidenceNoteId) : null
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
    const [loadedTasks, loadedSources] = await Promise.all([
      taskApi.listAll(workspaceId.value, filters), issueApi.listAll(workspaceId.value)
    ]);
    tasks.value = loadedTasks; issueSources.value = loadedSources;
  } catch (err) {
    error.value = err.message;
  }
}

// 创建或更新任务。
async function save() {
  if (!canWrite.value) return;
  const saved = await submit(async () => {
    if (form.id) {
      await taskApi.update(workspaceId.value, form.id, payload());
    } else {
      await taskApi.create(workspaceId.value, payload());
    }
  });
  if (saved) { resetForm(); await load(); }
}

// 快捷切换任务看板状态。
async function move(task, status) {
  if (pendingIds.has(task.id)) return;
  pendingIds.add(task.id);
  try {
    error.value = '';
    await taskApi.updateStatus(workspaceId.value, task.id, status);
    await load();
  } catch (err) {
    error.value = err.message;
  } finally {
    pendingIds.delete(task.id);
  }
}

async function unlinkSource(id) {
  if (pendingIds.has(id)) return;
  pendingIds.add(id);
  try { error.value = ''; await issueApi.unlink(workspaceId.value, id); await load(); }
  catch (err) { error.value = err.message; }
  finally { pendingIds.delete(id); }
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
    const selectedTask = tasks.value.find((task) => task.id === Number(route.query.taskId));
    if (selectedTask && canWrite.value) edit(selectedTask);
  } catch (err) {
    error.value = err.message;
  }
});

watch(() => form.projectId, async (projectId, previous, onCleanup) => {
  let active = true;
  onCleanup(() => { active = false; });
  try {
    const notes = projectId
      ? await noteApi.listAll(workspaceId.value, { projectId: Number(projectId) })
      : [];
    if (!active) return;
    evidenceNotes.value = notes;
    if (form.evidenceNoteId && !notes.some((note) => note.id === Number(form.evidenceNoteId))) {
      form.evidenceNoteId = '';
    }
  } catch (err) {
    if (active) formError.value = err.message;
  }
});
</script>

<style scoped>
.task-card .actions select { width: auto; min-height: 36px; flex: 1; }
.task-card .actions { flex-wrap: wrap; }
</style>
