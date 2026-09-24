<template>
  <div class="tiptap-editor-shell">
    <div v-if="editor && editable" class="tiptap-format-toolbar" role="toolbar" aria-label="正文格式">
      <select
        class="tiptap-block-select"
        :value="activeBlock"
        aria-label="段落类型"
        @change="setBlockType"
      >
        <option value="paragraph">正文</option>
        <option value="heading-1">一级标题</option>
        <option value="heading-2">二级标题</option>
        <option value="heading-3">三级标题</option>
      </select>

      <span class="tiptap-toolbar-divider" aria-hidden="true"></span>

      <button
        type="button"
        :class="{ active: isActive('bold') }"
        aria-label="加粗"
        title="加粗"
        @click="editor.chain().focus().toggleBold().run()"
      >
        <Bold :size="17" />
      </button>
      <button
        type="button"
        :class="{ active: isActive('italic') }"
        aria-label="斜体"
        title="斜体"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        <Italic :size="17" />
      </button>

      <span class="tiptap-toolbar-divider" aria-hidden="true"></span>

      <button
        type="button"
        :class="{ active: isActive('bulletList') }"
        aria-label="无序列表"
        title="无序列表"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        <List :size="17" />
      </button>
      <button
        type="button"
        :class="{ active: isActive('orderedList') }"
        aria-label="有序列表"
        title="有序列表"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        <ListOrdered :size="17" />
      </button>
      <button
        type="button"
        :class="{ active: isActive('blockquote') }"
        aria-label="引用"
        title="引用"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        <Quote :size="17" />
      </button>
      <button
        type="button"
        :class="{ active: isActive('codeBlock') }"
        aria-label="代码块"
        title="代码块"
        @click="editor.chain().focus().toggleCodeBlock().run()"
      >
        <Code2 :size="17" />
      </button>

      <span class="tiptap-toolbar-spacer"></span>

      <button
        type="button"
        :disabled="!canUndo"
        aria-label="撤销"
        title="撤销"
        @click="editor.chain().focus().undo().run()"
      >
        <Undo2 :size="17" />
      </button>
      <button
        type="button"
        :disabled="!canRedo"
        aria-label="重做"
        title="重做"
        @click="editor.chain().focus().redo().run()"
      >
        <Redo2 :size="17" />
      </button>
    </div>

    <EditorContent :editor="editor" class="tiptap-editor-content" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, shallowRef, triggerRef, watch } from 'vue';
import { Editor, EditorContent } from '@tiptap/vue-3';
import StarterKit from '@tiptap/starter-kit';
import { Markdown } from '@tiptap/markdown';
import Placeholder from '@tiptap/extension-placeholder';
import { Bold, Code2, Italic, List, ListOrdered, Quote, Redo2, Undo2 } from '@lucide/vue';

const props = defineProps({
  editable: { type: Boolean, default: true },
  modelValue: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:modelValue', 'ready', 'error']);
const editor = shallowRef(null);

const activeBlock = computed(() => {
  if (!editor.value) return 'paragraph';
  if (editor.value.isActive('heading', { level: 1 })) return 'heading-1';
  if (editor.value.isActive('heading', { level: 2 })) return 'heading-2';
  if (editor.value.isActive('heading', { level: 3 })) return 'heading-3';
  return 'paragraph';
});
const canUndo = computed(() => {
  return editor.value?.can().undo() ?? false;
});
const canRedo = computed(() => {
  return editor.value?.can().redo() ?? false;
});

function isActive(name) {
  return editor.value?.isActive(name) ?? false;
}

function refreshToolbar() {
  triggerRef(editor);
}

function setBlockType(event) {
  const value = event.target.value;
  if (value === 'paragraph') {
    editor.value.chain().focus().setParagraph().run();
    return;
  }

  const level = Number(value.replace('heading-', ''));
  editor.value.chain().focus().setHeading({ level }).run();
}

onMounted(() => {
  try {
    editor.value = new Editor({
      editable: props.editable,
      extensions: [
        StarterKit.configure({
          heading: { levels: [1, 2, 3] }
        }),
        Markdown.configure({
          markedOptions: { gfm: true }
        }),
        Placeholder.configure({
          placeholder: '开始书写'
        })
      ],
      content: props.modelValue,
      contentType: 'markdown',
      editorProps: {
        attributes: {
          'aria-label': 'Markdown 正文编辑器'
        }
      },
      onCreate: () => {
        refreshToolbar();
        emit('ready');
      },
      onSelectionUpdate: refreshToolbar,
      onUpdate: ({ editor: currentEditor }) => {
        refreshToolbar();
        const markdown = currentEditor.getMarkdown();
        if (markdown !== props.modelValue) emit('update:modelValue', markdown);
      }
    });
  } catch (error) {
    emit('error', error);
  }
});

watch(() => props.modelValue, (value) => {
  if (!editor.value || value === editor.value.getMarkdown()) return;
  editor.value.commands.setContent(value, {
    contentType: 'markdown',
    emitUpdate: false
  });
});

watch(() => props.editable, (value) => editor.value?.setEditable(value));

onBeforeUnmount(() => {
  editor.value?.destroy();
  editor.value = null;
});
</script>
