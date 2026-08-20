<template>
  <div class="position-page">
    <n-page-header title="岗位管理" subtitle="维护岗位名称、业务编码、显示顺序和启用状态">
      <template #extra>
        <n-space>
          <n-button :loading="loading" @click="loadPositions">刷新</n-button>
          <n-button type="primary" @click="openCreate">新增岗位</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-card class="toolbar-card" :bordered="false">
      <n-space justify="space-between" align="center">
        <n-space>
          <n-input v-model:value="keyword" clearable placeholder="搜索岗位名称或编码" style="width: 300px" />
          <n-select v-model:value="statusFilter" clearable placeholder="全部状态" :options="statusOptions" style="width: 140px" />
        </n-space>
        <n-space>
          <n-text depth="3">已选择 {{ checkedRowKeys.length }} 项</n-text>
          <n-button :disabled="checkedRowKeys.length === 0" @click="openBatchEdit">批量修改</n-button>
          <n-button type="error" secondary :disabled="checkedRowKeys.length === 0" @click="confirmBatchDelete">批量删除</n-button>
        </n-space>
      </n-space>
    </n-card>

    <n-card :bordered="false">
      <n-data-table
        :columns="columns"
        :data="filteredPositions"
        :loading="loading"
        :row-key="row => row.id"
        :checked-row-keys="checkedRowKeys"
        :pagination="pagination"
        @update:checked-row-keys="keys => checkedRowKeys = keys as number[]"
      />
      <n-empty v-if="!loading && filteredPositions.length === 0" class="empty" description="暂无岗位数据" />
    </n-card>

    <n-modal v-model:show="editorVisible" preset="card" :title="editorTitle" style="width: min(520px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="88">
        <n-form-item label="岗位名称" path="name">
          <n-input v-model:value="form.name" placeholder="请输入岗位名称" />
        </n-form-item>
        <n-form-item label="岗位编码" path="codeNumber">
          <n-input v-model:value="form.codeNumber" placeholder="请输入唯一岗位编码" />
        </n-form-item>
        <n-form-item label="显示顺序">
          <n-input-number v-model:value="form.orderNumber" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="form.status" checked-value="Activity" unchecked-value="BANNED">
            <template #checked>启用</template>
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

    <n-modal v-model:show="batchVisible" preset="card" title="批量修改岗位" style="width: min(560px, 92vw)">
      <n-alert type="warning">仅勾选的字段会应用到 {{ checkedRowKeys.length }} 个岗位。</n-alert>
      <n-form label-placement="left" label-width="92" class="batch-form">
        <n-form-item label="显示顺序">
          <n-space align="center">
            <n-checkbox v-model:checked="batch.enabled.orderNumber" />
            <n-input-number v-model:value="batch.orderNumber" :disabled="!batch.enabled.orderNumber" :min="0" style="width: 320px" />
          </n-space>
        </n-form-item>
        <n-form-item label="状态">
          <n-space align="center">
            <n-checkbox v-model:checked="batch.enabled.status" />
            <n-switch v-model:value="batch.status" :disabled="!batch.enabled.status" checked-value="Activity" unchecked-value="BANNED">
              <template #checked>启用</template>
              <template #unchecked>停用</template>
            </n-switch>
          </n-space>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="batchVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submitBatchEdit">应用修改</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { NButton, NSpace, NTag } from "naive-ui";

interface Position {
  id: number;
  name: string;
  position_code: string;
  order_number: number;
  status: "Activity" | "BANNED";
}

interface PositionPage {
  content?: Position[];
  positions?: Position[];
  totalElements?: number;
  total?: number;
}

const http = useHttp("http://127.0.0.1:8080");
const message = useMessage();
const dialog = useDialog();
const loading = ref(false);
const submitting = ref(false);
const positions = ref<Position[]>([]);
const keyword = ref("");
const statusFilter = ref<Position["status"] | null>(null);
const checkedRowKeys = ref<number[]>([]);
const editorVisible = ref(false);
const batchVisible = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref<{ validate: () => Promise<void> } | null>(null);
const form = reactive({ name: "", codeNumber: "", orderNumber: 0, status: "Activity" as Position["status"] });
const batch = reactive({
  orderNumber: 0,
  status: "Activity" as Position["status"],
  enabled: { orderNumber: false, status: false },
});
const rules = {
  name: { required: true, message: "请输入岗位名称", trigger: ["input", "blur"] },
  codeNumber: { required: true, message: "请输入岗位编码", trigger: ["input", "blur"] },
};
const statusOptions = [
  { label: "启用", value: "Activity" },
  { label: "停用", value: "BANNED" },
];
const pagination = { pageSize: 20 };
const editorTitle = computed(() => editingId.value ? "编辑岗位" : "新增岗位");

const filteredPositions = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return positions.value.filter(position => {
    const matchesKeyword = !text || position.name.toLowerCase().includes(text) || position.position_code.toLowerCase().includes(text);
    return matchesKeyword && (!statusFilter.value || position.status === statusFilter.value);
  });
});

