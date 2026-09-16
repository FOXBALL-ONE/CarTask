<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">进出申请</h1>
        <p class="page__desc">登记车辆进出申请，审核通过后下发科拓月卡</p>
      </div>
      <button v-if="can('vehicle-inout-request:apply')" class="button button--primary" type="button"
              @click="openCreate"><span class="material-icons-outlined">add</span>登记申请
      </button>
    </header>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.key" :class="{ 'vp-tab--active': activeTab === tab.key }" class="vp-tab"
              type="button" @click="activeTab = tab.key">{{ tab.label }}
      </button>
    </div>

    <p v-if="successMessage" class="notice">{{ successMessage }}</p>
    <p v-if="actionError" class="notice notice--error">{{ actionError }}</p>

    <section class="card">
      <div class="toolbar">
        <input v-model="keyword" class="input" placeholder="编号 / 车牌号 / 车主 / 手机号" type="search"
               @keyup.enter="search">
        <div class="toolbar__right">
          <button class="button button--soft" type="button" @click="search"><span
              class="material-icons-outlined">search</span>搜索
          </button>
          <button class="button button--ghost" type="button" @click="resetFilters"><span
              class="material-icons-outlined">restart_alt</span>重置
          </button>
        </div>
      </div>

      <div v-if="loading" class="state">正在加载申请数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th>编号</th>
            <th>车牌号</th>
            <th>车主</th>
            <th>单位</th>
            <th>月卡名称</th>
            <th>停车区域</th>
            <th>有效期</th>
            <th>申请时间</th>
            <th>状态</th>
            <th>下发状态</th>
            <th class="right">操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="request in requests" :key="request.id">
            <td>{{ String(request.id).padStart(4, "0") }}</td>
            <td><strong class="primary-text">{{ request.plate }}</strong></td>
            <td>{{ request.owner }}</td>
            <td>{{ request.dept || "-" }}</td>
            <td>{{ request.card_name }}</td>
            <td>{{ request.area_name || "-" }}</td>
            <td class="muted">{{ formatDateTime(request.valid_from) }} ~ {{ formatDateTime(request.valid_to) }}</td>
            <td class="muted">{{ formatDateTime(request.apply_time) }}</td>
            <td><span :class="statusClass(request.status)" class="tag">{{ request.status }}</span></td>
            <td><span :class="syncClass(request.sync_status)" :title="request.sync_message || ''"
                      class="tag">{{ request.sync_status }}</span></td>
            <td class="right actions-cell">
              <button class="row-action" title="详情" type="button" @click="openDetail(request)"><span
                  class="material-icons-outlined">visibility</span></button>
              <button v-if="request.status === '待审核' && can('vehicle-inout-request:apply')" class="row-action"
                      title="编辑" type="button" @click="openEdit(request)"><span
                  class="material-icons-outlined">edit</span></button>
              <button v-if="request.status === '待审核' && can('vehicle-inout-request:review')"
                      class="button button--primary button--sm" type="button" @click="review(request, true)"><span
                  class="material-icons-outlined">check</span>通过
              </button>
              <button v-if="request.status === '待审核' && can('vehicle-inout-request:review')"
                      class="button button--soft button--sm" type="button" @click="review(request, false)"><span
                  class="material-icons-outlined">close</span>驳回
              </button>
              <button v-if="request.status === '已通过' && can('vehicle-inout-request:sync')"
                      :disabled="busy === `sync-${request.id}`" class="button button--primary button--sm" type="button"
                      @click="synchronize(request)"><span class="material-icons-outlined">cloud_upload</span>{{
                  request.sync_status === "下发失败" ? "重试下发" : "下发月卡"
                }}
              </button>
              <button
                  v-if="(request.status === '待审核' || request.status === '已通过') && request.sync_status !== '已下发' && can('vehicle-inout-request:apply')"
                  class="button button--ghost button--sm" type="button" @click="cancel(request)">撤销
              </button>
            </td>
          </tr>
          <tr v-if="requests.length === 0">
            <td class="empty" colspan="11">暂无申请记录</td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{
          total
        }} 条</span>
        <button :disabled="page <= 1" aria-label="上一页" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" aria-label="下一页" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <div class="modal">
        <header class="modal__head"><h2>申请详情</h2>
          <button aria-label="关闭" class="icon-button" type="button" @click="detail = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <table class="table table--compact">
            <tbody>
            <tr>
              <td>编号</td>
              <td>{{ String(detail.id).padStart(4, "0") }}</td>
            </tr>
            <tr>
              <td>车牌号</td>
              <td>{{ detail.plate }}</td>
            </tr>
            <tr>
              <td>车主</td>
              <td>{{ detail.owner }}</td>
            </tr>
            <tr>
              <td>联系电话</td>
              <td>{{ detail.phone }}</td>
            </tr>
            <tr>
              <td>单位</td>
              <td>{{ detail.dept || "-" }}</td>
            </tr>
            <tr>
              <td>月卡名称</td>
              <td>{{ detail.card_name }}</td>
            </tr>
            <tr>
              <td>停车区域</td>
              <td>{{ detail.area_name || "-" }}</td>
            </tr>
            <tr>
              <td>有效期</td>
              <td>{{ formatDateTime(detail.valid_from) }} ~ {{ formatDateTime(detail.valid_to) }}</td>
            </tr>
            <tr>
              <td>申请时间</td>
              <td>{{ formatDateTime(detail.apply_time) }}</td>
            </tr>
            <tr>
              <td>状态</td>
              <td><span :class="statusClass(detail.status)" class="tag">{{ detail.status }}</span></td>
            </tr>
            <tr>
              <td>下发状态</td>
              <td><span :class="syncClass(detail.sync_status)" class="tag">{{ detail.sync_status }}</span></td>
            </tr>
            <tr v-if="detail.card_id">
              <td>科拓月卡</td>
              <td>{{ detail.card_id }}</td>
            </tr>
            <tr v-if="detail.sync_message">
              <td>下发说明</td>
              <td class="wrap-text">{{ detail.sync_message }}</td>
            </tr>
            <tr v-if="detail.reviewed_by">
              <td>审批人</td>
              <td>{{ detail.reviewed_by }}</td>
            </tr>
            <tr v-if="detail.reviewed_at">
              <td>审批时间</td>
              <td>{{ formatDateTime(detail.reviewed_at) }}</td>
            </tr>
            <tr v-if="detail.review_reason">
              <td>审批意见</td>
              <td class="wrap-text">{{ detail.review_reason }}</td>
            </tr>
            <tr v-if="detail.synced_at">
              <td>下发时间</td>
              <td>{{ formatDateTime(detail.synced_at) }}</td>
            </tr>
            </tbody>
          </table>
        </div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="detail = null">关闭</button>
        </footer>
      </div>
    </div>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="save">
        <header class="modal__head"><h2>{{ editingId ? "编辑申请" : "登记车辆进出申请" }}</h2>
          <button aria-label="关闭" class="icon-button" type="button" @click="editorVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <label v-if="!editingId && canCreateArchives" class="archive-toggle">
            <input v-model="form.createArchives" type="checkbox">
            <span>车牌尚未建档，同时新建车主档案与登录账号</span>
          </label>
          <div class="form-grid">
            <label class="field">
              <span>车牌号 <em>*</em></span>
              <input v-if="form.createArchives" v-model.trim="form.plate" class="input" maxlength="32"
                     placeholder="如：京A12345"
                     required>
              <template v-else>
                <input v-model.trim="form.plate" class="input" list="vin-plate-options"
                       placeholder="输入或选择已建档车牌"
                       required @input="onPlateInput">
                <datalist id="vin-plate-options">
                  <option v-for="option in plateOptions" :key="option.id" :value="option.plate"></option>
                </datalist>
              </template>
            </label>
            <label class="field"><span>月卡名称 <em>*</em></span><input v-model.trim="form.cardName" class="input"
                                                                        maxlength="64" placeholder="如：内部员工月卡"
                                                                        required></label>
            <label class="field">
              <span>停车区域</span>
              <select v-if="areaOptions.length" v-model="form.areaCode" class="select">
                <option value="">不指定</option>
                <option v-for="area in areaOptions" :key="area.zoneCode" :value="area.zoneCode">{{
                    area.zoneName
                  }}
                </option>
              </select>
              <input v-else v-model.trim="form.areaCode" class="input" maxlength="64" placeholder="区域编码，可留空">
            </label>
            <label class="field"><span>有效期开始 <em>*</em></span><input v-model="form.validFrom" class="input"
                                                                          required type="datetime-local"></label>
            <label class="field"><span>有效期结束 <em>*</em></span><input v-model="form.validTo" class="input"
                                                                          required type="datetime-local"></label>
            <template v-if="form.createArchives">
              <label class="field"><span>车主姓名 <em>*</em></span><input v-model.trim="form.ownerName" class="input"
                                                                          maxlength="128" placeholder="与证件一致"
                                                                          required></label>
              <label class="field"><span>登录手机号 <em>*</em></span><input v-model.trim="form.phone" class="input"
                                                                            maxlength="32" placeholder="同时作为登录名"
                                                                            required></label>
              <label class="field">
                <span>部门 <em>*</em></span>
                <select v-model.number="form.departmentId" class="select" required>
                  <option :value="null">请选择部门</option>
                  <option v-for="department in departments" :key="department.id" :value="department.id">
                    {{ department.name }}
                  </option>
                </select>
              </label>
              <label class="field"><span>职务 <em>*</em></span><input v-model.trim="form.jobTitle" class="input"
                                                                      maxlength="64" placeholder="如：部门经理" required></label>
              <label class="field"><span>初始密码</span><input v-model="form.password" class="input" maxlength="64"
                                                               placeholder="留空则使用系统初始密码"
                                                               type="password"></label>
            </template>
          </div>
          <p class="form-hint">
            <template v-if="form.createArchives">
              将新建车主档案、该车主的登录账号与车牌档案。月卡使用人、电话与部门取自下面填写的内容；登录名即手机号，留空密码时下发系统初始密码并要求首次登录改密。
            </template>
            <template v-else-if="selectedPlate">车主「{{
                selectedPlate.owner
              }}」{{ selectedOwnerPhone ? `（${selectedOwnerPhone}）` : "" }}——月卡使用人、电话与部门由车主档案带出，本页不修改车主信息。
            </template>
            <template v-else>车牌必须已在「车牌信息」中建档；申请提交后需审核，审核通过再下发月卡。</template>
          </p>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="editorVisible = false">取消</button>
          <button :disabled="saving" class="button button--primary" type="submit">{{
              saving ? "保存中..." : "保存"
            }}
          </button>
        </footer>
      </form>
    </div>
  </section>
