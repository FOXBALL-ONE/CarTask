<template>
  <div class="commute-routes">
    <header class="app-header">
      <div class="header-content">
        <h1>发车表</h1>
        <button class="refresh-btn" title="刷新" @click="load">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 4v6h6M23 20v-6h-6" />
            <path d="M20.49 9A9 9 0 0 0 5.64 5.64M3.51 15A9 9 0 0 0 18.36 18.36" />
          </svg>
        </button>
      </div>
      <n-alert type="info" closable :show-icon="false" class="header-time">
        {{ today.label }} · 最后更新：{{ lastUpdated }}
      </n-alert>
    </header>

    <div class="routes-container">
      <div v-if="errorMessage" class="state-box">
        <n-empty description="加载失败" />
        <p class="error-detail">{{ errorMessage }}</p>
        <n-button type="error" size="small" @click="load">重新加载</n-button>
      </div>

      <n-spin v-else-if="loading" description="正在同步发车时间..." />

      <div v-else-if="routes.length === 0" class="state-box">
        <n-empty description="暂未发布发车线路" />
      </div>

      <template v-else>
        <n-collapse>
          <n-collapse-item
            v-for="(route, index) in routes"
            :key="route.id"
            :title="`${String(index + 1).padStart(2, '0')}. ${route.route_name}`"
            :name="route.id"
          >
            <div class="route-card">
              <div class="route-header">
                <h3>{{ route.route_name }}</h3>
                <n-tag type="success" size="small">已发布</n-tag>
              </div>

              <div class="route-path-card">
                <div class="path-item">
                  <div class="path-dot path-dot--start"></div>
                  <div class="path-content">
                    <p class="path-label">起点</p>
                    <p class="path-address">{{ route.start_address }}</p>
                  </div>
                </div>
                <div class="path-line"></div>
                <div class="path-item">
                  <div class="path-dot path-dot--end"></div>
                  <div class="path-content">
                    <p class="path-label">终点</p>
                    <p class="path-address">{{ route.end_address }}</p>
                  </div>
                </div>
              </div>

              <div class="departures-section">
                <div class="section-header">
                  <h4>今日班次</h4>
                  <span class="count-badge">{{ getDepartures(route).length }} 班</span>
                </div>

                <div v-if="getDepartures(route).length" class="departure-list">
                  <div
                    v-for="(departure, idx) in getDepartures(route)"
                    :key="`${departure.time}-${idx}`"
                    :class="['departure-item', { 'is-first': idx === 0 }]"
                  >
                    <time class="departure-time">{{ departure.time }}</time>
                    <div class="departure-info">
                      <div v-if="departure.season || departure.vehicle_count" class="departure-meta">
                        <n-tag v-if="departure.season" type="info" size="small">{{ departure.season }}</n-tag>
                        <span v-if="departure.vehicle_count" class="vehicle-badge">🚌 {{ departure.vehicle_count }} 辆</span>
                      </div>
                      <span v-if="idx === 0" class="badge-first">首班车</span>
                    </div>
                  </div>
                </div>
                <n-empty v-else description="暂无班次" />
              </div>

              <div v-if="route.remark" class="remark-box">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 11h14M12 3v18" />
                </svg>
                <span>{{ route.remark }}</span>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>

        <div class="info-banner">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 16v-4M12 8h.01" fill="white" />
          </svg>
          时刻可能因季节性作息调整，请以现场通知为准
        </div>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
interface Departure { time: string; season?: string | null; vehicle_count?: number | null }
interface LegacyStop { name: string; time: string }
interface CommuteRoute {
  id: number;
  route_name: string;
  start_address: string;
  end_address: string;
  departures?: Departure[];
  route_stops?: LegacyStop[];
  remark?: string | null;
}

const http = useHttp();
const routes = ref<CommuteRoute[]>([]);
const loading = ref(true);
const errorMessage = ref("");
const lastUpdated = ref("--:--");
const now = new Date();
const today = {
  iso: now.toISOString().slice(0, 10),
  label: new Intl.DateTimeFormat("zh-CN", {month: "long", day: "numeric", weekday: "short"}).format(now),
};

