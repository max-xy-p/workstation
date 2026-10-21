import test from 'node:test';
import assert from 'node:assert/strict';
import { sourceChanges } from '../src/utils/taskIssuePresentation.mjs';

test('来源差异仅比较业务字段，失败不消除差异，恢复原状无差异', () => {
  const confirmedState = { title: '标题', status: 'OPEN', stateReason: null };
  const source = { ...confirmedState, confirmedState, sourceUpdatedAt: 'new' };
  assert.deepEqual(sourceChanges(source), []);
  source.title = '新标题'; source.status = 'CLOSED'; source.stateReason = 'completed'; source.trackingStatus = 'ERROR';
  assert.deepEqual(sourceChanges(source).map(change => change.field), ['title', 'status', 'stateReason']);
  assert.equal(sourceChanges(source)[1].after, '已关闭');
  Object.assign(source, confirmedState); assert.deepEqual(sourceChanges(source), []);
});
