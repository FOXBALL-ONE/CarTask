<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">数据导入导出</h1>
        <p class="page__desc">使用 Excel 批量维护用户、车辆、设备和门禁人员数据</p>
      </div>
      <button v-if="canExportAll" class="button button--soft page__export-all" type="button" :disabled="!!busy" @click="downloadAll">
        <span class="material-icons-outlined">download_for_offline</span>{{ busy === "all-export" ? "导出中..." : "导出全部数据" }}
      </button>
    </header>

    <section class="notice-panel transfer-all">
      <div class="notice-panel__title">关联资料整批导入</div>
      <p>样表包含部门、岗位、用户、车主、车位、车牌、设备和门禁人员。用编码关联新数据，任一错误整批回滚。</p>
      <div class="transfer-card__actions">
        <button v-if="canUseAllSheets" class="button button--ghost" type="button" :disabled="!!busy" @click="download('all', 'template')">
          <span class="material-icons-outlined">description</span>{{ busy === 'all-template' ? '下载中...' : '下载全部导入样表' }}
        </button>
        <label v-if="canUseAllSheets" class="upload-button" :class="{ disabled: !!busy }">
          <span class="material-icons-outlined">upload_file</span>{{ busy === 'all-import' ? '导入中...' : '导入全部数据' }}
          <input type="file" accept=".xlsx,.xls" :disabled="!!busy" @change="(event) => importFile('all', event)">
        </label>
      </div>
      <p>样表中的示例行会实际导入，请先修改或删除。无需导入的工作表保留表头即可；全部导出的文件不能直接作为导入样表。</p>
    </section>

    <section class="transfer-grid">
      <article v-for="resource in visibleResources" :key="resource.key" class="transfer-card">
        <div class="transfer-card__icon"><span class="material-icons-outlined">{{ resource.icon }}</span></div>
        <div class="transfer-card__body">
          <h2>{{ resource.label }}</h2>
          <p>{{ resource.description }}</p>
          <div class="transfer-card__actions">
            <button class="button button--ghost" type="button" :disabled="!!busy" @click="download(resource.key, 'template')">
              <span class="material-icons-outlined">description</span>{{ busy === `${resource.key}-template` ? "下载中..." : "下载模板" }}
            </button>
            <button class="button button--soft" type="button" :disabled="!!busy" @click="download(resource.key, 'export')">
              <span class="material-icons-outlined">download</span>{{ busy === `${resource.key}-export` ? "导出中..." : "导出全部" }}
            </button>
          </div>
          <label v-if="canHandle(resource.key, 'manage')" class="upload-button" :class="{ disabled: !!busy }">
            <span class="material-icons-outlined">upload_file</span>
            <span>{{ busy === `${resource.key}-import` ? "导入中..." : "上传 Excel 导入" }}</span>
            <input type="file" accept=".xlsx,.xls" :disabled="!!busy" @change="(event) => importFile(resource.key, event)">
          </label>
        </div>
      </article>
    </section>

    <section class="notice-panel">
      <div class="notice-panel__title"><span class="material-icons-outlined">info</span>导入说明</div>
      <ul>
        <li>请先下载对应模板，保持表头名称不变后再填写数据。</li>
        <li>用户导入的密码会按系统安全策略加密保存；账号和邮箱不能重复。</li>
        <li>用户通过部门编码、岗位编码关联，车牌通过车主卡号关联；单表导入也支持已有数据库 ID。车主、车位、车牌状态填写 0 或 1。</li>
        <li>进出记录、审批申请等过程数据不开放导入，避免破坏系统流水和审计完整性。</li>
      </ul>
    </section>

    <p v-if="message" role="status" class="feedback" :class="{ error: feedbackType === 'error' }">{{ message }}</p>
  </section>
</template>

