<template>
  <main class="report-page">
    <header class="page-header">
      <button aria-label="返回主页" class="back-button" type="button" @click="navigateTo('/wx')">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="m15 18-6-6 6-6"/>
        </svg>
      </button>
      <div class="page-heading">
        <h1>我的违规上报</h1>
        <p>查看已提交的举报和处理进度</p>
      </div>
      <NuxtLink class="create-button" to="/wx/violation/report">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        <span>新增举报</span>
      </NuxtLink>
    </header>

    <div v-if="submissionNotice" class="success-notice" role="status">
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="m5 12 4 4L19 6"/>
      </svg>
      举报已提交，我们会尽快核实处理。
      <button aria-label="关闭提示" type="button" @click="router.replace({path: route.path})">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="m6 6 12 12M18 6 6 18"/>
        </svg>
      </button>
    </div>

    <section aria-label="举报概况" class="report-overview">
      <div class="overview-copy">
        <span class="overview-label">我的举报</span>
        <strong>{{ total }}</strong>
        <span class="overview-unit">条记录</span>
      </div>
      <div aria-hidden="true" class="overview-icon">
        <svg viewBox="0 0 48 48">
          <path d="M12 8h17l8 8v24H12z"/>
          <path d="M28 8v9h9M18 25h13M18 31h13M18 19h4"/>
          <circle cx="34" cy="35" r="7"/>
          <path d="m31 35 2 2 4-4"/>
        </svg>
      </div>
      <div class="overview-foot"><span>{{ pendingCount }} 条等待处理</span><span
          class="overview-dot"/><span>记录仅本人可见</span></div>
    </section>

    <section aria-labelledby="records-title" class="records-section">
      <div class="section-heading">
        <div>
          <h2 id="records-title">上报记录</h2>
          <p>点击记录查看详情</p>
        </div>
        <button :disabled="loading" aria-label="刷新记录" class="refresh-button" type="button" @click="loadReports">
          <svg :class="{ 'is-spinning': loading }" aria-hidden="true" viewBox="0 0 24 24">
            <path d="M20 11a8 8 0 0 0-14.8-3L3 11M4 5v6h6M4 13a8 8 0 0 0 14.8 3L21 13m-1 6v-6h-6"/>
          </svg>
        </button>
      </div>

      <div aria-label="按状态筛选" class="status-filters">
        <button v-for="filter in filters" :key="filter.value" :aria-pressed="activeFilter === filter.value"
                :class="{ 'filter-chip--active': activeFilter === filter.value }" class="filter-chip" type="button"
                @click="activeFilter = filter.value">
          {{ filter.label }}<span v-if="filter.value === 'ALL'">{{ total }}</span>
        </button>
      </div>

      <div v-if="loading" class="state-card" role="status">
        <span class="loader"/>
        <strong>正在加载上报记录</strong>
        <span>请稍候</span>
      </div>
      <div v-else-if="errorMessage" class="state-card state-card--error" role="alert">
        <span class="state-icon">!</span>
        <strong>记录暂时无法加载</strong>
        <span>{{ errorMessage }}</span>
        <button class="retry-button" type="button" @click="loadReports">重新加载</button>
      </div>
      <div v-else-if="filteredReports.length === 0" class="state-card">
        <div aria-hidden="true" class="empty-icon">
          <svg viewBox="0 0 48 48">
            <path d="M12 7h17l8 8v26H12z"/>
            <path d="M28 7v9h9M18 24h13M18 30h8"/>
            <path d="m30 34 8 8m0-8-8 8"/>
          </svg>
        </div>
        <strong>{{ activeFilter === 'ALL' ? '还没有举报记录' : '没有符合状态的记录' }}</strong>
        <span>{{ activeFilter === 'ALL' ? '发现违规情况后，可在这里提交上报' : '试试切换其他状态查看' }}</span>
        <NuxtLink v-if="activeFilter === 'ALL'" class="empty-create" to="/wx/violation/report">去新增举报</NuxtLink>
      </div>
      <div v-else class="record-list">
        <button v-for="report in filteredReports" :key="report.id" class="record-card" type="button"
                @click="openDetails(report)">
          <div class="record-card__top">
            <span class="record-type"><span class="record-type__dot"/>{{ report.violation_type }}</span>
            <span :class="`status--${report.status.toLowerCase()}`" class="status-tag"><i/>{{
                statusLabel(report.status)
              }}</span>
          </div>
          <div class="record-vehicle">
            <span aria-hidden="true" class="vehicle-icon">
              <svg viewBox="0 0 24 24"><path d="m5 11 1.5-4h11L19 11l1.5 2v5h-17v-5L5 11Z"/><path
                  d="M6.5 11h11M7 15h.1M17 15h.1M8 18v2m8-2v2"/></svg>
            </span>
            <strong>{{ report.vehicle_number }}</strong>
            <span aria-hidden="true" class="record-arrow"><svg viewBox="0 0 24 24"><path
                d="m9 18 6-6-6-6"/></svg></span>
          </div>
          <p class="record-description">{{ report.description }}</p>
          <div class="record-card__bottom">
            <span class="record-time"><svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path
                d="M12 7v5l3 2"/></svg>{{ formatDateTime(report.violation_time) }}</span>
            <span v-if="report.location" class="record-location"><svg aria-hidden="true" viewBox="0 0 24 24"><path
                d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10"
                                                                            r="2"/></svg>{{ report.location }}</span>
          </div>
        </button>
      </div>
    </section>

    <div v-if="selectedReport" class="detail-overlay" @click.self="closeDetails">
      <section aria-labelledby="detail-title" aria-modal="true" class="detail-sheet" role="dialog">
        <div class="sheet-handle"/>
        <header class="detail-header">
          <div>
            <span class="detail-kicker">上报详情 · #{{ String(selectedReport.id).padStart(5, '0') }}</span>
            <h2 id="detail-title">{{ selectedReport.violation_type }}</h2>
          </div>
          <button aria-label="关闭详情" class="close-button" type="button" @click="closeDetails">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m6 6 12 12M18 6 6 18"/>
            </svg>
          </button>
        </header>
        <div class="detail-scroll">
          <div :class="`detail-status--${selectedReport.status.toLowerCase()}`" class="detail-status">
            <span class="detail-status__icon"><svg aria-hidden="true" viewBox="0 0 24 24"><path
                d="m5 12 4 4L19 6"/></svg></span>
            <div><strong>{{
                statusLabel(selectedReport.status)
              }}</strong><span>{{ statusDescription(selectedReport.status) }}</span></div>
          </div>

          <div v-if="detailLoading" class="detail-loading"><span class="loader"/>正在获取最新处理状态</div>
          <template v-else>
            <dl class="detail-fields">
              <div>
                <dt>车牌号码</dt>
                <dd>{{ selectedReport.vehicle_number }}</dd>
              </div>
              <div>
                <dt>违规类型</dt>
                <dd>{{ selectedReport.violation_type }}</dd>
              </div>
              <div>
                <dt>发生时间</dt>
                <dd>{{ formatDateTime(selectedReport.violation_time) }}</dd>
              </div>
              <div>
                <dt>发生地点</dt>
                <dd>{{ selectedReport.location || '未填写' }}</dd>
              </div>
              <div>
                <dt>提交时间</dt>
                <dd>{{ formatDateTime(selectedReport.created_at) }}</dd>
              </div>
              <div v-if="selectedReport.handled_at">
                <dt>处理时间</dt>
                <dd>{{ formatDateTime(selectedReport.handled_at) }}</dd>
              </div>
            </dl>
            <section class="detail-note"><h3>违规说明</h3>
              <p>{{ selectedReport.description }}</p></section>
            <section v-if="selectedReport.handling_remark" class="detail-note"><h3>处理意见</h3>
              <p>{{ selectedReport.handling_remark }}</p></section>
            <section v-if="selectedReport.evidence" class="detail-evidence"><h3>现场图片</h3>
              <div v-if="evidenceLoading" class="evidence-placeholder"><span class="loader"/>正在加载图片</div>
              <img v-else-if="evidenceUrl" :src="evidenceUrl" alt="举报上传的现场图片">
              <p v-else-if="evidenceError" class="evidence-error">图片加载失败</p>
              <a v-else :href="selectedReport.evidence" rel="noreferrer" target="_blank">查看现场图片</a>
            </section>
          </template>
        </div>
        <footer class="detail-footer">
          <button class="footer-button" type="button" @click="closeDetails">完成</button>
        </footer>
      </section>
    </div>
  </main>
