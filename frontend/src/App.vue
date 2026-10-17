<template>
  <RouterView v-if="isBarePage" />
  <div v-else class="app-shell" :class="{ 'app-notes': notesPage, 'app-focused': notesPage && focused }">
    <a class="skip-link" href="#workspace-main">跳到主要内容</a>
    <aside class="sidebar">
      <RouterLink class="brand" to="/" title="返回概览">
        <span class="brand-mark">知</span><span class="brand-copy"><strong>Workstation</strong><small>知识与项目工作台</small></span>
      </RouterLink>
      <WorkspaceSwitcher class="sidebar-space-switcher" />
      <nav class="nav" aria-label="主导航">
        <template v-for="group in navigationGroups" :key="group.title">
          <p class="nav-label">{{ group.title }}</p>
          <RouterLink v-for="item in group.items" :key="item.name" class="nav-item" :class="{ 'router-link-active': isNavigationActive(item.name, route.name) }" active-class="route-active" :aria-current="isNavigationActive(item.name, route.name) ? 'page' : undefined" :title="item.title" :to="{ name: item.name, params: workspaceParams }">
            <AppIcon :name="item.name" /><span>{{ item.title }}</span>
          </RouterLink>
        </template>
      </nav>
      <div class="sidebar-foot">
        <div class="owner-chip"><span class="owner-avatar">{{ ownerInitial }}</span><span><strong>{{ auth.state.user?.displayName || auth.state.user?.username }}</strong><small>{{ workspaceRoleLabel }}</small></span></div>
        <button class="logout-button" type="button" :disabled="loggingOut" @click="signOut"><AppIcon name="logout" /><span>{{ loggingOut ? '退出中…' : '退出登录' }}</span></button>
      </div>
    </aside>
    <main id="workspace-main" class="main" tabindex="-1">
      <header class="topbar">
        <div class="page-context"><h1>{{ notesPage ? '笔记' : route.meta.title }}</h1><p>{{ currentDescription }}</p></div>
        <div class="topbar-actions">
          <WorkspaceSwitcher class="mobile-space-switcher" />
          <div id="page-actions"></div>
          <RouterLink v-if="route.name === 'dashboard' && workspace.hasPermission('NOTE_WRITE')" class="button" :to="{ name: 'note-new', params: workspaceParams }"><AppIcon name="write" /><span>新建笔记</span></RouterLink>
          <button class="mobile-logout secondary" type="button" aria-label="退出登录" :disabled="loggingOut" @click="signOut"><AppIcon name="logout" /></button>
        </div>
      </header>
      <RouterView v-slot="{ Component }"><component :is="Component" :key="pageViewKey(route)" /></RouterView>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import WorkspaceSwitcher from './components/WorkspaceSwitcher.vue';
import AppIcon from './components/AppIcon.vue';
import { auth } from './auth';
import { workspace } from './workspace';
import { isNavigationActive, isNotesRoute, pageViewKey } from './utils/pageLayout.mjs';

const route = useRoute();
const router = useRouter();
const loggingOut = ref(false);
const focused = ref(false);
provide('notesFocus', focused);
const notesPage = computed(() => isNotesRoute(route.name));
const isBarePage = computed(() => ['login', 'workspace-unavailable', 'root'].includes(route.name));
const workspaceParams = computed(() => ({ workspaceId: workspace.currentId.value }));
const ownerInitial = computed(() => auth.state.user?.username?.slice(0, 1).toUpperCase() || '知');
const workspaceRoleLabel = computed(() => ({ OWNER: '所有者', ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' }[workspace.state.current?.role] || '工作空间成员'));
const navigationGroups = [
  { title: '工作', items: [{ name: 'dashboard', title: '概览' }, { name: 'projects', title: '项目' }, { name: 'tasks', title: '任务' }, { name: 'notes', title: '笔记' }, { name: 'search', title: '搜索' }] },
  { title: '管理', items: [{ name: 'integrations', title: '数据源' }, { name: 'taxonomy', title: '标签' }, { name: 'workspace-settings', title: '空间设置' }] }
];
const descriptions = {
  dashboard: '继续你的项目、任务与知识记录。', projects: '整理项目资料，查看推进情况。',
  'project-detail': '项目资料、开发情况与关联工作。', tasks: '按状态组织当前空间的工作。',
  notes: '整理、阅读和编辑工作中的知识。', integrations: '管理代码托管连接与同步状态。',
  'integration-detail': '查看仓库、执行记录和连接设置。', taxonomy: '用标签串联你的知识。',
  search: '查找当前空间的项目、任务和笔记。', 'workspace-settings': '管理空间信息与协作成员。'
};
const currentDescription = computed(() => descriptions[notesPage.value ? 'notes' : route.name] || '');
watch(() => [route.name, route.params.workspaceId], () => { if (!notesPage.value) focused.value = false; });

async function signOut() {
  loggingOut.value = true;
  try { await auth.logout(); }
  finally { loggingOut.value = false; await router.replace({ name: 'login' }); }
}
function handleUnauthorized() {
  if (route.name === 'login') return;
  const redirect = route.fullPath;
  auth.clearSession();
  void router.replace({ name: 'login', query: { redirect, reason: 'expired' } });
}
onMounted(() => window.addEventListener('workstation:unauthorized', handleUnauthorized));
onBeforeUnmount(() => window.removeEventListener('workstation:unauthorized', handleUnauthorized));
</script>
