<template>
  <div class="note-tree-branch">
    <div class="note-tree-folder-row" :style="{ '--tree-depth': depth }">
      <button class="tree-disclosure" type="button" :aria-label="expanded ? '折叠文件夹' : '展开文件夹'" @click="$emit('toggle', folder.id)">
        <ChevronRight :class="{ expanded }" />
      </button>
      <button class="tree-folder-name" type="button" @click="$emit('toggle', folder.id)">
        <FolderOpen v-if="expanded" />
        <Folder v-else />
        <span>{{ folder.name }}</span>
      </button>
      <span class="tree-folder-count">{{ folder.notes.length }}</span>
      <div class="tree-row-actions">
        <button type="button" title="在此文件夹中新建笔记" aria-label="在此文件夹中新建笔记" @click="$emit('new-note', folder.id)"><FilePlus2 /></button>
        <button type="button" title="新建子文件夹" aria-label="新建子文件夹" @click="$emit('new-child', folder.id)"><FolderPlus /></button>
        <button type="button" title="编辑文件夹" aria-label="编辑文件夹" @click="$emit('edit', folder)"><Pencil /></button>
        <button type="button" title="删除文件夹" aria-label="删除文件夹" @click="$emit('delete', folder)"><Trash2 /></button>
      </div>
    </div>

    <div v-if="expanded">
      <NoteTreeItem
        v-for="child in folder.children"
        :key="child.id"
        :folder="child"
        :depth="depth + 1"
        :expanded-ids="expandedIds"
        :selected-note-id="selectedNoteId"
        @toggle="$emit('toggle', $event)"
        @new-note="$emit('new-note', $event)"
        @new-child="$emit('new-child', $event)"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"
      />
      <RouterLink
        v-for="note in folder.notes"
        :key="note.id"
        class="note-tree-note-row"
        :class="{ selected: selectedNoteId === note.id }"
        :style="{ '--tree-depth': depth + 1 }"
        :to="{ name: 'note-edit', params: { id: note.id } }"
      >
        <FileText />
        <span>{{ note.title }}</span>
      </RouterLink>
    </div>
  </div>
</template>

<script setup>
import { ChevronRight, FilePlus2, FileText, Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from '@lucide/vue';
import { computed } from 'vue';
import { RouterLink } from 'vue-router';

const props = defineProps({
  folder: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  expandedIds: { type: Set, required: true },
  selectedNoteId: { type: Number, default: null }
});

defineEmits(['toggle', 'new-note', 'new-child', 'edit', 'delete']);

const expanded = computed(() => props.expandedIds.has(props.folder.id));
</script>
