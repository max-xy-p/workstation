import { mergeNote } from './noteMerge.mjs';
self.onmessage = event => {
  try { self.postMessage(mergeNote(event.data)); }
  catch { self.postMessage({ available: false, reason: '无法自动合并，请手动核对原文。' }); }
};
