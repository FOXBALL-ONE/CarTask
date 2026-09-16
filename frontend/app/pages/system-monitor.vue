<template>
  <section class="monitor-page">
    <header class="page-heading">
      <div>
        <p class="page-heading__eyebrow">RUNTIME / LIVE SNAPSHOT</p>
        <h1>系统监控</h1>
        <p v-if="snapshot" class="page-heading__meta">最近更新 {{ formatDateTime(snapshot.captured_at) }}</p>
      </div>
      <div class="heading-actions">
        <span class="live-indicator"><i/>30 秒自动刷新</span>
        <button :disabled="loading" class="refresh-button" title="刷新监控数据" type="button" @click="loadSnapshot">
          <span :class="{ spinning: loading }" class="material-icons-outlined">refresh</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="monitor-error" role="alert">
      <span class="material-icons-outlined">error_outline</span>{{ errorMessage }}
    </p>

    <div v-if="loading && !snapshot" aria-live="polite" class="monitor-loading">
      正在读取运行状态
    </div>

    <template v-else-if="snapshot">
      <section aria-label="关键资源概览" class="overview-grid">
        <article class="overview-metric overview-metric--cpu">
          <div class="overview-metric__label"><span class="material-icons-outlined">memory</span>系统 CPU</div>
          <strong>{{ formatPercent(snapshot.system.system_cpu_load) }}</strong>
          <small>应用进程 {{ formatPercent(snapshot.system.process_cpu_load) }}</small>
        </article>
        <article class="overview-metric overview-metric--memory">
          <div class="overview-metric__label"><span class="material-icons-outlined">dns</span>物理内存</div>
          <strong>{{ formatBytes(snapshot.system.physical_memory.used) }}</strong>
          <small>总计 {{ formatBytes(snapshot.system.physical_memory.max) }}</small>
        </article>
        <article class="overview-metric overview-metric--heap">
          <div class="overview-metric__label"><span class="material-icons-outlined">data_object</span>JVM 堆内存</div>
          <strong>{{ formatBytes(snapshot.jvm.heap_memory.used) }}</strong>
          <small>最大 {{ formatBytes(snapshot.jvm.heap_memory.max) }}</small>
        </article>
        <article class="overview-metric overview-metric--gc">
          <div class="overview-metric__label"><span class="material-icons-outlined">auto_delete</span>GC 累计耗时</div>
          <strong>{{ formatDuration(gcTime) }}</strong>
          <small>{{ gcCount.toLocaleString("zh-CN") }} 次回收</small>
        </article>
      </section>

      <section class="monitor-grid monitor-grid--primary">
        <article class="monitor-panel">
          <header class="panel-heading">
            <div><span class="panel-heading__icon material-icons-outlined">computer</span>
              <h2>服务器信息</h2></div>
            <span class="panel-status">{{ snapshot.system.available_processors }} 核</span>
          </header>
          <dl class="key-value-grid">
            <div>
              <dt>服务器名称</dt>
              <dd>{{ snapshot.system.host_name }}</dd>
            </div>
            <div>
              <dt>服务器 IP</dt>
              <dd>{{ snapshot.system.host_address }}</dd>
            </div>
            <div>
              <dt>操作系统</dt>
              <dd>{{ snapshot.system.operating_system }}</dd>
            </div>
            <div>
              <dt>系统架构</dt>
              <dd>{{ snapshot.system.architecture }}</dd>
            </div>
          </dl>
          <div class="section-rule"/>
          <div class="subsection-heading"><h3>磁盘状态</h3><span>{{ snapshot.system.disks.length }} 个卷</span></div>
          <div class="disk-list">
            <div v-for="disk in snapshot.system.disks" :key="disk.path" class="disk-row">
              <div class="disk-row__name"><span class="material-icons-outlined">storage</span><strong>{{
                  disk.path
                }}</strong><small>{{ disk.file_system }}</small></div>
              <div class="disk-row__usage">
                <div class="metric-track"><span
                    :style="{ width: memoryWidth({ used: disk.used_bytes, committed: disk.total_bytes, max: disk.total_bytes }) }"/>
                </div>
                <small>已用 {{ formatBytes(disk.used_bytes) }} / {{ formatBytes(disk.total_bytes) }}</small></div>
              <strong class="disk-row__free">可用 {{ formatBytes(disk.usable_bytes) }}</strong>
            </div>
            <p v-if="snapshot.system.disks.length === 0" class="empty-state">未读取到磁盘信息</p>
          </div>
        </article>

        <article class="monitor-panel">
          <header class="panel-heading">
            <div><span
                class="panel-heading__icon panel-heading__icon--violet material-icons-outlined">settings_suggest</span>
              <h2>Java 虚拟机</h2></div>
            <span class="panel-status">运行 {{ formatDuration(snapshot.jvm.uptime_millis) }}</span>
          </header>
          <div class="memory-usage-list">
            <div class="memory-usage">
              <div class="memory-usage__label"><span>堆内存</span><strong>{{
                  formatBytes(snapshot.jvm.heap_memory.used)
                }}</strong></div>
              <div class="usage-rail"><span :style="{ width: memoryWidth(snapshot.jvm.heap_memory) }"/></div>
              <small>已提交 {{ formatBytes(snapshot.jvm.heap_memory.committed) }} · 最大
                {{ formatBytes(snapshot.jvm.heap_memory.max) }}</small></div>
            <div class="memory-usage memory-usage--non-heap">
              <div class="memory-usage__label">
                <span>非堆内存</span><strong>{{ formatBytes(snapshot.jvm.non_heap_memory.used) }}</strong></div>
              <div class="usage-rail"><span :style="{ width: memoryWidth(snapshot.jvm.non_heap_memory) }"/></div>
              <small>已提交 {{ formatBytes(snapshot.jvm.non_heap_memory.committed) }} · 最大
                {{ formatBytes(snapshot.jvm.non_heap_memory.max) }}</small></div>
          </div>
          <dl class="key-value-grid key-value-grid--jvm">
            <div>
              <dt>Java 名称</dt>
              <dd>{{ snapshot.jvm.name }}</dd>
            </div>
            <div>
              <dt>Java 版本</dt>
              <dd>{{ snapshot.jvm.version }}</dd>
            </div>
            <div>
              <dt>运行时厂商</dt>
              <dd>{{ snapshot.jvm.vendor }}</dd>
            </div>
            <div>
              <dt>启动时间</dt>
              <dd>{{ formatDateTime(snapshot.jvm.start_time) }}</dd>
            </div>
            <div>
              <dt>活动线程</dt>
              <dd>{{ snapshot.jvm.thread_count }}</dd>
            </div>
            <div>
              <dt>峰值线程</dt>
              <dd>{{ snapshot.jvm.peak_thread_count }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <section class="monitor-grid monitor-grid--secondary">
        <article class="monitor-panel gc-panel">
          <header class="panel-heading">
            <div><span class="panel-heading__icon panel-heading__icon--amber material-icons-outlined">auto_delete</span>
              <h2>垃圾回收</h2></div>
            <span class="panel-status">{{ gcCount.toLocaleString("zh-CN") }} 次</span>
          </header>
          <div class="gc-list">
            <div v-for="collector in snapshot.garbageCollectors" :key="collector.name" class="gc-row">
              <div><strong>{{ collector.name }}</strong><small>累计回收次数</small></div>
              <b>{{ formatCount(collector.collection_count) }}</b>
              <div><strong>{{ formatDuration(collector.collection_time_millis) }}</strong><small>累计回收耗时</small>
              </div>
            </div>
            <p v-if="snapshot.garbageCollectors.length === 0" class="empty-state">未读取到垃圾回收器信息</p>
          </div>
        </article>

        <article class="monitor-panel service-panel">
          <header class="panel-heading">
            <div><span class="panel-heading__icon panel-heading__icon--green material-icons-outlined">database</span>
              <h2>数据库</h2></div>
            <span :class="healthClass(snapshot.database.status)"
                  class="health-badge"><i/>{{ healthLabel(snapshot.database.status) }}</span>
          </header>
          <dl v-if="snapshot.database.status === 'UP'" class="service-details">
            <div>
              <dt>数据库</dt>
              <dd>{{ snapshot.database.product }} {{ snapshot.database.version }}</dd>
            </div>
            <div>
              <dt>连接地址</dt>
              <dd class="service-details__mono">{{ snapshot.database.url }}</dd>
            </div>
          </dl>
          <p v-else class="service-unavailable">数据库连接不可用</p>
          <div class="service-metrics">
            <div><span>活动连接</span><strong>{{ formatCount(snapshot.database.active_connections) }}</strong></div>
            <div><span>空闲连接</span><strong>{{ formatCount(snapshot.database.idle_connections) }}</strong></div>
            <div><span>连接池上限</span><strong>{{ formatCount(snapshot.database.max_connections) }}</strong></div>
          </div>
        </article>
      </section>

      <section class="monitor-panel redis-panel">
        <header class="panel-heading">
          <div><span class="panel-heading__icon panel-heading__icon--red material-icons-outlined">hub</span>
            <h2>Redis</h2></div>
          <span :class="healthClass(snapshot.redis.status)"
                class="health-badge"><i/>{{ healthLabel(snapshot.redis.status) }}</span>
        </header>
        <div v-if="snapshot.redis.status === 'UP'" class="redis-layout">
          <dl class="service-details redis-details">
            <div>
              <dt>Redis 版本</dt>
              <dd>{{ snapshot.redis.version ?? "—" }}</dd>
            </div>
            <div>
              <dt>运行模式</dt>
              <dd>{{ snapshot.redis.mode ?? "—" }}</dd>
            </div>
            <div>
              <dt>端口</dt>
              <dd>{{ snapshot.redis.port ?? "—" }}</dd>
            </div>
            <div>
              <dt>运行时长</dt>
              <dd>{{ formatDuration((snapshot.redis.uptime_seconds ?? 0) * 1000) }}</dd>
            </div>
          </dl>
          <div class="redis-metrics">
            <div><span>已用内存</span><strong>{{ formatBytes(snapshot.redis.used_memory_bytes) }}</strong><small>上限
              {{ formatBytes(snapshot.redis.max_memory_bytes) }}</small></div>
            <div><span>客户端数</span><strong>{{
                formatCount(snapshot.redis.connected_clients)
              }}</strong><small>当前已连接</small></div>
            <div><span>Key 数量</span><strong>{{
                formatCount(snapshot.redis.key_count)
              }}</strong><small>所有逻辑库</small></div>
            <div><span>命令速率</span><strong>{{ formatCount(snapshot.redis.operations_per_second) }}</strong><small>ops
              / 秒</small></div>
            <div><span>命中次数</span><strong>{{
                formatCount(snapshot.redis.keyspace_hits)
              }}</strong><small>累计命中</small></div>
            <div><span>未命中次数</span><strong>{{
                formatCount(snapshot.redis.keyspace_misses)
              }}</strong><small>累计未命中</small></div>
          </div>
        </div>
        <p v-else class="service-unavailable">Redis 服务不可用</p>
      </section>
    </template>
  </section>
</template>

<script lang="ts" setup>
interface UsageData {
  used: number;
  committed: number;
  max: number
}

interface DiskData {
  path: string;
  file_system: string;
  total_bytes: number;
  usable_bytes: number;
  used_bytes: number
}

interface SystemData {
  host_name: string;
  host_address: string;
  operating_system: string;
  architecture: string;
  available_processors: number;
  system_cpu_load: number | null;
  process_cpu_load: number | null;
  physical_memory: UsageData;
  disks: DiskData[]
}

interface JvmData {
  name: string;
  vendor: string;
  version: string;
  start_time: string;
  uptime_millis: number;
  heap_memory: UsageData;
  non_heap_memory: UsageData;
  thread_count: number;
  peak_thread_count: number
}

interface GcData {
  name: string;
  collection_count: number;
  collection_time_millis: number
}

interface DatabaseData {
  status: "UP" | "DOWN";
  product: string | null;
  version: string | null;
  driver: string | null;
  url: string | null;
  active_connections: number | null;
  idle_connections: number | null;
  total_connections: number | null;
  max_connections: number | null;
  waiting_threads: number | null
}

interface RedisData {
  status: "UP" | "DOWN";
  version: string | null;
  mode: string | null;
  port: string | null;
  uptime_seconds: number | null;
  connected_clients: number | null;
  used_memory_bytes: number | null;
  max_memory_bytes: number | null;
  key_count: number | null;
  operations_per_second: number | null;
  keyspace_hits: number | null;
  keyspace_misses: number | null
}

interface MonitorSnapshot {
  captured_at: string;
  system: SystemData;
  jvm: JvmData;
  garbageCollectors: GcData[];
  database: DatabaseData;
  redis: RedisData
}

const http = useHttp();
const authStore = useAuthStore();
const snapshot = ref<MonitorSnapshot | null>(null);
const loading = ref(false);
const errorMessage = ref("");
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const gcCount = computed(() => snapshot.value?.garbageCollectors.reduce((total, collector) => total + Math.max(0, collector.collection_count), 0) ?? 0);
const gcTime = computed(() => snapshot.value?.garbageCollectors.reduce((total, collector) => total + Math.max(0, collector.collection_time_millis), 0) ?? 0);

async function loadSnapshot() {
  loading.value = true;
  errorMessage.value = "";
  try {
    snapshot.value = await http.get<MonitorSnapshot>("/system-monitor");
  } catch (error) {
    if ((error as { statusCode?: number }).statusCode === 403) {
      snapshot.value = null;
      await authStore.refreshSession().catch(() => undefined);
      await navigateTo("/");
      return;
    }
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "无法读取系统监控数据";
  } finally {
    loading.value = false;
  }
}

function memoryWidth(usage: UsageData) {
  const limit = usage.max > 0 ? usage.max : usage.committed;
  return limit > 0 ? `${Math.min(100, Math.max(0, usage.used / limit * 100))}%` : "0%";
}

function formatBytes(value: number | null) {
  if (value === null || value < 0 || !Number.isFinite(value)) return "—";
  if (value < 1024) return `${value} B`;
  const units = ["KB", "MB", "GB", "TB"];
  const index = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)) - 1);
  return `${(value / 1024 ** (index + 1)).toFixed(value >= 1024 ** 3 ? 2 : 1)} ${units[index]}`;
}

