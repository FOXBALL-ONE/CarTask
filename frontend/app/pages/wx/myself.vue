<template>
  <main class="myself-page">
    <header class="page-header">
      <button aria-label="返回微信首页" class="back-button" type="button" @click="navigateTo('/wx')">
        <span aria-hidden="true"/>
      </button>
      <h1>用户中心</h1>
      <span aria-hidden="true" class="header-spacer"/>
    </header>

    <div class="page-content">
      <section v-if="mustChangePassword" class="security-notice" role="status">
        <span aria-hidden="true" class="notice-mark">!</span>
        <div>
          <strong>请先修改初始密码</strong>
          <p>为了账号安全，修改密码后才能继续使用。</p>
        </div>
      </section>

      <section aria-labelledby="account-title" class="account-section">
        <div class="section-heading">
          <div>
            <p class="section-eyebrow">ACCOUNT</p>
            <h2 id="account-title">基础信息</h2>
          </div>
          <span class="section-count">{{ profile ? "已登录" : "" }}</span>
        </div>

        <div v-if="loading" aria-label="正在加载用户信息" class="account-card account-card--loading" role="status">
          <div class="skeleton-avatar"/>
          <div class="skeleton-copy">
            <span/>
            <span/>
          </div>
          <div class="skeleton-rows">
            <span v-for="index in 6" :key="index"/>
          </div>
        </div>

        <div v-else-if="loadError" class="state-card" role="alert">
          <span aria-hidden="true" class="state-mark">!</span>
          <strong>暂时无法获取用户信息</strong>
          <p>{{ loadError }}</p>
          <button class="retry-button" type="button" @click="loadProfile">重新加载</button>
        </div>

        <div v-else-if="profile" class="account-card">
          <div class="identity">
            <div aria-hidden="true" class="avatar">
              <img v-if="avatarSrc && !avatarBroken" :src="avatarSrc" alt="" @error="avatarBroken = true">
              <span v-else>{{ initial }}</span>
            </div>
            <div class="identity-copy">
              <h3>{{ displayName }}</h3>
              <p>@{{ profile.username }}</p>
            </div>
            <span aria-hidden="true" class="identity-dot"/>
          </div>

          <dl class="profile-details">
            <div class="detail-row">
              <dt>用户编号</dt>
              <dd>{{ profile.user_id }}</dd>
            </div>
            <div class="detail-row">
              <dt>手机号</dt>
              <dd>{{ profile.phone || "未绑定" }}</dd>
            </div>
            <div class="detail-row">
              <dt>邮箱</dt>
              <dd>{{ profile.email || "未填写" }}</dd>
            </div>
            <div class="detail-row">
              <dt>所属部门</dt>
              <dd>{{ profile.department_name || "未设置" }}</dd>
            </div>
            <div class="detail-row">
              <dt>账号角色</dt>
              <dd>{{ profile.role_name || profile.role || "普通用户" }}</dd>
            </div>
            <div class="detail-row">
              <dt>注册时间</dt>
              <dd>{{ formatDateTime(profile.created_at) }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section aria-labelledby="security-title" class="security-section">
        <div class="section-heading section-heading--security">
          <div>
            <p class="section-eyebrow">SECURITY</p>
            <h2 id="security-title">账号安全</h2>
          </div>
        </div>

        <div class="security-card">
          <button
              :aria-controls="passwordOpen ? 'password-form' : undefined"
              :aria-expanded="passwordOpen"
              class="password-toggle"
              type="button"
              @click="passwordOpen = mustChangePassword || !passwordOpen"
          >
            <span aria-hidden="true" class="password-symbol"><i/><b/></span>
            <span class="password-copy">
              <strong>修改登录密码</strong>
              <small>定期更新密码，保护账号安全</small>
            </span>
            <span :class="{'toggle-chevron--open': passwordOpen}" aria-hidden="true" class="toggle-chevron"/>
          </button>

          <form
              v-if="passwordOpen"
              id="password-form"
              autocomplete="on"
              class="password-form"
              @submit.prevent="submitPassword"
          >
            <label class="field">
              <span>当前密码</span>
              <input
                  v-model="passwordForm.current"
                  autocomplete="current-password"
                  enterkeyhint="next"
                  placeholder="请输入当前密码"
                  required
                  type="password"
              >
            </label>
            <label class="field">
              <span>新密码</span>
              <input
                  v-model="passwordForm.next"
                  :minlength="MIN_PASSWORD_LENGTH"
                  placeholder="请输入新密码"
                  autocomplete="new-password"
                  enterkeyhint="next"
                  required
                  type="password"
              >
            </label>
            <label class="field">
              <span>确认新密码</span>
              <input
                  v-model="passwordForm.confirm"
                  :minlength="MIN_PASSWORD_LENGTH"
                  autocomplete="new-password"
                  enterkeyhint="done"
                  placeholder="请再次输入新密码"
                  required
                  type="password"
              >
            </label>

            <p v-if="formError" class="form-message form-message--error" role="alert">{{ formError }}</p>
            <p v-if="successMessage" class="form-message form-message--success" role="status">{{ successMessage }}</p>

            <button :disabled="store.saving || redirecting" class="submit-button" type="submit">
              {{ store.saving ? "正在保存..." : redirecting ? "正在前往登录..." : "确认修改" }}
            </button>
            <p class="password-hint">至少 8 位，且大写字母、小写字母、数字、特殊符号至少包含 3 类。修改成功后需重新登录。</p>
          </form>
        </div>
      </section>

      <p class="page-footnote">如需更新其他个人资料，请联系管理员。</p>
    </div>
  </main>
</template>

<script lang="ts" setup>
import type {ProfileData} from "~/stores/profile";
import {MIN_PASSWORD_LENGTH, passwordPolicyError} from "~/utils/password";

definePageMeta({layout: false});

const router = useRouter();
const store = useProfileStore();
const authStore = useAuthStore();

const profile = ref<ProfileData | null>(null);
const loading = ref(true);
const loadError = ref("");
const formError = ref("");
const successMessage = ref("");
const passwordOpen = ref(false);
const redirecting = ref(false);
const avatarBroken = ref(false);
const passwordForm = reactive({current: "", next: "", confirm: ""});

const mustChangePassword = computed(() => profile.value?.must_change_password === true);
const avatarSrc = computed(() => profile.value?.avatar?.trim() || "");
const displayName = computed(() => profile.value?.name?.trim() || profile.value?.username || "用户");
const initial = computed(() => displayName.value.trim().charAt(0).toUpperCase() || "U");

useHead({title: "用户中心"});

function formatDateTime(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "—";
}

async function loadProfile() {
  loading.value = true;
  loadError.value = "";
  try {
    profile.value = await store.load();
    avatarBroken.value = false;
    if (mustChangePassword.value) passwordOpen.value = true;
  } catch (error) {
    loadError.value = (error as { statusMessage?: string }).statusMessage || "请检查网络后重试。";
  } finally {
    loading.value = false;
  }
}

async function submitPassword() {
  formError.value = "";
  successMessage.value = "";

  const passwordError = passwordPolicyError(passwordForm.next, "新密码");
  if (passwordError) {
    formError.value = passwordError;
    return;
  }
  if (passwordForm.next !== passwordForm.confirm) {
    formError.value = "两次输入的新密码不一致。";
    return;
  }
  if (passwordForm.next === passwordForm.current) {
    formError.value = "新密码不能与当前密码相同。";
    return;
  }

  try {
    await store.changePassword({
      current_password: passwordForm.current,
      new_password: passwordForm.next,
    });
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "密码修改失败，请稍后重试。";
    return;
  }

  passwordForm.current = "";
  passwordForm.next = "";
  passwordForm.confirm = "";
  successMessage.value = "密码修改成功，即将前往登录页。";
  redirecting.value = true;
  await new Promise((resolve) => setTimeout(resolve, 900));
  try {
    await authStore.logout();
  } catch {
    // 密码接口已撤销会话，本地登录态仍需清除。
  }
  await router.replace("/login");
}

onMounted(() => {
  void loadProfile();
});
</script>

<style scoped>
:global(html),
:global(body),
:global(#__nuxt) {
  background: #f4f6f8;
  margin: 0;
  min-height: 100%;
}

.myself-page {
  box-sizing: border-box;
  background: #f4f6f8;
  color: #25313b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  margin: 0 auto;
  max-width: 458px;
  min-height: 100dvh;
  padding-bottom: calc(22px + env(safe-area-inset-bottom, 0px));
}

.page-header {
  align-items: center;
  background: rgb(255 255 255 / 96%);
  border-bottom: 1px solid #edf0f2;
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  height: 52px;
  padding: 0 14px;
  position: sticky;
  top: 0;
  z-index: 5;
}

.page-header h1 {
  color: #222d36;
  font-size: 16px;
  font-weight: 650;
  margin: 0;
  text-align: center;
}

.back-button {
  align-items: center;
  background: transparent;
  border: 0;
  display: flex;
  height: 42px;
  justify-content: flex-start;
  padding: 0;
  width: 40px;
}

.back-button span {
  border-bottom: 1.8px solid #53616d;
  border-left: 1.8px solid #53616d;
  height: 10px;
  transform: rotate(45deg);
  width: 10px;
}

.header-spacer {
  height: 1px;
}

.page-content {
  padding: 19px 16px 0;
}

.security-notice {
  align-items: flex-start;
  background: #fff8e9;
  border: 1px solid #f4e6c6;
  border-radius: 12px;
  color: #87632b;
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  padding: 12px;
}

.notice-mark,
.state-mark {
  align-items: center;
  background: #f3d58c;
  border-radius: 50%;
  color: #77551f;
  display: inline-flex;
  flex: 0 0 18px;
  font-size: 12px;
  font-weight: 700;
  height: 18px;
  justify-content: center;
  line-height: 1;
  margin-top: 1px;
  width: 18px;
}

.security-notice strong {
  font-size: 12px;
  font-weight: 650;
}

.security-notice p {
  color: #9b8052;
  font-size: 11px;
  line-height: 1.5;
  margin: 4px 0 0;
}

.account-section,
.security-section {
  margin-bottom: 22px;
}

.section-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin: 0 2px 11px;
}

