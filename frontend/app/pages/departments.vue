<template>
  <section class="page">
    <header class="page__header">
      <div>
        <h1 class="page__title">部门管理</h1>
        <p class="page__desc">维护组织架构树形结构</p>
      </div>
      <div class="page__actions">
        <button class="button button--ghost button--sm" type="button" @click="toggleAll">
          <span class="material-icons-outlined">unfold_more</span>展开/折叠
        </button>
        <button v-if="canManageDepartment" class="button button--primary button--sm" type="button" @click="openCreate">
          <span class="material-icons-outlined">add</span>新增部门
        </button>
      </div>
    </header>

    <section class="card">
      <header class="card__head"><h2 class="card__title">部门架构</h2></header>
      <div v-if="loading" class="state">正在加载部门数据...</div>
      <div v-else-if="errorMessage" class="state state--error">{{ errorMessage }}</div>
      <div v-else class="card__body">
        <ul v-if="roots.length" class="tree">
          <department-node
            v-for="department in roots"
            :key="department.id"
            :department="department"
            :departments="departments"
            :level="0"
            :open="openDepartments"
            @toggle="toggleDepartment"
            @edit="openEdit"
            @remove="removeDepartment"
          />
        </ul>
        <p v-else class="empty-state">暂无数据</p>
      </div>
    </section>

    <div v-if="editorVisible" class="modal-mask" @click.self="editorVisible = false">
      <form class="modal" @submit.prevent="saveDepartment">
        <header class="modal__head">
          <h2 class="modal__title">{{ editingId === null ? "新增部门" : "编辑部门" }}</h2>
          <button class="icon-button" type="button" title="关闭" @click="editorVisible = false"><span class="material-icons-outlined">close</span></button>
        </header>
        <div class="modal__body">
          <div class="form-grid">
            <label class="field"><span class="field__label">部门名称<em>*</em></span><input v-model.trim="form.name" class="input" required></label>
            <label class="field"><span class="field__label">部门编码<em>*</em></span><input v-model.trim="form.code" class="input" required></label>
            <label class="field"><span class="field__label">上级部门</span><select v-model.number="form.parent" class="select"><option :value="0">顶级部门</option><option v-for="department in parentOptions" :key="department.id" :value="department.id">{{ department.name }}</option></select></label>
            <label class="field"><span class="field__label">显示排序</span><input v-model.number="form.sort" class="input" type="number" min="1"></label>
            <label class="field"><span class="field__label">负责人</span><input v-model.trim="form.leader" class="input"></label>
            <label class="field"><span class="field__label">联系电话</span><input v-model.trim="form.phone" class="input"></label>
            <label class="field full"><span class="field__label">状态</span><select v-model.number="form.status" class="select"><option :value="1">正常</option><option :value="0">停用</option></select></label>
          </div>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </div>
        <footer class="modal__foot">
          <button class="button button--ghost" type="button" @click="editorVisible = false">取消</button>
          <button class="button button--primary" type="submit" :disabled="saving">{{ saving ? "保存中..." : "保存" }}</button>
        </footer>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
interface Department {
  id: number;
  name: string;
  code: string;
  parent: number | null;
  sort: number;
  leader?: string;
  phone?: string;
  status: number;
}

interface DepartmentNodeProps {
  department: Department;
  departments: Department[];
  level: number;
  open: Set<number>;
}

