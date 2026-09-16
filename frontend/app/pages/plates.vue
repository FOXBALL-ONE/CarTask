<template>
  <section class="page">
    <header class="page__header">
      <div><h1 class="page__title">车牌信息</h1>
        <p class="page__desc">管理车牌、车主关联与车辆年检信息</p></div>
      <button v-if="can('plate:manage')" class="button button--primary" type="button" @click="openCreate"><span
          class="material-icons-outlined">add</span>新增车牌
      </button>
    </header>
    <section class="card">
      <div class="toolbar">
        <input v-model="keyword" class="input" placeholder="编号 / 车牌号 / 车主" type="search" @keyup.enter="search">
        <select v-model="status" aria-label="车牌状态" class="select">
          <option value="">全部状态</option>
          <option value="1">正常</option>
          <option value="0">停用</option>
        </select>
        <select v-model="inspectionStatus" aria-label="年检状态" class="select">
          <option value="">全部年检状态</option>
          <option value="有效">年检有效</option>
          <option value="已过期">年检已过期</option>
          <option value="未年检">未年检</option>
        </select>
        <div class="toolbar__right">
          <button class="button button--soft" type="button" @click="search"><span
              class="material-icons-outlined">search</span>搜索
          </button>
          <button class="button button--ghost" type="button" @click="resetFilters"><span
              class="material-icons-outlined">restart_alt</span>重置
          </button>
        </div>
      </div>
      <div v-if="loading" class="state">正在加载车牌数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th>编号</th>
            <th>车牌号</th>
            <th>车主</th>
            <th>车辆类型</th>
            <th>登记日期</th>
            <th>年检状态</th>
            <th>年检有效期</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="plate in plates" :key="plate.id">
            <td>{{ String(plate.id).padStart(4, "0") }}</td>
            <td><strong class="primary-text">{{ plate.plate }}</strong></td>
            <td>{{ plate.owner }}</td>
            <td>{{ plate.carBrand || "-" }}</td>
            <td class="muted">{{ plate.regDate }}</td>
            <td><span :class="inspectionTag(plate.inspectionStatus)" class="tag">{{ plate.inspectionStatus }}</span>
            </td>
            <td :title="plate.inspectionDate ? `年检日期 ${plate.inspectionDate}` : '尚未登记年检'" class="muted">
              {{ plate.inspectionValidUntil || "-" }}
            </td>
            <td><span :class="plate.status === 1 ? 'tag--green' : 'tag--red'" class="tag">{{
                plate.status === 1 ? "正常" : "停用"
              }}</span></td>
            <td>
              <button v-if="can('plate:manage')" class="row-action" title="年检登记" type="button"
                      @click="openInspection(plate)"><span class="material-icons-outlined">fact_check</span></button>
              <button v-if="can('plate:manage')" class="row-action" title="编辑" type="button" @click="openEdit(plate)">
                <span class="material-icons-outlined">edit</span></button>
              <button v-if="can('plate:manage')" class="row-action row-action--danger" title="删除" type="button"
                      @click="removePlate(plate)"><span class="material-icons-outlined">delete</span></button>
            </td>
          </tr>
          <tr v-if="plates.length === 0">
            <td class="empty" colspan="9">暂无数据</td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{
          total
        }} 条</span><label class="pagination__size">每页<select v-model.number="pageSize" aria-label="每页展示条数"
                                                                class="select" @change="changePageSize">
        <option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条</option>
      </select></label>
        <button :disabled="page <= 1" aria-label="上一页" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" aria-label="下一页" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>
    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="savePlate">
        <header class="modal__head"><h2>{{ modalTitle }}</h2>
          <button aria-label="关闭" class="icon-button" type="button" @click="editorVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid">
            <template v-if="!inspectionMode">
              <label class="field"><span>车牌号 <em>*</em></span><input v-model.trim="form.plate" class="input"
                                                                        placeholder="如：京A12345"
                                                                        required></label>
              <label class="field"><span>车主 <em>*</em></span><select v-model.number="form.ownerId" class="select"
                                                                       required>
                <option :value="null">请选择车主</option>
                <option v-for="owner in owners" :key="owner.id" :value="owner.id">{{ owner.name }}</option>
              </select></label>
              <label class="field"><span>车辆类型</span><input v-model.trim="form.carBrand" class="input" maxlength="64"
                                                               placeholder="如：小型轿车"></label>
              <label class="field"><span>登记日期 <em>*</em></span><input v-model="form.regDate" class="input"
                                                                          required type="date"></label>
              <label class="field"><span>状态</span><select v-model.number="form.status" class="select">
                <option :value="1">正常</option>
                <option :value="0">停用</option>
              </select></label>
            </template>
            <label class="field"><span>年检日期 <em v-if="inspectionMode">*</em></span><input
                v-model="form.inspectionDate" :required="inspectionMode" class="input" type="date"></label>
            <label class="field"><span>年检有效期</span><input v-model="form.inspectionValidUntil" class="input"
                                                               type="date"></label>
            <label class="field"><span>年检备注</span><input v-model.trim="form.inspectionRemark" class="input"
                                                             maxlength="255"
                                                             placeholder="检验机构、未通过原因等"></label>
          </div>
          <p class="form-hint">
            只填年检日期时，年检有效期按年检日期起一年计算。年检日期与有效期都留空表示未年检，会清除已有的年检登记。</p>
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
interface Plate {
  id: number;
  plate: string;
  owner: string;
  ownerId: number;
  status: number;
  regDate: string;
  carBrand: string;
  inspectionDate: string | null;
  inspectionValidUntil: string | null;
  inspectionStatus: string;
  inspectionRemark: string | null
}

