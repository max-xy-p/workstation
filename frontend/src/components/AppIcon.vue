<template>
  <svg
    class="app-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'integrations'">
      <ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0" />
    </template>
    <template v-else-if="name === 'dashboard'">
      <path d="M4 5.5h6.5V12H4zM13.5 5.5H20V9h-6.5zM13.5 12H20v6.5h-6.5zM4 15h6.5v3.5H4z" />
    </template>
    <template v-else-if="name === 'projects'">
      <path d="M3.5 7.5h6l1.6 2H20.5v8.8a1.7 1.7 0 0 1-1.7 1.7H5.2a1.7 1.7 0 0 1-1.7-1.7z" />
      <path d="M3.5 7.5V5.7A1.7 1.7 0 0 1 5.2 4h4.3l1.6 2h7.7a1.7 1.7 0 0 1 1.7 1.5" />
    </template>
    <template v-else-if="name === 'tasks'">
      <path d="M9 6h11M9 12h11M9 18h11" />
      <path d="m4 6 1.2 1.2L7.5 5M4 12l1.2 1.2L7.5 11M4 18l1.2 1.2L7.5 17" />
    </template>
    <template v-else-if="name === 'notes'">
      <path d="M6 3.5h9l3 3V20.5H6z" />
      <path d="M14.5 3.5v4h4M9 11h6M9 15h6" />
    </template>
    <template v-else-if="name === 'taxonomy'">
      <path d="M12 4v5M6 20v-5M18 20v-5M6 15h12M6 15v-3h12v3" />
      <circle cx="12" cy="4" r="1.7" /><circle cx="6" cy="20" r="1.7" /><circle cx="18" cy="20" r="1.7" />
    </template>
    <template v-else-if="name === 'write'">
      <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16z" />
      <path d="m13.5 6.5 4 4M4 16l4 4" />
    </template>
    <template v-else-if="name === 'search'">
      <circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" />
    </template>
    <template v-else-if="name === 'arrow'">
      <path d="M5 12h14M14 7l5 5-5 5" />
    </template>
    <template v-else-if="name === 'import'">
      <path d="M12 3v12M7.5 10.5 12 15l4.5-4.5" /><path d="M4 18.5h16" />
    </template>
    <template v-else-if="name === 'logout'">
      <path d="M10 5H5v14h5M14 8l4 4-4 4M8 12h10" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  name: {
    type: String,
    required: true
  }
});
</script>
