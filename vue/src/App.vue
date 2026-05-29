<template>
  <router-view v-if="isFullscreenLayout" />

  <div v-else class="app-shell">
    <header class="app-header">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true">S</div>
        <h1 class="app-title">KnowHub AI</h1>
      </div>
    </header>

    <main class="app-main">
      <router-view />
    </main>

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()

const isFullscreenLayout = computed(() => route.meta?.layout === 'fullscreen')
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding: 20px 28px 28px;
}

.app-header {
  max-width: 1440px;
  margin: 0 auto 16px;
  min-height: 56px;
  display: flex;
  align-items: center;
  padding: 0 4px 14px;
  border-bottom: 1px solid var(--color-border);
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
  box-shadow: 0 2px 8px rgba(67, 56, 202, 0.2);
}

.app-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 18px;
  line-height: 1;
  font-weight: 700;
  color: var(--color-text-strong);
  font-optical-sizing: auto;
}

.app-main {
  max-width: 1440px;
  margin: 0 auto;
}

@media (max-width: 960px) {
  .app-shell {
    padding: 14px 18px 18px;
  }

  .app-header {
    margin-bottom: 10px;
    padding-bottom: 8px;
  }
}
</style>
