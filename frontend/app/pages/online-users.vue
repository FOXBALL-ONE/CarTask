<template>
  <section class="presence-page">
    <header class="presence-heading">
      <div>
        <p class="eyebrow">LIVE PRESENCE / 02S PULSE</p>
        <h1>在线用户</h1>
        <p class="heading-copy">查看正在使用系统的账户。每 2 秒自动刷新一次，超过 10 秒未活动会暂时离线。</p>
      </div>
      <div class="heading-status" :class="{ 'heading-status--error': errorMessage }">
        <span class="status-dot" />
        <span>{{ errorMessage ? "连接异常" : "实时监测中" }}</span>
        <button class="refresh-button" type="button" :disabled="loading" title="立即刷新" @click="loadOnlineUsers">
          <span class="material-icons-outlined" :class="{ spinning: loading }">refresh</span>
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
        <div class="count-card__halo" aria-hidden="true" />
        <div class="count-card__content">
          <span class="card-kicker">CURRENTLY ONLINE</span>
          <strong>{{ onlineUsers.length }}</strong>
          <span class="count-caption">个用户正在使用系统</span>
        </div>
        <div class="count-card__footer">
          <span><i class="pulse-dot" />心跳正常</span>
          <span>阈值 {{ staleAfterSeconds }} 秒</span>
        </div>
      </article>

      <article class="pulse-card">
        <div class="pulse-card__top"><span class="card-kicker">PULSE WINDOW</span><span class="pulse-card__time">{{ lastUpdatedLabel }}</span></div>
        <div class="pulse-visual" aria-hidden="true">
          <span class="pulse-visual__ring pulse-visual__ring--outer" />
          <span class="pulse-visual__ring pulse-visual__ring--inner" />
          <span class="pulse-visual__core"><span class="material-icons-outlined">sensors</span></span>
        </div>
        <p>服务端正在收集每一次已认证请求，在线状态不会写入业务数据库。</p>
      </article>

      <article class="snapshot-card">
        <span class="card-kicker">SNAPSHOT</span>
        <div class="snapshot-card__value"><strong>{{ capturedAtLabel }}</strong><span>最近采样</span></div>
        <div class="snapshot-line"><span>刷新频率</span><b>2s</b></div>
        <div class="snapshot-line"><span>在线定义</span><b>≤ {{ staleAfterSeconds }}s</b></div>
      </article>
    </section>

    <section class="roster-panel">
      <header class="roster-heading">
        <div><span class="section-mark">01</span><div><h2>实时名册</h2><p>按最近活动时间排序</p></div></div>
        <span class="roster-total">{{ onlineUsers.length }} ONLINE</span>
      </header>

      <div v-if="loading && !onlineUsers.length" class="roster-empty"><span class="material-icons-outlined spinning">progress_activity</span><p>正在接收在线信号…</p></div>
      <div v-else-if="!onlineUsers.length" class="roster-empty"><span class="material-icons-outlined">no_accounts</span><p>当前没有检测到在线用户</p><small>用户发起请求后会自动出现在这里。</small></div>
      <div v-else class="roster-list">
        <article v-for="(user, index) in onlineUsers" :key="user.id" class="roster-row">
          <span class="roster-index">{{ String(index + 1).padStart(2, "0") }}</span>
          <span class="roster-avatar">{{ initials(user.display_name || user.username) }}</span>
          <div class="roster-identity"><strong>{{ user.display_name || user.username }}</strong><span>@{{ user.username }}</span></div>
          <span class="roster-role">{{ user.role }}</span>
          <div class="roster-seen"><span><i class="online-dot" />在线</span><small>最后活动 {{ relativeTime(user.last_seen) }}</small></div>
        </article>
      </div>
      <footer class="roster-footer"><span>数据来源：认证请求心跳</span><span>轮询间隔 2 秒</span></footer>
    </section>
  </section>
</template>

<script setup lang="ts">
interface OnlineUser { id: number; username: string; display_name?: string | null; role: string; last_seen: string }
interface OnlineResponse { items: OnlineUser[]; total: number; captured_at: string; stale_after_seconds: number }

const http = useHttp();
const authStore = useAuthStore();
const onlineUsers = ref<OnlineUser[]>([]);
const capturedAt = ref("");
const staleAfterSeconds = ref(10);
const loading = ref(false);
const errorMessage = ref("");
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const lastUpdatedLabel = computed(() => capturedAt.value ? formatTime(capturedAt.value) : "等待采样");
const capturedAtLabel = computed(() => capturedAt.value ? formatTime(capturedAt.value) : "—");

async function loadOnlineUsers() {
  loading.value = true;
  try {
    const result = await http.get<OnlineResponse>("/online-users");
    onlineUsers.value = result.items || [];
    capturedAt.value = result.captured_at || new Date().toISOString();
    staleAfterSeconds.value = result.stale_after_seconds || 10;
    errorMessage.value = "";
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "在线状态暂时无法读取";
  } finally {
    loading.value = false;
  }
}

