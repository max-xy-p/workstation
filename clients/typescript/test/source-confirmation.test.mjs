import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';
test('来源确认保留关联、来源、确认与任务版本及显式动作', async () => {
  const calls=[];
  const transport={post:async (...args)=>{calls.push(args);return {pending:false};}};
  const body={expectedLinkToken:'generation',expectedSourceVersion:2,expectedConfirmationVersion:0,expectedTaskVersion:3,action:'KEEP'};
  const result=await createWorkstationApi(transport).issueApi.confirm(9,5,body);
  assert.deepEqual(calls,[['/workspaces/9/tasks/5/issue-source/confirm',body]]);assert.equal(result.pending,false);
});
