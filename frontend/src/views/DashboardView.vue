<template>
  <section class="page dashboard-page">
    <p v-if="error" class="error">{{ error }}</p>
    <section class="dashboard-lead">
      <div class="lead-copy">
        <span class="eyebrow">工作概览</span>
        <h2>继续上次的进度</h2>
        <p>从最近更新的项目和笔记继续，也可以直接检索标题。</p>
      </div>
      <div class="stats" aria-label="工作概览">
        <div class="stat">
          <span>活跃项目</span>
          <strong>{{ summary.activeProjectCount }}</strong>
          <small>正在进行</small>
        </div>
        <div class="stat">
          <span>待办任务</span>
          <strong>{{ summary.todoTaskCount }}</strong>
          <small>等待处理</small>
        </div>
        <div class="stat">
          <span>进行中</span>
          <strong>{{ summary.inProgressTaskCount }}</strong>
          <small>保持推进</small>
        </div>
      </div>
    </section>

    <section class="quick-search">
      <div class="search-field">
        <AppIcon name="search" />
        <input v-model="keyword" aria-label="搜索工作空间" placeholder="搜索项目、任务或笔记内容…" @keyup.enter="search('search')" />
      </div>
      <div class="search-scopes">
        <button @click="search('search')">全部</button>
        <button class="secondary" @click="search('projects')">项目</button>
        <button class="secondary" @click="search('tasks')">任务</button>
        <button @click="search('notes')">笔记</button>
      </div>
    </section>

    <section class="panel collection-panel">
      <div class="card-title"><h2>开发数据快照</h2><RouterLink :to="{ name: 'integrations', params: workspaceParams }">数据源设置</RouterLink></div>
      <p v-if="developmentError" class="error">{{ developmentError }}</p>
      <p v-if="!development.items.length" class="empty">连接 GitHub 或 GitLab 后，可在这里查看已关联仓库。</p>
      <div v-for="r in development.items" :key="r.id" class="record-row"><RouterLink :to="{ name: 'project-detail', params: { ...workspaceParams, id: r.projectId } }">{{ r.provider }} · {{ r.namespace }}/{{ r.name }}</RouterLink><span class="meta">近期采集的开放 Issue：{{ r.collectedOpenIssues ?? '未采集' }} · PR/MR：{{ r.collectedOpenChangeRequests ?? '未采集' }} · 最近流水线：{{ pipelineLabel(r.latestPipelineStatus) }} · 资料同步：{{ formatDate(r.lastSyncedAt) }} · {{ r.lastError || (r.connectionId ? '已连接' : '已断开') }}</span></div>
      <nav class="pagination" aria-label="开发快照分页"><button class="secondary" :disabled="development.page === 0" @click="loadDevelopment(development.page - 1)">上一页</button><button class="secondary" :disabled="!development.hasMore" @click="loadDevelopment(development.page + 1)">下一页</button></nav>
    </section>
    <div class="dashboard-grid">
      <section class="panel collection-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">项目</span>
            <h2>最近项目</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'projects', params: workspaceParams }">查看全部 →</RouterLink>
        </div>
        <div v-if="summary.recentProjects.length" class="record-list">
          <RouterLink v-for="(project, index) in summary.recentProjects" :key="project.id" class="record-row" :to="{ name: 'project-detail', params: { ...workspaceParams, id: project.id } }">
            <span class="record-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="record-main">
              <h3>{{ project.name }}</h3>
              <p>{{ project.techStack || '技术栈待补充' }} · {{ formatDate(project.updatedAt) }}</p>
            </div>
            <span class="badge">{{ projectStatusLabel(project.status) }}</span>
          </RouterLink>
        </div>
        <p v-else class="empty">还没有项目，从记录一个正在做的事情开始。</p>
      </section>

      <section class="panel collection-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">笔记</span>
            <h2>最近笔记</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'notes', params: workspaceParams }">进入知识库 →</RouterLink>
        </div>
        <div v-if="summary.recentNotes.length" class="record-list">
          <RouterLink v-for="(note, index) in summary.recentNotes" :key="note.id" class="record-row" :to="{ name: 'note-edit', params: { ...workspaceParams, id: note.id } }">
            <span class="record-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="record-main">
              <h3>{{ note.title }}</h3>
              <p>{{ note.folderName || '未归档' }} · {{ formatDate(note.updatedAt) }}</p>
            </div>
            <span class="row-arrow">→</span>
          </RouterLink>
        </div>
        <p v-else class="empty">知识库还是空的，先写下第一条笔记。</p>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { integrationApi, dashboardApi } from '../api/workstation';
import { PROJECT_STATUSES, optionLabel } from '../constants/options';
import AppIcon from '../components/AppIcon.vue';
import { workspaceIdFromRoute, workspaceRouteParams } from '../utils/workspaceRoute';

const route = useRoute();
const router = useRouter();
const workspaceParams = workspaceRouteParams(route);
const keyword = ref('');
const error = ref('');
const developmentError = ref('');
const development = reactive({ items: [], page: 0, hasMore: false });
async function loadDevelopment(page = 0) {
  try { developmentError.value = ''; Object.assign(development, await integrationApi.workspaceDevelopment(workspaceIdFromRoute(route), page, 5)); }
  catch (err) { developmentError.value = err.message; }
}
const summary = reactive({
  activeProjectCount: 0,
  todoTaskCount: 0,
  inProgressTaskCount: 0,
  recentProjects: [],
  recentNotes: []
});

const pipelineLabel = (value) => ({ SUCCESS: '成功', SKIPPED: '已跳过', NEUTRAL: '中性结果', FAILED: '失败', CANCELLED: '取消', RUNNING: '运行中', PENDING: '等待', UNKNOWN: '未知' }[value] || '未采集');

function projectStatusLabel(value) {
  return optionLabel(PROJECT_STATUSES, value);
}

// 格式化首页最近更新列表中的时间。
function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}

// 根据入口按钮跳转到对应模块，并把关键字带到目标列表页。
function search(target) {
  router.push({ name: target, params: workspaceParams, query: { keyword: keyword.value } });
}

// 加载首页统计数字和最近更新列表。
async function load() {
  try {
    error.value = '';
    Object.assign(summary, await dashboardApi.summary(workspaceIdFromRoute(route)));
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(() => { load(); loadDevelopment(); });
</script>
