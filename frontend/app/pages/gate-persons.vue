<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">人员信息</h1>
        <p class="page__desc">门禁授权人员信息管理</p>
      </div>
      <div class="page__actions">
        <template v-if="activeTab === 'all'">
          <button class="btn btn--ghost btn--sm" type="button" @click="openImport"><span class="material-icons-outlined">upload</span>导入</button>
          <button class="btn btn--ghost btn--sm" type="button" @click="exportPersons"><span class="material-icons-outlined">download</span>导出</button>
          <button v-if="can('gate-person:manage')" class="btn btn--primary btn--sm" type="button" @click="openCreate"><span class="material-icons-outlined">add</span>新增人员</button>
        </template>
        <button v-else-if="activeTab === 'pending' && can('gate-person:manage')" class="btn btn--ghost btn--sm" type="button" @click="approveAll"><span class="material-icons-outlined">done_all</span>批量通过</button>
      </div>
    </header>

    <div class="tabs">
      <button class="gp-tab" :class="{ 'gp-tab--active': activeTab === 'all' }" type="button" @click="activeTab = 'all'">人员信息</button>
      <button class="gp-tab" :class="{ 'gp-tab--active': activeTab === 'pending' }" type="button" @click="activeTab = 'pending'">待审核人员</button>
      <button class="gp-tab" :class="{ 'gp-tab--active': activeTab === 'delete' }" type="button" @click="activeTab = 'delete'">申请删除人员</button>
    </div>

    <section class="card">
      <div class="card__body filter-body">
        <div class="toolbar">
          <input v-model="filters.keyword" class="input" :placeholder="activeTab === 'delete' ? '搜索编号 / 姓名 / 手机号 / 身份证' : '搜索编号 / 姓名 / 手机号 / 身份证'" @keyup.enter="search">
          <select v-if="activeTab === 'all'" v-model="filters.dept" class="select" aria-label="单位"><option value="">全部单位</option><option v-for="dept in departments" :key="dept" :value="dept">{{ dept }}</option></select>
          <select v-if="activeTab === 'all'" v-model="filters.approveStatus" class="select" aria-label="审批状态"><option value="">全部审批状态</option><option value="通过">通过</option><option value="审核中">审核中</option><option value="拒绝">拒绝</option></select>
          <select v-if="activeTab === 'all'" v-model="filters.syncStatus" class="select" aria-label="同步状态"><option value="">全部同步状态</option><option value="已同步">已同步</option><option value="未同步">未同步</option></select>
          <select v-if="activeTab === 'delete'" v-model="deleteStatus" class="select" aria-label="申请状态"><option value="">全部状态</option><option value="待处理">待处理</option><option value="已同意">已同意</option><option value="已拒绝">已拒绝</option></select>
          <div class="toolbar__right">
            <button class="btn btn--soft btn--sm" type="button" @click="search"><span class="material-icons-outlined">search</span>搜索</button>
            <button class="btn btn--ghost btn--sm" type="button" @click="resetFilters"><span class="material-icons-outlined">restart_alt</span>重置</button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="state">正在加载数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead v-if="activeTab !== 'delete'"><tr><th>人脸信息</th><th>编号</th><th>单位</th><th>用户名</th><th>手机号</th><th>身份证</th><th>{{ activeTab === 'pending' ? '申请时间' : '入库时间' }}</th><th>{{ activeTab === 'pending' ? '状态' : '审批状态' }}</th><th v-if="activeTab === 'all'">同步状态</th><th class="right">操作</th></tr></thead>
          <thead v-else><tr><th>人脸信息</th><th>编号</th><th>单位</th><th>用户名</th><th>手机号</th><th>身份证</th><th>删除原因</th><th>申请时间</th><th>状态</th><th class="right">操作</th></tr></thead>
          <tbody>
            <template v-if="activeTab !== 'delete'">
              <tr v-for="person in visiblePersons" :key="person.id">
                <td><img class="record-face zoomable" :src="person.face || fallbackFace" alt="人脸" @error="useFallbackFace"></td>
                <td>{{ person.code }}</td><td>{{ person.dept }}</td><td>{{ person.name }}</td><td>{{ person.phone }}</td><td>{{ person.idCard }}</td><td>{{ person.createTime }}</td>
                <td><span class="tag" :class="approveClass(person.approveStatus)">{{ person.approveStatus }}</span></td>
                <td v-if="activeTab === 'all'"><span class="tag" :class="person.syncStatus === '已同步' ? 'tag--green' : 'tag--orange'">{{ person.syncStatus }}</span></td>
                <td class="right actions-cell">
                  <button class="row-act" type="button" title="查看" @click="openDetail(person)"><span class="material-icons-outlined">visibility</span></button>
                  <template v-if="activeTab === 'all'"><button v-if="can('gate-person:manage')" class="row-act" type="button" title="编辑" @click="openEdit(person)"><span class="material-icons-outlined">edit</span></button><button class="row-act row-act--danger" type="button" title="申请删除" @click="requestDelete(person)"><span class="material-icons-outlined">delete</span></button></template>
                  <template v-else><button v-if="can('gate-person:manage')" class="btn btn--primary btn--sm" type="button" @click="approvePerson(person)"><span class="material-icons-outlined">check</span>通过</button><button v-if="can('gate-person:manage')" class="btn btn--soft btn--sm" type="button" @click="rejectPerson(person)"><span class="material-icons-outlined">close</span>拒绝</button></template>
                </td>
              </tr>
            </template>
            <tr v-for="request in visibleRequests" v-else :key="request.id">
              <td><img class="record-face zoomable" :src="request.face || fallbackFace" alt="人脸" @error="useFallbackFace"></td>
              <td>{{ request.code }}</td><td>{{ request.dept }}</td><td>{{ request.name }}</td><td>{{ request.phone }}</td><td>{{ request.idCard }}</td><td>{{ request.reason }}</td><td>{{ request.applyTime }}</td>
              <td><span class="tag" :class="deleteClass(request.status)">{{ request.status }}</span></td>
              <td class="right actions-cell"><button class="row-act" type="button" title="查看" @click="openDeleteDetail(request)"><span class="material-icons-outlined">visibility</span></button><template v-if="request.status === '待处理'"><button v-if="can('gate-person:manage')" class="btn btn--primary btn--sm" type="button" @click="approveDelete(request)"><span class="material-icons-outlined">check</span>同意</button><button v-if="can('gate-person:manage')" class="btn btn--soft btn--sm" type="button" @click="rejectDelete(request)"><span class="material-icons-outlined">close</span>拒绝</button></template></td>
            </tr>
            <tr v-if="totalRows === 0"><td class="empty-row" :colspan="activeTab === 'all' ? 10 : 9">{{ activeTab === 'pending' ? '暂无待审核人员' : activeTab === 'delete' ? '暂无删除申请' : '暂无记录' }}</td></tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{ totalRows }} 条</span><button class="page-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button><button v-for="number in pageNumbers" :key="number" class="page-btn" :class="{ active: number === page }" type="button" @click="changePage(number)">{{ number }}</button><button class="page-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button></footer>
    </section>

    <div v-if="detailPerson || detailRequest" class="modal-mask" @click.self="closeDetail">
      <div class="modal modal--detail"><header class="modal__head"><h2 class="modal__title">{{ detailRequest ? '删除申请详情' : '人员详情' }}</h2><button class="icon-btn" type="button" title="关闭" @click="closeDetail"><span class="material-icons-outlined">close</span></button></header><div class="modal__body detail-body"><img class="detail-face" :src="(detailPerson || detailRequest)?.face || fallbackFace" alt="人脸照片"><table class="table table--compact"><tbody><tr><td>编号</td><td>{{ (detailPerson || detailRequest)?.code }}</td></tr><tr><td>单位</td><td>{{ (detailPerson || detailRequest)?.dept }}</td></tr><tr><td>用户名</td><td>{{ (detailPerson || detailRequest)?.name }}</td></tr><tr><td>手机号</td><td>{{ (detailPerson || detailRequest)?.phone }}</td></tr><tr><td>身份证</td><td>{{ (detailPerson || detailRequest)?.idCard }}</td></tr><tr v-if="detailRequest"><td>删除原因</td><td>{{ detailRequest.reason }}</td></tr><tr><td>{{ detailRequest ? '申请时间' : '入库时间' }}</td><td>{{ detailRequest?.applyTime || detailPerson?.createTime }}</td></tr><tr><td>状态</td><td>{{ detailRequest?.status || detailPerson?.approveStatus }}</td></tr><tr v-if="detailPerson"><td>同步状态</td><td>{{ detailPerson.syncStatus }}</td></tr></tbody></table></div><footer class="modal__foot"><button class="btn btn--ghost" type="button" @click="closeDetail">关闭</button></footer></div>
    </div>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false"><form class="modal" @submit.prevent="savePerson"><header class="modal__head"><h2 class="modal__title">{{ editingId ? '编辑人员' : '新增人员' }}</h2><button class="icon-btn" type="button" title="关闭" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button></header><div class="modal__body"><div class="form-grid"><label class="field"><span class="field__label">编号<em>*</em></span><input v-model.trim="form.code" class="input" required placeholder="人员编号"></label><label class="field"><span class="field__label">单位<em>*</em></span><input v-model.trim="form.dept" class="input" required placeholder="所属单位" list="gate-depts"><datalist id="gate-depts"><option v-for="dept in departments" :key="dept" :value="dept"></option></datalist></label><label class="field"><span class="field__label">用户名<em>*</em></span><input v-model.trim="form.name" class="input" required placeholder="请输入用户名"></label><label class="field"><span class="field__label">手机号</span><input v-model.trim="form.phone" class="input" placeholder="请输入手机号"></label><label class="field"><span class="field__label">身份证</span><input v-model.trim="form.idCard" class="input" placeholder="请输入身份证号"></label><label class="field field--full"><span class="field__label">人脸照片<em v-if="!editingId">*</em></span><input ref="faceInput" class="input file-input" type="file" accept="image/*" :required="!editingId" @change="selectFace"></label></div><p v-if="formError" class="form-error">{{ formError }}</p></div><footer class="modal__foot"><button class="btn btn--ghost" type="button" @click="editorVisible = false">取消</button><button class="btn btn--primary" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button></footer></form></div>

    <div v-if="importVisible" class="modal-mask" @click.self="importVisible = false"><div class="modal"><header class="modal__head"><h2 class="modal__title">导入人员信息</h2><button class="icon-btn" type="button" title="关闭" @click="importVisible = false"><span class="material-icons-outlined">close</span></button></header><div class="modal__body"><input ref="importInput" class="input file-input" type="file" accept=".csv" @change="selectImport"><p class="import-hint">CSV 列顺序：编号、单位、用户名、手机号、身份证</p><p v-if="formError" class="form-error">{{ formError }}</p></div><footer class="modal__foot"><button class="btn btn--ghost" type="button" @click="importVisible = false">取消</button><button class="btn btn--primary" type="button" @click="importCsv">确认导入</button></footer></div></div>
  </section>
</template>

<script setup lang="ts">
interface Person { id: number; code: string; dept: string; name: string; phone: string; idCard: string; face?: string | null; createTime: string; approveStatus: string; syncStatus: string }
interface DeleteRequest extends Person { reason: string; applyTime: string; status: string }

const http = useHttp();
const { can } = usePermission();
const fallbackFace = "/favicon.ico";
const activeTab = ref<"all" | "pending" | "delete">("all");
const persons = ref<Person[]>([]);
const requests = ref<DeleteRequest[]>([]);
const personTotal = ref(0);
const requestTotal = ref(0);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const page = ref(1);
const pageSize = 8;
const filters = reactive({ keyword: "", dept: "", approveStatus: "", syncStatus: "" });
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
const form = reactive({ code: "", dept: "", name: "", phone: "", idCard: "" });

const departments = computed(() => [...new Set(persons.value.map((person) => person.dept).filter(Boolean))]);
const filteredPersons = computed(() => persons.value.filter((person) => (!filters.keyword || `${person.code}${person.name}${person.phone}${person.idCard}`.toLowerCase().includes(filters.keyword.toLowerCase())) && (!filters.dept || person.dept === filters.dept) && (!filters.approveStatus || person.approveStatus === filters.approveStatus) && (!filters.syncStatus || person.syncStatus === filters.syncStatus)));
const pendingPersons = computed(() => persons.value.filter((person) => person.approveStatus === "审核中" && (!filters.keyword || `${person.code}${person.name}${person.phone}${person.idCard}`.toLowerCase().includes(filters.keyword.toLowerCase()))));
const filteredRequests = computed(() => requests.value.filter((request) => (!filters.keyword || `${request.code}${request.name}${request.phone}${request.idCard}`.toLowerCase().includes(filters.keyword.toLowerCase())) && (!deleteStatus.value || request.status === deleteStatus.value)));
const currentRows = computed(() => activeTab.value === "all" ? filteredPersons.value : activeTab.value === "pending" ? pendingPersons.value : filteredRequests.value);
const totalRows = computed(() => activeTab.value === "delete" ? requestTotal.value : personTotal.value);
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / pageSize)));
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const visiblePersons = computed(() => currentRows.value as Person[]);
const visibleRequests = computed(() => currentRows.value as DeleteRequest[]);

watch(activeTab, () => { page.value = 1; resetFilters(false); });
watch([filters, deleteStatus], () => { if (page.value > totalPages.value) page.value = 1; }, { deep: true });

async function loadData() {
  loading.value = true; errorMessage.value = "";
  try {
    const personData = await http.get<{ items: Person[]; total: number }>("/gate-persons", {
      keyword: filters.keyword || undefined,
      dept: filters.dept || undefined,
      approveStatus: activeTab.value === "pending" ? "审核中" : filters.approveStatus || undefined,
      syncStatus: filters.syncStatus || undefined,
      page: page.value,
      pageSize,
    });
    const requestData = await http.get<{ items: DeleteRequest[]; total: number }>("/gate-persons/delete-requests", {
      keyword: filters.keyword || undefined,
      status: deleteStatus.value || undefined,
      page: page.value,
      page_size: pageSize,
    });
    persons.value = personData.items || [];
    personTotal.value = personData.total || 0;
    requests.value = requestData.items || [];
    requestTotal.value = requestData.total || 0;
  } catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "门禁人员数据加载失败"; }
  finally { loading.value = false; }
}
async function search() { page.value = 1; await loadData(); }
async function resetFilters(resetKeyword = true) { if (resetKeyword) filters.keyword = ""; filters.dept = ""; filters.approveStatus = ""; filters.syncStatus = ""; deleteStatus.value = ""; page.value = 1; await loadData(); }
function changePage(nextPage: number) { if (nextPage < 1 || nextPage > totalPages.value) return; page.value = nextPage; void loadData(); }
function approveClass(status: string) { return status === "通过" ? "tag--green" : status === "审核中" ? "tag--blue" : "tag--red"; }
function deleteClass(status: string) { return status === "待处理" ? "tag--orange" : status === "已同意" ? "tag--green" : "tag--red"; }
function useFallbackFace(event: Event) { (event.target as HTMLImageElement).src = fallbackFace; }
async function openDetail(person: Person) { detailRequest.value = null; try { detailPerson.value = await http.get<Person>(`/gate-persons/${person.id}`); } catch { detailPerson.value = person; } }
function openDeleteDetail(request: DeleteRequest) { detailPerson.value = null; detailRequest.value = request; }
function closeDetail() { detailPerson.value = null; detailRequest.value = null; }
function openCreate() { editingId.value = null; Object.assign(form, { code: `GP${String(persons.value.length + 1).padStart(4, "0")}`, dept: "", name: "", phone: "", idCard: "" }); selectedFace.value = null; formError.value = ""; editorVisible.value = true; }
function openEdit(person: Person) { editingId.value = person.id; Object.assign(form, { code: person.code, dept: person.dept, name: person.name, phone: person.phone, idCard: person.idCard }); selectedFace.value = null; formError.value = ""; editorVisible.value = true; }
function selectFace(event: Event) { selectedFace.value = (event.target as HTMLInputElement).files?.[0] || null; }
function selectImport(event: Event) { selectedImport.value = (event.target as HTMLInputElement).files?.[0] || null; }

async function savePerson() {
  if (!form.code || !form.dept || !form.name || (!editingId.value && !selectedFace.value)) { formError.value = "编号、单位、用户名和人脸照片不能为空"; return; }
  saving.value = true; formError.value = "";
  try {
    const body = new FormData(); body.append("code", form.code); body.append("dept", form.dept); body.append("name", form.name); body.append("phone", form.phone); body.append("idCard", form.idCard); if (selectedFace.value) body.append("face", selectedFace.value);
    if (editingId.value) await http.put(`/gate-persons/${editingId.value}`, body, { payloadMode: "json" }); else await http.post("/gate-persons", body, { payloadMode: "json" });
    editorVisible.value = false; await loadData();
  } catch (error) { formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败"; }
  finally { saving.value = false; }
}
async function requestDelete(person: Person) { const reason = window.prompt(`请输入删除“${person.name}”的原因`); if (!reason?.trim()) return; try { await http.post(`/gate-persons/${person.id}/delete-requests`, { reason: reason.trim() }, { payloadMode: "json" }); await loadData(); } catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "提交删除申请失败"; } }
async function approvePerson(person: Person) { await updateApproval(person, "approve"); }
async function rejectPerson(person: Person) { await updateApproval(person, "reject"); }
async function updateApproval(person: Person, action: "approve" | "reject") { try { await http.put(`/gate-persons/${person.id}/${action}`); await loadData(); } catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "审批操作失败"; } }
async function approveAll() { const pending = pendingPersons.value; if (!pending.length) return; if (!window.confirm(`确认批量通过 ${pending.length} 名待审核人员？`)) return; await Promise.all(pending.map((person) => http.put(`/gate-persons/${person.id}/approve`))); await loadData(); }
async function approveDelete(request: DeleteRequest) { try { await http.put(`/gate-persons/delete-requests/${request.id}/approve`); await loadData(); } catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除申请处理失败"; } }
async function rejectDelete(request: DeleteRequest) { try { await http.put(`/gate-persons/delete-requests/${request.id}/reject`); await loadData(); } catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除申请处理失败"; } }
function openImport() { selectedImport.value = null; formError.value = ""; importVisible.value = true; }
async function importCsv() { if (!selectedImport.value) { formError.value = "请选择 CSV 文件"; return; } formError.value = "导入接口需要逐条上传人脸照片，请使用新增人员完成导入"; }
function exportPersons() { const rows = [["编号", "单位", "用户名", "手机号", "身份证", "入库时间", "审批状态", "同步状态"], ...persons.value.map((person) => [person.code, person.dept, person.name, person.phone, person.idCard, person.createTime, person.approveStatus, person.syncStatus])]; const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n"); const link = document.createElement("a"); link.href = URL.createObjectURL(new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" })); link.download = "门禁人员信息.csv"; link.click(); URL.revokeObjectURL(link.href); }
onMounted(loadData);
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadData);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }.page__desc { color: var(--text-sub); font-size: 13px; margin: 2px 0 0; }.page__actions, .toolbar__right { display: flex; gap: 8px; }
.tabs { border-bottom: 1px solid var(--border); display: flex; gap: 4px; margin-bottom: 16px; }.gp-tab { background: transparent; border: 0; border-bottom: 2px solid transparent; color: var(--text-sub); cursor: pointer; font-size: 14px; font-weight: 500; padding: 10px 20px; transition: color .15s, border-color .15s; white-space: nowrap; }.gp-tab:hover { color: var(--text); }.gp-tab--active { border-bottom-color: var(--primary); color: var(--primary); }
.card { background: var(--card); border: 1px solid var(--border-strong); border-radius: var(--radius); }.card__body { padding: 18px; }.filter-body { padding-bottom: 4px; }.toolbar { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }.toolbar .input, .toolbar .select { min-width: 140px; width: auto; }.toolbar__right { margin-left: auto; }.input, .select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; outline: none; padding: 0 10px; }.input:focus, .select:focus { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary-soft); }.btn { align-items: center; border: 0; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; font-size: 13px; gap: 5px; height: 32px; padding: 0 12px; transition: all var(--tr); white-space: nowrap; }.btn--sm { font-size: 12px; height: 28px; padding: 0 10px; }.btn--primary { background: var(--primary); color: #fff; }.btn--soft { background: var(--primary-soft); color: var(--primary); }.btn--ghost { background: var(--card); border: 1px solid var(--border-strong); color: var(--text-sub); }.btn:hover:not(:disabled) { filter: brightness(.97); }.btn .material-icons-outlined { font-size: 16px; }
.table-wrap { overflow-x: auto; }table.table { border-collapse: collapse; font-size: 13px; min-width: 980px; width: 100%; }.table th { background: var(--bg); border-bottom: 1px solid var(--border); color: var(--text-mute); font-size: 12px; font-weight: 500; padding: 10px 16px; text-align: left; white-space: nowrap; }.table td { border-bottom: 1px solid var(--border); color: var(--text); padding: 11px 16px; white-space: nowrap; }.table tbody tr:hover { background: var(--bg); }.table tbody tr:last-child td { border-bottom: none; }.right { text-align: right !important; }.actions-cell { white-space: nowrap; }.record-face { border: 1px solid var(--border); border-radius: 4px; height: 48px; object-fit: cover; width: 48px; }.row-act, .icon-btn { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; margin: 0 1px; width: 28px; }.row-act:hover, .icon-btn:hover { background: var(--bg); color: var(--text); }.row-act--danger:hover { background: var(--red-soft); color: var(--red); }.row-act .material-icons-outlined { font-size: 16px; }.tag { align-items: center; border-radius: 4px; display: inline-flex; font-size: 12px; font-weight: 500; gap: 4px; line-height: 1.5; padding: 2px 8px; }.tag::before { border-radius: 50%; content: ""; height: 5px; width: 5px; }.tag--green { background: var(--green-soft, #f0fdf4); color: var(--green, #059669); }.tag--green::before { background: var(--green, #059669); }.tag--blue { background: var(--primary-soft); color: var(--primary); }.tag--blue::before { background: var(--primary); }.tag--orange { background: var(--orange-soft, #fff7ed); color: var(--orange, #ea580c); }.tag--orange::before { background: var(--orange, #ea580c); }.tag--red { background: var(--red-soft, #fef2f2); color: var(--red); }.tag--red::before { background: var(--red); }.empty-row { color: var(--text-mute) !important; padding: 40px !important; text-align: center !important; }.state { color: var(--text-mute); padding: 48px; text-align: center; }.state--error, .form-error { color: var(--red); }
.pagination { align-items: center; border-top: 1px solid var(--border); display: flex; flex-wrap: wrap; gap: 4px; justify-content: flex-end; padding: 12px 16px; }.pagination__info { color: var(--text-sub); font-size: 12px; margin-right: auto; }.page-btn { align-items: center; background: var(--card); border: 1px solid var(--border-strong); border-radius: 5px; color: var(--text-sub); cursor: pointer; display: flex; font-size: 12px; height: 28px; justify-content: center; min-width: 28px; padding: 0 6px; transition: all var(--tr); }.page-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }.page-btn.active { background: var(--primary); border-color: var(--primary); color: #fff; }.page-btn:disabled { cursor: not-allowed; opacity: .4; }.page-btn .material-icons-outlined { font-size: 16px; }
.modal-mask { align-items: center; background: rgb(0 0 0 / 40%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 100; }.modal { background: var(--card); border: 1px solid var(--border-strong); border-radius: var(--radius); display: flex; flex-direction: column; max-height: calc(100vh - 40px); max-width: calc(100vw - 40px); overflow: hidden; width: 640px; }.modal--detail { width: 640px; }.modal__head { align-items: center; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; padding: 14px 20px; }.modal__title { color: var(--text); font-size: 15px; font-weight: 600; margin: 0; }.modal__body { overflow-y: auto; padding: 20px; }.modal__foot { border-top: 1px solid var(--border); display: flex; gap: 8px; justify-content: flex-end; padding: 12px 20px; }.detail-body { display: flex; gap: 24px; align-items: flex-start; }.detail-face { border: 1px solid var(--border); border-radius: 6px; flex-shrink: 0; height: 140px; object-fit: cover; width: 105px; }.table--compact { margin: 0; min-width: 0 !important; }.table--compact td { padding: 8px 12px; }.table--compact td:first-child { color: var(--text-sub); width: 100px; }.form-grid { display: grid; gap: 0 16px; grid-template-columns: 1fr 1fr; }.field { display: block; margin-bottom: 14px; }.field__label { color: var(--text-sub); display: block; font-size: 12px; font-weight: 500; margin-bottom: 5px; }.field__label em { color: var(--red); font-style: normal; margin-left: 2px; }.field .input { width: 100%; }.field--full { grid-column: 1 / -1; }.file-input { padding-top: 7px; }.import-hint { color: var(--text-sub); font-size: 12px; margin: 10px 0 0; }
@media (max-width: 768px) { .page { padding: 16px; }.toolbar { align-items: stretch; flex-direction: column; }.toolbar .input, .toolbar .select { width: 100%; }.toolbar__right { justify-content: flex-end; margin-left: 0; }.form-grid { grid-template-columns: 1fr; }.field--full { grid-column: auto; }.detail-body { flex-direction: column; }.modal { max-width: calc(100vw - 24px); }.tabs { overflow-x: auto; } }
</style>