</template>

<script lang="ts" setup>
interface Request {
  id: number;
  plate: string;
  owner: string;
  owner_id: number;
  phone: string;
  dept: string;
  card_name: string;
  area_code: string | null;
  area_name: string | null;
  valid_from: string;
  valid_to: string;
  status: string;
  sync_status: string;
  sync_message: string | null;
  card_id: number | null;
  apply_time: string;
  reviewed_by: string | null;
  reviewed_at: string | null;
  review_reason: string | null;
  synced_at: string | null;
}

interface RequestList {
  items: Request[];
  total: number
}

interface PlateOption {
  id: number;
  plate: string;
  owner: string;
  ownerId: number
}

interface OwnerOption {
  id: number;
  name: string;
  phone: string
}

interface Zone {
  zoneCode?: string | null;
  zoneName?: string | null
}

interface ZonePage {
  content?: Zone[];
  items?: Zone[]
}

const http = useHttp();
const {can} = usePermission();
const tabs = [
  {key: "all", label: "全部申请"},
  {key: "pending", label: "待审核"},
  {key: "approved", label: "已通过"},
  {key: "synced", label: "已下发"},
];
const activeTab = ref("all");
const keyword = ref("");
const page = ref(1);
const pageSize = 8;
const requests = ref<Request[]>([]);
const total = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const successMessage = ref("");
/** 操作类错误（审核、下发）不能复用 errorMessage：那个会把整张表与分页一起换成错误态。 */
const actionError = ref("");
const busy = ref("");
const detail = ref<Request | null>(null);
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const formError = ref("");
const form = reactive({
  plate: "",
  cardName: "",
  areaCode: "",
  validFrom: "",
  validTo: "",
  createArchives: false,
  ownerName: "",
  phone: "",
  departmentId: null as number | null,
  jobTitle: "",
  password: ""
});
const plateOptions = ref<PlateOption[]>([]);
const areaOptions = ref<{ zoneCode: string; zoneName: string }[]>([]);
const ownerPhones = ref<Record<number, string>>({});

