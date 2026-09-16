<template>
  <section class="page">
    <header class="page__header">
      <div><h1 class="page__title">车辆进出记录</h1>
        <p class="page__desc">查看车辆通行记录与放行信息</p></div>
      <button v-if="can('vehicle-record:export')" class="button button--soft button--sm" type="button"
              @click="exportRecords"><span
          class="material-icons-outlined">download</span>导出
      </button>
    </header>
    <section class="card">
      <div class="card__body card__body--filters">
        <div class="toolbar"><input v-model.trim="filters.keyword" class="input" placeholder="搜索车牌 / 车主 / 部门"
                                    type="search" @keyup.enter="search"><select
            v-model="filters.direction" aria-label="通行方向" class="select">
          <option value="">全部方向</option>
          <option value="进">进</option>
          <option value="出">出</option>
        </select><select v-model="filters.gate" aria-label="通道" class="select">
          <option value="">全部通道</option>
          <option v-for="gate in gates" :key="gate" :value="gate">{{ gate }}</option>
        </select><select v-model="filters.passType" aria-label="放行类型" class="select">
          <option value="">全部放行类型</option>
          <option v-for="passType in passTypes" :key="passType" :value="passType">{{ passLabel(passType) }}</option>
        </select><select v-model="timeFormat" aria-label="时间展示格式" class="select">
          <option value="24">24小时制</option>
          <option value="12">12小时制</option>
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
      <div v-if="loading" class="state">正在加载车辆进出记录...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th>车牌号码</th>
            <th>车主名称</th>
            <th>方向</th>
            <th>进/出时间</th>
            <th>进出场地点</th>
            <th>车辆类型</th>
            <th>放行类型</th>
            <th>记录状态</th>
            <th class="actions-cell">操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="record in pagedRecords" :key="record.id">
            <td class="plate">{{ record.plate || "-" }}</td>
            <td>{{ record.owner || "-" }}</td>
            <td><span :class="record.direction === '进' ? 'tag--blue' : 'tag--gray'" class="tag">{{
                record.direction
              }}</span></td>
            <td class="text-sub nowrap">{{ formatTime(record.time) }}</td>
            <td>{{ record.gate || "-" }}</td>
            <td>{{ record.vehicleType || "-" }}</td>
            <td><span :class="passClass(record.passType)" class="tag">{{ passLabel(record.passType) || "-" }}</span>
            </td>
            <td class="text-sub remark-cell">{{ record.status || "-" }}</td>
            <td class="actions-cell">
              <button :aria-label="`查看${record.plate || '车辆'}详情`" class="detail-action" type="button"
                      @click="openDetails(record)"><span class="material-icons-outlined">visibility</span>查看详情
              </button>
            </td>
          </tr>
          <tr v-if="pagedRecords.length === 0">
            <td class="empty" colspan="9">暂无记录</td>
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
        <button :disabled="page <= 1" class="page-btn" type="button" @click="changePage(page - 1)"><span
            class="material-icons-outlined">chevron_left</span></button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                class="page-btn" type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" class="page-btn" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>
    <div v-if="selectedRecord" class="detail-mask" role="presentation" @click.self="closeDetails">
      <section :aria-label="`${selectedRecord.plate || '车辆'}通行详情`" aria-modal="true" class="detail-modal"
               role="dialog">
        <header class="detail-modal__head">
          <div><span class="detail-kicker">通行记录</span>
            <h2>{{ selectedRecord.plate || "无牌车辆" }}</h2></div>
          <button aria-label="关闭详情" class="icon-button" type="button" @click="closeDetails"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="detail-modal__body">
          <div class="detail-photo-panel">
            <div v-if="detailImageLoading" class="detail-photo-empty"><span
                class="material-icons-outlined detail-spinner">progress_activity</span><strong>正在加载抓拍图片</strong><span>请稍候</span>
            </div>
            <div v-else-if="detailImageSrc && !detailImageError" class="detail-photo-frame">
              <button :aria-label="`放大查看${selectedRecord.plate || '车辆'}抓拍图片`" class="detail-photo-trigger"
                      title="放大查看图片"
                      type="button" @click="openImagePreview"><img
                  :alt="`${selectedRecord.plate || '车辆'}抓拍图片`" :src="detailImageSrc"
                  @error="detailImageError = true"><span aria-hidden="true"
                                                         class="material-icons-outlined detail-photo-zoom">zoom_in</span>
              </button>
            </div>
            <div v-else class="detail-photo-empty"><span class="material-icons-outlined">no_photography</span><strong>{{
                selectedRecord.photo ? "图片暂时无法加载" : "暂无抓拍图片"
              }}</strong><span>{{ selectedRecord.photo ? "请稍后重试或联系管理员" : "该记录未返回图片地址" }}</span>
            </div>
            <div class="detail-photo-caption"><span
                class="material-icons-outlined">photo_camera</span><span>进出场抓拍</span></div>
          </div>
          <div class="detail-info-panel">
            <div class="detail-status-row"><span :class="selectedRecord.direction === '进' ? 'tag--blue' : 'tag--gray'"
                                                 class="tag">{{
                selectedRecord.direction
              }}场</span><span :class="passClass(selectedRecord.passType)" class="tag">{{
                passLabel(selectedRecord.passType) || "未知放行"
              }}</span><span v-if="selectedRecord.status"
                             :class="selectedRecord.status === '正常' ? 'tag--green' : 'tag--red'"
                             class="tag">{{
                selectedRecord.status
              }}</span></div>
            <dl class="detail-grid">
              <div>
                <dt>通行时间</dt>
                <dd>{{ formatTime(selectedRecord.time) || "-" }}</dd>
              </div>
              <div>
                <dt>进出场地点</dt>
                <dd>{{ selectedRecord.gate || "-" }}</dd>
              </div>
              <div>
                <dt>车主名称</dt>
                <dd>{{ selectedRecord.owner || "-" }}</dd>
              </div>
              <div>
                <dt>所属部门</dt>
                <dd>{{ selectedRecord.dept || "-" }}</dd>
              </div>
              <div>
                <dt>车辆类型</dt>
                <dd>{{ selectedRecord.vehicleType || "-" }}</dd>
              </div>
              <div>
                <dt>记录编号</dt>
                <dd>{{ selectedRecord.id }}</dd>
              </div>
              <div class="detail-grid__wide">
                <dt>记录状态</dt>
                <dd>{{ selectedRecord.status || "-" }}</dd>
              </div>
            </dl>
          </div>
        </div>
        <footer class="detail-modal__foot">
          <button class="button button--ghost" type="button" @click="closeDetails"><span
              class="material-icons-outlined">close</span>关闭
          </button>
        </footer>
      </section>
      <div v-if="imagePreviewVisible && detailImageSrc && !detailImageError" class="image-preview-mask"
           role="presentation" @click.self="closeImagePreview">
        <section :aria-label="`${selectedRecord.plate || '车辆'}抓拍图片预览`" aria-modal="true" class="image-preview"
                 role="dialog">
          <button aria-label="关闭图片预览" class="icon-button image-preview__close" title="关闭图片预览" type="button"
                  @click="closeImagePreview"><span class="material-icons-outlined">close</span></button>
          <img :alt="`${selectedRecord.plate || '车辆'}抓拍图片预览`" :src="detailImageSrc" class="image-preview__image"
               @error="detailImageError = true"></section>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
