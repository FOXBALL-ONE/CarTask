<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">系统配置</h1>
        <p class="page__desc">修改运行参数，保存后重载使其生效</p>
      </div>
      <div class="page__actions">
        <button :disabled="loading || saving" class="button button--ghost button--sm" type="button" @click="loadConfig">
          <span class="material-icons-outlined">refresh</span>重新读取
        </button>
        <button v-if="canManage" :disabled="loading || saving" class="button button--primary button--sm"
                type="button" @click="saveConfig">
          <span class="material-icons-outlined">save</span>{{ saving ? "保存中..." : "保存配置" }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="state">正在读取配置...</div>
    <div v-else-if="loadError" class="state state--error">{{ loadError }}</div>

    <template v-else>
      <section class="card">
        <header class="card__head">
          <h2 class="card__title">跨域配置</h2>
          <span class="card__hint">CORS_ALLOWED_ORIGINS</span>
        </header>
        <div class="card__body">
          <div class="form-grid">
            <label class="field full">
              <span class="field__label">允许的来源</span>
              <input v-model.trim="form.CORS_ALLOWED_ORIGINS" class="input" :disabled="!canManage"
                     placeholder="http://localhost:8090,http://127.0.0.1:8090">
              <span class="field__hint">多个地址用英文逗号分隔。填写 * 表示放行任意来源，但此时必须关闭下面的「允许凭据」。</span>
            </label>
            <label class="field">
              <span class="field__label">允许携带凭据</span>
              <select v-model="form.CORS_ALLOW_CREDENTIALS" class="select" :disabled="!canManage">
                <option value="true">是</option>
                <option value="false">否</option>
              </select>
              <span class="field__hint">浏览器携带 Cookie 时应开启。</span>
            </label>
          </div>
        </div>
      </section>

      <section class="card">
        <header class="card__head">
          <h2 class="card__title">短信服务</h2>
          <span class="card__hint">阿里云短信</span>
        </header>
        <div class="card__body">
          <div class="form-grid">
            <label class="field">
              <span class="field__label">启用短信</span>
              <select v-model="form.SMS_ENABLED" class="select" :disabled="!canManage">
                <option value="true">是</option>
                <option value="false">否</option>
              </select>
              <span class="field__hint">关闭后不再真实发送验证码。</span>
            </label>
            <label class="field">
              <span class="field__label">跳过验证码校验</span>
              <select v-model="form.SMS_SKIP_VERIFICATION" class="select" :disabled="!canManage">
                <option value="true">是</option>
                <option value="false">否</option>
              </select>
              <span class="field__hint">临时开关，开启时登录 / 重置密码 / 换绑手机号都不再校验验证码。</span>
            </label>
            <label class="field">
              <span class="field__label">Access Key ID</span>
              <input v-model.trim="form.SMS_ACCESS_KEY_ID" class="input" :disabled="!canManage">
            </label>
            <label class="field">
              <span class="field__label">Access Key Secret</span>
              <input v-model.trim="form.SMS_ACCESS_KEY_SECRET" class="input" :disabled="!canManage"
                     placeholder="留空表示不修改" type="password" autocomplete="new-password">
              <span class="field__hint">出于安全考虑不回显明文，保持原样即不修改。</span>
            </label>
            <label class="field">
              <span class="field__label">短信签名</span>
              <input v-model.trim="form.SMS_SIGN_NAME" class="input" :disabled="!canManage">
            </label>
            <label class="field">
              <span class="field__label">模板代码</span>
              <input v-model.trim="form.SMS_TEMPLATE_CODE" class="input" :disabled="!canManage">
            </label>
          </div>
        </div>
      </section>

      <section class="card">
        <header class="card__head">
          <h2 class="card__title">文件服务</h2>
          <span class="card__hint">FILE_BASE_URL</span>
        </header>
        <div class="card__body">
          <label class="field">
            <span class="field__label">文件下载基础地址</span>
            <input v-model.trim="form.FILE_BASE_URL" class="input" :disabled="!canManage"
                   placeholder="http://192.168.1.95:8080">
            <span class="field__hint">用于拼接文件下载链接，必须是外部可访问的绝对 HTTP(S) 地址，改错会导致图片和附件打不开。</span>
          </label>
        </div>
      </section>

      <section class="card">
        <header class="card__head">
          <h2 class="card__title">车辆进出记录图片下载</h2>
          <span class="card__hint">科拓接口限速配置</span>
        </header>
        <div class="card__body">
          <div class="form-grid">
            <label class="field">
              <span class="field__label">图片下载启动间隔</span>
              <input v-model.trim="form.KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL" class="input" :disabled="!canManage"
                     placeholder="200ms">
              <span class="field__hint">两次下载启动时间的最小间隔。填写 0ms 表示不主动限速。示例：200ms、1s、30s。</span>
            </label>
            <label class="field">
              <span class="field__label">图片下载并行数</span>
              <input v-model.trim="form.KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY" class="input" :disabled="!canManage"
                     placeholder="4" type="number" min="1" max="16">
              <span class="field__hint">同时进行的图片下载任务数，范围 1 到 16。</span>
            </label>
          </div>
        </div>
      </section>

      <p v-if="saveError" class="feedback feedback--error" role="alert">
        <span class="material-icons-outlined">error</span>{{ saveError }}
      </p>

      <div class="footer-bar">
        <p class="footer-bar__note">配置保存在服务端 .env 文件中，保存后需要重载才能生效。</p>
        <button v-if="canManage" :disabled="saving" class="button button--primary" type="button" @click="saveConfig">
          <span class="material-icons-outlined">save</span>{{ saving ? "保存中..." : "保存配置" }}
        </button>
      </div>
    </template>

    <div v-if="reloadVisible" class="modal-mask">
      <div class="modal">
        <header class="modal__head">
          <h2 class="modal__title">{{ reloading ? "正在重载" : "配置已保存" }}</h2>
          <button v-if="!reloading" class="icon-button" title="稍后再说" type="button" @click="reloadVisible = false">
            <span class="material-icons-outlined">close</span>
          </button>
        </header>
        <div class="modal__body">
          <template v-if="reloading">
            <p>服务正在按新配置重启，请稍候…</p>
            <div class="progress"><span/></div>
            <p class="modal__hint">预计 10~30 秒。重启期间页面会短暂无法访问，连接恢复后自动刷新。</p>
          </template>
          <template v-else>
            <p>配置已写入 .env，但尚未生效。需要重载服务才能应用新的配置。</p>
            <p class="modal__hint">重载会重启后端进程，正在进行的同步任务会中断，约 10~30 秒后恢复。</p>
          </template>
        </div>
        <footer v-if="!reloading" class="modal__foot">
          <button class="button button--ghost" type="button" @click="reloadVisible = false">稍后手动重载</button>
          <button class="button button--primary" type="button" @click="reloadNow">立即重载</button>
        </footer>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
/** 与后端 SystemConfigService 的 EDITABLE_KEYS 白名单一一对应。 */
interface SystemConfigForm {
  CORS_ALLOWED_ORIGINS: string;
  CORS_ALLOW_CREDENTIALS: string;
  SMS_ENABLED: string;
  SMS_SKIP_VERIFICATION: string;
  SMS_ACCESS_KEY_ID: string;
  SMS_ACCESS_KEY_SECRET: string;
  SMS_SIGN_NAME: string;
  SMS_TEMPLATE_CODE: string;
  FILE_BASE_URL: string;
  KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL: string;
  KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY: string;
}

const http = useHttp();
const authStore = useAuthStore();
const {can} = usePermission();
const canManage = computed(() => can("system-config:write"));

const loading = ref(true);
const saving = ref(false);
const reloading = ref(false);
const reloadVisible = ref(false);
const loadError = ref("");
const saveError = ref("");

const form = reactive<SystemConfigForm>({
  CORS_ALLOWED_ORIGINS: "",
  CORS_ALLOW_CREDENTIALS: "true",
  SMS_ENABLED: "false",
  SMS_SKIP_VERIFICATION: "false",
  SMS_ACCESS_KEY_ID: "",
  SMS_ACCESS_KEY_SECRET: "",
  SMS_SIGN_NAME: "",
  SMS_TEMPLATE_CODE: "",
  FILE_BASE_URL: "",
  KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL: "200ms",
  KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY: "4",
});

async function loadConfig() {
  loading.value = true;
  loadError.value = "";
  saveError.value = "";
  try {
    const values = await http.get<Record<string, string>>("/system/config");
    Object.keys(form).forEach((key) => {
      const value = values?.[key];
      if (typeof value === "string") form[key as keyof SystemConfigForm] = value;
    });
  } catch (error) {
    loadError.value = (error as { statusMessage?: string }).statusMessage || "配置读取失败";
  } finally {
    loading.value = false;
  }
}

async function saveConfig() {
  saving.value = true;
  saveError.value = "";
  try {
    await http.put("/system/config", {...form}, {payloadMode: "json"});
    // 保存后服务端会把密钥重新按掩码返回，这里同步一次，避免继续拿着明文。
    await loadConfig();
    reloadVisible.value = true;
  } catch (error) {
    saveError.value = (error as { statusMessage?: string }).statusMessage || "配置保存失败";
  } finally {
    saving.value = false;
  }
}

async function reloadNow() {
  reloading.value = true;
  try {
    await http.post("/system/config/reload");
  } catch {
    // 重启信号发出后连接可能立刻断开，这里失败也继续等待恢复。
  }
  await waitForRestart();
  window.location.reload();
}

const delay = (millis: number) => new Promise((resolve) => setTimeout(resolve, millis));

/** ping：探一次服务。403/401 说明新上下文已经起来、只是当前会话无权访问，同样算恢复。 */
async function ping(): Promise<boolean> {
  try {
    await http.get("/system/config");
    return true;
  } catch (error) {
    const status = (error as { statusCode?: number }).statusCode;
    return status === 401 || status === 403;
  }
}

/**
 * waitForRestart：等到新进程真正接管为止。
 *
 * 重启分两段——旧上下文关闭、新上下文启动，而旧进程在关闭前的一小段时间里仍然会应答。
 * 所以先给旧进程最多 15 秒退场时间，这段里探到通不算数；之后的第一次成功才说明是新进程。
 */
async function waitForRestart() {
  const downDeadline = Date.now() + 15_000;
  while (Date.now() < downDeadline) {
    await delay(800);
    if (!(await ping())) break;
  }
  const upDeadline = Date.now() + 120_000;
  while (Date.now() < upDeadline) {
    await delay(1000);
    if (await ping()) return;
  }
}

onMounted(async () => {
  try {
    const user = await authStore.refreshSession();
    if (!user?.permissions.includes("system-config:read")) {
      await navigateTo("/");
      return;
    }
  } catch {
    // 读取失败时下面的请求会给出更具体的提示。
  }
  await loadConfig();
});
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px;
}

