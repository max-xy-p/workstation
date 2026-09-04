<template>
  <RouterView v-if="isLoginPage" />

  <div v-else class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">知</span>
        <span class="brand-copy">
          <strong>个人工作台</strong>
          <small>你的知识与工作</small>
        </span>
      </RouterLink>

      <div class="workspace-chip">
        <span class="status-dot"></span>
        <span>私人知识库</span>
      </div>

      <p class="nav-label">工作区</p>
      <nav class="nav" aria-label="主导航">
        <RouterLink v-for="item in navItems" :key="item.name" class="nav-item" :to="{ name: item.name }">
          <AppIcon :name="item.name" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="owner-chip">
          <span class="owner-avatar">{{ ownerInitial }}</span>
          <span>
            <strong>{{ auth.state.user?.username }}</strong>
            <small>站点所有者</small>
          </span>
        </div>
        <button class="logout-button" type="button" :disabled="loggingOut" @click="signOut">
          <AppIcon name="logout" />
          <span>{{ loggingOut ? '退出中…' : '退出登录' }}</span>
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="page-context">
          <span class="eyebrow">{{ currentMeta.section }}</span>
          <h1>{{ route.meta.title }}</h1>
          <p>{{ currentMeta.description }}</p>
        </div>
        <div class="topbar-actions">
          <RouterLink class="button topbar-action" :to="{ name: 'note-new' }">
            <AppIcon name="write" />
            <span>新建笔记</span>
          </RouterLink>
          <button class="mobile-logout" type="button" aria-label="退出登录" :disabled="loggingOut" @click="signOut">
            <AppIcon name="logout" />
          </button>
        </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { auth } from './auth';
import AppIcon from './components/AppIcon.vue';
import { routes } from './router';

const route = useRoute();
const router = useRouter();
const loggingOut = ref(false);
const isLoginPage = computed(() => route.name === 'login');
const ownerInitial = computed(() => auth.state.user?.username?.slice(0, 1).toUpperCase() || '知');
const navItems = computed(() => routes
  .filter((item) => ['dashboard', 'projects', 'tasks', 'notes', 'taxonomy'].includes(item.name))
  .map((item) => ({ name: item.name, title: item.meta.title })));

const pageMeta = {
  dashboard: { section: '今日工作台', description: '回到最重要的项目、任务与笔记。' },
  projects: { section: '项目资料', description: '沉淀项目背景、技术栈和推进上下文。' },
  'project-detail': { section: '项目资料', description: '查看一个项目的任务与知识记录。' },
  tasks: { section: '任务清单', description: '让正在推进的工作保持清晰。' },
  notes: { section: '个人知识库', description: '检索、整理并继续你的知识积累。' },
  'note-new': { section: 'Markdown 编辑器', description: '把刚刚想到的内容写下来。' },
  'note-edit': { section: 'Markdown 编辑器', description: '专注编辑，右侧即时查看最终效果。' },
  taxonomy: { section: '知识组织', description: '维护知识库的分类与标签。' }
};

const currentMeta = computed(() => pageMeta[route.name] || { section: 'Workspace', description: '' });

async function signOut() {
  loggingOut.value = true;
  try {
    await auth.logout();
  } finally {
    loggingOut.value = false;
    await router.replace({ name: 'login' });
  }
}

function handleUnauthorized() {
  if (route.name === 'login') {
    return;
  }
  const redirect = route.fullPath;
  auth.clearSession();
  router.replace({ name: 'login', query: { redirect, reason: 'expired' } });
}

onMounted(() => window.addEventListener('workstation:unauthorized', handleUnauthorized));
onBeforeUnmount(() => window.removeEventListener('workstation:unauthorized', handleUnauthorized));
</script>
