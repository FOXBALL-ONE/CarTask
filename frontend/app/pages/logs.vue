<template>
  <section class="page">
    <header class="page__header">
      <div><h1 class="page__title">日志管理</h1>
        <p class="page__desc">{{
            activeTab === "op" ? "记录系统中所有用户的操作行为" : "记录用户登录与退出系统行为"
          }}</p></div>
      <button v-if="can('audit:delete')" class="button button--danger button--sm" type="button" @click="clearCurrent">
        <span class="material-icons-outlined">delete_sweep</span>清空日志
      </button>
    </header>
    <section class="card">
      <header class="card__head">
        <div class="tabs">
          <button :class="{ active: activeTab === 'op' }" class="tab" type="button" @click="switchTab('op')"><span
              class="material-icons-outlined">manage_history</span>操作日志
          </button>
          <button :class="{ active: activeTab === 'lg' }" class="tab" type="button" @click="switchTab('lg')"><span
              class="material-icons-outlined">login</span>登录日志
          </button>
        </div>
      </header>
      <div class="card__body card__body--filters">
        <div class="toolbar"><input v-model.trim="currentFilters.keyword" :placeholder="activeTab === 'op' ? '操作人 / 描述' : '账号 / IP'" class="input"
                                    type="search"
                                    @keyup.enter="search"><select v-if="activeTab === 'op'"
                                                                  v-model="operationFilters.module" aria-label="日志模块"
                                                                  class="select">
          <option value="">全部模块</option>
          <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
        </select><select v-model="currentFilters.status" aria-label="日志状态" class="select">
          <option value="">全部状态</option>
          <option value="成功">成功</option>
          <option value="失败">失败</option>
        </select>
          <div class="toolbar__right">
            <button class="button button--soft button--sm" type="button" @click="search"><span
                class="material-icons-outlined">search</span>搜索
            </button>
            <button class="button button--ghost button--sm" type="button" @click="resetFilters"><span
                class="material-icons-outlined">restart_alt</span>重置
            </button>
          </div>
        </div>
      </div>
      <div v-if="loading" class="state">正在加载日志数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr v-if="activeTab === 'op'">
            <th>编号</th>
            <th>操作人</th>
            <th>模块</th>
            <th>操作</th>
            <th>描述</th>
            <th>IP</th>
            <th>状态</th>
            <th>耗时</th>
            <th>时间</th>
          </tr>
          <tr v-else>
            <th>编号</th>
            <th>账号</th>
            <th>IP</th>
            <th>登录地点</th>
            <th>浏览器</th>
            <th>操作系统</th>
            <th>状态</th>
            <th>消息</th>
            <th>时间</th>
          </tr>
          </thead>
          <tbody>
          <template v-if="activeTab === 'op'">
            <tr v-for="log in pagedOperationLogs" :key="log.id">
              <td>{{ String(log.id).padStart(4, "0") }}</td>
              <td><strong>{{ log.user }}</strong></td>
              <td>{{ log.module }}</td>
              <td><span class="tag tag--blue">{{ log.action }}</span></td>
              <td class="text-sub">{{ log.desc }}</td>
              <td>{{ log.ip || "-" }}</td>
              <td><span :class="log.status === '成功' ? 'tag--green' : 'tag--red'" class="tag">{{ log.status }}</span>
              </td>
              <td><span class="tag tag--gray">{{ log.cost || "-" }}</span></td>
              <td class="text-sub nowrap">{{ log.time }}</td>
            </tr>
          </template>
          <template v-else>
            <tr v-for="log in pagedLoginLogs" :key="log.id">
              <td>{{ String(log.id).padStart(4, "0") }}</td>
              <td><strong>{{ log.user }}</strong></td>
              <td>{{ log.ip || "-" }}</td>
              <td>{{ log.location || "-" }}</td>
              <td>{{ log.browser || "-" }}</td>
              <td>{{ log.os || "-" }}</td>
              <td><span :class="log.status === '成功' ? 'tag--green' : 'tag--red'" class="tag">{{ log.status }}</span>
              </td>
              <td class="text-sub">{{ log.message || "-" }}</td>
              <td class="text-sub nowrap">{{ log.time }}</td>
            </tr>
          </template>
          <tr v-if="activeRows.length === 0">
            <td class="empty" colspan="9">暂无数据</td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{
          activeTotal
        }} 条</span>
        <button :disabled="page <= 1" class="page-btn" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                class="page-btn" type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" class="page-btn" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>
  </section>
