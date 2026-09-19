<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">角色管理</h1>
        <p class="page__desc">角色及二维权限矩阵配置</p>
      </div>
      <button v-if="can('role:manage')" class="button button--primary button--sm" type="button" @click="openCreate">
        <span class="material-icons-outlined">add</span>新增角色
      </button>
    </header>

    <section class="card">
      <div v-if="loading" class="state">正在加载角色数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
          <tr>
            <th>角色编号</th>
            <th>角色名称</th>
            <th>权限标识</th>
            <th>数据范围</th>
            <th>用户数</th>
            <th>状态</th>
            <th class="actions-cell">操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="role in visibleRoles" :key="role.id">
            <td>{{ String(role.id).padStart(4, "0") }}</td>
            <td><strong class="role-name">{{ role.name }}</strong></td>
            <td><span class="tag tag--gray">{{ role.code }}</span></td>
            <td>{{ dataScope(role) }}</td>
            <td>{{ userCount(role.id) }}</td>
            <td><span :class="role.status === 1 ? 'tag--green' : 'tag--red'" class="tag">{{
                role.status === 1 ? "正常" : "停用"
              }}</span></td>
            <td class="actions-cell">
              <button v-if="can('role:manage')" class="row-action" title="编辑" type="button" @click="openEdit(role)">
                <span class="material-icons-outlined">edit</span></button>
              <button v-if="can('role:manage')" class="row-action row-action--danger" title="删除" type="button"
                      @click="removeRole(role)"><span class="material-icons-outlined">delete</span></button>
            </td>
          </tr>
          <tr v-if="visibleRoles.length === 0">
            <td class="empty" colspan="7">暂无数据</td>
          </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !errorMessage" class="pagination"><span class="pagination__info">共 {{
          roleTotal
        }} 条</span>
        <button :disabled="page <= 1" type="button" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span>
        </button>
        <button v-for="pageNumber in pageNumbers" :key="pageNumber" :class="{ active: pageNumber === page }"
                type="button" @click="changePage(pageNumber)">{{ pageNumber }}
        </button>
        <button :disabled="page >= totalPages" type="button" @click="changePage(page + 1)"><span
            class="material-icons-outlined">chevron_right</span></button>
      </footer>
    </section>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal modal--lg" @submit.prevent="saveRole">
        <header class="modal__head"><h2 class="modal__title">{{ editingId ? "编辑角色" : "新增角色" }}</h2>
          <button class="icon-button" title="关闭" type="button" @click="editorVisible = false"><span
              class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid"><label class="field"><span class="field__label">角色名称<em>*</em></span><input
              v-model.trim="form.name" class="input" required></label><label class="field"><span class="field__label">权限标识<em>*</em></span><input
              v-model.trim="form.code" class="input" placeholder="如：admin" required></label><label class="field"><span
              class="field__label">状态</span><select v-model.number="form.status" class="select">
            <option :value="1">正常</option>
            <option :value="0">停用</option>
          </select></label><label class="field full"><span class="field__label">备注</span><textarea
              v-model="form.remark" class="textarea"/></label></div>
          <section class="perm-section">
            <div class="perm-section__head">
              <div class="perm-section__title">菜单权限</div>
              <div class="perm-section__actions">
                <button class="button button--ghost button--sm" type="button" @click="setAllExpanded(true)">展开全部
                </button>
                <button class="button button--ghost button--sm" type="button" @click="setAllExpanded(false)">折叠全部
                </button>
                <button class="button button--soft button--sm" type="button" @click="setAllChecked(true)">全选</button>
                <button class="button button--ghost button--sm" type="button" @click="setAllChecked(false)">清空
                </button>
              </div>
            </div>
            <div class="perm-tree">
              <div v-for="group in permissions" :key="group.key" class="perm-group">
                <div class="perm-group__head" @click="toggleGroup(group.key)"><span
                    :class="{ open: expandedGroups.has(group.key) }" class="material-icons-outlined perm-group__toggle">expand_more</span><span
                    class="material-icons-outlined perm-group__icon">folder</span><span
                    class="perm-group__name">{{ group.name }}</span><span
                    class="perm-group__count">{{ groupCheckedCount(group) }}/{{ groupTotalCount(group) }}</span>
                  <button class="button button--ghost button--sm perm-group__all" type="button"
                          @click.stop="setGroupChecked(group, !groupFullyChecked(group))">全选本组
                  </button>
                </div>
                <div :class="{ open: expandedGroups.has(group.key) }" class="perm-group__body">
                  <div v-for="sub in group.children" :key="sub.key" class="perm-sub">
                    <div class="perm-sub__head" @click="toggleSub(sub.key)"><span
                        :class="{ open: expandedSubs.has(sub.key) }" class="material-icons-outlined perm-sub__icon">chevron_right</span><span
                        class="perm-sub__name">{{ sub.name }}</span><span class="perm-sub__count">{{
                        checkedCount(sub)
                      }}/{{ sub.ops.length }}</span>
                      <button class="button button--ghost button--sm" type="button"
                              @click.stop="setSubChecked(sub, !subFullyChecked(sub))">全选
                      </button>
                    </div>
                    <div :class="{ open: expandedSubs.has(sub.key) }" class="perm-sub__ops"><label
                        v-for="operation in sub.ops" :key="operation.code" class="perm-op"><input
                        v-model="selectedPermissions[sub.key]" :value="operation.code" class="checkbox" type="checkbox"><span>{{
                        operation.label
                      }}</span></label></div>
                  </div>
                </div>
              </div>
            </div>
          </section>
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
interface Role {
  id: number;
  name: string;
  code: string;
  sort?: number;
  status: number;
  remark?: string | null;
  users?: number;
  permission_codes?: string[]
}

