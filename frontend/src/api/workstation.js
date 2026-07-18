import { api } from './client';

export const dashboardApi = {
  summary: () => api.get('/dashboard')
};

export const projectApi = {
  list: (params) => api.get('/projects', params),
  detail: (id) => api.get(`/projects/${id}`),
  create: (payload) => api.post('/projects', payload),
  update: (id, payload) => api.put(`/projects/${id}`, payload),
  updateStatus: (id, status) => api.patch(`/projects/${id}/status`, { status }),
  delete: (id) => api.delete(`/projects/${id}`)
};

export const taskApi = {
  list: (params) => api.get('/tasks', params),
  get: (id) => api.get(`/tasks/${id}`),
  create: (payload) => api.post('/tasks', payload),
  update: (id, payload) => api.put(`/tasks/${id}`, payload),
  updateStatus: (id, status) => api.patch(`/tasks/${id}/status`, { status }),
  delete: (id) => api.delete(`/tasks/${id}`)
};

export const noteApi = {
  list: (params) => api.get('/notes', params),
  get: (id) => api.get(`/notes/${id}`),
  create: (payload) => api.post('/notes', payload),
  update: (id, payload) => api.put(`/notes/${id}`, payload),
  delete: (id) => api.delete(`/notes/${id}`)
};

export const categoryApi = {
  list: () => api.get('/categories'),
  get: (id) => api.get(`/categories/${id}`),
  create: (payload) => api.post('/categories', payload),
  update: (id, payload) => api.put(`/categories/${id}`, payload),
  delete: (id) => api.delete(`/categories/${id}`)
};

export const tagApi = {
  list: () => api.get('/tags'),
  get: (id) => api.get(`/tags/${id}`),
  create: (payload) => api.post('/tags', payload),
  update: (id, payload) => api.put(`/tags/${id}`, payload),
  delete: (id) => api.delete(`/tags/${id}`)
};
