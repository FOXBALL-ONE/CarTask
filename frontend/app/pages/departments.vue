<template>
  <div class="department-page">
    <n-page-header title="部门管理" subtitle="维护组织架构、负责人和部门层级">
      <template #extra>
        <n-space>
          <n-button :loading="loading" @click="loadDepartments">刷新</n-button>
          <n-button type="primary" @click="openCreate()">新增部门</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-card class="toolbar-card" :bordered="false">
      <n-space justify="space-between" align="center">
        <n-input v-model:value="keyword" clearable placeholder="搜索部门名称、编码、负责人或电话" style="max-width: 420px">
          <template #prefix>⌕</template>
        </n-input>
        <n-space>
          <n-text depth="3">已选择 {{ checkedRowKeys.length }} 项</n-text>
          <n-button :disabled="checkedRowKeys.length === 0" @click="openBatchEdit">批量修改</n-button>
          <n-button type="error" secondary :disabled="checkedRowKeys.length === 0" @click="confirmBatchDelete">批量删除</n-button>
        </n-space>
      </n-space>
    </n-card>

    <n-card :bordered="false">
      <n-data-table
        remote
        :columns="columns"
        :data="filteredTree"
        :loading="loading"
        :row-key="row => row.id"
        :checked-row-keys="checkedRowKeys"
        default-expand-all
        @update:checked-row-keys="keys => checkedRowKeys = keys as number[]"
      />
      <n-empty v-if="!loading && filteredTree.length === 0" class="empty" description="暂无部门数据" />
    </n-card>

    <n-modal v-model:show="editorVisible" preset="card" :title="editorTitle" style="width: min(640px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="92">
        <n-form-item label="部门名称" path="name"><n-input v-model:value="form.name" placeholder="请输入部门名称" /></n-form-item>
        <n-form-item label="部门编码" path="departmentCode"><n-input v-model:value="form.departmentCode" placeholder="请输入唯一部门编码" /></n-form-item>
        <n-form-item label="上级部门">
          <n-tree-select v-model:value="form.superiorId" clearable :options="superiorOptions" placeholder="不选择则为顶级部门" />
        </n-form-item>
        <n-form-item label="显示顺序"><n-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" /></n-form-item>
        <n-form-item label="负责人"><n-input v-model:value="form.director" clearable placeholder="请输入负责人" /></n-form-item>
        <n-form-item label="联系电话"><n-input v-model:value="form.contactPhone" clearable placeholder="请输入联系电话" /></n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0">
            <template #checked>正常</template>
            <template #unchecked>停用</template>
          </n-switch>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="editorVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submitEditor">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="childrenVisible" preset="card" title="批量新增下级部门" style="width: min(900px, 94vw)">
      <n-alert type="info" :show-icon="false">上级部门：{{ activeDepartment?.name }}</n-alert>
      <n-dynamic-input v-model:value="childForms" :min="1" class="children-input" @create="createChildForm">
        <template #default="{ value }">
          <n-grid :cols="24" :x-gap="8">
            <n-form-item-gi :span="6" label="部门名称" required><n-input v-model:value="value.name" /></n-form-item-gi>
            <n-form-item-gi :span="5" label="部门编码" required><n-input v-model:value="value.departmentCode" /></n-form-item-gi>
            <n-form-item-gi :span="3" label="排序"><n-input-number v-model:value="value.sortOrder" :min="0" /></n-form-item-gi>
            <n-form-item-gi :span="5" label="负责人"><n-input v-model:value="value.director" /></n-form-item-gi>
            <n-form-item-gi :span="5" label="联系电话"><n-input v-model:value="value.contactPhone" /></n-form-item-gi>
          </n-grid>
        </template>
      </n-dynamic-input>
      <template #footer>
        <n-space justify="end">
          <n-button @click="childrenVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submitChildren">批量新增</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="batchVisible" preset="card" title="批量修改部门" style="width: min(620px, 92vw)">
      <n-alert type="warning">仅勾选的字段会应用到 {{ checkedRowKeys.length }} 个部门。</n-alert>
      <n-form label-placement="left" label-width="100" class="batch-form">
        <n-form-item label="移动到部门">
          <n-space align="center"><n-checkbox v-model:checked="batch.enabled.superiorId" /> <n-tree-select v-model:value="batch.superiorId" clearable :disabled="!batch.enabled.superiorId" :options="batchSuperiorOptions" style="width: 360px" /></n-space>
        </n-form-item>
        <n-form-item label="显示顺序">
          <n-space align="center"><n-checkbox v-model:checked="batch.enabled.sortOrder" /> <n-input-number v-model:value="batch.sortOrder" :disabled="!batch.enabled.sortOrder" :min="0" style="width: 360px" /></n-space>
        </n-form-item>
        <n-form-item label="负责人">
          <n-space align="center"><n-checkbox v-model:checked="batch.enabled.director" /> <n-input v-model:value="batch.director" :disabled="!batch.enabled.director" style="width: 360px" /></n-space>
        </n-form-item>
        <n-form-item label="联系电话">
          <n-space align="center"><n-checkbox v-model:checked="batch.enabled.contactPhone" /> <n-input v-model:value="batch.contactPhone" :disabled="!batch.enabled.contactPhone" style="width: 360px" /></n-space>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end"><n-button @click="batchVisible = false">取消</n-button><n-button type="primary" :loading="submitting" @click="submitBatchEdit">应用修改</n-button></n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { NButton, NSpace, NTag } from "naive-ui";

