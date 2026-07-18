export const PROJECT_STATUSES = [
  { value: 'PLANNING', label: '规划中' },
  { value: 'ACTIVE', label: '进行中' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'ARCHIVED', label: '已归档' }
];

export const TASK_STATUSES = [
  { value: 'TODO', label: '待办' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'DONE', label: '已完成' }
];

export const TASK_PRIORITIES = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' }
];

export const KANBAN_COLUMNS = TASK_STATUSES;

export function optionLabel(options, value) {
  return options.find((option) => option.value === value)?.label || value || '-';
}
