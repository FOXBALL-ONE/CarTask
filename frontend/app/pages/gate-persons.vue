<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">人员信息</h1>
        <p class="page__desc">门禁授权人员信息管理</p>
      </div>
      <div class="page__actions">
        <template v-if="activeTab === 'all'">
          <button v-if="can('gate-person:manage')" class="btn btn--ghost btn--sm" type="button" @click="openImport">
            <span class="material-icons-outlined">upload</span>导入
          </button>
          <button v-if="can('gate-person:export')" :disabled="busy === 'export'" class="btn btn--ghost btn--sm"
                  type="button" @click="exportPersons"><span
              class="material-icons-outlined">download</span>{{ busy === "export" ? "导出中..." : "导出" }}
          </button>
          <button v-if="can('gate-person:manage')" class="btn btn--primary btn--sm" type="button" @click="openCreate">
            <span class="material-icons-outlined">add</span>新增人员
          </button>
        </template>
        <button v-else-if="activeTab === 'pending' && can('gate-person:review')" :disabled="busy === 'batch'"
                class="btn btn--ghost btn--sm" type="button" @click="approveAll"><span class="material-icons-outlined">done_all</span>{{
            busy === "batch" ? "处理中..." : "批量通过"
          }}
        </button>
      </div>
    </header>

    <div class="tabs">
      <button :class="{ 'gp-tab--active': activeTab === 'all' }" class="gp-tab" type="button"
              @click="activeTab = 'all'">人员信息
      </button>
      <button :class="{ 'gp-tab--active': activeTab === 'pending' }" class="gp-tab" type="button"
              @click="activeTab = 'pending'">待审核人员
      </button>
      <button :class="{ 'gp-tab--active': activeTab === 'delete' }" class="gp-tab" type="button"
              @click="activeTab = 'delete'">申请删除人员
      </button>
    </div>

    <p v-if="successMessage" class="notice">{{ successMessage }}</p>
    <p v-if="actionError" class="notice notice--error">{{ actionError }}</p>

    <section class="card">
      <div class="card__body filter-body">
        <div class="toolbar">
          <input v-model="filters.keyword" :placeholder="activeTab === 'delete' ? '搜索编号 / 姓名 / 手机号 / 身份证' : '搜索编号 / 姓名 / 手机号 / 身份证'"
                 class="input"
                 @keyup.enter="search">
          <input v-if="activeTab === 'all'" v-model.trim="filters.dept" aria-label="单位" class="input dept-filter-input"
                 list="gate-filter-depts" placeholder="输入或选择单位" autocomplete="off"
                 :aria-invalid="Boolean(departmentFilterError)"
                 :aria-describedby="departmentFilterError ? 'gate-filter-dept-error' : undefined"
                 @input="departmentFilterError = ''" @change="validateDepartmentFilter() && search()" @keyup.enter.prevent="search">
          <datalist id="gate-filter-depts">
            <option v-for="dept in departmentOptions" :key="dept" :value="dept">{{ dept }}</option>
          </datalist>
          <select v-if="activeTab === 'all'" v-model="filters.approveStatus" aria-label="审批状态" class="select"
                  @change="search">
            <option value="">全部审批状态</option>
            <option value="通过">通过</option>
            <option value="审核中">审核中</option>
            <option value="拒绝">拒绝</option>
          </select>
          <!-- 同步状态筛选已移除：后端没有任何接口会把人员置为「已同步」（见 GatePerson.SyncStatus
               的写入点只有 mock 数据），所以「已同步」恒为空、「未同步」恒等于全部，
               两个选项都筛不出东西。等门禁设备下发接进来之后再把筛选加回来。 -->
          <select v-if="activeTab === 'delete'" v-model="deleteStatus" aria-label="申请状态" class="select"
                  @change="search">
            <option value="">全部状态</option>
            <option value="待处理">待处理</option>
            <option value="已同意">已同意</option>
            <option value="已拒绝">已拒绝</option>
          </select>
          <div class="toolbar__right">
            <button class="btn btn--soft btn--sm" type="button" @click="search"><span class="material-icons-outlined">search</span>搜索
            </button>
            <button class="btn btn--ghost btn--sm" type="button" @click="resetFilters"><span
                class="material-icons-outlined">restart_alt</span>重置
            </button>
          </div>
        </div>
        <p v-if="departmentFilterError" id="gate-filter-dept-error" class="filter-error" role="alert">
          {{ departmentFilterError }}
        </p>
      </div>

      <div v-if="loading" class="state">正在加载数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead v-if="activeTab !== 'delete'">
          <tr>
            <th>人脸信息</th>
            <th>编号</th>
            <th>单位</th>
            <th>用户名</th>
            <th>手机号</th>
            <th>身份证</th>
            <th>{{ activeTab === 'pending' ? '申请时间' : '入库时间' }}</th>
            <th>{{ activeTab === 'pending' ? '状态' : '审批状态' }}</th>
            <th v-if="activeTab === 'all'">同步状态</th>
            <th class="right">操作</th>
          </tr>
          </thead>
          <thead v-else>
          <tr>
            <th>人脸信息</th>
            <th>编号</th>
            <th>单位</th>
            <th>用户名</th>
            <th>手机号</th>
            <th>身份证</th>
            <th>删除原因</th>
            <th>申请时间</th>
            <th>状态</th>
            <th class="right">操作</th>
          </tr>
          </thead>
          <tbody>
          <template v-if="activeTab !== 'delete'">
            <tr v-for="person in visiblePersons" :key="person.id">
              <td><img :src="faceSrc(person.face)" alt="人脸" class="record-face zoomable" @error="useFallbackFace">
              </td>
              <td>{{ person.code }}</td>
              <td>{{ person.dept }}</td>
              <td>{{ person.name }}</td>
              <td>{{ person.phone }}</td>
              <td>{{ person.idCard }}</td>
              <td>{{ formatDateTime(person.createTime) }}</td>
              <td><span :class="approveClass(person.approveStatus)" class="tag">{{ person.approveStatus }}</span></td>
              <td v-if="activeTab === 'all'"><span :class="person.syncStatus === '已同步' ? 'tag--green' : 'tag--orange'"
                                                   class="tag">{{
                  person.syncStatus
                }}</span></td>
              <td class="right actions-cell">
                <button class="row-act" title="查看" type="button" @click="openDetail(person)"><span
                    class="material-icons-outlined">visibility</span></button>
                <template v-if="activeTab === 'all'">
                  <button v-if="can('gate-person:manage')" class="row-act" title="编辑" type="button"
                          @click="openEdit(person)"><span class="material-icons-outlined">edit</span></button>
                  <button v-if="can('gate-person:manage')" class="row-act row-act--danger" title="申请删除"
                          type="button" @click="requestDelete(person)"><span
                      class="material-icons-outlined">delete</span></button>
                </template>
                <template v-else>
                  <button v-if="can('gate-person:review')" class="btn btn--primary btn--sm" type="button"
                          @click="approvePerson(person)"><span class="material-icons-outlined">check</span>通过
                  </button>
                  <button v-if="can('gate-person:review')" class="btn btn--soft btn--sm" type="button"
                          @click="rejectPerson(person)"><span class="material-icons-outlined">close</span>拒绝
                  </button>
                </template>
              </td>
            </tr>
          </template>
          <tr v-for="request in visibleRequests" v-else :key="request.id">
            <td><img :src="faceSrc(request.face)" alt="人脸" class="record-face zoomable" @error="useFallbackFace"></td>
            <td>{{ request.code }}</td>
            <td>{{ request.dept }}</td>
            <td>{{ request.name }}</td>
            <td>{{ request.phone }}</td>
            <td>{{ request.idCard }}</td>
            <td>{{ request.reason }}</td>
            <td>{{ formatDateTime(request.applyTime) }}</td>
            <td><span :class="deleteClass(request.status)" class="tag">{{ request.status }}</span></td>
            <td class="right actions-cell">
              <button class="row-act" title="查看" type="button" @click="openDeleteDetail(request)"><span
                  class="material-icons-outlined">visibility</span></button>
              <template v-if="request.status === '待处理'">
                <button v-if="can('gate-person:review')" class="btn btn--primary btn--sm" type="button"
                        @click="approveDelete(request)"><span class="material-icons-outlined">check</span>同意
                </button>
                <button v-if="can('gate-person:review')" class="btn btn--soft btn--sm" type="button"
                        @click="rejectDelete(request)"><span class="material-icons-outlined">close</span>拒绝
                </button>
              </template>
            </td>
          </tr>
          <tr v-if="totalRows === 0">
            <td :colspan="activeTab === 'pending' ? 9 : 10" class="empty-row">
              {{ activeTab === 'pending' ? '暂无待审核人员' : activeTab === 'delete' ? '暂无删除申请' : '暂无记录' }}
            </td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{
          totalRows
        }} 条</span>
        <button :disabled="page <= 1" class="page-btn" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="number in pageNumbers" :key="number" :class="{ active: number === page }" class="page-btn"
                type="button" @click="changePage(number)">{{ number }}
        </button>
        <button :disabled="page >= totalPages" class="page-btn" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="detailPerson || detailRequest" class="modal-mask" @click.self="closeDetail">
      <div class="modal modal--detail">
        <header class="modal__head"><h2 class="modal__title">{{ detailRequest ? '删除申请详情' : '人员详情' }}</h2>
          <button class="icon-btn" title="关闭" type="button" @click="closeDetail"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body detail-body"><img :src="faceSrc((detailPerson || detailRequest)?.face)"
                                                  alt="人脸照片" class="detail-face">
          <table class="table table--compact">
            <tbody>
            <tr>
              <td>编号</td>
              <td>{{ (detailPerson || detailRequest)?.code }}</td>
            </tr>
            <tr>
              <td>单位</td>
              <td>{{ (detailPerson || detailRequest)?.dept }}</td>
            </tr>
            <tr>
              <td>用户名</td>
              <td>{{ (detailPerson || detailRequest)?.name }}</td>
            </tr>
            <tr>
              <td>手机号</td>
              <td>{{ (detailPerson || detailRequest)?.phone }}</td>
            </tr>
            <tr>
              <td>身份证</td>
              <td>{{ (detailPerson || detailRequest)?.idCard }}</td>
            </tr>
            <tr v-if="detailRequest">
              <td>删除原因</td>
              <td>{{ detailRequest.reason }}</td>
            </tr>
            <tr>
              <td>{{ detailRequest ? '申请时间' : '入库时间' }}</td>
              <td>{{ formatDateTime(detailRequest?.applyTime || detailPerson?.createTime) }}</td>
            </tr>
            <tr>
              <td>{{ detailRequest ? '状态' : '审批状态' }}</td>
              <td><span v-if="detailRequest" :class="deleteClass(detailRequest.status)"
                        class="tag">{{ detailRequest.status }}</span><span
                  v-else-if="detailPerson" :class="approveClass(detailPerson.approveStatus)"
                  class="tag">{{ detailPerson.approveStatus }}</span></td>
            </tr>
            <tr v-if="detailPerson">
              <td>同步状态</td>
              <td>{{ detailPerson.syncStatus }}</td>
            </tr>
            </tbody>
          </table>
        </div>
        <footer class="modal__foot">
          <button class="btn btn--ghost" type="button" @click="closeDetail">关闭</button>
        </footer>
      </div>
    </div>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="savePerson">
        <header class="modal__head"><h2 class="modal__title">{{ editingId ? '编辑人员' : '新增人员' }}</h2>
          <button class="icon-btn" title="关闭" type="button" @click="editorVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid"><label class="field"><span class="field__label">编号<em>*</em></span><input
              v-model.trim="form.code" class="input" placeholder="人员编号" required></label><label class="field"><span
              class="field__label">单位<em>*</em></span><input v-model.trim="form.dept" class="input" list="gate-depts"
                                                               placeholder="所属单位" required>
            <datalist id="gate-depts">
              <option v-for="dept in departmentOptions" :key="dept" :value="dept"></option>
            </datalist>
          </label><label class="field"><span class="field__label">用户名<em>*</em></span><input v-model.trim="form.name"
                                                                                                class="input" placeholder="请输入用户名"
                                                                                                required></label><label
              class="field"><span class="field__label">手机号<em>*</em></span><input v-model.trim="form.phone"
                                                                                     class="input" placeholder="请输入手机号"
                                                                                     required></label><label
              class="field"><span class="field__label">身份证<em>*</em></span><input v-model.trim="form.idCard"
                                                                                     class="input" placeholder="18 位身份证号"
                                                                                     required></label><label
              class="field field--full"><span class="field__label">人脸照片<em v-if="!editingId">*</em></span><input
              ref="faceInput" :required="!editingId" accept="image/*" class="input file-input" type="file"
              @change="selectFace"><span class="field__hint">支持 jpg/png/gif/webp/bmp，不超过 2MB</span></label></div>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="btn btn--ghost" type="button" @click="editorVisible = false">取消</button>
          <button :disabled="saving" class="btn btn--primary" type="submit">{{ saving ? '保存中...' : '保存' }}</button>
        </footer>
      </form>
    </div>

    <div v-if="importVisible" class="modal-mask" @click.self="importVisible = false">
      <div class="modal">
        <header class="modal__head"><h2 class="modal__title">导入人员信息</h2>
          <button class="icon-btn" title="关闭" type="button" @click="importVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body"><input ref="importInput" accept=".xlsx,.xls" class="input file-input" type="file"
                                        @change="selectImport">
          <p class="import-hint">使用 Excel 模板导入，列为：人员编号、部门、姓名、手机号、身份证号。导入的人员一律为「审核中 /
            未同步」，需在「待审核人员」中审核。样表不含人脸照片，导入后请逐条补充照片。</p>
          <p class="import-hint">
            <button :disabled="busy === 'template'" class="btn btn--ghost btn--sm" type="button"
                    @click="downloadTemplate"><span class="material-icons-outlined">description</span>{{
                busy === "template" ? "下载中..." : "下载导入模板"
              }}
            </button>
          </p>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal__foot">
          <button class="btn btn--ghost" type="button" @click="importVisible = false">取消</button>
          <button :disabled="busy === 'import'" class="btn btn--primary" type="button" @click="importExcel">
            {{ busy === "import" ? "导入中..." : "确认导入" }}
          </button>
        </footer>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
