<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">用户管理</h1>
        <p class="page__desc">维护系统用户账号信息</p>
      </div>
      <div class="page__actions">
        <button class="button button--ghost" type="button" @click="exportUsers">
          <span class="material-icons-outlined">download</span>导出
        </button>
        <button v-if="can('user:create')" class="button button--primary" type="button" @click="openCreate">
          <span class="material-icons-outlined">add</span>新增用户
        </button>
      </div>
    </header>

    <div class="user-layout">
      <aside class="panel department-panel">
        <div class="panel__head department-panel__head">
          <span class="material-icons-outlined" aria-hidden="true">account_tree</span>
          <h2>部门列表</h2>
          <span class="panel__count">共 {{ departments.length }} 个</span>
        </div>
        <div class="department-tree">
          <div class="department-row" :class="{ active: selectedDepartment === 0 }">
            <span class="department-row__spacer" aria-hidden="true" />
            <button class="department-row__select" type="button" @click="selectDepartment(0)">
              <span class="material-icons-outlined" aria-hidden="true">apartment</span>
              <span class="department-name">全部部门</span>
            </button>
          </div>
          <div class="department-tree__sep" aria-hidden="true" />
          <template v-for="department in departmentRoots" :key="department.id">
            <department-node
              :department="department"
              :children="departmentChildren"
              :level="0"
              :selected="selectedDepartment"
              :expanded="expandedDepartments"
              @select="selectDepartment"
              @toggle="toggleDepartment"
            />
          </template>
          <p v-if="!loading && departmentRoots.length === 0" class="department-empty">暂无部门</p>
        </div>
      </aside>

      <section class="panel user-panel">
        <div class="filter-bar">
          <input v-model="keyword" class="input" type="search" placeholder="账号 / 用户名 / 手机号" @keyup.enter="search">
          <select v-model="status" class="select" aria-label="用户状态">
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="0">停用</option>
          </select>
          <div class="filter-actions">
            <button class="button button--soft" type="button" @click="search"><span class="material-icons-outlined">search</span>搜索</button>
            <button class="button button--ghost" type="button" @click="resetFilters"><span class="material-icons-outlined">restart_alt</span>重置</button>
          </div>
        </div>

        <div v-if="loading" class="state">正在加载用户数据...</div>
        <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th class="checkbox-cell"><input v-model="allChecked" class="checkbox" type="checkbox" aria-label="选择全部用户"></th>
                <th>编号</th><th>账号</th><th>用户名</th><th>部门</th><th>职务</th><th>手机号</th><th>角色</th><th>状态</th><th class="actions-cell">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td class="checkbox-cell"><input v-model="selectedIds" class="checkbox" type="checkbox" :value="user.id" :aria-label="`选择${user.name || user.username}`"></td>
                <td>{{ String(user.id).padStart(4, "0") }}</td>
                <td><strong class="username">{{ user.username }}</strong></td>
                <td>{{ user.name || "-" }}</td>
                <td>{{ departmentName(user.deptId) }}</td>
                <td>{{ user.jobTitle || "-" }}</td>
                <td>{{ user.phone || "-" }}</td>
                <td><span class="role-tag">{{ roleName(user) }}</span></td>
                <td>
                  <button v-if="can('user:disable')" class="status-switch" :class="{ enabled: user.status === 1 }" type="button" :aria-label="user.status === 1 ? '停用用户' : '启用用户'" :aria-pressed="user.status === 1" @click="toggleStatus(user)">
                    <span />
                  </button>
                </td>
                <td class="actions-cell">
                  <button v-if="can('user:update')" class="row-action" type="button" title="编辑" @click="openEdit(user)"><span class="material-icons-outlined">edit</span></button>
                  <button v-if="can('user:disable')" class="row-action row-action--danger" type="button" title="删除" @click="removeUser(user)"><span class="material-icons-outlined">delete</span></button>
                </td>
              </tr>
              <tr v-if="users.length === 0"><td class="empty" colspan="10">暂无数据</td></tr>
            </tbody>
          </table>
        </div>
        <footer v-if="!loading && !errorMessage" class="pagination">
          <span>共 {{ total }} 条</span>
          <label class="pagination__size">每页<select v-model.number="pageSize" class="select" aria-label="每页展示条数" @change="changePageSize">
            <option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条</option>
          </select></label>
          <button type="button" :disabled="page <= 1" @click="changePage(page - 1)"><span class="material-icons-outlined">chevron_left</span></button>
          <button v-for="pageNumber in pageNumbers" :key="pageNumber" type="button" :class="{ active: pageNumber === page }" @click="changePage(pageNumber)">{{ pageNumber }}</button>
          <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)"><span class="material-icons-outlined">chevron_right</span></button>
        </footer>
      </section>
    </div>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="saveUser">
        <header class="modal__head"><h2>{{ editingId ? "编辑用户" : "新增用户" }}</h2><button type="button" class="icon-button" title="关闭" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button></header>
        <div class="modal__body">
          <div class="form-grid">
            <label class="field"><span>账号 <em>*</em></span><input v-model.trim="form.username" class="input" required placeholder="请输入登录账号"></label>
            <label class="field"><span>用户名 <em>*</em></span><input v-model.trim="form.name" class="input" required placeholder="请输入用户名"></label>
            <label v-if="!editingId" class="field"><span>初始密码 <em>*</em></span><input v-model="form.password" class="input" type="password" required placeholder="请输入初始密码"></label>
            <label class="field"><span>部门 <em>*</em></span><select v-model="form.deptId" class="select" required><option :value="null">请选择部门</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
            <label class="field"><span>职务</span><input v-model.trim="form.jobTitle" class="input" maxlength="128" placeholder="请输入职务"></label>
            <label class="field"><span>角色 <em>*</em></span><select v-model="form.roleId" class="select" required :disabled="editingId !== null && !canManageScope" :title="editingId !== null && !canManageScope ? '只有超级管理员可以分配角色' : ''"><option :value="null">请选择角色</option><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select></label>
            <label class="field"><span>手机号 <em>*</em></span><input v-model.trim="form.phone" class="input" required placeholder="请输入手机号"></label>
            <label class="field"><span>状态</span><select v-model.number="form.status" class="select" :disabled="editingId !== null && !canDisableUser" :title="editingId !== null && !canDisableUser ? '当前角色不能启停账号' : ''"><option :value="1">正常</option><option :value="0">停用</option></select></label>
          </div>
          <div v-if="editingId && canManageScope" class="field field--full managed-scope">
            <span>部门管理范围</span>
            <DepartmentTreePicker v-model="managedDepartments" :departments="departments" />
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer class="modal__foot"><button class="button button--ghost" type="button" @click="editorVisible = false">取消</button><button class="button button--primary" type="submit" :disabled="saving">{{ saving ? "保存中..." : "保存" }}</button></footer>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
interface User {
  id: number;
  username: string;
  name?: string | null;
  deptId?: number | null;
  jobTitle?: string | null;
  phone?: string | null;
  roleIds?: number[];
  status: number;
}
interface Department { id: number; name: string; parent?: number | null }
interface Role { id: number; name: string; code?: string }
interface RoleList { items: Role[] }
interface UserList { items: User[]; total: number; page: number; pageSize: number }
interface DepartmentNodeProps { department: Department; children: Department[]; level: number; selected: number; expanded: number[] }

