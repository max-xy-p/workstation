<template>
  <section class="page project-detail-page">
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="detail.project" class="split workspace-split">
      <section class="panel project-overview-panel">
        <div class="card-title">
          <h2>{{ detail.project.name }}</h2>
          <span class="badge">{{ projectStatusLabel(detail.project.status) }}</span>
        </div>
        <p>{{ detail.project.description || '未填写描述' }}</p>
        <p v-if="detail.project.homepageUrl" class="meta">项目入口：<a :href="detail.project.homepageUrl" target="_blank" rel="noopener noreferrer">{{ detail.project.homepageUrl }}</a></p>
        <ProjectDevelopment :workspace-id="workspaceId" :project-id="detail.project.id" />
        <p class="meta">仓库：{{ detail.project.repositoryUrl || '未填写' }}</p>
        <p class="meta">技术栈：{{ detail.project.techStack || '未填写' }}</p>
        <pre class="markdown-body">{{ detail.project.runGuide || '未填写运行说明' }}</pre>

        <section class="project-progress">
          <h3>项目推进</h3>
          <p>{{ completedCount }} / {{ detail.tasks.length }} 项任务已完成 · {{ detail.notes.length }} 篇项目笔记</p>
          <progress :value="completedCount" :max="Math.max(detail.tasks.length, 1)" aria-label="项目任务完成进度"></progress>
          <h4>最近更新的任务</h4>
          <ul v-if="recentTasks.length">
            <li v-for="task in recentTasks" :key="task.id">
              <RouterLink :to="{ name: 'tasks', params: workspaceParams, query: { projectId: detail.project.id, taskId: task.id } }">{{ task.title }}</RouterLink>
              · {{ taskStatusLabel(task.status) }} · {{ new Date(task.updatedAt).toLocaleString() }}
            </li>
          </ul>
          <p v-else class="empty">创建第一项任务，开始推进项目。</p>
        </section>

        <h3>关联任务</h3>
        <div v-if="detail.tasks.length" class="list">
          <div v-for="task in detail.tasks" :key="task.id" class="card">
            <div class="card-title">
              <h3>{{ task.title }}</h3>
              <span class="badge">{{ taskStatusLabel(task.status) }}</span>
            </div>
            <p class="meta">{{ priorityLabel(task.priority) }} · {{ task.dueDate || '无截止日期' }}</p>
            <RouterLink v-if="task.evidenceNoteId" :to="{ name: 'note-edit', params: { ...workspaceParams, id: task.evidenceNoteId } }">依据笔记：{{ task.evidenceNoteTitle }}</RouterLink>
          </div>
        </div>
        <p v-else class="empty">暂无任务</p>

        <h3>关联笔记</h3>
        <div v-if="detail.notes.length" class="list">
          <RouterLink v-for="note in detail.notes" :key="note.id" class="card" :to="{ name: 'note-edit', params: { ...workspaceParams, id: note.id } }">
            <h3>{{ note.title }}</h3>
            <p class="meta">{{ note.folderName || '未归档' }}</p>
          </RouterLink>
        </div>
        <p v-else class="empty">暂无笔记</p>
      </section>

      <section class="panel form-panel">
        <div class="section-heading"><div><span class="eyebrow">项目设置</span><h2>编辑项目</h2></div></div>
        <form class="form-grid" @submit.prevent="saveProject">
          <label class="wide">名称<input v-model="projectForm.name" required /></label>
          <label class="wide">状态
            <select v-model="projectForm.status" required>
              <option value="" disabled>请选择</option>
              <option v-for="status in PROJECT_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <label class="wide">描述<textarea v-model="projectForm.description" /></label>
          <label class="wide">项目入口<input v-model="projectForm.homepageUrl" type="url" maxlength="500" /></label>
          <label class="wide">仓库地址<input v-model="projectForm.repositoryUrl" /></label>
          <label class="wide">技术栈<input v-model="projectForm.techStack" /></label>
          <label class="wide">运行说明<textarea v-model="projectForm.runGuide" /></label>
          <div class="actions wide">
            <button type="submit">保存</button>
            <button type="button" class="danger" @click="removeProject">删除</button>
            <RouterLink :to="{ name: 'note-new', params: workspaceParams, query: { projectId: detail.project.id } }"><button type="button" class="secondary">新建笔记</button></RouterLink>
          </div>
        </form>

        <div class="section-heading form-section-heading"><div><span class="eyebrow">关联工作</span><h2>新建任务</h2></div></div>
        <form class="form-grid" @submit.prevent="createTask">
          <label class="wide">标题<input v-model="taskForm.title" required /></label>
          <label>状态
            <select v-model="taskForm.status" required>
              <option value="" disabled>请选择</option>
              <option v-for="status in TASK_STATUSES" :key="status.value" :value="status.value">{{ status.label }}</option>
            </select>
          </label>
          <label>优先级
            <select v-model="taskForm.priority" required>
              <option value="" disabled>请选择</option>
              <option v-for="priority in TASK_PRIORITIES" :key="priority.value" :value="priority.value">{{ priority.label }}</option>
            </select>
          </label>
          <label class="wide">截止日期<input v-model="taskForm.dueDate" type="date" /></label>
          <label class="wide">依据笔记
            <select v-model="taskForm.evidenceNoteId">
              <option value="">未关联</option>
              <option v-for="note in detail.notes" :key="note.id" :value="note.id">{{ note.title }}</option>
            </select>
          </label>
          <label class="wide">描述<textarea v-model="taskForm.description" /></label>
          <div class="actions wide"><button type="submit">创建任务</button></div>
        </form>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import ProjectDevelopment from '../components/ProjectDevelopment.vue';