interface User {
  roleIds?: number[]
}

interface UserList {
  items: User[]
}

interface RoleList {
  items: Role[];
  total: number
}

interface PermissionOperation {
  label: string;
  code: string
}

interface PermissionSub {
  key: string;
  name: string;
  ops: PermissionOperation[]
}

interface PermissionGroup {
  key: string;
  name: string;
  children: PermissionSub[]
}

const permissions: PermissionGroup[] = [
  {
    key: "system",
    name: "系统管理",
    children: [{key: "dashboard", name: "仪表盘", ops: [{label: "查看", code: "dashboard:read"}]}, {
      key: "monitor",
      name: "系统监控",
      ops: [{label: "查看", code: "system-monitor:read"}]
    }, {
      key: "users",
      name: "用户管理",
      ops: [{label: "查看", code: "user:read"}, {label: "新增", code: "user:create"}, {
        label: "编辑",
        code: "user:update"
      }, {label: "停用/删除", code: "user:disable"}, {label: "分配角色", code: "user:role-assign"}]
    }, {
      key: "roles",
      name: "角色管理",
      ops: [{label: "查看", code: "role:read"}, {label: "管理", code: "role:manage"}]
    }, {
      key: "permissions",
      name: "权限管理",
      ops: [{label: "查看", code: "permission:read"}, {label: "管理", code: "permission:manage"}]
    }, {
      key: "depts",
      name: "部门管理",
      ops: [{label: "查看", code: "department:read"}, {label: "管理", code: "department:manage"}]
    }, {
      key: "posts",
      name: "岗位管理",
      ops: [{label: "查看", code: "position:read"}, {label: "管理", code: "position:manage"}]
    }, {key: "backup", name: "数据备份", ops: [{label: "备份", code: "backup:manage"}]}]
  },
  {
    key: "parking",
    name: "车辆信息",
    children: [{
      key: "owners",
      name: "车主信息",
      ops: [{label: "查看", code: "owner:read"}, {label: "管理", code: "owner:manage"}]
    }, {
      key: "spots",
      name: "车位信息",
      ops: [{label: "查看", code: "spot:read"}, {label: "管理", code: "spot:manage"}]
    }, {
      key: "plates",
      name: "车牌信息",
      ops: [{label: "查看", code: "plate:read"}, {label: "管理", code: "plate:manage"}, {
        label: "查看月卡同步",
        code: "plate:sync:read"
      }, {label: "重试月卡同步", code: "plate:sync:retry"}, {label: "执行月卡对账", code: "plate:sync:reconcile"}]
    }, {
      key: "vehicle-inout-requests",
      name: "进出申请",
      ops: [{label: "查看", code: "vehicle-inout-request:read"}, {
        label: "登记",
        code: "vehicle-inout-request:apply"
      }, {label: "审核", code: "vehicle-inout-request:review"}, {label: "下发月卡", code: "vehicle-inout-request:sync"}]
    }]
  },
  {
    key: "access",
    name: "门禁管理",
    children: [{
      key: "devices",
      name: "设备管理",
      ops: [{label: "查看", code: "device:read"}, {label: "管理", code: "device:manage"}]
    }, {
      key: "gate-persons",
      name: "人员信息",
      ops: [{label: "查看", code: "gate-person:read"}, {label: "录入", code: "gate-person:manage"}, {
        label: "审核",
        code: "gate-person:review"
      }, {label: "导出", code: "gate-person:export"}]
    }]
  },
  {
    key: "violations",
    name: "违规管理",
    children: [{
      key: "violations",
      name: "违规管理",
      ops: [{label: "查看", code: "violation:read"}, {label: "管理", code: "violation:manage"}, {
        label: "导出",
        code: "violation:export"
      }]
    }]
  },
  {
    key: "records",
    name: "进出记录",
    children: [{
      key: "person-records",
      name: "人员进出",
      ops: [{label: "查看", code: "person-record:read"}, {label: "导出", code: "person-record:export"}]
    }, {
      key: "vehicle-records",
      name: "车辆进出",
      ops: [{label: "查看", code: "vehicle-record:read"}, {label: "导出", code: "vehicle-record:export"}]
    }]
  },
  {
    key: "logs",
    name: "系统日志",
    children: [{
      key: "logs",
      name: "日志管理",
      ops: [{label: "查看", code: "audit:read"}, {label: "导出", code: "audit:export"}, {
        label: "校验",
        code: "audit:verify"
      }, {label: "清空", code: "audit:delete"}]
    }, {key: "sync-schedule", name: "同步周期", ops: [{label: "修改", code: "sync-schedule:manage"}]}]
  },
];
const http = useHttp();
const {can} = usePermission();
const roles = ref<Role[]>([]);
const roleTotal = ref(0);
const users = ref<User[]>([]);
const loading = ref(true);
const errorMessage = ref("");
const page = ref(1);
const pageSize = 8;
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const formError = ref("");
const form = reactive({name: "", code: "", status: 1, remark: ""});
const expandedGroups = ref(new Set<string>([permissions[0].key]));
const expandedSubs = ref(new Set<string>(permissions[0].children.map((item) => item.key)));
const selectedPermissions = reactive<Record<string, string[]>>({});
const totalPages = computed(() => Math.max(1, Math.ceil(roleTotal.value / pageSize)));
const visibleRoles = computed(() => roles.value);
const pageNumbers = computed(() => Array.from({length: totalPages.value}, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));

