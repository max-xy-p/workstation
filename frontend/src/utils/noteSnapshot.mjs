/** 当前笔记和草稿共用同一种规范快照，不包含展示字段。 */
export function noteSnapshot(value) {
  return {
    title: value.title.trim(), content: value.content,
    folderId: value.folderId ? Number(value.folderId) : null,
    projectId: value.projectId ? Number(value.projectId) : null,
    tagIds: [...new Set((value.tagIds || value.tags?.map(tag => tag.id) || []).map(Number))].sort((a, b) => a - b)
  };
}

/** 旧草稿缺少可信基准时返回 null，不使用最新内容猜测编辑基准。 */
export function parseMergeBase(snapshot) {
  try {
    const value = JSON.parse(snapshot);
    if (typeof value.title !== 'string' || typeof value.content !== 'string') return null;
    for (const field of ['folderId', 'projectId']) {
      if (value[field] !== null && (!Number.isSafeInteger(value[field]) || value[field] < 1)) return null;
    }
    if (!Array.isArray(value.tagIds) || value.tagIds.some(id => !Number.isSafeInteger(id) || id < 1)) return null;
    return noteSnapshot(value);
  } catch { return null; }
}
