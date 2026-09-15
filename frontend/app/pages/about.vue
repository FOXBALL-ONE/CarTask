<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">关于系统</h1>
        <p class="page__desc">当前实例的版本、运行环境与技术栈</p>
      </div>
    </header>

    <section class="hero">
      <div class="hero__mark">{{ systemName.trim().charAt(0).toUpperCase() || "C" }}</div>
      <div class="hero__body">
        <h2>{{ systemName }}</h2>
        <p>智慧停车管理系统 · {{ about?.application.version || "读取中..." }}</p>
      </div>
      <span v-if="about" :class="{ 'state-badge--error': loadError }" class="state-badge">
        {{ loadError ? "运行环境读取失败" : "运行中" }}
      </span>
    </section>

    <section class="grid">
      <article class="card">
        <h3><span class="material-icons-outlined">memory</span>运行环境</h3>
        <dl>
          <dt>Java</dt>
          <dd>{{ about?.runtime.java_version || "—" }}</dd>
          <dt>JVM</dt>
          <dd>{{ about?.runtime.jvm_name || "—" }}</dd>
          <dt>运行厂商</dt>
          <dd>{{ about?.runtime.java_vendor || "—" }}</dd>
          <dt>Spring Boot</dt>
          <dd>{{ about?.framework.spring_boot_version || "—" }}</dd>
          <dt>时区</dt>
          <dd>{{ about?.runtime.time_zone || "—" }}</dd>
        </dl>
      </article>

      <article class="card">
        <h3><span class="material-icons-outlined">storage</span>数据存储</h3>
        <dl>
          <dt>数据库</dt>
          <dd>{{ about?.database.product || "—" }}</dd>
          <dt>版本</dt>
          <dd>{{ about?.database.version || "—" }}</dd>
          <dt>缓存</dt>
          <dd>Redis</dd>
        </dl>
      </article>

      <article class="card">
        <h3><span class="material-icons-outlined">schedule</span>本次运行</h3>
        <dl>
          <dt>启动时间</dt>
          <dd>{{ about ? formatDateTime(about.runtime.started_at) : "—" }}</dd>
          <dt>已运行</dt>
          <dd>{{ uptimeText }}</dd>
        </dl>
      </article>
    </section>

    <section class="notice-panel">
      <div class="notice-panel__title"><span class="material-icons-outlined">layers</span>技术栈</div>
      <ul>
        <li><strong>后端</strong>：Spring Boot + Kotlin，PostgreSQL 存业务数据，Redis 存会话与验证码，JWT 无状态鉴权。</li>
        <li><strong>前端</strong>：Nuxt + Vue 3 + Tailwind，服务端渲染，按权限码控制页面与操作入口。</li>
        <li><strong>外部对接</strong>：科拓开放平台，用于停车区域、车辆进出记录与抓拍图片同步。</li>
      </ul>
      <p class="notice-panel__foot">
        这里只显示版本与运行环境。主机名、内存、磁盘、连接数等信息在「系统监控」页，那一页单独受
        <code>system-monitor:read</code> 控制。
      </p>
    </section>

    <p v-if="loadError" class="feedback error" role="alert">{{ loadError }}</p>
  </section>
</template>

<script lang="ts" setup>
interface AboutData {
  application: { name: string; version: string };
  runtime: {
    java_version: string;
    java_vendor: string;
    jvm_name: string;
    started_at: string;
    uptime_millis: number;
    time_zone: string;
  };
  framework: { spring_boot_version: string | null };
  database: { product: string; version: string };
}

const http = useHttp();
const about = ref<AboutData | null>(null);
const loadError = ref("");
const systemName = ref("智慧停车管理系统");
/**
 * 运行时长本地往上加，不拿客户端的 Date.now() 去减服务端的启动时间——两边时钟有偏差时，
 * 那个差值可能是负的，也可能凭空多出几个小时。以拉取那一刻的值为基准再累加本地流逝的时间，
 * 结果只取决于单调的时间差。
 */
const uptimeBaseMillis = ref(0);
const uptimeFetchedAt = ref(0);
/** 每秒更新一次的本地时刻；计算属性依赖它才会跟着重算。 */
const uptimeNow = ref(0);
let tickTimer: ReturnType<typeof setInterval> | undefined;