function getDepartures(route: CommuteRoute): Departure[] {
  const departures = route.departures?.length ? route.departures : (route.route_stops || []).map((stop) => ({time: stop.time}));
  return [...departures].sort((left, right) => left.time.localeCompare(right.time));
}

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<{ items: CommuteRoute[] }>("/commute-routes/public");
    routes.value = result.items || [];
    lastUpdated.value = new Intl.DateTimeFormat("zh-CN", {hour: "2-digit", minute: "2-digit", hour12: false}).format(new Date());
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "发车表暂时无法获取，请稍后重试";
  } finally {
    loading.value = false;
  }
}

useHead({title: "发车表"});
onMounted(load);
</script>

<style scoped>
.commute-routes {
  background: #f5f5f5;
  min-height: 100vh;
  padding-top: 60px;
}

.app-header {
  background: #fff;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eee;
}

.app-header h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #222;
}

.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
}

.refresh-btn:hover {
  color: #333;
}

:deep(.header-time.n-alert) {
  margin: 0;
  padding: 8px 16px;
  font-size: 12px;
  border: none;
  border-radius: 0;
}

.routes-container {
  padding: 16px;
  padding-bottom: 32px;
  max-width: 800px;
  margin: 0 auto;
}

.state-box {
  background: #fff;
  border-radius: 8px;
  padding: 40px 16px;
  text-align: center;
  margin-top: 20px;
}

.error-detail {
  color: #d32f2f;
  font-size: 12px;
  margin: 12px 0;
}

:deep(.n-spin) {
  justify-content: center;
  padding: 40px;
}

:deep(.n-collapse) {
  margin-bottom: 16px;
  border-radius: 8px;
  overflow: hidden;
}

:deep(.n-collapse-item) {
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid #eee;
}

:deep(.n-collapse-item__header) {
  padding: 16px;
  font-weight: 600;
  color: #222;
}

.route-card {
  padding: 16px;
}

.route-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.route-header h3 {
  margin: 0;
  font-size: 18px;
  color: #222;
}

.route-path-card {
  background: #f9f9f9;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 20px;
}

.path-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  position: relative;
}

.path-item:last-child {
  margin-bottom: 0;
}

.path-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #ff9500;
  background: #fff;
  flex-shrink: 0;
  margin-top: 4px;
}

.path-dot--end {
  background: #222;
  border-color: #222;
}

.path-line {
  height: 16px;
  width: 1px;
  background: #ddd;
  margin-left: 5px;
  display: block;
}

.path-content {
  flex: 1;
  min-width: 0;
}

.path-label {
  font-size: 11px;
  color: #999;
  margin: 0 0 4px;
  text-transform: uppercase;
}

.path-address {
  font-size: 14px;
  color: #222;
  margin: 0;
  word-wrap: break-word;
  white-space: normal;
}

.departures-section {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.section-header h4 {
  margin: 0;
  font-size: 16px;
  color: #222;
}

.count-badge {
  background: #ff9500;
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.departure-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.departure-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 6px;
  border-left: 3px solid #ddd;
}

.departure-item.is-first {
  border-left-color: #ff9500;
  background: #fffaf0;
}

.departure-time {
  font-size: 18px;
  font-weight: 600;
  color: #222;
  min-width: 60px;
  font-family: monospace;
}

.departure-item.is-first .departure-time {
  color: #ff9500;
}

.departure-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.departure-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.vehicle-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: #666;
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
}

.badge-first {
  color: #ff9500;
  font-size: 12px;
  font-weight: 600;
}

.remark-box {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 6px;
  border-left: 3px solid #ff9500;
  font-size: 13px;
  color: #666;
  margin-top: 12px;
}

.remark-box svg {
  margin-top: 2px;
  flex-shrink: 0;
  color: #ff9500;
}

.info-banner {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px;
  background: #fef3e3;
  border-radius: 6px;
  border-left: 3px solid #ff9500;
  font-size: 12px;
  color: #666;
  margin-top: 16px;
}

.info-banner svg {
  flex-shrink: 0;
}

:deep(.n-empty) {
  padding: 24px 16px;
}
</style>