interface Department {
  id: number;
  name: string;
  code: string;
  parent: number | null;
  sort: number;
  leader: string | null;
  phone: string | null;
  status: number;
  children?: Department[];
}
interface ChildForm { name: string; departmentCode: string; sortOrder: number; director: string; contactPhone: string }

const http = useHttp("http://127.0.0.1:8080");
const message = useMessage();
const dialog = useDialog();
const loading = ref(false);
const submitting = ref(false);
const departments = ref<Department[]>([]);
const keyword = ref("");
const checkedRowKeys = ref<number[]>([]);
const editorVisible = ref(false);
const childrenVisible = ref(false);
const batchVisible = ref(false);
const editingId = ref<number | null>(null);
const activeDepartment = ref<Department | null>(null);
const formRef = ref<FormInst | null>(null);
const form = reactive({ name: "", departmentCode: "", superiorId: null as number | null, sortOrder: 0, director: "", contactPhone: "", status: 1 });
const childForms = ref<ChildForm[]>([createChildForm()]);
const batch = reactive({
  superiorId: null as number | null, sortOrder: 0, director: "", contactPhone: "",
  enabled: { superiorId: false, sortOrder: false, director: false, contactPhone: false },
});
const rules: FormRules = {
  name: { required: true, message: "请输入部门名称", trigger: ["input", "blur"] },
  departmentCode: { required: true, message: "请输入部门编码", trigger: ["input", "blur"] },
};

function createChildForm(): ChildForm { return { name: "", departmentCode: "", sortOrder: 0, director: "", contactPhone: "" }; }
function buildTree(items: Department[]): Department[] {
  const map = new Map(items.map(item => [item.id, { ...item, children: [] as Department[] }]));
  const roots: Department[] = [];
  map.forEach(item => { const parent = item.parent ? map.get(item.parent) : undefined; (parent ? parent.children! : roots).push(item); });
  const sort = (nodes: Department[]) => { nodes.sort((a, b) => a.sort - b.sort || a.name.localeCompare(b.name, "zh-CN")); nodes.forEach(node => sort(node.children ?? [])); };
  sort(roots); return roots;
}
function matches(item: Department, text: string) { return [item.name, item.code, item.leader, item.phone].some(value => value?.toLowerCase().includes(text)); }
function filterTree(nodes: Department[], text: string): Department[] {
  if (!text) return nodes;
  return nodes.flatMap(node => { const children = filterTree(node.children ?? [], text); return matches(node, text) || children.length ? [{ ...node, children }] : []; });
}
const departmentTree = computed(() => buildTree(departments.value));
const filteredTree = computed(() => filterTree(departmentTree.value, keyword.value.trim().toLowerCase()));
function toOptions(nodes: Department[], disabledIds = new Set<number>()): TreeSelectOption[] {
  return nodes.map(node => ({ label: `${node.name}（${node.code}）`, key: node.id, disabled: disabledIds.has(node.id), children: toOptions(node.children ?? [], disabledIds) }));
}
function descendantsOf(id: number) { const result = new Set<number>([id]); let changed = true; while (changed) { changed = false; departments.value.forEach(item => { if (item.parent && result.has(item.parent) && !result.has(item.id)) { result.add(item.id); changed = true; } }); } return result; }
const superiorOptions = computed(() => toOptions(departmentTree.value, editingId.value ? descendantsOf(editingId.value) : new Set()));
const batchSuperiorOptions = computed(() => toOptions(departmentTree.value, new Set(checkedRowKeys.value)));
const editorTitle = computed(() => editingId.value ? "编辑部门" : "新增部门");