interface Person {
  id: number;
  code: string;
  dept: string;
  name: string;
  phone: string;
  idCard: string;
  face?: string | null;
  createTime: string;
  approveStatus: string;
  syncStatus: string
}

interface DeleteRequest extends Person {
  reason: string;
  applyTime: string;
  status: string
}

const http = useHttp();
const {can} = usePermission();
const runtimeConfig = useRuntimeConfig();
const token = useCookie<string | null>("cartask_auth_token");
const fallbackFace = "/favicon.ico";
const activeTab = ref<"all" | "pending" | "delete">("all");
const persons = ref<Person[]>([]);
const requests = ref<DeleteRequest[]>([]);
const personTotal = ref(0);
const requestTotal = ref(0);
const loading = ref(true);
const saving = ref(false);
const busy = ref("");
const errorMessage = ref("");
const successMessage = ref("");
/** 操作类错误（导出、审核等）不能复用 errorMessage：那个会把整张表与分页一起换成错误态。 */
const actionError = ref("");
const formError = ref("");
const page = ref(1);
const pageSize = 8;
const filters = reactive({keyword: "", dept: "", approveStatus: ""});
const deleteStatus = ref("");
const editorVisible = ref(false);
const importVisible = ref(false);
const editingId = ref<number | null>(null);
const detailPerson = ref<Person | null>(null);
const detailRequest = ref<DeleteRequest | null>(null);
const selectedFace = ref<File | null>(null);
const selectedImport = ref<File | null>(null);
const faceInput = ref<HTMLInputElement>();
const importInput = ref<HTMLInputElement>();
const form = reactive({code: "", dept: "", name: "", phone: "", idCard: ""});
const departmentOptions = ref<string[]>([]);
const departmentFilterError = ref("");

