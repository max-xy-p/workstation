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
      <details ref="actionMenu" class="tree-action-menu" @focusout="closeMenuOnFocusOut">
        <summary title="文件夹操作" aria-label="文件夹操作"><Ellipsis /></summary>
        <div class="tree-action-popover">
          <button type="button" @click="runAction('new-note', folder.id)"><FilePlus2 /><span>新建笔记</span></button>
          <button type="button" @click="runAction('new-child', folder.id)"><FolderPlus /><span>新建子文件夹</span></button>
          <button type="button" @click="runAction('edit', folder)"><Pencil /><span>编辑文件夹</span></button>
          <button class="danger" type="button" @click="runAction('delete', folder)"><Trash2 /><span>删除文件夹</span></button>
        </div>
      </details>
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
import { ChevronRight, Ellipsis, FilePlus2, FileText, Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from '@lucide/vue';
import { computed, ref } from 'vue';
import { RouterLink } from 'vue-router';

const props = defineProps({
  folder: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  expandedIds: { type: Set, required: true },
  selectedNoteId: { type: Number, default: null }
});

const emit = defineEmits(['toggle', 'new-note', 'new-child', 'edit', 'delete']);

const expanded = computed(() => props.expandedIds.has(props.folder.id));
const actionMenu = ref(null);

function runAction(action, payload) {
  actionMenu.value?.removeAttribute('open');
  emit(action, payload);
}

function closeMenuOnFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.removeAttribute('open');
}
</script>