.section-eyebrow {
  color: #9aa5ae;
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 1.4px;
  margin: 0 0 3px;
}

.section-heading h2 {
  color: #2b3640;
  font-size: 15px;
  font-weight: 650;
  margin: 0;
}

.section-count {
  color: #2b9b69;
  font-size: 10px;
}

.account-card,
.security-card,
.state-card {
  background: #fff;
  border: 1px solid #edf0f3;
  border-radius: 14px;
  box-shadow: 0 3px 12px rgb(36 55 72 / 3%);
}

.account-card {
  padding: 17px 15px 4px;
}

.identity {
  align-items: center;
  border-bottom: 1px solid #f0f2f4;
  display: flex;
  gap: 12px;
  padding: 0 1px 16px;
}

.avatar {
  align-items: center;
  background: #e8f3fc;
  border: 1px solid #d7eafa;
  border-radius: 13px;
  color: #2688d2;
  display: inline-flex;
  flex: 0 0 52px;
  font-size: 20px;
  font-weight: 650;
  height: 52px;
  justify-content: center;
  overflow: hidden;
  width: 52px;
}

.avatar img {
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.identity-copy {
  flex: 1;
  min-width: 0;
}

.identity-copy h3 {
  color: #27323b;
  font-size: 16px;
  font-weight: 650;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-copy p {
  color: #9aa4ad;
  font-size: 11px;
  margin: 5px 0 0;
}

.identity-dot {
  background: #36b47b;
  border: 3px solid #e7f7ee;
  border-radius: 50%;
  height: 7px;
  width: 7px;
}

.profile-details {
  margin: 0;
}

.detail-row {
  align-items: flex-start;
  border-bottom: 1px solid #f1f3f5;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-height: 43px;
  padding: 13px 1px 10px;
}

.detail-row:last-child {
  border-bottom: 0;
}

.detail-row dt {
  color: #87939d;
  flex: 0 0 74px;
  font-size: 11px;
}

.detail-row dd {
  color: #35414b;
  font-size: 11px;
  line-height: 1.45;
  margin: 0;
  max-width: 68%;
  overflow-wrap: anywhere;
  text-align: right;
}

.security-section {
  margin-top: 22px;
}

.section-heading--security {
  margin-bottom: 11px;
}

.security-card {
  overflow: hidden;
}

.password-toggle {
  align-items: center;
  background: #fff;
  border: 0;
  color: inherit;
  display: flex;
  font: inherit;
  min-height: 70px;
  padding: 12px 15px;
  text-align: left;
  width: 100%;
}

.password-toggle:active {
  background: #f9fbfc;
}

.password-symbol {
  align-items: center;
  background: #edf6fd;
  border-radius: 10px;
  display: inline-flex;
  flex: 0 0 38px;
  height: 38px;
  justify-content: center;
  margin-right: 11px;
  position: relative;
  width: 38px;
}

.password-symbol i {
  border: 1.6px solid #298bce;
  border-radius: 9px 9px 2px 2px;
  height: 9px;
  position: absolute;
  top: 8px;
  width: 13px;
}

.password-symbol b {
  background: #298bce;
  border-radius: 3px;
  height: 12px;
  position: absolute;
  top: 16px;
  width: 17px;
}

.password-symbol b::after {
  background: #edf6fd;
  border-radius: 50%;
  content: "";
  height: 3px;
  left: 7px;
  position: absolute;
  top: 4px;
  width: 3px;
}

.password-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.password-copy strong {
  color: #303b44;
  font-size: 12px;
  font-weight: 650;
}

.password-copy small {
  color: #9aa4ad;
  font-size: 10px;
}

.toggle-chevron {
  border-bottom: 1.5px solid #aeb8c0;
  border-right: 1.5px solid #aeb8c0;
  height: 7px;
  margin-right: 2px;
  transform: rotate(45deg) translateY(-2px);
  transition: transform .18s ease;
  width: 7px;
}

.toggle-chevron--open {
  transform: rotate(225deg) translate(-1px, -1px);
}

.password-form {
  border-top: 1px solid #f0f2f4;
  padding: 15px;
}

.field {
  display: block;
  margin-bottom: 13px;
}

.field > span {
  color: #56626c;
  display: block;
  font-size: 11px;
  font-weight: 550;
  margin: 0 0 7px;
}

.field input {
  appearance: none;
  background: #f8fafb;
  border: 1px solid #e8edf0;
  border-radius: 9px;
  box-sizing: border-box;
  color: #2a353e;
  font: inherit;
  font-size: 13px;
  height: 44px;
  outline: none;
  padding: 0 12px;
  width: 100%;
}

.field input::placeholder {
  color: #b0b9c0;
  font-size: 11px;
}

.field input:focus {
  background: #fff;
  border-color: #76b9e9;
  box-shadow: 0 0 0 3px rgb(48 145 211 / 10%);
}

.form-message {
  border-radius: 8px;
  font-size: 11px;
  line-height: 1.5;
  margin: 2px 0 12px;
  padding: 9px 10px;
}

.form-message--error {
  background: #fff1ef;
  color: #c6534e;
}

.form-message--success {
  background: #edf8f1;
  color: #258557;
}

.submit-button {
  background: #2589d6;
  border: 0;
  border-radius: 9px;
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  height: 45px;
  margin-top: 3px;
  transition: background .15s ease, transform .15s ease;
  width: 100%;
}

.submit-button:active {
  background: #1d78bf;
  transform: scale(.99);
}

.submit-button:disabled {
  background: #91bfe0;
}

.password-hint {
  color: #a0aab2;
  font-size: 10px;
  line-height: 1.5;
  margin: 10px 2px 1px;
  text-align: center;
}

.state-card {
  align-items: center;
  color: #7d8993;
  display: flex;
  flex-direction: column;
  padding: 26px 18px;
  text-align: center;
}

.state-mark {
  background: #fff0ed;
  color: #cb625b;
  flex: 0 0 32px;
  font-size: 16px;
  height: 32px;
  width: 32px;
}

.state-card strong {
  color: #45515b;
  font-size: 13px;
  margin-top: 12px;
}

.state-card p {
  font-size: 11px;
  line-height: 1.5;
  margin: 6px 0 0;
}

.retry-button {
  background: #eaf4fc;
  border: 0;
  border-radius: 8px;
  color: #267fc1;
  font: inherit;
  font-size: 11px;
  margin-top: 15px;
  min-height: 35px;
  padding: 0 15px;
}

.account-card--loading {
  display: grid;
  gap: 17px;
  grid-template-columns: 52px 1fr;
  padding: 17px 15px;
}

.skeleton-avatar,
.skeleton-copy span,
.skeleton-rows span {
  animation: shimmer 1.2s ease-in-out infinite alternate;
  background: #edf1f4;
  border-radius: 7px;
}

.skeleton-avatar {
  height: 52px;
  width: 52px;
}

.skeleton-copy {
  align-self: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-copy span:first-child {
  height: 12px;
  width: 42%;
}

.skeleton-copy span:last-child {
  height: 9px;
  width: 28%;
}

.skeleton-rows {
  display: grid;
  gap: 11px;
  grid-column: 1 / -1;
}

.skeleton-rows span {
  height: 9px;
  width: 100%;
}

.skeleton-rows span:nth-child(even) {
  width: 82%;
}

@keyframes shimmer {
  to {
    opacity: .48;
  }
}

.page-footnote {
  color: #a7b0b7;
  font-size: 10px;
  line-height: 1.6;
  margin: 25px 3px 0;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
  }
}
</style>