interface DepartmentOption {
  id: number;
  name: string
}

const departments = ref<DepartmentOption[]>([]);
let plateQueryTimer: ReturnType<typeof setTimeout> | null = null;
let loadSequence = 0;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
/** 表单里输入的车牌若与某条已建档车牌完全一致，就在表单里带出车主信息供确认。 */
const selectedPlate = computed(() => {
  const value = form.plate.replace(/[\s·.。]/g, "").toUpperCase();
  if (!value) return null;
  return plateOptions.value.find((option) => option.plate.replace(/[\s·.。]/g, "").toUpperCase() === value) || null;
});
const selectedOwnerPhone = computed(() => (selectedPlate.value ? ownerPhones.value[selectedPlate.value.ownerId] || "" : ""));
/**
 * 顺带建档等同手工建账号 + 建车主 + 建车牌，因此入口要求三项权限齐全。
 * 这只是体验层：真正的拦截在服务端（VehicleInoutArchiveService.requireAuthorities）。
 */
const canCreateArchives = computed(() => can("user:create") && can("owner:manage") && can("plate:manage"));

/** 标签页映射到服务端的精确筛选：已通过看审批结论，已下发看下发结论，两个维度独立。 */
function tabFilters() {
  switch (activeTab.value) {
    case "pending":
      return {status: "待审核", syncStatus: undefined as string | undefined};
    case "approved":
      return {status: "已通过", syncStatus: undefined as string | undefined};
    case "synced":
      return {status: undefined as string | undefined, syncStatus: "已下发"};
    default:
      return {status: undefined as string | undefined, syncStatus: undefined as string | undefined};
  }
}