/**
 * 人脸照片由受鉴权保护的下载接口提供，<img> 不会带上 Bearer 头，直接放到 src 一定是 401/403。
 * 所以先按同一套凭据取 blob，再换成 object URL；取不到的（例如历史 mock 值）保持占位图。
 */
const faceObjectUrls = ref<Record<string, string>>({});
const loadingFaces = new Set<string>();
/** 列表请求序号：连续改筛选会并发多次加载，靠它丢弃过期响应。 */
let loadSequence = 0;
/** 与后端 ParkingApiController.BATCH_REVIEW_MAX 保持一致。 */
const BATCH_REVIEW_SIZE = 200;

const totalRows = computed(() => activeTab.value === "delete" ? requestTotal.value : personTotal.value);
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / pageSize)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
/** 列表已由服务端按范围、关键词与状态筛选并分页，这里不再重复过滤，否则总数与行数会对不上。 */
const visiblePersons = computed(() => activeTab.value === "delete" ? [] : persons.value as Person[]);
const visibleRequests = computed(() => activeTab.value === "delete" ? requests.value as DeleteRequest[] : []);

watch(activeTab, () => {
  page.value = 1;
  resetFilters(false);
});
watch([filters, deleteStatus], () => {
  if (page.value > totalPages.value) page.value = 1;
}, {deep: true});

