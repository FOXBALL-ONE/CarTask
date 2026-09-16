<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">停车区域</h1>
        <p class="page__desc">查看科拓同步的停车场详情与停车区域字典</p>
      </div>
      <div class="header-actions">
        <button class="button button--ghost" type="button" @click="goSync"><span class="material-icons-outlined">sync_alt</span>数据同步
        </button>
        <button class="button button--soft" type="button" @click="refresh"><span
            class="material-icons-outlined">refresh</span>刷新
        </button>
      </div>
    </header>

    <section :class="{ 'lot-card--empty': !lot }" class="lot-card">
      <template v-if="lot">
        <div class="lot-card__info">
          <div class="lot-card__icon"><span class="material-icons-outlined">local_parking</span></div>
          <div class="lot-card__text">
            <h2>{{ lot.name }}</h2>
            <p>车场编号 {{ lot.parkCode || "-" }}</p>
          </div>
        </div>
        <div class="lot-card__metrics">
          <div class="metric"><span>总车位</span><strong>{{ lot.totalPlaceCount }}</strong></div>
          <div class="metric"><span>区域数量</span><strong>{{ lot.areaCount }}</strong></div>
          <div class="metric"><span>最近同步</span><strong class="metric__time">{{
              formatDateTime(lot.updatedAt)
            }}</strong></div>
        </div>
      </template>
      <template v-else>
        <div class="lot-card__info">
          <div class="lot-card__icon"><span class="material-icons-outlined">local_parking</span></div>
          <div class="lot-card__text">
            <h2>尚未同步停车场详情</h2>
            <p>执行一次数据同步后，这里会展示停车场名称和总车位。</p>
          </div>
        </div>
      </template>
    </section>

    <section class="panel">
      <div v-if="loading" class="state">正在加载停车区域数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th>编号</th>
            <th>区域编码</th>
            <th>区域名称</th>
            <th>车位数量</th>
            <th>显示排序</th>
            <th>状态</th>
            <th>更新时间</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="zone in zones" :key="zone.id">
            <td>{{ String(zone.id).padStart(4, "0") }}</td>
            <td><span class="code-tag">{{ zone.zoneCode || "-" }}</span></td>
            <td><strong class="zone-name">{{ zone.zoneName || "-" }}</strong></td>
            <td>{{ zone.placeCount }}</td>
            <td>{{ zone.orderNumber }}</td>
            <td><span :class="isActive(zone) ? 'status-tag--normal' : 'status-tag--disabled'" class="status-tag">{{
                isActive(zone) ? "启用" : "停用"
              }}</span></td>
            <td class="muted">{{ formatDateTime(zone.updatedAt) }}</td>
          </tr>
          <tr v-if="zones.length === 0">
            <td class="empty" colspan="7">暂无数据</td>
          </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="!loading && !errorMessage" class="pagination">
        <span>共 {{ total }} 条</span>
        <button :disabled="page <= 1" aria-label="上一页" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" aria-label="下一页" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>
  </section>
</template>

<script lang="ts" setup>
interface Zone {
  id: number;
  zoneCode?: string | null;
  zoneName?: string | null;
  orderNumber: number;
  placeCount: number;
  status: string;
  updatedAt?: string | null;
}

interface ZonePage {
  content?: Zone[];
  items?: Zone[];
  totalElements?: number;
  total?: number;
  page?: { totalElements?: number }
}

interface ParkingLotInfo {
  id: number;
  parkCode?: string | null;
  name: string;
  totalPlaceCount: number;
  areaCount: number;
  parkArea?: string | null;
  updatedAt?: string | null;
}

const http = useHttp();
const {can} = usePermission();
const page = ref(1);
const pageSize = 8;
const zones = ref<Zone[]>([]);
const total = ref(0);
const lot = ref<ParkingLotInfo | null>(null);
const loading = ref(true);
const errorMessage = ref("");

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));

async function loadLot() {
  try {
    const result = await http.get<ParkingLotInfo | undefined>("/parking-lot");
    // 未同步时统一响应会兜底为空对象，视为暂无车场详情。
    lot.value = result?.name ? result : null;
  } catch {
    // 车场详情缺失不影响区域列表展示。
    lot.value = null;
  }
}

