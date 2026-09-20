export function workspaceIdFromRoute(route) {
  const workspaceId = Number(route.params.workspaceId);
  if (!Number.isSafeInteger(workspaceId) || workspaceId < 1) {
    throw new Error('工作空间参数不合法');
  }
  return workspaceId;
}

export function workspaceRouteParams(route, extra = {}) {
  return { workspaceId: workspaceIdFromRoute(route), ...extra };
}
