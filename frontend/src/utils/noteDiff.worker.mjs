import { compareNoteText } from './noteDiff.mjs';

self.onmessage = event => {
  try {
    self.postMessage(compareNoteText(event.data.before, event.data.after));
  } catch {
    self.postMessage({ available: false, reason: '无法生成差异，请切换到预览查看原文。' });
  }
};