<script setup lang="ts">
type ResourceKey = "users" | "positions" | "owners" | "spots" | "plates" | "devices" | "gate-persons";
/** "all" 是整批接口（/excel/all/*），不是 resources 里的一张卡片。 */
type TransferResource = "all" | ResourceKey;
const resources: { key: ResourceKey; label: string; icon: string; description: string }[] = [
  { key: "users", label: "用户数据", icon: "group", description: "批量创建系统用户，包含账号、组织归属和状态。" },
  { key: "positions", label: "岗位数据", icon: "badge", description: "批量维护岗位名称、编码、排序和启用状态。" },
  { key: "owners", label: "车主数据", icon: "person", description: "批量维护车主卡号、联系方式、车位及车牌数量。" },
  { key: "spots", label: "车位数据", icon: "local_parking", description: "批量维护车位编号、区域、类型和占用状态。" },
  { key: "plates", label: "车牌数据", icon: "pin_drop", description: "批量登记车牌，并关联系统内已有车主。" },
  { key: "devices", label: "设备数据", icon: "router", description: "批量维护门禁、摄像头等接入设备信息。" },
  { key: "gate-persons", label: "门禁人员", icon: "badge", description: "批量登记门禁人员基础信息，导入后可继续审批同步。" },
];
/**
 * 与后端 ExcelController 的 @PreAuthorize 一一对应：模板与导出要资源的 :read，导入要 :manage。
 *
 * 两边口径必须一致，否则会出现「按钮点得动、请求被 403 拒绝」；前端这里只是体验层，真正的
 * 拦截在服务端。
 */
const resourcePermissions: Record<ResourceKey, { read: string; manage: string }> = {
  users: { read: "user:read", manage: "user:create" },
  positions: { read: "position:read", manage: "position:manage" },
  owners: { read: "owner:read", manage: "owner:manage" },
  spots: { read: "spot:read", manage: "spot:manage" },
  plates: { read: "plate:read", manage: "plate:manage" },
  devices: { read: "device:read", manage: "device:manage" },
  "gate-persons": { read: "gate-person:read", manage: "gate-person:manage" },
};
/** 这些接口后端都要求三种管理角色之一，普通用户即使有读权限也不该看到这里的操作入口。 */
const adminRoles = ["SUPER_ADMIN", "ADMIN", "DEPT_ADMIN"];
const { can } = usePermission();
const authStore = useAuthStore();
const busy = ref("");
const message = ref("");
const feedbackType = ref<"success" | "error">("success");
const runtimeConfig = useRuntimeConfig();
const token = useCookie<string | null>("cartask_auth_token");

const isAdminRole = computed(() => {
  const role = authStore.user?.role;
  return typeof role === "string" && adminRoles.includes(role);
});

/** 单个资源是否有对应权限。导入按钮用 manage，卡片本身用 read。 */
function canHandle(resource: ResourceKey, action: "read" | "manage") {
  return isAdminRole.value && can(resourcePermissions[resource][action]);
}

/** 与 canAny 的 ANY 语义相对：整批接口要求的是一整组权限全都要有。 */
function canAll(permissions: string[]) {
  return isAdminRole.value && permissions.every((permission) => can(permission));
}

// /excel/all/export 要整组读权限；/excel/all/template 与 /excel/all/import 还要 department:manage。
const canExportAll = computed(() => canAll(Object.values(resourcePermissions).map((item) => item.read)));
const canUseAllSheets = computed(() => canAll([
  "department:manage",
  ...Object.values(resourcePermissions).map((item) => item.manage),
]));

// 没有读权限的资源整张卡片隐藏：连模板都下载不了，摆在那里只会让人点了才发现被拒。
const visibleResources = computed(() => resources.filter((resource) => canHandle(resource.key, "read")));

function endpoint(resource: TransferResource, action: "template" | "export" | "import") {
  const base = String(runtimeConfig.public.baseUrl || "http://127.0.0.1:8080/api").replace(/\/$/, "");
  return `${base}/excel/${resource}/${action}`;
}

function authorization() {
  return token.value ? { Authorization: /^Bearer\s/i.test(token.value) ? token.value : `Bearer ${token.value}` } : {};
}

async function download(resource: TransferResource, action: "template" | "export") {
  busy.value = `${resource}-${action}`;
  message.value = "";
  try {
    const response = await fetch(endpoint(resource, action), { headers: authorization() });
    if (!response.ok) throw new Error(`下载失败（${response.status}）`);
    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") || "";
    const filename = decodeURIComponent(disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || `${resource}-${action}.xlsx`);
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
    showMessage(action === "export" ? "导出完成" : "模板已下载");
  } catch (error) {
    showMessage(error instanceof Error ? error.message : "下载失败", "error");
  } finally {
    busy.value = "";
  }
}