interface PlateList {
  items: Plate[];
  total: number
}

interface Owner {
  id: number;
  name: string
}

const http = useHttp();
const {can} = usePermission();
const keyword = ref("");
const status = ref("");
const inspectionStatus = ref("");
const page = ref(1);
const pageSizes = [30, 40, 50];
const pageSize = ref(30);
const plates = ref<Plate[]>([]);
const owners = ref<Owner[]>([]);
const total = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const editorVisible = ref(false);
const inspectionMode = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const formError = ref("");
const form = reactive({
  plate: "",
  ownerId: null as number | null,
  status: 1,
  regDate: today(),
  inspectionDate: "",
  inspectionValidUntil: "",
  inspectionRemark: "",
  carBrand: ""
});
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const modalTitle = computed(() => (inspectionMode.value ? "车辆年检登记" : editingId.value ? "编辑车牌" : "新增车牌"));

// 日期按本地时区拼：toISOString 走 UTC，东八区凌晨会算成前一天。
function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function inspectionTag(state: string) {
  return state === "有效" ? "tag--green" : state === "已过期" ? "tag--red" : "tag--gray";
}

async function loadPlates() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<PlateList>("/plates", {
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      inspectionStatus: inspectionStatus.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    });
    plates.value = result.items || [];
    total.value = result.total || 0;
    if (!owners.value.length) owners.value = (await http.get<{ items: Owner[] }>("/owners", {
      page: 1,
      pageSize: 100
    })).items || [];
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "车牌数据加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  void loadPlates();
}

function resetFilters() {
  keyword.value = "";
  status.value = "";
  inspectionStatus.value = "";
  page.value = 1;
  void loadPlates();
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  void loadPlates();
}

function changePageSize() {
  page.value = 1;
  void loadPlates();
}

function openCreate() {
  editingId.value = null;
  inspectionMode.value = false;
  Object.assign(form, {
    plate: "",
    ownerId: owners.value[0]?.id ?? null,
    status: 1,
    regDate: today(),
    inspectionDate: "",
    inspectionValidUntil: "",
    inspectionRemark: "",
    carBrand: ""
  });
  formError.value = "";
  editorVisible.value = true;
}

function openEdit(plate: Plate) {
  editingId.value = plate.id;
  inspectionMode.value = false;
  Object.assign(form, {
    plate: plate.plate,
    ownerId: plate.ownerId,
    status: plate.status,
    regDate: plate.regDate,
    inspectionDate: plate.inspectionDate || "",
    inspectionValidUntil: plate.inspectionValidUntil || "",
    inspectionRemark: plate.inspectionRemark || "",
    carBrand: plate.carBrand || ""
  });
  formError.value = "";
  editorVisible.value = true;
}

// 年检登记只带年检字段：日期默认今天，有效期留空则由后端按年检日期起一年推算，避免两端各算一套。
function openInspection(plate: Plate) {
  editingId.value = plate.id;
  inspectionMode.value = true;
  Object.assign(form, {inspectionDate: today(), inspectionValidUntil: "", inspectionRemark: ""});
  formError.value = "";
  editorVisible.value = true;
}

async function savePlate() {
  formError.value = "";
  saving.value = true;
  try {
    if (inspectionMode.value) {
      await http.put(`/plates/${editingId.value}`, {
        inspected: true,
        inspectionDate: form.inspectionDate,
        inspectionValidUntil: form.inspectionValidUntil || null,
        inspectionRemark: form.inspectionRemark
      }, {payloadMode: "json"});
    } else {
      const owner = owners.value.find((item) => item.id === form.ownerId);
      if (!owner) {
        formError.value = "请选择车主";
        return;
      }
      // 年检日期与有效期都留空表示这辆车没有年检登记，后端会清空已有的年检信息。
      const payload = {
        plate: form.plate,
        owner: owner.name,
        ownerId: owner.id,
        status: form.status,
        regDate: form.regDate,
        carBrand: form.carBrand,
        inspected: Boolean(form.inspectionDate || form.inspectionValidUntil),
        inspectionDate: form.inspectionDate || null,
        inspectionValidUntil: form.inspectionValidUntil || null,
        inspectionRemark: form.inspectionRemark
      };
      if (editingId.value) await http.put(`/plates/${editingId.value}`, payload, {payloadMode: "json"}); else await http.post("/plates", payload, {payloadMode: "json"});
    }
    editorVisible.value = false;
    await loadPlates();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function removePlate(plate: Plate) {
  if (!window.confirm(`确认删除车牌“${plate.plate}”？`)) return;
  try {
    await http.delete(`/plates/${plate.id}`);
    await loadPlates();
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败";
  }
}

onMounted(loadPlates);
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadPlates);
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

.toolbar .input {
  min-width: 220px;
}

.toolbar .select {
  min-width: 120px;
}

.input:focus, .select:focus {
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

.button:disabled {
  cursor: wait;
  opacity: .6;
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

.button .material-icons-outlined {
  font-size: 16px;
}

.table-wrap {
  overflow-x: auto;
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 920px;
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
  background: var(--green-soft);
  color: var(--green);
}

.tag--red {
  background: var(--red-soft);
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

.row-action--danger:hover {
  background: var(--danger-soft);
  color: var(--red);
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

.pagination__size {
  align-items: center;
  color: var(--text-sub);
  display: flex;
  font-size: 12px;
  gap: 6px;
}

.pagination__size .select {
  font-size: 12px;
  height: 28px;
  padding: 0 4px 0 8px;
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
  max-width: 620px;
  width: 100%;
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
  padding: 20px 18px;
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

@media (max-width: 600px) {
  .page {
    padding: 16px;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .input, .toolbar .select {
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
}
</style>
