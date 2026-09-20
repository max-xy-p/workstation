const DATABASE_NAME = 'workstation-cache';
const DATABASE_VERSION = 2;
const DRAFT_STORE = 'note-drafts';

let databasePromise;

function openDatabase() {
  if (!window.indexedDB) {
    return Promise.reject(new Error('当前浏览器不支持 IndexedDB'));
  }
  if (databasePromise) return databasePromise;

  databasePromise = new Promise((resolve, reject) => {
    const request = window.indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      let store;
      if (!database.objectStoreNames.contains(DRAFT_STORE)) {
        store = database.createObjectStore(DRAFT_STORE, { keyPath: 'key' });
        store.createIndex('updatedAt', 'updatedAt');
      } else {
        store = request.transaction.objectStore(DRAFT_STORE);
      }
      if (!store.indexNames.contains('subjectId')) store.createIndex('subjectId', 'subjectId');
      if (!store.indexNames.contains('workspaceId')) store.createIndex('workspaceId', 'workspaceId');
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('无法打开 IndexedDB'));
    request.onblocked = () => reject(new Error('IndexedDB 升级被其他页面阻止'));
  });

  databasePromise.catch(() => {
    databasePromise = null;
  });
  return databasePromise;
}

function transactionCompletion(transaction) {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error || new Error('IndexedDB 事务失败'));
    transaction.onabort = () => reject(transaction.error || new Error('IndexedDB 事务已中止'));
  });
}

export async function getNoteDraft(key) {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const request = database.transaction(DRAFT_STORE, 'readonly').objectStore(DRAFT_STORE).get(key);
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error || new Error('读取本地草稿失败'));
  });
}

export async function putNoteDraft(draft) {
  const database = await openDatabase();
  const transaction = database.transaction(DRAFT_STORE, 'readwrite');
  const completion = transactionCompletion(transaction);
  transaction.objectStore(DRAFT_STORE).put(draft);
  await completion;
}

export async function deleteNoteDraft(key) {
  const database = await openDatabase();
  const transaction = database.transaction(DRAFT_STORE, 'readwrite');
  const completion = transactionCompletion(transaction);
  transaction.objectStore(DRAFT_STORE).delete(key);
  await completion;
}
