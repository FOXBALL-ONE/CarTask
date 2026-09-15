<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">数据备份</h1>
        <p class="page__desc">导出数据库 SQL 脚本，并可一并打包系统中登记的全部附件</p>
      </div>
      <button :disabled="busy || !loaded || !canBackup" class="button button--primary page__action" type="button"
              @click="runBackup">
        <span class="material-icons-outlined">archive</span>{{
          busy ? "备份生成中..." : includeFiles ? "生成备份压缩包" : "生成 SQL 备份"
        }}
      </button>
    </header>

    <section class="overview">
      <article class="overview__item">
        <span class="material-icons-outlined">storage</span>
        <div>
          <div class="overview__label">数据库</div>
          <div class="overview__value">{{ summary ? `${summary.table_count} 张表` : "读取中..." }}</div>
          <div class="overview__hint">{{ summary?.database_product || "—" }}</div>
        </div>
      </article>
      <article class="overview__item">
        <span class="material-icons-outlined">perm_media</span>
        <div>
          <div class="overview__label">已登记附件</div>
          <div class="overview__value">{{ summary ? `${summary.file_count} 个` : "读取中..." }}</div>
          <div class="overview__hint">{{ summary ? formatBytes(summary.file_bytes) : "—" }}</div>
        </div>
      </article>
      <article class="overview__item">
        <span class="material-icons-outlined">folder_open</span>
        <div>
          <div class="overview__label">附件存储目录</div>
          <div :title="summary?.storage_root || ''" class="overview__value overview__value--path">
            {{ summary?.storage_root || "—" }}
          </div>
          <div class="overview__hint">以服务器上的实际路径为准</div>
        </div>
      </article>
    </section>

    <section class="notice-panel">
      <div class="notice-panel__title">备份内容</div>
      <label class="option">
        <input v-model="includeFiles" :disabled="busy" type="checkbox">
        <span>
          <strong>同时打包已登记的文件</strong>
          <em>勾选后下载的是压缩包，内含 SQL 脚本（database.sql）、全部附件和附件清单（manifest.csv）；不勾选则只下载 SQL 脚本。</em>
        </span>
      </label>
      <p class="notice-panel__foot">
        SQL 备份始终会生成，压缩包只是在其基础上多带上附件，不会替代 SQL 文件。
      </p>
    </section>

    <section v-if="progress" aria-live="polite" class="progress-panel" role="status">
      <header class="progress-panel__head">
        <div>
          <div class="progress-panel__label">{{ progress.label }}</div>
          <p class="progress-panel__detail">{{ progressDetail }}</p>
        </div>
        <span class="progress-panel__percent">{{ progress.percent }}%</span>
      </header>
      <div class="progress-bar">
        <div :style="{ width: `${progress.percent}%` }" class="progress-bar__fill"/>
      </div>
      <p v-if="progress.message" class="progress-panel__message">{{ progress.message }}</p>
    </section>

    <section class="notice-panel">
      <div class="notice-panel__title"><span class="material-icons-outlined">info</span>备份说明</div>
      <ul>
        <li>SQL 脚本包含当前 schema 下全部业务表的结构与数据（系统表除外）。恢复时请先启动应用让表结构就绪，再用 <code>psql
          -f</code> 执行脚本。
        </li>
        <li>脚本不会做任何清理，请确认目标库为空或已确认可以覆盖同主键数据后再执行。</li>
        <li>压缩包里的附件按相对路径存放；元数据仍在、物理文件已丢失的条目会在清单里标成 <code>MISSING</code>，不会静默少一个。
        </li>
        <li>生成期间系统进入静默：除本页与健康检查外的请求都会返回
          503，定时同步跳过本次执行，已经在跑的同步会让备份等它结束再开始。这是为了让导出的数据停在同一时刻，而不是半个批次。
        </li>
        <li>
          产物只落在服务器临时目录里，下载写出后立即删除，服务器上不留档。备份包含账号口令散列与生物特征照片，请妥善保管下载到的文件。
        </li>
        <li>同一时刻只允许执行一次备份，重复点击会提示已有任务在执行。</li>
      </ul>
    </section>

    <p v-if="message" :class="{ error: feedbackType === 'error' }" class="feedback" role="status">{{ message }}</p>
  </section>
