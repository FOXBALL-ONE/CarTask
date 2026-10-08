<template>
  <main class="schedule-page">
    <header class="schedule-header">
      <div class="schedule-header__brand">
        <span class="material-icons-outlined schedule-header__icon">directions_bus</span>
        <div>
          <p>福清市车务管理系统</p>
          <h1>发车表</h1>
        </div>
      </div>
      <button class="header-action" title="刷新发车表" type="button" @click="load">
        <span class="material-icons-outlined">refresh</span>
      </button>
    </header>

    <section class="schedule-content" aria-live="polite">
      <div class="schedule-titlebar">
        <div>
          <p class="eyebrow">SHUTTLE SCHEDULE</p>
          <h2>查询线路</h2>
        </div>
      </div>

      <p v-if="errorMessage" class="state state--error">
        <span class="material-icons-outlined">error_outline</span>{{ errorMessage }}
      </p>
      <p v-else-if="loading" class="state">
        <span class="material-icons-outlined spin">progress_activity</span>正在加载发车表...
      </p>
      <p v-else-if="routes.length === 0" class="state">
        <span class="material-icons-outlined">directions_bus</span>暂未发布发车线路
      </p>
      <template v-else>
        <label class="route-picker">
          <span class="route-picker__label">选择线路</span>
          <span class="route-picker__control">
            <select v-model="selectedRouteId" aria-label="选择发车线路">
              <option v-for="route in routes" :key="route.id" :value="route.id">{{ route.route_name }}</option>
            </select>
            <span class="material-icons-outlined">expand_more</span>
          </span>
        </label>

        <section v-if="selectedRoute" class="schedule-card">
          <header class="schedule-card__header">
            <div>
              <p class="schedule-card__caption">点对点发车</p>
              <h3>{{ selectedRoute.start_address }}<span class="material-icons-outlined">arrow_forward</span>{{ selectedRoute.end_address }}</h3>
            </div>
            <span class="schedule-card__route-name">{{ selectedRoute.route_name }}</span>
          </header>

          <p class="schedule-notice">
            <span class="material-icons-outlined">info</span>
            <span>发车时刻表（按季节性作息时间调整）</span>
          </p>

          <ol v-if="selectedDepartures.length" class="departure-list">
            <li v-for="(departure, index) in selectedDepartures" :key="`${departure.time}-${index}`" class="departure">
              <span class="departure__rail" aria-hidden="true">
                <span :class="{'departure__dot--active': index === 0}" class="departure__dot">
                  <span v-if="index === 0" class="material-icons-outlined">check</span>
                </span>
              </span>
              <div class="departure__body">
                <div class="departure__route">
                  <span>{{ selectedRoute.start_address }}</span>
                  <span class="material-icons-outlined">arrow_forward</span>
                  <span>{{ selectedRoute.end_address }}</span>
                  <span v-if="departure.season" class="season">{{ departure.season }}</span>
                </div>
                <div class="departure__meta">
                  <time>{{ departure.time }}</time>
                  <span v-if="departure.vehicle_count" class="vehicle-count">{{ departure.vehicle_count }}辆</span>
                </div>
              </div>
            </li>
          </ol>
          <p v-else class="empty-schedule">当前线路暂无发车时刻</p>

          <p v-if="selectedRoute.remark" class="schedule-remark">
            <span class="material-icons-outlined">notes</span>{{ selectedRoute.remark }}
          </p>
        </section>
      </template>
    </section>
  </main>
</template>

<script lang="ts" setup>
interface Departure {
  time: string;
  season?: string | null;
  vehicle_count?: number | null;
}

interface LegacyStop {
  name: string;
  time: string;
}

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
const selectedRouteId = ref<number | null>(null);
const loading = ref(true);
const errorMessage = ref("");

const selectedRoute = computed(() => routes.value.find((route) => route.id === selectedRouteId.value) || routes.value[0] || null);
const selectedDepartures = computed(() => {
  const route = selectedRoute.value;
  if (!route) {
    return [];
  }
  const departures = route.departures?.length
    ? route.departures
    : (route.route_stops || []).map((stop) => ({time: stop.time}));
  return [...departures].sort((left, right) => left.time.localeCompare(right.time));
});

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<{ items: CommuteRoute[] }>("/commute-routes/public");
    routes.value = result.items || [];
    if (!routes.value.some((route) => route.id === selectedRouteId.value)) {
      selectedRouteId.value = routes.value[0]?.id ?? null;
    }
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
.schedule-page {
  min-height: 100dvh;
  background: #f7f7f7;
  color: #222;
}

.schedule-header {
  align-items: center;
  background: #171717;
  color: #fff;
  display: flex;
  justify-content: space-between;
  min-height: 86px;
  padding: 0 max(20px, calc((100% - 920px) / 2));
}

.schedule-header__brand {
  align-items: center;
  display: flex;
  gap: 12px;
}

.schedule-header__icon {
  align-items: center;
  border: 1px solid rgb(255 255 255 / 30%);
  border-radius: 6px;
  color: #fff;
  display: flex;
  font-size: 25px;
  height: 44px;
  justify-content: center;
  width: 44px;
}

.schedule-header p {
  color: rgb(255 255 255 / 66%);
  font-size: 12px;
  margin: 0 0 2px;
}

.schedule-header h1 {
  font-size: 21px;
  font-weight: 600;
  margin: 0;
}

.header-action {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: rgb(255 255 255 / 80%);
  cursor: pointer;
  display: inline-flex;
  height: 40px;
  justify-content: center;
  width: 40px;
}

.header-action:hover {
  background: rgb(255 255 255 / 12%);
  color: #fff;
}

.schedule-content {
  margin: 0 auto;
  max-width: 920px;
  padding: 30px 24px 54px;
}

