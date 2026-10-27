import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';
test('任务写入携带用户所见版本，版本零保留在状态请求和删除查询中', async () => {
  const calls=[];const transport=Object.fromEntries(['put','patch','delete'].map(method=>[method,async(...args)=>{calls.push({method,args});return {};} ]));
  const api=createWorkstationApi(transport).taskApi;
  await api.update(9,5,{title:'任务',expectedVersion:0});await api.updateStatus(9,5,'DONE',0);await api.delete(9,5,0);
  assert.equal(calls[0].args[1].expectedVersion,0);
  assert.deepEqual(calls[1].args,['/workspaces/9/tasks/5/status',{status:'DONE',expectedVersion:0}]);
  assert.deepEqual(calls[2].args,['/workspaces/9/tasks/5?expectedVersion=0']);
});
