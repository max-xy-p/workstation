<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">知</span>
        <span class="brand-copy">
          <strong>个人工作台</strong>
          <small>Knowledge desk</small>
        </span>
      </RouterLink>

      <div class="workspace-chip">
        <span class="status-dot"></span>
        <span>本地知识库</span>
      </div>

      <p class="nav-label">工作区</p>
      <nav class="nav" aria-label="主导航">
        <RouterLink v-for="item in navItems" :key="item.name" class="nav-item" :to="{ name: item.name }">
          <AppIcon :name="item.name" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <p>资料保存在本机</p>
        <span>workstation / private</span>
      </div>
    </aside>
    <main class="main">
      <header class="topbar">
        <div class="page-context">
          <span class="eyebrow">{{ currentMeta.section }}</span>
          <h1>{{ route.meta.title }}</h1>
          <p>{{ currentMeta.description }}</p>
        </div>
        <RouterLink class="button topbar-action" :to="{ name: 'note-new' }">
          <AppIcon name="write" />
          <span>新建笔记</span>
        </RouterLink>
      </header>
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import AppIcon from './components/AppIcon.vue';
import { routes } from './router';

const route = useRoute();
const navItems = computed(() => routes.filter((item) => ['dashboard', 'projects', 'tasks', 'notes', 'taxonomy'].includes(item.name))
  .map((item) => ({ name: item.name, title: item.meta.title })));

const pageMeta = {
  dashboard: { section: 'Overview', description: '回到最重要的项目、任务与笔记。' },
  projects: { section: 'Projects', description: '沉淀项目背景、技术栈和推进上下文。' },
  'project-detail': { section: 'Project', description: '查看一个项目的任务与知识记录。' },
  tasks: { section: 'Tasks', description: '让正在推进的工作保持清晰。' },
  notes: { section: 'Library', description: '检索、整理并继续你的知识积累。' },
  'note-new': { section: 'Editor', description: '把刚刚想到的内容写下来。' },
  'note-edit': { section: 'Editor', description: '专注编辑，右侧即时查看最终效果。' },
  taxonomy: { section: 'Organize', description: '维护知识库的分类与标签。' }
};

const currentMeta = computed(() => pageMeta[route.name] || { section: 'Workspace', description: '' });
</script>
