<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">个人中心</h1>
        <p class="page__desc">维护个人资料、头像与登录密码</p>
      </div>
    </header>

    <div v-if="forcePasswordChange" class="notice">
      <span class="material-icons-outlined">info</span>
      <span>当前账号仍在使用初始密码，请先设置新密码后再使用系统。</span>
    </div>

    <div class="profile-grid">
      <!-- 左侧：头像与账号信息 -->
      <section class="panel info-card">
        <div class="avatar-wrap">
          <div class="avatar-display" title="点击更换头像" @click="pickAvatar">
            <img v-if="avatarSrc" :src="avatarSrc" alt="头像" >
            <span v-else class="avatar-initial">{{ initial }}</span>
          </div>
          <button
            class="avatar-badge"
            type="button"
            title="更换头像"
            :disabled="store.uploading"
            @click="pickAvatar"
          >
            <span class="material-icons-outlined">{{ store.uploading ? "hourglass_empty" : "photo_camera" }}</span>
          </button>
          <input
            ref="avatarInput"
            class="avatar-input"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            @change="onAvatarSelected"
          >
        </div>
        <div class="info-card__name">{{ displayName }}</div>
        <dl class="info-list">
          <div class="info-row">
            <span class="material-icons-outlined">person</span>
            <dt>用户账号</dt>
            <dd>{{ profile?.username || "-" }}</dd>
          </div>
          <div class="info-row">
            <span class="material-icons-outlined">phone</span>
            <dt>手机号码</dt>
            <dd>{{ profile?.phone || "-" }}</dd>
          </div>
          <div class="info-row">
            <span class="material-icons-outlined">apartment</span>
            <dt>所属部门</dt>
            <dd>{{ profile?.department_name || "-" }}</dd>
          </div>
          <div class="info-row">
            <span class="material-icons-outlined">badge</span>
            <dt>角色权限</dt>
            <dd>{{ profile?.role_name || profile?.role || "-" }}</dd>
          </div>
          <div class="info-row">
            <span class="material-icons-outlined">schedule</span>
            <dt>创建日期</dt>
            <dd>{{ createdAt }}</dd>
          </div>
        </dl>
      </section>

      <!-- 右侧：资料与改密 -->
      <section class="panel">
        <div class="panel__head tabs">
          <button
            class="tab"
            type="button"
            :class="{ 'tab--active': tab === 'info' }"
            :disabled="forcePasswordChange"
            :title="forcePasswordChange ? '请先完成密码修改' : ''"
            @click="tab = 'info'"
          >基本资料</button>
          <button
            class="tab"
            type="button"
            :class="{ 'tab--active': tab === 'password' }"
            @click="tab = 'password'"
          >修改密码</button>
        </div>

        <div class="panel__body">
          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="form-success">{{ successMessage }}</p>

          <!-- 基本资料 -->
          <form v-show="tab === 'info'" class="form-grid" @submit.prevent="submitProfile">
            <label class="field">
              <span class="field__label">用户账号</span>
              <input class="input" :value="profile?.username || ''" disabled >
            </label>
            <div class="field-row">
              <label class="field">
                <span class="field__label">用户昵称<em>*</em></span>
                <input v-model.trim="form.name" class="input" placeholder="请输入用户昵称" required >
              </label>
              <label class="field">
                <span class="field__label">手机号码</span>
                <input v-model.trim="form.phone" class="input" placeholder="请输入手机号码" >
              </label>
              <label class="field">
                <span class="field__label">邮箱</span>
                <input v-model.trim="form.email" class="input" type="email" placeholder="请输入邮箱" required >
              </label>
              <label class="field">
                <span class="field__label">性别</span>
                <select v-model="form.gender" class="select">
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                  <option value="UNKNOWN">保密</option>
                </select>
              </label>
            </div>
            <div class="form-actions">
              <button class="btn btn--primary" type="submit" :disabled="store.saving">
                {{ store.saving ? "保存中..." : "保存" }}
              </button>
              <button class="btn btn--ghost" type="button" @click="resetProfileForm">重置</button>
            </div>
          </form>

          <!-- 修改密码 -->
          <form v-show="tab === 'password'" class="form-grid" @submit.prevent="submitPassword">
            <label class="field">
              <span class="field__label">原密码<em>*</em></span>
              <input v-model="passwordForm.current" class="input" type="password" autocomplete="current-password" placeholder="请输入原密码" required >
            </label>
            <label class="field">
              <span class="field__label">新密码<em>*</em></span>
              <input v-model="passwordForm.next" class="input" type="password" autocomplete="new-password" :placeholder="`请输入新密码（至少 ${MIN_PASSWORD_LENGTH} 位）`" required >
            </label>
            <label class="field">
              <span class="field__label">确认密码<em>*</em></span>
              <input v-model="passwordForm.confirm" class="input" type="password" autocomplete="new-password" placeholder="请再次输入新密码" required >
            </label>
            <div class="form-actions">
              <button class="btn btn--primary" type="submit" :disabled="store.saving">
                {{ store.saving ? "提交中..." : "保存" }}
              </button>
              <button v-if="!forcePasswordChange" class="btn btn--ghost" type="button" @click="resetPasswordForm">重置</button>
            </div>
          </form>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import type {Gender, ProfileData} from "~/stores/profile";

