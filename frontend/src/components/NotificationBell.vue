<template>
  <RouterLink v-if="notifications.state.enabled || notifications.state.error" class="notification-bell"
    :to="{ name: 'notifications' }" :aria-label="label" :title="label">
    <Bell aria-hidden="true" />
    <span v-if="notifications.state.unreadCount" class="notification-badge" aria-hidden="true">{{ notifications.state.unreadCount > 99 ? '99+' : notifications.state.unreadCount }}</span>
  </RouterLink>
</template>

<script setup>
import { computed } from 'vue';
import { Bell } from '@lucide/vue';
import { notifications } from '../notifications';
const label = computed(() => notifications.state.error ? '通知暂不可用，打开通知中心重试'
  : notifications.state.unreadCount ? '通知，' + notifications.state.unreadCount + ' 条未读' : '通知，没有未读消息');
</script>

<style scoped>
.notification-bell { position: relative; display: grid; place-items: center; width: 40px; height: 40px; flex: none; color: var(--ink); border: 1px solid var(--line); border-radius: 8px; background: white; text-decoration: none; }
.notification-bell:hover { background: #f1f5f8; }
.notification-bell svg { width: 19px; height: 19px; }
.notification-badge { position: absolute; top: -6px; right: -7px; padding: 2px 5px; min-width: 18px; border-radius: 10px; background: #b74e46; color: white; font-size: 10px; line-height: 14px; text-align: center; }
</style>
