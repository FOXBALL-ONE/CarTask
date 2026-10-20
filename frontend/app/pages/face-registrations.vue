<template>
  <section class="face-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">门禁资料 / 人脸数据登记</p>
        <h1>人脸数据登记</h1>
        <p class="lede">登记一张照片和必要身份信息，审核通过后再进入设备下发队列。</p>
      </div>
      <div class="head-actions">
        <button v-if="can('gate-person:manage')" class="btn btn-quiet" type="button" @click="importOpen = true">
          批量导入
        </button>
        <button v-if="can('gate-person:export')" class="btn btn-quiet" type="button" @click="exportRows">导出</button>
        <button v-if="can('gate-person:manage')" class="btn btn-quiet" type="button" @click="downloadTemplate">
          下载模板
        </button>
        <button v-if="can('gate-person:manage')" class="btn btn-primary" type="button" @click="openEditor()">新增登记
        </button>
        <button v-if="can('gate-person:review') && selectedIds.length" class="btn btn-primary" type="button"
                @click="batchReview(true)">批量通过 ({{ selectedIds.length }})
        </button>
        <button v-if="can('gate-person:review') && selectedIds.length" class="btn btn-danger" type="button"
                @click="batchReview(false)">批量拒绝
        </button>
      </div>
    </header>

    <div class="status-strip">
      <span class="unit-mark"><span class="material-icons-outlined">apartment</span>{{
          workingDepartment || '当前工作单位'
        }}</span>
      <span class="strip-copy">所有新登记自动进入审核中；身份证号和照片受权限保护。</span>
      <span class="strip-count">{{ total }} 条记录</span>
    </div>

    <section aria-label="筛选人脸登记" class="toolbar">
      <label class="search-field"><span class="material-icons-outlined">search</span><input v-model="filters.keyword"
                                                                                            placeholder="搜索姓名、手机号、编号或身份证后四位"
                                                                                            @keyup.enter="loadRows"></label>
      <select v-model="filters.approve_status" aria-label="审核状态" @change="loadRows">
        <option value="">全部审核状态</option>
        <option value="PENDING">审核中</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已拒绝</option>
      </select>
      <select v-model="filters.sync_status" aria-label="同步状态" @change="loadRows">
        <option value="">全部同步状态</option>
        <option value="NOT_SYNCED">未同步</option>
        <option value="SYNCED">已同步</option>
      </select>
      <button class="icon-action" title="刷新列表" type="button" @click="loadRows"><span
          class="material-icons-outlined">refresh</span></button>
    </section>

    <div v-if="notice" :class="noticeType" class="notice">{{ notice }}</div>
    <section class="table-wrap">
      <table>
        <thead>
        <tr>
          <th class="select-col"><input v-model="selectAll" aria-label="选择当前页审核中记录" type="checkbox"></th>
          <th class="photo-col">照片</th>
          <th>姓名 / 编号</th>
          <th>手机号</th>
          <th>身份证号</th>
          <th>单位</th>
          <th>审核</th>
          <th>更新</th>
          <th class="action-col">操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td class="select-col"><input v-if="can('gate-person:review') && row.approve_status === '审核中'"
                                        :aria-label="`选择 ${row.name}`"
                                        :checked="selectedIds.includes(row.id)"
                                        :disabled="!selectedIds.includes(row.id) && selectedIds.length >= 200" type="checkbox"
                                        @change="toggleSelected(row.id, ($event.target as HTMLInputElement).checked)">
          </td>
          <td class="photo-col"><img :src="photoSrc(row)" alt="登记照片" class="thumb" @error="fallbackPhoto"></td>
          <td><strong>{{ row.name }}</strong><small>{{ row.code }}</small></td>
          <td>{{ row.phone }}</td>
          <td class="sensitive">{{ row.id_card }}</td>
          <td>{{ row.dept }}</td>
          <td><span :class="statusClass(row.approve_status)" class="status">{{ row.approve_status }}</span></td>
          <td class="date-cell">{{ formatDate(row.updated_at) }}</td>
          <td class="row-actions">
            <button class="text-action" type="button" @click="openDetail(row)">详情</button>
            <button v-if="can('gate-person:manage')" class="text-action" type="button" @click="openEditor(row)">编辑
            </button>
            <button v-if="can('gate-person:review') && row.approve_status === '审核中'" class="text-action review"
                    type="button" @click="openReview(row)">审核
            </button>
          </td>
        </tr>
        <tr v-if="!loading && !rows.length">
          <td class="empty" colspan="9">没有匹配的人脸登记</td>
        </tr>
        <tr v-if="loading">
          <td class="empty" colspan="9">正在读取登记记录…</td>
        </tr>
        </tbody>
      </table>
    </section>
    <footer class="pager"><span>共 {{ total }} 条</span>
      <button :disabled="page === 1" class="icon-action" title="上一页" type="button" @click="page--; loadRows()"><span
          class="material-icons-outlined">chevron_left</span></button>
      <strong>{{ page }} / {{ pages }}</strong>
      <button :disabled="page >= pages" class="icon-action" title="下一页" type="button" @click="page++; loadRows()">
        <span class="material-icons-outlined">chevron_right</span></button>
    </footer>

    <div v-if="editorOpen" class="modal-mask" @click.self="closeEditor">
      <form class="modal" @submit.prevent="saveEditor">
        <header class="modal-head">
          <div><p class="eyebrow">{{ editing ? '修改登记' : '新建登记' }}</p>
            <h2>{{ editing ? '更新身份资料' : '登记人脸资料' }}</h2></div>
          <button class="icon-action" title="关闭" type="button" @click="closeEditor"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal-body">
          <div class="form-grid"><label>姓名<input v-model.trim="form.name" maxlength="128" required></label><label>手机号<input
              v-model.trim="form.phone" inputmode="numeric" required></label><label class="wide">身份证号<input
              v-model.trim="form.id_card" :required="!editing" maxlength="18"
              @input="form.id_card = form.id_card.toUpperCase()"><small>{{
              editing && !form.id_card ? '当前账号无权查看完整号码；留空表示保持原号码。' : '支持 18 位号码，末位 x 会自动转为 X。'
            }}</small></label><label v-if="canChooseDepartment" class="wide">工作单位<select
              v-model="form.department_code">
            <option value="">使用当前工作单位</option>
            <option v-for="department in departments" :key="department.code" :value="department.code">{{
                department.name
              }}
            </option>
          </select></label><label class="wide photo-picker">照片<input ref="photoInput" :required="!editing"
                                                                       accept=".jpg,.jpeg,.png,.webp"
                                                                       type="file"
                                                                       @change="selectPhoto"><small>JPEG、PNG 或 WEBP，最大
            2MB。只保存一张照片。</small></label></div>
          <div v-if="previewUrl" class="preview"><img :src="previewUrl" alt="照片预览"><span>照片预览</span></div>
          <p v-if="formError" class="form-error">{{ formError }}</p></div>
        <footer class="modal-foot">
          <button class="btn btn-quiet" type="button" @click="closeEditor">取消</button>
          <button :disabled="saving" class="btn btn-primary" type="submit">
            {{ saving ? '保存中…' : editing ? '保存修改' : '提交审核' }}
          </button>
        </footer>
      </form>
    </div>

    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <article class="modal detail-modal">
        <header class="modal-head">
          <div><p class="eyebrow">登记详情</p>
            <h2>{{ detail.name }}</h2></div>
          <button class="icon-action" title="关闭" type="button" @click="detail = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="detail-layout"><img :src="photoSrc(detail)" alt="登记照片" class="detail-photo">
          <dl>
            <dt>人员编号</dt>
            <dd>{{ detail.code }}</dd>
            <dt>手机号</dt>
            <dd>{{ detail.phone }}</dd>
            <dt>身份证号</dt>
            <dd>{{ detail.id_card }}</dd>
            <dt>所属单位</dt>
            <dd>{{ detail.dept }}</dd>
            <dt>状态</dt>
            <dd><span :class="statusClass(detail.approve_status)" class="status">{{ detail.approve_status }}</span></dd>
            <dt>更新时间</dt>
            <dd>{{ formatDate(detail.updated_at) }}</dd>
          </dl>
        </div>
        <footer class="modal-foot">
          <button class="btn btn-quiet" type="button" @click="detail = null">关闭</button>
        </footer>
      </article>
    </div>

    <div v-if="reviewing" class="modal-mask" @click.self="reviewing = null">
      <form class="modal review-modal" @submit.prevent="submitReview">
        <header class="modal-head">
          <div><p class="eyebrow">审核登记</p>
            <h2>{{ reviewing.name }}</h2></div>
          <button class="icon-action" title="关闭" type="button" @click="reviewing = null"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal-body"><p>请选择审核结果。拒绝时必须留下原因，登记人修改后可以重新提交。</p><textarea
            v-model.trim="reviewReason" :required="reviewDecision === false" maxlength="500"
            placeholder="拒绝原因（必填）"></textarea></div>
        <footer class="modal-foot">
          <button class="btn btn-danger" type="button" @click="reviewDecision = false; submitReview()">拒绝</button>
          <button class="btn btn-primary" type="button" @click="reviewDecision = true; submitReview()">通过</button>
        </footer>
      </form>
    </div>

    <div v-if="importOpen" class="modal-mask" @click.self="importOpen = false">
      <form class="modal" @submit.prevent="previewImport">
        <header class="modal-head">
          <div><p class="eyebrow">批量登记</p>
            <h2>Excel + 照片包</h2></div>
          <button class="icon-action" title="关闭" type="button" @click="importOpen = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal-body"><label>Excel 文件<input accept=".xlsx" required type="file"
                                                        @change="importExcel = ($event.target as HTMLInputElement).files?.[0] || null"></label><label>照片压缩包<input
            accept=".zip" required type="file"
            @change="importZip = ($event.target as HTMLInputElement).files?.[0] || null"></label>
          <p class="import-note">照片文件名必须与 Excel
            中的“照片文件名”完全一致。预览会先校验整批数据，任何错误都不会写入。</p>
          <div v-if="importRows.length" class="import-results">
            <div v-for="item in importRows" :key="item.row" :class="{invalid: item.errors.length}"><strong>第
              {{ item.row }} 行 · {{ item.name }}</strong><span>{{
                item.errors.length ? item.errors.join('；') : '校验通过'
              }}</span></div>
          </div>
        </div>
        <footer class="modal-foot">
          <button class="btn btn-quiet" type="button" @click="importOpen = false">取消</button>
          <button :disabled="importing" class="btn btn-primary" type="submit">{{
              importing ? '校验中…' : '预览校验'
            }}
          </button>
          <button v-if="importValid" class="btn btn-primary" type="button" @click="commitImport">确认导入</button>
        </footer>
      </form>
    </div>
  </section>
