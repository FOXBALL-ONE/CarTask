<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>发车表</h1>
        <p>维护对外展示的点对点线路与发车时刻</p>
      </div>
      <div class="page-actions">
        <button class="button button--ghost" type="button" @click="openPublicPage">
          <span class="material-icons-outlined">open_in_new</span>打开公开页
        </button>
        <button class="button button--primary" type="button" @click="openCreate">
          <span class="material-icons-outlined">add</span>新增线路
        </button>
      </div>
    </header>

    <section class="panel">
      <form class="filters" @submit.prevent="search">
        <input v-model.trim="keyword" placeholder="线路名称、起点或终点" type="search">
        <button class="button button--soft" type="submit"><span class="material-icons-outlined">search</span>搜索</button>
        <button class="button button--ghost" type="button" @click="reset"><span class="material-icons-outlined">restart_alt</span>重置</button>
      </form>
      <p v-if="errorMessage" class="state state--error">{{ errorMessage }}</p>
      <p v-else-if="loading" class="state">正在加载发车表...</p>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr><th>线路名称</th><th>点对点</th><th>发车时刻</th><th>备注</th><th aria-label="操作"/></tr>
          </thead>
          <tbody>
            <tr v-for="route in items" :key="route.id">
              <td>
                <div class="route-cell"><strong>{{ route.route_name }}</strong><small>{{ routeDepartures(route).length }} 个班次</small></div>
              </td>
              <td><span class="direction">{{ route.start_address }}<span class="material-icons-outlined">arrow_forward</span>{{ route.end_address }}</span></td>
              <td><div class="time-list"><span v-for="departure in routeDepartures(route)" :key="departure.time" class="time-chip">{{ departure.time }}<small v-if="departure.season">{{ departure.season }}</small></span></div></td>
              <td class="remark">{{ route.remark || "-" }}</td>
              <td class="actions">
                <button class="icon-button" title="编辑" type="button" @click="openEdit(route)"><span class="material-icons-outlined">edit</span></button>
                <button class="icon-button icon-button--danger" title="删除" type="button" @click="remove(route)"><span class="material-icons-outlined">delete</span></button>
              </td>
            </tr>
            <tr v-if="items.length === 0"><td class="empty" colspan="5">暂无发车线路</td></tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading" class="pager">
        <span>共 {{ total }} 条</span>
        <button :disabled="page === 1" class="icon-button" title="上一页" type="button" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button :disabled="page >= totalPages" class="icon-button" title="下一页" type="button" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="save">
        <header><h2>{{ editingId === null ? "新增线路" : "编辑线路" }}</h2><button class="icon-button" title="关闭" type="button" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button></header>
        <div class="modal-body">
          <div class="grid">
            <label class="field"><span>线路名称</span><input v-model.trim="form.route_name" maxlength="120" placeholder="例如：行政服务中心—市政府（下午）" required></label>
            <label class="field"><span>备注</span><input v-model.trim="form.remark" maxlength="500" placeholder="可选，例如：按季节性作息时间调整"></label>
            <label class="field"><span>起点</span><input v-model.trim="form.start_address" maxlength="255" required></label>
            <label class="field"><span>终点</span><input v-model.trim="form.end_address" maxlength="255" required></label>
          </div>
          <section class="departures">
            <header class="departures__header">
              <div><h3>发车时刻</h3><p>仅维护起点到终点的班次，不再填写途经站点。</p></div>
              <button class="button button--soft" type="button" @click="addDeparture"><span class="material-icons-outlined">add</span>添加班次</button>
            </header>
            <div class="departure-row departure-row--heading"><span>序号</span><span>时间</span><span>季节</span><span>车辆数</span><span aria-hidden="true"/></div>
            <div v-for="(departure, index) in form.departures" :key="index" class="departure-row">
              <span class="departure-index">{{ index + 1 }}</span>
              <input v-model="departure.time" aria-label="发车时间" required type="time">
              <select v-model="departure.season" aria-label="季节">
                <option value="">全年</option><option value="冬季">冬季</option><option value="夏季">夏季</option>
              </select>
              <input v-model.number="departure.vehicle_count" aria-label="车辆数" max="99" min="1" placeholder="-" type="number">
              <button :disabled="form.departures.length === 1" class="icon-button icon-button--danger" title="删除班次" type="button" @click="form.departures.splice(index, 1)"><span class="material-icons-outlined">delete</span></button>
            </div>
          </section>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer><button class="button button--ghost" type="button" @click="editorVisible = false">取消</button><button :disabled="saving" class="button button--primary" type="submit">{{ saving ? "保存中..." : "保存" }}</button></footer>
      </form>
    </div>
  </section>
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