// 页面结构对齐原型 20260625115857 的「个人中心」，样式沿用本站的 CSS 变量体系。
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const store = useProfileStore();

const MIN_PASSWORD_LENGTH = 6;
/** 头像落库前先压到该边长以内，避免把大图 base64 存进用户表。 */
const AVATAR_MAX_EDGE = 256;
/** 原图上限，超过就不做前端压缩直接拒绝，避免卡住浏览器。 */
const AVATAR_MAX_SOURCE_BYTES = 5 * 1024 * 1024;

const profile = ref<ProfileData | null>(null);
const tab = ref<"info" | "password">("info");
const errorMessage = ref("");
const successMessage = ref("");
const avatarInput = ref<HTMLInputElement>();
const form = reactive({
  name: "",
  phone: "",
  email: "",
  gender: "UNKNOWN" as Gender,
});
const passwordForm = reactive({ current: "", next: "", confirm: "" });

const avatarSrc = computed(() => profile.value?.avatar?.trim() || "");
const displayName = computed(() => profile.value?.name || profile.value?.username || "-");
const initial = computed(() => displayName.value.trim().charAt(0).toUpperCase() || "A");
const createdAt = computed(() => formatDateTime(profile.value?.created_at || ""));
// 初始密码未修改时锁定「基本资料」标签，只允许完成改密。
const forcePasswordChange = computed(() => profile.value?.must_change_password === true);

useHead({ title: "个人中心" });

function formatDateTime(value: string) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) {
    return "-";
  }

  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function clearMessages() {
  errorMessage.value = "";
  successMessage.value = "";
}

function describeError(error: unknown, fallback: string) {
  return (error as { statusMessage?: string }).statusMessage || fallback;
}

function applyProfile(data: ProfileData) {
  profile.value = data;
  form.name = data.name || "";
  form.phone = data.phone || "";
  form.email = data.email || "";
  form.gender = data.gender || "UNKNOWN";
}

async function loadProfile() {
  try {
    applyProfile(await store.load());
  } catch (error) {
    errorMessage.value = describeError(error, "加载个人资料失败");
  }
}

async function submitProfile() {
  clearMessages();
  if (!form.name) {
    errorMessage.value = "用户昵称不能为空";
    return;
  }

  try {
    applyProfile(await store.save({
      name: form.name,
      phone: form.phone,
      email: form.email,
      gender: form.gender,
    }));
    successMessage.value = "个人资料已保存";
  } catch (error) {
    errorMessage.value = describeError(error, "保存失败");
  }
}