function apiBase() {
  return String(runtimeConfig.public.baseUrl || "http://127.0.0.1:8080/api").replace(/\/$/, "");
}

function authorization() {
  return token.value ? {Authorization: /^Bearer\s/i.test(token.value) ? token.value : `Bearer ${token.value}`} : {};
}

/**
 * 导入导出必须用原生 fetch 才能拿 blob 与 multipart，但 useHttp 的登录失效处理要自己补上：
 * 否则 token 过期时用户只看到「导出失败（401）」，本地 token 不清、也不会回到登录页。
 */
async function authorizedFetch(url: string, init?: RequestInit) {
  const response = await fetch(url, {
    ...init,
    headers: {...authorization(), ...(init?.headers as Record<string, string> | undefined)},
  });
  if (response.status === 401) {
    token.value = null;
    await navigateTo("/login");
  }
  return response;
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

function faceSrc(face?: string | null) {
  return (face && faceObjectUrls.value[face]) || fallbackFace;
}

async function loadFace(face?: string | null) {
  // 只处理真正的下载地址；历史数据里的档案标识不是 URL，取不到就保持占位图。
  if (!face || !/^https?:\/\//i.test(face) || faceObjectUrls.value[face] || loadingFaces.has(face)) return;
  loadingFaces.add(face);
  try {
    const response = await fetch(face, {headers: authorization()});
    if (!response.ok) return;
    faceObjectUrls.value = {...faceObjectUrls.value, [face]: URL.createObjectURL(await response.blob())};
  } catch {
    // 单张人脸取不到不应该打断整个列表，占位图已足够提示。
  } finally {
    loadingFaces.delete(face);
  }
}

function loadVisibleFaces() {
  const needed = new Set<string>();
  persons.value.forEach((person) => {
    if (person.face) needed.add(person.face);
    void loadFace(person.face);
  });
  requests.value.forEach((request) => {
    if (request.face) needed.add(request.face);
    void loadFace(request.face);
  });
  // 释放已经不在当前结果集里的人脸 object URL：翻页与切换筛选会不断产生新的 blob，
  // 只靠卸载时统一回收会让内存一直涨。
  const entries = Object.entries(faceObjectUrls.value);
  const stale = entries.filter(([face]) => !needed.has(face));
  if (!stale.length) return;
  stale.forEach(([, url]) => URL.revokeObjectURL(url));
  faceObjectUrls.value = Object.fromEntries(entries.filter(([face]) => needed.has(face)));
}

async function loadDepartments() {
  if (!can("department:read")) return;
  try {
    const data = await http.get<{ id: number; name: string }[]>("/depts");
    departmentOptions.value = [...new Set((data || []).map((item) => item.name).filter(Boolean))];
  } catch {
    // 部门候选保持为空，避免把列表中的历史单位误当作当前有效部门。
  }
}

async function loadData() {
  const requestId = ++loadSequence;
  loading.value = true;
  errorMessage.value = "";
  try {
    const personData = await http.get<{ items: Person[]; total: number }>("/gate-persons", {
      keyword: filters.keyword || undefined,
      dept: filters.dept || undefined,
      approveStatus: activeTab.value === "pending" ? "审核中" : filters.approveStatus || undefined,
      page: page.value,
      pageSize,
    });
    const requestData = await http.get<{ items: DeleteRequest[]; total: number }>("/gate-persons/delete-requests", {
      keyword: filters.keyword || undefined,
      status: deleteStatus.value || undefined,
      page: page.value,
      page_size: pageSize,
    });
    // 连续改筛选会并发多次 loadData，晚到的旧响应不能覆盖新结果。
    if (requestId !== loadSequence) return;
    persons.value = personData.items || [];
    personTotal.value = personData.total || 0;
    requests.value = requestData.items || [];
    requestTotal.value = requestData.total || 0;
    loadVisibleFaces();
  } catch (error) {
    if (requestId === loadSequence) errorMessage.value = (error as {
      statusMessage?: string
    }).statusMessage || "门禁人员数据加载失败";
  } finally {
    if (requestId === loadSequence) loading.value = false;
  }
}

async function search() {
  if (!validateDepartmentFilter()) return;
  page.value = 1;
  successMessage.value = "";
  await loadData();
}

function validateDepartmentFilter() {
  const value = filters.dept.trim();
  filters.dept = value;
  if (!value) {
    departmentFilterError.value = "";
    return true;
  }
  if (departmentOptions.value.includes(value)) {
    departmentFilterError.value = "";
    return true;
  }
  departmentFilterError.value = "请选择现有部门";
  return false;
}

async function resetFilters(resetKeyword = true) {
  if (resetKeyword) filters.keyword = "";
  filters.dept = "";
  departmentFilterError.value = "";
  filters.approveStatus = "";
  deleteStatus.value = "";
  page.value = 1;
  await loadData();
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  void loadData();
}

function approveClass(status: string) {
  return status === "通过" ? "tag--green" : status === "审核中" ? "tag--blue" : "tag--red";
}

function deleteClass(status: string) {
  return status === "待处理" ? "tag--orange" : status === "已同意" ? "tag--green" : "tag--red";
}

function useFallbackFace(event: Event) {
  (event.target as HTMLImageElement).src = fallbackFace;
}

async function openDetail(person: Person) {
  detailRequest.value = null;
  try {
    detailPerson.value = await http.get<Person>(`/gate-persons/${person.id}`);
  } catch {
    detailPerson.value = person;
  }
}

function openDeleteDetail(request: DeleteRequest) {
  detailPerson.value = null;
  detailRequest.value = request;
}

function closeDetail() {
  detailPerson.value = null;
  detailRequest.value = null;
}

/** 原型按「已有数量 + 1」预填编号。这里只在没有筛选时用服务端总数推导：
 * 筛选状态下 personTotal 是过滤后的总数，推出来的编号几乎必然与库中已有编号冲突，
 * 那种情况留空交给用户填，唯一性最终由服务端校验。 */
function nextCode() {
  if (filters.keyword || filters.dept || filters.approveStatus) return "";
  const used = new Set(persons.value.map((person) => person.code));
  let index = personTotal.value + 1;
  while (used.has(`GP${String(index).padStart(4, "0")}`)) index += 1;
  return `GP${String(index).padStart(4, "0")}`;
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {code: nextCode(), dept: "", name: "", phone: "", idCard: ""});
  selectedFace.value = null;
  formError.value = "";
  editorVisible.value = true;
}

function openEdit(person: Person) {
  editingId.value = person.id;
  Object.assign(form, {
    code: person.code,
    dept: person.dept,
    name: person.name,
    phone: person.phone,
    idCard: person.idCard
  });
  selectedFace.value = null;
  formError.value = "";
  editorVisible.value = true;
}

function selectFace(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] || null;
  if (file && file.size > 2 * 1024 * 1024) {
    formError.value = "人脸照片不能超过 2MB";
    selectedFace.value = null;
    (event.target as HTMLInputElement).value = "";
    return;
  }
  formError.value = "";
  selectedFace.value = file;
}

