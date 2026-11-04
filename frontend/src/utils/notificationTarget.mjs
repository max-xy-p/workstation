// 只构造已知站内路由，不信任通知中的任意 URL。
export function notificationTarget(notification) {
  const workspaceId = notification.workspaceId;
  if (!Number.isSafeInteger(workspaceId) || workspaceId <= 0) return null;
  const base = { workspaceId };
  if (notification.resourceType === 'WORKSPACE') return { name: 'dashboard', params: base };
  if (notification.resourceType === 'TASK') return { name: 'tasks', params: base };
  const id = notification.resourceId;
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  if (notification.resourceType === 'PROJECT') return { name: 'project-detail', params: { ...base, id } };
  if (notification.resourceType === 'NOTE') return { name: 'note-edit', params: { ...base, id } };
  return null;
}