</template>

<script lang="ts" setup>
interface ViolationReport {
  id: number;
  vehicle_number: string;
  violation_type: string;
  violation_time: string;
  location: string | null;
  description: string;
  evidence: string | null;
  status: string;
  handling_remark: string | null;
  handled_at: string | null;
  created_at: string;
}

interface ReportList {
  items: ViolationReport[];
  total: number;
}

const http = useHttp();
const runtimeConfig = useRuntimeConfig();
definePageMeta({layout: false});
const route = useRoute();
const router = useRouter();
const authToken = useCookie<string | null>("cartask_auth_token", {sameSite: "lax", path: "/"});
const reports = ref<ViolationReport[]>([]);
const total = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const activeFilter = ref("ALL");
const selectedReport = ref<ViolationReport | null>(null);
const detailLoading = ref(false);
const evidenceLoading = ref(false);
const evidenceError = ref(false);
const evidenceUrl = ref("");
let detailRequestId = 0;
let evidenceRequestId = 0;

const filters = [
  {value: "ALL", label: "全部"},
  {value: "PENDING", label: "待处理"},
  {value: "ACCEPTED", label: "处理中"},
  {value: "RESOLVED", label: "已处理"},
  {value: "REJECTED", label: "未通过"},
];

const filteredReports = computed(() => activeFilter.value === "ALL"
    ? reports.value
    : reports.value.filter((report) => report.status === activeFilter.value));
