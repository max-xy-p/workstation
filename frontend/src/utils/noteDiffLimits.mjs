export const MAX_DIFF_LENGTH = 200_000;
export const MAX_DIFF_LINES = 5_000;
export const DIFF_TIMEOUT_MS = 1_000;

export function diffInputReason(before, after) {
  if (typeof before !== 'string' || typeof after !== 'string') return '比较内容不可用，请重新读取版本。';
  if (before.length > MAX_DIFF_LENGTH || after.length > MAX_DIFF_LENGTH) return '内容较长，请切换到预览查看原文。';
  if (before.split('\n').length > MAX_DIFF_LINES || after.split('\n').length > MAX_DIFF_LINES)
    return '正文超过 5,000 行，请切换到预览查看原文。';
  return '';
}

export function textWindow(text, page, size = 12_000) {
  let start = page * size;
  let end = Math.min(text.length, start + size);
  // 分段预览不在 UTF-16 代理对中间切开字符。
  if (start > 0 && /[\uDC00-\uDFFF]/.test(text.charAt(start)) && /[\uD800-\uDBFF]/.test(text.charAt(start - 1))) start++;
  if (end < text.length && /[\uD800-\uDBFF]/.test(text.charAt(end - 1)) && /[\uDC00-\uDFFF]/.test(text.charAt(end))) end++;
  return text.slice(start, end);
}