</template>

<script lang="ts" setup>
interface Registration {
  id: number;
  code: string;
  name: string;
  phone: string;
  id_card: string;
  dept: string;
  department_code: string;
  photo_url?: string | null;
  approve_status: string;
  sync_status: string;
  updated_at: string
}

interface Department {
  code: string;
  name: string
}

interface ImportRow {
  row: number;
  name: string;
  errors: string[]
}

const http = useHttp();
const auth = useAuthStore();
const {can} = usePermission();
const runtime = useRuntimeConfig();
const token = useCookie<string | null>("cartask_auth_token");
const rows = ref<Registration[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(true);
const saving = ref(false);
const notice = ref("");
const noticeType = ref("success");
const selectedIds = ref<number[]>([]);
const filters = reactive({keyword: "", approve_status: "", sync_status: ""});
const departments = ref<Department[]>([]);
const detail = ref<Registration | null>(null);
const reviewing = ref<Registration | null>(null);
const editorOpen = ref(false);
const importOpen = ref(false);
const editing = ref<Registration | null>(null);
const formError = ref("");
const previewUrl = ref("");
const selectedPhoto = ref<File | null>(null);
const photoInput = ref<HTMLInputElement>();
const reviewReason = ref("");
const reviewDecision = ref<boolean | null>(null);
const importExcel = ref<File | null>(null);
const importZip = ref<File | null>(null);
const importRows = ref<ImportRow[]>([]);
const importValid = ref(false);
const importing = ref(false);
const form = reactive({name: "", phone: "", id_card: "", department_code: ""});
const workingDepartment = computed(() => auth.user?.working_department_name || "");
const canChooseDepartment = computed(() => ["SUPER_ADMIN", "ADMIN"].includes(auth.user?.role || ""));
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const selectAll = computed({
  get: () => rows.value.filter(row => row.approve_status === "审核中").length > 0 && rows.value.filter(row => row.approve_status === "审核中").every(row => selectedIds.value.includes(row.id)),
  set: (value: boolean) => {
    const currentPageIds = rows.value.filter(row => row.approve_status === "审核中").map(row => row.id);
    if (value) selectedIds.value = [...new Set([...selectedIds.value, ...currentPageIds])].slice(0, 200); else selectedIds.value = selectedIds.value.filter(id => !currentPageIds.includes(id));
  }
});
const baseUrl = () => String(runtime.public.baseUrl || "http://127.0.0.1:8080/api").replace(/\/$/, "");
const authHeaders = () => token.value ? {Authorization: /^Bearer\s/i.test(token.value) ? token.value : `Bearer ${token.value}`} : {};

async function loadRows() {
  loading.value = true;
  detail.value = null;
  try {
    const result = await http.get<{ items: Registration[]; total: number }>("/face-registrations", {
      ...filters,
      page: page.value,
      page_size: pageSize
    });
    rows.value = result.items || [];
    total.value = result.total || 0;
    await loadPhotos(rows.value);
  } catch (error) {
    showError((error as { statusMessage?: string }).statusMessage || "登记记录加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadPhotos(items: Registration[]) {
  const needed = new Set(items.map(item => item.id));
  for (const [id, url] of Object.entries(photoCache.value)) if (!needed.has(Number(id))) {
    URL.revokeObjectURL(url);
    delete photoCache.value[Number(id)];
  }
  await Promise.all(items.filter(item => item.photo_url && !photoCache.value[item.id]).map(async item => {
    try {
      const response = await fetch(`${baseUrl().replace(/\/api$/, "")}${item.photo_url}`, {headers: authHeaders()});
      if (response.ok) photoCache.value = {...photoCache.value, [item.id]: URL.createObjectURL(await response.blob())};
    } catch { /* 占位图保留 */
    }
  }));
}

function toggleSelected(id: number, checked: boolean) {
  selectedIds.value = checked ? [...new Set([...selectedIds.value, id])].slice(0, 200) : selectedIds.value.filter(item => item !== id);
}

const photoCache = ref<Record<number, string>>({});
const photoSrc = (item: Registration) => photoCache.value[item.id] || "/favicon.ico";

function fallbackPhoto(event: Event) {
  (event.target as HTMLImageElement).src = "/favicon.ico";
}

function formatDate(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

function statusClass(value: string) {
  return value === "已通过" ? "approved" : value === "已拒绝" ? "rejected" : "pending";
}

function showError(message: string) {
  notice.value = message;
  noticeType.value = "error";
}

function showSuccess(message: string) {
  notice.value = message;
  noticeType.value = "success";
  setTimeout(() => {
    notice.value = "";
  }, 2600);
}

function openEditor(item?: Registration) {
  editing.value = item || null;
  form.name = item?.name || "";
  form.phone = item?.phone || "";
  form.id_card = item?.id_card?.startsWith("*") ? "" : item?.id_card || "";
  form.department_code = item?.department_code || "";
  selectedPhoto.value = null;
  previewUrl.value = item ? photoSrc(item) : "";
  formError.value = "";
  editorOpen.value = true;
}

function closeEditor() {
  editorOpen.value = false;
  if (previewUrl.value && previewUrl.value.startsWith("blob:")) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = "";
}

function selectPhoto(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  selectedPhoto.value = file;
  if (previewUrl.value.startsWith("blob:")) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = URL.createObjectURL(file);
}

async function saveEditor() {
  saving.value = true;
  formError.value = "";
  try {
    const body = new FormData();
    body.append("name", form.name);
    body.append("phone", form.phone);
    if (form.id_card) body.append("id_card", form.id_card);
    if (form.department_code) body.append("department_code", form.department_code);
    if (selectedPhoto.value) body.append("photo", selectedPhoto.value);
    const url = editing.value ? `${baseUrl()}/face-registrations/${editing.value.id}` : `${baseUrl()}/face-registrations`;
    const response = await fetch(url, {method: editing.value ? "PUT" : "POST", headers: authHeaders(), body});
    if (!response.ok) throw new Error((await response.json()).message || "保存失败");
    closeEditor();
    showSuccess(editing.value ? "登记已更新并重新进入审核" : "登记已提交审核");
    await loadRows();
  } catch (error) {
    formError.value = (error as Error).message || "保存失败";
  } finally {
    saving.value = false;
  }
}

function openDetail(item: Registration) {
  detail.value = item;
}

function openReview(item: Registration) {
  reviewing.value = item;
  reviewReason.value = "";
  reviewDecision.value = null;
}

async function submitReview() {
  if (!reviewing.value || reviewDecision.value === null || (reviewDecision.value === false && !reviewReason.value)) return;
  try {
    await http.put(`/face-registrations/${reviewing.value.id}/review`, undefined, {
      params: {
        approved: reviewDecision.value,
        reason: reviewReason.value
      }
    });
    reviewing.value = null;
    showSuccess("审核结果已保存");
    await loadRows();
  } catch (error) {
    showError((error as { statusMessage?: string }).statusMessage || "审核失败");
  }
}

async function batchReview(approved: boolean) {
  const ids = [...selectedIds.value];
  if (!ids.length) return;
  const reason = approved ? undefined : window.prompt("请输入拒绝原因");
  if (!approved && !reason?.trim()) return;
  try {
    await http.put("/face-registrations/reviews", undefined, {params: {ids, approved, reason}});
    selectedIds.value = selectedIds.value.filter(id => !ids.includes(id));
    showSuccess(`已审核 ${ids.length} 条登记`);
    await loadRows();
  } catch (error) {
    showError((error as { statusMessage?: string }).statusMessage || "批量审核失败");
  }
}

async function downloadTemplate() {
  const response = await fetch(`${baseUrl()}/face-registrations/template`, {headers: authHeaders()});
  if (response.ok) triggerDownload(await response.blob(), "人脸登记导入模板.xlsx");
}

async function exportRows() {
  const query = new URLSearchParams(Object.entries(filters).filter(([, value]) => value));
  const response = await fetch(`${baseUrl()}/face-registrations/export?${query}`, {headers: authHeaders()});
  if (response.ok) triggerDownload(await response.blob(), "人脸登记导出.xlsx");
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

async function previewImport() {
  if (!importExcel.value || !importZip.value) return;
  importing.value = true;
  try {
    const body = new FormData();
    body.append("file", importExcel.value);
    body.append("photos", importZip.value);
    body.append("preview", "true");
    const response = await fetch(`${baseUrl()}/face-registrations/import`, {
      method: "POST",
      headers: authHeaders(),
      body
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "导入校验失败");
    importRows.value = data.data.rows;
    importValid.value = data.data.valid;
  } catch (error) {
    showError((error as Error).message);
  } finally {
    importing.value = false;
  }
}

async function commitImport() {
  if (!importExcel.value || !importZip.value) return;
  importing.value = true;
  try {
    const body = new FormData();
    body.append("file", importExcel.value);
    body.append("photos", importZip.value);
    const response = await fetch(`${baseUrl()}/face-registrations/import`, {
      method: "POST",
      headers: authHeaders(),
      body
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "导入失败");
    importOpen.value = false;
    importRows.value = [];
    showSuccess(`已导入 ${data.data.imported} 条登记`);
    await loadRows();
  } catch (error) {
    showError((error as Error).message);
  } finally {
    importing.value = false;
  }
}

watch([() => filters.keyword, () => filters.approve_status, () => filters.sync_status], () => {
  page.value = 1;
  selectedIds.value = [];
  void loadRows();
});
watch(() => auth.scopeEpoch, () => {
  selectedIds.value = [];
  void loadRows();
});
onMounted(async () => {
  await loadRows();
  if (can("department:read")) {
    try {
      const all = await http.get<{ id: number; code: string; name: string }[]>("/depts");
      const allowedIds = new Set(auth.user?.working_department_options?.map(item => item.id) || []);
      departments.value = all.filter(item => auth.user?.role === "SUPER_ADMIN" || allowedIds.size === 0 || allowedIds.has(item.id)).map(item => ({
        code: item.code,
        name: item.name
      }));
    } catch {
      departments.value = [];
    }
  }
});
onBeforeUnmount(() => Object.values(photoCache.value).forEach(url => URL.revokeObjectURL(url)));
</script>

<style scoped>
.face-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 28px 32px 48px;
  color: var(--text)
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--primary);
  font: 600 11px/1.2 ui-monospace, SFMono-Regular, monospace;
  letter-spacing: .08em;
  text-transform: uppercase
}

.page-head h1, .modal h2 {
  margin: 0;
  font-size: 28px;
  letter-spacing: 0;
  font-weight: 700
}

.lede {
  margin: 8px 0 0;
  color: var(--text-sub);
  font-size: 14px
}

.head-actions, .row-actions, .pager {
  display: flex;
  align-items: center;
  gap: 8px
}

.btn {
  border: 1px solid transparent;
  border-radius: 6px;
  min-height: 38px;
  padding: 0 14px;
  font-weight: 600;
  cursor: pointer
}

.btn-primary {
  background: var(--primary);
  color: var(--on-solid)
}

.btn-quiet {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text)
}

.btn-danger {
  background: var(--danger);
  color: #fff;
  border: 0;
  border-radius: 6px;
  padding: 10px 16px;
  font-weight: 600
}

.status-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 28px 0 14px;
  padding: 12px 14px;
  background: var(--primary-soft);
  border-left: 3px solid var(--primary);
  font-size: 13px
}

.unit-mark {
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: 700;
  color: var(--primary)
}

.unit-mark .material-icons-outlined {
  font-size: 18px
}

.strip-copy {
  color: var(--text-sub)
}

.strip-count {
  margin-left: auto;
  color: var(--text-sub);
  font-variant-numeric: tabular-nums
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px
}

.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 520px;
  border: 1px solid var(--border-strong);
  background: var(--card);
  border-radius: 6px;
  padding: 0 11px;
  height: 40px
}

.search-field span {
  color: var(--text-mute);
  font-size: 19px
}

.search-field input, .toolbar select, .form-grid input, .form-grid select, textarea {
  border: 1px solid var(--border-strong);
  background: var(--card);
  color: var(--text);
  border-radius: 6px;
  padding: 10px 11px;
  font: inherit
}

.search-field input {
  border: 0;
  padding: 0;
  outline: 0;
  width: 100%
}

.toolbar select {
  height: 40px;
  min-width: 150px
}

.icon-action {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  background: var(--card);
  color: var(--text-sub);
  cursor: pointer
}

.icon-action:disabled {
  opacity: .45;
  cursor: not-allowed
}

.notice {
  margin: 12px 0;
  padding: 10px 12px;
  border-radius: 5px;
  font-size: 13px
}

.notice.success {
  background: var(--success-soft);
  color: var(--success-text)
}

.notice.error, .form-error {
  background: var(--danger-soft);
  color: var(--danger-text)
}

.table-wrap {
  overflow: auto;
  border: 1px solid var(--border-strong);
  background: var(--card)
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1020px
}

th, td {
  padding: 13px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  white-space: nowrap
}

th {
  color: var(--text-sub);
  background: var(--bg);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .04em
}

tbody tr:hover {
  background: var(--bg)
}

td strong, td small {
  display: block
}

td small {
  margin-top: 3px;
  color: var(--text-mute);
  font: 11px ui-monospace, SFMono-Regular, monospace
}

.select-col {
  width: 36px;
  text-align: center;
  padding-left: 8px;
  padding-right: 8px
}

.photo-col {
  width: 68px
}

.thumb {
  width: 42px;
  height: 42px;
  object-fit: cover;
  border-radius: 5px;
  background: var(--neutral-soft)
}

.action-col {
  width: 210px
}

.text-action {
  border: 0;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  padding: 5px 0;
  font: 600 12px inherit
}

.text-action.review {
  color: var(--danger)
}

.sensitive {
  font-variant-numeric: tabular-nums
}

.date-cell {
  color: var(--text-sub);
  font-variant-numeric: tabular-nums
}

.status {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700
}

.status.pending {
  background: var(--warning-soft);
  color: var(--warning-text)
}

.status.approved {
  background: var(--success-soft);
  color: var(--success-text)
}

.status.rejected {
  background: var(--danger-soft);
  color: var(--danger-text)
}

.empty {
  text-align: center;
  padding: 48px;
  color: var(--text-mute)
}

.pager {
  justify-content: flex-end;
  padding-top: 14px;
  color: var(--text-sub);
  font-size: 12px
}

.pager strong {
  color: var(--text);
  font-variant-numeric: tabular-nums
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgb(24 24 27 / 42%);
  display: grid;
  place-items: center;
  padding: 20px
}

.modal {
  width: min(620px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  background: var(--card);
  border-radius: 8px;
  box-shadow: var(--shadow-pop)
}

.modal-head, .modal-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border)
}

.modal-foot {
  border-top: 1px solid var(--border);
  border-bottom: 0;
  justify-content: flex-end
}

.modal-body {
  padding: 22px
}

.form-grid {
  display: grid;
  grid-template-columns:1fr 1fr;
  gap: 16px
}

.form-grid label, .modal-body > label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--text-sub);
  font-size: 12px;
  font-weight: 600
}