function resetProfileForm() {
  clearMessages();
  if (profile.value) {
    applyProfile(profile.value);
  }
}

function resetPasswordForm() {
  clearMessages();
  passwordForm.current = "";
  passwordForm.next = "";
  passwordForm.confirm = "";
}

async function submitPassword() {
  clearMessages();
  if (passwordForm.next.length < MIN_PASSWORD_LENGTH) {
    errorMessage.value = `新密码长度不能少于 ${MIN_PASSWORD_LENGTH} 位`;
    return;
  }
  if (passwordForm.next !== passwordForm.confirm) {
    errorMessage.value = "两次输入的新密码不一致";
    return;
  }
  if (passwordForm.next === passwordForm.current) {
    errorMessage.value = "新密码不能与原密码相同";
    return;
  }

  try {
    await store.changePassword({
      current_password: passwordForm.current,
      new_password: passwordForm.next,
    });
  } catch (error) {
    errorMessage.value = describeError(error, "密码修改失败");
    return;
  }

  // 改密后服务端撤销了全部会话，必须重新登录。
  successMessage.value = "密码修改成功，请使用新密码重新登录";
  passwordForm.current = "";
  passwordForm.next = "";
  passwordForm.confirm = "";
  await nextTick();
  await wait(1200);
  try {
    await authStore.logout();
  } catch {
    // 会话已在服务端撤销，本地状态仍必须清理。
  }
  await router.replace("/login");
}

function wait(milliseconds: number) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function pickAvatar() {
  if (!store.uploading) {
    avatarInput.value?.click();
  }
}

/** 读取本地图片并等比缩放到 [AVATAR_MAX_EDGE] 内，统一转成 JPEG data URL。 */
async function toAvatarDataUrl(file: File) {
  const objectUrl = URL.createObjectURL(file);
  try {
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error("图片读取失败，请更换图片"));
      element.src = objectUrl;
    });
    const scale = Math.min(1, AVATAR_MAX_EDGE / Math.max(image.width, image.height));
    const width = Math.max(1, Math.round(image.width * scale));
    const height = Math.max(1, Math.round(image.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("当前浏览器不支持图片处理");
    }
    // 透明区域用白色填充，避免转成 JPEG 后变成黑块。
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);
    return canvas.toDataURL("image/jpeg", 0.85);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 重置 input，否则连续选同一张图不会再触发 change。
  input.value = "";
  if (!file) {
    return;
  }
  clearMessages();

  if (!file.type.startsWith("image/")) {
    errorMessage.value = "请选择图片文件";
    return;
  }
  if (file.size > AVATAR_MAX_SOURCE_BYTES) {
    errorMessage.value = "图片不能超过 5MB";
    return;
  }

  try {
    const dataUrl = await toAvatarDataUrl(file);
    applyProfile(await store.updateAvatar(dataUrl));
    successMessage.value = "头像已更新";
  } catch (error) {
    errorMessage.value = describeError(error, "头像上传失败");
  }
}

onMounted(async () => {
  await loadProfile();
  if (forcePasswordChange.value || route.query.tab === "password") {
    tab.value = "password";
  }
});

// 资料异步返回「必须改密」时立即切到改密标签，避免停在被锁定的基本资料页。
watch(forcePasswordChange, (required) => {
  if (required) {
    tab.value = "password";
  }
});
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }
.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }
.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }
.page__desc { color: var(--text-sub); margin: 2px 0 0; }