const pendingCount = computed(() => reports.value.filter((report) => ["PENDING", "ACCEPTED"].includes(report.status)).length);
const submissionNotice = computed(() => route.query.submitted === "1");

async function loadReports() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<ReportList>("/wx/violation-reports", {page: 1, page_size: 100});
    reports.value = result.items || [];
    total.value = result.total || 0;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "请检查网络后重试";
  } finally {
    loading.value = false;
  }
}

function statusLabel(status: string) {
  return ({
    PENDING: "待处理",
    ACCEPTED: "处理中",
    RESOLVED: "已处理",
    REJECTED: "未通过"
  } as Record<string, string>)[status] || status;
}

function statusDescription(status: string) {
  return ({
    PENDING: "已收到你的上报，工作人员会尽快核实。",
    ACCEPTED: "工作人员正在核实这条上报。",
    RESOLVED: "这条上报已经处理完成。",
    REJECTED: "该上报未通过审核，可查看处理意见。",
  } as Record<string, string>)[status] || "可在这里查看当前处理进度。";
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  return value.replace("T", " ").slice(0, 16);
}

function clearEvidenceUrl() {
  if (evidenceUrl.value.startsWith("blob:")) URL.revokeObjectURL(evidenceUrl.value);
  evidenceUrl.value = "";
}

async function loadEvidence(url: string | null) {
  const requestId = ++evidenceRequestId;
  clearEvidenceUrl();
  evidenceLoading.value = false;
  evidenceError.value = false;
  if (!url) {
    evidenceLoading.value = false;
    return;
  }
  let path = url;
  try {
    const parsed = new URL(url, window.location.origin);
    if (!parsed.pathname.includes("/api/files/")) {
      evidenceUrl.value = url;
      return;
    }
    path = url.startsWith("http") ? url : `${String(runtimeConfig.public.baseUrl).replace(/\/api\/?$/, "")}${url}`;
  } catch {
    evidenceUrl.value = url;
    return;
  }
  evidenceLoading.value = true;
  const token = authToken.value?.trim();
  try {
    const response = await fetch(path, {headers: token ? {Authorization: /^bearer\s/i.test(token) ? token : `Bearer ${token}`} : {}});
    if (!response.ok) throw new Error("image request failed");
    const blob = await response.blob();
    if (requestId !== evidenceRequestId) return;
    evidenceUrl.value = URL.createObjectURL(blob);
  } catch {
    if (requestId !== evidenceRequestId) return;
    evidenceError.value = true;
  } finally {
    if (requestId === evidenceRequestId) evidenceLoading.value = false;
  }
}

async function openDetails(report: ViolationReport) {
  selectedReport.value = report;
  detailLoading.value = true;
  const requestId = ++detailRequestId;
  void loadEvidence(report.evidence);
  try {
    const latest = await http.get<ViolationReport>(`/wx/violation-reports/${report.id}`);
    if (requestId === detailRequestId) {
      selectedReport.value = latest;
      if (latest.evidence !== report.evidence) void loadEvidence(latest.evidence);
    }
  } catch {
    // 列表数据仍可用于查看详情；详情接口失败时保留当前记录。
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false;
  }
}

function closeDetails() {
  detailRequestId += 1;
  evidenceRequestId += 1;
  selectedReport.value = null;
  detailLoading.value = false;
  evidenceLoading.value = false;
  clearEvidenceUrl();
}