async function loadRequests() {
  const requestId = ++loadSequence;
  loading.value = true;
  errorMessage.value = "";
  try {
    const filters = tabFilters();
    const result = await http.get<RequestList>("/vehicle-inout-requests", {
      keyword: keyword.value || undefined,
      status: filters.status,
      sync_status: filters.syncStatus,
      page: page.value,
      page_size: pageSize,
    });
    // 连续切换标签页会并发多次加载，晚到的旧响应不能覆盖新结果。
    if (requestId !== loadSequence) return;
    requests.value = result.items || [];
    total.value = result.total || 0;
  } catch (error) {
    if (requestId === loadSequence) errorMessage.value = (error as {
      statusMessage?: string
    }).statusMessage || "申请数据加载失败";
  } finally {
    if (requestId === loadSequence) loading.value = false;
  }
}

/** 车牌候选来自车牌档案；额外查一次只为了让表单里能提前显示车主，失败不影响提交。 */
async function loadPlateOptions(plateKeyword = "") {
  try {
    const found = (await http.get<{ items: PlateOption[] }>("/plates", {
      keyword: plateKeyword || undefined,
      page: 1,
      pageSize: 50
    })).items || [];
    // 与已有候选合并而不是替换：输入一次之后下拉里就只剩那几条会很难用。
    const merged = new Map(plateOptions.value.map((item) => [item.id, item]));
    found.forEach((item) => merged.set(item.id, item));
    plateOptions.value = [...merged.values()];
  } catch {
    // 候选加载失败不阻断登记：车牌最终由服务端按档案校验。
  }
}

function onPlateInput(event: Event) {
  // 直接读事件里的值而不是等 v-model 落地：input 事件上 v-model 的写入时机依赖编译结果。
  const value = (event.target as HTMLInputElement).value.trim();
  if (plateQueryTimer) clearTimeout(plateQueryTimer);
  plateQueryTimer = setTimeout(() => {
    void loadPlateOptions(value);
  }, 250);
}

