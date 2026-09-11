<template>
  <aside
      :class="{ 'sidebar--collapsed': collapsed, 'mobile-open': mobileOpen }"
      aria-label="主导航"
      class="sidebar"
  >
    <div class="sidebar__logo">
      <div class="logo__mark">{{ logoMark }}</div>
      <div class="logo__text">
        <div class="logo__title">{{ systemName }}</div>
      </div>
    </div>

    <nav class="sidebar__nav">
      <section v-for="group in navigation" :key="group.title" class="nav__group">
        <h2 class="nav__group-title">{{ group.title }}</h2>
        <template v-for="item in group.items" :key="item.label">
          <template v-if="itemVisible(item)">
            <button
                v-if="item.children"
                :aria-expanded="expandedGroups.includes(item.label)"
                :class="{ open: expandedGroups.includes(item.label) }"
                :title="collapsed ? item.label : undefined"
                class="nav__item nav__parent"
                type="button"
                @click="toggleGroup(item.label)"
            >
              <span class="material-icons-outlined nav__icon">{{ item.icon }}</span>
              <span class="nav__label">{{ item.label }}</span>
              <span class="material-icons-outlined nav__chevron">chevron_right</span>
            </button>
            <div v-if="item.children" :class="{ open: expandedGroups.includes(item.label) }" class="nav__sub">
              <button
                  v-for="child in item.children"
                  :key="child.page"
                  :class="{ active: activePage === child.page }"
                  :title="collapsed ? child.label : undefined"
                  class="nav__item"
                  type="button"
                  @click="selectPage(child.route)"
              >
                <span class="material-icons-outlined nav__icon">{{ child.icon }}</span>
                <span class="nav__label">{{ child.label }}</span>
              </button>
            </div>
            <button
                v-else
                :class="{ active: activePage === item.page }"
                :title="collapsed ? item.label : undefined"
                class="nav__item"
                type="button"
                @click="selectPage(item.route)"
            >
              <span class="material-icons-outlined nav__icon">{{ item.icon }}</span>
              <span class="nav__label">{{ item.label }}</span>
            </button>
          </template>
        </template>
      </section>
    </nav>

    <div class="sidebar__footer">
      <button
          :aria-expanded="settingsOpen"
          :title="collapsed ? '系统设置' : undefined"
          class="nav__item"
          type="button"
          @click="settingsOpen = !settingsOpen"
      >
        <span class="material-icons-outlined nav__icon">settings</span>
        <span class="nav__label">系统设置</span>
      </button>
      <div v-show="settingsOpen && !collapsed" class="dropdown-menu settings-menu">
        <button class="dropdown-item" type="button" @click="emit('settings', 'config')">
          <span class="material-icons-outlined">build</span>系统配置
        </button>
        <button class="dropdown-item" type="button" @click="emit('settings', 'backup')">
          <span class="material-icons-outlined">storage</span>数据备份
        </button>
        <button class="dropdown-item" type="button" @click="emit('settings', 'about')">
          <span class="material-icons-outlined">info</span>关于系统
        </button>
      </div>
    </div>
  </aside>
</template>

<script lang="ts" setup>
interface NavigationItem {
  label: string;
  icon: string;
  page?: string;
  route?: string;
  permission?: string;
  children?: NavigationItem[];
}

const props = withDefaults(defineProps<{
  activePage?: string;
  collapsed?: boolean;
  mobileOpen?: boolean;
  systemName?: string;
}>(), {
  activePage: "dashboard",
  collapsed: false,
  mobileOpen: false,
  systemName: "Admin Pro",
});

const emit = defineEmits<{
  navigate: [];
  expand: [];
  settings: [action: "config" | "backup" | "about"];
}>();

const navigation: { title: string; items: NavigationItem[] }[] = [
  {
    title: "主菜单",
    items: [
      {label: "仪表盘", icon: "grid_view", page: "dashboard", route: "/"},
      {label: "用户管理", icon: "group", page: "users", route: "/users"},
      {label: "角色管理", icon: "verified_user", page: "roles", route: "/roles"},
      {label: "部门管理", icon: "account_tree", page: "depts", route: "/departments"},
      {label: "岗位管理", icon: "badge", page: "posts", route: "/positions"},
    ],
  },
  {
    title: "车辆信息",
    items: [{
      label: "车辆信息",
      icon: "directions_car",
      children: [
        {label: "车主信息", icon: "person", page: "owners", route: "/owners"},
        {label: "车位信息", icon: "local_parking", page: "spots", route: "/spots"},
        {label: "车牌信息", icon: "pin_drop", page: "plates", route: "/plates"},
      ],
    }],
  },
  {title: "设备管理", items: [{label: "设备管理", icon: "router", page: "devices", route: "/devices"}]},
  {
    title: "门禁管理",
    items: [{
      label: "门禁管理",
      icon: "door_front",
      children: [{label: "人员信息", icon: "badge", page: "gate-persons", route: "/gate-persons"}],
    }],
  },
  {
    title: "进出记录",
    items: [{
      label: "进出记录",
      icon: "swap_horiz",
      children: [
        {label: "人员进出", icon: "directions_walk", page: "person-records", route: "/person-records"},
        {label: "车辆进出", icon: "directions_car", page: "vehicle-records", route: "/vehicle-records"},
      ],
    }],
  },
  {
    title: "系统",
    items: [
      {
        label: "系统监控",
        icon: "monitor_heart",
        page: "system-monitor",
        route: "/system-monitor",
        permission: "system-monitor:read"
      },
      {label: "日志管理", icon: "receipt_long", page: "logs", route: "/logs"},
    ],
  },
];

