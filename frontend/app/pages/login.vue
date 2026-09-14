<template>
  <div class="login-page">
    <div class="login-wrap">
      <!-- 页头即道闸：抬杆=放行，闭杆=待验证。这是全页唯一的动效落点。 -->
      <header class="login__head">
        <div class="gate" :class="{ 'gate--open': gateOpen }" aria-hidden="true">
          <span class="gate__lane" />
          <span class="gate__post" />
          <span class="gate__light" />
          <span class="gate__arm" />
        </div>
        <div class="login__brand">
          <div id="loginTitle" class="login__title">{{ sysName }}</div>
          <p class="login__subtitle">车辆出入管理平台</p>
        </div>
      </header>

      <!-- 两种登录方式：密码登录与短信登录（短信登录同样需要手机号 + 图形验证码） -->
      <div class="login__tabs" role="tablist">
        <button
          id="loginTabPassword"
          type="button"
          role="tab"
          class="login__tab"
          :class="{ 'login__tab--active': mode === 'password' }"
          aria-controls="loginForm"
          :aria-selected="mode === 'password'"
          @click="switchMode('password')"
        >
          密码登录
        </button>
        <button
          id="loginTabSms"
          type="button"
          role="tab"
          class="login__tab"
          :class="{ 'login__tab--active': mode === 'sms' }"
          aria-controls="smsLoginForm"
          :aria-selected="mode === 'sms'"
          @click="switchMode('sms')"
        >
          短信登录
        </button>
      </div>

      <form
        v-show="mode === 'password'"
        id="loginForm"
        role="tabpanel"
        aria-labelledby="loginTabPassword"
        autocomplete="off"
        @submit.prevent="submitLogin"
      >
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
          :disabled="busy || authStore.captchaLoading || !authStore.captchaToken"
        >{{ busy ? "登录中..." : "登 录" }}</button>
        <div v-show="authStore.errorMessage" id="loginError" class="login__error">{{ authStore.errorMessage }}</div>
      </form>

      <form
        v-show="mode === 'sms'"
        id="smsLoginForm"
        role="tabpanel"
        aria-labelledby="loginTabSms"
        autocomplete="off"
        @submit.prevent="submitSmsLogin"
      >
        <div class="login__field">
          <label class="login__label" for="smsPhone">手机号</label>
          <div class="login__input-wrap">
            <span class="material-icons-outlined">smartphone</span>
            <input
              id="smsPhone"
              v-model="smsForm.phone"
              type="tel"
              class="login__input"
              placeholder="请输入手机号"
              maxlength="20"
            >
          </div>
        </div>
        <div class="login__field">
          <label class="login__label" for="smsCaptcha">图形验证码</label>
          <div class="login__captcha-row">
            <div class="login__input-wrap">
              <span class="material-icons-outlined">verified</span>
              <input
                id="smsCaptcha"
                v-model="smsForm.captcha"
                type="text"
                class="login__input"
                placeholder="请输入验证码"
                maxlength="4"
              >
            </div>
            <button
              id="smsCaptchaBox"
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
        <div class="login__field">
          <label class="login__label" for="smsCode">短信验证码</label>
          <div class="login__captcha-row">
            <div class="login__input-wrap">
              <span class="material-icons-outlined">sms</span>
              <input
                id="smsCode"
                v-model="smsForm.code"
                type="text"
                class="login__input"
                placeholder="请输入短信验证码"
                inputmode="numeric"
                maxlength="6"
              >
            </div>
            <button
              id="smsSendBtn"
              type="button"
              class="login__sms-btn"
              :disabled="smsSendDisabled"
              @click="sendSmsCode"
            >{{ smsSendText }}</button>
          </div>
        </div>
        <button
          type="submit"
          class="login__btn"
          :disabled="busy"
        >{{ busy ? "登录中..." : "登 录" }}</button>
        <div v-show="authStore.errorMessage" id="smsLoginError" class="login__error">{{ authStore.errorMessage }}</div>
      </form>

      <div class="login__hint">
        演示账号：<code>admin</code> / <code>123456</code><br>
        其他用户：<code>zhangsan</code> / <code>123456</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 页面结构与字段沿用原型 20260625115857/login.html，视觉层按「夜间停车场道闸」重做：
