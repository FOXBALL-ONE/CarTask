<template>
  <section class="presence-page">
    <header class="presence-heading">
      <div>
        <p class="eyebrow">LIVE PRESENCE / 02S PULSE</p>
        <h1>在线用户</h1>
        <p class="heading-copy">查看正在使用系统的账户。每 2 秒自动刷新一次，超过 10 秒未活动会暂时离线。</p>
      </div>
      <div :class="{ 'heading-status--error': errorMessage }" class="heading-status">
        <span class="status-dot"/>
        <span>{{ errorMessage ? "连接异常" : "实时监测中" }}</span>
        <button :disabled="loading" class="refresh-button" title="立即刷新" type="button" @click="loadOnlineUsers">
          <span :class="{ spinning: loading }" class="material-icons-outlined">refresh</span>
        </button>
      </div>
    </header>

    <div v-if="errorMessage" class="error-banner">
      <span class="material-icons-outlined">wifi_off</span>
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadOnlineUsers">重试</button>
    </div>

    <section class="signal-grid">
      <article class="count-card">
        <div aria-hidden="true" class="count-card__halo"/>
        <div class="count-card__content">
          <span class="card-kicker">CURRENTLY ONLINE</span>
          <strong>{{ onlineUsers.length }}</strong>
          <span class="count-caption">个用户正在使用系统</span>
        </div>
      </article>

    </section>

    <section class="roster-panel">
      <header class="roster-heading">
        <div><span class="section-mark">01</span>
          <div><h2>实时名册</h2>
            <p>按最近活动时间排序</p></div>
        </div>
        <div class="roster-heading__meta">
          <label v-if="canForceLogout" class="roster-pick-all">
            <input :checked="allSelected" :disabled="!onlineUsers.length" :indeterminate.prop="someSelected"
                   type="checkbox" @change="toggleAll">
            <span>全选</span>
          </label>
          <button v-if="canForceLogout" :disabled="!selectedIds.length || loggingOut" class="force-logout" type="button"
                  @click="forceLogoutSelected">
            <span class="material-icons-outlined">logout</span>{{ loggingOut ? "处理中…" : `强制登出${selectedIds.length ? ` (${selectedIds.length})` : ""}` }}
          </button>
          <span class="roster-total">{{ onlineUsers.length }} ONLINE</span>
        </div>
      </header>

      <p v-if="actionError || actionMessage" :class="{ 'roster-notice--error': actionError }" class="roster-notice"
         role="status">
        <span class="material-icons-outlined">{{ actionError ? "error_outline" : "check_circle" }}</span>
        <span>{{ actionError || actionMessage }}</span>
      </p>

      <div v-if="loading && !onlineUsers.length" class="roster-empty"><span class="material-icons-outlined spinning">progress_activity</span>
        <p>正在接收在线信号…</p></div>
      <div v-else-if="!onlineUsers.length" class="roster-empty"><span class="material-icons-outlined">no_accounts</span>
        <p>当前没有检测到在线用户</p><small>用户发起请求后会自动出现在这里。</small></div>
      <div v-else class="roster-list">
        <article v-for="(user, index) in onlineUsers" :key="user.id"
                 :class="{ 'roster-row--selectable': canForceLogout }" class="roster-row">
          <label v-if="canForceLogout" class="roster-pick">
            <input v-model="selectedIds" :aria-label="`选择 ${user.display_name || user.username}`" :value="user.id"
                   class="roster-check" type="checkbox">
          </label>
          <span class="roster-index">{{ String(index + 1).padStart(2, "0") }}</span>
          <span class="roster-avatar">{{ initials(user.display_name || user.username) }}</span>
          <div class="roster-identity"><strong>{{ user.display_name || user.username }}</strong><span>@{{
              user.username
            }}</span></div>
          <span class="roster-role">{{ user.role }}</span>
          <div class="roster-seen"><span><i class="online-dot"/>在线</span><small>最后活动
            {{ relativeTime(user.last_seen) }}</small></div>
        </article>
      </div>
      <footer class="roster-footer"><span>数据来源：认证请求心跳</span><span>轮询间隔 2 秒</span></footer>
    </section>
  </section>
