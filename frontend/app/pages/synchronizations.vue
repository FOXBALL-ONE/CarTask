<template>
  <section class="sync-page">
    <header class="page-heading">
      <div>
        <p class="page-heading__eyebrow">KEYTOP DATA BRIDGE</p>
        <h1>数据同步</h1>
        <p>从外部停车平台拉取基础资料，并安全写入本地业务字典。</p>
      </div>
      <span class="page-heading__mode"><span />手动执行</span>
    </header>

    <article class="sync-card" :class="`sync-card--${syncState}`">
      <div class="sync-card__main">
        <header class="sync-card__header">
          <div class="sync-card__icon"><span class="material-icons-outlined">map</span></div>
          <div>
            <div class="sync-card__title-row">
              <h2>停车区域</h2>
              <span class="state-badge" aria-live="polite">{{ stateLabel }}</span>
            </div>
            <p>按科拓区域编码匹配本地数据；新增缺失区域，并更新已有区域的名称和顺序。</p>
          </div>
        </header>

        <div class="sync-route" :class="{ 'sync-route--running': syncState === 'running' }">
          <div class="endpoint">
            <span class="endpoint__mark endpoint__mark--remote"><span class="material-icons-outlined">cloud</span></span>
            <span><small>数据来源</small><strong>科拓开放平台</strong></span>
          </div>
          <div class="conduit" aria-hidden="true">
            <span class="conduit__line" />
            <span class="conduit__packet" />
            <span class="material-icons-outlined conduit__arrow">arrow_forward</span>
          </div>
          <div class="endpoint endpoint--target">
            <span class="endpoint__mark"><span class="material-icons-outlined">dns</span></span>
            <span><small>写入目标</small><strong>本地区域字典</strong></span>
          </div>
        </div>

        <div v-if="syncState === 'error'" class="feedback feedback--error" role="alert">
          <span class="material-icons-outlined">error_outline</span>
          <div><strong>同步未完成</strong><p>{{ errorMessage }}</p></div>
        </div>
        <div v-else-if="syncState === 'success'" class="feedback feedback--success" role="status">
          <span class="material-icons-outlined">check_circle_outline</span>
          <div><strong>同步完成</strong><p>{{ resultSummary }}</p></div>
        </div>
      </div>

      <aside class="sync-card__action">
        <template v-if="!confirming">
          <p class="action-label">本次操作</p>
          <p class="action-copy">立即向科拓发起一次请求。自动同步计划不会受到影响。</p>
          <button class="sync-button" type="button" :disabled="syncState === 'running'" @click="confirming = true">
            <span class="material-icons-outlined" :class="{ spinning: syncState === 'running' }">{{ syncState === 'running' ? 'sync' : 'sync_alt' }}</span>
            {{ syncState === "running" ? "正在同步..." : "立即同步" }}
          </button>
        </template>
        <template v-else>
          <p class="action-label">确认执行</p>
          <p class="action-copy">同步会新增或更新本地区域，但不会删除现有区域。</p>
          <div class="confirm-actions">
            <button class="cancel-button" type="button" @click="confirming = false">取消</button>
            <button class="sync-button" type="button" @click="runSynchronization">
              <span class="material-icons-outlined">play_arrow</span>执行同步
            </button>
          </div>
        </template>
      </aside>
    </article>

    <article class="sync-card sync-card--records" :class="`sync-card--${accessRecordSyncState}`">
      <div class="sync-card__main">
        <header class="sync-card__header">
          <div class="sync-card__icon"><span class="material-icons-outlined">directions_car</span></div>
          <div>
            <div class="sync-card__title-row">
              <h2>车辆进出记录</h2>
              <span class="state-badge" aria-live="polite">{{ accessRecordStateLabel }}</span>
            </div>
            <p>以最近一次成功快照为起点增量拉取车辆进出记录，并将抓拍图片保存到本地文件库。</p>
          </div>
        </header>

        <div class="sync-route" :class="{ 'sync-route--running': accessRecordSyncState === 'running' || accessRecordPreviewing }">
          <div class="endpoint">
            <span class="endpoint__mark endpoint__mark--remote"><span class="material-icons-outlined">photo_camera</span></span>
            <span><small>数据来源</small><strong>科拓进出流水</strong></span>
          </div>
          <div class="conduit" aria-hidden="true">
            <span class="conduit__line" />
            <span class="conduit__packet" />
            <span class="material-icons-outlined conduit__arrow">arrow_forward</span>
          </div>
          <div class="endpoint endpoint--target">
            <span class="endpoint__mark"><span class="material-icons-outlined">folder_copy</span></span>
            <span><small>写入目标</small><strong>本地流水与图片库</strong></span>
          </div>
        </div>

        <div v-if="accessRecordSyncState === 'error'" class="feedback feedback--error" role="alert">
          <span class="material-icons-outlined">error_outline</span>
          <div><strong>同步未完成</strong><p>{{ accessRecordErrorMessage }}</p></div>
        </div>
        <div v-else-if="accessRecordSyncState === 'success'" class="feedback feedback--success" role="status">
          <span class="material-icons-outlined">check_circle_outline</span>
          <div><strong>同步完成</strong><p>{{ accessRecordResultSummary }}</p></div>
        </div>
        <div v-else-if="accessRecordPreview && accessRecordSyncState === 'idle'" class="feedback feedback--preview" role="status">
          <span class="material-icons-outlined">fact_check</span>
          <div><strong>同步预检完成</strong><p>{{ accessRecordPreviewSummary }}</p></div>
        </div>
      </div>

      <aside class="sync-card__action">
        <template v-if="accessRecordPreviewing || accessRecordSyncState === 'running'">
          <p class="action-label">{{ accessRecordPreviewing ? "正在预检" : "正在同步" }}</p>
          <p class="action-copy">{{ accessRecordPreviewing ? "正在读取科拓待同步记录数，尚未写入本地数据。" : "正在保存流水并下载可用的抓拍图片。" }}</p>
          <button class="sync-button" type="button" disabled>
            <span class="material-icons-outlined spinning">sync</span>{{ accessRecordPreviewing ? "正在获取..." : "正在同步..." }}
          </button>
        </template>
        <template v-else-if="!accessRecordPreview">
          <p class="action-label">同步预检</p>
          <p class="action-copy">先读取本次时间范围与待同步数量，确认后才会写入本地流水和图片库。</p>
          <button class="sync-button" type="button" @click="previewAccessRecordSynchronization">
            <span class="material-icons-outlined">query_stats</span>获取同步情况
          </button>
        </template>
        <template v-else>
          <p class="action-label">{{ accessRecordPreview.initial_sync ? "确认初始化同步" : "确认增量同步" }}</p>
          <p class="action-copy">{{ accessRecordPreview.pending_count === null ? "科拓未返回总数；确认后将按预检时间范围同步。" : `预检发现 ${accessRecordPreview.pending_count} 条记录，确认后开始同步。` }}</p>
          <div class="confirm-actions">
            <button class="cancel-button" type="button" @click="resetAccessRecordPreview">取消</button>
            <button class="sync-button" type="button" @click="runAccessRecordSynchronization">
              <span class="material-icons-outlined">play_arrow</span>执行同步
            </button>
          </div>
        </template>
      </aside>
    </article>

    <article class="sync-card sync-card--accounts" :class="`sync-card--${accountSyncState}`">
      <div class="sync-card__main">
        <header class="sync-card__header">
          <div class="sync-card__icon"><span class="material-icons-outlined">person_add</span></div>
          <div>
            <div class="sync-card__title-row">
              <h2>车辆业主账号</h2>
              <span class="state-badge" aria-live="polite">{{ accountStateLabel }}</span>
            </div>
            <p>以近 30 天有进出记录的车牌为依据（自动去重），经车牌档案关联车主信息，为尚未注册的业主自动创建平台登录账号。</p>
          </div>
        </header>

        <div class="sync-route" :class="{ 'sync-route--running': accountSyncState === 'running' }">
          <div class="endpoint">
            <span class="endpoint__mark endpoint__mark--remote"><span class="material-icons-outlined">directions_car</span></span>
            <span><small>数据来源</small><strong>车辆主档业主信息</strong></span>
          </div>
          <div class="conduit" aria-hidden="true">
            <span class="conduit__line" />
            <span class="conduit__packet" />
            <span class="material-icons-outlined conduit__arrow">arrow_forward</span>
          </div>
          <div class="endpoint endpoint--target">
            <span class="endpoint__mark"><span class="material-icons-outlined">badge</span></span>
            <span><small>写入目标</small><strong>平台用户账号</strong></span>
          </div>
        </div>

        <div v-if="accountSyncState === 'error'" class="feedback feedback--error" role="alert">
          <span class="material-icons-outlined">error_outline</span>
          <div><strong>生成未完成</strong><p>{{ accountErrorMessage }}</p></div>
        </div>
        <div v-else-if="accountSyncState === 'success'" class="feedback feedback--success" role="status">
          <span class="material-icons-outlined">check_circle_outline</span>
          <div><strong>生成完成</strong><p>{{ accountResultSummary }}</p></div>
        </div>
      </div>

      <aside class="sync-card__action">
        <template v-if="!accountConfirming">
          <p class="action-label">本次操作</p>
          <p class="action-copy">为缺少账号的业主按手机号创建登录账号，初始密码统一发放。已有账号不会受到影响。</p>
          <button class="sync-button" type="button" :disabled="accountSyncState === 'running'" @click="accountConfirming = true">
            <span class="material-icons-outlined" :class="{ spinning: accountSyncState === 'running' }">{{ accountSyncState === 'running' ? 'sync' : 'person_add_alt' }}</span>
            {{ accountSyncState === "running" ? "正在生成..." : "立即生成" }}
          </button>
        </template>
        <template v-else>
          <p class="action-label">确认执行</p>
          <p class="action-copy">将以业主手机号作为登录名创建账号；已存在的账号自动跳过，不会重复创建。同一业主的多个车牌只处理一次。</p>
          <div class="confirm-actions">
            <button class="cancel-button" type="button" @click="accountConfirming = false">取消</button>
            <button class="sync-button" type="button" @click="runAccountGeneration">
              <span class="material-icons-outlined">play_arrow</span>执行生成
            </button>
          </div>
        </template>
      </aside>
    </article>

    <section class="result-panel result-panel--records" aria-live="polite">
      <header class="result-panel__header">
        <div>
          <p class="result-panel__label">最近一次进出记录同步</p>
          <p class="result-panel__time">{{ accessRecordResult ? formatDateTime(accessRecordResult.executed_at) : "执行后将在这里显示本次统计" }}</p>
        </div>
        <span v-if="accessRecordResult" class="result-panel__total">新游标 {{ formatDateTime(accessRecordResult.cursor_time) }}</span>
      </header>
      <div class="metric-grid">
        <div class="metric"><span>处理流水</span><strong>{{ accessRecordResult?.processed_count ?? "—" }}</strong></div>
        <div class="metric metric--photo"><span>图片落地</span><strong>{{ accessRecordResult?.local_photo_count ?? "—" }}</strong></div>
        <div class="metric metric--retry"><span>待重试图片</span><strong>{{ accessRecordResult?.failed_photo_count ?? "—" }}</strong></div>
      </div>
    </section>

    <section class="result-panel result-panel--accounts" aria-live="polite">
      <header class="result-panel__header">
        <div>
          <p class="result-panel__label">最近一次账号生成</p>
          <p class="result-panel__time">{{ accountResult ? formatDateTime(accountResult.executed_at) : "执行后将在这里显示本次统计" }}</p>
        </div>
        <span v-if="accountResult" class="result-panel__total">新建 {{ accountResult.created_count }} 个账号</span>
      </header>
      <div class="metric-grid">
        <div class="metric"><span>新建账号</span><strong>{{ accountResult?.created_count ?? "—" }}</strong></div>
        <div class="metric metric--quiet"><span>跳过</span><strong>{{ accountResult?.skipped_count ?? "—" }}</strong></div>
        <div class="metric metric--retry"><span>失败</span><strong>{{ accountResult?.failed_count ?? "—" }}</strong></div>
      </div>
    </section>

    <section class="result-panel" aria-live="polite">
      <header class="result-panel__header">
        <div>
          <p class="result-panel__label">最近一次手动结果</p>
          <p class="result-panel__time">{{ result ? formatDateTime(result.executed_at) : "执行后将在这里显示本次统计" }}</p>
        </div>
        <span v-if="result" class="result-panel__total">共接收 {{ result.received_count }} 个区域</span>
      </header>
      <div class="metric-grid">
        <div class="metric"><span>接收</span><strong>{{ result?.received_count ?? "—" }}</strong></div>
        <div class="metric metric--created"><span>新增</span><strong>{{ result?.created_count ?? "—" }}</strong></div>
        <div class="metric metric--updated"><span>更新</span><strong>{{ result?.updated_count ?? "—" }}</strong></div>
        <div class="metric metric--quiet"><span>未变化</span><strong>{{ result?.unchanged_count ?? "—" }}</strong></div>
      </div>
    </section>

    <section class="sync-note">
      <span class="material-icons-outlined">info</span>
      <div><strong>同步规则</strong><p>科拓区域编码是幂等匹配依据；接口返回为空或失败时不会覆盖本地区域，科拓端已删除的区域也不会被自动删除。</p></div>
    </section>
    <section class="sync-note sync-note--records">
      <span class="material-icons-outlined">history</span>
      <div><strong>增量规则</strong><p>仅从上一次成功快照继续读取。图片下载失败会保留源地址并在后续同步中自动重试，不影响流水检查点推进。</p></div>
    </section>
    <section class="sync-note sync-note--accounts">
      <span class="material-icons-outlined">key</span>
      <div><strong>账号规则</strong><p>仅处理近 30 天有进出记录的车牌，车牌去除间隔符后匹配有效车牌档案并关联在营车主。业主手机号即登录名，初始密码统一发放，请提醒业主及时修改。缺少车牌档案、车主已停用或手机号为空的记录不会处理，补全资料后可再次执行。</p></div>
    </section>
  </section>