// 深色柏油底 + 车位线，页头是一根可抬起的栏杆，登录成功即抬杆放行。
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
const smsForm = reactive({
  phone: "",
  captcha: "",
  code: "",
});
const mode = ref<"password" | "sms">("password");
/** 登录成功后的抬杆动效状态；期间按钮保持「登录中」避免闪回可点。 */
const gateOpen = ref(false);
const busy = computed(() => authStore.loading || gateOpen.value);
// 与后端 SmsVerificationService.SEND_INTERVAL 保持一致的重发倒计时。
const SMS_RESEND_SECONDS = 60;
const smsCountdown = ref(0);
const smsSendDisabled = computed(
  () => smsCountdown.value > 0 || authStore.smsSending || busy.value || authStore.captchaLoading,
);
const smsSendText = computed(() => {
  if (smsCountdown.value > 0) {
    return `${smsCountdown.value}s 后重发`;
  }

  return authStore.smsSending ? "发送中..." : "获取验证码";
});
// 与后端 SmsVerificationService.normalize 相同的手机号格式。
const PHONE_PATTERN = /^\+?[0-9]{6,20}$/;
let countdownTimer: ReturnType<typeof setInterval> | null = null;

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

/** 登录成功后的落点：初始密码未修改时必须先去改密，不能进入任何业务页面。 */
async function goAfterLogin() {
  if (authStore.mustChangePassword) {
    await router.replace({ path: "/profile", query: { tab: "password" } });
    return;
  }
  await router.replace(targetPath());
}

/** 抬杆放行：留出一小段过闸动效再跳转；用户偏好减少动效时直接放行。 */
async function passGate() {
  if (import.meta.client && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    return;
  }
  gateOpen.value = true;
  await new Promise((resolve) => setTimeout(resolve, 440));
}

// 从后端获取验证码；点击图片可刷新。与原版一致：刷新期间保留旧图，失败才显示「获取失败」。
async function genCaptcha() {
  await authStore.refreshCaptcha();
}

function stopCountdown() {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown() {
  stopCountdown();
  smsCountdown.value = SMS_RESEND_SECONDS;
  countdownTimer = setInterval(() => {
    smsCountdown.value -= 1;
    if (smsCountdown.value <= 0) {
      stopCountdown();
      smsCountdown.value = 0;
    }
  }, 1000);
}

// 切换登录方式：清空上一次的错误与图形验证码答案，并换一张新验证码。
// 短信发送会一次性消费图形验证码，切回来时旧 token 可能已失效，必须刷新。
function switchMode(next: "password" | "sms") {
  if (mode.value === next) {
    return;
  }

  mode.value = next;
  authStore.clearError();
  form.captcha = "";
  smsForm.captcha = "";
  void genCaptcha();
}

// 已登录（存在 JWT）则直接进入系统，对等原版 sessionStorage.loggedIn 跳转；
// 在 setup 阶段同步执行，避免先绘制登录页再跳转。
if (authStore.isAuthenticated) {
  await navigateTo(targetPath(), { replace: true });
}

/**
 * 系统尚未初始化时把操作者送到配置引导页。
 *
 * 没有数据库就没有账号，连图形验证码都取不到，登录页在这个阶段是一条死路：留在原地只会让人以为
 * 服务坏了。问一句后端就能把这条死路换成一句明确的「先配系统」。
 */
async function redirectToSetupIfNeeded() {
  try {
    const status = await useHttp().get<{ setup_required: boolean }>("/setup/status");
    if (status.setup_required) {
      await navigateTo("/setup", { replace: true });
    }
  } catch {
    // 后端不可达或该接口不存在（正常模式下才有 /setup/status，配置模式下才有真实的 true）：
    // 留在登录页，登录失败时的提示已经足够说明问题。
  }
}

onMounted(() => {
  const storedSysName = localStorage.getItem("sysName");
  if (storedSysName) {
    sysName.value = storedSysName;
  }

  authStore.restoreSession();
  void genCaptcha();
  void redirectToSetupIfNeeded();
});

onUnmounted(stopCountdown);

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

    await passGate();
    await goAfterLogin();
  } catch {
    // 与原版一致：失败后刷新验证码并清空输入。
    form.captcha = "";
    void genCaptcha();
  }
}