</template>

<script lang="ts" setup>
interface OnlineUser {
  id: number;
  username: string;
  display_name?: string | null;
  role: string;
  last_seen: string
}

interface OnlineResponse {
  items: OnlineUser[];
  total: number;
  captured_at: string;
  stale_after_seconds: number
}

interface ForceLogoutResult {
  logged_out: number[];
  skipped: number[]
}

const http = useHttp();
const authStore = useAuthStore();
const {can} = usePermission();
/**
 * 强制登出只对超级管理员开放（后端是 hasRole('SUPER_ADMIN') + online-user:logout 双卡）。
 * 这里只决定按钮显不显示——真正的拦截在服务端，隐藏只是不让没权限的人点了才发现。
 */
const canForceLogout = computed(() => can("online-user:logout"));
const onlineUsers = ref<OnlineUser[]>([]);
const staleAfterSeconds = ref(10);
const loading = ref(false);
const errorMessage = ref("");
const selectedIds = ref<number[]>([]);
const loggingOut = ref(false);
const actionMessage = ref("");
const actionError = ref("");
let refreshTimer: ReturnType<typeof setInterval> | undefined;
let actionTimer: ReturnType<typeof setTimeout> | undefined;

const allSelected = computed(() => onlineUsers.value.length > 0 && selectedIds.value.length === onlineUsers.value.length);
const someSelected = computed(() => selectedIds.value.length > 0 && !allSelected.value);

async function loadOnlineUsers() {
  loading.value = true;
  try {
    const result = await http.get<OnlineResponse>("/online-users");
    onlineUsers.value = result.items || [];
    staleAfterSeconds.value = result.stale_after_seconds || 10;
    errorMessage.value = "";
    // 名册每 2 秒重建一次，勾选状态要跟着当前名单收敛，否则会留下已经离线的人的 id。
    selectedIds.value = selectedIds.value.filter((id) => onlineUsers.value.some((user) => user.id === id));
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "在线状态暂时无法读取";
  } finally {
    loading.value = false;
  }
}

function initials(value: string) {
  return value.trim().slice(0, 2).toUpperCase() || "?";
}

function relativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  return seconds < 2 ? "刚刚" : `${seconds} 秒前`;
}

function toggleAll(event: Event) {
  selectedIds.value = (event.target as HTMLInputElement).checked
      ? onlineUsers.value.map((user) => user.id)
      : [];
}

function clearActionFeedback() {
  if (actionTimer) clearTimeout(actionTimer);
  actionTimer = setTimeout(() => {
    actionMessage.value = "";
    actionError.value = "";
  }, 5_000);
}

/**
 * 强制登出选中的在线用户。
 *
 * 服务端做的是撤销登录凭据（自增 token version），被踢的人**所有设备**上的 token 一起失效，
 * 下一次请求就会被打回登录页；这里清掉的只是在线名单，好让人立刻从列表里消失。
 * 后果不可逆，所以必须确认，并把要踢的人名列出来。
 */
async function forceLogoutSelected() {
  const ids = [...selectedIds.value];
  if (!ids.length || loggingOut.value) return;
  const names = onlineUsers.value
      .filter((user) => ids.includes(user.id))
      .map((user) => user.display_name || user.username)
      .join("、");
  if (!window.confirm(`确认强制登出 ${ids.length} 个用户？\n\n${names}\n\n他们所有设备上的登录凭据会立即失效，需要重新登录。`)) return;

  loggingOut.value = true;
  actionMessage.value = "";
  actionError.value = "";
  try {
    const result = await http.post<ForceLogoutResult>("/online-users/logout", {user_ids: ids}, {payloadMode: "json"});
    const done = result.logged_out?.length ?? 0;
    const skipped = result.skipped?.length ?? 0;
    actionMessage.value = skipped
        ? `已强制登出 ${done} 个用户，另有 ${skipped} 个账号已不存在被跳过`
        : `已强制登出 ${done} 个用户`;
    selectedIds.value = [];
    await loadOnlineUsers();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "强制登出失败";
  } finally {
    loggingOut.value = false;
    clearActionFeedback();
  }
}

