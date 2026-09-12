<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">车主信息</h1>
        <p class="page__desc">管理车主出入证及车位车牌信息</p>
      </div>
      <button v-if="can('owner:manage')" class="button button--primary" type="button" @click="openCreate">
        <span class="material-icons-outlined">add</span>新增车主
      </button>
    </header>

    <section class="card">
      <div class="toolbar">
        <input v-model="keyword" class="input" type="search" placeholder="卡片ID / 户主名 / 手机号" @keyup.enter="search">
        <select v-model="status" class="select" aria-label="车主状态">
          <option value="">全部状态</option>
          <option value="1">正常</option>
          <option value="0">停用</option>
        </select>
        <div class="toolbar__right">
          <button class="button button--soft" type="button" @click="search"><span class="material-icons-outlined">search</span>搜索</button>
          <button class="button button--ghost" type="button" @click="resetFilters"><span class="material-icons-outlined">restart_alt</span>重置</button>
        </div>
      </div>

      <div v-if="loading" class="state">正在加载车主数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead><tr><th>编号</th><th>卡片ID</th><th>户主名</th><th>部门</th><th>手机号</th><th>车位数量</th><th>余额</th><th>车牌号码</th><th>到期时间</th><th>状态</th><th>备注</th><th class="actions-cell">操作</th></tr></thead>
          <tbody>
            <tr v-for="owner in owners" :key="owner.id">
              <td>{{ String(owner.id).padStart(4, "0") }}</td>
              <td><strong class="primary-text">{{ owner.cardId }}</strong></td>
              <td>{{ owner.name }}</td>
              <td>{{ owner.dept }}</td>
              <td>{{ owner.phone }}</td>
              <td><span class="tag tag--blue">{{ owner.spotCount }} 个</span></td>
              <td>{{ Number(owner.balance).toFixed(2) }}</td>
              <td><template v-if="platesByOwner[owner.id]?.length"><span v-for="plate in platesByOwner[owner.id]" :key="plate" class="tag tag--gray plate-tag">{{ plate }}</span></template><span v-else class="muted">{{ owner.plateCount ? `${owner.plateCount} 个` : "-" }}</span></td>
              <td class="muted">-</td>
              <td><span class="tag" :class="owner.status === 1 ? 'tag--green' : 'tag--red'">{{ owner.status === 1 ? "正常" : "停用" }}</span></td>
              <td class="muted">-</td>
              <td class="actions-cell">
                <button v-if="can('owner:manage')" class="row-action" type="button" title="充值" @click="rechargeOwner(owner)"><span class="material-icons-outlined">account_balance_wallet</span></button><button v-if="can('owner:manage')" class="row-action" type="button" title="修改信息" @click="openEdit(owner)"><span class="material-icons-outlined">edit</span></button>
                <button v-if="can('owner:manage')" class="row-action row-action--danger" type="button" title="删除" @click="removeOwner(owner)"><span class="material-icons-outlined">delete</span></button>
              </td>
            </tr>
            <tr v-if="owners.length === 0"><td colspan="12" class="empty">暂无数据</td></tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination">
        <span class="pagination__info">共 {{ total }} 条</span>
        <label class="pagination__size">每页<select v-model.number="pageSize" class="select" aria-label="每页展示条数" @change="changePageSize">
          <option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条</option>
        </select></label>
        <button type="button" :disabled="page <= 1" aria-label="上一页" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" type="button" :class="{ active: pageNumber === page }" @click="changePage(pageNumber)">{{ pageNumber }}</button>
        <button type="button" :disabled="page >= totalPages" aria-label="下一页" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="saveOwner">
        <header class="modal__head"><h2>{{ editingId ? "编辑车主" : "新增车主" }}</h2><button class="icon-button" type="button" aria-label="关闭" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button></header>
        <div class="modal__body">
          <div class="form-grid">
            <label class="field"><span>卡片ID <em>*</em></span><input v-model.trim="form.cardId" class="input" required placeholder="如：CAR20240001"></label>
            <label class="field"><span>户主名 <em>*</em></span><input v-model.trim="form.name" class="input" required></label>
            <label class="field"><span>部门 <em>*</em></span><input v-model.trim="form.dept" class="input" required></label>
            <label class="field"><span>手机号 <em>*</em></span><input v-model.trim="form.phone" class="input" required></label>
            <label class="field"><span>车位数量 <em>*</em></span><input v-model.number="form.spotCount" class="input" min="0" type="number" required></label>
            <label class="field"><span>车牌数量 <em>*</em></span><input v-model.number="form.plateCount" class="input" min="0" type="number" required></label>
            <label class="field"><span>余额</span><input v-model.number="form.balance" class="input" min="0" step="0.01" type="number"></label>
            <label class="field"><span>状态</span><select v-model.number="form.status" class="select"><option :value="1">正常</option><option :value="0">停用</option></select></label>
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer class="modal__foot"><button class="button button--ghost" type="button" @click="editorVisible = false">取消</button><button class="button button--primary" type="submit" :disabled="saving">{{ saving ? "保存中..." : "保存" }}</button></footer>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Owner { id: number; cardId: string; name: string; dept: string; phone: string; spotCount: number; plateCount: number; balance: number | string; status: number }
interface Plate { id: number; plate: string; owner: string; ownerId: number; status: number; regDate: string }
interface OwnerList { items: Owner[]; total: number; page: number; pageSize: number }
interface PlateList { items: Plate[]; total: number }

