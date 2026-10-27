export const isTaskVersionConflict = error => error?.status === 409 && error?.code === 'TASK_VERSION_CONFLICT';

// 完整展示所有可编辑字段，避免用户只核对标题却覆盖其他成员修改的项目或依据笔记。
export function taskComparisonRows(local, latest, labels = {}) {
  if (!latest) return [];
  const value = (task, field) => {
    if (labels[field]) return labels[field](task[field]);
    if (field === 'projectId') return task.projectName || (task[field] ? `项目 ${task[field]}` : '未关联');
    if (field === 'evidenceNoteId') return task.evidenceNoteTitle || (task[field] ? `笔记 ${task[field]}` : '未关联');
    return String(task[field] ?? '') || '未填写';
  };
  return [['title', '标题'], ['description', '描述'], ['projectId', '所属项目'], ['status', '状态'],
    ['priority', '优先级'], ['dueDate', '截止日期'], ['evidenceNoteId', '依据笔记']]
    .map(([field, label]) => ({ field, label, local: value(local, field), latest: value(latest, field) }));
}