.form-grid .wide {
  grid-column: 1/-1
}

.form-grid small, .import-note {
  color: var(--text-mute);
  font-size: 11px;
  font-weight: 400
}

.preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  color: var(--text-sub);
  font-size: 12px
}

.preview img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px
}

.form-error {
  margin: 16px 0 0;
  padding: 9px;
  border-radius: 5px;
  font-size: 12px
}

.detail-layout {
  display: grid;
  grid-template-columns:180px 1fr;
  gap: 24px;
  padding: 22px
}

.detail-photo {
  width: 180px;
  height: 220px;
  object-fit: cover;
  border-radius: 7px;
  background: var(--neutral-soft)
}

dl {
  display: grid;
  grid-template-columns:100px 1fr;
  gap: 12px;
  margin: 0;
  font-size: 13px
}

dt {
  color: var(--text-mute)
}

dd {
  margin: 0;
  color: var(--text)
}

.review-modal textarea {
  width: 100%;
  min-height: 120px;
  resize: vertical
}

.import-results {
  margin-top: 16px;
  border-top: 1px solid var(--border)
}

.import-results div {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border);
  font-size: 12px
}

.import-results span {
  color: var(--success-text)
}

.import-results .invalid span {
  color: var(--danger-text)
}

@media (max-width: 800px) {
  .face-page {
    padding: 20px 16px 36px
  }

  .page-head {
    display: block
  }

  .head-actions {
    margin-top: 16px;
    flex-wrap: wrap
  }

  .status-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px
  }

  .strip-count {
    margin-left: 0
  }

  .toolbar {
    flex-wrap: wrap
  }

  .search-field {
    max-width: none;
    flex-basis: 100%
  }

  .toolbar select {
    flex: 1;
    min-width: 0
  }

  .form-grid {
    grid-template-columns:1fr
  }

  .form-grid .wide {
    grid-column: auto
  }

  .detail-layout {
    grid-template-columns:1fr
  }

  .detail-photo {
    width: 100%;
    height: 240px
  }
}
</style>
