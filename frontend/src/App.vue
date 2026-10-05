<template>
  <RouterView v-if="isBarePage" />

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
        <select :value="workspace.currentId.value || ''" aria-label="切换工作空间" @change="switchWorkspace">
          <option v-for="item in workspace.state.items" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <RouterLink :to="{ name: 'workspace-settings', params: { workspaceId: workspace.currentId.value } }" aria-label="工作空间设置" title="工作空间设置">设置</RouterLink>
      </div>

      <p class="nav-label">工作区</p>
      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          class="nav-item"
          :class="{ 'router-link-active': isNavActive(item.name) }"
          active-class="route-active"
          :to="{ name: item.name, params: { workspaceId: workspace.currentId.value } }"
        >
          <AppIcon :name="item.icon" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="owner-chip">
          <span class="owner-avatar">{{ ownerInitial }}</span>
          <span>
            <strong>{{ auth.state.user?.displayName || auth.state.user?.username }}</strong>
            <small>{{ workspaceRoleLabel }}</small>
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
          <RouterLink class="button topbar-action" :to="{ name: 'note-new', params: { workspaceId: workspace.currentId.value } }">
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
          <component :is="Component" :key="route.fullPath" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { auth } from './auth';
import { workspace } from './workspace';
import AppIcon from './components/AppIcon.vue';
import { routes } from './router';

const route = useRoute();
const router = useRouter();
const loggingOut = ref(false);
const isBarePage = computed(() => ['login', 'workspace-unavailable', 'root'].includes(route.name));
const isEditorPage = computed(() => ['notes', 'note-new', 'note-edit', 'note-trash'].includes(route.name));
const isModuleWorkspace = computed(() => ['notes', 'note-new', 'note-edit', 'note-import', 'note-trash'].includes(route.name));
const ownerInitial = computed(() => auth.state.user?.username?.slice(0, 1).toUpperCase() || '知');
const workspaceRoleLabel = computed(() => ({
  OWNER: '工作空间所有者',
  ADMIN: '工作空间管理员',
  EDITOR: '编辑者',
  VIEWER: '只读成员'
}[workspace.state.current?.role] || '工作空间成员'));
const navOrder = ['integrations', 'notes', 'projects', 'tasks', 'search', 'taxonomy', 'dashboard'];
const navItems = computed(() => navOrder.map((name) => {
  const item = routes.find((routeItem) => routeItem.name === name);
  return { name, title: item.meta.title, icon: name === 'note-import' ? 'import' : name };
}));

const pageMeta = {
  integrations: { section: '项目数据', description: '连接代码托管平台，将开发情况汇总到工作台。' },
  dashboard: { section: '今日工作台', description: '回到最重要的项目、任务与笔记。' },
  projects: { section: '项目资料', description: '沉淀项目背景、技术栈和推进上下文。' },
  'project-detail': { section: '项目资料', description: '查看一个项目的任务与知识记录。' },
  tasks: { section: '任务清单', description: '让正在推进的工作保持清晰。' },
  notes: { section: '个人知识库', description: '检索、整理并继续你的知识积累。' },
  'note-import': { section: '内容迁移', description: '将已有 Markdown 文档加入知识库。' },
  'note-trash': { section: '笔记回收站', description: '恢复误删笔记或执行永久删除。' },
  'note-new': { section: 'Markdown 编辑器', description: '把刚刚想到的内容写下来。' },
  'note-edit': { section: 'Markdown 编辑器', description: '在一张文稿中书写和阅读。' },
  taxonomy: { section: '知识组织', description: '维护笔记的棬索标签。' },
  'workspace-settings': { section: '团队协作', description: '管理工作空间与成员。' },
  search: { section: '查找', description: '在当前工作空间检索项目、任务与笔记。' }
};

const currentMeta = computed(() => pageMeta[route.name] || { section: 'Workspace', description: '' });

function isNavActive(name) {
  if (name === 'notes') return ['notes', 'note-new', 'note-edit', 'note-trash'].includes(route.name);
  return route.name === name;
}

function switchWorkspace(event) {
  const workspaceId = Number(event.target.value);
  if (Number.isSafeInteger(workspaceId)) {
    void router.push({ name: 'dashboard', params: { workspaceId } });
  }
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

<style scoped>
.workspace-chip select { min-width: 0; flex: 1; width: 100%; border: 0; background: transparent; padding: 0.25rem; }
.workspace-chip a { flex: none; font-size: 0.8rem; }
</style>