const columns = [
  { type: "selection" },
  {
    title: "岗位名称",
    key: "name",
    minWidth: 220,
    render: (row: Position) => h(NSpace, { align: "center", wrap: false }, {
      default: () => [h("strong", row.name), h(NTag, { size: "small", bordered: false }, { default: () => row.position_code })],
    }),
  },
  { title: "显示顺序", key: "order_number", width: 120 },
  {
    title: "状态",
    key: "status",
    width: 120,
    render: (row: Position) => h(NTag, { type: row.status === "Activity" ? "success" : "warning", bordered: false }, { default: () => row.status === "Activity" ? "启用" : "停用" }),
  },
  {
    title: "操作",
    key: "actions",
    width: 180,
    fixed: "right",
    render: (row: Position) => h(NSpace, { size: 6 }, {
      default: () => [
        h(NButton, { size: "small", onClick: () => openEdit(row) }, { default: () => "编辑" }),
        h(NButton, { size: "small", type: "error", tertiary: true, onClick: () => confirmDelete(row) }, { default: () => "删除" }),
      ],
    }),
  },
];

function params(values: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== null && value !== ""));
}

function normalizePosition(value: Position & { code_number?: string; orderNumber?: number; codeNumber?: string }): Position {
  return {
    id: value.id,
    name: value.name,
    position_code: value.position_code ?? value.code_number ?? value.codeNumber ?? "",
    order_number: value.order_number ?? value.orderNumber ?? 0,
    status: value.status,
  };
}

async function loadPositions() {
  loading.value = true;
  try {
    const result = await http.get<PositionPage>("/api/positions", { page: 1, page_size: 100 });
    const records = result.positions ?? result.content ?? [];
    positions.value = records.map(normalizePosition).sort((a, b) => a.order_number - b.order_number || a.name.localeCompare(b.name, "zh-CN"));
    checkedRowKeys.value = checkedRowKeys.value.filter(id => positions.value.some(position => position.id === id));
  } catch (error) {
    message.error((error as { statusMessage?: string }).statusMessage || "岗位列表加载失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, { name: "", codeNumber: "", orderNumber: 0, status: "Activity" });
}
function openCreate() {
  editingId.value = null;
  resetForm();
  editorVisible.value = true;
}
function openEdit(row: Position) {
  editingId.value = row.id;
  Object.assign(form, { name: row.name, codeNumber: row.position_code, orderNumber: row.order_number, status: row.status });
  editorVisible.value = true;
}
async function submitEditor() {
  await formRef.value?.validate();
  submitting.value = true;
  try {
    const payload = params({ name: form.name, position_code: form.codeNumber, sort_order: form.orderNumber, status: form.status });
    if (editingId.value) {
      await http.put(`/api/positions/${editingId.value}`, { id: editingId.value, name: form.name, codeNumber: form.codeNumber, orderNumber: form.orderNumber, status: form.status }, { payloadMode: "json" });
    } else {
      await http.post("/api/positions", { name: form.name, codeNumber: form.codeNumber, orderNumber: form.orderNumber, status: form.status }, { payloadMode: "json" });
    }
    void payload;
    message.success(editingId.value ? "岗位更新成功" : "岗位创建成功");
    editorVisible.value = false;
    await loadPositions();
  } catch (error) {
    message.error((error as { statusMessage?: string }).statusMessage || "保存失败");
  } finally {
    submitting.value = false;
  }
}
function openBatchEdit() {
  Object.assign(batch, { orderNumber: 0, status: "Activity" });
  batch.enabled.orderNumber = false;
  batch.enabled.status = false;
  batchVisible.value = true;
}
async function submitBatchEdit() {
  if (!batch.enabled.orderNumber && !batch.enabled.status) {
    message.warning("请至少勾选一个要修改的字段");
    return;
  }
  submitting.value = true;
  try {
    const records = positions.value.filter(position => checkedRowKeys.value.includes(position.id)).map(position => ({
      id: position.id,
      name: position.name,
      codeNumber: position.position_code,
      orderNumber: batch.enabled.orderNumber ? batch.orderNumber : position.order_number,
      status: batch.enabled.status ? batch.status : position.status,
    }));
    await http.put("/api/positions/batch", records, { payloadMode: "json" });
    message.success("批量修改成功");
    batchVisible.value = false;
    await loadPositions();
  } catch (error) {
    message.error((error as { statusMessage?: string }).statusMessage || "批量修改失败");
  } finally {
    submitting.value = false;
  }
}
function confirmDelete(row: Position) {
  dialog.warning({
    title: "删除岗位",
    content: `确认删除“${row.name}”吗？`,
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      await http.delete(`/api/positions/${row.id}`);
      message.success("删除成功");
      await loadPositions();
    },
  });
}
function confirmBatchDelete() {
  dialog.warning({
    title: "批量删除",
    content: `确认删除选中的 ${checkedRowKeys.value.length} 个岗位吗？`,
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      await http.delete("/api/positions/batch", { id: checkedRowKeys.value });
      message.success("批量删除成功");
      checkedRowKeys.value = [];
      await loadPositions();
    },
  });
}

onMounted(loadPositions);
</script>

<style scoped>
.position-page { min-height: 100%; padding: 24px; background: #f5f7fa; }
.toolbar-card { margin: 20px 0 12px; }
.empty { padding: 64px 0; }
.batch-form { margin-top: 20px; }
:deep(.n-data-table) { border-radius: 8px; overflow: hidden; }
@media (max-width: 720px) { .position-page { padding: 14px; } }
</style>