</template>

<script setup lang="ts">
interface ParkingAreaSyncResult {
  received_count: number;
  created_count: number;
  updated_count: number;
  unchanged_count: number;
  executed_at: string;
}

interface AccessRecordSyncResult {
  processed_count: number;
  local_photo_count: number;
  failed_photo_count: number;
  start_time: string;
  cursor_time: string;
  executed_at: string;
}

interface AccessRecordSyncPreview {
  initial_sync: boolean;
  pending_count: number | null;
  start_time: string;
  end_time: string;
  checkpoint_time: string | null;
  checked_at: string;
}

interface AccountGenerateResult {
  created_count: number;
  skipped_count: number;
  failed_count: number;
  executed_at: string;
}

type SyncState = "idle" | "running" | "success" | "error";

const http = useHttp();
const syncState = ref<SyncState>("idle");
const confirming = ref(false);
const errorMessage = ref("");
const result = ref<ParkingAreaSyncResult | null>(null);
const accessRecordSyncState = ref<SyncState>("idle");
const accessRecordPreviewing = ref(false);
const accessRecordErrorMessage = ref("");
const accessRecordResult = ref<AccessRecordSyncResult | null>(null);
const accessRecordPreview = ref<AccessRecordSyncPreview | null>(null);
const accountSyncState = ref<SyncState>("idle");
const accountConfirming = ref(false);
const accountErrorMessage = ref("");
const accountResult = ref<AccountGenerateResult | null>(null);
const stateLabel = computed(() => ({
  idle: "待执行",
  running: "同步中",
  success: "已完成",
  error: "执行失败",
})[syncState.value]);
const resultSummary = computed(() => {
  if (!result.value) return "本地数据未发生变化。";
  if (result.value.received_count === 0) return "科拓未返回停车区域，本地数据未改变。";
  return `接收 ${result.value.received_count} 个区域，新增 ${result.value.created_count} 个，更新 ${result.value.updated_count} 个。`;
});
const accessRecordStateLabel = computed(() => {
  if (accessRecordPreviewing.value) return "预检中";
  if (accessRecordPreview.value && accessRecordSyncState.value === "idle") return "待确认";
  return {
    idle: "待执行",
    running: "同步中",
    success: "已完成",
    error: "执行失败",
  }[accessRecordSyncState.value];
});
const accessRecordPreviewSummary = computed(() => {
  if (!accessRecordPreview.value) return "";
  const preview = accessRecordPreview.value;
  const syncType = preview.initial_sync ? "初始化同步" : "增量同步";
  const count = preview.pending_count === null ? "科拓未返回待同步总数" : `待同步 ${preview.pending_count} 条记录`;
  return `${syncType}，时间范围 ${formatDateTime(preview.start_time)} 至 ${formatDateTime(preview.end_time)}，${count}。`;
});
const accessRecordResultSummary = computed(() => {
  if (!accessRecordResult.value) return "本地数据未发生变化。";
  const { processed_count, local_photo_count, failed_photo_count } = accessRecordResult.value;
  if (processed_count === 0) return "没有发现上次快照之后的新进出流水。";
  if (failed_photo_count === 0) return `处理 ${processed_count} 条流水，${local_photo_count} 张抓拍图片已保存到本地。`;
  return `处理 ${processed_count} 条流水，${local_photo_count} 张图片已保存，${failed_photo_count} 张将在后续同步中重试。`;
});
const accountStateLabel = computed(() => ({
  idle: "待执行",
  running: "生成中",
  success: "已完成",
  error: "执行失败",
})[accountSyncState.value]);
const accountResultSummary = computed(() => {
  if (!accountResult.value) return "本地数据未发生变化。";
  const { created_count, skipped_count, failed_count } = accountResult.value;
  if (created_count === 0) return "没有需要新建账号的业主，全部已注册或资料不完整。";
  if (failed_count === 0) return `为 ${created_count} 位业主创建了登录账号，跳过 ${skipped_count} 位。`;
  return `为 ${created_count} 位业主创建了登录账号，跳过 ${skipped_count} 位，${failed_count} 位创建失败。`;
});