const DepartmentNode = defineComponent({
  name: "DepartmentNode",
  props: {
    department: { type: Object as PropType<Department>, required: true },
    children: { type: Array as PropType<Department[]>, required: true },
    level: { type: Number, required: true },
    selected: { type: Number, required: true },
    expanded: { type: Array as PropType<number[]>, required: true },
  },
  emits: ["select", "toggle"],
  setup(props: DepartmentNodeProps, { emit }) {
    const subDepartments = computed(() => props.children.filter((item) => (item.parent ?? 0) === props.department.id));
    const isExpanded = computed(() => props.expanded.includes(props.department.id));
    return () => {
      const hasChildren = subDepartments.value.length > 0;
      return h("div", [
        h("div", {
          class: ["department-row", {
            active: props.selected === props.department.id,
            "department-row--group": hasChildren,
            expanded: isExpanded.value,
          }],
          // 层级交给 CSS：缩进和左侧导轨都由 --dept-level 算出来，窄屏改成横向排列时也不用重新计算。
          style: { "--dept-level": String(props.level) },
        }, [
          hasChildren
            ? h("button", {
              class: "department-row__toggle",
              type: "button",
              "aria-expanded": String(isExpanded.value),
              "aria-label": `${isExpanded.value ? "收起" : "展开"}${props.department.name}`,
              onClick: () => emit("toggle", props.department.id),
            }, [h("span", { class: "material-icons-outlined" }, "chevron_right")])
            : h("span", { class: "department-row__spacer", "aria-hidden": "true" }),
          h("button", {
            class: "department-row__select",
            type: "button",
            onClick: () => emit("select", props.department.id),
          }, [
            h("span", { class: "material-icons-outlined", "aria-hidden": "true" }, hasChildren ? "folder" : "badge"),
            h("span", { class: "department-name", title: props.department.name }, props.department.name),
          ]),
        ]),
        // 收起时直接卸载子树，展开后各层回到默认展开状态
        isExpanded.value && hasChildren
          ? h("div", subDepartments.value.map((child) => h(DepartmentNode, {
            department: child,
            children: props.children,
            level: props.level + 1,
            selected: props.selected,
            expanded: props.expanded,
            onSelect: (id: number) => emit("select", id),
            onToggle: (id: number) => emit("toggle", id),
          })))
          : null,
      ]);
    };
  },
});