/** 发送短信验证码：手机号与图形验证码缺一不可。 */
async function sendSmsCode() {
  authStore.clearError();
  const phone = smsForm.phone.trim();
  const captchaAnswer = smsForm.captcha.trim();

  if (!phone) {
    authStore.setError("请输入手机号");
    return;
  }
  if (!PHONE_PATTERN.test(phone)) {
    authStore.setError("手机号格式无效");
    return;
  }
  if (!captchaAnswer) {
    authStore.setError("请输入图形验证码答案");
    return;
  }

  try {
    await authStore.sendSmsCode({ phone, captchaAnswer });
  } catch {
    // 校验未通过：换一张验证码并清空答案，避免拿旧 token 反复重试。
    smsForm.captcha = "";
    void genCaptcha();
    return;
  }

  // 发送成功，图形验证码已在后端一次性消费，换新并开始重发倒计时。
  smsForm.captcha = "";
  void genCaptcha();
  startCountdown();
}

/** 短信登录：手机号 + 图形验证码（换取短信验证码时校验）+ 短信验证码。 */
async function submitSmsLogin() {
  authStore.clearError();
  const phone = smsForm.phone.trim();
  const code = smsForm.code.trim();

  if (!phone) {
    authStore.setError("请输入手机号");
    return;
  }
  if (!PHONE_PATTERN.test(phone)) {
    authStore.setError("手机号格式无效");
    return;
  }
  if (!code) {
    authStore.setError("请输入短信验证码");
    return;
  }

  try {
    await authStore.smsLogin({ phone, code });

    await passGate();
    await goAfterLogin();
  } catch {
    // 短信验证码在校验时即被作废，失败后必须重新获取。
    smsForm.code = "";
  }
}
</script>

<style scoped>
/* ==========================================================================
   设计方向：停车场道闸（浅色）
   页面是入口（闸口），卡片是岗亭终端，页头的栏杆抬起来就是「放行」。
   浅色混凝土地面 + 车位线透视 + 车牌蓝的动作色，
   全页唯一的高饱和色是栏杆上的路面标线黄，只出现在这一个地方。
   配色集中在下面这组变量里，换主题只改这一段。
   ========================================================================== */
.login-page {
  /* 变量带 g- 前缀，避免与全局主题变量（--primary/--text 等）互相干扰 */
  --g-bg: #eef2f7;                 /* 白天场地：浅混凝土 */
  --g-bg-glow: rgb(37 99 235 / 12%); /* 顶部天光 */
  --g-lane: rgb(15 23 42 / 8%);     /* 地面车位线 */
  --g-panel: #ffffff;
  --g-surface: rgb(15 23 42 / 4%);  /* 分段控件轨道等次级面 */
  --g-inset: #f8fafc;               /* 验证码底 */
  --g-line: rgb(15 23 42 / 8%);
  --g-line-strong: rgb(15 23 42 / 14%);
  --g-text: #0f172a;
  --g-sub: #475569;
  /* 占位符与页脚说明都用这个色，必须过 AA 正文对比度（4.5:1 @ 12px） */
  --g-mute: #64748b;
  --g-accent: #3b82f6;
  --g-accent-deep: #2563eb;
  --g-accent-text: #1d4ed8;         /* 浅底上的蓝色文字/按钮字 */
  --g-mark: #f5c518;                /* 栏杆黄：路面标线色 */
  --g-post: #64748b;
  --g-danger: #dc2626;
  --g-danger-bg: #fef2f2;
  --g-danger-border: #fecaca;
  --g-card-shadow: 0 18px 48px rgb(15 23 42 / 12%);
  --g-tr: .18s ease;

  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh; /* 与 .app-shell 一致，移动端不会被地址栏高度截断 */
  background: var(--g-bg);
  padding: 24px;
  position: relative;
  overflow: hidden;
  /* styles.css body 字体；CJK 用系统栈比拉网络字体更清晰也更快 */
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  /* preflight 强制 line-height: 1.5，还原浏览器默认 normal */
  line-height: normal;
  touch-action: manipulation;
  color: var(--g-text);
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}

/* 地面：车位分隔线做透视，向远处收拢并淡出 */
.login-page::before {
  content: "";
  position: absolute;
  inset: 54% -30% -12% -30%;
  background-image: repeating-linear-gradient(90deg, var(--g-lane) 0 1px, transparent 1px 96px);
  transform: perspective(520px) rotateX(64deg);
  transform-origin: bottom center;
  -webkit-mask-image: linear-gradient(to top, rgb(0 0 0 / 85%), transparent 80%);
  mask-image: linear-gradient(to top, rgb(0 0 0 / 85%), transparent 80%);
  pointer-events: none;
}

/* 顶部天光：把视线收到卡片上 */
.login-page::after {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(58% 40% at 50% 0%, var(--g-bg-glow), transparent 72%);
  pointer-events: none;
}