.page__header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page__title {
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.page__desc {
  color: var(--text-sub);
  margin: 2px 0 0;
}

.page__actions {
  display: flex;
  gap: 8px;
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  margin-bottom: 16px;
}

.card__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 14px 18px;
}

.card__title {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}

.card__hint {
  color: var(--text-mute);
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 11px;
}

.card__body {
  padding: 18px;
}

.state {
  color: var(--text-mute);
  padding: 48px 20px;
  text-align: center;
}

.state--error {
  color: var(--red);
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 13px;
  gap: 5px;
  height: 36px;
  justify-content: center;
  padding: 0 14px;
  white-space: nowrap;
}

.button--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
}

.button--primary {
  background: var(--primary);
  color: var(--on-solid);
}

.button--ghost {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text-sub);
}

.button:hover:not(:disabled) {
  filter: brightness(.97);
}

.button:disabled {
  cursor: not-allowed;
  opacity: .6;
}

.button .material-icons-outlined {
  font-size: 16px;
}

.form-grid {
  display: grid;
  gap: 0 16px;
  grid-template-columns: 1fr 1fr;
}

.field {
  display: block;
  margin-bottom: 16px;
}

.field__label {
  color: var(--text-sub);
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 5px;
}

.field__hint {
  color: var(--text-mute);
  display: block;
  font-size: 11px;
  line-height: 1.5;
  margin-top: 5px;
}

