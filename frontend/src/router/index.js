import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '../views/DashboardView.vue';
import ProjectDetailView from '../views/ProjectDetailView.vue';
import ProjectsView from '../views/ProjectsView.vue';
import TasksView from '../views/TasksView.vue';
import NotesView from '../views/NotesView.vue';
import NoteEditorView from '../views/NoteEditorView.vue';
import TaxonomyView from '../views/TaxonomyView.vue';

export const routes = [
  { path: '/', name: 'dashboard', component: DashboardView, meta: { title: '概览' } },
  { path: '/projects', name: 'projects', component: ProjectsView, meta: { title: '项目' } },
  { path: '/projects/:id', name: 'project-detail', component: ProjectDetailView, meta: { title: '项目详情' } },
  { path: '/tasks', name: 'tasks', component: TasksView, meta: { title: '任务看板' } },
  { path: '/notes', name: 'notes', component: NotesView, meta: { title: '笔记' } },
  { path: '/notes/new', name: 'note-new', component: NoteEditorView, meta: { title: '新建笔记' } },
  { path: '/notes/:id/edit', name: 'note-edit', component: NoteEditorView, meta: { title: '编辑笔记' } },
  { path: '/taxonomy', name: 'taxonomy', component: TaxonomyView, meta: { title: '分类标签' } }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
