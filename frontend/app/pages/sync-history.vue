<template>
  <section class="page">
    <header class="page__header"><div><h1 class="page__title">同步执行历史</h1><p class="page__desc">数据同步与账号生成任务的近期执行记录，包含手动触发和定时调度。</p></div></header>
    <section class="card">
      <div class="card__body card__body--filters"><div class="toolbar"><select v-model="filters.taskKey" class="select" aria-label="同步任务" @change="search"><option value="">全部任务</option><option v-for="task in tasks" :key="task.key" :value="task.key">{{ task.label }}</option></select><div class="toolbar__right"><button class="button button--soft button--sm" type="button" @click="search"><span class="material-icons-outlined">search</span>查询</button><button class="button button--ghost button--sm" type="button" @click="resetFilters"><span class="material-icons-outlined">restart_alt</span>重置</button></div></div></div>
      <div v-if="loading" class="state">正在加载执行历史...</div><div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div><div v-else class="table-wrap"><table class="table"><thead><tr><th>编号</th><th>任务</th><th>触发方式</th><th>状态</th><th>操作人</th><th>开始时间</th><th>耗时</th><th>处理数</th><th>摘要 / 错误</th></tr></thead><tbody><tr v-for="run in runs" :key="run.id"><td>{{ String(run.id).padStart(4, "0") }}</td><td><strong>{{ run.task_name }}</strong></td><td><span class="tag" :class="run.trigger === 'MANUAL' ? 'tag--blue' : 'tag--gray'">{{ triggerLabel(run.trigger) }}</span></td><td><span class="tag" :class="run.status === 'SUCCESS' ? 'tag--green' : 'tag--red'">{{ statusLabel(run.status) }}</span></td><td>{{ run.actor_username }}</td><td class="text-sub nowrap">{{ formatDateTime(run.started_at) }}</td><td><span class="tag tag--gray">{{ formatDuration(run.duration_ms) }}</span></td><td>{{ run.processed_count ?? "—" }}</td><td class="summary-cell" :title="run.error || run.summary || ''"><span :class="{ 'summary-cell--error': run.error }">{{ run.error || run.summary || "-" }}</span></td></tr><tr v-if="runs.length === 0"><td colspan="9" class="empty">暂无数据</td></tr></tbody></table></div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{ total }} 条</span><button class="page-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button><button v-for="pageNumber in pageNumbers" :key="pageNumber" class="page-btn" :class="{ active: pageNumber === page }" type="button" @click="changePage(pageNumber)">{{ pageNumber }}</button><button class="page-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button></footer>
    </section>
  </section>
</template>

<script setup lang="ts">
interface SyncTaskRunRecord {
  id: number;
  task_key: string;
  task_name: string;
  trigger: "MANUAL" | "SCHEDULED";
  status: "SUCCESS" | "FAILED";
  actor_username: string;
  started_at: string;
  duration_ms: number;
  processed_count: number | null;
  summary: string | null;
  error: string | null;
}

interface SyncHistoryResponse {
  runs: SyncTaskRunRecord[];
  page: number;
  total: number;
}

const http = useHttp();
const pageSize = 20;
const tasks = [
  { key: "parking_area.sync", label: "停车区域同步" },
  { key: "car_cap_info.sync", label: "车辆进出记录同步" },
  { key: "owner.archive.generate", label: "车主档案补建" },
  { key: "account.generate", label: "车辆业主账号生成" },
];
const filters = reactive({ taskKey: "" });
const runs = ref<SyncTaskRunRecord[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(true);
const errorMessage = ref("");
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));

function triggerLabel(trigger: SyncTaskRunRecord["trigger"]) {
  return trigger === "MANUAL" ? "手动" : "定时";
}
function statusLabel(status: SyncTaskRunRecord["status"]) {
  return status === "SUCCESS" ? "成功" : "失败";
}
function formatDuration(durationMs: number) {
  if (durationMs < 1000) return `${durationMs} ms`;
  const seconds = Math.round(durationMs / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
}
function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(date);
}