.input, .select {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-sizing: border-box;
  color: var(--text);
  font: inherit;
  font-size: 13px;
  height: 34px;
  outline: none;
  padding: 0 10px;
  width: 100%;
}

.input:focus, .select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft);
}

.input:disabled, .select:disabled {
  background: var(--bg);
  color: var(--text-mute);
  cursor: not-allowed;
}

.full {
  grid-column: 1 / -1;
}

.feedback {
  align-items: center;
  border-radius: 6px;
  display: flex;
  font-size: 13px;
  gap: 8px;
  margin: 0 0 16px;
  padding: 10px 12px;
}

.feedback--error {
  background: var(--danger-soft);
  color: var(--red);
}

.feedback .material-icons-outlined {
  font-size: 18px;
}

.footer-bar {
  align-items: center;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  padding: 14px 18px;
}

.footer-bar__note {
  color: var(--text-sub);
  font-size: 12px;
  margin: 0;
}

.modal-mask {
  align-items: center;
  background: rgb(0 0 0 / 40%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 300;
}

.modal {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 20%);
  max-width: 460px;
  width: 100%;
}

.modal__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 14px 20px;
}

.modal__title {
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.modal__body {
  padding: 20px;
}

.modal__body p {
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}

.modal__hint {
  color: var(--text-mute) !important;
  font-size: 12px !important;
  margin-top: 10px !important;
}

.modal__foot {
  align-items: center;
  border-top: 1px solid var(--border);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 14px 20px;
}

.progress {
  background: var(--bg);
  border-radius: 3px;
  height: 4px;
  margin: 14px 0 0;
  overflow: hidden;
}

.progress span {
  animation: slide 1.2s ease-in-out infinite;
  background: var(--primary);
  border-radius: inherit;
  display: block;
  height: 100%;
  width: 40%;
}

@keyframes slide {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(250%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .progress span {
    animation: none;
    width: 100%;
  }
}

.icon-button {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 5px;
  color: var(--text-mute);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  width: 28px;
}

.icon-button:hover {
  background: var(--bg);
  color: var(--text);
}

.icon-button .material-icons-outlined {
  font-size: 18px;
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .page__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .full {
    grid-column: auto;
  }

  .footer-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