const DepartmentNode = defineComponent({
  name: "DepartmentNode",
  props: {
    department: { type: Object as PropType<Department>, required: true },
    departments: { type: Array as PropType<Department[]>, required: true },
    level: { type: Number, required: true },
    open: { type: Object as PropType<Set<number>>, required: true },
  },
  emits: ["toggle", "edit", "remove"],
  setup(props: DepartmentNodeProps, { emit }) {
    const children = computed(() => props.departments.filter((item) => item.parent === props.department.id).sort((a, b) => a.sort - b.sort || a.id - b.id));
    const hasChildren = computed(() => children.value.length > 0);
    return () => h("li", { class: "tree-node" }, [
      h("div", { class: "tree-row", style: { paddingLeft: `${10 + props.level * 16}px` } }, [
        h("button", { class: ["tree-toggle", { open: props.open.has(props.department.id) }], type: "button", title: hasChildren.value ? "展开/折叠" : undefined, onClick: () => hasChildren.value && emit("toggle", props.department.id) }, [
          h("span", { class: "material-icons-outlined" }, hasChildren.value ? "chevron_right" : "circle"),
        ]),
        h("span", { class: "material-icons-outlined tree-icon" }, "apartment"),
        h("strong", { class: "department-name" }, props.department.name),
        h("span", { class: "tag tag--gray" }, props.department.code),
        h("span", { class: "tree-meta" }, `负责人：${props.department.leader || "-"} · ${props.department.phone || "-"}`),
        h("span", { class: ["tag", props.department.status === 1 ? "tag--green" : "tag--red"] }, props.department.status === 1 ? "正常" : "停用"),
        canManageDepartment.value ? h("button", { class: "row-act", type: "button", title: "编辑", onClick: (event: Event) => { event.stopPropagation(); emit("edit", props.department.id); } }, [h("span", { class: "material-icons-outlined" }, "edit")]) : null,
        canManageDepartment.value ? h("button", { class: "row-act row-act--danger", type: "button", title: "删除", onClick: (event: Event) => { event.stopPropagation(); emit("remove", props.department.id); } }, [h("span", { class: "material-icons-outlined" }, "delete")]) : null,
      ]),
      hasChildren.value && props.open.has(props.department.id)
        ? h("ul", { class: "tree tree-children open" }, children.value.map((child) => h(DepartmentNode, { department: child, departments: props.departments, level: props.level + 1, open: props.open, onToggle: (id: number) => emit("toggle", id), onEdit: (id: number) => emit("edit", id), onRemove: (id: number) => emit("remove", id) })))
        : null,
    ]);
  },
});

const http = useHttp();
const { can } = usePermission();
// 行内操作在渲染函数里生成，用 computed 让它在权限变化时同样生效。
const canManageDepartment = computed(() => can("department:manage"));
const departments = ref<Department[]>([]);
const openDepartments = ref<Set<number>>(new Set());
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref("");
const formError = ref("");
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({ name: "", code: "", parent: 0, sort: 1, leader: "", phone: "", status: 1 });

const roots = computed(() => departments.value.filter((department) => (department.parent ?? 0) === 0).sort((a, b) => a.sort - b.sort || a.id - b.id));
const parentOptions = computed(() => departments.value.filter((department) => department.id !== editingId.value && !descendantIds(editingId.value).has(department.id)).sort((a, b) => a.sort - b.sort || a.id - b.id));

function descendantIds(id: number | null) {
  const result = new Set<number>();
  if (id === null) return result;
  const pending = [id];
  while (pending.length) {
    const current = pending.shift()!;
    departments.value.filter((item) => item.parent === current).forEach((item) => { result.add(item.id); pending.push(item.id); });
  }
  return result;
}

async function loadDepartments() {
  loading.value = true;
  errorMessage.value = "";
  try {
    departments.value = (await http.get<Department[]>("/depts")) || [];
  } catch (error) {
    errorMessage.value = (error as { statusMessage?: string }).statusMessage || "部门数据加载失败";
  } finally {
    loading.value = false;
  }
}

function toggleDepartment(id: number) {
  const next = new Set(openDepartments.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  openDepartments.value = next;
}

function toggleAll() {
  const allOpen = departments.value.some((department) => departments.value.some((item) => item.parent === department.id)) && departments.value.filter((department) => departments.value.some((item) => item.parent === department.id)).every((department) => openDepartments.value.has(department.id));
  openDepartments.value = allOpen ? new Set() : new Set(departments.value.map((department) => department.id));
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { name: "", code: "", parent: 0, sort: 1, leader: "", phone: "", status: 1 });
  formError.value = "";
  editorVisible.value = true;
}