/** 车主电话只在需要时按车主姓名回查一次，避免把整张车主表拉到前端。 */
async function loadOwnerPhone(plate: PlateOption) {
  if (ownerPhones.value[plate.ownerId] !== undefined) return;
  try {
    const found = (await http.get<{ items: OwnerOption[] }>("/owners", {
      keyword: plate.owner,
      page: 1,
      pageSize: 20
    })).items || [];
    const owner = found.find((item) => item.id === plate.ownerId);
    ownerPhones.value = {...ownerPhones.value, [plate.ownerId]: owner?.phone || ""};
  } catch {
    ownerPhones.value = {...ownerPhones.value, [plate.ownerId]: ""};
  }
}

async function loadDepartments() {
  if (!can("department:read")) return;
  try {
    const result = await http.get<DepartmentOption[]>("/depts");
    departments.value = (result || []).filter((item) => item.id != null && Boolean(item.name));
  } catch {
    // 拿不到部门字典时下拉是空的，保存会被 required 拦下，不会造出没有部门的账号。
  }
}

async function loadAreaOptions() {
  if (!can("dictionary:read")) return;
  try {
    const result = await http.get<ZonePage | Zone[]>("/zone-types", {page: 1, page_size: 100});
    const rows = Array.isArray(result) ? result : (result.content ?? result.items ?? []);
    areaOptions.value = rows
        .filter((zone) => Boolean(zone.zoneCode && zone.zoneName))
        .map((zone) => ({zoneCode: zone.zoneCode as string, zoneName: zone.zoneName as string}));
  } catch {
    // 拿不到区域字典时退回手工输入区域编码，不挡住登记流程。
  }
}

function search() {
  page.value = 1;
  successMessage.value = "";
  void loadRequests();
}

function resetFilters() {
  keyword.value = "";
  page.value = 1;
  void loadRequests();
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  void loadRequests();
}

function statusClass(status: string) {
  return status === "已通过" ? "tag--green" : status === "待审核" ? "tag--blue" : status === "已驳回" ? "tag--red" : "tag--gray";
}

function syncClass(status: string) {
  return status === "已下发" ? "tag--green" : status === "下发失败" ? "tag--red" : "tag--orange";
}

function formatDateTime(value?: string | null) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

/**
 * `datetime-local` 输入框只认 `YYYY-MM-DDTHH:mm`。
 * 后端返回的是 ISO 本地时间（可能带秒），直接回填会让输入框显示为空，
 * 用户以为有效期是空的，保存时又被前端拦下。
 */
function toInputDateTime(value?: string | null) {
  return value ? value.slice(0, 16) : "";
}

/** 有效期默认给一个月：月卡按月经办是常态，留空让用户从零开始填更容易填错年份。 */
function defaultValidity() {
  const now = new Date();
  const start = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}T00:00`;
  const end = new Date(now.getFullYear(), now.getMonth() + 1, now.getDate());
  const finish = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, "0")}-${String(end.getDate()).padStart(2, "0")}T23:59`;
  return {validFrom: start, validTo: finish};
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    plate: "",
    cardName: "",
    areaCode: "",
    createArchives: false,
    ownerName: "",
    phone: "",
    departmentId: null,
    jobTitle: "",
    password: "", ...defaultValidity()
  });
  formError.value = "";
  editorVisible.value = true;
  void loadPlateOptions();
}

function openEdit(request: Request) {
  editingId.value = request.id;
  Object.assign(form, {
    createArchives: false,
    ownerName: "",
    phone: "",
    departmentId: null,
    jobTitle: "",
    password: "",
    plate: request.plate,
    cardName: request.card_name,
    areaCode: request.area_code || "",
    validFrom: toInputDateTime(request.valid_from),
    validTo: toInputDateTime(request.valid_to),
  });
  formError.value = "";
  editorVisible.value = true;
  void loadPlateOptions(request.plate);
}

function openDetail(request: Request) {
  detail.value = request;
}

