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
        <input v-model="keyword" aria-label="搜索标题" placeholder="搜索项目、任务或笔记标题…" @keyup.enter="search('notes')" />
      </div>
      <div class="search-scopes">
        <button class="secondary" @click="search('projects')">项目</button>
        <button class="secondary" @click="search('tasks')">任务</button>
        <button @click="search('notes')">笔记</button>
      </div>
    </section>

    <div class="dashboard-grid">
      <section class="panel collection-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">项目</span>
            <h2>最近项目</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'projects' }">查看全部 →</RouterLink>
        </div>
        <div v-if="summary.recentProjects.length" class="record-list">
          <RouterLink v-for="(project, index) in summary.recentProjects" :key="project.id" class="record-row" :to="`/projects/${project.id}`">
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
          <RouterLink class="text-link" :to="{ name: 'notes' }">进入知识库 →</RouterLink>
        </div>
        <div v-if="summary.recentNotes.length" class="record-list">
          <RouterLink v-for="(note, index) in summary.recentNotes" :key="note.id" class="record-row" :to="`/notes/${note.id}/edit`">
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
import { RouterLink, useRouter } from 'vue-router';
import { dashboardApi } from '../api/workstation';
import { PROJECT_STATUSES, optionLabel } from '../constants/options';
import AppIcon from '../components/AppIcon.vue';

const router = useRouter();
const keyword = ref('');
const error = ref('');
const summary = reactive({
  activeProjectCount: 0,
  todoTaskCount: 0,
  inProgressTaskCount: 0,
  recentProjects: [],
  recentNotes: []
});

function projectStatusLabel(value) {
  return optionLabel(PROJECT_STATUSES, value);
}

// 格式化首页最近更新列表中的时间。
function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}

// 根据入口按钮跳转到对应模块，并把关键字带到目标列表页。
function search(target) {
  router.push({ name: target, query: { keyword: keyword.value } });
}

// 加载首页统计数字和最近更新列表。
async function load() {
  try {
    error.value = '';
    Object.assign(summary, await dashboardApi.summary());
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(load);
</script>
