import { api, resetCsrfToken, submitWithCsrf } from './client';

export const authApi = {
  currentUser: () => api.get('/auth/me', undefined, { suppressUnauthorized: true }),
  login() {
    // 这里只发起 OIDC 登录；用户名和密码始终由 Keycloak 页面收集。
    window.location.assign('/oauth2/authorization/keycloak');
  },
  async logout() {
    await submitWithCsrf('/auth/logout');
    resetCsrfToken();
  }
};

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

// 文件夹 API：用于维护笔记的多级目录。
export const noteFolderApi = {
  list: () => api.get('/note-folders'),
  get: (id) => api.get(`/note-folders/${id}`),
  create: (payload) => api.post('/note-folders', payload),
  update: (id, payload) => api.put(`/note-folders/${id}`, payload),
  delete: (id) => api.delete(`/note-folders/${id}`)
};

// 标签 API：用于维护笔记标签。
export const tagApi = {
  list: () => api.get('/tags'),
  get: (id) => api.get(`/tags/${id}`),
  create: (payload) => api.post('/tags', payload),
  update: (id, payload) => api.put(`/tags/${id}`, payload),
  delete: (id) => api.delete(`/tags/${id}`)
};