async function save() {
  formError.value = "";
  if (!form.plate || !form.cardName || !form.validFrom || !form.validTo) {
    formError.value = "车牌号、月卡名称与有效期不能为空";
    return;
  }
  if (form.createArchives && (!form.ownerName || !form.phone || !form.departmentId || !form.jobTitle)) {
    formError.value = "新建档案时车主姓名、登录手机号、部门与职务不能为空";
    return;
  }
  saving.value = true;
  actionError.value = "";
  try {
    // 编辑时 area_code 必须显式传空串才表示清空：null 与「字段缺省」在后端是两件事（缺省=不改）。
    // 建档只在登记时给出：编辑走的是已存在的车牌，后端不接受在这里再造一份档案。
    const newOwner = !editingId.value && form.createArchives
        ? {
          name: form.ownerName,
          phone: form.phone,
          department_id: form.departmentId,
          job_title: form.jobTitle,
          password: form.password || null
        }
        : undefined;
    const payload = {
      plate: form.plate,
      card_name: form.cardName,
      area_code: form.areaCode || "",
      valid_from: form.validFrom,
      valid_to: form.validTo,
      new_owner: newOwner
    };
    if (editingId.value) await http.put(`/vehicle-inout-requests/${editingId.value}`, payload, {payloadMode: "json"});
    else await http.post("/vehicle-inout-requests", payload, {payloadMode: "json"});
    editorVisible.value = false;
    successMessage.value = editingId.value ? "申请已更新" : "申请已提交，等待审核";
    await loadRequests();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function review(request: Request, approved: boolean) {
  const reason = approved ? undefined : window.prompt(`请输入驳回「${request.plate}」的原因`);
  if (!approved && !reason?.trim()) return;
  if (approved && !window.confirm(`确认通过「${request.plate}」的进出申请？`)) return;
  actionError.value = "";
  successMessage.value = "";
  try {
    await http.put(`/vehicle-inout-requests/${request.id}/review`, {
      approved,
      reason: reason?.trim() || null
    }, {payloadMode: "json"});
    successMessage.value = approved ? `已通过【${request.plate}】，可在列表中下发月卡` : `已驳回【${request.plate}】`;
    await loadRequests();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "审核失败";
  }
}

async function synchronize(request: Request) {
  if (!window.confirm(`确认把「${request.plate}」的月卡下发给科拓平台？`)) return;
  busy.value = `sync-${request.id}`;
  actionError.value = "";
  successMessage.value = "";
  try {
    const result = await http.put<{ synced: boolean; message: string }>(`/vehicle-inout-requests/${request.id}/sync`);
    // 下发失败不抛 HTTP 错误：结论要落在列表上（下发失败 + 失败原因），否则这条申请看起来像没处理过。
    if (result?.synced) successMessage.value = `【${request.plate}】月卡已下发`;
    else actionError.value = result?.message || "月卡下发失败";
    await loadRequests();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "月卡下发失败";
    await loadRequests();
  } finally {
    busy.value = "";
  }
}

async function cancel(request: Request) {
  const reason = window.prompt(`请输入撤销「${request.plate}」申请的原因（可留空）`);
  if (reason === null) return;
  actionError.value = "";
  successMessage.value = "";
  try {
    await http.put(`/vehicle-inout-requests/${request.id}/cancel`, {reason: reason.trim() || null}, {payloadMode: "json"});
    successMessage.value = `已撤销【${request.plate}】`;
    await loadRequests();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "撤销失败";
  }
}

watch(activeTab, () => {
  page.value = 1;
  successMessage.value = "";
  void loadRequests();
});
/** 车牌在表单里解析成已有档案后，补一次车主电话查询用于确认。 */
watch(selectedPlate, (plate) => {
  if (plate && !form.createArchives) void loadOwnerPhone(plate);
});

onMounted(async () => {
  await Promise.all([loadAreaOptions(), loadDepartments(), loadRequests()]);
});
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadRequests);
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
  font-size: 13px;
  margin: 2px 0 0;
}

.tabs {
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
}

.vp-tab {
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text-sub);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  padding: 10px 20px;
  transition: color .15s, border-color .15s;
  white-space: nowrap;
}

.vp-tab:hover {
  color: var(--text);
}

.vp-tab--active {
  border-bottom-color: var(--primary);
  color: var(--primary);
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  overflow: hidden;
}

.toolbar {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 18px;
}

.toolbar__right {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.toolbar .input {
  min-width: 260px;
}

.input, .select {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-sizing: border-box;
  color: var(--text);
  font: inherit;
  height: 34px;
  min-width: 120px;
  outline: none;
  padding: 0 10px;
}

.input:focus, .select:focus, .input:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft);
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

.button--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
}