interface VehicleRecord {
  id: number;
  plate?: string | null;
  owner?: string | null;
  dept?: string | null;
  time: string;
  direction: string;
  gate?: string | null;
  method?: string | null;
  status?: string | null;
  photo?: string | null;
  vehicleType?: string | null;
  passDesc?: string | null
}

interface RecordList {
  items: VehicleRecord[];
  total: number
}

const http = useHttp();
const {can} = usePermission();
const pageSizes = [10, 20, 50, 100];
const pageSize = ref(20);
const records = ref<VehicleRecord[]>([]);
const total = ref(0);
const loading = ref(true);
const errorMessage = ref("");
const page = ref(1);
const filters = reactive({keyword: "", direction: "", gate: "", passType: ""});
const normalizedRecords = computed(() => records.value.map((record) => ({
  ...record,
  passType: record.method || "未知",
  passDesc: record.status || ""
})));
const gates = computed(() => [...new Set(normalizedRecords.value.map((record) => record.gate).filter((gate): gate is string => Boolean(gate)))]);
const passTypes = computed(() => [...new Set(normalizedRecords.value.map((record) => record.passType).filter(Boolean))]);
const filteredRecords = computed(() => {
  const keyword = filters.keyword.toLowerCase();
  return normalizedRecords.value.filter((record) => (!keyword || `${record.plate || ""}${record.owner || ""}${record.dept || ""}`.toLowerCase().includes(keyword)) && (!filters.direction || record.direction === filters.direction) && (!filters.gate || record.gate === filters.gate) && (!filters.passType || record.passType === filters.passType));
});
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const pagedRecords = computed(() => filteredRecords.value);
const selectedRecord = ref<VehicleRecord | null>(null);
const detailImageSrc = ref("");
const detailImageError = ref(false);
const detailImageLoading = ref(false);
const imagePreviewVisible = ref(false);
const authToken = useCookie<string | null>("cartask_auth_token", {sameSite: "lax", path: "/"});
const timeFormat = ref("24");