/**
 * 数据范围由角色编码决定，不是角色上可配置的字段。
 *
 * 部门范围是按「人」分配的（见用户管理里的部门管理范围），放到角色上会有两个真值来源，
 * 而且改角色会撤销该角色全部持有者的会话。所以这一列是推导出来的只读说明。
 */
function dataScope(role: Role) {
  switch (role.code.toUpperCase()) {
    case "SUPER_ADMIN":
    case "ADMIN":
      return "全部（可切换工作部门）";
    case "DEPT_ADMIN":
      return "指定部门（按用户分配）";
    default:
      return "仅本人";
  }
}

function userCount(id: number) {
  const declared = roles.value.find((role) => role.id === id)?.users;
  return declared ?? (users.value.filter((user) => user.roleIds?.includes(id)).length || "-");
}

function checkedCount(sub: PermissionSub) {
  return selectedPermissions[sub.key]?.length || 0;
}

function subFullyChecked(sub: PermissionSub) {
  return checkedCount(sub) === sub.ops.length;
}

function groupTotalCount(group: PermissionGroup) {
  return group.children.reduce((total, sub) => total + sub.ops.length, 0);
}

function groupCheckedCount(group: PermissionGroup) {
  return group.children.reduce((total, sub) => total + checkedCount(sub), 0);
}

function groupFullyChecked(group: PermissionGroup) {
  return groupCheckedCount(group) === groupTotalCount(group);
}

function setSubChecked(sub: PermissionSub, checked: boolean) {
  selectedPermissions[sub.key] = checked ? sub.ops.map((operation) => operation.code) : [];
}

function setGroupChecked(group: PermissionGroup, checked: boolean) {
  group.children.forEach((sub) => setSubChecked(sub, checked));
}

function setAllChecked(checked: boolean) {
  permissions.forEach((group) => setGroupChecked(group, checked));
}

function toggleGroup(key: string) {
  const next = new Set(expandedGroups.value);
  if (next.has(key)) next.delete(key); else next.add(key);
  expandedGroups.value = next;
}

function toggleSub(key: string) {
  const next = new Set(expandedSubs.value);
  if (next.has(key)) next.delete(key); else next.add(key);
  expandedSubs.value = next;
}

