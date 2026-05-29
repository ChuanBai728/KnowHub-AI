<template>
  <span class="status-badge" :class="badgeClass">
    <span class="status-dot"></span>
    {{ label || '未设置' }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { normalizeCode } from '../../utils/manageFormat'

const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'default'
  },
  code: {
    type: [String, Number],
    default: ''
  }
})

const badgeClass = computed(() => {
  if (props.type === 'parse') {
    return mapParseClass(normalizeCode(props.code))
  }
  if (props.type === 'strategy') {
    return mapStrategyClass(normalizeCode(props.code))
  }
  if (props.type === 'index') {
    return mapIndexClass(normalizeCode(props.code))
  }
  if (props.type === 'task') {
    return mapTaskClass(normalizeCode(props.code))
  }
  return 'status-default'
})

function mapParseClass(code) {
  if (code === '3') return 'status-success'
  if (code === '2') return 'status-processing'
  if (code === '4') return 'status-danger'
  return 'status-waiting'
}

function mapStrategyClass(code) {
  if (code === '3') return 'status-success'
  if (code === '2') return 'status-processing'
  return 'status-waiting'
}

function mapIndexClass(code) {
  if (code === '3') return 'status-success'
  if (code === '2') return 'status-processing'
  if (code === '4') return 'status-danger'
  return 'status-waiting'
}

function mapTaskClass(code) {
  if (code === '3') return 'status-success'
  if (code === '2' || code === '1') return 'status-processing'
  if (code === '4') return 'status-danger'
  return 'status-default'
}
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid transparent;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: currentColor;
}

.status-default {
  background: rgba(107, 94, 78, 0.08);
  color: var(--color-muted-strong);
  border-color: rgba(107, 94, 78, 0.15);
}

.status-waiting {
  background: rgba(180, 98, 26, 0.08);
  color: var(--color-warning);
  border-color: rgba(180, 98, 26, 0.18);
}

.status-processing {
  background: rgba(67, 56, 202, 0.08);
  color: var(--color-primary);
  border-color: rgba(67, 56, 202, 0.18);
}

.status-success {
  background: rgba(22, 118, 94, 0.08);
  color: var(--color-success);
  border-color: rgba(22, 118, 94, 0.18);
}

.status-danger {
  background: rgba(185, 64, 48, 0.08);
  color: var(--color-danger);
  border-color: rgba(185, 64, 48, 0.18);
}
</style>