.notice { align-items: center; background: #fff7ed; border: 1px solid #fed7aa; border-radius: 8px; color: #9a3412; display: flex; gap: 8px; margin-bottom: 16px; padding: 10px 14px; }
.notice .material-icons-outlined { font-size: 18px; }

.profile-grid { align-items: start; display: grid; gap: 16px; grid-template-columns: 340px minmax(0, 1fr); }
.panel { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; overflow: hidden; }

.info-card { padding: 32px 24px; text-align: center; }
.avatar-wrap { height: 120px; margin: 0 auto 18px; position: relative; width: 120px; }
.avatar-display {
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  font-size: 42px;
  font-weight: 600;
  height: 100%;
  justify-content: center;
  overflow: hidden;
  width: 100%;
}
.avatar-display img { height: 100%; object-fit: cover; width: 100%; }
.avatar-badge {
  align-items: center;
  background: var(--primary);
  border: 0;
  border-radius: 50%;
  bottom: 0;
  box-shadow: 0 2px 8px rgb(0 0 0 / 15%);
  color: #fff;
  cursor: pointer;
  display: flex;
  height: 36px;
  justify-content: center;
  padding: 0;
  position: absolute;
  right: 0;
  width: 36px;
}
.avatar-badge:disabled { cursor: not-allowed; opacity: 0.7; }
.avatar-badge .material-icons-outlined { font-size: 18px; }
.avatar-input { display: none; }

.info-card__name { border-bottom: 1px solid var(--border); color: var(--text); font-size: 20px; font-weight: 600; margin-bottom: 20px; padding-bottom: 16px; }
.info-list { margin: 0; text-align: left; }
.info-row { align-items: center; display: flex; margin-bottom: 14px; }
.info-row:last-child { margin-bottom: 0; }
.info-row .material-icons-outlined { color: var(--text-mute); flex: 0 0 auto; font-size: 20px; margin-right: 10px; }
.info-row dt { color: var(--text-mute); flex: 0 0 70px; font-size: 13px; }
.info-row dd { color: var(--text); flex: 1; font-size: 13px; font-weight: 500; margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.panel__head { border-bottom: 1px solid var(--border); padding: 0; }
.tabs { display: flex; }
.tab { background: none; border: 0; border-bottom: 2px solid transparent; color: var(--text-sub); cursor: pointer; font: inherit; font-size: 14px; font-weight: 500; margin-bottom: -1px; padding: 15px 24px; transition: color var(--tr), border-color var(--tr); }
.tab:hover:not(:disabled) { color: var(--primary); }
.tab--active { border-bottom-color: var(--primary); color: var(--primary); }
.tab:disabled { color: var(--text-mute); cursor: not-allowed; }

.panel__body { padding: 22px 24px; }
.form-grid { margin: 0; }
.field-row { display: grid; gap: 0 16px; grid-template-columns: 1fr 1fr; }
.field { display: block; margin-bottom: 16px; }
.field__label { color: var(--text-sub); display: block; font-size: 12px; font-weight: 500; margin-bottom: 5px; }
.field__label em { color: var(--red); font-style: normal; margin-left: 2px; }
.input, .select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; outline: none; padding: 0 10px; width: 100%; }
.input:focus, .select:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgb(37 99 235 / 10%); }
.input:disabled { background: var(--bg); color: var(--text-mute); }

.form-actions { display: flex; gap: 8px; margin-top: 4px; }
.btn { align-items: center; border: 0; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; font-size: 13px; gap: 5px; height: 32px; padding: 0 16px; transition: all var(--tr); white-space: nowrap; }
.btn--primary { background: var(--primary); color: #fff; }
.btn--primary:disabled { cursor: not-allowed; opacity: 0.7; }
.btn--ghost { background: var(--card); border: 1px solid var(--border-strong); color: var(--text-sub); }
.btn--ghost:hover { color: var(--text); }

.form-error { color: var(--red); margin: 0 0 14px; }
.form-success { color: #059669; margin: 0 0 14px; }

@media (max-width: 900px) {
  .profile-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 640px) {
  .page { padding: 16px; }
  .field-row { grid-template-columns: minmax(0, 1fr); }
  .tab { padding: 13px 16px; }
  .panel__body { padding: 18px 16px; }
}
</style>
