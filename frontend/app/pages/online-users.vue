<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">在线用户</h1>
        <p class="page__desc">查看正在使用系统的账户，每 2 秒自动刷新，超过 10 秒未活动会暂时离线</p>
      </div>
      <div class="page__actions">
        <label v-if="canForceLogout" class="pick-all">
          <input :checked="allSelected" :disabled="!onlineUsers.length" :indeterminate.prop="someSelected"
                 type="checkbox" @change="toggleAll">
          <span>全选</span>
        </label>
        <button v-if="canForceLogout" :disabled="!selectedIds.length || loggingOut" class="btn btn--danger-soft btn--sm"
                type="button" @click="forceLogoutSelected">
          <span class="material-icons-outlined">logout</span>{{ loggingOut ? "处理中…" : `强制登出${selectedIds.length ? ` (${selectedIds.length})` : ""}` }}
        </button>
      </div>
    </header>

    <p v-if="actionError || actionMessage" :class="{ 'notice--error': actionError }" class="notice" role="status">
      {{ actionError || actionMessage }}
    </p>

    <section class="card">
      <header class="card__head">
        <div><span class="section-mark">01</span>
          <div><h2>实时名册</h2>
            <p>按最近活动时间排序</p></div>
        </div>
        <span class="online-count"><i class="online-dot"/>{{ onlineUsers.length }} 在线</span>
      </header>

      <div v-if="errorMessage" class="state state--error">
        <span class="material-icons-outlined">wifi_off</span>{{ errorMessage }}
        <button class="btn btn--ghost btn--sm" type="button" @click="loadOnlineUsers">重试</button>
      </div>
      <div v-else-if="loading && !onlineUsers.length" class="state"><span class="spinner"/>正在接收在线信号…</div>
      <div v-else-if="!onlineUsers.length" class="empty-state">
        <span class="material-icons-outlined">no_accounts</span>
        <strong>当前没有检测到在线用户</strong>
        <small>用户发起请求后会自动出现在这里</small>
      </div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th v-if="canForceLogout" class="check-cell"/>
            <th>编号</th>
            <th>用户</th>
            <th>角色</th>
            <th>状态</th>
            <th>最后活动</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(user, index) in onlineUsers" :key="user.id" :class="{ 'row--selected': selectedIds.includes(user.id) }">
            <td v-if="canForceLogout" class="check-cell">
              <input v-model="selectedIds" :aria-label="`选择 ${user.display_name || user.username}`" :value="user.id" type="checkbox">
            </td>
            <td class="text-sub">{{ String(index + 1).padStart(2, "0") }}</td>
            <td>
              <div class="identity">
                <span class="avatar">{{ initials(user.display_name || user.username) }}</span>
                <span><strong>{{ user.display_name || user.username }}</strong><small>@{{ user.username }}</small></span>
              </div>
            </td>
            <td><span class="tag tag--blue">{{ user.role }}</span></td>
            <td><span class="tag tag--green"><i class="online-dot"/>在线</span></td>
            <td class="text-sub nowrap">{{ relativeTime(user.last_seen) }}</td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer class="card__foot"><span>数据来源：认证请求心跳</span><span>轮询间隔 2 秒</span></footer>
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
.page {
  min-height: 100%;
  padding: 24px
}

.page__header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 20px
}

.page__title {
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  margin: 0
}

.page__desc {
  color: var(--text-sub);
  font-size: 13px;
  margin: 2px 0 0
}

.page__actions {
  align-items: center;
  display: flex;
  gap: 10px
}

.pick-all {
  align-items: center;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  font-size: 12px;
  gap: 6px;
  white-space: nowrap;
}

.pick-all input {
  accent-color: var(--primary);
  cursor: pointer;
  height: 14px;
  margin: 0;
  width: 14px;
}

.pick-all input:disabled {
  cursor: not-allowed;
}