onMounted(loadReports);
onBeforeUnmount(() => {
  evidenceRequestId += 1;
  clearEvidenceUrl();
});
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) {
  background: #f4f6f8;
  min-height: 100%;
  margin: 0;
}

.report-page {
  background: #f4f6f8;
  color: #242a31;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  height: 100dvh;
  margin: 0 auto;
  max-width: 458px;
  overflow: hidden;
}

.page-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #edf0f2;
  display: flex;
  flex: 0 0 70px;
  gap: 12px;
  padding: 0 16px;
}

.back-button, .refresh-button, .close-button {
  align-items: center;
  background: transparent;
  border: 0;
  color: #59636d;
  display: inline-flex;
  flex: 0 0 auto;
  justify-content: center;
  padding: 0;
}

.back-button {
  height: 38px;
  margin-left: -8px;
  width: 32px;
}

.back-button svg, .create-button svg, .refresh-button svg, .close-button svg {
  fill: none;
  height: 22px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
  width: 22px;
}

.page-heading {
  flex: 1;
  min-width: 0;
}

.success-notice {
  align-items: center;
  background: #eaf8f0;
  color: #278654;
  display: flex;
  flex: 0 0 auto;
  font-size: 11px;
  gap: 7px;
  margin: 12px 16px 0;
  padding: 10px 11px;
}

.success-notice > svg {
  fill: none;
  height: 16px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
  width: 16px;
}

.success-notice button {
  align-items: center;
  background: transparent;
  border: 0;
  color: #65a886;
  display: inline-flex;
  height: 22px;
  justify-content: center;
  margin-left: auto;
  padding: 0;
  width: 22px;
}

.success-notice button svg {
  fill: none;
  height: 14px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-width: 1.8;
  width: 14px;
}

.page-heading h1 {
  color: #20272e;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.3;
  margin: 0;
}

.page-heading p {
  color: #98a1aa;
  font-size: 11px;
  margin: 4px 0 0;
}

.create-button {
  align-items: center;
  background: #268be5;
  border-radius: 8px;
  color: #fff;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  gap: 4px;
  justify-content: center;
  min-height: 34px;
  padding: 0 9px;
  text-decoration: none;
}

.create-button svg {
  height: 17px;
  stroke-width: 2;
  width: 17px;
}

.report-overview {
  background: linear-gradient(125deg, #238ee9, #43a9f1);
  border-radius: 14px;
  box-shadow: 0 8px 20px rgb(41 139 221 / 15%);
  color: #fff;
  flex: 0 0 128px;
  margin: 16px 16px 4px;
  overflow: hidden;
  padding: 16px 18px 12px;
  position: relative;
}

.overview-copy {
  align-items: baseline;
  display: flex;
  gap: 7px;
  position: relative;
  z-index: 1;
}

.overview-label {
  align-self: flex-start;
  font-size: 12px;
  opacity: .9;
}

.overview-copy strong {
  font-size: 31px;
  font-weight: 650;
  line-height: 1.1;
  margin-left: 4px;
}

.overview-unit {
  font-size: 11px;
  opacity: .86;
}

.overview-icon {
  color: rgb(255 255 255 / 26%);
  height: 76px;
  position: absolute;
  right: 14px;
  top: 9px;
  width: 76px;
}

.overview-icon svg {
  fill: none;
  height: 100%;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
  width: 100%;
}

.overview-foot {
  align-items: center;
  border-top: 1px solid rgb(255 255 255 / 24%);
  bottom: 0;
  display: flex;
  font-size: 10px;
  gap: 7px;
  left: 18px;
  opacity: .9;
  padding: 10px 0;
  position: absolute;
  right: 18px;
}

.overview-dot {
  background: #b7e2ff;
  border-radius: 50%;
  height: 3px;
  width: 3px;
}

.records-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 8px 16px 12px;
}

.section-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 6px 0 10px;
}

.section-heading h2 {
  color: #252c32;
  font-size: 15px;
  font-weight: 650;
  margin: 0;
}

.section-heading p {
  color: #9ba4ad;
  font-size: 11px;
  margin: 4px 0 0;
}

.refresh-button {
  border: 1px solid #e7ebef;
  border-radius: 8px;
  height: 32px;
  width: 34px;
}

.refresh-button svg {
  height: 16px;
  width: 16px;
}

.refresh-button:disabled {
  opacity: .65;
}

.is-spinning {
  animation: spin .9s linear infinite;
}