async function downloadAll() {
  busy.value = "all-export";
  message.value = "";
  try {
    const response = await fetch(`${String(runtimeConfig.public.baseUrl || "http://127.0.0.1:8080/api").replace(/\/$/, "")}/excel/all/export`, { headers: authorization() });
    if (!response.ok) throw new Error(`下载失败（${response.status}）`);
    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") || "";
    const filename = decodeURIComponent(disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || "全部数据.xlsx");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
    showMessage("全部数据导出完成");
  } catch (error) {
    showMessage(error instanceof Error ? error.message : "下载失败", "error");
  } finally {
    busy.value = "";
  }
}

async function importFile(resource: TransferResource, event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  busy.value = `${resource}-import`;
  message.value = "";
  try {
    const formData = new FormData();
    formData.append("file", file);
    const response = await fetch(endpoint(resource, "import"), { method: "POST", headers: authorization(), body: formData });
    const body = await response.json().catch(() => ({})) as { message?: string; data?: { count?: number; counts?: Record<string, number> } };
    if (!response.ok) throw new Error(body.message || `导入失败（${response.status}）`);
    const labels: Record<string, string> = { departments: "部门", ...Object.fromEntries(resources.map((item) => [item.key, item.label])) };
    const detail = resource === "all" ? Object.entries(body.data?.counts || {}).map(([key, count]) => `${labels[key] || key} ${count} 条`).join("，") : "";
    showMessage(`导入完成，共处理 ${body.data?.count ?? 0} 条数据${detail ? "（" + detail + "）" : ""}`);
  } catch (error) {
    showMessage(error instanceof Error ? error.message : "导入失败", "error");
  } finally {
    input.value = "";
    busy.value = "";
  }
}

function showMessage(text: string, type: "success" | "error" = "success") {
  message.value = text;
  feedbackType.value = type;
}
</script>

<style scoped>
.transfer-all { margin-bottom: 18px; }
.transfer-all .upload-button { margin-top: 0; }
.page { min-height: 100%; padding: 24px; }
.page__header { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 20px; }
.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }
.page__desc { color: var(--text-sub); margin: 4px 0 0; }
.transfer-grid { display: grid; gap: 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.transfer-card { align-items: flex-start; background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; display: flex; gap: 16px; padding: 22px; }
.transfer-card__icon { align-items: center; background: var(--primary-soft); border-radius: 8px; color: var(--primary); display: flex; flex-shrink: 0; height: 42px; justify-content: center; width: 42px; }
.transfer-card__icon .material-icons-outlined { font-size: 22px; }
.transfer-card__body { min-width: 0; }
.transfer-card h2 { color: var(--text); font-size: 15px; margin: 0; }
.transfer-card p { color: var(--text-sub); line-height: 1.6; margin: 6px 0 16px; }
.transfer-card__actions { display: flex; flex-wrap: wrap; gap: 8px; }
.button, .upload-button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 32px; justify-content: center; padding: 0 11px; white-space: nowrap; }
.button:disabled, .upload-button.disabled { cursor: wait; opacity: .6; }
.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }
.button--soft { background: var(--primary-soft); color: var(--primary); }
.button .material-icons-outlined, .upload-button .material-icons-outlined { font-size: 16px; }
.upload-button { background: var(--primary); color: #fff; margin-top: 10px; position: relative; }
.upload-button input { cursor: pointer; inset: 0; opacity: 0; position: absolute; }
.notice-panel { background: var(--bg); border: 1px solid var(--border); border-radius: 8px; color: var(--text-sub); margin-top: 18px; padding: 16px 18px; }
.notice-panel__title { align-items: center; color: var(--text); display: flex; font-weight: 600; gap: 6px; }
.notice-panel__title .material-icons-outlined { color: var(--primary); font-size: 18px; }
.notice-panel ul { line-height: 1.8; margin: 8px 0 0; padding-left: 20px; }
.feedback { color: #059669; margin: 14px 0 0; }
.feedback.error { color: var(--red); }
@media (max-width: 700px) { .page { padding: 16px; }.transfer-grid { grid-template-columns: 1fr; }.transfer-card { padding: 18px; } }
@media (max-width: 700px) { .page__header { flex-direction: column; gap: 12px; }.page__export-all { width: 100%; } }
</style>