</template>

<script lang="ts" setup>
interface OperationLog {
  id: number;
  user: string;
  module: string;
  action: string;
  desc: string;
  ip?: string | null;
  status: string;
  time: string;
  cost?: string | null
}

interface LoginLog {
  id: number;
  user: string;
  ip?: string | null;
  location?: string | null;
  browser?: string | null;
  os?: string | null;
  status: string;
  time: string;
  message?: string | null
}

interface LogList<T> {
  items: T[];
  total: number
}

const http = useHttp();
const {can} = usePermission();
const pageSize = 8;
const activeTab = ref<"op" | "lg">("op");
const operationLogs = ref<OperationLog[]>([]);
const loginLogs = ref<LoginLog[]>([]);
const operationTotal = ref(0);
const loginTotal = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const page = ref(1);
const operationFilters = reactive({keyword: "", module: "", status: ""});
const loginFilters = reactive({keyword: "", status: ""});
const modules = computed(() => [...new Set(operationLogs.value.map((log) => log.module).filter(Boolean))]);
const filteredOperationLogs = computed(() => {
  const keyword = operationFilters.keyword.toLowerCase();
  return operationLogs.value.filter((log) => (!keyword || `${log.user}${log.desc}`.toLowerCase().includes(keyword)) && (!operationFilters.module || log.module === operationFilters.module) && (!operationFilters.status || log.status === operationFilters.status));
});
const filteredLoginLogs = computed(() => {
  const keyword = loginFilters.keyword.toLowerCase();
  return loginLogs.value.filter((log) => (!keyword || `${log.user}${log.ip || ""}`.toLowerCase().includes(keyword)) && (!loginFilters.status || log.status === loginFilters.status));
});
const activeRows = computed(() => activeTab.value === "op" ? filteredOperationLogs.value : filteredLoginLogs.value);
const activeTotal = computed(() => activeTab.value === "op" ? operationTotal.value : loginTotal.value);
const totalPages = computed(() => Math.max(1, Math.ceil(activeTotal.value / pageSize)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const pagedOperationLogs = computed(() => filteredOperationLogs.value);
const pagedLoginLogs = computed(() => filteredLoginLogs.value);
const currentFilters = computed(() => activeTab.value === "op" ? operationFilters : loginFilters);

function switchTab(tab: "op" | "lg") {
  activeTab.value = tab;
  page.value = 1;
  void loadLogs();
}

async function search() {
  page.value = 1;
  await loadLogs();
}

async function resetFilters() {
  if (activeTab.value === "op") Object.assign(operationFilters, {
    keyword: "",
    module: "",
    status: ""
  }); else Object.assign(loginFilters, {keyword: "", status: ""});
  page.value = 1;
  await loadLogs();
}

function changePage(nextPage: number) {
  page.value = Math.min(Math.max(1, nextPage), totalPages.value);
  void loadLogs();
}

async function clearCurrent() {
  if (!window.confirm(`确认清空所有${activeTab.value === "op" ? "操作" : "登录"}日志？`)) return;
  try {
    await http.delete(activeTab.value === "op" ? "/operation-logs" : "/login-logs");
    page.value = 1;
    await loadLogs();
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "清空日志失败";
  }
}

async function loadLogs() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const [operationResult, loginResult] = await Promise.all([http.get<LogList<OperationLog>>("/operation-logs", {
      keyword: operationFilters.keyword || undefined,
      module: operationFilters.module || undefined,
      status: operationFilters.status || undefined,
      page: page.value,
      pageSize
    }), http.get<LogList<LoginLog>>("/login-logs", {
      keyword: loginFilters.keyword || undefined,
      status: loginFilters.status || undefined,
      page: page.value,
      pageSize
    })]);
    operationLogs.value = operationResult.items || [];
    operationTotal.value = operationResult.total || 0;
    loginLogs.value = loginResult.items || [];
    loginTotal.value = loginResult.total || 0;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "日志数据加载失败";
  } finally {
    loading.value = false;
  }
}

