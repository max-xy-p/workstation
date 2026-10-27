import test from 'node:test';
import assert from 'node:assert/strict';
import { isTaskVersionConflict, taskComparisonRows } from '../src/utils/taskConflict.mjs';
test('任务冲突不能混同来源冲突、网络错误或资源删除', () => {
  assert.equal(isTaskVersionConflict({ status: 409, code: 'TASK_VERSION_CONFLICT' }), true);
  for (const error of [{status:409,code:'TASK_ISSUE_CONFLICT'},{status:404},{status:500},null]) assert.equal(isTaskVersionConflict(error),false);
});
test('冲突对照覆盖全部编辑字段，保留原输入且不把版本号当作业务内容', () => {
  const local={title:'我的标题',projectId:1,status:'TODO',evidenceNoteId:3};
  const latest={title:'服务端标题',projectId:2,status:'DONE',evidenceNoteId:null,version:9};
  const before=JSON.stringify(local);const rows=taskComparisonRows(local,latest);
  assert.deepEqual(rows.map(row=>row.field),['title','description','projectId','status','priority','dueDate','evidenceNoteId']);
  assert.equal(rows[0].local,'我的标题');assert.equal(rows[2].latest,'项目 2');assert.equal(JSON.stringify(local),before);
  assert.deepEqual(taskComparisonRows(local,null),[]);
});
