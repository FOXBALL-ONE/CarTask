<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">岗位管理</h1>
        <p class="page__desc">维护组织岗位信息</p>
      </div>
      <button v-if="can('position:manage')" class="button button--primary" type="button" @click="openCreate">
        <span class="material-icons-outlined">add</span>新增岗位
      </button>
    </header>

    <section class="panel">
      <div class="filter-bar">
        <input v-model.trim="keyword" class="input" type="search" placeholder="岗位名称 / 编码" @keyup.enter="search">
        <select v-model="status" class="select" aria-label="岗位状态">
          <option value="">全部状态</option>
          <option value="1">正常</option>
          <option value="0">停用</option>
        </select>
        <div class="filter-actions">
          <button class="button button--soft" type="button" @click="search"><span class="material-icons-outlined">search</span>搜索</button>
          <button class="button button--ghost" type="button" @click="resetFilters"><span class="material-icons-outlined">restart_alt</span>重置</button>
        </div>
      </div>

      <div v-if="loading" class="state">正在加载岗位数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>编号</th><th>岗位名称</th><th>岗位编码</th><th>显示排序</th><th>状态</th><th>备注</th><th class="actions-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="position in pagedPositions" :key="position.id">
              <td>{{ String(position.id).padStart(4, "0") }}</td>
              <td><strong class="position-name">{{ position.name }}</strong></td>
              <td><span class="code-tag">{{ position.code }}</span></td>
              <td>{{ position.sort }}</td>
              <td><span class="status-tag" :class="position.status === 1 ? 'status-tag--normal' : 'status-tag--disabled'">{{ position.status === 1 ? "正常" : "停用" }}</span></td>
              <td class="remark">{{ position.remark || "-" }}</td>
              <td class="actions-cell">
                <button v-if="can('position:manage')" class="row-action" type="button" title="编辑" @click="openEdit(position)"><span class="material-icons-outlined">edit</span></button>
                <button v-if="can('position:manage')" class="row-action row-action--danger" type="button" title="删除" @click="removePosition(position)"><span class="material-icons-outlined">delete</span></button>
              </td>
            </tr>
            <tr v-if="pagedPositions.length === 0"><td class="empty" colspan="7">暂无数据</td></tr>
          </tbody>
        </table>
      </div>

      <footer v-if="!loading && !errorMessage" class="pagination">
        <span>共 {{ filteredPositions.length }} 条</span>
        <button type="button" :disabled="page <= 1" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" type="button" :class="{ active: pageNumber === page }" @click="changePage(pageNumber)">{{ pageNumber }}</button>
        <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="savePosition">
        <header class="modal__head">
          <h2>{{ editingId ? "编辑岗位" : "新增岗位" }}</h2>
          <button class="icon-button" type="button" title="关闭" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid">
            <label class="field"><span>岗位名称 <em>*</em></span><input v-model.trim="form.name" class="input" required placeholder="请输入岗位名称"></label>
            <label class="field"><span>岗位编码 <em>*</em></span><input v-model.trim="form.code" class="input" required placeholder="请输入岗位编码"></label>
            <label class="field"><span>显示排序</span><input v-model.number="form.sort" class="input" type="number" min="1"></label>
            <label class="field"><span>状态</span><select v-model.number="form.status" class="select"><option :value="1">正常</option><option :value="0">停用</option></select></label>
            <label class="field field--full"><span>备注</span><textarea v-model.trim="form.remark" class="textarea" placeholder="请输入备注" /></label>
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer class="modal__foot"><button class="button button--ghost" type="button" @click="editorVisible = false">取消</button><button class="button button--primary" type="submit" :disabled="saving">{{ saving ? "保存中..." : "保存" }}</button></footer>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Position {
  id: number;
  name: string;
  code: string;
  sort: number;
  status: number;
  remark?: string | null;
}
interface PositionApi {
  id: number;
  name: string;
  codeNumber?: string;
  code?: string;
  orderNumber?: number;
  sort?: number;
  status: number | string;
  remark?: string | null;
}
interface PositionPage { content?: PositionApi[]; items?: PositionApi[] }

const http = useHttp();
const { can } = usePermission();
const keyword = ref("");
const status = ref("");
const page = ref(1);
const pageSize = 8;
const positions = ref<Position[]>([]);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({ name: "", code: "", sort: 1, status: 1, remark: "" });

const filteredPositions = computed(() => {
  const searchText = keyword.value.toLowerCase();
  return positions.value.filter((position) => {
    const matchesKeyword = !searchText || `${position.name}${position.code}`.toLowerCase().includes(searchText);
    return matchesKeyword && (status.value === "" || position.status === Number(status.value));
  });
});
const totalPages = computed(() => Math.max(1, Math.ceil(filteredPositions.value.length / pageSize)));
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const pagedPositions = computed(() => filteredPositions.value.slice((page.value - 1) * pageSize, page.value * pageSize));

function normalizePosition(value: PositionApi): Position {
  return {
    id: value.id,
    name: value.name,
    code: value.codeNumber ?? value.code ?? "",
    sort: value.orderNumber ?? value.sort ?? 0,
    status: value.status === 1 || value.status === "Activity" ? 1 : 0,
    remark: value.remark ?? null,
  };
}

async function loadPositions() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<PositionPage | Position[]>("/posts");
    const rows = Array.isArray(result) ? result : (result.content ?? result.items ?? []);
    positions.value = rows.map(normalizePosition);
    if (page.value > totalPages.value) page.value = totalPages.value;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "岗位数据加载失败";
  } finally {
    loading.value = false;
  }
}