function normalizedAuthorization(token?: string | null) {
  const value = token?.trim();
  return value ? (/^bearer\s+/i.test(value) ? value : `Bearer ${value}`) : "";
}

function isProtectedFileUrl(url: string) {
  try {
    return new URL(url, window.location.origin).pathname.includes("/api/files/");
  } catch {
    return url.includes("/api/files/");
  }
}

async function loadDetailImage(url: string) {
  detailImageLoading.value = true;
  detailImageError.value = false;
  try {
    if (!isProtectedFileUrl(url)) {
      detailImageSrc.value = url;
      return;
    }
    const response = await fetch(url, {headers: {Authorization: normalizedAuthorization(authToken.value)}});
    if (!response.ok) throw new Error(`image request failed: ${response.status}`);
    const blob = await response.blob();
    detailImageSrc.value = URL.createObjectURL(blob);
  } catch {
    detailImageSrc.value = "";
    detailImageError.value = true;
  } finally {
    detailImageLoading.value = false;
  }
}

function releaseDetailImage() {
  if (detailImageSrc.value.startsWith("blob:")) URL.revokeObjectURL(detailImageSrc.value);
  detailImageSrc.value = "";
}

function openDetails(record: VehicleRecord) {
  releaseDetailImage();
  selectedRecord.value = record;
  detailImageError.value = false;
  imagePreviewVisible.value = false;
  if (record.photo) void loadDetailImage(record.photo);
}

function openImagePreview() {
  imagePreviewVisible.value = true;
}

function closeImagePreview() {
  imagePreviewVisible.value = false;
}

function closeDetails() {
  closeImagePreview();
  releaseDetailImage();
  selectedRecord.value = null;
  detailImageError.value = false;
  detailImageLoading.value = false;
}

function handleDetailsKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (imagePreviewVisible.value) {
    closeImagePreview();
    return;
  }
  if (selectedRecord.value) closeDetails();
}

function passLabel(passType?: string) {
  return passType === "0" ? "自动" : passType || "";
}

function formatTime(raw?: string | null) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(raw || "");
  if (!match) return raw || "";
  const [, year, month, day, hourText, minute, second] = match;
  const hour = Number(hourText);
  const suffix = second ? `${Number(second)}秒` : "";
  if (timeFormat.value === "12") {
    return `${year}年${Number(month)}月${Number(day)}日${hour < 12 ? "上午" : "下午"}${hour % 12 || 12}点${Number(minute)}分${suffix}`;
  }
  return `${year}年${Number(month)}月${Number(day)}日${hour}点${Number(minute)}分${suffix}`;
}