onMounted(async () => {
  try {
    const user = await authStore.refreshSession();
    if (!user?.permissions.includes("user:read")) {
      await navigateTo("/");
      return;
    }
  } catch { /* 请求会显示可读错误状态。 */
  }
  await loadOnlineUsers();
  refreshTimer = setInterval(loadOnlineUsers, 2_000);
});

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer);
  if (actionTimer) clearTimeout(actionTimer);
});
</script>

<style scoped>
.presence-page {
  --ink: #11221f;
  --muted: #64807a;
  --line: #d7e7e1;
  --mint: #dff7eb;
  --signal: #16a36b;
  /* 实心色块（计数卡底色）与强调正文分开：深色下前者要保持深绿，后者必须提亮。 */
  --deep: #123c35;
  --strong: #123c35;
  --surface: var(--card);
  --chip-bg: #f0f8f4;
  --chip-border: #d4ebe0;
  --chip-text: #33745b;
  min-height: 100%;
  padding: 30px 32px 42px;
  background: #f7fbf9;
  color: var(--ink);
}

.presence-heading {
  align-items: flex-end;
  display: flex;
  gap: 20px;
  justify-content: space-between;
  margin: 0 auto 26px;
  max-width: 1240px;
}

.eyebrow, .card-kicker {
  color: var(--signal);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .16em;
  margin: 0 0 8px;
}

.presence-heading h1 {
  font-size: clamp(28px, 4vw, 46px);
  font-weight: 680;
  letter-spacing: -.045em;
  margin: 0;
}

.heading-copy {
  color: var(--muted);
  font-size: 13px;
  margin: 8px 0 0;
}

.heading-status {
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--strong);
  display: flex;
  font-size: 12px;
  gap: 8px;
  padding: 6px 8px 6px 13px;
  white-space: nowrap;
}

.heading-status--error {
  color: var(--danger-text);
}

.status-dot, .online-dot, .pulse-dot {
  background: var(--signal);
  border-radius: 50%;
  display: inline-block;
  height: 7px;
  width: 7px;
}

.heading-status--error .status-dot {
  background: #d84a3a;
}

.refresh-button {
  align-items: center;
  background: var(--mint);
  border: 0;
  border-radius: 50%;
  color: var(--signal);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  margin-left: 4px;
  width: 28px;
}

.refresh-button:disabled {
  cursor: wait;
  opacity: .6;
}

.refresh-button .material-icons-outlined {
  font-size: 16px;
}

.error-banner {
  align-items: center;
  background: #fff2f0;
  border: 1px solid #f4c9c2;
  border-radius: 8px;
  color: #a93a2f;
  display: flex;
  font-size: 12px;
  gap: 8px;
  margin: 0 auto 16px;
  max-width: 1240px;
  padding: 10px 12px;
}

.error-banner .material-icons-outlined {
  font-size: 17px;
}

.error-banner button {
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  font: inherit;
  font-weight: 650;
  margin-left: auto;
  text-decoration: underline;
}

.signal-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr);
  margin: 0 auto 18px;
  max-width: 1240px;
}

.count-card, .roster-panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
}

.count-card {
  background: var(--deep);
  color: #f4fff9;
  min-height: 220px;
  padding: 22px;
  position: relative;
}

.count-card__halo {
  border: 1px solid rgb(164 244 205 / 24%);
  border-radius: 50%;
  /* 卡片现在是整行宽，装饰弧要跟着放大，否则右侧会空掉。 */
  height: 320px;
  position: absolute;
  right: -70px;
  top: -140px;
  width: 320px;
}