.button--primary {
  background: var(--primary);
  color: var(--on-solid);
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

.button:hover:not(:disabled) {
  filter: brightness(.97);
}

.button:disabled {
  cursor: wait;
  opacity: .6;
}

.button .material-icons-outlined {
  font-size: 16px;
}

.notice {
  background: var(--success-soft);
  border: 1px solid var(--success);
  border-radius: 6px;
  color: var(--success);
  font-size: 13px;
  margin: 0 0 14px;
  padding: 9px 12px;
}

.notice--error {
  background: var(--danger-soft);
  border-color: var(--red);
  color: var(--red);
}

.table-wrap {
  overflow-x: auto;
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 1180px;
  width: 100%;
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 12px;
  font-weight: 500;
  padding: 10px 14px;
  text-align: left;
  white-space: nowrap;
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 11px 14px;
  white-space: nowrap;
}

.table tbody tr:hover {
  background: var(--bg);
}

.primary-text {
  color: var(--primary);
}

.muted {
  color: var(--text-sub) !important;
}

.right {
  text-align: right !important;
}

.actions-cell {
  white-space: nowrap;
}

.actions-cell .button {
  margin-left: 4px;
}

.tag {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 12px;
  gap: 4px;
  line-height: 1.5;
  padding: 2px 8px;
}

.tag::before {
  background: currentColor;
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px;
}

.tag--green {
  background: var(--success-tint);
  color: var(--green);
}

.tag--blue {
  background: var(--primary-soft);
  color: var(--primary);
}

.tag--orange {
  background: var(--warning-soft);
  color: var(--orange);
}

.tag--red {
  background: var(--danger-tint);
  color: var(--red);
}

.tag--gray {
  background: var(--neutral-soft);
  color: var(--text-sub);
}

.row-action, .icon-button {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 5px;
  color: var(--text-mute);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  width: 28px;
}

.row-action:hover, .icon-button:hover {
  background: var(--bg);
  color: var(--text);
}

.row-action .material-icons-outlined, .icon-button .material-icons-outlined {
  font-size: 16px;
}

.state, .empty {
  color: var(--text-mute);
  padding: 48px;
  text-align: center;
}

.state--error, .form-error {
  color: var(--red);
}

.pagination {
  align-items: center;
  border-top: 1px solid var(--border);
  color: var(--text-sub);
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  padding: 12px 16px;
}

.pagination__info {
  margin-right: auto;
  font-size: 12px;
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

.modal-mask {
  align-items: center;
  background: rgb(0 0 0 / 38%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 300;
}

.modal {
  background: var(--card);
  border-radius: 8px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 20%);
  max-width: 720px;
  width: 100%;
  max-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal__head, .modal__foot {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 14px 18px;
}

.modal__head {
  border-bottom: 1px solid var(--border);
}

.modal__head h2 {
  color: var(--text);
  font-size: 16px;
  margin: 0;
}

.modal__foot {
  border-top: 1px solid var(--border);
  gap: 8px;
  justify-content: flex-end;
}

.modal__body {
  overflow-y: auto;
  padding: 20px 18px;
}

.archive-toggle {
  align-items: center;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  font-size: 13px;
  gap: 8px;
  margin-bottom: 14px;
  padding: 9px 12px;
}

.archive-toggle input {
  accent-color: var(--primary);
  height: 15px;
  width: 15px;
}

.form-grid {
  display: grid;
  gap: 14px 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  color: var(--text-sub);
  font-size: 12px;
  font-weight: 500;
}

.field em {
  color: var(--red);
  font-style: normal;
}

.field .input, .field .select {
  width: 100%;
}

.form-hint {
  color: var(--text-mute);
  line-height: 1.6;
  margin: 14px 0 0;
}

.form-error {
  margin: 14px 0 0;
}

.table--compact td {
  padding: 8px 12px;
  white-space: normal;
}

.table--compact {
  min-width: 0;
}

.table--compact td:first-child {
  color: var(--text-sub);
  width: 110px;
}

.wrap-text {
  word-break: break-all;
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .input {
    min-width: 0;
    width: 100%;
  }

  .toolbar__right {
    justify-content: flex-end;
    margin-left: 0;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .modal-mask {
    padding: 12px;
  }

  .tabs {
    overflow-x: auto;
  }
}
</style>