const http = useHttp();
const { can } = usePermission();
/**
 * 只有超级管理员能读写别人的部门管理范围。
 *
 * 部门管理与平台管理没有 user:role-assign，调 /managed-departments 会 403；保存时若照旧发这一次
 * 请求，整个编辑都会失败——它们本来能改的姓名、职务、手机号也跟着改不了。
 */
const canManageScope = computed(() => can("user:role-assign"));
/** 启停账号要 user:disable；部门管理没有这一项。新建时状态只是初始值，不受这条约束。 */
const canDisableUser = computed(() => can("user:disable"));
const keyword = ref("");
const status = ref("");
const selectedDepartment = ref(0);
/** 处于展开状态的部门 id；折叠状态只影响树的显示，不影响右侧列表的筛选结果。 */
const expandedDepartments = ref<number[]>([]);
const page = ref(1);
const pageSizes = [10, 20, 50, 100];
const pageSize = ref(20);
const users = ref<User[]>([]);
const total = ref(0);
const departments = ref<Department[]>([]);
const roles = ref<Role[]>([]);
const allUsers = ref<User[]>([]);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const originalRoleId = ref<number | null>(null);
const originalStatus = ref(1);
const selectedIds = ref<number[]>([]);
const form = reactive({ username: "", name: "", password: "", deptId: null as number | null, jobTitle: "", roleId: null as number | null, phone: "", status: 1 });
/** 该用户的部门管理范围；仅编辑已有用户时可配置（接口按用户 ID 整体替换）。 */
const managedDepartments = ref<ManagedDepartment[]>([]);

const departmentRoots = computed(() => departments.value.filter((department) => (department.parent ?? 0) === 0));
const departmentChildren = computed(() => departments.value);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, page.value - 3), page.value + 2));
const allChecked = computed({ get: () => users.value.length > 0 && users.value.every((user) => selectedIds.value.includes(user.id)), set: (checked: boolean) => { selectedIds.value = checked ? users.value.map((user) => user.id) : []; } });

function departmentName(id?: number | null) { return departments.value.find((department) => department.id === id)?.name || "-"; }
function roleName(user: User) { return roles.value.filter((role) => user.roleIds?.includes(role.id)).map((role) => role.name).join("、") || "未分配"; }

async function loadReferenceData() {
  const [departmentResult, roleResult] = await Promise.all([http.get<Department[]>("/depts"), http.get<RoleList>("/roles", { page: 1, pageSize: 100 })]);
  departments.value = departmentResult || [];
  roles.value = roleResult.items || [];
  // 有下级的部门默认展开，进页面就能看到完整的上下级结构
  expandedDepartments.value = departments.value
    .filter((department) => departments.value.some((child) => (child.parent ?? 0) === department.id))
    .map((department) => department.id);
}

async function loadUsers() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<UserList>("/users", { keyword: keyword.value || undefined, status: status.value || undefined, department_id: selectedDepartment.value || undefined, page: page.value, pageSize: pageSize.value });
    users.value = result.items || [];
    total.value = result.total || 0;
    selectedIds.value = [];
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "用户数据加载失败";
  } finally {
    loading.value = false;
  }
}

function applyUserView() {
  const filtered = selectedDepartment.value === 0
    ? allUsers.value
    : allUsers.value.filter((user) => user.deptId === selectedDepartment.value);
  total.value = filtered.length;
  const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value));
  if (page.value > maxPage) page.value = maxPage;
  const start = (page.value - 1) * pageSize.value;
  users.value = filtered.slice(start, start + pageSize.value);
  selectedIds.value = [];
}

