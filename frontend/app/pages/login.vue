<template>
  <div class="login-page">
    <div class="login-wrap">
      <div id="loginTitle" class="login__title">{{ sysName }}</div>
      <form id="loginForm" autocomplete="off" @submit.prevent="submitLogin">
        <div class="login__field">
          <label class="login__label" for="loginUser">用户名</label>
          <div class="login__input-wrap">
            <span class="material-icons-outlined">person</span>
            <input
              id="loginUser"
              v-model="form.username"
              type="text"
              class="login__input"
              placeholder="请输入用户名"
            >
          </div>
        </div>
        <div class="login__field">
          <label class="login__label" for="loginPass">密码</label>
          <div class="login__input-wrap">
            <span class="material-icons-outlined">lock</span>
            <input
              id="loginPass"
              v-model="form.password"
              type="password"
              class="login__input"
              placeholder="请输入密码"
            >
          </div>
        </div>
        <div class="login__field">
          <label class="login__label" for="loginCaptcha">验证码</label>
          <div class="login__captcha-row">
            <div class="login__input-wrap">
              <span class="material-icons-outlined">verified</span>
              <input
                id="loginCaptcha"
                v-model="form.captcha"
                type="text"
                class="login__input"
                placeholder="请输入验证码"
                maxlength="4"
              >
            </div>
            <button
              id="captchaBox"
              type="button"
              class="login__captcha"
              :disabled="authStore.captchaLoading"
              aria-label="刷新验证码"
              @click="genCaptcha"
            >
              <img v-if="authStore.captchaImage" :src="authStore.captchaImage" alt="验证码" >
              <span v-else-if="authStore.captchaFailed" class="login__captcha-fallback">获取失败，点击重试</span>
              <span v-else class="login__captcha-fallback">加载中...</span>
            </button>
          </div>
        </div>
        <button
          type="submit"
          class="login__btn"
          :disabled="authStore.loading || authStore.captchaLoading || !authStore.captchaToken"
        >{{ authStore.loading ? "登录中..." : "登 录" }}</button>
        <div v-show="authStore.errorMessage" id="loginError" class="login__error">{{ authStore.errorMessage }}</div>
      </form>
      <div class="login__hint">
        演示账号：<code>admin</code> / <code>123456</code><br>
        其他用户：<code>zhangsan</code> / <code>123456</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// UI 1:1 复刻自原型 20260625115857/login.html，样式必须与原版保持一致。
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

// 系统名称（可由系统设置修改），与原版 localStorage.sysName 逻辑一致。
const sysName = ref("Admin Pro");
const form = reactive({
  username: "",
  password: "",
  captcha: "",
});
useHead({
  title: () => `登录 - ${sysName.value}`,
  link: [
    {
      rel: "stylesheet",
      href: "https://fonts.googleapis.com/icon?family=Material+Icons+Outlined",
    },
  ],
});

function targetPath(): string {
  const value = route.query.redirect;
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) {
    return "/";
  }

  return value;
}

// 从后端获取验证码；点击图片可刷新。与原版一致：刷新期间保留旧图，失败才显示「获取失败」。
async function genCaptcha() {
  await authStore.refreshCaptcha();
}

// 已登录（存在 JWT）则直接进入系统，对等原版 sessionStorage.loggedIn 跳转；
// 在 setup 阶段同步执行，避免先绘制登录页再跳转。
if (authStore.isAuthenticated) {
  await navigateTo(targetPath(), { replace: true });
}

onMounted(() => {
  const storedSysName = localStorage.getItem("sysName");
  if (storedSysName) {
    sysName.value = storedSysName;
  }

  authStore.restoreSession();
  void genCaptcha();
});

async function submitLogin() {
  authStore.clearError();
  const username = form.username.trim();
  const password = form.password.trim();
  const captchaAnswer = form.captcha.trim();

  if (!captchaAnswer) {
    authStore.setError("请输入验证码答案");
    return;
  }

  try {
    await authStore.login({
      username,
      password,
      captchaAnswer,
    });

    await router.replace(targetPath());
  } catch {
    // 与原版一致：失败后刷新验证码并清空输入。
    form.captcha = "";
    void genCaptcha();
  }
}
</script>

