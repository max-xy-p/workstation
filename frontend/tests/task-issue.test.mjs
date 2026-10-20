import test from 'node:test';
import assert from 'node:assert/strict';
import { issueDifference, sourceTrackingLabel, safeIssueUrl, sourceReasonLabel } from '../src/utils/taskIssuePresentation.mjs';

test('来源状态差异不推断本地业务结果', () => {
  assert.match(issueDifference({ status: 'CLOSED', trackingStatus: 'ACTIVE' }, 'IN_PROGRESS'), /尚未完成/);
  assert.match(issueDifference({ status: 'OPEN', trackingStatus: 'ACTIVE' }, 'DONE'), /仍开放/);
  assert.equal(issueDifference({ status: 'CLOSED', trackingStatus: 'ACTIVE' }, 'DONE'), '');
  for (const trackingStatus of ['STOPPED', 'ERROR']) assert.equal(issueDifference({ status: 'CLOSED', trackingStatus }, 'TODO'), '');
  assert.equal(issueDifference(null, 'TODO'), '');
});
test('来源链接限定 GitHub HTTPS 并拒绝凭证和脚本', () => {
  assert.equal(safeIssueUrl('https://github.com/a/b/issues/1'), 'https://github.com/a/b/issues/1');
  for (const value of ['javascript:alert(1)', 'http://github.com/a/b', 'https://github.com.evil.test', 'https://user:pass@github.com/a', null]) assert.equal(safeIssueUrl(value), '#');
  assert.match(sourceTrackingLabel('STOPPED'), /停止/);
  assert.match(sourceTrackingLabel('ERROR'), /旧快照/);
});

test('关闭原因使用中文且未知原因不伪装为完成', () => {
  assert.equal(sourceReasonLabel('completed'), '已完成');
  assert.equal(sourceReasonLabel('not_planned'), '不再计划处理');
  assert.match(sourceReasonLabel('future_reason'), /未识别/);
  assert.equal(sourceReasonLabel(null), '未提供');
});