async function loadHistory() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<SyncHistoryResponse>("/synchronizations/history", {
      page: page.value,
      page_size: pageSize,
      task_key: filters.taskKey || undefined,
    });
    runs.value = result.runs || [];
    total.value = result.total || 0;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "执行历史加载失败";
  } finally {
    loading.value = false;
  }
}
async function search() {
  page.value = 1;
  await loadHistory();
}
async function resetFilters() {
  filters.taskKey = "";
  page.value = 1;
  await loadHistory();
}
function changePage(nextPage: number) {
  page.value = Math.min(Math.max(1, nextPage), totalPages.value);
  void loadHistory();
}
onMounted(loadHistory);
</script>

<style scoped>
.page{min-height:100%;padding:24px}.page__header{align-items:center;display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between;margin-bottom:20px}.page__title{color:var(--text);font-size:18px;font-weight:600;margin:0}.page__desc{color:var(--text-sub);font-size:13px;margin:2px 0 0}.card{background:var(--card);border:1px solid var(--border-strong);border-radius:8px;overflow:hidden}.card__body{padding:18px}.card__body--filters{padding-bottom:4px}.toolbar{align-items:center;display:flex;flex-wrap:wrap;gap:10px;margin-bottom:14px}.toolbar .select{min-width:160px;width:auto}.toolbar__right{display:flex;gap:6px;margin-left:auto}.select{background:var(--card);border:1px solid var(--border-strong);border-radius:6px;box-sizing:border-box;color:var(--text);font:inherit;height:34px;outline:none;padding:0 10px}.select:focus{border-color:var(--primary);box-shadow:0 0 0 2px var(--primary-soft)}.button{align-items:center;border:1px solid transparent;border-radius:6px;cursor:pointer;display:inline-flex;font:inherit;font-size:13px;gap:5px;height:32px;justify-content:center;padding:0 12px;white-space:nowrap}.button--sm{font-size:12px;height:28px;padding:0 10px}.button--soft{background:var(--primary-soft);color:var(--primary)}.button--ghost{background:var(--card);border-color:var(--border-strong);color:var(--text-sub)}.button .material-icons-outlined{font-size:16px}.table-wrap{overflow-x:auto}.table{border-collapse:collapse;font-size:13px;min-width:1050px;width:100%}.table th{background:var(--bg);border-bottom:1px solid var(--border);color:var(--text-mute);font-size:12px;font-weight:500;padding:10px 16px;text-align:left;white-space:nowrap}.table td{border-bottom:1px solid var(--border);color:var(--text);padding:11px 16px;white-space:nowrap}.table tbody tr:hover{background:var(--bg)}.text-sub{color:var(--text-sub)!important}.nowrap{white-space:nowrap}.tag{align-items:center;border-radius:4px;display:inline-flex;font-size:12px;gap:4px;line-height:1.5;padding:2px 8px}.tag:before{background:currentColor;border-radius:50%;content:"";height:5px;width:5px}.tag--green{background:#ecfdf5;color:#059669}.tag--red{background:#fef2f2;color:#dc2626}.tag--blue{background:var(--primary-soft);color:var(--primary)}.tag--gray{background:#f4f4f5;color:var(--text-sub)}.summary-cell{max-width:360px;overflow:hidden;text-overflow:ellipsis}.summary-cell--error{color:var(--red)!important}.state,.empty{color:var(--text-mute);padding:40px;text-align:center}.state--error{color:var(--red)}.pagination{align-items:center;border-top:1px solid var(--border);display:flex;flex-wrap:wrap;gap:4px;justify-content:flex-end;padding:12px 16px}.pagination__info{color:var(--text-sub);font-size:12px;margin-right:auto}.page-btn{align-items:center;background:var(--card);border:1px solid var(--border-strong);border-radius:5px;color:var(--text-sub);cursor:pointer;display:flex;font-size:12px;height:28px;justify-content:center;min-width:28px;padding:0 6px}.page-btn.active{background:var(--primary);border-color:var(--primary);color:#fff}.page-btn:disabled{cursor:not-allowed;opacity:.4}.page-btn .material-icons-outlined{font-size:16px}@media(max-width:768px){.page{padding:16px}.toolbar__right{margin-left:0;width:100%}.toolbar .select{flex:1}}
</style>
