function compareNames(left, right) {
  return left.name.localeCompare(right.name, 'zh-CN');
}

export function buildFolderTree(folders, notes = []) {
  const nodes = new Map(folders.map((folder) => [folder.id, { ...folder, children: [], notes: [] }]));
  const roots = [];

  nodes.forEach((node) => {
    const parent = nodes.get(node.parentId);
    if (parent && parent.id !== node.id) parent.children.push(node);
    else roots.push(node);
  });

  notes.forEach((note) => nodes.get(note.folderId)?.notes.push(note));
  nodes.forEach((node) => node.children.sort(compareNames));
  roots.sort(compareNames);
  return roots;
}

export function flattenFolderOptions(folders) {
  const options = [];

  function visit(nodes, depth, path) {
    nodes.forEach((node) => {
      const currentPath = [...path, node.name];
      options.push({ ...node, depth, path: currentPath.join(' / ') });
      visit(node.children, depth + 1, currentPath);
    });
  }

  visit(buildFolderTree(folders), 0, []);
  return options;
}

export function descendantFolderIds(folders, folderId) {
  const childrenByParent = new Map();
  folders.forEach((folder) => {
    if (!childrenByParent.has(folder.parentId)) childrenByParent.set(folder.parentId, []);
    childrenByParent.get(folder.parentId).push(folder.id);
  });

  const descendants = new Set([folderId]);
  const pending = [folderId];
  while (pending.length) {
    const current = pending.pop();
    (childrenByParent.get(current) || []).forEach((childId) => {
      if (!descendants.has(childId)) {
        descendants.add(childId);
        pending.push(childId);
      }
    });
  }
  return descendants;
}
