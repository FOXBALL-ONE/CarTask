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
          <span class="stat-card__delta" :class="stat.trend">{{ stat.delta }}</span>
        </article>
      </div>

      <div class="dash-grid">
        <article class="card chart-card">
          <div class="chart-card__head">
            <div class="chart-card__title">可停区域</div>
            <div class="chart-legend">
              <span class="legend__item"><span class="legend__dot" style="background: var(--primary);" />已用</span>
              <span class="legend__item"><span class="legend__dot" style="background: #e4e4e7;" />空闲</span>
            </div>
          </div>
          <div v-if="parkingAreas.length" class="bars">
            <div v-for="area in parkingAreas" :key="area.area" class="bar-col">
              <div class="bar-stack">
                <div class="bar-seg bar-used" :style="{ height: `${usedHeight(area)}%` }" />
                <div class="bar-seg bar-free" :style="{ height: `${freeHeight(area)}%` }" />
                <div class="bar-hover" @mouseenter="showBarTip(area, $event)" @mousemove="moveTip" @mouseleave="hideTip" />
              </div>
              <div class="bar-label">{{ area.area }}</div>
            </div>
          </div>
          <p v-else class="empty-state">暂无车位数据</p>
        </article>

        <article class="card chart-card">
          <div class="chart-card__head">
            <div class="chart-card__title">违规类型</div>
          </div>
          <div v-if="donutSlices.length" class="donut-layout">
            <div class="donut-legend">
              <div v-for="(slice, index) in donutSlices" :key="slice.name" class="legend-row" :class="{ 'legend-row--bordered': index > 0 }">
                <span class="legend__dot legend__dot--round" :style="{ background: slice.color }" />
                <span class="legend-row__name">{{ slice.name }}</span>
                <strong>{{ slice.value }}</strong>
                <span class="legend-row__unit">次</span>
              </div>
            </div>
            <div class="donut-svg">
              <svg viewBox="0 0 180 180" style="width: 180px; height: 180px; display: block;" role="img" aria-label="违规类型占比">
                <path v-for="slice in donutSlices" :key="`arc-${slice.name}`" :d="slice.path" :fill="slice.color" stroke="var(--card)" stroke-width="2" />
                <text x="90" y="85" text-anchor="middle" font-size="24" font-weight="700" fill="var(--text)">{{ violationTotal }}</text>
                <text x="90" y="105" text-anchor="middle" font-size="11" fill="var(--text-sub)">违规总数</text>
              </svg>
            </div>
          </div>
          <p v-else class="empty-state">暂无违规记录</p>
        </article>
      </div>

      <div class="dash-grid dash-grid--trends">
        <article class="card chart-card">
          <div class="chart-card__head">
            <div class="chart-card__title">违规数量趋势</div>
            <div v-if="violationTrend" class="chart-legend">
              <span v-for="series in violationTrend.series" :key="series.name" class="legend__item">
                <span class="legend__dot legend__dot--round" :style="{ background: series.color }" />{{ series.name }}
              </span>
            </div>
          </div>
          <div class="line-chart">
            <canvas ref="violationCanvas" class="line-canvas" @mousemove="handleLineHover" @mouseleave="hideTip" />
          </div>
        </article>

        <article class="card chart-card">
          <div class="chart-card__head">
            <div class="chart-card__title">出入数量趋势</div>
            <div v-if="inoutTrend" class="chart-legend">
              <span v-for="series in inoutTrend.series" :key="series.name" class="legend__item">
                <span class="legend__dot legend__dot--round" :style="{ background: series.color }" />{{ series.name }}
              </span>
            </div>
          </div>
          <div class="line-chart">
            <canvas ref="inoutCanvas" class="line-canvas" @mousemove="handleLineHover" @mouseleave="hideTip" />
          </div>
        </article>
      </div>

      <div v-show="tip.visible" class="chart-tip" :style="{ left: `${tip.x}px`, top: `${tip.y}px` }" aria-hidden="true">
        <div class="chart-tip__title">{{ tip.title }}</div>
        <div v-for="item in tip.items" :key="item.name" class="chart-tip__row">
          <span :style="item.color ? { color: item.color } : undefined">{{ item.name }}</span>
          <b :style="item.color ? { color: item.color } : undefined">{{ item.value }}</b>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
