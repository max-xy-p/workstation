import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({
  breaks: true,
  gfm: true
});

export function renderMarkdown(markdown) {
  const rawHtml = marked.parse(markdown || '');
  // 渲染笔记时会使用 v-html，必须先清洗 HTML，避免 Markdown 中的脚本被执行。
  return DOMPurify.sanitize(rawHtml);
}
