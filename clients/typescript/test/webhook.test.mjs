import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorkstationApi } from '../dist/index.js';
test('Webhook 配置和投递重试保留工作空间与版本，不提供浏览器回调调用', async () => {
  const calls=[];
  const transport=Object.fromEntries(['get','post','put','patch','delete'].map(method=>[method,async(...args)=>{calls.push({method,args});return {};} ]));
  const api=createWorkstationApi(transport).webhookApi;
  await api.get(9,2);await api.configure(9,2,{enabled:false,expectedVersion:3,secret:null});await api.deliveries(9,2,1,20);await api.retry(9,2,8);
  assert.deepEqual(calls[0].args,['/workspaces/9/integrations/2/webhook']);
  assert.equal(calls[1].args[1].expectedVersion,3);
  assert.deepEqual(calls[2].args,['/workspaces/9/integrations/2/webhook/deliveries',{page:1,size:20}]);
  assert.deepEqual(calls[3],{method:'post',args:['/workspaces/9/integrations/2/webhook/deliveries/8/retry']});
  assert.equal(api.receive,undefined);
});