async function initialize() {
  try { await loadReferenceData(); } catch { /* The list request reports the visible failure state. */ }
  await loadUsers();
}
function search() { page.value = 1; void loadUsers(); }
function resetFilters() { keyword.value = ""; status.value = ""; selectedDepartment.value = 0; page.value = 1; void loadUsers(); }
function selectDepartment(id: number) { selectedDepartment.value = id; page.value = 1; void loadUsers(); }
function toggleDepartment(id: number) {
  expandedDepartments.value = expandedDepartments.value.includes(id)
    ? expandedDepartments.value.filter((item) => item !== id)
    : [...expandedDepartments.value, id];
}
function changePage(nextPage: number) { if (nextPage < 1 || nextPage > totalPages.value) return; page.value = nextPage; void loadUsers(); }
function changePageSize() { page.value = 1; void loadUsers(); }
function openCreate() { editingId.value = null; originalRoleId.value = null; originalStatus.value = 1; managedDepartments.value = []; Object.assign(form, { username: "", name: "", password: "", deptId: departments.value[0]?.id ?? null, jobTitle: "", roleId: roles.value[0]?.id ?? null, phone: "", status: 1 }); formError.value = ""; editorVisible.value = true; }
function openEdit(user: User) {
  managedDepartments.value = [];
  if (canManageScope.value) {
    void http.get<{ departments: ManagedDepartment[] }>(`/users/${user.id}/managed-departments`)
      .then((result) => { managedDepartments.value = result.departments || []; })
      .catch(() => { managedDepartments.value = []; });
  }
  editingId.value = user.id; originalRoleId.value = user.roleIds?.[0] ?? null; originalStatus.value = user.status; Object.assign(form, { username: user.username, name: user.name || "", password: "", deptId: user.deptId ?? null, jobTitle: user.jobTitle || "", roleId: user.roleIds?.[0] ?? null, phone: user.phone || "", status: user.status }); formError.value = ""; editorVisible.value = true; }