function initials(value: string) { return value.trim().slice(0, 2).toUpperCase() || "?"; }
function formatTime(value: string) { return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date(value)); }
function relativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  return seconds < 2 ? "刚刚" : `${seconds} 秒前`;
}

onMounted(async () => {
  try {
    const user = await authStore.refreshSession();
    if (!user?.permissions.includes("user:read")) {
      await navigateTo("/");
      return;
    }
  } catch { /* 请求会显示可读错误状态。 */ }
  await loadOnlineUsers();
  refreshTimer = setInterval(loadOnlineUsers, 2_000);
});

onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer); });
</script>

<style scoped>
.presence-page { --ink: #11221f; --muted: #64807a; --line: #d7e7e1; --mint: #dff7eb; --signal: #16a36b; --deep: #123c35; min-height: 100%; padding: 30px 32px 42px; background: #f7fbf9; color: var(--ink); }
.presence-heading { align-items: flex-end; display: flex; gap: 20px; justify-content: space-between; margin: 0 auto 26px; max-width: 1240px; }.eyebrow, .card-kicker { color: var(--signal); font-family: "SFMono-Regular", Consolas, monospace; font-size: 10px; font-weight: 700; letter-spacing: .16em; margin: 0 0 8px; }.presence-heading h1 { font-size: clamp(28px, 4vw, 46px); font-weight: 680; letter-spacing: -.045em; margin: 0; }.heading-copy { color: var(--muted); font-size: 13px; margin: 8px 0 0; }.heading-status { align-items: center; background: #fff; border: 1px solid var(--line); border-radius: 999px; color: var(--deep); display: flex; font-size: 12px; gap: 8px; padding: 6px 8px 6px 13px; white-space: nowrap; }.heading-status--error { color: #b42318; }.status-dot, .online-dot, .pulse-dot { background: var(--signal); border-radius: 50%; display: inline-block; height: 7px; width: 7px; }.heading-status--error .status-dot { background: #d84a3a; }.refresh-button { align-items: center; background: var(--mint); border: 0; border-radius: 50%; color: var(--signal); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; margin-left: 4px; width: 28px; }.refresh-button:disabled { cursor: wait; opacity: .6; }.refresh-button .material-icons-outlined { font-size: 16px; }
.error-banner { align-items: center; background: #fff2f0; border: 1px solid #f4c9c2; border-radius: 8px; color: #a93a2f; display: flex; font-size: 12px; gap: 8px; margin: 0 auto 16px; max-width: 1240px; padding: 10px 12px; }.error-banner .material-icons-outlined { font-size: 17px; }.error-banner button { background: transparent; border: 0; color: inherit; cursor: pointer; font: inherit; font-weight: 650; margin-left: auto; text-decoration: underline; }
.signal-grid { display: grid; gap: 14px; grid-template-columns: minmax(260px, 1.15fr) minmax(270px, 1fr) minmax(220px, .8fr); margin: 0 auto 18px; max-width: 1240px; }.count-card, .pulse-card, .snapshot-card, .roster-panel { background: #fff; border: 1px solid var(--line); border-radius: 14px; overflow: hidden; }.count-card { background: var(--deep); color: #f4fff9; min-height: 220px; padding: 22px; position: relative; }.count-card__halo { border: 1px solid rgb(164 244 205 / 24%); border-radius: 50%; height: 230px; position: absolute; right: -58px; top: -60px; width: 230px; }.count-card__halo::after { border: 1px solid rgb(164 244 205 / 16%); border-radius: 50%; content: ""; inset: 24px; position: absolute; }.count-card__content { position: relative; z-index: 1; }.count-card .card-kicker { color: #8ee6b9; }.count-card strong { display: block; font-family: "SFMono-Regular", Consolas, monospace; font-size: clamp(58px, 8vw, 86px); font-weight: 500; letter-spacing: -.08em; line-height: .95; margin: 18px 0 8px; }.count-caption { color: #b9d9cb; font-size: 12px; }.count-card__footer { bottom: 18px; color: #9ac6b4; display: flex; font-size: 10px; justify-content: space-between; left: 22px; position: absolute; right: 22px; }.count-card__footer span { align-items: center; display: inline-flex; gap: 6px; }.pulse-dot { animation: blink 1.8s ease-in-out infinite; box-shadow: 0 0 0 4px rgb(93 226 157 / 14%); }
.pulse-card { min-height: 220px; padding: 22px; position: relative; }.pulse-card__top { align-items: center; display: flex; justify-content: space-between; }.pulse-card__time { color: var(--muted); font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; }.pulse-visual { height: 123px; margin: 2px auto 0; position: relative; width: 170px; }.pulse-visual__ring { border: 1px solid #9fdfc0; border-radius: 50%; left: 50%; position: absolute; top: 50%; transform: translate(-50%, -50%); }.pulse-visual__ring--outer { animation: breathe 2.4s ease-in-out infinite; height: 112px; opacity: .55; width: 112px; }.pulse-visual__ring--inner { height: 76px; opacity: .7; width: 76px; }.pulse-visual__core { align-items: center; background: var(--mint); border: 7px solid #fff; border-radius: 50%; box-shadow: 0 0 0 1px #a5e2c4; color: var(--signal); display: flex; height: 52px; justify-content: center; left: 50%; position: absolute; top: 50%; transform: translate(-50%, -50%); width: 52px; }.pulse-visual__core .material-icons-outlined { font-size: 24px; }.pulse-card p { color: var(--muted); font-size: 11px; line-height: 1.55; margin: 0; text-align: center; }.snapshot-card { min-height: 220px; padding: 22px; }.snapshot-card__value { border-bottom: 1px solid var(--line); display: grid; gap: 4px; padding: 20px 0 18px; }.snapshot-card__value strong { color: var(--deep); font-family: "SFMono-Regular", Consolas, monospace; font-size: 25px; letter-spacing: -.06em; }.snapshot-card__value span, .snapshot-line span { color: var(--muted); font-size: 11px; }.snapshot-line { align-items: center; border-bottom: 1px solid var(--line); display: flex; justify-content: space-between; padding: 11px 0; }.snapshot-line:last-child { border-bottom: 0; }.snapshot-line b { color: var(--deep); font-family: "SFMono-Regular", Consolas, monospace; font-size: 12px; }
.roster-panel { margin: 0 auto; max-width: 1240px; }.roster-heading { align-items: center; border-bottom: 1px solid var(--line); display: flex; justify-content: space-between; padding: 18px 22px; }.roster-heading > div { align-items: center; display: flex; gap: 13px; }.section-mark { color: var(--signal); font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; }.roster-heading h2 { font-size: 15px; margin: 0; }.roster-heading p { color: var(--muted); font-size: 11px; margin: 3px 0 0; }.roster-total { color: var(--signal); font-family: "SFMono-Regular", Consolas, monospace; font-size: 10px; letter-spacing: .1em; }.roster-list { padding: 0 22px; }.roster-row { align-items: center; border-bottom: 1px solid var(--line); display: grid; gap: 14px; grid-template-columns: 32px 38px minmax(160px, 1fr) 110px minmax(130px, .7fr); min-height: 68px; }.roster-row:last-child { border-bottom: 0; }.roster-index { color: #95afa7; font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; }.roster-avatar { align-items: center; background: var(--mint); border: 1px solid #b9e8cf; border-radius: 11px; color: var(--signal); display: flex; font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; height: 34px; justify-content: center; width: 34px; }.roster-identity { display: grid; gap: 3px; min-width: 0; }.roster-identity strong { color: var(--deep); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.roster-identity span, .roster-seen small { color: var(--muted); font-size: 11px; }.roster-role { background: #f0f8f4; border: 1px solid #d4ebe0; border-radius: 5px; color: #33745b; display: inline-flex; font-family: "SFMono-Regular", Consolas, monospace; font-size: 10px; justify-self: start; padding: 4px 7px; }.roster-seen { display: grid; gap: 4px; justify-items: end; }.roster-seen > span { align-items: center; color: var(--signal); display: inline-flex; font-size: 11px; gap: 5px; }.online-dot { height: 6px; width: 6px; }.roster-footer { color: #91a9a1; display: flex; font-size: 10px; justify-content: space-between; padding: 13px 22px; }
.roster-empty { align-items: center; color: var(--muted); display: flex; flex-direction: column; gap: 7px; justify-content: center; min-height: 190px; }.roster-empty .material-icons-outlined { color: #9bcfba; font-size: 31px; }.roster-empty p { font-size: 13px; margin: 0; }.roster-empty small { font-size: 11px; }.spinning { animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }@keyframes blink { 50% { opacity: .35; } }@keyframes breathe { 50% { opacity: .2; transform: translate(-50%, -50%) scale(1.08); } }@media (prefers-reduced-motion: reduce) { .spinning, .pulse-dot, .pulse-visual__ring--outer { animation: none; } }
@media (max-width: 900px) { .presence-page { padding: 22px 18px 32px; }.signal-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.count-card { grid-row: span 2; }.snapshot-card { min-height: auto; }.roster-row { grid-template-columns: 28px 38px minmax(0, 1fr) 100px; }.roster-role { display: none; } }
@media (max-width: 600px) { .presence-heading { align-items: flex-start; flex-direction: column; }.heading-status { align-self: flex-start; }.signal-grid { grid-template-columns: 1fr; }.count-card { grid-row: auto; }.roster-heading, .roster-list { padding-left: 15px; padding-right: 15px; }.roster-row { gap: 9px; grid-template-columns: 26px 34px minmax(0, 1fr); }.roster-seen { grid-column: 3; justify-items: start; }.roster-footer { padding-left: 15px; padding-right: 15px; } }
</style>