interface ListResponse {
  items: CommuteRoute[];
  total: number;
}

const http = useHttp();
const items = ref<CommuteRoute[]>([]);
const total = ref(0);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const keyword = ref("");
const page = ref(1);
const pageSize = 10;
const editingId = ref<number | null>(null);
const editorVisible = ref(false);
const form = reactive({
  route_name: "",
  start_address: "",
  end_address: "",
  departures: [{time: "", season: "", vehicle_count: null}] as Departure[],
  remark: "",
});
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

function routeDepartures(route: CommuteRoute): Departure[] {
  const departures = route.departures?.length
    ? route.departures
    : (route.route_stops || []).map((stop) => ({time: stop.time}));
  return [...departures].sort((left, right) => left.time.localeCompare(right.time));
}

function resetForm() {
  Object.assign(form, {
    route_name: "",
    start_address: "",
    end_address: "",
    departures: [{time: "", season: "", vehicle_count: null}],
    remark: "",
  });
}

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<ListResponse>("/commute-routes", {keyword: keyword.value || undefined, page: page.value, page_size: pageSize});
    items.value = result.items || [];
    total.value = result.total || 0;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "发车表加载失败";
  } finally {
    loading.value = false;
  }
}

async function search() {
  page.value = 1;
  await load();
}

async function reset() {
  keyword.value = "";
  await search();
}

async function changePage(nextPage: number) {
  if (nextPage >= 1 && nextPage <= totalPages.value) {
    page.value = nextPage;
    await load();
  }
}

function openCreate() {
  editingId.value = null;
  formError.value = "";
  resetForm();
  editorVisible.value = true;
}

function openPublicPage() {
  window.open("/commute-routes", "_blank", "noopener,noreferrer");
}

function openEdit(route: CommuteRoute) {
  editingId.value = route.id;
  formError.value = "";
  Object.assign(form, {
    route_name: route.route_name,
    start_address: route.start_address,
    end_address: route.end_address,
    departures: routeDepartures(route).map((departure) => ({
      time: departure.time,
      season: departure.season || "",
      vehicle_count: departure.vehicle_count ?? null,
    })),
    remark: route.remark || "",
  });
  editorVisible.value = true;
}

function addDeparture() {
  form.departures.push({time: "", season: "", vehicle_count: null});
}