.schedule-titlebar {
  align-items: end;
  background: #d8170d;
  color: #fff;
  display: flex;
  margin: -30px -24px 24px;
  min-height: 76px;
  padding: 0 24px;
}

.schedule-titlebar h2 {
  font-size: 24px;
  font-weight: 650;
  margin: 0 0 18px;
}

.eyebrow {
  display: none;
}

.route-picker {
  background: #fff;
  border: 1px solid #dedede;
  display: block;
  margin-bottom: 12px;
  padding: 12px 16px;
}

.route-picker__label {
  color: #777;
  display: block;
  font-size: 12px;
  margin-bottom: 5px;
}

.route-picker__control {
  align-items: center;
  display: flex;
  gap: 5px;
}

.route-picker select {
  appearance: none;
  background: transparent;
  border: 0;
  color: #333;
  cursor: pointer;
  flex: 1;
  font: inherit;
  font-size: 17px;
  min-width: 0;
  outline: 0;
  padding: 0;
}

.route-picker .material-icons-outlined {
  color: #888;
  font-size: 21px;
  pointer-events: none;
}

.schedule-card {
  background: #fff;
  border: 1px solid #e2e2e2;
}

.schedule-card__header {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 20px 20px 16px;
}

.schedule-card__caption {
  color: #888;
  font-size: 12px;
  margin: 0 0 6px;
}

.schedule-card h3 {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  font-size: 20px;
  font-weight: 600;
  gap: 5px;
  line-height: 1.35;
  margin: 0;
}

.schedule-card h3 .material-icons-outlined,
.departure__route .material-icons-outlined {
  color: #b0b0b0;
  font-size: 19px;
}

.schedule-card__route-name {
  color: #888;
  font-size: 12px;
  max-width: 180px;
  text-align: right;
}

.schedule-notice {
  align-items: center;
  background: #eaf8ff;
  color: #2a80ad;
  display: flex;
  font-size: 14px;
  gap: 8px;
  line-height: 1.5;
  margin: 0;
  padding: 13px 20px;
}

.schedule-notice .material-icons-outlined {
  font-size: 20px;
}

.departure-list {
  list-style: none;
  margin: 0;
  padding: 8px 20px 8px;
}

.departure {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  min-height: 88px;
}

.departure__rail {
  position: relative;
}

.departure__rail::after {
  background: #e6e6e6;
  bottom: 0;
  content: "";
  left: 11px;
  position: absolute;
  top: 0;
  width: 1px;
}

.departure:last-child .departure__rail::after {
  bottom: 50%;
}

.departure__dot {
  align-items: center;
  background: #a8a8a8;
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  display: flex;
  height: 12px;
  justify-content: center;
  left: 5px;
  position: absolute;
  top: 25px;
  width: 12px;
  z-index: 1;
}

.departure__dot--active {
  background: #22b873;
  height: 18px;
  left: 2px;
  top: 22px;
  width: 18px;
}

.departure__dot .material-icons-outlined {
  font-size: 12px;
}

.departure__body {
  border-bottom: 1px solid #ededed;
  min-width: 0;
  padding: 16px 0 14px 16px;
}

.departure:last-child .departure__body {
  border-bottom: 0;
}

.departure__route {
  align-items: center;
  color: #666;
  display: flex;
  flex-wrap: wrap;
  font-size: 16px;
  gap: 4px;
  line-height: 1.4;
}

.departure:first-child .departure__route {
  color: #20a86b;
}

.season,
.vehicle-count {
  color: #888;
  font-size: 12px;
  margin-left: 5px;
}

.season {
  color: #888;
}

.departure__meta {
  align-items: baseline;
  display: flex;
  gap: 12px;
  margin-top: 7px;
}

.departure time {
  color: #888;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

.departure:first-child time {
  color: #20a86b;
}

.vehicle-count {
  margin-left: 0;
}

.schedule-remark {
  align-items: flex-start;
  border-top: 1px solid #ededed;
  color: #777;
  display: flex;
  font-size: 12px;
  gap: 7px;
  line-height: 1.55;
  margin: 0 20px;
  padding: 14px 0 16px;
}

.schedule-remark .material-icons-outlined {
  color: #999;
  font-size: 16px;
}

.state {
  align-items: center;
  background: #fff;
  border: 1px solid #e2e2e2;
  color: #999;
  display: flex;
  flex-direction: column;
  font-size: 13px;
  gap: 10px;
  justify-content: center;
  min-height: 200px;
}

.state .material-icons-outlined {
  font-size: 28px;
}

.state--error {
  color: #c62828;
}

.empty-schedule {
  color: #999;
  font-size: 13px;
  padding: 28px 20px;
  text-align: center;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 600px) {
  .schedule-header {
    min-height: 78px;
    padding: 0 16px;
  }

  .schedule-header__icon {
    font-size: 22px;
    height: 38px;
    width: 38px;
  }

  .schedule-header h1 {
    font-size: 19px;
  }

  .schedule-content {
    padding: 24px 0 36px;
  }

  .schedule-titlebar {
    margin: -24px 0 16px;
    min-height: 64px;
    padding: 0 16px;
  }

  .schedule-titlebar h2 {
    font-size: 22px;
    margin-bottom: 15px;
  }

  .route-picker {
    margin: 0 12px 10px;
  }

  .schedule-card {
    border-left: 0;
    border-right: 0;
  }

  .schedule-card__header {
    padding: 18px 16px 14px;
  }

  .schedule-card h3 {
    font-size: 18px;
  }

  .schedule-card__route-name {
    max-width: 120px;
  }

  .schedule-notice {
    padding: 12px 16px;
  }

  .departure-list {
    padding-left: 16px;
    padding-right: 16px;
  }

  .departure__route {
    font-size: 15px;
  }
}
</style>
