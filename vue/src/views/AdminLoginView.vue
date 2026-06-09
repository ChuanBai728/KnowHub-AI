<template>
  <section class="login-shell">
    <div class="login-panel">
      <div class="login-copy">
        <div class="brand-lockup">
          <div class="brand-mark">KH</div>
          <span class="brand-name">KnowHub AI</span>
        </div>
        <h1>进入管理后台工作台</h1>
        <p class="login-description">
          管理文档接入、知识路由与对话观测。账号和密码由当前部署环境配置，登录后才能进入后台。
        </p>
        <div class="copy-accent"></div>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <div class="form-header">
          <p>后台入口</p>
          <h2>管理台登录</h2>
        </div>

        <label class="field">
          <span>账号</span>
          <input v-model="form.username" type="text" placeholder="请输入后台账号" autocomplete="username" />
        </label>

        <label class="field">
          <span>密码</span>
          <input v-model="form.password" type="password" placeholder="请输入后台密码" autocomplete="current-password" />
        </label>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

        <div class="form-actions">
          <button class="secondary-button" type="button" @click="goBackChat">返回聊天</button>
          <button class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '登录中...' : '进入管理台' }}
          </button>
        </div>
      </form>
    </div>

  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminAuthApi, APIError } from '../api/api'
import { saveAdminAuth } from '../utils/adminAuth'

const router = useRouter()
const route = useRoute()

const form = reactive({
  username: 'admin',
  password: 'KnowHub2024!'
})
const errorMessage = ref('')
const submitting = ref(false)

async function submitLogin() {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password.trim()) {
    errorMessage.value = '请输入账号和密码。'
    return
  }

  submitting.value = true
  try {
    const result = await adminAuthApi.login({
      username: form.username.trim(),
      password: form.password
    })
    saveAdminAuth({
      username: result?.username || form.username.trim(),
      token: result?.token || ''
    })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/admin')
      ? route.query.redirect
      : '/admin/dashboard'
    router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof APIError || error instanceof Error
      ? error.message
      : '登录失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

function goBackChat() {
  router.push('/chat')
}
</script>

<style scoped>
.login-shell {
  position: relative;
  min-height: 100vh;
  padding: 32px;
  display: grid;
  place-items: center;
  background: var(--color-bg);
  background-image:
    radial-gradient(ellipse at 20% 50%, rgba(67, 56, 202, 0.04), transparent 60%),
    radial-gradient(ellipse at 80% 20%, rgba(224, 96, 64, 0.03), transparent 50%);
}

.login-panel {
  width: min(960px, 100%);
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.login-copy {
  background: var(--color-surface);
  border-radius: var(--radius-lg) 0 0 var(--radius-lg);
  padding: 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  position: relative;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(67, 56, 202, 0.2);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-strong);
}

.copy-accent {
  position: absolute;
  bottom: 0;
  left: 44px;
  right: 44px;
  height: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  border-radius: 3px 3px 0 0;
  opacity: 0.4;
}

.login-copy h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-strong);
  line-height: 1.3;
  font-optical-sizing: auto;
}

.login-description {
  max-width: 580px;
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-muted-strong);
}

.login-form {
  background: var(--color-surface);
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  border-left: 1px solid var(--color-border);
  padding: 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header p {
  margin: 0;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.form-header h2 {
  margin: 8px 0 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-strong);
  font-optical-sizing: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
}

.field span {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-muted-strong);
}

.field input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 11px 14px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.field input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft), var(--shadow-sm);
}

.error-message {
  margin: 16px 0 0;
  color: var(--color-danger);
  font-size: 14px;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.primary-button,
.secondary-button {
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: 11px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease, box-shadow 0.2s ease;
}

.primary-button {
  flex: 2;
}

.secondary-button {
  flex: 1;
}

.primary-button {
  color: #ffffff;
  background: var(--color-primary);
  box-shadow: 0 2px 8px rgba(67, 56, 202, 0.2);
}

.primary-button:hover {
  opacity: 0.92;
  box-shadow: 0 4px 16px rgba(67, 56, 202, 0.25);
}

.secondary-button {
  color: var(--color-text);
  background: var(--color-surface);
  border-color: var(--color-border);
}

.secondary-button:hover {
  background: var(--color-surface-soft);
  border-color: var(--color-border-strong);
}

@media (max-width: 960px) {
  .login-shell {
    padding: 18px;
  }

  .login-panel {
    grid-template-columns: 1fr;
  }

  .login-copy {
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  }

  .login-form {
    border-left: none;
    border-top: 1px solid var(--color-border);
    border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  }

  .login-copy,
  .login-form {
    padding: 28px;
  }

  .copy-accent {
    left: 28px;
    right: 28px;
  }
}
</style>