const uptimeText = computed(() => {
  if (!about.value) return "—";
  const elapsed = (uptimeNow.value || Date.now()) - uptimeFetchedAt.value;
  return formatDuration(uptimeBaseMillis.value + Math.max(0, elapsed));
});

function formatDuration(millis: number) {
  const totalSeconds = Math.floor(millis / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return days > 0
      ? `${days} 天 ${hours} 小时 ${minutes} 分`
      : hours > 0
          ? `${hours} 小时 ${minutes} 分 ${seconds} 秒`
          : `${minutes} 分 ${seconds} 秒`;
}

function formatDateTime(value: string) {
  return value.replace("T", " ").slice(0, 19);
}

onMounted(async () => {
  const stored = localStorage.getItem("sysName");
  if (stored) systemName.value = stored;
  try {
    about.value = await http.get<AboutData>("/system/about");
    uptimeBaseMillis.value = about.value.runtime.uptime_millis;
    uptimeFetchedAt.value = Date.now();
    uptimeNow.value = uptimeFetchedAt.value;
    tickTimer = setInterval(() => {
      uptimeNow.value = Date.now();
    }, 1000);
  } catch (error) {
    loadError.value = (error as { statusMessage?: string }).statusMessage || "运行环境读取失败";
  }
});

onBeforeUnmount(() => {
  if (tickTimer) clearInterval(tickTimer);
});
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px;
}

.page__header {
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
  margin: 4px 0 0;
}

.hero {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  display: flex;
  gap: 16px;
  padding: 22px 24px;
}

.hero__mark {
  align-items: center;
  background: var(--primary);
  border-radius: 12px;
  color: #fff;
  display: flex;
  flex: 0 0 52px;
  font-size: 24px;
  font-weight: 700;
  height: 52px;
  justify-content: center;
}

.hero__body {
  flex: 1;
  min-width: 0;
}

.hero__body h2 {
  color: var(--text);
  font-size: 17px;
  font-weight: 650;
  margin: 0;
}

.hero__body p {
  color: var(--text-sub);
  margin: 5px 0 0;
}

.state-badge {
  background: color-mix(in srgb, #059669 12%, var(--card));
  border: 1px solid transparent;
  border-radius: 4px;
  color: #059669;
  font-size: 11px;
  padding: 3px 9px;
}

.state-badge--error {
  background: color-mix(in srgb, var(--red) 10%, var(--card));
  color: var(--red);
}

.grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 16px;
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  padding: 18px;
}

.card h3 {
  align-items: center;
  color: var(--text);
  display: flex;
  font-size: 14px;
  font-weight: 600;
  gap: 7px;
  margin: 0 0 12px;
}

.card h3 .material-icons-outlined {
  color: var(--primary);
  font-size: 18px;
}

.card dl {
  display: grid;
  gap: 8px 12px;
  grid-template-columns: auto minmax(0, 1fr);
  margin: 0;
}

.card dt {
  color: var(--text-mute);
  font-size: 12px;
  white-space: nowrap;
}

.card dd {
  color: var(--text);
  font-size: 12px;
  margin: 0;
  overflow-wrap: anywhere;
}

.notice-panel {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text-sub);
  margin-top: 18px;
  padding: 16px 18px;
}

.notice-panel__title {
  align-items: center;
  color: var(--text);
  display: flex;
  font-weight: 600;
  gap: 6px;
}

.notice-panel__title .material-icons-outlined {
  color: var(--primary);
  font-size: 18px;
}

.notice-panel ul {
  line-height: 1.8;
  margin: 8px 0 0;
  padding-left: 20px;
}

.notice-panel strong {
  color: var(--text);
}

.notice-panel__foot {
  margin: 12px 0 0;
}

.notice-panel code {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  font-size: 12px;
  padding: 1px 5px;
}

.feedback {
  margin: 14px 0 0;
}

.feedback.error {
  color: var(--red);
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .page {
    padding: 16px;
  }

  .hero {
    flex-wrap: wrap;
  }
}
</style>