.login-wrap {
  position: relative;
  z-index: 1;
  width: 400px;
  max-width: 100%;
  background: var(--g-panel);
  border: 1px solid var(--g-line-strong);
  border-radius: 16px;
  box-shadow: var(--g-card-shadow);
  padding: 26px 30px 30px;
  animation: loginIn .45s cubic-bezier(.22, .9, .28, 1);
}
@keyframes loginIn { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: translateY(0); } }

/* ====== 页头：道闸 + 品牌 ====== */
.login__head { align-items: center; display: flex; gap: 14px; margin-bottom: 22px; }
.login__brand { min-width: 0; }
.login__title { color: var(--g-text); font-size: 20px; font-weight: 700; letter-spacing: .01em; margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.login__subtitle { color: var(--g-mute); font-size: 12px; letter-spacing: .08em; margin: 4px 0 0; }

.gate { flex: 0 0 auto; height: 68px; position: relative; width: 78px; }
/* 车道边线 */
.gate__lane { background: var(--g-line-strong); bottom: 5px; height: 1px; left: 0; position: absolute; right: 0; }
/* 立柱 */
.gate__post { background: var(--g-post); border-radius: 2px; bottom: 5px; height: 22px; position: absolute; right: 0; width: 8px; }
/* 立柱指示灯：闭杆红灯，抬杆绿灯 */
.gate__light { background: #ef4444; border-radius: 50%; bottom: 20px; box-shadow: 0 0 5px rgb(239 68 68 / 45%); height: 4px; position: absolute; right: 2px; transition: background .3s ease, box-shadow .3s ease; width: 4px; }
.gate--open .gate__light { background: #22c55e; box-shadow: 0 0 6px rgb(34 197 94 / 55%); }
/* 栏杆：黄黑标线，绕右端转轴抬起 */
.gate__arm {
  background: repeating-linear-gradient(115deg, var(--g-mark) 0 8px, #1f2937 8px 16px);
  border-radius: 3px;
  bottom: 25px;
  height: 5px;
  position: absolute;
  right: 3px;
  transform: rotate(0deg);
  transform-origin: right center;
  transition: transform .42s cubic-bezier(.22, .9, .28, 1);
  width: 44px;
}
.gate--open .gate__arm { transform: rotate(-58deg); }

/* ====== 登录方式：分段控件 ====== */
.login__tabs {
  background: var(--g-surface);
  border: 1px solid var(--g-line);
  border-radius: 10px;
  display: grid;
  gap: 4px;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 22px;
  padding: 4px;
}
.login__tab {
  background: transparent;
  border: 0;
  border-radius: 7px;
  color: var(--g-sub);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  height: 32px;
  transition: background var(--g-tr), color var(--g-tr);
}
.login__tab:hover:not(.login__tab--active) { color: var(--g-text); }
.login__tab--active { background: var(--g-panel); box-shadow: 0 1px 3px rgb(15 23 42 / 14%); color: var(--g-accent-text); }

/* ====== 表单 ====== */
.login__field { margin-bottom: 14px; }
.login__label { color: var(--g-sub); display: block; font-size: 12px; font-weight: 500; margin-bottom: 6px; }
.login__input-wrap { position: relative; }
.login__input-wrap .material-icons-outlined { color: var(--g-mute); font-size: 19px; left: 12px; position: absolute; top: 50%; transform: translateY(-50%); }
.login__input {
  width: 100%;
  height: 44px;
  padding: 0 12px 0 40px;
  background: var(--g-panel);
  border: 1px solid var(--g-line-strong);
  border-radius: 9px;
  color: var(--g-text);
  font-size: 14px;
  outline: none;
  transition: background var(--g-tr), border-color var(--g-tr), box-shadow var(--g-tr);
}
.login__input::placeholder { color: var(--g-mute); opacity: 1; }
.login__input:focus-visible, .login__input:focus {
  border-color: var(--g-accent);
  box-shadow: 0 0 0 3px rgb(59 130 246 / 15%);
}

.login__captcha-row { align-items: center; display: flex; gap: 10px; }
.login__captcha-row .login__input-wrap { flex: 1; }
/* 验证码图：浅底保证识别度 */
.login__captcha {
  align-items: center;
  background: var(--g-inset);
  border: 1px solid var(--g-line-strong);
  border-radius: 9px;
  color: var(--g-sub);
  cursor: pointer;
  display: flex;
  flex: 0 0 auto;
  font: inherit;
  height: 44px;
  justify-content: center;
  min-width: 120px;
  overflow: hidden;
  padding: 0;
  transition: opacity var(--g-tr);
  user-select: none;
  width: 120px;
}
.login__captcha:disabled { cursor: wait; opacity: .7; }
.login__captcha img { height: 100%; object-fit: cover; width: 100%; }
.login__captcha-fallback { color: var(--g-sub); font-size: 12px; line-height: 1.4; padding: 0 8px; }

/* 获取短信验证码 */
.login__sms-btn {
  background: rgb(37 99 235 / 8%);
  border: 1px solid rgb(37 99 235 / 28%);
  border-radius: 9px;
  color: var(--g-accent-text);
  cursor: pointer;
  flex: 0 0 auto;
  font: inherit;
  font-size: 13px;
  height: 44px;
  min-width: 120px;
  padding: 0;
  transition: background var(--g-tr), border-color var(--g-tr), color var(--g-tr);
  width: 120px;
}
.login__sms-btn:hover:not(:disabled) { background: rgb(37 99 235 / 14%); border-color: rgb(37 99 235 / 45%); }
.login__sms-btn:disabled { background: var(--g-surface); border-color: var(--g-line); color: var(--g-mute); cursor: not-allowed; }

/* 按钮渐变只取深蓝两档：白字在 #3b82f6 上只有 3.7:1，不到 AA 正文要求 */
.login__btn {
  background: linear-gradient(180deg, var(--g-accent-deep), var(--g-accent-text));
  border: 0;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgb(37 99 235 / 28%);
  color: #fff;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  height: 46px;
  letter-spacing: .04em;
  margin-top: 8px;
  transition: box-shadow var(--g-tr), filter var(--g-tr), transform var(--g-tr);
  width: 100%;
}
.login__btn:hover:not(:disabled) { box-shadow: 0 10px 26px rgb(37 99 235 / 34%); filter: brightness(1.04); transform: translateY(-1px); }
.login__btn:active:not(:disabled) { box-shadow: 0 6px 16px rgb(37 99 235 / 26%); transform: translateY(0); }
.login__btn:disabled { box-shadow: none; cursor: not-allowed; opacity: .5; }

.login__error {
  background: var(--g-danger-bg);
  border: 1px solid var(--g-danger-border);
  border-radius: 8px;
  color: var(--g-danger);
  font-size: 12.5px;
  margin-top: 14px;
  padding: 8px 12px;
  text-align: center;
}

.login__hint { color: var(--g-mute); font-size: 12px; line-height: 1.7; margin-top: 20px; text-align: center; }
.login__hint code {
  background: var(--g-surface);
  border: 1px solid var(--g-line);
  border-radius: 5px;
  color: var(--g-sub);
  font-family: ui-monospace, Consolas, 'SFMono-Regular', monospace;
  font-size: 11.5px;
  padding: 2px 6px;
}

/* styles.css 中 .material-icons-outlined 基础规则 */
.material-icons-outlined { font-size: 18px; user-select: none; vertical-align: middle; }

/* 键盘可达性：所有可交互元素保留可见焦点环 */
.login__tab:focus-visible,
.login__sms-btn:focus-visible,
.login__captcha:focus-visible,
.login__btn:focus-visible {
  outline: 2px solid var(--g-accent);
  outline-offset: 2px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .login-page { padding: 16px; }
  .login-wrap { max-width: 420px; padding: 22px 22px 26px; width: 100%; }
  .login__title { font-size: 19px; }
  /* 16px 以下 iOS 会自动放大表单，必须保持 16px */
  .login__input, .login__btn, .login__captcha, .login__sms-btn { font-size: 16px; height: 48px; }
  .login__input { padding: 0 12px 0 42px; }
  .login__captcha, .login__sms-btn { min-width: 112px; width: 112px; }
  .login__sms-btn { font-size: 14px; }
  .login__label, .login__hint { font-size: 13px; }
}

@media (max-width: 480px) {
  .login-wrap { padding: 20px 18px 24px; }
  .login__head { gap: 10px; margin-bottom: 18px; }
  .gate { height: 60px; width: 66px; }
  .gate__arm { width: 38px; }
  .login__captcha-row { flex-direction: column; align-items: stretch; gap: 10px; }
  .login__captcha, .login__sms-btn { min-width: 100%; width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  .login-wrap { animation: none; }
  .gate__arm, .gate__light, .login__btn { transition: none; }
  .login__btn:hover:not(:disabled) { transform: none; }
}
</style>