watch(activeRows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value;
});
onMounted(loadLogs);
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px
}

.page__header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 20px
}

.page__title {
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  margin: 0
}

.page__desc {
  color: var(--text-sub);
  font-size: 13px;
  margin: 2px 0 0
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  overflow: hidden
}

.card__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  padding: 14px 18px
}

.tabs {
  display: flex;
  gap: 18px
}

.tab {
  align-items: center;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 13px;
  gap: 5px;
  padding: 4px 0 8px
}

.tab:hover {
  color: var(--text)
}

.tab.active {
  border-bottom-color: var(--primary);
  color: var(--primary)
}

.tab .material-icons-outlined {
  font-size: 16px
}

.card__body {
  padding: 18px
}

.card__body--filters {
  padding-bottom: 4px
}

.toolbar {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px
}

.toolbar .input, .toolbar .select {
  min-width: 140px;
  width: auto
}

.toolbar__right {
  display: flex;
  gap: 6px;
  margin-left: auto
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
  padding: 0 10px
}

.input:focus, .select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft)
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 13px;
  gap: 5px;
  height: 32px;
  justify-content: center;
  padding: 0 12px;
  white-space: nowrap
}

.button--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px
}

.button--soft {
  background: var(--primary-soft);
  color: var(--primary)
}

.button--ghost {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--text-sub)
}

.button--danger {
  background: var(--card);
  border-color: var(--border-strong);
  color: var(--red)
}

.button .material-icons-outlined {
  font-size: 16px
}

.table-wrap {
  overflow-x: auto
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 1050px;
  width: 100%
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 12px;
  font-weight: 500;
  padding: 10px 16px;
  text-align: left;
  white-space: nowrap
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 11px 16px;
  white-space: nowrap
}

.table tbody tr:hover {
  background: var(--bg)
}

.text-sub {
  color: var(--text-sub) !important
}

.nowrap {
  white-space: nowrap
}

.tag {
  align-items: center;
  border-radius: 4px;
  display: inline-flex;
  font-size: 12px;
  gap: 4px;
  line-height: 1.5;
  padding: 2px 8px
}

.tag:before {
  background: currentColor;
  border-radius: 50%;
  content: "";
  height: 5px;
  width: 5px
}

.tag--green {
  background: var(--success-soft);
  color: var(--success)
}

.tag--red {
  background: var(--danger-soft);
  color: var(--danger)
}

.tag--blue {
  background: var(--primary-soft);
  color: var(--primary)
}

.tag--gray {
  background: var(--neutral-soft);
  color: var(--text-sub)
}

.state, .empty {
  color: var(--text-mute);
  padding: 40px;
  text-align: center
}

.state--error {
  color: var(--red)
}

.pagination {
  align-items: center;
  border-top: 1px solid var(--border);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
  padding: 12px 16px
}

.pagination__info {
  color: var(--text-sub);
  font-size: 12px;
  margin-right: auto
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
  padding: 0 6px
}

.page-btn.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--on-solid)
}

.page-btn:disabled {
  cursor: not-allowed;
  opacity: .4
}

.page-btn .material-icons-outlined {
  font-size: 16px
}

@media (max-width: 768px) {
  .page {
    padding: 16px
  }

  .toolbar__right {
    margin-left: 0
  }

  .toolbar .input, .toolbar .select, .toolbar__right {
    width: 100%
  }

  .toolbar__right .button {
    flex: 1
  }
}
</style>