async function runSynchronization() {
  confirming.value = false;
  syncState.value = "running";
  errorMessage.value = "";
  try {
    result.value = await http.post<ParkingAreaSyncResult>("/synchronizations/parking-areas");
    syncState.value = "success";
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "无法连接同步服务，请检查科拓配置后重试。";
    syncState.value = "error";
  }
}

async function runAccessRecordSynchronization() {
  accessRecordPreview.value = null;
  accessRecordSyncState.value = "running";
  accessRecordErrorMessage.value = "";
  try {
    accessRecordResult.value = await http.post<AccessRecordSyncResult>("/synchronizations/access-records");
    accessRecordSyncState.value = "success";
  } catch (error) {
    accessRecordErrorMessage.value = (error as { statusMessage?: string }).statusMessage || "无法连接同步服务，请检查科拓配置后重试。";
    accessRecordSyncState.value = "error";
  }
}

async function runAccountGeneration() {
  accountConfirming.value = false;
  accountSyncState.value = "running";
  accountErrorMessage.value = "";
  try {
    accountResult.value = await http.post<AccountGenerateResult>("/synchronizations/accounts");
    accountSyncState.value = "success";
  } catch (error) {
    accountErrorMessage.value = (error as { statusMessage?: string }).statusMessage || "无法连接账号生成服务，请稍后重试。";
    accountSyncState.value = "error";
  }
}