</template>

<script lang="ts" setup>
interface BackupSummary {
  database_product: string;
  table_count: number;
  file_count: number;
  file_bytes: number;
  storage_root: string;
}

interface BackupProgress {
  phase: "IDLE" | "COUNTING" | "DUMPING" | "ARCHIVING" | "FINISHED" | "FAILED";
  label: string;
  percent: number;
  tables_done: number;
  tables_total: number;
  rows_done: number;
  rows_total: number;
  files_done: number;
  files_total: number;
  message: string | null;
}

const {can} = usePermission();
const runtimeConfig = useRuntimeConfig();
const token = useCookie<string | null>("cartask_auth_token");
const canBackup = computed(() => can("backup:manage"));
const includeFiles = ref(false);
const busy = ref(false);
const loaded = ref(false);
const summary = ref<BackupSummary | null>(null);
const progress = ref<BackupProgress | null>(null);
const message = ref("");
const feedbackType = ref<"success" | "error">("success");
let progressTimer: ReturnType<typeof setInterval> | undefined;

/** 进度明细按阶段换口径：统计看表数、导出看行数、打包看附件数。 */
const progressDetail = computed(() => {
  const item = progress.value;
  if (!item) return "";
  switch (item.phase) {
    case "COUNTING":
      return `已统计 ${item.tables_done} / ${item.tables_total} 张表`;
    case "DUMPING":
      return `表 ${item.tables_done} / ${item.tables_total} · 数据 ${item.rows_done} / ${item.rows_total} 行`;
    case "ARCHIVING":
      return `附件 ${item.files_done} / ${item.files_total} 个`;
    default:
      return "";
  }
});

function baseUrl() {
  return String(runtimeConfig.public.baseUrl || "http://127.0.0.1:8080/api").replace(/\/$/, "");
}

function authorization() {
  return token.value ? {Authorization: /^Bearer\s/i.test(token.value) ? token.value : `Bearer ${token.value}`} : {};
}