function passClass(passType?: string) {
  return passType === "0" || passType === "自动" || passType === "车牌识别" || passType === "自动放行" ? "tag--green" : passType === "刷卡" || passType === "手动放行" ? "tag--blue" : "tag--red";
}

function search() {
  page.value = 1;
  void loadRecords();
}

function resetFilters() {
  Object.assign(filters, {keyword: "", direction: "", gate: "", passType: ""});
  page.value = 1;
  void loadRecords();
}

function changePage(nextPage: number) {
  page.value = Math.min(Math.max(1, nextPage), totalPages.value);
  void loadRecords();
}

function changePageSize() {
  page.value = 1;
  void loadRecords();
}

async function loadRecords() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<RecordList>("/vehicle-records", {
      keyword: filters.keyword || undefined,
      direction: filters.direction || undefined,
      gate: filters.gate || undefined,
      passType: filters.passType || undefined,
      page: page.value,
      pageSize: pageSize.value
    });
    records.value = result.items || [];
    total.value = result.total || 0;
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "车辆进出记录加载失败";
  } finally {
    loading.value = false;
  }
}

function exportRecords() {
  const rows = [["车牌号码", "车主名称", "方向", "进出时间", "进出场地点", "车辆类型", "放行类型", "记录状态"], ...filteredRecords.value.map((record) => [record.plate || "", record.owner || "", record.direction, formatTime(record.time), record.gate || "", record.vehicleType || "", passLabel(record.passType), record.status || ""])];
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob(["\ufeff" + csv], {type: "text/csv;charset=utf-8"}));
  link.download = "车辆进出记录.csv";
  link.click();
  URL.revokeObjectURL(link.href);
}

watch(filteredRecords, () => {
  if (page.value > totalPages.value) page.value = totalPages.value;
});
watch(timeFormat, (value) => {
  localStorage.setItem("cartask_time_format", value);
});
onMounted(() => {
  if (localStorage.getItem("cartask_time_format") === "12") timeFormat.value = "12";
  loadRecords();
  window.addEventListener("keydown", handleDetailsKeydown);
});
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(loadRecords);
onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleDetailsKeydown);
  releaseDetailImage();
});
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

.button .material-icons-outlined {
  font-size: 16px
}

.table-wrap {
  overflow-x: auto
}

.table {
  border-collapse: collapse;
  font-size: 13px;
  min-width: 1040px;
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
  padding: 9px 16px;
  white-space: nowrap
}

.table tbody tr:hover {
  background: var(--bg)
}

.plate {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: .5px
}

.text-sub {
  color: var(--text-sub) !important
}

.nowrap {
  white-space: nowrap
}

.remark-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis
}

.actions-cell {
  text-align: right !important
}

.detail-action {
  align-items: center;
  background: transparent;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--primary);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 12px;
  gap: 4px;
  height: 30px;
  padding: 0 9px
}

.detail-action:hover {
  background: var(--primary-soft)
}

