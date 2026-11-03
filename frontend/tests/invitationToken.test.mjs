import test from 'node:test';
import assert from 'node:assert/strict';
import { readInvitationToken } from '../src/utils/invitationToken.mjs';

test('邀请凭据跨登录保留，错误的新链接不会使用旧邀请', () => {
  const values = new Map();
  const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
  const token = '12345678-1234-1234-1234-123456789abc.' + 'a'.repeat(43);
  assert.equal(readInvitationToken('#' + token, storage), token);
  assert.equal(readInvitationToken('', storage), token);
  assert.equal(readInvitationToken('#bad-token', storage), '');
  assert.equal(readInvitationToken('', storage), '');
});
