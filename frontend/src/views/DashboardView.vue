<template>
  <section class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="stats">
      <div class="stat">
        <span>进行中的项目</span>
        <strong>{{ summary.activeProjectCount }}</strong>
      </div>
      <div class="stat">
        <span>待办任务</span>
        <strong>{{ summary.todoTaskCount }}</strong>
      </div>
      <div class="stat">
        <span>进行中任务</span>
        <strong>{{ summary.inProgressTaskCount }}</strong>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <input v-model="keyword" class="wide" placeholder="搜索标题" @keyup.enter="search('projects')" />
        <button @click="search('projects')">搜项目</button>
        <button class="secondary" @click="search('tasks')">搜任务</button>
        <button class="secondary" @click="search('notes')">搜笔记</button>
      </div>
    </div>

    <div class="split">
      <section class="panel">
        <h2>最近更新的项目</h2>
        <div v-if="summary.recentProjects.length" class="list">
          <RouterLink v-for="project in summary.recentProjects" :key="project.id" class="card" :to="`/projects/${project.id}`">
            <div class="card-title">
              <h3>{{ project.name }}</h3>
              <span class="badge">{{ projectStatusLabel(project.status) }}</span>
            </div>
            <p class="meta">{{ project.techStack || '未填写技术栈' }} · {{ formatDate(project.updatedAt) }}</p>
          </RouterLink>
        </div>
        <p v-else class="empty">暂无项目</p>
      </section>

      <section class="panel">
        <h2>最近更新的笔记</h2>
        <div v-if="summary.recentNotes.length" class="list">
          <RouterLink v-for="note in summary.recentNotes" :key="note.id" class="card" :to="`/notes/${note.id}/edit`">
            <div class="card-title">
              <h3>{{ note.title }}</h3>
            </div>
            <p class="meta">{{ note.categoryName || '未分类' }} · {{ formatDate(note.updatedAt) }}</p>
          </RouterLink>
        </div>
        <p v-else class="empty">暂无笔记</p>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { dashboardApi } from '../api/workstation';
import { PROJECT_STATUSES, optionLabel } from '../constants/options';

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
