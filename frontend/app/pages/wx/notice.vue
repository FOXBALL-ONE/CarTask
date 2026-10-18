<template>
  <main class="notice-page">
    <header class="page-header">
      <button aria-label="返回主页" class="back-button" type="button" @click="navigateTo('/wx')">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="m15 18-6-6 6-6"/>
        </svg>
      </button>
      <div class="page-heading">
        <h1>通报栏</h1>
        <p>已通过审核的违规记录</p>
      </div>
      <button :disabled="loading || loadingMore" aria-label="刷新通报" class="refresh-button" type="button"
              @click="loadNotices(true)">
        <svg :class="{ 'is-spinning': loading || loadingMore }" aria-hidden="true" viewBox="0 0 24 24">
          <path d="M20 11a8 8 0 0 0-14.8-3L3 11M4 5v6h6M4 13a8 8 0 0 0 14.8 3L21 13m-1 6v-6h-6"/>
        </svg>
      </button>
    </header>


    <section aria-labelledby="notices-title" class="notices-section">
      <div class="section-heading">
        <div><h2 id="notices-title">违规记录</h2>
          <p>按发生时间由近及远排列</p></div>
      </div>

      <div ref="listElement" class="notice-list" @scroll.passive="onListScroll">
        <div v-if="loading" class="state-card" role="status"><span class="loader"/><strong>正在加载通报</strong><span>请稍候</span>
        </div>
        <div v-else-if="errorMessage && notices.length === 0" class="state-card state-card--error" role="alert">
          <span class="state-icon">!</span><strong>通报暂时无法加载</strong><span>{{ errorMessage }}</span>
          <button class="retry-button" type="button" @click="loadNotices(true)">重新加载</button>
        </div>
        <div v-else-if="notices.length === 0" class="state-card">
          <span aria-hidden="true" class="empty-icon"><svg viewBox="0 0 48 48"><path d="M12 7h24v34H12z"/><path
              d="M18 17h12M18 23h12M18 29h7"/><circle cx="33" cy="33" r="8"/><path d="m30 33 2 2 4-4"/></svg></span>
          <strong>暂无已审核的违规通报</strong><span>记录通过审核后会展示在这里</span>
        </div>
        <template v-else>
          <button v-for="notice in notices" :key="notice.id" class="notice-card" type="button"
                  @click="openDetails(notice)">
            <div class="notice-card__top">
              <span class="notice-type"><span class="type-mark"><svg aria-hidden="true" viewBox="0 0 24 24"><path
                  d="m5 11 1.5-4h11L19 11l1.5 2v5h-17v-5L5 11Z"/><path
                  d="M6.5 11h11M7 15h.1M17 15h.1"/></svg></span>{{ notice.type_name }}</span>
              <span :class="notice.status === 'HANDLED' ? 'notice-status--handled' : 'notice-status--confirmed'"
                    class="notice-status"><i/>{{ statusLabel(notice.status) }}</span>
            </div>
            <div class="vehicle-line"><strong>{{ notice.subject_number }}</strong><span
                v-if="notice.subject_name">{{ notice.subject_name }}</span><span aria-hidden="true"
                                                                                 class="card-chevron"><svg
                viewBox="0 0 24 24"><path d="m9 18 6-6-6-6"/></svg></span></div>
            <p class="notice-description">{{ notice.description || '暂无违规说明' }}</p>
            <div class="notice-card__bottom">
              <span class="notice-time"><svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path
                  d="M12 7v5l3 2"/></svg>{{ formatDateTime(notice.violation_time) }}</span>
              <span v-if="notice.location" class="notice-location"><svg aria-hidden="true" viewBox="0 0 24 24"><path
                  d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10"
                                                                              r="2"/></svg>{{ notice.location }}</span>
            </div>
          </button>
          <div v-if="loadingMore" class="load-more-state" role="status"><span class="loader loader--small"/>正在加载更多通报
          </div>
          <div v-else class="load-more-hint">{{
              notices.length < total ? '继续向上滑动加载更多' : '已显示全部通报'
            }}
          </div>
          <div v-if="errorMessage" class="list-error" role="alert">{{ errorMessage }}
            <button type="button" @click="loadNotices(false)">重试</button>
          </div>
        </template>
      </div>
    </section>

    <div v-if="selectedNotice" class="detail-overlay" @click.self="closeDetails">
      <section aria-labelledby="notice-detail-title" aria-modal="true" class="detail-sheet" role="dialog">
        <div class="sheet-handle"/>
        <header class="detail-header">
          <div><span class="detail-kicker">违规通报 · #{{ String(selectedNotice.id).padStart(5, '0') }}</span>
            <h2 id="notice-detail-title">{{ selectedNotice.type_name }}</h2></div>
          <button aria-label="关闭详情" class="close-button" type="button" @click="closeDetails">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m6 6 12 12M18 6 6 18"/>
            </svg>
          </button>
        </header>
        <div class="detail-scroll">
          <div class="review-confirmation">
            <span class="confirmation-icon"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m5 12 4 4L19 6"/></svg></span>
            <div><strong>审核已通过</strong><span>{{
                selectedNotice.status === 'HANDLED' ? '该记录已完成处理。' : '该记录已审核确认。'
              }}</span></div>
          </div>
          <div v-if="detailLoading" class="detail-loading"><span class="loader loader--small"/>正在读取通报详情</div>
          <template v-else>
            <div class="score-panel"><span>违规分值</span><strong>{{ selectedNotice.score }}<small>分</small></strong>
            </div>
            <dl class="detail-fields">
              <div>
                <dt>车牌号码</dt>
                <dd>{{ selectedNotice.subject_number }}</dd>
              </div>
              <div v-if="selectedNotice.subject_name">
                <dt>车主</dt>
                <dd>{{ selectedNotice.subject_name }}</dd>
              </div>
              <div>
                <dt>违规类型</dt>
                <dd>{{ selectedNotice.type_name }}</dd>
              </div>
              <div>
                <dt>发生时间</dt>
                <dd>{{ formatDateTime(selectedNotice.violation_time) }}</dd>
              </div>
              <div>
                <dt>发生地点</dt>
                <dd>{{ selectedNotice.location || '未登记' }}</dd>
              </div>
              <div v-if="selectedNotice.handled_at">
                <dt>处理时间</dt>
                <dd>{{ formatDateTime(selectedNotice.handled_at) }}</dd>
              </div>
            </dl>
            <section class="detail-note"><h3>违规说明</h3>
              <p>{{ selectedNotice.description || '暂无违规说明' }}</p></section>
            <section v-if="selectedNotice.handling_remark" class="detail-note"><h3>处理意见</h3>
              <p>{{ selectedNotice.handling_remark }}</p></section>
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
interface ViolationNotice {
  id: number;
  subject_number: string;
  subject_name: string;
  type_name: string;
  score: number;
  description: string | null;
  location: string | null;
  violation_time: string;
  status: "CONFIRMED" | "HANDLED" | string;
  handling_remark: string | null;
  handled_at: string | null;
}