const http = useHttp();
const { can } = usePermission();
const keyword = ref("");
const status = ref("");
const page = ref(1);
const pageSizes = [10, 20, 50, 100];
const pageSize = ref(20);
const owners = ref<Owner[]>([]);
const platesByOwner = ref<Record<number, string[]>>({});
const total = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const formError = ref("");
const form = reactive({ cardId: "", name: "", dept: "", phone: "", spotCount: 0, plateCount: 0, balance: 0, status: 1 });
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));

async function loadOwners() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<OwnerList>("/owners", { keyword: keyword.value || undefined, status: status.value || undefined, page: page.value, pageSize: pageSize.value });
    owners.value = result.items || [];
    total.value = result.total || 0;
    try {
      const plates = await http.get<PlateList>("/plates", { page: 1, pageSize: 100 });
      platesByOwner.value = (plates.items || []).reduce<Record<number, string[]>>((grouped, plate) => {
        (grouped[plate.ownerId] ||= []).push(plate.plate);
        return grouped;
      }, {});
    } catch { platesByOwner.value = {}; }
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "车主数据加载失败";
  } finally { loading.value = false; }
}
function search() { page.value = 1; void loadOwners(); }
function resetFilters() { keyword.value = ""; status.value = ""; page.value = 1; void loadOwners(); }
function changePage(nextPage: number) { if (nextPage < 1 || nextPage > totalPages.value) return; page.value = nextPage; void loadOwners(); }
function changePageSize() { page.value = 1; void loadOwners(); }
function openCreate() { editingId.value = null; Object.assign(form, { cardId: "", name: "", dept: "", phone: "", spotCount: 0, plateCount: 0, balance: 0, status: 1 }); formError.value = ""; editorVisible.value = true; }
function openEdit(owner: Owner) { editingId.value = owner.id; Object.assign(form, { cardId: owner.cardId, name: owner.name, dept: owner.dept, phone: owner.phone, spotCount: owner.spotCount, plateCount: owner.plateCount, balance: Number(owner.balance), status: owner.status }); formError.value = ""; editorVisible.value = true; }
async function saveOwner() {
  saving.value = true; formError.value = "";
  try {
    const payload = { cardId: form.cardId, name: form.name, dept: form.dept, phone: form.phone, spotCount: form.spotCount, plateCount: form.plateCount, balance: form.balance, status: form.status };
    if (editingId.value) await http.put(`/owners/${editingId.value}`, payload, { payloadMode: "json" });
    else await http.post("/owners", payload, { payloadMode: "json" });
    editorVisible.value = false; await loadOwners();
  } catch (error) { formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败"; }
  finally { saving.value = false; }
}
async function removeOwner(owner: Owner) {
  if (!window.confirm(`确认删除“${owner.name}”吗？`)) return;
  try { await http.delete(`/owners/${owner.id}`); await loadOwners(); }
  catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败"; }
}
async function rechargeOwner(owner: Owner) {
  const rawAmount = window.prompt(`请输入“${owner.name}”的充值金额`);
  if (!rawAmount?.trim()) return;
  const amount = Number(rawAmount);
  if (!Number.isFinite(amount) || amount <= 0) { errorMessage.value = "充值金额必须为正数"; return; }
  try { await http.post(`/owners/${owner.id}/recharge`, { amount }, { payloadMode: "json" }); await loadOwners(); }
  catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "充值失败"; }
}
onMounted(loadOwners);
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadOwners);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }.page__desc { color: var(--text-sub); font-size: 13px; margin: 2px 0 0; }.card { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; overflow: hidden; }.toolbar { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; padding: 18px; }.toolbar__right { display: flex; gap: 6px; margin-left: auto; }.input, .select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; outline: none; padding: 0 10px; }.toolbar .input { min-width: 260px; }.toolbar .select { min-width: 120px; }.input:focus, .select:focus { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary-soft); }.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 32px; justify-content: center; padding: 0 12px; white-space: nowrap; }.button:disabled { cursor: wait; opacity: .6; }.button--primary { background: var(--primary); color: #fff; }.button--soft { background: var(--primary-soft); color: var(--primary); }.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }.button:hover:not(:disabled) { filter: brightness(.97); }.button .material-icons-outlined { font-size: 16px; }.table-wrap { overflow-x: auto; }.table { border-collapse: collapse; font-size: 13px; min-width: 1120px; width: 100%; }.table th { background: var(--bg); border-bottom: 1px solid var(--border); color: var(--text-mute); font-size: 12px; font-weight: 500; padding: 10px 14px; text-align: left; white-space: nowrap; }.table td { border-bottom: 1px solid var(--border); color: var(--text); padding: 11px 14px; white-space: nowrap; }.table tbody tr:hover { background: var(--bg); }.actions-cell { text-align: right !important; }.primary-text { color: var(--primary); }.muted { color: var(--text-sub) !important; }.tag { align-items: center; border-radius: 4px; display: inline-flex; font-size: 12px; gap: 4px; line-height: 1.5; padding: 2px 8px; }.tag::before { background: currentColor; border-radius: 50%; content: ""; height: 5px; width: 5px; }.tag--green { background: var(--green-soft, #dcfce7); color: var(--green, #15803d); }.tag--red { background: var(--red-soft, #fee2e2); color: var(--red); }.tag--blue { background: var(--primary-soft); color: var(--primary); }.tag--gray { background: #f4f4f5; color: var(--text-sub); }.plate-tag { margin: 1px; }.row-action, .icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; width: 28px; }.row-action:hover, .icon-button:hover { background: var(--bg); color: var(--text); }.row-action--danger:hover { background: #fef2f2; color: var(--red); }.row-action .material-icons-outlined, .icon-button .material-icons-outlined { font-size: 16px; }.state, .empty { color: var(--text-mute); padding: 48px; text-align: center; }.state--error, .form-error { color: var(--red); }.pagination { align-items: center; border-top: 1px solid var(--border); color: var(--text-sub); display: flex; gap: 4px; justify-content: flex-end; padding: 12px 16px; }.pagination__info { margin-right: auto; font-size: 12px; }.pagination__size { align-items: center; color: var(--text-sub); display: flex; font-size: 12px; gap: 6px; }.pagination__size .select { font-size: 12px; height: 28px; padding: 0 4px 0 8px; }.pagination button { align-items: center; background: transparent; border: 1px solid transparent; border-radius: 5px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; min-width: 28px; }.pagination button:hover:not(:disabled), .pagination button.active { background: var(--primary-soft); color: var(--primary); }.pagination button:disabled { cursor: not-allowed; opacity: .4; }.pagination .material-icons-outlined { font-size: 18px; }.modal-mask { align-items: center; background: rgb(0 0 0 / 38%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 300; }.modal { background: var(--card); border-radius: 8px; box-shadow: 0 16px 48px rgb(0 0 0 / 20%); max-width: 620px; width: 100%; }.modal__head, .modal__foot { align-items: center; display: flex; justify-content: space-between; padding: 14px 18px; }.modal__head { border-bottom: 1px solid var(--border); }.modal__head h2 { color: var(--text); font-size: 16px; margin: 0; }.modal__foot { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }.modal__body { padding: 20px 18px; }.form-grid { display: grid; gap: 14px 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }.field { display: grid; gap: 6px; }.field span { color: var(--text-sub); font-size: 12px; font-weight: 500; }.field em { color: var(--red); font-style: normal; }.field .input, .field .select { width: 100%; }.form-error { margin: 14px 0 0; }@media (max-width: 600px) { .page { padding: 16px; }.toolbar { align-items: stretch; flex-direction: column; }.toolbar .input, .toolbar .select { min-width: 0; width: 100%; }.toolbar__right { justify-content: flex-end; margin-left: 0; }.form-grid { grid-template-columns: 1fr; }.modal-mask { padding: 12px; } }
</style>
