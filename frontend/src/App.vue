<template>
  <RouterView v-if="isLoginPage" />

  <div v-else :class="isModuleWorkspace ? 'module-workspace-shell' : 'app-shell'">
    <aside v-if="!isModuleWorkspace" class="sidebar">
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
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          class="nav-item"
          :class="{ 'router-link-active': isNavActive(item.name) }"
          active-class="route-active"
          :to="{ name: item.name }"
        >
          <AppIcon :name="item.icon" />
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

    <main :class="isModuleWorkspace ? 'module-workspace-main' : 'main'">
      <header v-if="!isModuleWorkspace && !isEditorPage" class="topbar">
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
const isEditorPage = computed(() => ['notes', 'note-new', 'note-edit'].includes(route.name));
const isModuleWorkspace = computed(() => ['notes', 'note-new', 'note-edit', 'note-import'].includes(route.name));
const ownerInitial = computed(() => auth.state.user?.username?.slice(0, 1).toUpperCase() || '知');
const navOrder = ['notes', 'projects', 'tasks', 'taxonomy', 'dashboard'];
const navItems = computed(() => navOrder.map((name) => {
  const item = routes.find((routeItem) => routeItem.name === name);
  return { name, title: item.meta.title, icon: name === 'note-import' ? 'import' : name };
}));

const pageMeta = {
  dashboard: { section: '今日工作台', description: '回到最重要的项目、任务与笔记。' },
  projects: { section: '项目资料', description: '沉淀项目背景、技术栈和推进上下文。' },
  'project-detail': { section: '项目资料', description: '查看一个项目的任务与知识记录。' },
  tasks: { section: '任务清单', description: '让正在推进的工作保持清晰。' },
  notes: { section: '个人知识库', description: '检索、整理并继续你的知识积累。' },
  'note-import': { section: '内容迁移', description: '将已有 Markdown 文档加入知识库。' },
  'note-new': { section: 'Markdown 编辑器', description: '把刚刚想到的内容写下来。' },
  'note-edit': { section: 'Markdown 编辑器', description: '在一张文稿中书写和阅读。' },
  taxonomy: { section: '知识组织', description: '维护笔记的棬索标签。' }
};

const currentMeta = computed(() => pageMeta[route.name] || { section: 'Workspace', description: '' });

function isNavActive(name) {
  if (name === 'notes') return ['notes', 'note-new', 'note-edit'].includes(route.name);
  return route.name === name;
}

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