function setAllExpanded(expanded: boolean) {
  expandedGroups.value = new Set(expanded ? permissions.map((group) => group.key) : []);
  expandedSubs.value = new Set(expanded ? permissions.flatMap((group) => group.children.map((sub) => sub.key)) : []);
}

function clearPermissions() {
  permissions.forEach((group) => group.children.forEach((sub) => {
    selectedPermissions[sub.key] = [];
  }));
}

function initializePermissions(codes: string[]) {
  clearPermissions();
  const assigned = new Set(codes);
  permissions.forEach((group) => group.children.forEach((sub) => {
    selectedPermissions[sub.key] = sub.ops.map((operation) => operation.code).filter((code) => assigned.has(code));
  }));
}

function selectedPermissionCodes() {
  return [...new Set(Object.values(selectedPermissions).flat())];
}

async function loadRoles() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<RoleList>("/roles", {page: page.value, pageSize});
    roles.value = result.items || [];
    roleTotal.value = result.total || 0;
    if (page.value > totalPages.value) page.value = totalPages.value;
    try {
      users.value = (await http.get<UserList>("/users", {page: 1, pageSize: 100})).items || [];
    } catch {
      users.value = [];
    }
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "角色数据加载失败";
  } finally {
    loading.value = false;
  }
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  void loadRoles();
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {name: "", code: "", status: 1, remark: ""});
  initializePermissions([]);
  formError.value = "";
  editorVisible.value = true;
}

async function openEdit(role: Role) {
  editingId.value = role.id;
  Object.assign(form, {name: role.name, code: role.code, status: role.status, remark: role.remark || ""});
  initializePermissions([]);
  formError.value = "";
  editorVisible.value = true;
  try {
    const detail = await http.get<Role>(`/roles/${role.id}`);
    initializePermissions(detail.permission_codes || []);
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "权限数据加载失败";
  }
}

async function saveRole() {
  if (!form.name || !form.code) {
    formError.value = "名称和标识不能为空";
    return;
  }
  saving.value = true;
  formError.value = "";
  const payload = {
    name: form.name,
    code: form.code,
    sort: editingId.value ? roles.value.find((role) => role.id === editingId.value)?.sort || 1 : roles.value.length + 1,
    status: form.status,
    remark: form.remark,
    permissions: selectedPermissionCodes()
  };
  try {
    if (editingId.value) await http.put(`/roles/${editingId.value}`, payload, {payloadMode: "json"}); else await http.post("/roles", payload, {payloadMode: "json"});
    editorVisible.value = false;
    await loadRoles();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally {
    saving.value = false;
  }
}

async function removeRole(role: Role) {
  if (!window.confirm(`确认删除“${role.name}”吗？`)) return;
  try {
    await http.delete(`/roles/${role.id}`);
    await loadRoles();
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败";
  }
}

onMounted(loadRoles);
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
  margin: 2px 0 0;
}

.card {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
}

.state, .empty {
  color: var(--text-mute);
  padding: 48px;
  text-align: center;
}