function formatPercent(value: number | null) {
  return value === null || value < 0 ? "—" : `${(value * 100).toFixed(1)}%`;
}

function formatCount(value: number | null) {
  return value === null || value < 0 ? "—" : value.toLocaleString("zh-CN");
}

function formatDuration(milliseconds: number) {
  if (!Number.isFinite(milliseconds) || milliseconds < 0) return "—";
  const seconds = Math.floor(milliseconds / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分`;
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`;
}

function formatDateTime(value: string) {
  return value.replace("T", " ").replace(/\.\d+$/, "");
}

function healthLabel(status: "UP" | "DOWN") {
  return status === "UP" ? "运行正常" : "不可用";
}

function healthClass(status: "UP" | "DOWN") {
  return status === "UP" ? "health-badge--up" : "health-badge--down";
}

onMounted(async () => {
  try {
    const user = await authStore.refreshSession();
    if (!user?.permissions.includes("system-monitor:read")) {
      await navigateTo("/");
      return;
    }
  } catch {
    // 后续监控请求会展示具体的认证或网络错误。
  }

  await loadSnapshot();
  refreshTimer = setInterval(loadSnapshot, 30_000);
});

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<style scoped>
.monitor-page {
  min-height: 100%;
  padding: 24px;
}

.page-heading {
  align-items: flex-end;
  display: flex;
  gap: 20px;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-heading__eyebrow {
  color: var(--primary);
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .14em;
  margin: 0 0 6px;
}

.page-heading h1 {
  color: var(--text);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0;
  margin: 0;
}

.page-heading__meta {
  color: var(--text-sub);
  font-size: 12px;
  margin: 5px 0 0;
}

.heading-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.live-indicator {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--text-sub);
  display: inline-flex;
  font-size: 12px;
  gap: 7px;
  height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.live-indicator i, .health-badge i {
  background: #059669;
  border-radius: 50%;
  height: 6px;
  width: 6px;
}

.refresh-button {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  height: 32px;
  justify-content: center;
  width: 32px;
}

.refresh-button:hover:not(:disabled) {
  background: var(--primary-soft);
  color: var(--primary);
}

.refresh-button:disabled {
  cursor: wait;
  opacity: .65;
}

.refresh-button .material-icons-outlined {
  font-size: 18px;
}

.monitor-error {
  align-items: center;
  background: color-mix(in srgb, var(--red) 10%, var(--card));
  border: 1px solid color-mix(in srgb, var(--red) 25%, var(--card));
  border-radius: 6px;
  color: var(--red);
  display: flex;
  font-size: 13px;
  gap: 8px;
  margin: 0 0 16px;
  padding: 10px 12px;
}

.monitor-error .material-icons-outlined {
  font-size: 18px;
}

.monitor-loading, .empty-state {
  color: var(--text-mute);
  font-size: 13px;
  padding: 48px 16px;
  text-align: center;
}

.overview-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.overview-metric {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 7px;
  min-width: 0;
  padding: 16px;
  position: relative;
}

.overview-metric::before {
  background: var(--primary);
  content: "";
  height: 2px;
  left: 16px;
  position: absolute;
  top: 0;
  width: 28px;
}

.overview-metric--memory::before {
  background: #0891b2;
}

.overview-metric--heap::before {
  background: #7c3aed;
}

.overview-metric--gc::before {
  background: #d97706;
}

.overview-metric__label {
  align-items: center;
  color: var(--text-sub);
  display: flex;
  font-size: 12px;
  gap: 6px;
}

.overview-metric__label .material-icons-outlined {
  color: var(--primary);
  font-size: 17px;
}

.overview-metric--memory .material-icons-outlined {
  color: #0891b2;
}

.overview-metric--heap .material-icons-outlined {
  color: #7c3aed;
}

.overview-metric--gc .material-icons-outlined {
  color: #d97706;
}

.overview-metric > strong {
  color: var(--text);
  display: block;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  margin: 12px 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-metric > small {
  color: var(--text-mute);
  display: block;
  font-size: 11px;
  margin-top: 7px;
}

.metric-track, .usage-rail {
  background: var(--bg);
  border-radius: 2px;
  height: 5px;
  overflow: hidden;
  width: 100%;
}

.metric-track span, .usage-rail span {
  background: var(--primary);
  border-radius: inherit;
  display: block;
  height: 100%;
  min-width: 2px;
  transition: width .35s ease;
}

.monitor-grid {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}

.monitor-grid--primary {
  grid-template-columns: minmax(0, 1.08fr) minmax(0, .92fr);
}

.monitor-grid--secondary {
  grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr);
}

.monitor-panel {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 7px;
  min-width: 0;
  overflow: hidden;
}

.panel-heading {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  min-height: 50px;
  padding: 0 17px;
}

.panel-heading > div {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 0;
}

.panel-heading h2 {
  color: var(--text);
  font-size: 14px;
  font-weight: 650;
  margin: 0;
}

.panel-heading__icon {
  color: var(--primary);
  font-size: 18px;
}

.panel-heading__icon--violet {
  color: #7c3aed;
}

.panel-heading__icon--amber {
  color: #d97706;
}

.panel-heading__icon--green {
  color: #059669;
}

.panel-heading__icon--red {
  color: var(--red);
}

.panel-status {
  color: var(--text-mute);
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 11px;
  white-space: nowrap;
}

.key-value-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.key-value-grid > div {
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px 17px;
}

.key-value-grid > div:nth-last-child(-n+2) {
  border-bottom: 0;
}

.key-value-grid dt, .service-details dt {
  color: var(--text-mute);
  font-size: 11px;
}

.key-value-grid dd, .service-details dd {
  color: var(--text);
  font-size: 12px;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-rule {
  background: var(--border);
  height: 1px;
}

.subsection-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 13px 17px 7px;
}

.subsection-heading h3 {
  color: var(--text);
  font-size: 12px;
  font-weight: 600;
  margin: 0;
}

.subsection-heading span {
  color: var(--text-mute);
  font-size: 11px;
}

.disk-list {
  padding: 0 17px 8px;
}

.disk-row {
  align-items: center;
  border-top: 1px solid var(--border);
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(110px, .6fr) minmax(180px, 1fr) minmax(110px, .55fr);
  min-height: 58px;
}

.disk-row__name {
  align-items: center;
  display: flex;
  gap: 6px;
  min-width: 0;
}

.disk-row__name .material-icons-outlined {
  color: var(--text-mute);
  font-size: 17px;
}

.disk-row__name strong {
  color: var(--text);
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 12px;
}

.disk-row__name small {
  color: var(--text-mute);
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.disk-row__usage small {
  color: var(--text-mute);
  display: block;
  font-size: 10px;
  margin-top: 4px;
}

.disk-row__free {
  color: var(--text-sub);
  font-size: 11px;
  font-weight: 500;
  text-align: right;
  white-space: nowrap;
}

.memory-usage-list {
  display: grid;
  gap: 18px;
  padding: 16px 17px;
}

.memory-usage__label {
  align-items: baseline;
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.memory-usage__label span {
  color: var(--text-sub);
  font-size: 12px;
}

.memory-usage__label strong {
  color: var(--text);
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.usage-rail {
  height: 7px;
}

.memory-usage small {
  color: var(--text-mute);
  display: block;
  font-size: 10px;
  margin-top: 5px;
}

.memory-usage--non-heap .usage-rail span {
  background: #7c3aed;
}

.key-value-grid--jvm > div:nth-last-child(-n+2) {
  border-bottom: 1px solid var(--border);
}

.key-value-grid--jvm > div:last-child {
  border-bottom: 0;
}

.gc-list {
  padding: 0 17px;
}

.gc-row {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) minmax(60px, .32fr) minmax(115px, .6fr);
  min-height: 60px;
}

.gc-row:last-child {
  border-bottom: 0;
}

.gc-row strong {
  color: var(--text);
  display: block;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gc-row small {
  color: var(--text-mute);
  display: block;
  font-size: 10px;
  margin-top: 3px;
}

.gc-row b {
  color: #d97706;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
}

.health-badge {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 11px;
  gap: 5px;
  padding: 3px 7px;
  white-space: nowrap;
}

.health-badge--up {
  background: color-mix(in srgb, #059669 10%, var(--card));
  color: #047857;
}

.health-badge--down {
  background: color-mix(in srgb, var(--red) 10%, var(--card));
  color: var(--red);
}

.health-badge--down i {
  background: var(--red);
}

.service-details {
  display: grid;
  gap: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.service-details > div {
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px 17px;
}

.service-details > div:last-child {
  border-left: 1px solid var(--border);
}

.service-details__mono {
  font-family: Consolas, "SFMono-Regular", monospace;
}

.service-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}

.service-metrics > div {
  border-right: 1px solid var(--border);
  padding: 13px 17px;
}

.service-metrics > div:last-child {
  border-right: 0;
}

.service-metrics span, .redis-metrics span {
  color: var(--text-mute);
  display: block;
  font-size: 11px;
}

.service-metrics strong {
  color: var(--text);
  display: block;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  margin-top: 5px;
}

.service-unavailable {
  color: var(--text-mute);
  font-size: 13px;
  margin: 0;
  padding: 32px 17px;
  text-align: center;
}

.redis-layout {
  display: grid;
  grid-template-columns: minmax(300px, .85fr) minmax(0, 1.65fr);
}

.redis-details {
  border-right: 1px solid var(--border);
  grid-template-columns: 1fr;
}

.redis-details > div:last-child {
  border-left: 0;
}

.redis-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.redis-metrics > div {
  border-bottom: 1px solid var(--border);
  border-right: 1px solid var(--border);
  min-width: 0;
  padding: 14px 17px;
}

.redis-metrics > div:nth-child(3n) {
  border-right: 0;
}

.redis-metrics > div:nth-last-child(-n+3) {
  border-bottom: 0;
}

.redis-metrics strong {
  color: var(--text);
  display: block;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.redis-metrics small {
  color: var(--text-mute);
  display: block;
  font-size: 10px;
  margin-top: 4px;
}

.spinning {
  animation: spin .8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }

  .metric-track span, .usage-rail span {
    transition: none;
  }
}

@media (max-width: 1120px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .monitor-grid--primary, .monitor-grid--secondary {
    grid-template-columns: 1fr;
  }

  .redis-layout {
    grid-template-columns: 1fr;
  }

  .redis-details {
    border-bottom: 1px solid var(--border);
    border-right: 0;
    grid-template-columns: repeat(2, 1fr);
  }

  .redis-details > div:nth-child(even) {
    border-left: 1px solid var(--border);
  }
}

@media (max-width: 680px) {
  .monitor-page {
    padding: 16px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .heading-actions {
    width: 100%;
  }

  .live-indicator {
    flex: 1;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .key-value-grid, .service-details {
    grid-template-columns: 1fr;
  }

  .key-value-grid > div:nth-last-child(2), .key-value-grid > div:nth-last-child(1) {
    border-bottom: 1px solid var(--border);
  }

  .key-value-grid > div:last-child {
    border-bottom: 0;
  }

  .disk-row {
    align-items: start;
    grid-template-columns: 1fr;
    gap: 6px;
    padding: 10px 0;
  }

  .disk-row__free {
    text-align: left;
  }

  .service-details > div:last-child {
    border-left: 0;
  }

  .service-metrics {
    grid-template-columns: 1fr;
  }

  .service-metrics > div {
    border-bottom: 1px solid var(--border);
    border-right: 0;
  }

  .service-metrics > div:last-child {
    border-bottom: 0;
  }

  .gc-row {
    grid-template-columns: 1fr auto;
    padding: 10px 0;
  }

  .gc-row > div:last-child {
    grid-column: 1 / -1;
  }

  .gc-row b {
    grid-column: 2;
    grid-row: 1;
  }

  .redis-details, .redis-metrics {
    grid-template-columns: 1fr;
  }

  .redis-details > div:nth-child(even), .redis-metrics > div {
    border-left: 0;
    border-right: 0;
  }

  .redis-metrics > div:nth-last-child(-n+3) {
    border-bottom: 1px solid var(--border);
  }

  .redis-metrics > div:last-child {
    border-bottom: 0;
  }
}
</style>