async function saveUser() {
  saving.value = true;
  formError.value = "";
  if (!form.username || !form.name || (!editingId.value && !form.password) || !form.deptId || !form.roleId || !form.phone) {
    formError.value = "请完整填写账号、用户名、密码、部门、角色和手机号";
    saving.value = false;
    return;
  }
  const payload = { username: form.username, name: form.name, password: form.password || undefined, deptId: form.deptId, jobTitle: form.jobTitle, phone: form.phone || undefined, status: form.status, roleIds: form.roleId ? [form.roleId] : [] };
  try {
    if (editingId.value) {
      // 改手机号只需要 user:update，其余三项各有自己的权限：无权限时控件已置灰，
      // 这里再挡一次，避免一个可选的越权请求失败把整次编辑一起带崩。
      await http.put(`/users/${editingId.value}`, { ...payload, roleIds: undefined, status: undefined }, { payloadMode: "json" });
      const role = roles.value.find((item) => item.id === form.roleId);
      if (canManageScope.value && form.roleId !== originalRoleId.value && role?.code) await http.put(`/users/${editingId.value}/role`, undefined, { params: { role: role.code } });
      if (canDisableUser.value && form.status !== originalStatus.value) await http.put(`/users/${editingId.value}/account-status`, undefined, { params: { enabled: form.status === 1, status: form.status === 1 ? "Activity" : "BANNED" } });
      if (canManageScope.value) await http.put(`/users/${editingId.value}/managed-departments`, { departments: managedDepartments.value }, { payloadMode: "json" });
    }
    else await http.post("/users", { ...payload, email: `${form.username}@local.invalid` }, { payloadMode: "json" });
    editorVisible.value = false;
    await loadUsers();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally { saving.value = false; }
}

async function toggleStatus(user: User) {
  const nextStatus = user.status === 1 ? 0 : 1;
  try {
    await http.put(`/users/${user.id}/account-status`, {
      enabled: nextStatus === 1,
      status: nextStatus === 1 ? "Activity" : "BANNED",
    });
    user.status = nextStatus;
  }
  catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "状态更新失败"; }
}
async function removeUser(user: User) {
  if (!window.confirm(`确认删除“${user.name || user.username}”吗？`)) return;
  try { await http.delete(`/users/${user.id}`); await loadUsers(); }
  catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败"; }
}
function exportUsers() {
  const rows = [["编号", "账号", "用户名", "部门", "职务", "手机号", "角色", "状态"], ...users.value.map((user) => [user.id, user.username, user.name || "", departmentName(user.deptId), user.jobTitle || "", user.phone || "", roleName(user), user.status === 1 ? "正常" : "停用"])]
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n");
  const link = document.createElement("a"); link.href = URL.createObjectURL(new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" })); link.download = "用户列表.csv"; link.click(); URL.revokeObjectURL(link.href);
}
onMounted(initialize);
// 切换工作部门后必须重载：列表数据是命令式加载进本地 ref 的，不会自动响应会话变化。
useScopeRefresh(initialize);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }.page__desc { color: var(--text-sub); margin: 2px 0 0; }.page__actions, .filter-actions { display: flex; gap: 8px; }
.user-layout { align-items: flex-start; display: flex; gap: 16px; }.panel { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; min-width: 0; }.department-panel { display: flex; flex-direction: column; flex: 0 0 240px; max-height: calc(100dvh - var(--topbar-h) - 32px); position: sticky; top: calc(var(--topbar-h) + 16px); }
.department-panel__head { align-items: center; display: flex; flex: none; gap: 8px; min-height: 70px; }
.department-panel__head .material-icons-outlined { color: var(--text-mute); font-size: 17px; }
.panel__count { background: var(--bg); border-radius: 999px; color: var(--text-mute); font-size: 11px; margin-left: auto; padding: 2px 8px; white-space: nowrap; }
.user-panel { flex: 1; overflow: hidden; }
.panel__head { border-bottom: 1px solid var(--border); padding: 14px 18px; }
.panel__head h2 { color: var(--text); font-size: 14px; margin: 0; }
.department-tree { flex: 1; min-height: 0; overflow-y: auto; padding: 6px 0 10px; scrollbar-color: var(--border-strong) transparent; scrollbar-width: thin; }
.department-tree::-webkit-scrollbar { width: 6px; }
.department-tree::-webkit-scrollbar-thumb { background: var(--border-strong); border-radius: 3px; }
.department-tree::-webkit-scrollbar-track { background: transparent; }
.department-tree__sep { background: var(--border); height: 1px; margin: 6px 16px 8px; }
.department-row {
  align-items: center;
  background-color: transparent;
  /* 每条导轨画在对应层级祖先展开箭头的中线上：左侧起始 8px + 箭头半宽 9px */
  background-image: repeating-linear-gradient(to right, var(--border-strong) 0 1px, transparent 1px 16px);
  background-position: 17px 0;
  background-repeat: no-repeat;
  background-size: calc(var(--dept-level, 0) * 16px) 100%;
  border-radius: 6px;
  display: flex;
  margin: 0 8px;
  min-height: 34px;
  padding-left: calc(8px + var(--dept-level, 0) * 16px);
  padding-right: 6px;
  position: relative;
  transition: background-color var(--tr);
}
.department-row__spacer { flex: none; width: 18px; }
.department-row__toggle { align-items: center; background: transparent; border: 0; border-radius: 4px; color: var(--text-mute); cursor: pointer; display: flex; flex: none; font: inherit; height: 22px; justify-content: center; padding: 0; width: 18px; }
.department-row__toggle .material-icons-outlined { font-size: 18px; transition: transform var(--tr); }
.department-row.expanded .department-row__toggle .material-icons-outlined { transform: rotate(90deg); }
.department-row__select { align-items: center; background: transparent; border: 0; color: var(--text-sub); cursor: pointer; display: flex; flex: 1; font: inherit; gap: 7px; min-width: 0; padding: 7px 0 7px 3px; text-align: left; }
.department-row__select .material-icons-outlined { color: var(--text-mute); flex: none; font-size: 16px; }
.department-row--group .department-name { color: var(--text); font-weight: 500; }
.department-row--group .department-row__select .material-icons-outlined { color: var(--text-sub); }
.department-row:hover { background-color: var(--bg); }
.department-row:hover .department-row__select { color: var(--text); }
.department-row.active { background-color: var(--primary-soft); }
.department-row.active::before { background: var(--primary); border-radius: 0 2px 2px 0; content: ""; height: 18px; left: 0; position: absolute; top: 50%; transform: translateY(-50%); width: 3px; }
.department-row.active .department-row__select, .department-row.active .department-name, .department-row.active .department-row__select .material-icons-outlined, .department-row.active .department-row__toggle { color: var(--primary); }
.department-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.department-empty { color: var(--text-mute); font-size: 12px; margin: 14px 18px; }
.filter-bar { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; padding: 18px; }.input, .select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; outline: none; padding: 0 10px; }.input { min-width: 200px; }.select { min-width: 120px; }.input:focus, .select:focus { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary-soft); }.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; gap: 5px; height: 32px; justify-content: center; padding: 0 12px; white-space: nowrap; }.button:disabled { cursor: wait; opacity: .6; }.button--primary { background: var(--primary); color: #fff; }.button--soft { background: var(--primary-soft); color: var(--primary); }.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }.button:hover:not(:disabled) { filter: brightness(.97); }.button .material-icons-outlined { font-size: 16px; }
.table-wrap { overflow-x: auto; }.table { border-collapse: collapse; font-size: 13px; min-width: 850px; width: 100%; }.table th { background: var(--bg); border-bottom: 1px solid var(--border); color: var(--text-mute); font-size: 12px; font-weight: 500; padding: 10px 14px; text-align: left; white-space: nowrap; }.table td { border-bottom: 1px solid var(--border); color: var(--text); padding: 11px 14px; white-space: nowrap; }.table tbody tr:hover { background: var(--bg); }.checkbox-cell { padding-left: 18px !important; width: 36px; }.actions-cell { text-align: right !important; }.checkbox { accent-color: var(--primary); height: 15px; width: 15px; }.username { color: var(--primary); }.role-tag { background: var(--primary-soft); border-radius: 4px; color: var(--primary); display: inline-flex; font-size: 12px; padding: 2px 8px; }.status-switch { background: #d4d4d8; border: 0; border-radius: 18px; cursor: pointer; height: 18px; padding: 2px; transition: background var(--tr); width: 34px; }.status-switch span { background: #fff; border-radius: 50%; display: block; height: 14px; transition: transform var(--tr); width: 14px; }.status-switch.enabled { background: #059669; }.status-switch.enabled span { transform: translateX(16px); }.row-action, .icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; width: 28px; }.row-action:hover { background: var(--bg); color: var(--text); }.row-action--danger:hover { background: #fef2f2; color: #dc2626; }.row-action .material-icons-outlined { font-size: 16px; }.empty, .state { color: var(--text-mute); padding: 48px; text-align: center; }.state--error, .form-error { color: #dc2626; }.pagination { align-items: center; color: var(--text-sub); display: flex; gap: 4px; justify-content: flex-end; padding: 14px 18px; }.pagination button { align-items: center; background: transparent; border: 1px solid transparent; border-radius: 5px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 28px; justify-content: center; min-width: 28px; }.pagination button:hover:not(:disabled), .pagination button.active { background: var(--primary-soft); color: var(--primary); }.pagination button:disabled { cursor: not-allowed; opacity: .4; }.pagination .material-icons-outlined { font-size: 18px; }.pagination__size { align-items: center; color: var(--text-sub); display: flex; font-size: 12px; gap: 6px; }.pagination__size .select { font-size: 12px; height: 28px; min-width: 0; padding: 0 4px 0 8px; width: auto; }
.modal-mask { align-items: center; background: rgb(0 0 0 / 38%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 300; }.modal { background: var(--card); border-radius: 8px; box-shadow: 0 16px 48px rgb(0 0 0 / 20%); max-width: 620px; width: 100%; }.modal__head, .modal__foot { align-items: center; display: flex; justify-content: space-between; padding: 14px 18px; }.modal__head { border-bottom: 1px solid var(--border); }.modal__head h2 { color: var(--text); font-size: 16px; margin: 0; }.modal__foot { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }.modal__body { padding: 20px 18px; }.form-grid { display: grid; gap: 14px 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }.field { display: grid; gap: 6px; }.field span { color: var(--text-sub); font-size: 12px; font-weight: 500; }.field em { color: #dc2626; font-style: normal; }.field .input, .field .select { width: 100%; }
@media (max-width: 900px) { .user-layout { flex-direction: column; }.department-panel { flex: none; max-height: none; position: static; width: 100%; }.department-tree { max-height: 260px; } }.department-panel { max-width: 100%; } @media (max-width: 600px) { .page { padding: 16px; }.input { min-width: 0; width: 100%; }.filter-bar { align-items: stretch; flex-direction: column; }.filter-actions { justify-content: flex-end; }.form-grid { grid-template-columns: 1fr; }.modal-mask { padding: 12px; } }
.managed-scope { border-top: 1px solid var(--border); display: grid; gap: 7px; margin-top: 16px; padding-top: 16px; }
.managed-scope > span { color: var(--text-sub); font-size: 11px; font-weight: 550; }
</style>