.state--error, .form-error {
  color: var(--red);
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

.button--sm {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
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

.button:disabled {
  cursor: wait;
  opacity: .6;
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
  min-width: 760px;
  width: 100%;
}

.table th {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  color: var(--text-mute);
  font-size: 12px;
  font-weight: 500;
  padding: 10px 16px;
  text-align: left;
  white-space: nowrap;
}

.table td {
  border-bottom: 1px solid var(--border);
  color: var(--text);
  padding: 11px 16px;
  white-space: nowrap;
}

.table tbody tr:hover {
  background: var(--bg);
}

.role-name {
  color: var(--primary);
}

.actions-cell {
  text-align: right !important;
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

.tag--gray {
  background: var(--neutral-soft);
  color: var(--text-sub);
}

.tag--green {
  background: var(--success-soft);
  color: var(--success);
}

.tag--red {
  background: var(--danger-soft);
  color: var(--danger);
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

.row-action:hover {
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

.pagination {
  align-items: center;
  border-top: 1px solid var(--border);
  color: var(--text-sub);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
  padding: 12px 16px;
}

.pagination__info {
  margin-right: auto;
  font-size: 12px;
}

.pagination button {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  min-width: 28px;
}

.pagination button:hover:not(:disabled), .pagination button.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--on-solid);
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
  background: rgb(0 0 0 / 40%);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 20px;
  position: fixed;
  z-index: 300;
}

.modal {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 20%);
  max-height: calc(100vh - 40px);
  overflow: hidden;
  width: 100%;
}

.modal--lg {
  max-width: 900px;
}

.modal__head, .modal__foot {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 14px 20px;
}

.modal__head {
  border-bottom: 1px solid var(--border);
}

.modal__title {
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.modal__body {
  max-height: calc(100vh - 150px);
  overflow-y: auto;
  padding: 20px;
}

.modal__foot {
  border-top: 1px solid var(--border);
  gap: 8px;
  justify-content: flex-end;
}

.form-grid {
  display: grid;
  gap: 0 16px;
  grid-template-columns: 1fr 1fr;
}

.field {
  margin-bottom: 14px;
}

.field__label {
  color: var(--text-sub);
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 5px;
}

.field__label em {
  color: var(--red);
  font-style: normal;
  margin-left: 2px;
}

.input, .select, .textarea {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-sizing: border-box;
  color: var(--text);
  font: inherit;
  height: 34px;
  outline: none;
  padding: 0 10px;
  width: 100%;
}

.textarea {
  height: auto;
  min-height: 70px;
  padding: 8px 10px;
  resize: vertical;
}

.input:focus, .select:focus, .textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-soft);
}

.full {
  grid-column: 1 / -1;
}

.perm-section {
  border-top: 1px dashed var(--border-strong);
  margin-top: 8px;
  padding-top: 16px;
}

.perm-section__head {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.perm-section__title {
  align-items: center;
  color: var(--text);
  display: flex;
  font-size: 14px;
  font-weight: 600;
  gap: 6px;
}

.perm-section__title::before {
  background: var(--primary);
  border-radius: 2px;
  content: "";
  height: 14px;
  width: 3px;
}

.perm-section__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.perm-tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 4px;
}

.perm-group {
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.perm-group__head {
  align-items: center;
  background: var(--bg);
  cursor: pointer;
  display: flex;
  gap: 6px;
  padding: 9px 12px;
  user-select: none;
}

.perm-group__head:hover {
  background: var(--primary-soft);
}

.perm-group__toggle {
  color: var(--text-mute);
  font-size: 20px;
  transition: transform var(--tr);
}

.perm-group__toggle:not(.open) {
  transform: rotate(-90deg);
}

.perm-group__icon {
  color: var(--text-mute);
  font-size: 18px;
}

.perm-group__name {
  font-size: 13px;
  font-weight: 600;
}

.perm-group__count, .perm-sub__count {
  background: var(--border);
  border-radius: 10px;
  color: var(--text-mute);
  font-size: 11px;
  padding: 1px 7px;
}

.perm-group__all {
  margin-left: auto;
}

.perm-group__body {
  display: none;
  padding: 4px 12px 8px 32px;
}

.perm-group__body.open {
  display: block;
}

.perm-sub {
  border-bottom: 1px solid var(--border);
  padding: 8px 0;
}

.perm-sub:last-child {
  border-bottom: none;
}

.perm-sub__head {
  align-items: center;
  cursor: pointer;
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}

.perm-sub__icon {
  color: var(--text-mute);
  font-size: 16px;
  transition: transform var(--tr);
}

.perm-sub__icon.open {
  transform: rotate(90deg);
}

.perm-sub__name {
  font-size: 13px;
  font-weight: 500;
}

.perm-sub__count {
  border-radius: 8px;
  padding: 1px 6px;
}

.perm-sub__head .button {
  font-size: 11px;
  height: 22px;
  margin-left: auto;
  padding: 0 6px;
}

.perm-sub__ops {
  display: none;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 22px;
}

.perm-sub__ops.open {
  display: flex;
}

.perm-op {
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-sub);
  cursor: pointer;
  display: inline-flex;
  font-size: 12px;
  gap: 4px;
  padding: 2px 7px;
}

.perm-op:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.perm-op input {
  margin: 0;
}

.checkbox {
  accent-color: var(--primary);
  height: 15px;
  width: 15px;
}

.form-error {
  margin: 10px 0 0;
}

.button--soft:hover {
  background: var(--primary-soft);
}

@media (max-width: 700px) {
  .page {
    padding: 16px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .full {
    grid-column: auto;
  }

  .modal-mask {
    padding: 12px;
  }

  .modal__body {
    padding: 16px;
  }

  .perm-group__body {
    padding-left: 16px;
  }
}
</style>