<style scoped>
/* ====== styles.css 基础变量（与原版 :root 完全一致，作用于本页根节点） ====== */
.login-page {
  /* 原型 body 样式 */
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  /* styles.css body 字体，避免 Tailwind preflight 改变字体渲染 */
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  /* preflight 强制 line-height: 1.5，原版为浏览器默认 normal，必须还原 */
  line-height: normal;
  /* styles.css body 的 touch-action，抑制移动端双击缩放 */
  touch-action: manipulation;
  color: var(--text);
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;

  --primary: #2563eb;
  --primary-soft: #eff4ff;
  --bg: #fafafa;
  --card: #ffffff;
  --text: #18181b;
  --text-sub: #71717a;
  --text-mute: #a1a1aa;
  --border: #f0f0f0;
  --border-strong: #e4e4e7;
  --blue: #2563eb;
  --green: #059669;
  --green-soft: #f0fdf4;
  --red: #dc2626;
  --red-soft: #fef2f2;
  --orange: #ea580c;
  --orange-soft: #fff7ed;
  --radius: 8px;
  --sidebar-w: 220px;
  --sidebar-w-min: 64px;
  --topbar-h: 56px;
  --tr: .2s ease;
}

/* ====== 以下样式与原型 login.html 逐条一致 ====== */
.login-wrap {
  width: 380px; max-width: 100%;
  background: var(--card);
  border-radius: 0;
  box-shadow: 0 20px 60px rgba(0,0,0,.15);
  padding: 36px;
  animation: loginIn .4s ease;
}
@keyframes loginIn { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
.login__title { text-align: center; font-size: 22px; font-weight: 700; color: var(--text); margin-bottom: 28px; }
.login__field { margin-bottom: 18px; }
.login__label { display: block; font-size: 13px; color: var(--text-sub); margin-bottom: 6px; font-weight: 500; }
.login__input-wrap { position: relative; }
.login__input-wrap .material-icons-outlined { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); font-size: 20px; color: var(--text-mute); }
.login__input {
  width: 100%; height: 44px; padding: 0 12px 0 40px;
  border: 1px solid var(--border-strong); border-radius: 0;
  font-size: 14px; color: var(--text); background: var(--card);
  transition: border-color var(--tr), box-shadow var(--tr);
  outline: none;
}
.login__input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(37,99,235,.1); }
.login__captcha-row { display: flex; gap: 10px; align-items: center; }
.login__captcha-row .login__input-wrap { flex: 1; }
.login__captcha {
  min-width: 120px;
  width: 120px;
  height: 44px;
  border: 1px solid var(--border-strong);
  border-radius: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  background: #f9fafb;
  flex-shrink: 0;
  overflow: hidden;
  padding: 0;
  color: var(--text-sub);
  font: inherit;
}
.login__captcha:disabled { cursor: wait; opacity: .7; }
.login__captcha-fallback { padding: 0 8px; font-size: 12px; line-height: 1.4; }
.login__captcha img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.login__btn {
  width: 100%; height: 44px; border: none; border-radius: 0;
  background: var(--primary); color: #fff; font-size: 15px; font-weight: 600;
  cursor: pointer; transition: opacity var(--tr);
  margin-top: 6px;
}
.login__btn:hover { opacity: .9; }
.login__error { color: var(--red); font-size: 13px; text-align: center; margin-top: 12px; }
.login__hint { text-align: center; font-size: 12px; color: var(--text-mute); margin-top: 20px; line-height: 1.6; }
.login__hint code { background: var(--bg); padding: 2px 6px; border-radius: 0; font-size: 12px; /* 抵消 preflight 的 mono 字体栈，还原浏览器默认等宽字体 */ font-family: monospace; }

/* styles.css 中 .material-icons-outlined 基础规则 */
.material-icons-outlined {
  font-size: 18px;
  vertical-align: middle;
  user-select: none;
}

/* 对齐浏览器默认占位符颜色，抵消 Tailwind preflight 的 placeholder 样式 */
.login__input::placeholder {
  color: #757575;
  opacity: 1;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .login-page { padding: 16px; }
  .login-wrap {
    width: 100%;
    max-width: 400px;
    padding: 28px 24px;
  }
  .login__title { font-size: 20px; margin-bottom: 24px; }
  .login__input, .login__btn, .login__captcha { height: 48px; font-size: 16px; }
  .login__input { padding: 0 12px 0 42px; }
  .login__captcha { min-width: 110px; width: 110px; }
  .login__label { font-size: 14px; }
  .login__hint { font-size: 13px; }
}

@media (max-width: 480px) {
  .login-wrap { padding: 24px 20px; }
  .login__captcha-row { flex-direction: column; gap: 12px; }
  .login__captcha { width: 100%; min-width: 100%; max-width: 100%; }
}
</style>
