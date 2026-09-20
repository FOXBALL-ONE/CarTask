<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>宣传通告</h1>
        <p>维护面向员工展示的宣传与通知内容</p>
      </div>
      <div class="page-actions">
        <button class="button button--ghost" type="button" @click="openPublicPage">
          <span class="material-icons-outlined">open_in_new</span>打开公开页
        </button>
        <button class="button button--primary" type="button" @click="openCreate">
          <span class="material-icons-outlined">add</span>新增通告
        </button>
      </div>
    </header>

    <section class="panel">
      <form class="filters" @submit.prevent="search">
        <label><span class="sr-only">标题</span><input v-model.trim="filters.title" placeholder="按标题搜索" type="search"></label>
        <label><span class="sr-only">发布时间</span><input v-model="filters.publishedDate" type="date"></label>
        <button class="button button--soft" type="submit"><span class="material-icons-outlined">search</span>搜索</button>
        <button class="button button--ghost" type="button" @click="reset"><span class="material-icons-outlined">restart_alt</span>重置</button>
      </form>

      <p v-if="errorMessage" class="state state--error">{{ errorMessage }}</p>
      <p v-else-if="loading" class="state">正在加载通告...</p>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>标题</th><th>正文摘要</th><th>发布时间</th><th aria-label="操作"/></tr></thead>
          <tbody>
          <tr v-for="item in items" :key="item.id">
            <td><strong>{{ item.title }}</strong></td>
            <td class="excerpt">{{ item.content }}</td>
            <td>{{ formatDate(item.published_at) }}</td>
            <td class="actions">
              <button class="icon-button" title="编辑" type="button" @click="openEdit(item)"><span class="material-icons-outlined">edit</span></button>
              <button class="icon-button icon-button--danger" title="删除" type="button" @click="remove(item)"><span class="material-icons-outlined">delete</span></button>
            </td>
          </tr>
          <tr v-if="items.length === 0"><td class="empty" colspan="4">暂无通告</td></tr>
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
        <header><h2>{{ editingId === null ? "新增通告" : "编辑通告" }}</h2><button class="icon-button" title="关闭" type="button" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button></header>
        <div class="modal-body">
          <label class="field"><span>标题</span><input v-model.trim="form.title" maxlength="160" required></label>
          <label class="field"><span>正文</span><textarea v-model.trim="form.content" rows="8" required/></label>
          <label class="field"><span>发布时间</span><input v-model="form.published_at" required type="datetime-local"></label>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer><button class="button button--ghost" type="button" @click="editorVisible = false">取消</button><button :disabled="saving" class="button button--primary" type="submit">{{ saving ? "保存中..." : "保存" }}</button></footer>
      </form>
    </div>
  </section>
</template>

<script lang="ts" setup>
interface Announcement { id: number; title: string; content: string; published_at: string }
interface ListResponse { items: Announcement[]; total: number }

const http = useHttp();
const items = ref<Announcement[]>([]);
const total = ref(0);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const page = ref(1);
const pageSize = 10;
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const filters = reactive({title: "", publishedDate: ""});
const form = reactive({title: "", content: "", published_at: ""});
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

function localDateTime(value: string) { return value ? value.slice(0, 16) : ""; }
function formatDate(value: string) { return value ? value.replace("T", " ").slice(0, 16) : "-"; }
function currentLocalDateTime() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}
function resetForm() { Object.assign(form, {title: "", content: "", published_at: currentLocalDateTime()}); }

