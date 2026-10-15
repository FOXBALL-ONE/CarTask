<template>
  <div id="app" class="main">
    <div class="van-nav-bar__placeholder" style="height: 46px;">
      <div class="van-nav-bar van-nav-bar--fixed van-hairline--bottom">
        <div class="van-nav-bar__content">
          <div class="van-nav-bar__title van-ellipsis">查询线路</div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-container">
      <div class="van-loading">
        <span class="van-loading__spinner van-loading__spinner--circular">
          <svg viewBox="25 25 50 50" class="van-loading__circular">
            <circle cx="50" cy="50" r="20" fill="none"></circle>
          </svg>
        </span>
        <span class="van-loading__text">正在同步发车时间...</span>
      </div>
    </div>

    <div v-else-if="errorMessage" class="error-container">
      <div class="van-empty">
        <div class="van-empty__image">
          <svg viewBox="0 0 160 160"><path d="M80 0C35.8 0 0 35.8 0 80s35.8 80 80 80 80-35.8 80-80S124.2 0 80 0zm0 140c-33.1 0-60-26.9-60-60s26.9-60 60-60 60 26.9 60 60-26.9 60-60 60z" fill="#DCDEE0"/><path d="M57.1 47.1L80 70l22.9-22.9c1.6-1.6 4.1-1.6 5.7 0s1.6 4.1 0 5.7L85.7 75.7l22.9 22.9c1.6 1.6 1.6 4.1 0 5.7-.8.8-1.8 1.2-2.8 1.2s-2.1-.4-2.8-1.2L80 81.4 57.1 104.3c-.8.8-1.8 1.2-2.8 1.2s-2.1-.4-2.8-1.2c-1.6-1.6-1.6-4.1 0-5.7l22.9-22.9-22.9-22.9c-1.6-1.6-1.6-4.1 0-5.7s4.1-1.6 5.6 0z" fill="#969799"/></svg>
        </div>
        <p class="van-empty__description">加载失败</p>
      </div>
      <p style="text-align: center; color: #ee0a24; font-size: 12px; margin: 12px 0;">{{ errorMessage }}</p>
      <div style="text-align: center;">
        <button class="van-button van-button--danger van-button--small" @click="load">
          <div class="van-button__content"><span class="van-button__text">重新加载</span></div>
        </button>
      </div>
    </div>

    <div v-else-if="routes.length === 0" class="empty-container">
      <div class="van-empty">
        <div class="van-empty__image">
          <svg viewBox="0 0 160 160"><path d="M80 0C35.8 0 0 35.8 0 80s35.8 80 80 80 80-35.8 80-80S124.2 0 80 0zm0 140c-33.1 0-60-26.9-60-60s26.9-60 60-60 60 26.9 60 60-26.9 60-60 60z" fill="#DCDEE0"/></svg>
        </div>
        <p class="van-empty__description">暂未发布发车线路</p>
      </div>
    </div>

    <div v-else class="van-collapse van-hairline--top-bottom">
      <div
        v-for="route in routes"
        :key="route.id"
        class="van-collapse-item"
        :class="{ 'van-collapse-item--border': !isExpanded(route.id) }"
      >
        <div
          role="button"
          tabindex="0"
          :aria-expanded="isExpanded(route.id)"
          class="van-cell van-cell--clickable van-collapse-item__title"
          :class="{ 'van-collapse-item__title--expanded': isExpanded(route.id) }"
          @click="toggleRoute(route.id)"
        >
          <div class="van-cell__title">
            <span>{{ route.route_name }}</span>
          </div>
          <i class="van-icon van-icon-arrow van-cell__right-icon"><!-- --></i>
        </div>
        <div
          class="van-collapse-item__wrapper"
          :style="getWrapperStyle(route.id)"
        >
          <div class="van-collapse-item__content">
            <div style="padding: 0px;">
              <div
                v-if="route.remark"
                role="alert"
                class="van-notice-bar"
                style="color: rgb(25, 137, 250); background: rgb(236, 249, 255);"
              >
                <i class="van-icon van-icon-info-o van-notice-bar__left-icon"><!-- --></i>
                <div role="marquee" class="van-notice-bar__wrap">
                  <div class="van-notice-bar__content"> {{ route.remark }} </div>
                </div>
              </div>
              <div class="van-steps van-steps--vertical">
                <div class="van-steps__items">
                  <div
                    v-for="(departure, idx) in getDepartures(route)"
                    :key="`${departure.time}-${idx}`"
                    class="van-hairline van-step van-step--vertical"
                    :class="{ 'van-step--process': !isDeparturePassed(departure.time) }"
                  >
                    <div
                      class="van-step__title"
                      :class="{ 'van-step__title--active': !isDeparturePassed(departure.time) }"
                    >
                      <h3>{{ formatDepartureName(departure, route) }}</h3>
                      <p>{{ departure.time }}</p>
                    </div>
                    <div class="van-step__circle-container">
                      <i
                        v-if="!isDeparturePassed(departure.time)"
                        class="van-icon van-icon-checked van-step__icon van-step__icon--active"
                      ><!-- --></i>
                      <i v-else class="van-step__circle"></i>
                    </div>
                    <div class="van-step__line"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
