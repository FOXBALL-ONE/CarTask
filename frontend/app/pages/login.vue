<template>
  <section class="login-page">
    <div class="login-background" aria-hidden="true">
      <span class="background-orb orb-primary" />
      <span class="background-orb orb-secondary" />
    </div>

    <n-card class="login-card" :bordered="false" content-style="padding: 0">
      <div class="login-card__heading">
        <n-text class="login-card__eyebrow">ShopMall</n-text>
        <h1>欢迎登录</h1>
        <n-text depth="3">使用您的账户继续访问服务</n-text>
      </div>

      <n-alert v-if="errorMessage" class="login-card__alert" type="error" :show-icon="true">
        {{ errorMessage }}
      </n-alert>

      <n-form class="login-form" :show-label="false" @submit.prevent="submitLogin">
        <n-form-item :validation-status="usernameError ? 'error' : undefined" :feedback="usernameError">
          <n-input
            v-model:value="form.username"
            size="large"
            maxlength="128"
            placeholder="用户名"
            :disabled="submitting"
            @update:value="usernameError = ''"
          >
            <template #prefix>账号</template>
          </n-input>
        </n-form-item>

        <n-form-item :validation-status="passwordError ? 'error' : undefined" :feedback="passwordError">
          <n-input
            v-model:value="form.password"
            type="password"
            size="large"
            maxlength="256"
            show-password-on="click"
            placeholder="密码"
            :disabled="submitting"
            @update:value="passwordError = ''"
            @keyup.enter="submitLogin"
          >
            <template #prefix>密码</template>
          </n-input>
        </n-form-item>

        <n-button
          class="login-form__submit"
          type="primary"
          size="large"
          attr-type="submit"
          :loading="submitting"
          :disabled="submitting"
          block
        >
          登录
        </n-button>
      </n-form>

      <n-text class="login-card__hint" depth="3">登录状态仅在当前浏览器会话中保留。</n-text>
    </n-card>
  </section>
</template>

<script setup lang="ts">
definePageMeta({
  layout: "auth",
});

interface LoginUser {
  user_id: number;
  username: string;
  role: string;
}

interface LoginResponse {
  access_token: string;
  expires_at: string;
  user: LoginUser;
}

interface LoginError {
  statusCode?: number;
  statusMessage?: string;
  data?: {
    retry_after?: number;
  };
}

const route = useRoute();
const router = useRouter();
const http = useHttp();
const form = reactive({
  username: "",
  password: "",
});
const submitting = ref(false);
const errorMessage = ref("");
const usernameError = ref("");
const passwordError = ref("");

function targetPath(value: unknown): string {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) {
    return "/positions";
  }

  return value;
}

async function submitLogin() {
  errorMessage.value = "";
  usernameError.value = form.username.trim() ? "" : "请输入用户名";
  passwordError.value = form.password ? "" : "请输入密码";

  if (usernameError.value || passwordError.value) {
    return;
  }

  submitting.value = true;
  try {
    await http.post<LoginResponse, { username: string; password: string }>("/auth/login", {
      username: form.username.trim(),
      password: form.password,
    });
    form.password = "";
    await router.replace(targetPath(route.query.redirect));
  } catch (error: unknown) {
    const loginError = error as LoginError;
    const retryAfter = loginError.data?.retry_after;
    const retryMessage = typeof retryAfter === "number" && retryAfter > 0
      ? `，请 ${retryAfter} 秒后重试`
      : "";

    if (loginError.statusCode === 401) {
      errorMessage.value = `用户名或密码错误${retryMessage}`;
    } else {
      errorMessage.value = `${loginError.statusMessage || "登录失败，请稍后重试"}${retryMessage}`;
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  place-items: center;
  overflow: hidden;
  padding: 24px;
  background: linear-gradient(135deg, #eef4ff 0%, #f8faff 48%, #eef8f5 100%);
}

.login-background,
.background-orb {
  position: absolute;
  pointer-events: none;
}

.login-background {
  inset: 0;
  overflow: hidden;
}

.background-orb {
  border-radius: 999px;
  filter: blur(2px);
}

.orb-primary {
  top: -190px;
  right: -120px;
  width: 470px;
  height: 470px;
  background: rgb(24 160 88 / 16%);
}

.orb-secondary {
  bottom: -260px;
  left: -100px;
  width: 570px;
  height: 570px;
  background: rgb(32 128 240 / 15%);
}

.login-card {
  position: relative;
  width: min(100%, 420px);
  padding: 38px;
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgb(27 63 116 / 15%);
}

.login-card__heading {
  margin-bottom: 30px;
}

.login-card__eyebrow {
  display: block;
  margin-bottom: 8px;
  color: #2080f0;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-card h1 {
  margin: 0 0 8px;
  color: #1f2937;
  font-size: 28px;
  line-height: 1.3;
}

.login-card__alert {
  margin-bottom: 18px;
}

.login-form :deep(.n-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.n-input__prefix) {
  min-width: 32px;
  color: #64748b;
  font-size: 13px;
}

.login-form__submit {
  margin-top: 4px;
}

.login-card__hint {
  display: block;
  margin-top: 24px;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 480px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 30px 24px;
  }
}

:global(.n-config-provider--dark) .login-page {
  background: linear-gradient(135deg, #121b2b 0%, #111827 50%, #10251e 100%);
}

:global(.n-config-provider--dark) .login-card h1 {
  color: #f8fafc;
}
</style>