async function load() {
  loading.value = true; errorMessage.value = "";
  try {
    const result = await http.get<ListResponse>("/announcements", {title: filters.title || undefined, published_date: filters.publishedDate || undefined, page: page.value, page_size: pageSize});
    items.value = result.items || []; total.value = result.total || 0;
  } catch (error) { errorMessage.value = (error as {statusMessage?: string}).statusMessage || "通告加载失败"; }
  finally { loading.value = false; }
}
async function search() { page.value = 1; await load(); }
async function reset() { Object.assign(filters, {title: "", publishedDate: ""}); await search(); }
async function changePage(nextPage: number) { if (nextPage >= 1 && nextPage <= totalPages.value) { page.value = nextPage; await load(); } }
function openCreate() { editingId.value = null; formError.value = ""; resetForm(); editorVisible.value = true; }
function openPublicPage() { window.open("/public-announcements", "_blank", "noopener,noreferrer"); }
function openEdit(item: Announcement) { editingId.value = item.id; formError.value = ""; Object.assign(form, {title: item.title, content: item.content, published_at: localDateTime(item.published_at)}); editorVisible.value = true; }
async function save() {
  saving.value = true; formError.value = "";
  try {
    const payload = {...form, published_at: form.published_at.length === 16 ? `${form.published_at}:00` : form.published_at};
    if (editingId.value === null) await http.post("/announcements", payload, {payloadMode: "json"}); else await http.put(`/announcements/${editingId.value}`, payload, {payloadMode: "json"});
    editorVisible.value = false; await load();
  } catch (error) { formError.value = (error as {statusMessage?: string}).statusMessage || "保存失败"; }
  finally { saving.value = false; }
}
async function remove(item: Announcement) { if (window.confirm(`确认删除“${item.title}”？`)) { await http.delete(`/announcements/${item.id}`); await load(); } }
onMounted(load);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }.page-header { align-items: center; display: flex; justify-content: space-between; gap: 16px; margin-bottom: 20px; }.page-header h1 { color: var(--text); font-size: 18px; margin: 0; }.page-header p { color: var(--text-sub); margin: 4px 0 0; }.page-actions { display: flex; flex-wrap: wrap; gap: 8px; }.panel,.modal { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; }.filters { align-items: center; border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; gap: 8px; padding: 14px 16px; }.filters input,.field input,.field textarea { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; padding: 8px 10px; }.filters input { height: 34px; }.field input,.field textarea { width: 100%; }.filters input:focus,.field input:focus,.field textarea:focus { border-color: var(--primary); outline: none; }.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 34px; padding: 0 12px; }.button:disabled,.icon-button:disabled { cursor: not-allowed; opacity: .45; }.button--primary { background: var(--primary); color: var(--on-solid); }.button--soft { background: var(--primary-soft); color: var(--primary); }.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }.button .material-icons-outlined { font-size: 17px; }.table-wrap { overflow-x: auto; } table { border-collapse: collapse; min-width: 680px; width: 100%; } th,td { border-bottom: 1px solid var(--border); color: var(--text); padding: 12px 16px; text-align: left; } th { background: var(--bg); color: var(--text-mute); font-size: 12px; font-weight: 500; } .excerpt { color: var(--text-sub); max-width: 460px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.actions { text-align: right; white-space: nowrap; }.icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 30px; justify-content: center; width: 30px; }.icon-button:hover { background: var(--bg); color: var(--text); }.icon-button--danger:hover { background: var(--danger-soft); color: var(--danger); }.icon-button .material-icons-outlined { font-size: 18px; }.state,.empty { color: var(--text-mute); padding: 44px; text-align: center; }.state--error,.form-error { color: var(--danger); }.pager { align-items: center; color: var(--text-sub); display: flex; gap: 8px; justify-content: flex-end; padding: 10px 16px; }.pager > :first-child { margin-right: auto; }.modal-mask { align-items: center; background: rgb(0 0 0 / 38%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 100; }.modal { display: flex; flex-direction: column; max-width: 600px; width: 100%; }.modal header,.modal footer { align-items: center; display: flex; justify-content: space-between; padding: 14px 18px; }.modal header { border-bottom: 1px solid var(--border); }.modal footer { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }.modal h2 { color: var(--text); font-size: 15px; margin: 0; }.modal-body { padding: 18px; }.field { display: block; margin-bottom: 14px; }.field span { color: var(--text-sub); display: block; font-size: 12px; margin-bottom: 6px; }.field textarea { resize: vertical; }.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }.form-error { margin: 0; } @media (max-width: 768px) { .page { padding: 16px; }.page-header { align-items: flex-start; flex-direction: column; }.filters label { flex: 1 1 100%; }.filters input { width: 100%; }.filters .button { flex: 1; justify-content: center; }.modal-mask { align-items: flex-end; padding: 0; }.modal { border-bottom-left-radius: 0; border-bottom-right-radius: 0; max-height: calc(100dvh - 24px); overflow: auto; } }
</style>