interface Departure {
  time: string;
  season?: string | null;
  vehicle_count?: number | null
}

interface LegacyStop {
  name: string;
  time: string
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
const loading = ref(true);
const errorMessage = ref("");
const expandedRoutes = ref<Set<number>>(new Set());
const currentTime = ref<string>('');

function getDepartures(route: CommuteRoute): Departure[] {
  const departures = route.departures?.length ? route.departures : (route.route_stops || []).map((stop) => ({time: stop.time}));
  return [...departures].sort((left, right) => left.time.localeCompare(right.time));
}

function formatDepartureName(departure: Departure, route: CommuteRoute): string {
  const parts = [route.start_address, route.end_address].filter(Boolean);
  const baseName = parts.join('→');

  const extras: string[] = [];
  if (departure.season) {
    extras.push(`（${departure.season}）`);
  }
  if (departure.vehicle_count) {
    extras.push(`${departure.vehicle_count}辆`);
  }

  return baseName + (extras.length ? extras.join('') : '');
}

function isExpanded(routeId: number): boolean {
  return expandedRoutes.value.has(routeId);
}

function toggleRoute(routeId: number) {
  if (expandedRoutes.value.has(routeId)) {
    expandedRoutes.value.delete(routeId);
  } else {
    expandedRoutes.value.clear();
    expandedRoutes.value.add(routeId);
  }
}

function getWrapperStyle(routeId: number): Record<string, string> {
  return isExpanded(routeId)
    ? { display: '', height: 'auto' }
    : { display: 'none', height: '0px' };
}

function isDeparturePassed(time: string): boolean {
  if (!currentTime.value) return false;
  return time < currentTime.value;
}

function updateCurrentTime() {
  const now = new Date();
  currentTime.value = now.toTimeString().slice(0, 5);
}

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<{ items: CommuteRoute[] }>("/commute-routes/public");
    routes.value = result.items || [];
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "发车表暂时无法获取，请稍后重试";
  } finally {
    loading.value = false;
  }
}

useHead({title: "查询线路"});
onMounted(() => {
  load();
  updateCurrentTime();
  setInterval(updateCurrentTime, 60000);
});
</script>

<style scoped>
/* 全局重置 */
#app.main {
  height: 100%;
  margin: 0;
  padding: 0;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif;
  font-size: 16px;
  color: #323233;
  -webkit-font-smoothing: antialiased;
}

/* Vant 基础样式 */
.van-hairline:after,
.van-hairline--top-bottom:after {
  position: absolute;
  box-sizing: border-box;
  content: " ";
  pointer-events: none;
  top: -50%;
  right: -50%;
  bottom: -50%;
  left: -50%;
  border: 0 solid #ebedf0;
  transform: scale(0.5);
}

.van-hairline--top-bottom:after {
  border-width: 1px 0;
}

.van-hairline--bottom:after {
  border-bottom-width: 1px;
}

/* 导航栏 */
.van-nav-bar {
  position: relative;
  z-index: 1;
  line-height: 22px;
  text-align: center;
  background-color: #be0f05;
  user-select: none;
}

.van-nav-bar--fixed {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
}

.van-nav-bar__content {
  position: relative;
  display: flex;
  align-items: center;
  height: 46px;
}

.van-nav-bar__title {
  max-width: 60%;
  margin: 0 auto;
  color: #fff;
  font-weight: 500;
  font-size: 16px;
}

.van-ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 加载/错误/空状态 */
.loading-container,
.error-container,
.empty-container {
  padding: 40px 16px;
  text-align: center;
}

.van-loading {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  color: #c8c9cc;
  font-size: 14px;
}

.van-loading__spinner {
  position: relative;
  box-sizing: border-box;
  width: 30px;
  height: 30px;
  animation: van-rotate 0.8s linear infinite;
}

.van-loading__spinner--circular {
  animation: van-rotate 1s linear infinite;
}

.van-loading__circular {
  animation: van-circular 1.5s ease-in-out infinite;
}

.van-loading__circular circle {
  stroke: currentColor;
  stroke-width: 3;
  stroke-linecap: round;
  animation: van-circular-dash 1.5s ease-in-out infinite;
}

@keyframes van-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes van-circular-dash {
  0% {
    stroke-dasharray: 1, 200;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -40;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -120;
  }
}

.van-loading__text {
  margin-top: 8px;
  color: #969799;
}

.van-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
}

.van-empty__image {
  width: 160px;
  height: 160px;
}

.van-empty__image svg {
  width: 100%;
  height: 100%;
}

.van-empty__description {
  margin-top: 16px;
  padding: 0 60px;
  color: #969799;
  font-size: 14px;
  line-height: 20px;
}