.detail-action .material-icons-outlined {
  font-size: 16px
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

.detail-mask {
  align-items: center;
  background: rgb(15 23 42 / 46%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 400
}

.detail-modal {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 20px 60px rgb(15 23 42 / 26%);
  max-width: 820px;
  overflow: hidden;
  width: 100%
}

.detail-modal__head {
  align-items: center;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  padding: 18px 22px
}

.detail-modal__head h2 {
  color: var(--text);
  font-size: 20px;
  font-weight: 650;
  margin: 2px 0 0
}

.detail-kicker {
  color: var(--text-mute);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .1em;
  text-transform: uppercase
}

.icon-button {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  height: 32px;
  justify-content: center;
  width: 32px
}

.icon-button:hover {
  background: var(--bg);
  color: var(--text)
}

.icon-button .material-icons-outlined {
  font-size: 20px
}

.detail-modal__body {
  display: grid;
  gap: 24px;
  grid-template-columns:minmax(260px, 1.05fr) minmax(260px, 1fr);
  padding: 22px
}

.detail-photo-panel {
  min-width: 0
}

.detail-photo-frame {
  background: #0f172a;
  border-radius: 6px;
  display: flex;
  min-height: 280px;
  overflow: hidden
}

.detail-photo-trigger {
  appearance: none;
  background: transparent;
  border: 0;
  cursor: zoom-in;
  display: block;
  padding: 0;
  position: relative;
  width: 100%
}

.detail-photo-frame img {
  display: block;
  height: 100%;
  max-height: 420px;
  object-fit: contain;
  width: 100%
}

.detail-photo-zoom {
  align-items: center;
  background: rgb(15 23 42 / 72%);
  border-radius: 50%;
  color: #fff;
  display: flex;
  font-size: 22px;
  height: 38px;
  justify-content: center;
  left: 50%;
  opacity: 0;
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  transition: opacity .16s ease;
  width: 38px
}

.detail-photo-trigger:hover .detail-photo-zoom, .detail-photo-trigger:focus-visible .detail-photo-zoom {
  opacity: 1
}

.detail-photo-trigger:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px
}

.detail-photo-empty {
  align-items: center;
  background: var(--bg);
  border: 1px dashed var(--border-strong);
  border-radius: 6px;
  color: var(--text-mute);
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
  min-height: 280px;
  padding: 24px;
  text-align: center
}

.detail-photo-empty .material-icons-outlined {
  font-size: 40px
}

.detail-photo-empty strong {
  color: var(--text-sub);
  font-size: 13px
}

.detail-photo-empty span:last-child {
  font-size: 11px
}

.detail-spinner {
  animation: detail-spin 1.1s linear infinite
}

.detail-photo-caption {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  font-size: 11px;
  gap: 5px;
  margin-top: 9px
}

.detail-photo-caption .material-icons-outlined {
  font-size: 15px
}

.detail-info-panel {
  min-width: 0
}

.detail-status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 18px
}

.detail-grid {
  display: grid;
  gap: 0 18px;
  grid-template-columns:repeat(2, minmax(0, 1fr));
  margin: 0
}

.detail-grid > div {
  border-bottom: 1px solid var(--border);
  padding: 12px 0
}

.detail-grid__wide {
  grid-column: 1 / -1
}

.detail-grid dt {
  color: var(--text-mute);
  font-size: 11px;
  margin-bottom: 5px
}

.detail-grid dd {
  color: var(--text);
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  overflow-wrap: anywhere
}

.detail-modal__foot {
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  padding: 12px 22px
}

.image-preview-mask {
  align-items: center;
  background: rgb(2 6 23 / 82%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 24px;
  position: fixed;
  z-index: 401
}

.image-preview {
  align-items: center;
  display: flex;
  height: 100%;
  justify-content: center;
  max-width: 1200px;
  position: relative;
  width: 100%
}

.image-preview__image {
  max-height: 100%;
  max-width: 100%;
  object-fit: contain
}

.image-preview__close {
  background: rgb(255 255 255 / 14%);
  color: #fff;
  position: absolute;
  right: 0;
  top: 0
}

.image-preview__close:hover {
  background: rgb(255 255 255 / 24%);
  color: #fff
}

@keyframes detail-spin {
  to {
    transform: rotate(360deg)
  }
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

  .detail-mask {
    padding: 12px
  }

  .detail-modal__body {
    grid-template-columns:1fr;
    padding: 16px
  }

  .detail-photo-frame, .detail-photo-empty {
    min-height: 220px
  }

  .detail-modal__head, .detail-modal__foot {
    padding-left: 16px;
    padding-right: 16px
  }

  .image-preview-mask {
    padding: 16px
  }

  .image-preview__close {
    right: 4px;
    top: 4px
  }
}

.pagination__size {
  align-items: center;
  color: var(--text-sub);
  display: flex;
  font-size: 12px;
  gap: 6px
}

.pagination__size .select {
  font-size: 12px;
  height: 28px;
  padding: 0 4px 0 8px
}
</style>