async function loadZones() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<ZonePage>("/zone-types", {page: page.value, page_size: pageSize});
    const rows = Array.isArray(result) ? result : (result.content ?? result.items ?? []);
    zones.value = rows;
    total.value = result.totalElements ?? result.total ?? result.page?.totalElements ?? rows.length;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "停车区域数据加载失败";
  } finally {
    loading.value = false;
  }
}

function isActive(zone: Zone) {
  return zone.status === "Activity";
}

function refresh() {
  page.value = 1;
  void loadLot();
  void loadZones();
}

function goSync() {
  void navigateTo("/synchronizations");
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  void loadZones();
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
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

onMounted(() => {
  void loadLot();
  void loadZones();
});
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px;
}

.page__header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
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
  margin: 2px 0 0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  gap: 5px;
  height: 32px;
  justify-content: center;
  padding: 0 12px;
  white-space: nowrap;
}

.button--soft {
  background: var(--primary-soft);
  color: var(--primary);
}

.button--ghost {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text-sub);
}

.button .material-icons-outlined {
  font-size: 16px;
}

.lot-card {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 16px 18px;
}

.lot-card--empty {
  border-style: dashed;
}

.lot-card__info {
  align-items: center;
  display: flex;
  gap: 12px;
}

.lot-card__icon {
  align-items: center;
  background: var(--primary-soft);
  border-radius: 9px;
  color: var(--primary);
  display: flex;
  flex: 0 0 40px;
  height: 40px;
  justify-content: center;
}

.lot-card__icon .material-icons-outlined {
  font-size: 21px;
}

.lot-card__text h2 {
  color: var(--text);
  font-size: 16px;
  font-weight: 650;
  margin: 0;
}

.lot-card__text p {
  color: var(--text-sub);
  margin: 3px 0 0;
}

.lot-card__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.metric {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 7px;
  min-width: 96px;
  padding: 10px 14px;
}

.metric span {
  color: var(--text-sub);
  display: block;
  font-size: 11px;
}

.metric strong {
  color: var(--text);
  display: block;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
  margin-top: 4px;
}

.metric .metric__time {
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
}

.panel {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  overflow: hidden;
}

.table-wrap {
  overflow-x: auto;
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 720px;
  width: 100%;
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 12px;
  font-weight: 500;
  padding: 10px 16px;
  text-align: left;
  white-space: nowrap;
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 11px 16px;
  white-space: nowrap;
}

.table tbody tr:hover {
  background: var(--bg);
}

.zone-name {
  color: var(--primary);
}

.code-tag, .status-tag {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 500;
  gap: 4px;
  line-height: 1.5;
  padding: 2px 8px;
}

.code-tag {
  background: var(--neutral-soft);
  color: var(--text-sub);
}

.code-tag::before {
  background: var(--text-mute);
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px;
}

.status-tag--normal {
  background: var(--success-soft);
  color: var(--success);
}

.status-tag--normal::before {
  background: var(--success);
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px;
}

.status-tag--disabled {
  background: var(--danger-soft);
  color: var(--danger);
}

.status-tag--disabled::before {
  background: var(--danger);
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px;
}

.muted {
  color: var(--text-sub) !important;
}

.state, .empty {
  color: var(--text-mute);
  padding: 48px;
  text-align: center;
}

.state--error {
  color: var(--danger);
}

.pagination {
  align-items: center;
  color: var(--text-sub);
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  padding: 14px 18px;
}

.pagination button {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  min-width: 28px;
}

.pagination button:hover:not(:disabled), .pagination button.active {
  background: var(--primary-soft);
  color: var(--primary);
}

.pagination button:disabled {
  cursor: not-allowed;
  opacity: .4;
}

.pagination .material-icons-outlined {
  font-size: 18px;
}

@media (max-width: 600px) {
  .page {
    padding: 16px;
  }

  .header-actions {
    justify-content: flex-end;
  }

  .lot-card__metrics {
    width: 100%;
  }
}
</style>
