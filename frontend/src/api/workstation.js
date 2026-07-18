import { api } from './client';

// 首页概览 API：用于首页一次性获取统计数字和最近更新数据。
export const dashboardApi = {
  summary: () => api.get('/dashboard')
};

// 项目 API：对应后端 /api/projects。
export const projectApi = {
  list: (params) => api.get('/projects', params),
  detail: (id) => api.get(`/projects/${id}`),
  create: (payload) => api.post('/projects', payload),
  update: (id, payload) => api.put(`/projects/${id}`, payload),
  updateStatus: (id, status) => api.patch(`/projects/${id}/status`, { status }),
  delete: (id) => api.delete(`/projects/${id}`)
};

// 任务 API：对应后端 /api/tasks，支持看板状态流转。
export const taskApi = {
  list: (params) => api.get('/tasks', params),
  get: (id) => api.get(`/tasks/${id}`),
  create: (payload) => api.post('/tasks', payload),
  update: (id, payload) => api.put(`/tasks/${id}`, payload),
  updateStatus: (id, status) => api.patch(`/tasks/${id}/status`, { status }),
  delete: (id) => api.delete(`/tasks/${id}`)
};

// 笔记 API：对应后端 /api/notes，包含 Markdown 导入入口。
export const noteApi = {
  list: (params) => api.get('/notes', params),
  get: (id) => api.get(`/notes/${id}`),
  create: (payload) => api.post('/notes', payload),
  update: (id, payload) => api.put(`/notes/${id}`, payload),
  importMarkdown: (payload) => api.post('/notes/import', payload),
  delete: (id) => api.delete(`/notes/${id}`)
};

// 分类 API：用于维护知识库分类。
export const categoryApi = {
  list: () => api.get('/categories'),
  get: (id) => api.get(`/categories/${id}`),
  create: (payload) => api.post('/categories', payload),
  update: (id, payload) => api.put(`/categories/${id}`, payload),
  delete: (id) => api.delete(`/categories/${id}`)
};

// 标签 API：用于维护笔记标签。
export const tagApi = {
  list: () => api.get('/tags'),
  get: (id) => api.get(`/tags/${id}`),
  create: (payload) => api.post('/tags', payload),
  update: (id, payload) => api.put(`/tags/${id}`, payload),
  delete: (id) => api.delete(`/tags/${id}`)
};