.notice {
  background: var(--success-soft);
  border: 1px solid var(--success);
  border-radius: 6px;
  color: var(--success);
  font-size: 13px;
  margin: 0 0 14px;
  padding: 9px 12px;
}

.notice--error {
  background: var(--danger-soft);
  border-color: var(--red);
  color: var(--red);
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  overflow: hidden
}

.card__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  padding: 16px 18px;
}

.card__head > div {
  align-items: center;
  display: flex;
  gap: 13px;
}

.section-mark {
  color: var(--primary);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.card__head h2 {
  font-size: 15px;
  margin: 0;
  color: var(--text);
}

.card__head p {
  color: var(--text-mute);
  font-size: 11px;
  margin: 3px 0 0;
}

.online-count {
  align-items: center;
  color: var(--success);
  display: inline-flex;
  font-size: 12px;
  font-weight: 600;
  gap: 6px;
}

.online-dot {
  background: var(--success);
  border-radius: 50%;
  display: inline-block;
  height: 6px;
  width: 6px;
}

.btn {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  gap: 5px;
  height: 32px;
  justify-content: center;
  padding: 0 12px;
  white-space: nowrap
}

.btn .material-icons-outlined {
  font-size: 16px
}

.btn--ghost {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text-sub)
}

.btn--danger-soft {
  background: var(--danger-soft);
  color: var(--danger)
}

.btn--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px
}

.btn:disabled {
  cursor: not-allowed;
  opacity: .6
}

.table-wrap {
  overflow-x: auto
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 760px;
  width: 100%
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 12px;
  font-weight: 500;
  padding: 10px 16px;
  text-align: left;
  white-space: nowrap
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 11px 16px;
  vertical-align: middle;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.table tbody tr:hover, .table tbody tr.row--selected {
  background: var(--bg)
}

.check-cell {
  width: 24px;
}

.check-cell input {
  accent-color: var(--primary);
  cursor: pointer;
}

.text-sub {
  color: var(--text-sub) !important
}

.nowrap {
  white-space: nowrap
}

.identity {
  align-items: center;
  display: flex;
  gap: 10px
}

.avatar {
  align-items: center;
  background: var(--primary-soft);
  border-radius: 8px;
  color: var(--primary);
  display: inline-flex;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  height: 32px;
  justify-content: center;
  width: 32px;
  flex-shrink: 0;
}

.identity strong {
  color: var(--text);
  display: block;
  font-size: 13px;
}

.identity small {
  color: var(--text-mute);
  display: block;
  font-size: 11px;
}

.tag {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 500;
  gap: 4px;
  line-height: 1.5;
  padding: 2px 8px
}

.tag--green {
  background: var(--success-soft);
  color: var(--success)
}

.tag--blue {
  background: var(--primary-soft);
  color: var(--primary)
}

.state, .empty-state {
  color: var(--text-mute);
  padding: 40px;
  text-align: center
}

.state {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: center;
}

.state .material-icons-outlined {
  font-size: 17px;
}

.state--error {
  color: var(--red)
}

.state--error .btn {
  margin-left: 6px;
}

.empty-state {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
  min-height: 190px;
}

.empty-state .material-icons-outlined {
  color: var(--text-mute);
  font-size: 32px;
  margin-bottom: 4px;
  opacity: .65;
}

.empty-state strong {
  color: var(--text-sub);
  font-size: 13px;
}

.empty-state small {
  font-size: 11px;
}

.spinner {
  animation: spin .8s linear infinite;
  border: 2px solid var(--border-strong);
  border-radius: 50%;
  border-top-color: var(--primary);
  display: inline-block;
  height: 16px;
  width: 16px;
}

.card__foot {
  color: var(--text-mute);
  display: flex;
  font-size: 10px;
  justify-content: space-between;
  padding: 13px 18px;
  border-top: 1px solid var(--border);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation: none;
  }
}

@media (max-width: 768px) {
  .page {
    padding: 16px
  }
}

@media (max-width: 600px) {
  .page__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