function formatBytes(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value >= 10 || index === 0 ? Math.round(value) : value.toFixed(1)} ${units[index]}`;
}

async function loadSummary() {
  if (!canBackup.value) {
    loaded.value = true;
    showMessage("数据备份仅对超级管理员开放", "error");
    return;
  }
  try {
    const response = await fetch(`${baseUrl()}/backup/summary`, {headers: authorization()});
    const body = await response.json().catch(() => ({})) as { data?: BackupSummary; message?: string };
    if (!response.ok) throw new Error(body.message || `读取备份概览失败（${response.status}）`);
    summary.value = body.data ?? null;
  } catch (error) {
    showMessage(error instanceof Error ? error.message : "读取备份概览失败", "error");
  } finally {
    loaded.value = true;
  }
}

async function loadProgress() {
  try {
    const response = await fetch(`${baseUrl()}/backup/progress`, {headers: authorization()});
    if (!response.ok) return;
    const body = await response.json() as { data?: BackupProgress };
    progress.value = body.data ?? null;
  } catch {
    // 轮询失败不影响正在进行的备份；下一次轮询还会再试。
  }
}

function startProgressPolling() {
  stopProgressPolling();
  void loadProgress();
  progressTimer = setInterval(loadProgress, 1000);
}

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer);
    progressTimer = undefined;
  }
}

async function runBackup() {
  busy.value = true;
  message.value = "";
  startProgressPolling();
  try {
    const response = await fetch(`${baseUrl()}/backup/export?include_files=${includeFiles.value}`, {headers: authorization()});
    if (!response.ok) {
      // 生成阶段失败会走统一响应体，能拿到具体原因（例如已有备份在执行）；流式响应开始后就不会再返回 JSON 了。
      const body = await response.json().catch(() => ({})) as { message?: string };
      throw new Error(body.message || `备份失败（${response.status}）`);
    }
    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") || "";
    // 纯 ASCII 文件名时 Spring 只会给 filename="..."，没有 RFC 5987 的 filename*=UTF-8''；
    // 只认后者的话会退回默认名，丢掉备份文件名里的时间戳。
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1];
    const fallback = includeFiles.value ? "cartask-backup.zip" : "cartask-backup.sql";
    const filename = encoded
        ? decodeURIComponent(encoded)
        : plain
            ? decodeURIComponent(plain)
            : fallback;
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
    showMessage(
        includeFiles.value
            ? `备份完成，已下载 ${filename}（${formatBytes(blob.size)}）`
            : `SQL 备份完成，已下载 ${filename}（${formatBytes(blob.size)}）`,
    );
  } catch (error) {
    showMessage(error instanceof Error ? error.message : "备份失败", "error");
  } finally {
    busy.value = false;
    // 再拉一次拿最终状态（完成还是失败），否则进度条会停在最后一个中间值上。
    void loadProgress();
    stopProgressPolling();
  }
}

function showMessage(text: string, type: "success" | "error" = "success") {
  message.value = text;
  feedbackType.value = type;
}

onMounted(loadSummary);
onBeforeUnmount(stopProgressPolling);
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px;
}

.page__header {
  align-items: flex-start;
  display: flex;
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
  margin: 4px 0 0;
}

.overview {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.overview__item {
  align-items: flex-start;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  display: flex;
  gap: 14px;
  padding: 18px;
}

.overview__item > .material-icons-outlined {
  color: var(--primary);
  font-size: 22px;
}

.overview__label {
  color: var(--text-sub);
  font-size: 12px;
}

.overview__value {
  color: var(--text);
  font-size: 16px;
  font-weight: 600;
  margin-top: 2px;
}

.overview__value--path {
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.overview__hint {
  color: var(--text-mute);
  font-size: 12px;
  margin-top: 2px;
  overflow-wrap: anywhere;
}

.option {
  align-items: flex-start;
  cursor: pointer;
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.option input {
  cursor: pointer;
  flex-shrink: 0;
  height: 16px;
  margin-top: 3px;
  width: 16px;
}

.option strong {
  color: var(--text);
  display: block;
  font-weight: 600;
}

.option em {
  color: var(--text-sub);
  display: block;
  font-style: normal;
  line-height: 1.7;
  margin-top: 4px;
}

.notice-panel__foot {
  color: var(--text-sub);
  margin: 12px 0 0;
}

.notice-panel code {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  font-size: 12px;
  padding: 1px 5px;
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  gap: 5px;
  height: 34px;
  justify-content: center;
  padding: 0 13px;
  white-space: nowrap;
}

.button:disabled {
  cursor: not-allowed;
  opacity: .6;
}

.button--primary {
  background: var(--primary);
  color: #fff;
}

.button .material-icons-outlined {
  font-size: 17px;
}

.progress-panel {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  margin-top: 18px;
  padding: 16px 18px;
}

.progress-panel__head {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.progress-panel__label {
  color: var(--text);
  font-weight: 600;
}

.progress-panel__detail {
  color: var(--text-sub);
  font-size: 12px;
  margin: 4px 0 0;
}

.progress-panel__percent {
  color: var(--primary);
  font-size: 18px;
  font-weight: 650;
}

.progress-bar {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 999px;
  height: 8px;
  margin-top: 12px;
  overflow: hidden;
}

.progress-bar__fill {
  background: var(--primary);
  height: 100%;
  transition: width .3s ease;
}

.progress-panel__message {
  color: var(--red);
  font-size: 12px;
  margin: 10px 0 0;
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

.feedback {
  color: #059669;
  margin: 14px 0 0;
}

.feedback.error {
  color: var(--red);
}

@media (max-width: 900px) {
  .overview {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .page {
    padding: 16px;
  }

  .page__header {
    flex-direction: column;
    gap: 12px;
  }

  .page__action {
    width: 100%;
  }
}
</style>
