import test from 'node:test';
import assert from 'node:assert/strict';
import { workspaceSwitchTarget } from '../src/utils/workspaceNavigation.mjs';

test('切换空间保留列表模块且不会携带原空间筛选', () => {
  for (const name of ['notes', 'projects', 'tasks', 'search', 'taxonomy', 'integrations', 'workspace-settings', 'note-trash']) {
    assert.deepEqual(workspaceSwitchTarget(name, 20), { name, params: { workspaceId: 20 } });
  }
});
test('详情和编辑页面切换后进入目标空间列表', () => {
  assert.deepEqual(workspaceSwitchTarget('project-detail', 20), { name: 'projects', params: { workspaceId: 20 } });
  assert.deepEqual(workspaceSwitchTarget('integration-detail', 20), { name: 'integrations', params: { workspaceId: 20 } });
  for (const name of ['note-edit', 'note-new']) {
    assert.deepEqual(workspaceSwitchTarget(name, 20), { name: 'notes', params: { workspaceId: 20 } });
  }
});
test('未知页面切换后回到概览', () => {
  assert.equal(workspaceSwitchTarget('unknown', 20).name, 'dashboard');
});
