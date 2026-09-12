<template>
  <div class="picker">
    <p v-if="!flatDepartments.length" class="picker__empty">暂无部门可选</p>
    <div v-else class="picker__list" role="group" aria-label="部门管理范围">
      <div v-for="row in flatDepartments" :key="row.department.id" class="picker__row">
        <label class="picker__item" :style="{ paddingLeft: `${12 + row.level * 18}px` }">
          <input
            :checked="isSelected(row.department.id)"
            type="checkbox"
            @change="toggle(row.department)"
          >
          <span class="material-icons-outlined">{{ hasChildren(row.department.id) ? "folder" : "badge" }}</span>
          <span class="picker__name">{{ row.department.name }}</span>
        </label>
        <label v-if="isSelected(row.department.id)" class="picker__descendants">
          <input
            :checked="isSelected(row.department.id)?.includeDescendants === true"
            type="checkbox"
            @change="toggleDescendants(row.department.id)"
          >
          含下级部门
        </label>
      </div>
    </div>
    <p class="picker__hint">
      仅当该用户的角色是「部门管理」时生效：他只能在被勾选的部门之间切换工作部门，看到的数据也限定在这些部门内。
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * 用户部门管理范围的多选树。
 *
 * 范围是**按用户**分配的，不是角色属性：两个部门管理各管自己的部门就需要不同范围，
 * 而把范围放到角色上还会因为角色变更会撤销该角色全部持有者的会话。
 */
interface Department {
  id: number;
  name: string;
  parent?: number | null;
  sort?: number;
}

/** 一个已分配的部门管理范围条目，与后端 ManagedDepartmentRequest 对齐。 */
export interface ManagedDepartment {
  department_id: number;
  department_name: string;
  include_descendants: boolean;
}

const props = defineProps<{
  departments: Department[];
  modelValue: ManagedDepartment[];
}>();

const emit = defineEmits<{
  "update:modelValue": [ManagedDepartment[]];
}>();

const flatDepartments = computed(() => {
  const rows: { department: Department; level: number }[] = [];
  const walk = (parentId: number, level: number) => {
    props.departments
      .filter((department) => (department.parent ?? 0) === parentId)
      .sort((left, right) => (left.sort ?? 0) - (right.sort ?? 0) || left.id - right.id)
      .forEach((department) => {
        rows.push({ department, level });
        walk(department.id, level + 1);
      });
  };
  walk(0, 0);
  return rows;
});

function hasChildren(id: number) {
  return props.departments.some((department) => (department.parent ?? 0) === id);
}

function isSelected(id: number) {
  return props.modelValue.find((item) => item.department_id === id);
}

function toggle(department: Department) {
  const current = props.modelValue.filter((item) => item.department_id !== department.id);
  if (current.length === props.modelValue.length) {
    current.push({ department_id: department.id, department_name: department.name, include_descendants: false });
  }
  // 保持与部门树的显示顺序一致，便于阅读与比对。
  const order = new Map(flatDepartments.value.map((row, index) => [row.department.id, index]));
  current.sort((left, right) => (order.get(left.department_id) ?? 0) - (order.get(right.department_id) ?? 0));
  emit("update:modelValue", current);
}

function toggleDescendants(id: number) {
  emit(
    "update:modelValue",
    props.modelValue.map((item) =>
      item.department_id === id ? { ...item, include_descendants: !item.include_descendants } : item,
    ),
  );
}
</script>

<style scoped>
.picker__list { border: 1px solid var(--border-strong); border-radius: 6px; max-height: 220px; overflow-y: auto; }
.picker__row { align-items: center; border-bottom: 1px solid var(--border); display: flex; gap: 10px; padding: 6px 10px; }
.picker__row:last-child { border-bottom: none; }
.picker__item { align-items: center; cursor: pointer; display: flex; flex: 1; gap: 6px; min-width: 0; }
.picker__item input { accent-color: var(--primary); }
.picker__item .material-icons-outlined { color: var(--text-mute); font-size: 16px; }
.picker__name { color: var(--text); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker__descendants { align-items: center; color: var(--text-sub); cursor: pointer; display: flex; flex: 0 0 auto; font-size: 11px; gap: 4px; }
.picker__descendants input { accent-color: var(--primary); }
.picker__empty, .picker__hint { color: var(--text-mute); font-size: 11px; line-height: 1.6; margin: 0; }
.picker__hint { margin-top: 8px; }
</style>
