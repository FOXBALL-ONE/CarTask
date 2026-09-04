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
  code: string;
  sort: number;
  status: "Activity" | "BANNED";
  remark: string | null;
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
    const matchesKeyword = !text || position.name.toLowerCase().includes(text) || position.code.toLowerCase().includes(text);
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
      default: () => [h("strong", row.name), h(NTag, { size: "small", bordered: false }, { default: () => row.code })],
    }),
  },
  { title: "显示顺序", key: "sort", width: 120 },
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

function normalizePosition(value: { id: number; name: string; code: string; sort: number; status: number; remark?: string | null }): Position {
  return {
    id: value.id,
    name: value.name,
    code: value.code,
    sort: value.sort,
    status: value.status === 1 ? "Activity" : "BANNED",
    remark: value.remark ?? null,
  };
}

async function loadPositions() {
  loading.value = true;
  try {
    const result = await http.get<Array<{ id: number; name: string; code: string; sort: number; status: number; remark?: string | null }>>("/api/posts");
    positions.value = result.map(normalizePosition).sort((a, b) => a.sort - b.sort || a.name.localeCompare(b.name, "zh-CN"));
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
  Object.assign(form, { name: row.name, codeNumber: row.code, orderNumber: row.sort, status: row.status });
  editorVisible.value = true;
}
async function submitEditor() {
  await formRef.value?.validate();
  submitting.value = true;
  try {
    const payload = { name: form.name, code: form.codeNumber, sort: form.orderNumber, status: form.status === "Activity" ? 1 : 0 };
    if (editingId.value) {
      await http.put(`/api/posts/${editingId.value}`, payload, { payloadMode: "json" });
    } else {
      await http.post("/api/posts", payload, { payloadMode: "json" });
    }
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
    const results = await Promise.allSettled(positions.value.filter(position => checkedRowKeys.value.includes(position.id)).map(position => http.put(`/api/posts/${position.id}`, {
      sort: batch.enabled.orderNumber ? batch.orderNumber : position.sort,
      status: batch.enabled.status ? (batch.status === "Activity" ? 1 : 0) : (position.status === "Activity" ? 1 : 0),
    }, { payloadMode: "json" })));
    const failedCount = results.filter(result => result.status === "rejected").length;
    const successCount = results.length - failedCount;
    await loadPositions();
    if (failedCount > 0) {
      if (successCount > 0) message.warning(`${failedCount} 个岗位更新失败，其余 ${successCount} 个已完成`);
      else message.error("岗位更新全部失败");
      return;
    }
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
      await http.delete(`/api/posts/${row.id}`);
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
      const results = await Promise.allSettled(checkedRowKeys.value.map(id => http.delete(`/api/posts/${id}`)));
      const failedCount = results.filter(result => result.status === "rejected").length;
      const successCount = results.length - failedCount;
      if (successCount > 0) message.success(`已删除 ${successCount} 个岗位`);
      if (failedCount > 0) message.warning(`${failedCount} 个岗位删除失败`);
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