.status-filters {
  display: flex;
  flex: 0 0 auto;
  gap: 7px;
  overflow-x: auto;
  padding: 0 0 12px;
  scrollbar-width: none;
}

.status-filters::-webkit-scrollbar {
  display: none;
}

.filter-chip {
  align-items: center;
  background: #fff;
  border: 1px solid #edf0f3;
  border-radius: 999px;
  color: #68727c;
  display: inline-flex;
  flex: 0 0 auto;
  font: inherit;
  font-size: 11px;
  gap: 5px;
  min-height: 30px;
  padding: 0 12px;
}

.filter-chip span {
  color: #96a1ab;
  font-size: 10px;
}

.filter-chip--active {
  background: #eaf4ff;
  border-color: #cae4fd;
  color: #187dd2;
  font-weight: 600;
}

.filter-chip--active span {
  color: #187dd2;
}

.record-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 2px;
}

.record-card {
  background: #fff;
  border: 1px solid #edf0f3;
  border-radius: 12px;
  color: inherit;
  display: block;
  flex: 0 0 auto;
  font: inherit;
  padding: 13px 13px 11px;
  text-align: left;
  width: 100%;
}

.record-card:active {
  background: #f9fbfd;
}

.record-card__top, .record-card__bottom {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.record-type {
  align-items: center;
  color: #343c44;
  display: inline-flex;
  font-size: 13px;
  font-weight: 650;
  gap: 7px;
}

.record-type__dot {
  background: #ff9c61;
  border-radius: 50%;
  height: 7px;
  width: 7px;
}

.status-tag {
  align-items: center;
  background: #fff6e8;
  border-radius: 999px;
  color: #cb831b;
  display: inline-flex;
  font-size: 10px;
  gap: 5px;
  padding: 5px 8px;
  white-space: nowrap;
}

.status-tag i {
  background: currentColor;
  border-radius: 50%;
  height: 5px;
  width: 5px;
}

.status--accepted {
  background: #eaf4ff;
  color: #338ad2;
}

.status--resolved {
  background: #eaf8f0;
  color: #299460;
}

.status--rejected {
  background: #fff0ef;
  color: #d7635c;
}

.record-vehicle {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.vehicle-icon {
  align-items: center;
  background: #f1f6fa;
  border-radius: 7px;
  color: #748b9d;
  display: inline-flex;
  height: 30px;
  justify-content: center;
  width: 32px;
}

.vehicle-icon svg {
  fill: none;
  height: 19px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.45;
  width: 19px;
}

.record-vehicle strong {
  color: #29323a;
  font-size: 14px;
  font-weight: 650;
  letter-spacing: .4px;
}

.record-arrow {
  color: #b9c1c8;
  margin-left: auto;
}

.record-arrow svg {
  fill: none;
  height: 17px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
  width: 17px;
}

.record-description {
  color: #77818b;
  display: -webkit-box;
  font-size: 11px;
  line-height: 1.55;
  margin: 9px 0 11px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.record-card__bottom {
  border-top: 1px solid #f1f3f5;
  color: #9aa3ac;
  gap: 10px;
  padding-top: 9px;
}

.record-time, .record-location {
  align-items: center;
  display: inline-flex;
  font-size: 10px;
  gap: 4px;
  min-width: 0;
}

.record-location {
  flex: 1;
  justify-content: flex-end;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-time svg, .record-location svg {
  fill: none;
  flex: 0 0 auto;
  height: 14px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
  width: 14px;
}

.state-card {
  align-items: center;
  background: #fff;
  border: 1px solid #edf0f3;
  border-radius: 12px;
  color: #98a2ac;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-height: 180px;
  padding: 24px;
  text-align: center;
}

.state-card > strong {
  color: #49545e;
  font-size: 14px;
  font-weight: 600;
  margin-top: 12px;
}

.state-card > span:last-of-type {
  font-size: 11px;
  margin-top: 6px;
}

.loader {
  animation: spin .9s linear infinite;
  border: 2px solid #dfeaf4;
  border-radius: 50%;
  border-top-color: #278ee4;
  height: 22px;
  width: 22px;
}

.state-card--error .state-icon {
  align-items: center;
  background: #fff0ef;
  border-radius: 50%;
  color: #d7635c;
  display: flex;
  font-size: 20px;
  height: 38px;
  justify-content: center;
  width: 38px;
}

.retry-button, .empty-create {
  background: #edf6ff;
  border: 0;
  border-radius: 7px;
  color: #2183d4;
  font: inherit;
  font-size: 12px;
  margin-top: 15px;
  padding: 9px 14px;
  text-decoration: none;
}

.empty-icon {
  align-items: center;
  background: #f2f7fb;
  border-radius: 50%;
  color: #a8bac8;
  display: flex;
  height: 60px;
  justify-content: center;
  width: 60px;
}

.empty-icon svg {
  fill: none;
  height: 33px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
  width: 33px;
}

.detail-overlay {
  align-items: flex-end;
  background: rgb(20 30 40 / 42%);
  display: flex;
  inset: 0;
  justify-content: center;
  position: fixed;
  z-index: 50;
}

.detail-sheet {
  background: #fff;
  border-radius: 18px 18px 0 0;
  display: flex;
  flex-direction: column;
  max-height: 88dvh;
  max-width: 458px;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom, 0px);
  width: 100%;
}

.sheet-handle {
  background: #dce1e5;
  border-radius: 999px;
  flex: 0 0 auto;
  height: 4px;
  margin: 9px auto 3px;
  width: 34px;
}

.detail-header {
  align-items: center;
  border-bottom: 1px solid #f0f2f4;
  display: flex;
  flex: 0 0 auto;
  justify-content: space-between;
  padding: 12px 18px;
}

.detail-kicker {
  color: #9aa3ad;
  font-size: 10px;
}

.detail-header h2 {
  color: #272f36;
  font-size: 16px;
  margin: 5px 0 0;
}

.close-button {
  border-radius: 50%;
  height: 32px;
  width: 32px;
}

.close-button svg {
  height: 19px;
  width: 19px;
}

.detail-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px 22px;
}

.detail-status {
  align-items: center;
  background: #fff7e9;
  border-radius: 10px;
  color: #cb831b;
  display: flex;
  gap: 10px;
  padding: 12px;
}

.detail-status--accepted {
  background: #edf6ff;
  color: #338ad2;
}

.detail-status--resolved {
  background: #eef9f2;
  color: #299460;
}

.detail-status--rejected {
  background: #fff1f0;
  color: #d7635c;
}

.detail-status__icon {
  align-items: center;
  background: rgb(255 255 255 / 76%);
  border-radius: 50%;
  display: inline-flex;
  height: 30px;
  justify-content: center;
  width: 30px;
}

.detail-status__icon svg {
  fill: none;
  height: 17px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
  width: 17px;
}

.detail-status strong, .detail-status span:last-child {
  display: block;
}

.detail-status strong {
  font-size: 12px;
}

.detail-status div > span {
  color: #8f969d;
  font-size: 10px;
  margin-top: 4px;
}

.detail-loading, .evidence-placeholder {
  align-items: center;
  color: #84909b;
  display: flex;
  font-size: 11px;
  gap: 8px;
  justify-content: center;
  padding: 18px;
}

.detail-loading .loader, .evidence-placeholder .loader {
  height: 16px;
  width: 16px;
}

.detail-fields {
  margin: 15px 0 0;
}

.detail-fields > div {
  align-items: flex-start;
  border-bottom: 1px solid #f1f3f5;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 11px 0;
}

.detail-fields dt {
  color: #8e98a1;
  flex: 0 0 66px;
  font-size: 11px;
}

.detail-fields dd {
  color: #343d45;
  font-size: 11px;
  margin: 0;
  text-align: right;
}

.detail-note, .detail-evidence {
  margin-top: 17px;
}

.detail-note h3, .detail-evidence h3 {
  color: #65717b;
  font-size: 11px;
  font-weight: 600;
  margin: 0 0 8px;
}

.detail-note p {
  background: #f7f9fa;
  border-radius: 8px;
  color: #4f5962;
  font-size: 12px;
  line-height: 1.7;
  margin: 0;
  padding: 11px;
  white-space: pre-wrap;
}

.detail-evidence img {
  border-radius: 9px;
  display: block;
  max-height: 220px;
  max-width: 100%;
  object-fit: contain;
}

.detail-evidence a {
  color: #2388dc;
  font-size: 12px;
}

.evidence-error {
  color: #d7635c;
  font-size: 11px;
}

.detail-footer {
  border-top: 1px solid #f0f2f4;
  flex: 0 0 auto;
  padding: 10px 18px calc(10px + env(safe-area-inset-bottom, 0px));
}

.footer-button {
  background: #288fe5;
  border: 0;
  border-radius: 9px;
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  height: 42px;
  width: 100%;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