.count-card__halo::after {
  border: 1px solid rgb(164 244 205 / 16%);
  border-radius: 50%;
  content: "";
  inset: 34px;
  position: absolute;
}

.count-card__content {
  position: relative;
  z-index: 1;
}

.count-card .card-kicker {
  color: #8ee6b9;
}

.count-card strong {
  display: block;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: clamp(58px, 8vw, 86px);
  font-weight: 500;
  letter-spacing: -.08em;
  line-height: .95;
  margin: 18px 0 8px;
}

.count-caption {
  color: #b9d9cb;
  font-size: 12px;
}

.count-card__footer {
  bottom: 18px;
  color: #9ac6b4;
  display: flex;
  font-size: 10px;
  justify-content: space-between;
  left: 22px;
  position: absolute;
  right: 22px;
}

.count-card__footer span {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.pulse-dot {
  animation: blink 1.8s ease-in-out infinite;
  box-shadow: 0 0 0 4px rgb(93 226 157 / 14%);
}

.roster-panel {
  margin: 0 auto;
  max-width: 1240px;
}

.roster-heading {
  align-items: center;
  border-bottom: 1px solid var(--line);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  padding: 18px 22px;
}

.roster-heading__meta {
  align-items: center;
  display: flex;
  gap: 12px;
}

.roster-heading > div {
  align-items: center;
  display: flex;
  gap: 13px;
}

.section-mark {
  color: var(--signal);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.roster-heading h2 {
  font-size: 15px;
  margin: 0;
}

.roster-heading p {
  color: var(--muted);
  font-size: 11px;
  margin: 3px 0 0;
}

.roster-total {
  color: var(--signal);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  letter-spacing: .1em;
}

/* 强制登出的操作反馈：成功用绿系，失败用全局危险色（深浅两套都有值）。 */
.roster-notice {
  align-items: center;
  background: var(--success-soft);
  border-bottom: 1px solid var(--line);
  color: var(--success-text);
  display: flex;
  font-size: 12px;
  gap: 7px;
  margin: 0;
  padding: 10px 22px;
}

.roster-notice--error {
  background: var(--danger-soft);
  color: var(--danger-text);
}

.roster-notice .material-icons-outlined {
  font-size: 16px;
}

.roster-pick-all {
  align-items: center;
  color: var(--muted);
  cursor: pointer;
  display: inline-flex;
  font-size: 11px;
  gap: 6px;
  white-space: nowrap;
}

.roster-check, .roster-pick-all input {
  accent-color: var(--signal);
  cursor: pointer;
  height: 14px;
  margin: 0;
  width: 14px;
}

.roster-pick-all input:disabled {
  cursor: not-allowed;
}

.roster-pick {
  align-items: center;
  display: flex;
  justify-content: center;
}

.force-logout {
  align-items: center;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 7px;
  color: var(--muted);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 11px;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  transition: background var(--tr), border-color var(--tr), color var(--tr);
  white-space: nowrap;
}

.force-logout:hover:not(:disabled) {
  background: var(--danger-soft);
  border-color: var(--danger-border);
  color: var(--danger-text);
}

.force-logout:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.force-logout .material-icons-outlined {
  font-size: 15px;
}

.roster-list {
  padding: 0 22px;
}

.roster-row {
  align-items: center;
  border-bottom: 1px solid var(--line);
  display: grid;
  gap: 14px;
  grid-template-columns: 32px 38px minmax(160px, 1fr) 110px minmax(130px, .7fr);
  min-height: 68px;
}

/* 有强制登出权限时多一列勾选框，其余列保持原样。 */
.roster-row--selectable {
  grid-template-columns: 22px 32px 38px minmax(160px, 1fr) 110px minmax(130px, .7fr);
}

.roster-row:last-child {
  border-bottom: 0;
}

.roster-index {
  color: #95afa7;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.roster-avatar {
  align-items: center;
  background: var(--mint);
  border: 1px solid #b9e8cf;
  border-radius: 11px;
  color: var(--signal);
  display: flex;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  height: 34px;
  justify-content: center;
  width: 34px;
}

.roster-identity {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.roster-identity strong {
  color: var(--strong);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-identity span, .roster-seen small {
  color: var(--muted);
  font-size: 11px;
}

.roster-role {
  background: var(--chip-bg);
  border: 1px solid var(--chip-border);
  border-radius: 5px;
  color: var(--chip-text);
  display: inline-flex;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  justify-self: start;
  padding: 4px 7px;
}

.roster-seen {
  display: grid;
  gap: 4px;
  justify-items: end;
}

.roster-seen > span {
  align-items: center;
  color: var(--signal);
  display: inline-flex;
  font-size: 11px;
  gap: 5px;
}

.online-dot {
  height: 6px;
  width: 6px;
}

.roster-footer {
  color: #91a9a1;
  display: flex;
  font-size: 10px;
  justify-content: space-between;
  padding: 13px 22px;
}

.roster-empty {
  align-items: center;
  color: var(--muted);
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
  min-height: 190px;
}

.roster-empty .material-icons-outlined {
  color: #9bcfba;
  font-size: 31px;
}

.roster-empty p {
  font-size: 13px;
  margin: 0;
}

.roster-empty small {
  font-size: 11px;
}

.spinning {
  animation: spin .8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes blink {
  50% {
    opacity: .35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning, .pulse-dot {
    animation: none;
  }
}

@media (max-width: 900px) {
  .presence-page {
    padding: 22px 18px 32px;
  }

  .roster-row {
    grid-template-columns: 28px 38px minmax(0, 1fr) 100px;
  }

  .roster-row--selectable {
    grid-template-columns: 22px 28px 38px minmax(0, 1fr) 100px;
  }

  .roster-role {
    display: none;
  }
}

@media (max-width: 600px) {
  .presence-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .heading-status {
    align-self: flex-start;
  }

  .roster-heading, .roster-list {
    padding-left: 15px;
    padding-right: 15px;
  }

  .roster-row {
    gap: 9px;
    grid-template-columns: 26px 34px minmax(0, 1fr);
  }

  .roster-row--selectable {
    grid-template-columns: 20px 26px 34px minmax(0, 1fr);
  }

  .roster-seen {
    grid-column: 3;
    justify-items: start;
  }

  .roster-row--selectable .roster-seen {
    grid-column: 4;
  }

  .roster-footer {
    padding-left: 15px;
    padding-right: 15px;
  }
}

/* ==========================================================================
   深色：同一套绿调，明暗关系整体翻面
   底色取偏绿的近黑而不是通用 --bg，卡片比底色抬起一级，浅绿填充改成深绿填充，
   强调色提亮到能在深底上读出。--deep 是计数卡的实心品牌色块，两种主题下都保持深绿，
   所以卡片内部的浅绿文字与装饰线一律不动。
   ========================================================================== */
[data-theme="dark"] .presence-page {
  --ink: #eef5f2;
  --muted: #93aaa3;
  --line: #2a3833;
  --mint: #17332a;
  --signal: #4fd39a;
  --strong: #eef5f2;
  --surface: #1b2421;
  --chip-bg: #17332a;
  --chip-border: #2a3833;
  --chip-text: #4fd39a;
  background: #121a17;
}

/* 错误提示与错误心跳点沿用全局危险色，避免深色下留一块浅红底。 */
[data-theme="dark"] .error-banner {
  background: var(--danger-soft);
  border-color: var(--danger-border);
  color: var(--danger-text);
}

[data-theme="dark"] .heading-status--error .status-dot {
  background: var(--danger);
}

[data-theme="dark"] .roster-avatar {
  border-color: var(--line);
}

/* 序号与页脚在浅色下是低对比灰绿，深色下要跟着提亮一档才读得清。 */
[data-theme="dark"] .roster-index,
[data-theme="dark"] .roster-footer {
  color: var(--muted);
}
</style>
