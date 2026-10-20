// 来源状态和本地状态独立表达，不推断业务处理结果。
export function issueDifference(source, taskStatus) {
  if (!source || source.trackingStatus === 'STOPPED' || source.trackingStatus === 'ERROR') return '';
  if (source.status === 'CLOSED' && taskStatus !== 'DONE') return '来源已关闭，本地任务尚未完成';
  if (source.status === 'OPEN' && taskStatus === 'DONE') return '本地已完成，来源仍开放';
  return '';
}
export function sourceTrackingLabel(status) {
  return ({ WAITING: '等待首次定点刷新', ACTIVE: '跟踪已启用', ERROR: '刷新失败，显示旧快照', STOPPED: '跟踪已停止，请检查仓库绑定和连接权限' })[status] || '跟踪状态未知';
}
export function safeIssueUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com' && !url.username && !url.password ? url.href : '#';
  } catch { return '#'; }
}

export function sourceReasonLabel(reason) {
  if (!reason) return '未提供';
  return ({ completed: '已完成', not_planned: '不再计划处理', reopened: '已重新打开' })[reason] || `未识别原因（${reason}）`;
}