.van-button {
  position: relative;
  display: inline-block;
  box-sizing: border-box;
  height: 44px;
  margin: 0;
  padding: 0;
  font-size: 16px;
  line-height: 1.2;
  text-align: center;
  border-radius:0 0;
  cursor: pointer;
  transition: opacity 0.2s;
  appearance: none;
  border: none;
}

.van-button--small {
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
}

.van-button--danger {
  color: #fff;
  background-color: #ee0a24;
  border: 1px solid #ee0a24;
}

.van-button:active:before {
  opacity: 0.1;
}

.van-button:before {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  background-color: #000;
  border: inherit;
  border-color: #000;
  border-radius:0 0;
  transform: translate(-50%, -50%);
  opacity: 0;
  content: " ";
}

.van-button__content {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

/* 折叠面板 */
.van-collapse {
  background-color: #fff;
}

.van-collapse-item {
  position: relative;
}

.van-collapse-item--border:after {
  position: absolute;
  box-sizing: border-box;
  content: " ";
  pointer-events: none;
  right: 16px;
  bottom: 0;
  left: 16px;
  border-bottom: 1px solid #ebedf0;
  transform: scaleY(0.5);
}

.van-cell {
  position: relative;
  display: flex;
  box-sizing: border-box;
  width: 100%;
  padding: 10px 16px;
  overflow: hidden;
  color: #323233;
  font-size: 14px;
  line-height: 24px;
  background-color: #fff;
}

.van-cell--clickable {
  cursor: pointer;
}

.van-cell--clickable:active {
  background-color: #f2f3f5;
}

.van-collapse-item__title {
  user-select: none;
}

.van-cell__title {
  flex: 1;
}

.van-cell__right-icon {
  margin-left: 4px;
  color: #969799;
  font-size: 16px;
  line-height: 24px;
  transition: transform 0.3s;
}

.van-collapse-item__title--expanded .van-cell__right-icon {
  transform: rotate(90deg);
}

.van-icon {
  position: relative;
  font: normal normal normal 14px/1 vant-icon;
  font-size: inherit;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
  display: inline-block;
}

.van-icon:before {
  display: inline-block;
}

.van-icon-arrow:before {
  content: "\e660";
}

.van-icon-info-o:before {
  content: "\e6d1";
}

.van-icon-volume-o:before {
  content: "\e73c";
}

.van-icon-checked:before {
  content: "\e68d";
}

@font-face {
  font-weight: 400;
  font-family: vant-icon;
  font-style: normal;
  font-display: auto;
  src: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg'/%3E") format("woff2");
}

.van-collapse-item__wrapper {
  overflow: hidden;
  transition: height 0.3s ease-in-out;
}

.van-collapse-item__content {
  padding: 12px 16px;
  color: #969799;
  font-size: 14px;
  line-height: 1.5;
  background-color: #fff;
}

/* 通知栏 */
.van-notice-bar {
  position: relative;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  color: #ed6a0c;
  font-size: 14px;
  line-height: 24px;
  background-color: #fffbe8;
}

.van-notice-bar__left-icon {
  min-width: 24px;
  font-size: 16px;
}

.van-notice-bar__wrap {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  height: 100%;
  overflow: hidden;
}

.van-notice-bar__content {
  position: relative;
  white-space: nowrap;
}

/* 步骤条 */
.van-steps {
  overflow: hidden;
  background-color: #fff;
}

.van-steps--vertical {
  padding: 0 0 0 32px;
}

.van-step {
  position: relative;
  flex: 1;
  color: #969799;
  font-size: 14px;
}

.van-step--vertical {
  display: block;
  float: none;
  padding: 10px 10px 10px 0;
  line-height: 18px;
}

.van-step--vertical:not(:last-child):after {
  border-bottom-width: 1px;
}

.van-step__title {
  position: relative;
  display: inline-block;
  word-break: break-all;
}

.van-step__title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: normal;
  line-height: 18px;
}

.van-step__title p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #969799;
}

.van-step--vertical .van-step__circle-container {
  position: absolute;
  top: 19px;
  left: -15px;
  z-index: 1;
  font-size: 12px;
  line-height: 1;
  transform: translate(-50%, -50%);
}

.van-step__circle {
  display: block;
  width: 5px;
  height: 5px;
  background-color: #969799;
  border-radius:0 0;
}

.van-step--vertical .van-step__line {
  top: 16px;
  left: -15px;
  width: 1px;
  height: 100%;
}

.van-step__line {
  position: absolute;
  background-color: #ebedf0;
  transition: background-color 0.3s;
}

.van-step:last-child .van-step__line {
  width: 0;
}

.van-step--process {
  color: #323233;
}

.van-step__title--active {
  color: #07c160;
}

.van-step__icon {
  display: block;
  font-size: 12px;
}

.van-step__icon--active {
  color: #07c160;
}

</style>