const columns: DataTableColumns<Department> = [
  { type: "selection" },
  { title: "部门名称", key: "name", minWidth: 220, render: row => h(NSpace, { align: "center", wrap: false }, { default: () => [h("strong", row.name), h(NTag, { size: "small", bordered: false }, { default: () => row.code })] }) },
  { title: "负责人", key: "leader", width: 130, render: row => row.leader || "—" },
  { title: "联系电话", key: "phone", width: 150, render: row => row.phone || "—" },
  { title: "排序", key: "sort", width: 80 },
  { title: "操作", key: "actions", width: 310, fixed: "right", render: row => h(NSpace, { size: 6 }, { default: () => [
    h(NButton, { size: "small", type: "primary", secondary: true, onClick: () => openCreate(row) }, { default: () => "新增下级" }),
    h(NButton, { size: "small", onClick: () => openChildren(row) }, { default: () => "批量下级" }),
    h(NButton, { size: "small", onClick: () => openEdit(row) }, { default: () => "编辑" }),
    h(NButton, { size: "small", type: "error", tertiary: true, onClick: () => confirmDelete(row) }, { default: () => "删除" }),
  ] }) },
];

async function loadDepartments() {
  loading.value = true;
  try { const result = await http.get<Department[]>("/api/depts"); departments.value = result; checkedRowKeys.value = checkedRowKeys.value.filter(id => result.some(item => item.id === id)); }
  catch (error) { message.error((error as { statusMessage?: string }).statusMessage || "部门列表加载失败"); }
  finally { loading.value = false; }
}
function resetForm() { Object.assign(form, { name: "", departmentCode: "", superiorId: null, sortOrder: 0, director: "", contactPhone: "", status: 1 }); }
function openCreate(parent?: Department) { editingId.value = null; resetForm(); form.superiorId = parent?.id ?? null; editorVisible.value = true; }
function openEdit(row: Department) { editingId.value = row.id; Object.assign(form, { name: row.name, departmentCode: row.code, superiorId: row.parent, sortOrder: row.sort, director: row.leader ?? "", contactPhone: row.phone ?? "", status: row.status }); editorVisible.value = true; }
async function submitEditor() {
  await formRef.value?.validate(); submitting.value = true;
  try {
    const payload = { name: form.name, code: form.departmentCode, parent: form.superiorId, sort: form.sortOrder, leader: form.director || null, phone: form.contactPhone || null, status: form.status };
    if (editingId.value) await http.put(`/api/depts/${editingId.value}`, payload, { payloadMode: "json" }); else await http.post("/api/depts", payload, { payloadMode: "json" });
    message.success(editingId.value ? "部门更新成功" : "部门创建成功"); editorVisible.value = false; await loadDepartments();
  } catch (error) { if ((error as { errors?: unknown }).errors) return; message.error((error as { statusMessage?: string }).statusMessage || "保存失败"); }
  finally { submitting.value = false; }
}
function openChildren(row: Department) { activeDepartment.value = row; childForms.value = [createChildForm(), createChildForm()]; childrenVisible.value = true; }
async function submitChildren() {
  if (!activeDepartment.value) return;
  if (childForms.value.some(item => !item.name.trim() || !item.departmentCode.trim())) { message.warning("请填写每个下级部门的名称和编码"); return; }
  submitting.value = true;
  try {
    const results = await Promise.allSettled(childForms.value.map(item => http.post("/api/depts", {
      name: item.name, code: item.departmentCode, parent: activeDepartment.value!.id,
      sort: item.sortOrder, leader: item.director || null, phone: item.contactPhone || null, status: 1,
    }, { payloadMode: "json" })));
    const failedCount = results.filter(result => result.status === "rejected").length;
    const successCount = childForms.value.length - failedCount;
    childrenVisible.value = false;
    await loadDepartments();
    if (successCount > 0) message.success(`已新增 ${successCount} 个下级部门`);
    if (failedCount > 0) message.warning(`${failedCount} 个下级部门创建失败`);
  } catch (error) { message.error((error as { statusMessage?: string }).statusMessage || "批量新增失败"); }
  finally { submitting.value = false; }
}
function openBatchEdit() { Object.assign(batch, { superiorId: null, sortOrder: 0, director: "", contactPhone: "" }); Object.values(batch.enabled).forEach((_, key) => { batch.enabled[key as keyof typeof batch.enabled] = false; }); batchVisible.value = true; }
async function submitBatchEdit() {
  const enabled = batch.enabled; if (!Object.values(enabled).some(Boolean)) { message.warning("请至少勾选一个要修改的字段"); return; }
  submitting.value = true;
  try {
    const payload: Record<string, unknown> = {};
    if (enabled.superiorId) payload.parent = batch.superiorId;
    if (enabled.sortOrder) payload.sort = batch.sortOrder;
    if (enabled.director) payload.leader = batch.director || null;
    if (enabled.contactPhone) payload.phone = batch.contactPhone || null;
    const results = await Promise.allSettled(checkedRowKeys.value.map(id => http.put(`/api/depts/${id}`, payload, { payloadMode: "json" })));
    const failedCount = results.filter(result => result.status === "rejected").length;
    const successCount = results.length - failedCount;
    await loadDepartments();
    if (failedCount > 0) {
      if (successCount > 0) message.warning(`${failedCount} 个部门更新失败，其余 ${successCount} 个已完成`);
      else message.error("部门更新全部失败");
      return;
    }
    message.success("批量修改成功"); batchVisible.value = false;
  } catch (error) { message.error((error as { statusMessage?: string }).statusMessage || "批量修改失败"); }
  finally { submitting.value = false; }
}
function confirmDelete(row: Department) { dialog.warning({ title: "删除部门", content: `确认删除“${row.name}”吗？存在下级部门时将无法删除。`, positiveText: "删除", negativeText: "取消", onPositiveClick: async () => { await http.delete(`/api/depts/${row.id}`); message.success("删除成功"); await loadDepartments(); } }); }
function confirmBatchDelete() { dialog.warning({ title: "批量删除", content: `确认删除选中的 ${checkedRowKeys.value.length} 个部门吗？存在未选中的下级部门时将无法删除。`, positiveText: "删除", negativeText: "取消", onPositiveClick: async () => { const results = await Promise.allSettled(checkedRowKeys.value.map(id => http.delete(`/api/depts/${id}`))); const failedCount = results.filter(result => result.status === "rejected").length; const successCount = results.length - failedCount; checkedRowKeys.value = []; await loadDepartments(); if (successCount > 0) message.success(`已删除 ${successCount} 个部门`); if (failedCount > 0) message.warning(`${failedCount} 个部门删除失败`); } }); }

onMounted(loadDepartments);
</script>

<style scoped>
.department-page { min-height: 100%; padding: 24px; background: #f5f7fa; }
.toolbar-card { margin: 20px 0 12px; }
.empty { padding: 64px 0; }
.children-input, .batch-form { margin-top: 20px; }
:deep(.n-data-table) { border-radius: 8px; overflow: hidden; }
@media (max-width: 720px) { .department-page { padding: 14px; } }
</style>