function selectImport(event: Event) {
  selectedImport.value = (event.target as HTMLInputElement).files?.[0] || null;
  formError.value = "";
}

async function savePerson() {
  if (!form.code || !form.dept || !form.name || !form.phone || !form.idCard || (!editingId.value && !selectedFace.value)) {
    formError.value = "编号、单位、用户名、手机号、身份证和人脸照片不能为空";
    return;
  }
  saving.value = true;
  formError.value = "";
  successMessage.value = "";
  try {
    const body = new FormData();
    body.append("code", form.code);
    body.append("dept", form.dept);
    body.append("name", form.name);
    body.append("phone", form.phone);
    body.append("idCard", form.idCard);
    if (selectedFace.value) body.append("face", selectedFace.value);
    if (editingId.value) await http.put(`/gate-persons/${editingId.value}`, body, {payloadMode: "json"}); else await http.post("/gate-persons", body, {payloadMode: "json"});
    editorVisible.value = false;
    successMessage.value = editingId.value ? "人员已更新，需重新审核" : "人员已录入，等待审核";
    await loadData();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function requestDelete(person: Person) {
  const reason = window.prompt(`请输入删除“${person.name}”的原因`);
  if (!reason?.trim()) return;
  actionError.value = "";
  try {
    await http.post(`/gate-persons/${person.id}/delete-requests`, {reason: reason.trim()}, {payloadMode: "json"});
    successMessage.value = "删除申请已提交，等待审核";
    await loadData();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "提交删除申请失败";
  }
}

async function approvePerson(person: Person) {
  await updateApproval(person, "approve", undefined);
}

async function rejectPerson(person: Person) {
  const reason = window.prompt(`请输入拒绝“${person.name}”的原因（可留空）`);
  if (reason === null) return;
  await updateApproval(person, "reject", reason.trim() || undefined);
}

async function updateApproval(person: Person, action: "approve" | "reject", reason?: string) {
  busy.value = `review-${person.id}`;
  successMessage.value = "";
  actionError.value = "";
  try {
    await http.put(`/gate-persons/${person.id}/${action}`, {reason});
    successMessage.value = action === "approve" ? `已通过【${person.name}】` : `已拒绝【${person.name}】`;
    await loadData();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "审批操作失败";
  } finally {
    busy.value = "";
  }
}

/** 批量通过要覆盖筛选后的全部待审核人员：只处理当前页，或忽略筛选条件去审看不见的人，都是错的。 */
async function pendingIds() {
  const ids: number[] = [];
  let currentPage = 1;
  while (true) {
    const data = await http.get<{ items: Person[]; total: number }>("/gate-persons", {
      keyword: filters.keyword || undefined,
      dept: filters.dept || undefined,
      approveStatus: "审核中",
      page: currentPage,
      pageSize: 100,
    });
    const items = data.items || [];
    ids.push(...items.map((person) => person.id));
    if (items.length === 0 || ids.length >= (data.total || 0)) return ids;
    currentPage += 1;
  }
}

async function approveAll() {
  if (busy.value) return;
  busy.value = "batch";
  successMessage.value = "";
  actionError.value = "";
  const ids = await pendingIds().catch(() => [] as number[]);
  if (!ids.length) {
    successMessage.value = "没有待审核人员";
    busy.value = "";
    return;
  }
  if (!window.confirm(`确认批量通过 ${ids.length} 名待审核人员？`)) {
    busy.value = "";
    return;
  }
  let reviewed = 0;
  try {
    // 与后端 BATCH_REVIEW_MAX 对齐分批提交：一次请求审太多人会把一个事务与逐条审计写放大成 N 倍。
    for (let start = 0; start < ids.length; start += BATCH_REVIEW_SIZE) {
      const chunk = ids.slice(start, start + BATCH_REVIEW_SIZE);
      const result = await http.put<{ reviewed: number }>("/gate-persons/reviews", {
        ids: chunk,
        approved: true
      }, {payloadMode: "json"});
      reviewed += result?.reviewed ?? chunk.length;
    }
    successMessage.value = `已通过 ${reviewed} 人`;
  } catch (error) {
    // 分批提交后失败不会回滚前面已成功的批次，必须把已生效的条数说清楚。
    const message = (error as { statusMessage?: string }).statusMessage || "批量审核失败";
    actionError.value = reviewed ? `${message}（已成功 ${reviewed} 人，其余未处理）` : message;
  } finally {
    await loadData();
    busy.value = "";
  }
}

async function approveDelete(request: DeleteRequest) {
  await updateDeleteRequest(request, "approve");
}

async function rejectDelete(request: DeleteRequest) {
  await updateDeleteRequest(request, "reject");
}

async function updateDeleteRequest(request: DeleteRequest, action: "approve" | "reject") {
  busy.value = `delete-${request.id}`;
  successMessage.value = "";
  actionError.value = "";
  try {
    await http.put(`/gate-persons/delete-requests/${request.id}/${action}`);
    successMessage.value = action === "approve" ? `已同意删除【${request.name}】` : `已拒绝删除【${request.name}】`;
    await loadData();
  } catch (error) {
    actionError.value = (error as { statusMessage?: string }).statusMessage || "删除申请处理失败";
  } finally {
    busy.value = "";
  }
}

function openImport() {
  selectedImport.value = null;
  formError.value = "";
  successMessage.value = "";
  importVisible.value = true;
}

async function downloadTemplate() {
  busy.value = "template";
  try {
    const response = await authorizedFetch(`${apiBase()}/excel/gate-persons/template`);
    if (!response.ok) throw new Error(`模板下载失败（${response.status}）`);
    saveBlob(await response.blob(), "门禁人员导入模板.xlsx");
  } catch (error) {
    formError.value = error instanceof Error ? error.message : "模板下载失败";
  } finally {
    busy.value = "";
  }
}

async function importExcel() {
  if (!selectedImport.value) {
    formError.value = "请选择 Excel 文件";
    return;
  }
  busy.value = "import";
  formError.value = "";
  try {
    const body = new FormData();
    body.append("file", selectedImport.value);
    const response = await authorizedFetch(`${apiBase()}/excel/gate-persons/import`, {method: "POST", body});
    const result = await response.json().catch(() => ({})) as { message?: string; data?: { count?: number } };
    if (!response.ok) throw new Error(result.message || `导入失败（${response.status}）`);
    importVisible.value = false;
    successMessage.value = `导入完成，共 ${result.data?.count ?? 0} 人，均为待审核状态`;
    await loadData();
  } catch (error) {
    formError.value = error instanceof Error ? error.message : "导入失败";
  } finally {
    if (importInput.value) importInput.value.value = "";
    busy.value = "";
  }
}

/** 导出走后端的全量导出：前端只导当前页会把绝大部分数据静默丢掉。 */
async function exportPersons() {
  busy.value = "export";
  actionError.value = "";
  successMessage.value = "";
  try {
    const response = await authorizedFetch(`${apiBase()}/excel/gate-persons/export`);
    if (!response.ok) throw new Error(`导出失败（${response.status}）`);
    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") || "";
    saveBlob(blob, decodeURIComponent(disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || "门禁人员列表.xlsx"));
    successMessage.value = "导出完成";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "导出失败";
  } finally {
    busy.value = "";
  }
}

function saveBlob(blob: Blob, filename: string) {
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

onMounted(async () => {
  await Promise.all([loadDepartments(), loadData()]);
});
onBeforeUnmount(() => {
  Object.values(faceObjectUrls.value).forEach((url) => URL.revokeObjectURL(url));
});
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadData);
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

.page__actions, .toolbar__right {
  display: flex;
  gap: 8px;
}

.tabs {
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
}

.gp-tab {
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

.gp-tab:hover {
  color: var(--text);
}

.gp-tab--active {
  border-bottom-color: var(--primary);
  color: var(--primary);
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
}

.card__body {
  padding: 18px;
}

.filter-body {
  padding-bottom: 4px;
}

.toolbar {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.toolbar .input, .toolbar .select {
  min-width: 140px;
  width: auto;
}

.toolbar .dept-filter-input {
  min-width: 180px;
}

.toolbar__right {
  margin-left: auto;
}

.input, .select {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-sizing: border-box;
  color: var(--text);
  font: inherit;
  height: 34px;
  outline: none;
  padding: 0 10px;
}

.input:focus, .select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft);
}

.filter-error {
  color: var(--red);
  font-size: 12px;
  margin: -8px 18px 12px;
}

.btn {
  align-items: center;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 13px;
  gap: 5px;
  height: 32px;
  padding: 0 12px;
  transition: all var(--tr);
  white-space: nowrap;
}

.btn--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
}

.btn--primary {
  background: var(--primary);
  color: var(--on-solid);
}

.btn--soft {
  background: var(--primary-soft);
  color: var(--primary);
}

.btn--ghost {
  background: var(--card);
  border: 1px solid var(--border-strong);
  color: var(--text-sub);
}

.btn:hover:not(:disabled) {
  filter: brightness(.97);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: .6;
}

.btn .material-icons-outlined {
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

table.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 980px;
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

.table tbody tr:last-child td {
  border-bottom: none;
}

.right {
  text-align: right !important;
}

.actions-cell {
  white-space: nowrap;
}

.record-face {
  border: 1px solid var(--border);
  border-radius: 4px;
  height: 48px;
  object-fit: cover;
  width: 48px;
}

.row-act, .icon-btn {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 5px;
  color: var(--text-mute);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  margin: 0 1px;
  width: 28px;
}

.row-act:hover, .icon-btn:hover {
  background: var(--bg);
  color: var(--text);
}

.row-act--danger:hover {
  background: var(--red-soft);
  color: var(--red);
}

.row-act .material-icons-outlined {
  font-size: 16px;
}

.tag {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 500;
  gap: 4px;
  line-height: 1.5;
  padding: 2px 8px;
}

.tag::before {
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px;
}

.tag--green {
  background: var(--success-soft);
  color: var(--success);
}

.tag--green::before {
  background: var(--success);
}

.tag--blue {
  background: var(--primary-soft);
  color: var(--primary);
}

.tag--blue::before {
  background: var(--primary);
}

.tag--orange {
  background: var(--warning-soft);
  color: var(--orange);
}

.tag--orange::before {
  background: var(--orange);
}

.tag--red {
  background: var(--danger-soft);
  color: var(--red);
}

.tag--red::before {
  background: var(--red);
}

.empty-row {
  color: var(--text-mute) !important;
  padding: 40px !important;
  text-align: center !important;
}

.state {
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
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
  padding: 12px 16px;
}

.pagination__info {
  color: var(--text-sub);
  font-size: 12px;
  margin-right: auto;
}

.page-btn {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  font-size: 12px;
  height: 28px;
  justify-content: center;
  min-width: 28px;
  padding: 0 6px;
  transition: all var(--tr);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.page-btn.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--on-solid);
}

.page-btn:disabled {
  cursor: not-allowed;
  opacity: .4;
}

.page-btn .material-icons-outlined {
  font-size: 16px;
}

.modal-mask {
  align-items: center;
  background: rgb(0 0 0 / 40%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 100;
}

.modal {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 40px);
  max-width: calc(100vw - 40px);
  overflow: hidden;
  width: 640px;
}

.modal--detail {
  width: 640px;
}

.modal__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 14px 20px;
}