async function save() {
  saving.value = true;
  formError.value = "";
  try {
    const payload = {
      route_name: form.route_name,
      start_address: form.start_address,
      end_address: form.end_address,
      departures: form.departures.map((departure) => ({
        time: departure.time,
        season: departure.season || null,
        vehicle_count: departure.vehicle_count || null,
      })),
      remark: form.remark || null,
    };
    if (editingId.value === null) {
      await http.post("/commute-routes", payload, {payloadMode: "json"});
    } else {
      await http.put(`/commute-routes/${editingId.value}`, payload, {payloadMode: "json"});
    }
    editorVisible.value = false;
    await load();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function remove(route: CommuteRoute) {
  if (window.confirm(`确认删除“${route.route_name}”？`)) {
    await http.delete(`/commute-routes/${route.id}`);
    await load();
  }
}

onMounted(load);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }
.page-header { align-items: center; display: flex; gap: 16px; justify-content: space-between; margin-bottom: 20px; }
.page-header h1 { color: var(--text); font-size: 18px; margin: 0; }
.page-header p { color: var(--text-sub); margin: 4px 0 0; }
.page-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.panel, .modal { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; }
.filters { align-items: center; border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; gap: 8px; padding: 14px 16px; }
.filters input, .field input, .departure-row input, .departure-row select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; padding: 0 10px; }
.filters input { min-width: 240px; }
.filters input:focus, .field input:focus, .departure-row input:focus, .departure-row select:focus { border-color: var(--primary); outline: none; }
.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 34px; padding: 0 12px; }
.button:disabled, .icon-button:disabled { cursor: not-allowed; opacity: .45; }
.button--primary { background: var(--primary); color: var(--on-solid); }
.button--soft { background: var(--primary-soft); color: var(--primary); }
.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }
.button .material-icons-outlined { font-size: 17px; }
.table-wrap { overflow-x: auto; }
table { border-collapse: collapse; min-width: 920px; width: 100%; }
th, td { border-bottom: 1px solid var(--border); color: var(--text); padding: 12px 16px; text-align: left; vertical-align: middle; }
th { background: var(--bg); color: var(--text-mute); font-size: 12px; font-weight: 500; }
.route-cell { display: grid; gap: 4px; }
.route-cell strong { font-weight: 600; }
.route-cell small { color: var(--text-mute); font-size: 11px; }
.direction { align-items: center; display: inline-flex; gap: 5px; white-space: nowrap; }
.direction .material-icons-outlined { color: var(--text-mute); font-size: 17px; }
.time-list { display: flex; flex-wrap: wrap; gap: 5px; max-width: 300px; }
.time-chip { background: var(--primary-soft); border-radius: 4px; color: var(--primary); font-family: Consolas, monospace; font-size: 12px; padding: 4px 7px; white-space: nowrap; }
.time-chip small { color: var(--text-sub); font-family: inherit; margin-left: 4px; }
.remark { color: var(--text-sub); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions { text-align: right; white-space: nowrap; }
.icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 30px; justify-content: center; width: 30px; }
.icon-button:hover { background: var(--bg); color: var(--text); }
.icon-button--danger:hover { background: var(--danger-soft); color: var(--danger); }
.icon-button .material-icons-outlined { font-size: 18px; }
.state, .empty { color: var(--text-mute); padding: 44px; text-align: center; }
.state--error, .form-error { color: var(--danger); }
.pager { align-items: center; color: var(--text-sub); display: flex; gap: 8px; justify-content: flex-end; padding: 10px 16px; }
.pager > :first-child { margin-right: auto; }
.modal-mask { align-items: center; background: rgb(0 0 0 / 38%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 100; }
.modal { display: flex; flex-direction: column; max-height: calc(100dvh - 40px); max-width: 760px; width: 100%; }
.modal header, .modal footer { align-items: center; display: flex; justify-content: space-between; padding: 14px 18px; }
.modal header { border-bottom: 1px solid var(--border); }
.modal footer { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }
.modal h2, .departures h3 { color: var(--text); font-size: 15px; margin: 0; }
.modal-body { overflow-y: auto; padding: 18px; }
.grid { display: grid; gap: 0 14px; grid-template-columns: 1fr 1fr; }
.field { display: block; margin-bottom: 14px; }
.field span { color: var(--text-sub); display: block; font-size: 12px; margin-bottom: 6px; }
.field input { width: 100%; }
.departures { border-top: 1px solid var(--border); margin-top: 4px; padding-top: 14px; }
.departures__header { align-items: center; display: flex; gap: 12px; justify-content: space-between; margin-bottom: 12px; }
.departures__header p { color: var(--text-mute); font-size: 12px; margin: 4px 0 0; }
.departure-row { align-items: center; display: grid; gap: 8px; grid-template-columns: 34px minmax(0, 1fr) 130px 110px 30px; margin-bottom: 8px; }
.departure-row--heading { color: var(--text-mute); font-size: 11px; margin-bottom: 5px; }
.departure-index { color: var(--text-mute); text-align: center; }
.departure-row input, .departure-row select { width: 100%; }
.form-error { margin: 14px 0 0; }
@media (max-width: 768px) {
  .page { padding: 16px; }
  .page-header { align-items: flex-start; flex-direction: column; }
  .filters input { min-width: 0; width: 100%; }
  .filters .button { flex: 1; justify-content: center; }
  .modal-mask { align-items: flex-end; padding: 0; }
  .modal { border-bottom-left-radius: 0; border-bottom-right-radius: 0; max-height: calc(100dvh - 16px); }
  .grid { grid-template-columns: 1fr; }
  .departure-row { grid-template-columns: 24px minmax(0, 1fr) 90px 74px 30px; }
  .departure-row--heading { font-size: 10px; }
  .modal-body { padding: 16px; }
}
</style>