const expandedGroups = ref<string[]>([]);
const settingsOpen = ref(false);
const authStore = useAuthStore();
const logoMark = computed(() => props.systemName.trim().charAt(0).toUpperCase() || "A");

watch(() => props.activePage, (page) => {
  for (const group of navigation) {
    for (const item of group.items) {
      if (item.children?.some((child) => child.page === page) && !expandedGroups.value.includes(item.label)) {
        expandedGroups.value.push(item.label);
      }
    }
  }
}, {immediate: true});

function toggleGroup(label: string) {
  if (props.collapsed) {
    emit("expand");
  }

  expandedGroups.value = expandedGroups.value.includes(label)
      ? expandedGroups.value.filter((group) => group !== label)
      : [...expandedGroups.value, label];
}

function itemVisible(item: NavigationItem) {
  return !item.permission || authStore.user?.permissions?.includes(item.permission) === true;
}

async function selectPage(route?: string) {
  settingsOpen.value = false;
  emit("navigate");
  if (route) {
    await navigateTo(route);
  }
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  background: var(--card);
  border-right: 1px solid var(--border-strong);
  display: flex;
  flex-direction: column;
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 50;
  transition: width 0.3s ease, transform 0.3s ease;
}

.sidebar--collapsed {
  width: var(--sidebar-w-min);
}

.sidebar__logo {
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border-strong);
}

.logo__mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}

.logo__text {
  min-width: 0;
  line-height: 1.3;
}

.logo__title {
  color: var(--text);
  display: -webkit-box;
  font-size: 16px;
  font-weight: 700;
  overflow: hidden;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.sidebar--collapsed .logo__text, .sidebar--collapsed .nav__label, .sidebar--collapsed .nav__group-title, .sidebar--collapsed .nav__chevron {
  display: none;
}

.sidebar--collapsed .logo__mark {
  width: 36px;
  height: 36px;
  font-size: 18px;
}

.sidebar--collapsed .sidebar__logo {
  justify-content: center;
  padding: 8px 0;
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px;
  scrollbar-width: none;
}

.sidebar__nav::-webkit-scrollbar {
  display: none;
}

.nav__group {
  margin-bottom: 8px;
}

.nav__group-title {
  color: var(--text-mute);
  font-size: 11px;
  font-weight: 400;
  padding: 12px 10px 4px;
  white-space: nowrap;
}

.nav__item {
  width: 100%;
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  font: inherit;
  font-size: 13px;
  gap: 10px;
  margin-bottom: 1px;
  padding: 8px 10px;
  position: relative;
  text-align: left;
  transition: background var(--tr), color var(--tr);
  white-space: nowrap;
}

.nav__item:hover {
  background: var(--bg);
  color: var(--text);
}

.nav__item.active {
  background: var(--primary-soft);
  color: var(--primary);
  font-weight: 500;
}

.nav__item.active::before {
  background: var(--primary);
  border-radius: 0 2px 2px 0;
  content: "";
  height: 60%;
  left: 0;
  position: absolute;
  top: 20%;
  width: 3px;
}

.nav__icon {
  flex-shrink: 0;
  font-size: 18px;
  line-height: 1;
}

.nav__chevron {
  font-size: 16px;
  margin-left: auto;
  transition: transform 0.2s;
}

.nav__parent.open .nav__chevron {
  transform: rotate(90deg);
}

.nav__sub {
  display: none;
  overflow: hidden;
}

.nav__sub.open {
  display: block;
}

.nav__sub .nav__item {
  font-size: 12px;
  padding-left: 38px;
}

.sidebar--collapsed .nav__sub {
  display: none;
}

.sidebar--collapsed .nav__item {
  justify-content: center;
  padding: 10px;
}

.sidebar--collapsed .nav__item.active::before {
  border-radius: 2px 2px 0 0;
  bottom: 0;
  height: 3px;
  left: 20%;
  top: auto;
  width: 60%;
}

.sidebar__footer {
  border-top: 1px solid var(--border);
  padding: 10px;
  position: relative;
}

.sidebar__footer .nav__item {
  margin-bottom: 0;
}

.dropdown-menu {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  bottom: 10px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
  min-width: 180px;
  padding: 6px;
  position: absolute;
  z-index: 200;
}

.settings-menu {
  left: calc(100% + 8px);
}

.dropdown-item {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: var(--text);
  cursor: pointer;
  display: flex;
  font: inherit;
  font-size: 13px;
  gap: 8px;
  padding: 8px 12px;
  text-align: left;
  width: 100%;
}

.dropdown-item:hover {
  background: var(--bg);
}

.dropdown-item .material-icons-outlined {
  color: var(--text-sub);
  font-size: 18px;
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
    width: var(--sidebar-w);
  }

  .sidebar.mobile-open {
    box-shadow: 8px 0 24px rgb(0 0 0 / 12%);
    transform: translateX(0);
  }

  .sidebar--collapsed .logo__text, .sidebar--collapsed .nav__label, .sidebar--collapsed .nav__group-title, .sidebar--collapsed .nav__chevron {
    display: initial;
  }

  .sidebar--collapsed .sidebar__logo {
    justify-content: flex-start;
    padding: 8px 16px;
  }

  .sidebar--collapsed .logo__mark {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .sidebar--collapsed .nav__item {
    justify-content: flex-start;
    padding: 8px 10px;
  }

  .sidebar--collapsed .nav__item.active::before {
    border-radius: 0 2px 2px 0;
    bottom: auto;
    height: 60%;
    left: 0;
    top: 20%;
    width: 3px;
  }

  .sidebar--collapsed .nav__sub {
    display: none;
  }
}
</style>
