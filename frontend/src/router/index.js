import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '../views/DashboardView.vue';
import ProjectDetailView from '../views/ProjectDetailView.vue';
import ProjectsView from '../views/ProjectsView.vue';
import TasksView from '../views/TasksView.vue';
import NotesView from '../views/NotesView.vue';
import TaxonomyView from '../views/TaxonomyView.vue';
import LoginView from '../views/LoginView.vue';
import WorkspaceUnavailableView from '../views/WorkspaceUnavailableView.vue';
import WorkspaceSettingsView from '../views/WorkspaceSettingsView.vue';
import SearchView from '../views/SearchView.vue';
import { auth } from '../auth';
import { workspace } from '../workspace';

export const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { title: '登录', public: true } },
  { path: '/', name: 'root', component: WorkspaceUnavailableView, meta: { title: '工作空间' } },
  { path: '/workspace-unavailable', name: 'workspace-unavailable', component: WorkspaceUnavailableView, meta: { title: '工作空间不可用' } },
  { path: '/w/:workspaceId/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '概览' } },
  { path: '/w/:workspaceId/settings', name: 'workspace-settings', component: WorkspaceSettingsView, meta: { title: '工作空间设置' } },
  { path: '/w/:workspaceId/search', name: 'search', component: SearchView, meta: { title: '搜索' } },
  { path: '/w/:workspaceId/projects', name: 'projects', component: ProjectsView, meta: { title: '项目' } },
  { path: '/w/:workspaceId/projects/:id', name: 'project-detail', component: ProjectDetailView, meta: { title: '项目详情' } },
  { path: '/w/:workspaceId/tasks', name: 'tasks', component: TasksView, meta: { title: '任务看板' } },
  { path: '/w/:workspaceId/notes', name: 'notes', component: NotesView, meta: { title: '笔记' } },
  { path: '/w/:workspaceId/notes/import', name: 'note-import', component: NotesView, meta: { title: '导入笔记' } },
  { path: '/w/:workspaceId/notes/trash', name: 'note-trash', component: NotesView, meta: { title: '笔记回收站' } },
  { path: '/w/:workspaceId/notes/new', name: 'note-new', component: NotesView, meta: { title: '新建笔记' } },
  { path: '/w/:workspaceId/notes/:id/edit', name: 'note-edit', component: NotesView, meta: { title: '编辑笔记' } },
  { path: '/w/:workspaceId/taxonomy', name: 'taxonomy', component: TaxonomyView, meta: { title: '标签' } }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

async function resolveLoginRoute() {
  const redirect = sessionStorage.getItem('workstation:login-redirect');
  sessionStorage.removeItem('workstation:login-redirect');
  if (redirect?.startsWith('/') && !redirect.startsWith('//')) return redirect;
  const defaultWorkspace = await workspace.ensureWorkspace();
  return defaultWorkspace
    ? { name: 'dashboard', params: { workspaceId: defaultWorkspace.id } }
    : { name: 'workspace-unavailable' };
}

async function resolveSignedInRoute(to) {
  if (to.name === 'login') return resolveLoginRoute();
  const defaultWorkspace = await workspace.ensureWorkspace();
  if (!defaultWorkspace) return to.name === 'workspace-unavailable' ? true : { name: 'workspace-unavailable' };
  if (to.name === 'root') {
    return { name: 'dashboard', params: { workspaceId: defaultWorkspace.id } };
  }
  if (to.name === 'workspace-unavailable') return true;
  try {
    await workspace.activateWorkspace(to.params.workspaceId);
  } catch (err) {
    if (err.status !== 404 && err.status !== 403) throw err;
    await workspace.refreshWorkspaces();
    return workspace.currentId.value
      ? { name: 'dashboard', params: { workspaceId: workspace.currentId.value } }
      : { name: 'workspace-unavailable' };
  }
  if (to.name === 'note-new' && !to.query.draftId) {
    return { name: to.name, params: to.params, query: { ...to.query, draftId: window.crypto.randomUUID() } };
  }
  return true;
}

router.beforeEach(async (to) => {
  const user = await auth.ensureSession();
  if (!user) return to.meta.public ? true : { name: 'login', query: { redirect: to.fullPath } };
  if (to.meta.public && to.name !== 'login') return true;
  return resolveSignedInRoute(to);
});

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '工作台';
  document.title = `${title} · 个人工作台`;
});

export default router;