.modal__title {
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.modal__body {
  overflow-y: auto;
  padding: 20px;
}

.modal__foot {
  border-top: 1px solid var(--border);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 20px;
}

.detail-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.detail-face {
  border: 1px solid var(--border);
  border-radius: 6px;
  flex-shrink: 0;
  height: 140px;
  object-fit: cover;
  width: 105px;
}

.table--compact {
  margin: 0;
  min-width: 0 !important;
}

.table--compact td {
  padding: 8px 12px;
}

.table--compact td:first-child {
  color: var(--text-sub);
  width: 100px;
}

.form-grid {
  display: grid;
  gap: 0 16px;
  grid-template-columns: 1fr 1fr;
}

.field {
  display: block;
  margin-bottom: 14px;
}

.field__label {
  color: var(--text-sub);
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 5px;
}

.field__label em {
  color: var(--red);
  font-style: normal;
  margin-left: 2px;
}

.field .input {
  width: 100%;
}

.field--full {
  grid-column: 1 / -1;
}

.field__hint {
  color: var(--text-mute);
  display: block;
  font-size: 12px;
  margin-top: 5px;
}

.file-input {
  padding-top: 7px;
}

.import-hint {
  color: var(--text-sub);
  font-size: 12px;
  margin: 10px 0 0;
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .input, .toolbar .select {
    width: 100%;
  }

  .toolbar__right {
    justify-content: flex-end;
    margin-left: 0;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .field--full {
    grid-column: auto;
  }

  .detail-body {
    flex-direction: column;
  }

  .modal {
    max-width: calc(100vw - 24px);
  }

  .tabs {
    overflow-x: auto;
  }
}
</style>