import { projectApi, taskApi } from '../api/workstation';
import { PROJECT_STATUSES, TASK_PRIORITIES, TASK_STATUSES, optionLabel } from '../constants/options';
import { workspaceIdFromRoute, workspaceRouteParams } from '../utils/workspaceRoute';

const route = useRoute();
const router = useRouter();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const workspaceParams = workspaceRouteParams(route);
const error = ref('');
const detail = reactive({ project: null, tasks: [], notes: [] });
const projectForm = reactive({});
const taskForm = reactive({ title: '', description: '', status: '', priority: '', dueDate: '', evidenceNoteId: '' });
const completedCount = computed(() => detail.tasks.filter((task) => task.status === 'DONE').length);
const recentTasks = computed(() => [...detail.tasks].sort((left, right) =>
  new Date(right.updatedAt) - new Date(left.updatedAt) || right.id - left.id).slice(0, 5));

const projectStatusLabel = (value) => optionLabel(PROJECT_STATUSES, value);
const taskStatusLabel = (value) => optionLabel(TASK_STATUSES, value);
const priorityLabel = (value) => optionLabel(TASK_PRIORITIES, value);

// 把项目详情数据同步到右侧编辑表单。
function syncProjectForm(project) {
  Object.assign(projectForm, {
    name: project.name,
    description: project.description || '',
    status: project.status,
    repositoryUrl: project.repositoryUrl || '',
    homepageUrl: project.homepageUrl || '',
    techStack: project.techStack || '',
    runGuide: project.runGuide || ''
  });
}

// 把项目表单转换为后端 ProjectRequest。
function projectPayload() {
  return {
    name: projectForm.name,
    description: projectForm.description || null,
    status: projectForm.status,
    repositoryUrl: projectForm.repositoryUrl || null,
    homepageUrl: projectForm.homepageUrl || null,
    techStack: projectForm.techStack || null,
    runGuide: projectForm.runGuide || null
  };
}

// 加载项目详情，包括项目本身、关联任务和关联笔记。
async function load() {
  try {
    error.value = '';
    Object.assign(detail, await projectApi.detail(workspaceId.value, route.params.id));
    syncProjectForm(detail.project);
  } catch (err) {
    error.value = err.message;
  }
}

// 保存项目基础信息。
async function saveProject() {
  try {
    error.value = '';
    await projectApi.update(workspaceId.value, detail.project.id, projectPayload());
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

// 在当前项目下创建任务。
async function createTask() {
  try {
    error.value = '';
    await taskApi.create(workspaceId.value, {
      title: taskForm.title,
      description: taskForm.description || null,
      projectId: detail.project.id,
      status: taskForm.status,
      priority: taskForm.priority,
      dueDate: taskForm.dueDate || null,
      evidenceNoteId: taskForm.evidenceNoteId ? Number(taskForm.evidenceNoteId) : null
    });
    Object.assign(taskForm, { title: '', description: '', status: '', priority: '', dueDate: '', evidenceNoteId: '' });
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

// 删除当前项目；删除规则由后端统一处理。
async function removeProject() {
  if (!window.confirm('确认删除项目？')) return;
  try {
    error.value = '';
    await projectApi.delete(workspaceId.value, detail.project.id);
    router.push({ name: 'projects', params: workspaceParams });
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>

<style scoped>
.project-progress { border-block: 1px solid var(--border, #e5e7eb); padding: 1rem 0; margin: 1.25rem 0; }
.project-progress progress { width: 100%; height: 0.5rem; accent-color: #6f785c; }
.project-progress ul { padding-left: 1.25rem; }
.project-progress li { margin: 0.65rem 0; line-height: 1.6; }
</style>