interface NoticePage {
  items: ViolationNotice[];
  total: number;
}

definePageMeta({layout: false});

const http = useHttp();
const notices = ref<ViolationNotice[]>([]);
const total = ref(0);
const page = ref(0);
const pageSize = 10;
const loading = ref(true);
const loadingMore = ref(false);
const errorMessage = ref("");
const listElement = ref<HTMLElement | null>(null);
const selectedNotice = ref<ViolationNotice | null>(null);
const detailLoading = ref(false);
let listRequestId = 0;
let detailRequestId = 0;

async function loadNotices(reset: boolean) {
  if (!reset && (loading.value || loadingMore.value || notices.value.length >= total.value)) return;
  const requestId = ++listRequestId;
  const nextPage = reset ? 1 : page.value + 1;
  errorMessage.value = "";
  if (reset) {
    loading.value = true;
    loadingMore.value = false;
    page.value = 0;
    notices.value = [];
    total.value = 0;
  } else {
    loadingMore.value = true;
  }

  try {
    const result = await http.get<NoticePage>("/wx/violation-notices", {page: nextPage, page_size: pageSize});
    if (requestId !== listRequestId) return;
    const nextItems = result.items || [];
    notices.value = reset ? nextItems : [...notices.value, ...nextItems];
    total.value = nextItems.length === 0 && notices.value.length < result.total ? notices.value.length : result.total || 0;
    page.value = nextPage;
  } catch (error) {
    if (requestId === listRequestId) errorMessage.value = (error as {
      statusMessage?: string
    }).statusMessage || "请检查网络后重试";
  } finally {
    if (requestId === listRequestId) {
      loading.value = false;
      loadingMore.value = false;
      void nextTick(() => {
        if (listElement.value && listElement.value.scrollHeight <= listElement.value.clientHeight + 1) onListScroll();
      });
    }
  }
}