interface Stat {
  label: string;
  value: number;
  delta: string;
  trend: "up" | "down" | "flat";
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

interface TrendSeries {
  name: string;
  data: number[];
  color: string;
}

interface Trend {
  labels: string[];
  series: TrendSeries[];
}

interface Dashboard {
  stats: Stat[];
  parking: ParkingArea[];
  violationTypes: ViolationType[];
  violationTrend?: Trend;
  inoutTrend?: Trend;
}

interface TipItem {
  name: string;
  value: number | string;
  color?: string;
}

interface TipState {
  visible: boolean;
  x: number;
  y: number;
  title: string;
  items: TipItem[];
}

interface ChartPoint {
  x: number;
  y: number;
  val: number;
  label: string;
}

interface ChartLayout {
  series: TrendSeries[];
  coords: ChartPoint[][];
}

const DONUT_RADIUS = 70;
const DONUT_INNER_RADIUS = 45;
const DONUT_CENTER = 90;
const FREE_TIP_COLOR = "#16a34a";

const http = useHttp();
const dashboard = ref<Dashboard>();
const loading = ref(true);
const loadError = ref("");
const violationCanvas = ref<HTMLCanvasElement | null>(null);
const inoutCanvas = ref<HTMLCanvasElement | null>(null);
const tip = ref<TipState>({ visible: false, x: 0, y: 0, title: "", items: [] });
const chartLayouts = new WeakMap<HTMLCanvasElement, ChartLayout>();

const parkingAreas = computed(() => dashboard.value?.parking ?? []);
const parkingMax = computed(() => Math.max(0, ...parkingAreas.value.map((area) => area.total)));
const violationTotal = computed(() => (dashboard.value?.violationTypes ?? []).reduce((sum, item) => sum + item.value, 0));
const violationTrend = computed(() => dashboard.value?.violationTrend);
const inoutTrend = computed(() => dashboard.value?.inoutTrend);

const donutSlices = computed(() => {
  const types = dashboard.value?.violationTypes ?? [];
  const total = violationTotal.value;
  if (!total) return [];
  const toRad = (degree: number) => (degree * Math.PI) / 180;
  let angle = -90;
  return types.map((item) => {
    // 单一类型时扇区接近整圆，避免起终点重合导致弧线无法绘制。
    const sweep = types.length === 1 ? 359.99 : (item.value / total) * 360;
    const startAngle = angle;
    const endAngle = angle + sweep;
    angle = endAngle;
    const x1 = DONUT_CENTER + DONUT_RADIUS * Math.cos(toRad(startAngle));
    const y1 = DONUT_CENTER + DONUT_RADIUS * Math.sin(toRad(startAngle));
    const x2 = DONUT_CENTER + DONUT_RADIUS * Math.cos(toRad(endAngle));
    const y2 = DONUT_CENTER + DONUT_RADIUS * Math.sin(toRad(endAngle));
    const x3 = DONUT_CENTER + DONUT_INNER_RADIUS * Math.cos(toRad(endAngle));
    const y3 = DONUT_CENTER + DONUT_INNER_RADIUS * Math.sin(toRad(endAngle));
    const x4 = DONUT_CENTER + DONUT_INNER_RADIUS * Math.cos(toRad(startAngle));
    const y4 = DONUT_CENTER + DONUT_INNER_RADIUS * Math.sin(toRad(startAngle));
    const largeArc = sweep > 180 ? 1 : 0;
    const path = `M${x1},${y1} A${DONUT_RADIUS},${DONUT_RADIUS} 0 ${largeArc} 1 ${x2},${y2} L${x3},${y3} A${DONUT_INNER_RADIUS},${DONUT_INNER_RADIUS} 0 ${largeArc} 0 ${x4},${y4} Z`;
    return { ...item, path };
  });
});

function usedHeight(area: ParkingArea) {
  return parkingMax.value ? (area.used / parkingMax.value) * 100 : 0;
}

function freeHeight(area: ParkingArea) {
  return parkingMax.value ? ((area.total - area.used) / parkingMax.value) * 100 : 0;
}

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

function drawLineChart(canvas: HTMLCanvasElement, series: TrendSeries[], labels: string[]) {
  const context = canvas.getContext("2d");
  if (!context || !series.length || !labels.length) return;
  const allData = series.flatMap((item) => item.data);
  const dataMax = Math.max(...allData);
  if (!Number.isFinite(dataMax)) return;

  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  context.scale(dpr, dpr);

  const width = rect.width;
  const height = rect.height;
  const padL = 36;
  const padR = 16;
  const padT = 16;
  const padB = 28;
  const chartWidth = width - padL - padR;
  const chartHeight = height - padT - padB;
  const maxVal = dataMax * 1.15 || 1;
  const minVal = 0;
  const count = labels.length;
  const xStep = chartWidth / Math.max(1, count - 1);
  const toX = (index: number) => padL + index * xStep;
  const toY = (value: number) => padT + chartHeight - ((value - minVal) / (maxVal - minVal)) * chartHeight;

  const computedStyle = getComputedStyle(document.documentElement);
  const borderColor = computedStyle.getPropertyValue("--border").trim() || "#f0f0f0";
  const textMuteColor = computedStyle.getPropertyValue("--text-mute").trim() || "#a1a1aa";

  context.strokeStyle = borderColor;
  context.lineWidth = 1;
  context.fillStyle = textMuteColor;
  context.font = "10px sans-serif";
  context.textAlign = "right";
  context.textBaseline = "middle";

  const yTicks = 4;
  for (let tick = 0; tick <= yTicks; tick++) {
    const value = Math.round(minVal + ((maxVal - minVal) * tick) / yTicks);
    const y = padT + chartHeight - (tick / yTicks) * chartHeight;
    context.beginPath();
    context.moveTo(padL, y);
    context.lineTo(width - padR, y);
    context.stroke();
    context.fillText(value.toString(), padL - 5, y);
  }

  for (let index = 0; index < count; index++) {
    const x = toX(index);
    context.beginPath();
    context.moveTo(x, padT);
    context.lineTo(x, padT + chartHeight);
    context.stroke();
  }

  context.textAlign = "center";
  context.textBaseline = "top";
  labels.forEach((label, index) => {
    if (index % 2 === 0) {
      context.fillText(label, toX(index), height - 18);
    }
  });

  const coords = series.map((item) => item.data.map((value, index) => ({ x: toX(index), y: toY(value), val: value, label: labels[index] ?? "" })));
  chartLayouts.set(canvas, { series, coords });

  series.forEach((item, seriesIndex) => {
    const points = coords[seriesIndex];
    const gradient = context.createLinearGradient(0, padT, 0, padT + chartHeight);
    gradient.addColorStop(0, `${item.color}4D`);
    gradient.addColorStop(1, `${item.color}03`);
    context.fillStyle = gradient;
    context.beginPath();
    context.moveTo(points[0].x, padT + chartHeight);
    points.forEach((point) => context.lineTo(point.x, point.y));
    context.lineTo(points[points.length - 1].x, padT + chartHeight);
    context.closePath();
    context.fill();

    context.strokeStyle = item.color;
    context.lineWidth = 2.5;
    context.lineJoin = "round";
    context.lineCap = "round";
    context.beginPath();
    points.forEach((point, index) => {
      if (index === 0) context.moveTo(point.x, point.y);
      else context.lineTo(point.x, point.y);
    });
    context.stroke();

    points.forEach((point) => {
      context.fillStyle = "#ffffff";
      context.strokeStyle = item.color;
      context.lineWidth = 2;
      context.beginPath();
      context.arc(point.x, point.y, 4, 0, Math.PI * 2);
      context.fill();
      context.stroke();
    });
  });
}

function redrawCharts() {
  const violation = violationTrend.value;
  if (violationCanvas.value && violation) drawLineChart(violationCanvas.value, violation.series, violation.labels);
  const inout = inoutTrend.value;
  if (inoutCanvas.value && inout) drawLineChart(inoutCanvas.value, inout.series, inout.labels);
}

function positionTip(event: MouseEvent) {
  tip.value.x = Math.min(event.clientX + 12, window.innerWidth - 160);
  tip.value.y = Math.max(10, event.clientY - 10);
}

function showBarTip(area: ParkingArea, event: MouseEvent) {
  tip.value = {
    visible: true,
    x: 0,
    y: 0,
    title: area.area,
    items: [
      { name: "总数", value: area.total },
      { name: "已用", value: area.used, color: "var(--primary)" },
      { name: "空闲", value: area.total - area.used, color: FREE_TIP_COLOR },
    ],
  };
  positionTip(event);
}

function handleLineHover(event: MouseEvent) {
  const canvas = event.currentTarget as HTMLCanvasElement;
  const layout = chartLayouts.get(canvas);
  if (!layout) return;
  const rect = canvas.getBoundingClientRect();
  const mouseX = event.clientX - rect.left;
  const mouseY = event.clientY - rect.top;
  let closestColumn = -1;
  let minDist = 15;
  layout.coords.forEach((seriesCoords) => {
    seriesCoords.forEach((point, columnIndex) => {
      const dist = Math.hypot(point.x - mouseX, point.y - mouseY);
      if (dist < minDist) {
        minDist = dist;
        closestColumn = columnIndex;
      }
    });
  });
  if (closestColumn < 0) {
    hideTip();
    return;
  }
  tip.value = {
    visible: true,
    x: 0,
    y: 0,
    title: layout.coords[0][closestColumn].label,
    items: layout.series.map((series, seriesIndex) => ({
      name: series.name,
      value: layout.coords[seriesIndex][closestColumn].val,
      color: series.color,
    })),
  };
  positionTip(event);
}

function moveTip(event: MouseEvent) {
  if (!tip.value.visible) return;
  positionTip(event);
}

function hideTip() {
  tip.value.visible = false;
}

function handleResize() {
  if (!loading.value && !loadError.value) redrawCharts();
}

watch(dashboard, () => {
  nextTick(redrawCharts);
});

onMounted(() => {
  void loadDashboard();
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});
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
.stat-card { border: 1px solid var(--border-strong); border-radius: 8px; color: #fff; min-height: 136px; overflow: hidden; padding: 20px; position: relative; transition: border-color var(--tr), transform var(--tr); }
.stat-card:hover { border-color: #d4d4d8; transform: translateY(-2px); }
.stat-card--blue { background: #3b82f6; }.stat-card--green { background: #10b981; }.stat-card--orange { background: #f59e0b; }.stat-card--red { background: #ef4444; }
.stat-card__value { display: block; font-size: 26px; font-weight: 700; line-height: 1; }
.stat-card__label { display: block; font-size: 13px; margin-top: 5px; opacity: .9; }
.stat-card__delta { background: rgb(255 255 255 / 20%); border-radius: 4px; font-size: 12px; font-weight: 600; padding: 2px 7px; position: absolute; right: 20px; top: 20px; }
.stat-card__delta.up { background: rgb(255 255 255 / 25%); }.stat-card__delta.down { background: rgb(0 0 0 / 15%); }
.dash-grid { display: grid; gap: 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.dash-grid--trends { margin-top: 16px; }
.card { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; overflow: hidden; }
.chart-card { padding: 20px; }
.chart-card__head { align-items: center; display: flex; justify-content: space-between; margin-bottom: 16px; }
.chart-card__title { color: var(--text); font-size: 14px; font-weight: 600; }
.chart-legend { align-items: center; display: flex; gap: 12px; }
.legend__item { align-items: center; color: var(--text-sub); display: flex; font-size: 12px; gap: 8px; }
.legend__dot { border-radius: 2px; flex-shrink: 0; height: 8px; width: 8px; }
.legend__dot--round { border-radius: 50%; height: 10px; width: 10px; }
.bars { align-items: flex-end; background-image: repeating-linear-gradient(to top, transparent 0, transparent calc(25% - 1px), var(--border) calc(25% - 1px), var(--border) 25%); background-position: bottom; background-size: 100% 100%; display: flex; gap: 8px; height: 200px; justify-content: space-around; padding-top: 10px; }
.bar-col { align-items: center; display: flex; flex: 1; flex-direction: column; gap: 6px; height: 100%; }
.bar-stack { border-radius: 4px 4px 0 0; display: flex; flex-direction: column-reverse; height: 100%; max-width: 30px; overflow: hidden; position: relative; width: 60%; }
.bar-seg { transition: height .6s ease; width: 100%; }
.bar-used { background: var(--primary); }
.bar-free { background: #cbd5e1; }
.bar-hover { cursor: pointer; inset: 0; position: absolute; }
.bar-label { color: var(--text-mute); font-size: 11px; }
.donut-layout { align-items: center; display: flex; gap: 24px; justify-content: space-between; padding: 8px 16px; }
.donut-legend { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.legend-row { align-items: center; border-radius: 6px; display: flex; font-size: 13px; gap: 8px; padding: 4px 10px; }
.legend-row--bordered { border-top: 1px solid var(--border); }
.legend-row__name { color: var(--text); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.legend-row strong { color: var(--text); font-size: 14px; font-weight: 600; }
.legend-row__unit { color: var(--text-sub); font-size: 12px; }
.donut-svg { display: flex; flex-shrink: 0; justify-content: center; margin: 8px auto 16px; }
.donut-svg path:hover { opacity: .8; }
.line-chart { padding: 10px 0; }
.line-canvas { display: block; height: 220px; width: 100%; }
.chart-tip { backdrop-filter: blur(4px); background: var(--text); border: 1px solid rgb(255 255 255 / 12%); border-radius: 10px; box-shadow: 0 8px 24px rgb(0 0 0 / 25%); color: #fff; display: block; font-size: 13px; min-width: 140px; padding: 10px 14px; pointer-events: none; position: fixed; z-index: 999; }
.chart-tip__title { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.chart-tip__row { align-items: center; display: flex; font-size: 13px; gap: 16px; justify-content: space-between; margin: 3px 0; }
.chart-tip__row span { color: rgb(255 255 255 / 65%); }
.chart-tip__row b { font-size: 14px; font-weight: 700; }
.loading-state, .error-state, .empty-state { color: var(--text-sub); padding: 40px 0; text-align: center; }
.error-state { color: var(--red); }
:global([data-theme="dark"]) .stat-card--blue { background: #2563eb; }:global([data-theme="dark"]) .stat-card--green { background: #059669; }:global([data-theme="dark"]) .stat-card--orange { background: #d97706; }:global([data-theme="dark"]) .stat-card--red { background: #dc2626; }
:global([data-theme="dark"]) .stat-card__delta.up { background: rgb(255 255 255 / 20%); }:global([data-theme="dark"]) .stat-card__delta.down { background: rgb(0 0 0 / 20%); }
:global([data-theme="dark"]) .bar-free { background: #3f3f46; }
:global([data-theme="dark"]) .chart-tip { background: #3f3f46; border-color: rgb(255 255 255 / 8%); color: #f4f4f5; }
:global([data-theme="dark"]) .chart-tip__row span { color: rgb(244 244 245 / 55%); }
@media (max-width: 980px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.dash-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .page { padding: 16px; } }
@media (max-width: 480px) { .stat-grid { grid-template-columns: 1fr; }.donut-layout { flex-direction: column; } }
</style>
