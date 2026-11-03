const storageKey = 'workstation:invitation-token';
const tokenPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.[A-Za-z0-9_-]{43}$/;

// URL 片段不发送给服务器；登录回跳只保存固定路径，凭据留在当前标签页会话中。
export function readInvitationToken(hash, storage) {
  if (hash) {
    const value = hash.slice(1);
    if (!tokenPattern.test(value)) {
      storage.removeItem(storageKey);
      return '';
    }
    storage.setItem(storageKey, value);
    return value;
  }
  const saved = storage.getItem(storageKey) || '';
  return tokenPattern.test(saved) ? saved : '';
}