function onListScroll() {
  const element = listElement.value;
  if (element && element.scrollHeight - element.scrollTop - element.clientHeight < 120) void loadNotices(false);
}

function statusLabel(status: string) {
  return status === "HANDLED" ? "已处理" : "已审核";
}

function formatDateTime(value: string | null | undefined) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

async function openDetails(notice: ViolationNotice) {
  selectedNotice.value = notice;
  detailLoading.value = true;
  const requestId = ++detailRequestId;
  try {
    const latest = await http.get<ViolationNotice>(`/wx/violation-notices/${notice.id}`);
    if (requestId === detailRequestId) selectedNotice.value = latest;
  } catch {
    // 列表记录仍可用于查看完整通报内容。
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false;
  }
}

function closeDetails() {
  detailRequestId += 1;
  selectedNotice.value = null;
  detailLoading.value = false;
}

onMounted(() => void loadNotices(true));
onBeforeUnmount(() => {
  listRequestId += 1;
  detailRequestId += 1;
});
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) {
  background: #f4f6f8;
  min-height: 100%;
  margin: 0;
}

.notice-page {
  background: #f4f6f8;
  color: #252d34;
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

.back-button svg, .refresh-button svg, .close-button svg {
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
  opacity: .6;
}

.is-spinning {
  animation: spin .9s linear infinite;
}

.notices-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 9px 16px 0;
}

.section-heading {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  justify-content: space-between;
  padding: 9px 0 10px;
}

.section-heading h2 {
  color: #2b333a;
  font-size: 14px;
  font-weight: 650;
  margin: 0;
}

.section-heading p {
  color: #9ba4ad;
  font-size: 10px;
  margin: 3px 0 0;
}

.notice-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 9px;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-bottom: 10px;
  scrollbar-width: none;
}

.notice-list::-webkit-scrollbar {
  display: none;
}

.notice-card {
  background: #fff;
  border: 1px solid #edf0f3;
  border-radius: 11px;
  color: inherit;
  flex: 0 0 auto;
  font: inherit;
  padding: 12px 12px 0;
  text-align: left;
  width: 100%;
}

.notice-card:active {
  background: #f9fbfd;
}