function openEdit(id: number) {
  const department = departments.value.find((item) => item.id === id);
  if (!department) return;
  editingId.value = id;
  Object.assign(form, { name: department.name, code: department.code, parent: department.parent ?? 0, sort: department.sort, leader: department.leader || "", phone: department.phone || "", status: department.status });
  formError.value = "";
  editorVisible.value = true;
}

async function saveDepartment() {
  if (!form.name || !form.code) { formError.value = "名称和编码不能为空"; return; }
  saving.value = true;
  formError.value = "";
  const payload = { name: form.name, code: form.code, parent: form.parent === 0 ? null : form.parent, sort: Number(form.sort) || 1, leader: form.leader, phone: form.phone, status: form.status };
  try {
    if (editingId.value === null) await http.post<Department>("/depts", payload, { payloadMode: "json" });
    else await http.put<Department>(`/depts/${editingId.value}`, payload, { payloadMode: "json" });
    editorVisible.value = false;
    await loadDepartments();
  } catch (error) {
    formError.value = (error as { statusMessage?: string }).statusMessage || "保存失败";
  } finally { saving.value = false; }
}

async function removeDepartment(id: number) {
  const department = departments.value.find((item) => item.id === id);
  if (!window.confirm(`确认删除“${department?.name || "该部门"}”？子部门将一并删除`)) return;
  try { await http.delete(`/depts/${id}`); await loadDepartments(); }
  catch (error) { errorMessage.value = (error as { statusMessage?: string }).statusMessage || "删除失败"; }
}

onMounted(loadDepartments);
</script>

