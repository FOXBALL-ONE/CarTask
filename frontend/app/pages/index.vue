<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">仪表盘</h1>
        <p class="page__desc">车场运营数据概览</p>
      </div>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadDashboard">
        <span class="material-icons-outlined">refresh</span>刷新
      </button>
    </header>

    <div v-if="loading" class="loading-state">正在加载数据...</div>
    <div v-else-if="loadError" class="error-state">{{ loadError }}</div>
    <template v-else>
      <div class="stat-grid">
        <article v-for="stat in dashboard?.stats" :key="stat.label" class="stat-card" :class="`stat-card--${stat.color}`">
          <strong class="stat-card__value">{{ stat.value }}</strong>
          <span class="stat-card__label">{{ stat.label }}</span>
          <span class="stat-card__delta">{{ stat.delta }}</span>
        </article>
      </div>

      <div class="dashboard-grid">
        <article class="panel">
          <h2 class="panel__title">可停区域</h2>
          <div v-if="dashboard?.parking.length" class="parking-list">
            <div v-for="area in dashboard.parking" :key="area.area" class="parking-row">
              <div class="parking-row__name">{{ area.area }}</div>
              <div class="parking-row__bar"><span :style="{ width: `${occupancy(area)}%` }" /></div>
              <div class="parking-row__count">{{ area.used }} / {{ area.total }}</div>
            </div>
          </div>
          <p v-else class="empty-state">暂无车位数据</p>
        </article>
        <article class="panel">
          <h2 class="panel__title">违规类型</h2>
          <div v-if="dashboard?.violationTypes.length" class="violation-list">
            <div v-for="violation in dashboard.violationTypes" :key="violation.name" class="violation-row">
              <span class="violation-row__dot" :style="{ background: violation.color }" />
              <span>{{ violation.name }}</span>
              <strong>{{ violation.value }} 次</strong>
            </div>
          </div>
          <p v-else class="empty-state">暂无违规记录</p>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
interface Stat {
  label: string;
  value: number;
  delta: string;
  color: "blue" | "green" | "orange" | "red";
}

interface ParkingArea {
  area: string;
  total: number;
  used: number;
}

interface ViolationType {
  name: string;
  value: number;
  color: string;
}

interface Dashboard {
  stats: Stat[];
  parking: ParkingArea[];
  violationTypes: ViolationType[];
}

const http = useHttp();
const dashboard = ref<Dashboard>();
const loading = ref(true);
const loadError = ref("");

async function loadDashboard() {
  loading.value = true;
  loadError.value = "";
  try {
    dashboard.value = await http.get<Dashboard>("/dashboard");
  } catch (error) {
    loadError.value = (error as { statusMessage?: string }).statusMessage || "仪表盘数据加载失败";
  } finally {
    loading.value = false;
  }
}

function occupancy(area: ParkingArea) {
  return area.total > 0 ? Math.round((area.used / area.total) * 100) : 0;
}

onMounted(loadDashboard);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }
.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }
.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }
.page__desc { color: var(--text-sub); font-size: 13px; margin: 2px 0 0; }
.refresh-button { align-items: center; background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; color: var(--text-sub); cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 32px; padding: 0 12px; }
.refresh-button:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.refresh-button:disabled { cursor: wait; opacity: 0.65; }
.refresh-button .material-icons-outlined { font-size: 16px; }
.stat-grid { display: grid; gap: 16px; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 20px; }
.stat-card { border: 1px solid var(--border); border-radius: 8px; display: grid; min-height: 136px; padding: 20px; transition: border-color var(--tr), transform var(--tr); }
.stat-card:hover { border-color: var(--primary); transform: translateY(-2px); }
.stat-card--blue { background: #eff6ff; }.stat-card--green { background: #ecfdf5; }.stat-card--orange { background: #fff7ed; }.stat-card--red { background: #fef2f2; }
.stat-card__value { color: var(--text); font-size: 26px; line-height: 1; }.stat-card__label { align-self: end; color: var(--text-sub); }.stat-card__delta { color: var(--text-mute); font-size: 12px; margin-top: 4px; }
.dashboard-grid { display: grid; gap: 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.panel { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; min-height: 260px; padding: 20px; }.panel__title { color: var(--text); font-size: 14px; font-weight: 600; margin: 0 0 18px; }
.parking-list, .violation-list { display: grid; gap: 14px; }.parking-row { align-items: center; display: grid; gap: 12px; grid-template-columns: 88px minmax(80px, 1fr) auto; }.parking-row__name, .parking-row__count { color: var(--text-sub); }.parking-row__count { font-size: 12px; }.parking-row__bar { background: var(--bg); border-radius: 3px; height: 8px; overflow: hidden; }.parking-row__bar span { background: var(--primary); border-radius: inherit; display: block; height: 100%; }.violation-row { align-items: center; border-bottom: 1px solid var(--border); color: var(--text-sub); display: grid; gap: 10px; grid-template-columns: auto 1fr auto; padding-bottom: 12px; }.violation-row strong { color: var(--text); font-size: 13px; }.violation-row__dot { border-radius: 50%; height: 9px; width: 9px; }.loading-state, .error-state, .empty-state { color: var(--text-sub); padding: 40px 0; text-align: center; }.error-state { color: var(--red); }
:global([data-theme="dark"]) .stat-card--blue { background: #1e3a5f; }:global([data-theme="dark"]) .stat-card--green { background: #164e31; }:global([data-theme="dark"]) .stat-card--orange { background: #4a3420; }:global([data-theme="dark"]) .stat-card--red { background: #4a1f1f; }
@media (max-width: 980px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.dashboard-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .page { padding: 16px; } }
@media (max-width: 480px) { .stat-grid { grid-template-columns: 1fr; }.parking-row { grid-template-columns: 1fr auto; }.parking-row__bar { grid-column: 1 / -1; grid-row: 2; } }
</style>
