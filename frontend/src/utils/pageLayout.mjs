const noteRoutes = new Set(['notes', 'note-new', 'note-edit', 'note-import', 'note-trash']);

export function isNotesRoute(name) {
  return noteRoutes.has(name);
}

// 切换笔记只重建编辑器，保留同一空间内的文件树与筛选状态。
export function pageViewKey(route) {
  if (isNotesRoute(route.name)) return `notes:${route.params.workspaceId}`;
  return route.fullPath;
}

export function isNavigationActive(name, routeName) {
  if (name === 'notes') return isNotesRoute(routeName);
  if (name === 'projects') return ['projects', 'project-detail'].includes(routeName);
  if (name === 'integrations') return ['integrations', 'integration-detail'].includes(routeName);
  return name === routeName;
}