async function previewAccessRecordSynchronization() {
  accessRecordPreviewing.value = true;
  accessRecordSyncState.value = "idle";
  accessRecordErrorMessage.value = "";
  accessRecordPreview.value = null;
  try {
    accessRecordPreview.value = await http.get<AccessRecordSyncPreview>("/synchronizations/access-records/preview");
  } catch (error) {
    accessRecordErrorMessage.value = (error as { statusMessage?: string }).statusMessage || "无法获取同步情况，请检查科拓配置后重试。";
    accessRecordSyncState.value = "error";
  } finally {
    accessRecordPreviewing.value = false;
  }
}

function resetAccessRecordPreview() {
  accessRecordPreview.value = null;
  accessRecordSyncState.value = "idle";
  accessRecordErrorMessage.value = "";
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(date);
}
</script>

<style scoped>
.sync-page { min-height: 100%; padding: 24px; }
.page-heading { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 20px; }
.page-heading__eyebrow { color: var(--primary); font-family: Consolas, "SFMono-Regular", monospace; font-size: 10px; font-weight: 700; letter-spacing: .16em; margin: 0 0 6px; }
.page-heading h1 { color: var(--text); font-size: 20px; font-weight: 650; letter-spacing: -.02em; margin: 0; }
.page-heading > div > p:last-child { color: var(--text-sub); font-size: 13px; margin: 5px 0 0; }
.page-heading__mode { align-items: center; background: var(--card); border: 1px solid var(--border-strong); border-radius: 999px; color: var(--text-sub); display: inline-flex; font-size: 12px; gap: 7px; padding: 6px 10px; white-space: nowrap; }
.page-heading__mode span { background: var(--primary); border-radius: 50%; height: 6px; width: 6px; }
.sync-card { background: var(--card); border: 1px solid var(--border-strong); border-radius: 10px; display: grid; grid-template-columns: minmax(0, 1fr) 260px; overflow: hidden; }
.sync-card + .sync-card { margin-top: 16px; }
.sync-card__main { min-width: 0; padding: 24px; }
.sync-card__header { align-items: flex-start; display: flex; gap: 13px; }
.sync-card__icon { align-items: center; background: var(--primary-soft); border-radius: 9px; color: var(--primary); display: flex; flex: 0 0 40px; height: 40px; justify-content: center; }
.sync-card__icon .material-icons-outlined { font-size: 21px; }
.sync-card__title-row { align-items: center; display: flex; flex-wrap: wrap; gap: 9px; }
.sync-card h2 { color: var(--text); font-size: 16px; font-weight: 650; margin: 0; }
.sync-card__header p { color: var(--text-sub); line-height: 1.65; margin: 4px 0 0; }
.state-badge { background: var(--bg); border: 1px solid var(--border); border-radius: 4px; color: var(--text-sub); font-size: 11px; padding: 2px 7px; }
.sync-card--running .state-badge { background: var(--primary-soft); color: var(--primary); }
.sync-card--success .state-badge { background: color-mix(in srgb, #059669 12%, var(--card)); color: #059669; }
.sync-card--error .state-badge { background: color-mix(in srgb, var(--red) 10%, var(--card)); color: var(--red); }
.sync-card--records .sync-card__icon { background: color-mix(in srgb, #059669 12%, var(--card)); color: #047857; }
.sync-card--records .endpoint__mark { color: #047857; }
.sync-card--records .endpoint__mark--remote { background: #047857; border-color: #047857; color: #fff; }
.sync-card--records .conduit__packet { background: #059669; border-color: color-mix(in srgb, #059669 20%, var(--card)); }
.sync-card--records .sync-route--running .conduit__line { background: color-mix(in srgb, #059669 25%, var(--card)); }
.sync-card--accounts .sync-card__icon { background: color-mix(in srgb, #d97706 12%, var(--card)); color: #b45309; }
.sync-card--accounts .endpoint__mark { color: #b45309; }
.sync-card--accounts .endpoint__mark--remote { background: #d97706; border-color: #d97706; color: #fff; }
.sync-card--accounts .conduit__packet { background: #d97706; border-color: color-mix(in srgb, #d97706 20%, var(--card)); }
.sync-card--accounts .sync-route--running .conduit__line { background: color-mix(in srgb, #d97706 25%, var(--card)); }
.sync-route { align-items: center; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; display: grid; gap: 16px; grid-template-columns: minmax(150px, 1fr) minmax(100px, .8fr) minmax(150px, 1fr); margin-top: 24px; padding: 18px; }
.endpoint { align-items: center; display: flex; gap: 10px; min-width: 0; }
.endpoint--target { justify-self: end; }
.endpoint__mark { align-items: center; background: var(--card); border: 1px solid var(--border-strong); border-radius: 50%; color: var(--primary); display: flex; flex: 0 0 36px; height: 36px; justify-content: center; }
.endpoint__mark--remote { background: var(--primary); border-color: var(--primary); color: #fff; }
.endpoint__mark .material-icons-outlined { font-size: 18px; }
.endpoint span:last-child { display: grid; min-width: 0; }
.endpoint small { color: var(--text-mute); font-size: 10px; margin-bottom: 2px; }
.endpoint strong { color: var(--text); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conduit { align-items: center; display: flex; height: 20px; position: relative; }
.conduit__line { background: var(--border-strong); height: 1px; width: 100%; }
.conduit__arrow { color: var(--text-mute); font-size: 17px; margin-left: -5px; }
.conduit__packet { background: var(--primary); border: 3px solid var(--primary-soft); border-radius: 50%; height: 9px; left: 0; opacity: 0; position: absolute; width: 9px; }
.sync-route--running .conduit__line { background: var(--primary-soft); }
.sync-route--running .conduit__packet { animation: data-flow 1.25s ease-in-out infinite; opacity: 1; }
.feedback { align-items: flex-start; border-radius: 7px; display: flex; gap: 9px; margin-top: 16px; padding: 11px 13px; }
.feedback .material-icons-outlined { font-size: 19px; }
.feedback strong { color: var(--text); font-size: 12px; }
.feedback p { color: var(--text-sub); line-height: 1.5; margin: 2px 0 0; }
.feedback--success { background: color-mix(in srgb, #059669 10%, var(--card)); }.feedback--success .material-icons-outlined { color: #059669; }
.feedback--error { background: color-mix(in srgb, var(--red) 9%, var(--card)); }.feedback--error .material-icons-outlined { color: var(--red); }
.feedback--preview { background: color-mix(in srgb, var(--primary) 9%, var(--card)); }.feedback--preview .material-icons-outlined { color: var(--primary); }
.sync-card__action { background: color-mix(in srgb, var(--primary-soft) 55%, var(--card)); border-left: 1px solid var(--border); display: flex; flex-direction: column; justify-content: center; padding: 24px; }
.action-label { color: var(--text); font-size: 12px; font-weight: 650; margin: 0; }
.action-copy { color: var(--text-sub); font-size: 12px; line-height: 1.65; margin: 7px 0 18px; }
.sync-button, .cancel-button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; font-size: 12px; gap: 6px; height: 34px; justify-content: center; padding: 0 13px; }
.sync-button { background: var(--primary); color: #fff; width: 100%; }.sync-button:hover { filter: brightness(.96); }.sync-button:disabled { cursor: wait; opacity: .65; }
.sync-button:focus-visible, .cancel-button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.sync-button .material-icons-outlined { font-size: 17px; }
.cancel-button { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }
.confirm-actions { display: grid; gap: 8px; grid-template-columns: .8fr 1.2fr; }
.result-panel { background: var(--card); border: 1px solid var(--border-strong); border-radius: 10px; margin-top: 16px; overflow: hidden; }
.result-panel__header { align-items: center; border-bottom: 1px solid var(--border); display: flex; gap: 16px; justify-content: space-between; padding: 15px 18px; }
.result-panel__label { color: var(--text); font-size: 12px; font-weight: 650; margin: 0; }.result-panel__time { color: var(--text-mute); font-size: 11px; margin: 3px 0 0; }
.result-panel__total { color: var(--text-sub); font-size: 11px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); }
.result-panel--records .metric-grid, .result-panel--accounts .metric-grid { grid-template-columns: repeat(3, 1fr); }
.metric { border-right: 1px solid var(--border); padding: 17px 18px; position: relative; }.metric:last-child { border-right: 0; }
.metric::before { background: var(--primary); content: ""; height: 2px; left: 18px; position: absolute; top: 0; width: 22px; }
.metric--created::before { background: #059669; }.metric--updated::before { background: #d97706; }.metric--quiet::before { background: var(--text-mute); }
.metric--photo::before { background: #059669; }.metric--retry::before { background: var(--red); }
.metric span { color: var(--text-sub); display: block; font-size: 11px; }
.metric strong { color: var(--text); display: block; font-family: Consolas, "SFMono-Regular", monospace; font-size: 23px; font-variant-numeric: tabular-nums; line-height: 1.2; margin-top: 5px; }
.sync-note { align-items: flex-start; color: var(--text-sub); display: flex; gap: 9px; margin-top: 14px; padding: 0 4px; }.sync-note > .material-icons-outlined { color: var(--primary); font-size: 18px; }.sync-note strong { color: var(--text); font-size: 12px; }.sync-note p { line-height: 1.65; margin: 2px 0 0; }
.sync-note--records > .material-icons-outlined { color: #047857; }
.sync-note--accounts > .material-icons-outlined { color: #b45309; }
.spinning { animation: spin .9s linear infinite; }
@keyframes data-flow { 0% { left: 0; opacity: 0; } 15%, 85% { opacity: 1; } 100% { left: calc(100% - 22px); opacity: 0; } }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .sync-route--running .conduit__packet, .spinning { animation: none; }.sync-route--running .conduit__packet { left: 50%; opacity: 1; } }
@media (max-width: 820px) { .sync-card { grid-template-columns: 1fr; }.sync-card__action { border-left: 0; border-top: 1px solid var(--border); }.sync-route { grid-template-columns: minmax(120px, 1fr) 70px minmax(120px, 1fr); } }
@media (max-width: 600px) { .sync-page { padding: 16px; }.page-heading { align-items: flex-start; flex-direction: column; gap: 12px; }.sync-card__main, .sync-card__action { padding: 18px; }.sync-route { align-items: stretch; grid-template-columns: 1fr; }.endpoint--target { justify-self: start; }.conduit { height: 32px; margin-left: 17px; width: 20px; }.conduit__line { height: 100%; width: 1px; }.conduit__arrow { bottom: -3px; left: -8px; margin: 0; position: absolute; transform: rotate(90deg); }.conduit__packet { left: -4px; top: 0; }.sync-route--running .conduit__packet { animation-name: data-flow-vertical; }.metric-grid { grid-template-columns: repeat(2, 1fr); }.metric:nth-child(2) { border-right: 0; }.metric:nth-child(-n+2) { border-bottom: 1px solid var(--border); }.result-panel--records .metric:nth-child(3), .result-panel--accounts .metric:nth-child(3) { border-right: 0; }.result-panel__header { align-items: flex-start; flex-direction: column; gap: 7px; } }
@media (max-width: 600px) and (prefers-reduced-motion: reduce) { .sync-route--running .conduit__packet { left: -4px; top: 50%; } }
@keyframes data-flow-vertical { 0% { opacity: 0; top: 0; } 15%, 85% { opacity: 1; } 100% { opacity: 0; top: calc(100% - 9px); } }
</style>
