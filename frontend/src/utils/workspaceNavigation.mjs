// 切换空间时保留模块，清除只属于原空间的资源标识和筛选条件。
export function workspaceSwitchTarget(routeName, workspaceId) {
  const moduleHomes = { 'project-detail': 'projects', 'integration-detail': 'integrations', 'note-edit': 'notes', 'note-new': 'notes' };
  const pages = ['dashboard', 'integrations', 'projects', 'tasks', 'search', 'taxonomy',
    'workspace-settings', 'notes', 'note-import', 'note-trash'];
  const name = moduleHomes[routeName] || (pages.includes(routeName) ? routeName : 'dashboard');
  return { name, params: { workspaceId } };
}