<style scoped>
.page { min-height: 100%; padding: 24px; }.page__header { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 20px; }.page__title { color: var(--text); font-size: 18px; font-weight: 600; margin: 0; }.page__desc { color: var(--text-sub); margin: 2px 0 0; }.page__actions { display: flex; gap: 8px; }
.card { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; }.card__head { align-items: center; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; padding: 14px 18px; }.card__title { color: var(--text); font-size: 14px; font-weight: 600; margin: 0; }.card__body { padding: 18px; }.state, .empty-state { color: var(--text-mute); padding: 30px; text-align: center; }.state--error, .form-error { color: var(--red); }
.button { align-items: center; border: 1px solid transparent; border-radius: 6px; cursor: pointer; display: inline-flex; font: inherit; font-size: 13px; gap: 5px; height: 32px; justify-content: center; padding: 0 12px; white-space: nowrap; }.button--sm { font-size: 12px; height: 28px; padding: 0 10px; }.button--primary { background: var(--primary); color: #fff; }.button--ghost { background: var(--card); border-color: var(--border-strong); color: var(--text-sub); }.button:hover:not(:disabled) { filter: brightness(.97); }.button:disabled { cursor: wait; opacity: .6; }.button .material-icons-outlined { font-size: 16px; }
.tree { list-style: none; margin: 0; padding: 0; }.tree-node { margin-bottom: 1px; }.tree-row { align-items: center; cursor: default; display: flex; gap: 6px; min-height: 34px; padding: 7px 10px; }.tree-row:hover { background: var(--bg); }.tree-toggle { align-items: center; background: transparent; border: 0; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 24px; justify-content: center; padding: 0; width: 18px; }.tree-toggle:not(.open) .material-icons-outlined { transform: none; }.tree-toggle.open .material-icons-outlined { transform: rotate(90deg); }.tree-toggle .material-icons-outlined { font-size: 16px; transition: transform var(--tr); }.tree-icon { color: #ea580c; font-size: 18px; }.department-name { color: var(--text); margin-right: 4px; }.tree-children { margin-left: 16px; }.tag { align-items: center; border-radius: 4px; display: inline-flex; font-size: 12px; gap: 4px; line-height: 1.5; padding: 2px 8px; }.tag::before { background: currentColor; border-radius: 50%; content: ""; height: 5px; width: 5px; }.tag--gray { background: #f4f4f5; color: var(--text-sub); margin-left: 4px; }.tag--green { background: #ecfdf5; color: #059669; }.tag--red { background: #fef2f2; color: #dc2626; }.tree-meta { color: var(--text-mute); font-size: 12px; margin-left: auto; margin-right: 8px; }.row-act, .icon-button { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 26px; justify-content: center; margin: 0 1px; width: 26px; }.row-act:hover { background: var(--bg); color: var(--text); }.row-act--danger:hover { background: #fef2f2; color: var(--red); }.row-act .material-icons-outlined, .icon-button .material-icons-outlined { font-size: 16px; }
:deep(.tree-node) { margin-bottom: 1px; }:deep(.tree-row) { align-items: center; background: transparent; border-radius: 6px; display: flex; gap: 6px; min-height: 34px; padding: 7px 10px; }:deep(.tree-row:hover) { background: var(--bg); }:deep(.tree-toggle) { align-items: center; background: transparent; border: 0; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 24px; justify-content: center; padding: 0; width: 18px; }:deep(.tree-toggle .material-icons-outlined) { font-size: 16px; transition: transform var(--tr); }:deep(.tree-toggle.open .material-icons-outlined) { transform: rotate(90deg); }:deep(.tree-children) { margin-left: 16px; }:deep(.tree-icon) { color: #ea580c; font-size: 18px; }:deep(.department-name) { color: var(--text); margin-right: 4px; }:deep(.tree-meta) { color: var(--text-mute); font-size: 12px; margin-left: auto; margin-right: 8px; }:deep(.tag) { align-items: center; border-radius: 4px; display: inline-flex; font-size: 12px; gap: 4px; line-height: 1.5; padding: 2px 8px; }:deep(.tag::before) { background: currentColor; border-radius: 50%; content: ""; height: 5px; width: 5px; }:deep(.tag--gray) { background: #f4f4f5; color: var(--text-sub); margin-left: 4px; }:deep(.tag--green) { background: #ecfdf5; color: #059669; }:deep(.tag--red) { background: #fef2f2; color: #dc2626; }:deep(.row-act), :deep(.icon-button) { align-items: center; background: transparent; border: 0; border-radius: 5px; color: var(--text-mute); cursor: pointer; display: inline-flex; height: 26px; justify-content: center; margin: 0 1px; width: 26px; }:deep(.row-act:hover) { background: var(--bg); color: var(--text); }:deep(.row-act--danger:hover) { background: #fef2f2; color: var(--red); }:deep(.row-act .material-icons-outlined), :deep(.icon-button .material-icons-outlined) { font-size: 16px; }
.modal-mask { align-items: center; background: rgb(0 0 0 / 40%); display: flex; inset: 0; justify-content: center; padding: 20px; position: fixed; z-index: 300; }.modal { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; box-shadow: 0 16px 48px rgb(0 0 0 / 20%); max-width: 620px; width: 100%; }.modal__head, .modal__foot { align-items: center; display: flex; justify-content: space-between; padding: 14px 20px; }.modal__head { border-bottom: 1px solid var(--border); }.modal__title { color: var(--text); font-size: 15px; font-weight: 600; margin: 0; }.modal__body { padding: 20px; }.modal__foot { border-top: 1px solid var(--border); gap: 8px; justify-content: flex-end; }.form-grid { display: grid; gap: 0 16px; grid-template-columns: 1fr 1fr; }.field { margin-bottom: 14px; }.field__label { color: var(--text-sub); display: block; font-size: 12px; font-weight: 500; margin-bottom: 5px; }.field__label em { color: var(--red); font-style: normal; margin-left: 2px; }.input, .select { background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; box-sizing: border-box; color: var(--text); font: inherit; height: 34px; outline: none; padding: 0 10px; width: 100%; }.input:focus, .select:focus { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary-soft); }.full { grid-column: 1 / -1; }
@media (max-width: 700px) { .page { padding: 16px; }.tree-row { align-items: flex-start; flex-wrap: wrap; }.tree-meta { margin-left: 24px; width: 100%; }.tag--green, .tag--red { margin-left: 0; }.form-grid { grid-template-columns: 1fr; }.full { grid-column: auto; }.modal-mask { padding: 12px; } }
</style>
