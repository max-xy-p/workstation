export const workspaceRoleLabels = {
  OWNER: '所有者', ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员'
};

export function workspaceTypeLabel(type) {
  return type === 'SHARED' ? '共享空间' : '个人空间';
}