function search() { page.value = 1; }
function resetFilters() { keyword.value = ""; status.value = ""; page.value = 1; }
function changePage(nextPage: number) { page.value = nextPage; }
function openCreate() {
  editingId.value = null;
  Object.assign(form, { name: "", code: "", sort: 1, status: 1, remark: "" });
  formError.value = "";
  editorVisible.value = true;
}
function openEdit(position: Position) {
  editingId.value = position.id;
  Object.assign(form, { name: position.name, code: position.code, sort: position.sort, status: position.status, remark: position.remark || "" });
  formError.value = "";
  editorVisible.value = true;
}

async function savePosition() {
  if (!form.name || !form.code) {
    formError.value = "名称和编码不能为空";
    return;
  }
  saving.value = true;
  formError.value = "";
  const payload = { name: form.name, code: form.code, sort: form.sort || 1, status: form.status, remark: form.remark || null };
  try {
    if (editingId.value) await http.put(`/posts/${editingId.value}`, payload, { payloadMode: "json" });
    else await http.post("/posts", payload, { payloadMode: "json" });
    editorVisible.value = false;
    await loadPositions();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function removePosition(position: Position) {
  if (!window.confirm(`确认删除“${position.name}”吗？`)) return;
  try {
    await http.delete(`/posts/${position.id}`);
    await loadPositions();
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败";
  }
}

onMounted(loadPositions);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }
.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }
.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }
.page__desc { color: var(--text-sub); margin: 2px 0 0; }
.panel { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; overflow: hidden; }
.filter-bar { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; padding: 18px 18px 0; }
.input, .select, .textarea { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; outline: none; }
.input, .select { height: 34px; padding: 0 10px; }
.input { min-width: 200px; }
.select { min-width: 120px; }
.textarea { min-height: 84px; padding: 9px 10px; resize: vertical; width: 100%; }
.input:focus, .select:focus, .textarea:focus { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary-soft); }
.filter-actions { display: flex; gap: 8px; }
.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 32px; justify-content: center; padding: 0 12px; white-space: nowrap; }
.button:disabled { cursor: wait; opacity: .6; }
.button--primary { background: var(--primary); color: #fff; }
.button--soft { background: var(--primary-soft); color: var(--primary); }
.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }
.button:hover:not(:disabled) { filter: brightness(.97); }
.button .material-icons-outlined { font-size: 16px; }
.table-wrap { overflow-x: auto; margin-top: 18px; }
.table { border-collapse: collapse; font-size: 13px; min-width: 760px; width: 100%; }
.table th { background: var(--bg); border-bottom: 1px solid var(--border); color: var(--text-mute); font-size: 12px; font-weight: 500; padding: 10px 16px; text-align: left; white-space: nowrap; }
.table td { border-bottom: 1px solid var(--border); color: var(--text); padding: 11px 16px; white-space: nowrap; }
.table tbody tr:hover { background: var(--bg); }
.position-name { color: var(--primary); }
.code-tag, .status-tag { align-items: center; border-radius: 4px; display: inline-flex; font-size: 12px; font-weight: 500; gap: 4px; line-height: 1.5; padding: 2px 8px; }
.code-tag { background: #f4f4f5; color: var(--text-sub); }
.code-tag::before, .status-tag::before { border-radius: 50%; content: ""; height: 5px; width: 5px; }
.status-tag--normal { background: #f0fdf4; color: #059669; }
.status-tag--normal::before { background: #059669; }
.status-tag--disabled { background: #fef2f2; color: #dc2626; }
.status-tag--disabled::before { background: #dc2626; }
.code-tag::before { background: var(--text-mute); }
.remark { color: var(--text-sub) !important; max-width: 260px; overflow: hidden; text-overflow: ellipsis; }
.actions-cell { text-align: right !important; }
.row-action, .icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; width: 28px; }
.row-action:hover { background: var(--bg); color: var(--text); }
.row-action--danger:hover { background: #fef2f2; color: #dc2626; }
.row-action .material-icons-outlined { font-size: 16px; }
.state, .empty { color: var(--text-mute); padding: 48px; text-align: center; }
.state--error, .form-error { color: #dc2626; }
.pagination { align-items: center; color: var(--text-sub); display: flex; gap: 4px; justify-content: flex-end; padding: 14px 18px; }
.pagination button { align-items: center; background: transparent; border: 1px solid transparent; border-radius: 5px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; min-width: 28px; }
.pagination button:hover:not(:disabled), .pagination button.active { background: var(--primary-soft); color: var(--primary); }
.pagination button:disabled { cursor: not-allowed; opacity: .4; }
.pagination .material-icons-outlined { font-size: 18px; }
.modal-mask { align-items: center; background: rgb(0 0 0 / 38%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 300; }
.modal { background: var(--card); border-radius: 8px; box-shadow: 0 16px 48px rgb(0 0 0 / 20%); max-width: 620px; width: 100%; }
.modal__head, .modal__foot { align-items: center; display: flex; justify-content: space-between; padding: 14px 18px; }
.modal__head { border-bottom: 1px solid var(--border); }
.modal__head h2 { color: var(--text); font-size: 16px; margin: 0; }
.modal__foot { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }
.modal__body { padding: 20px 18px; }
.form-grid { display: grid; gap: 14px 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.field { display: grid; gap: 6px; }
.field--full { grid-column: 1 / -1; }
.field span { color: var(--text-sub); font-size: 12px; font-weight: 500; }
.field em { color: #dc2626; font-style: normal; }
.field .input, .field .select { min-width: 0; width: 100%; }
@media (max-width: 600px) { .page { padding: 16px; }.filter-bar { align-items: stretch; flex-direction: column; }.input { min-width: 0; width: 100%; }.filter-actions { justify-content: flex-end; }.form-grid { grid-template-columns: 1fr; }.modal-mask { padding: 12px; } }
</style>
