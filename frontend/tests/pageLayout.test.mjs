import test from 'node:test';
import assert from 'node:assert/strict';
import { isNavigationActive, isNotesRoute, pageViewKey } from '../src/utils/pageLayout.mjs';

test('同一空间切换笔记保留文件树，切换空间重新建立页面', () => {
  const route = (name, workspaceId, fullPath) => ({ name, params: { workspaceId }, fullPath });
  assert.equal(pageViewKey(route('notes', 1, '/w/1/notes')), pageViewKey(route('note-edit', 1, '/w/1/notes/3/edit')));
  assert.equal(pageViewKey(route('note-new', 1, '/w/1/notes/new?draftId=a')), pageViewKey(route('note-trash', 1, '/w/1/notes/trash')));
  assert.notEqual(pageViewKey(route('notes', 1, '/w/1/notes')), pageViewKey(route('notes', 2, '/w/2/notes')));
});

test('详情和笔记辅助页面保持所属导航高亮', () => {
  for (const name of ['notes', 'note-new', 'note-edit', 'note-import', 'note-trash']) {
    assert.equal(isNotesRoute(name), true);
    assert.equal(isNavigationActive('notes', name), true);
  }
  assert.equal(isNavigationActive('projects', 'project-detail'), true);
  assert.equal(isNavigationActive('integrations', 'integration-detail'), true);
  assert.equal(isNavigationActive('dashboard', 'project-detail'), false);
  assert.equal(isNotesRoute('taxonomy'), false);
});

test('搜索参数及普通页面变化重新加载对应结果', () => {
  const key = (fullPath) => pageViewKey({ name: 'search', params: { workspaceId: 1 }, fullPath });
  assert.notEqual(key('/w/1/search?keyword=甲'), key('/w/1/search?keyword=乙'));
});