.notice-card__top {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.notice-type {
  align-items: center;
  color: #303941;
  display: inline-flex;
  font-size: 12px;
  font-weight: 650;
  gap: 7px;
  min-width: 0;
}

.type-mark {
  align-items: center;
  background: #eaf5ff;
  border-radius: 6px;
  color: #328fd2;
  display: inline-flex;
  flex: 0 0 auto;
  height: 25px;
  justify-content: center;
  width: 25px;
}

.type-mark svg {
  fill: none;
  height: 15px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.4;
  width: 15px;
}

.notice-status {
  align-items: center;
  background: #eaf5ff;
  border-radius: 999px;
  color: #3283c3;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 9px;
  gap: 4px;
  padding: 5px 7px;
}

.notice-status i {
  background: currentColor;
  border-radius: 50%;
  height: 4px;
  width: 4px;
}

.notice-status--handled {
  background: #eaf7f0;
  color: #329363;
}

.vehicle-line {
  align-items: baseline;
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.vehicle-line > strong {
  color: #2e3941;
  font-size: 13px;
  font-weight: 650;
  letter-spacing: .3px;
}

.vehicle-line > span:nth-child(2) {
  color: #8e99a2;
  font-size: 10px;
}

.card-chevron {
  color: #b6c0c8;
  margin-left: auto;
}

.card-chevron svg {
  fill: none;
  height: 16px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
  width: 16px;
}

.notice-description {
  color: #79858e;
  display: -webkit-box;
  font-size: 10px;
  line-height: 1.6;
  margin: 7px 0 9px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  white-space: pre-wrap;
}

.notice-card__bottom {
  align-items: center;
  border-top: 1px solid #f0f2f4;
  color: #98a2aa;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-height: 32px;
}

.notice-time, .notice-location {
  align-items: center;
  display: inline-flex;
  font-size: 9px;
  gap: 4px;
  min-width: 0;
}

.notice-location {
  flex: 1;
  justify-content: flex-end;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time svg, .notice-location svg {
  fill: none;
  flex: 0 0 auto;
  height: 13px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.45;
  width: 13px;
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
  min-height: 170px;
  padding: 22px;
  text-align: center;
}

.state-card > strong {
  color: #49545e;
  font-size: 13px;
  font-weight: 600;
  margin-top: 12px;
}

.state-card > span:last-of-type {
  font-size: 10px;
  margin-top: 6px;
}

.loader {
  animation: spin .9s linear infinite;
  border: 2px solid #dfeaf4;
  border-radius: 50%;
  border-top-color: #278ee4;
  height: 21px;
  width: 21px;
}

.loader--small {
  height: 15px;
  width: 15px;
}

.state-card--error .state-icon {
  align-items: center;
  background: #fff0ef;
  border-radius: 50%;
  color: #d7635c;
  display: flex;
  font-size: 18px;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.retry-button {
  background: #edf6ff;
  border: 0;
  border-radius: 7px;
  color: #2183d4;
  font: inherit;
  font-size: 11px;
  margin-top: 13px;
  padding: 8px 13px;
}

.empty-icon {
  align-items: center;
  background: #f2f7fb;
  border-radius: 50%;
  color: #a8bac8;
  display: flex;
  height: 58px;
  justify-content: center;
  width: 58px;
}

.empty-icon svg {
  fill: none;
  height: 31px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
  width: 31px;
}

.load-more-state, .load-more-hint {
  align-items: center;
  color: #a0a9b1;
  display: flex;
  flex: 0 0 auto;
  font-size: 9px;
  gap: 7px;
  justify-content: center;
  min-height: 32px;
}

.list-error {
  align-items: center;
  background: #fff1f0;
  border-radius: 7px;
  color: #c95c55;
  display: flex;
  flex: 0 0 auto;
  font-size: 10px;
  justify-content: space-between;
  padding: 8px 10px;
}

.list-error button {
  background: transparent;
  border: 0;
  color: #ba524b;
  font: inherit;
  font-size: 10px;
  text-decoration: underline;
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
  font-size: 9px;
}

.detail-header h2 {
  color: #272f36;
  font-size: 16px;
  margin: 4px 0 0;
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
  padding: 13px 16px 20px;
}

.review-confirmation {
  align-items: center;
  background: #eaf7f0;
  border-radius: 10px;
  color: #329363;
  display: flex;
  gap: 9px;
  padding: 11px;
}

.confirmation-icon {
  align-items: center;
  background: #fff;
  border-radius: 50%;
  display: inline-flex;
  height: 29px;
  justify-content: center;
  width: 29px;
}

.confirmation-icon svg {
  fill: none;
  height: 16px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
  width: 16px;
}

.review-confirmation strong, .review-confirmation span {
  display: block;
}

.review-confirmation strong {
  font-size: 11px;
}

.review-confirmation div > span {
  color: #789b88;
  font-size: 9px;
  margin-top: 3px;
}

.score-panel {
  align-items: center;
  background: #f6f9fc;
  border-radius: 9px;
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  padding: 10px 12px;
}

.score-panel > span {
  color: #75818b;
  font-size: 10px;
}

.score-panel > strong {
  color: #347fb2;
  font-size: 20px;
}

.score-panel small {
  font-size: 9px;
  font-weight: 500;
  margin-left: 3px;
}

.detail-fields {
  margin: 9px 0 0;
}

.detail-fields > div {
  align-items: flex-start;
  border-bottom: 1px solid #f1f3f5;
  display: flex;
  gap: 14px;
  justify-content: space-between;
  padding: 10px 1px;
}

.detail-fields dt {
  color: #8e98a1;
  flex: 0 0 58px;
  font-size: 10px;
}

.detail-fields dd {
  color: #343d45;
  font-size: 10px;
  margin: 0;
  text-align: right;
}

.detail-note {
  margin-top: 16px;
}

.detail-note h3 {
  color: #65717b;
  font-size: 10px;
  font-weight: 650;
  margin: 0 0 7px;
}

.detail-note p {
  background: #f7f9fa;
  border-radius: 8px;
  color: #4f5962;
  font-size: 11px;
  line-height: 1.65;
  margin: 0;
  padding: 10px;
  white-space: pre-wrap;
}

.detail-loading {
  align-items: center;
  color: #89949d;
  display: flex;
  font-size: 10px;
  gap: 7px;
  justify-content: center;
  padding: 16px;
}

.detail-footer {
  border-top: 1px solid #f0f2f4;
  flex: 0 0 auto;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom, 0px));
}

.footer-button {
  background: #288fe5;
  border: 0;
  border-radius: 9px;
  color: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  height: 41px;
  width: 100%;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
