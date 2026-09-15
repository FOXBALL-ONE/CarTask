<template>
  <aside :class="sidebarClass" aria-label="主导航">
    <div
        :class="['flex min-h-[72px] items-center gap-2.5 border-b border-[var(--border-strong)] py-2',
                 collapsed ? 'justify-center px-0 max-[768px]:justify-start max-[768px]:px-4' : 'px-4']"
    >
      <div
          :class="['grid shrink-0 place-items-center rounded-[10px] bg-[var(--primary)] font-bold text-white',
                   collapsed ? 'size-9 text-[18px] max-[768px]:size-10 max-[768px]:text-[20px]' : 'size-10 text-[20px]']"
      >
        {{ logoMark }}
      </div>
      <div :class="['min-w-0 leading-[1.3]', collapsedHidden]">
        <div class="line-clamp-2 break-all text-[16px] font-bold text-[var(--text)]">{{ systemName }}</div>
      </div>
    </div>

    <nav class="flex-1 overflow-y-auto px-2.5 py-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <section v-for="group in navigation" :key="group.title" class="mb-2">
        <h2
            :class="['whitespace-nowrap px-2.5 pt-3 pb-1 text-[11px] font-normal text-[var(--text-mute)]',
                     collapsedGroupTitle]"
        >{{ group.title }}</h2>
        <template v-for="item in group.items" :key="item.label">
          <template v-if="itemVisible(item)">
            <button
                v-if="item.children"
                :aria-expanded="expandedGroups.includes(item.label)"
                :class="[navItemClass(false), navItemPadding, 'mb-px text-[13px]']"
                :title="collapsed ? item.label : undefined"
                type="button"
                @click="toggleGroup(item.label)"
            >
              <span :class="navIconClass">{{ item.icon }}</span>
              <span :class="collapsedHidden">{{ item.label }}</span>
              <span :class="chevronClass(expandedGroups.includes(item.label))">chevron_right</span>
            </button>
            <div v-if="item.children" :class="['overflow-hidden', subListClass(expandedGroups.includes(item.label))]">
              <button
                  v-for="child in visibleChildren(item)"
                  :key="child.page"
                  :class="[navItemClass(activePage === child.page), 'mb-px py-2 pr-2.5 pl-[38px] text-[12px]']"
                  :title="collapsed ? child.label : undefined"
                  type="button"
                  @click="selectPage(child.route)"
              >
                <span :class="navIconClass">{{ child.icon }}</span>
                <span :class="collapsedHidden">{{ child.label }}</span>
              </button>
            </div>
            <button
                v-else
                :class="[navItemClass(activePage === item.page), navItemPadding, 'mb-px text-[13px]']"
                :title="collapsed ? item.label : undefined"
                type="button"
                @click="selectPage(item.route)"
            >
              <span :class="navIconClass">{{ item.icon }}</span>
              <span :class="collapsedHidden">{{ item.label }}</span>
            </button>
          </template>
        </template>
      </section>
    </nav>

    <div class="relative border-t border-[var(--border)] p-2.5">
      <button
          :aria-expanded="settingsOpen"
          :class="[navItemClass(false), navItemPadding, 'mb-0 text-[13px]']"
          :title="collapsed ? '系统设置' : undefined"
          type="button"
          @click="settingsOpen = !settingsOpen"
      >
        <span :class="navIconClass">settings</span>
        <span :class="collapsedHidden">系统设置</span>
      </button>
      <div
          v-show="settingsOpen && !collapsed"
          class="absolute bottom-2.5 left-[calc(100%+8px)] z-[200] min-w-[180px] rounded-lg border border-[var(--border)] bg-[var(--card)] p-1.5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
      >
        <button :class="dropdownItemClass" type="button" @click="emit('settings', 'config')">
          <span class="material-icons-outlined text-[18px] leading-none text-[var(--text-sub)]">build</span>系统配置
        </button>
        <button v-if="isSuperAdmin" :class="dropdownItemClass" type="button" @click="emit('settings', 'backup')">
          <span class="material-icons-outlined text-[18px] leading-none text-[var(--text-sub)]">storage</span>数据备份
        </button>
        <button :class="dropdownItemClass" type="button" @click="emit('settings', 'about')">
          <span class="material-icons-outlined text-[18px] leading-none text-[var(--text-sub)]">info</span>关于系统
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
  /** 需要的权限之一（ANY 语义）；缺省表示无需权限。 */
  permissions?: string[];
  /**
   * 额外要求担任的角色之一。
   *
   * 用于"有读权限但页面本身是平台控制台"的情况：部门管理需要 role:read 才能渲染用户列表的
   * 角色列、需要 department:read 才能画出部门树，但他不该看到角色配置与部门维护页面。
   */
  roles?: string[];
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
      {label: "仪表盘", icon: "grid_view", page: "dashboard", route: "/", permissions: ["dashboard:read"]},
      {label: "用户管理", icon: "group", page: "users", route: "/users", permissions: ["user:read"]},
      {label: "在线用户", icon: "sensors", page: "online-users", route: "/online-users", permissions: ["user:read"]},
      {
        label: "角色管理",
        icon: "verified_user",
        page: "roles",
        route: "/roles",
        permissions: ["role:read"],
        roles: ["SUPER_ADMIN", "ADMIN"]
      },
      {
        label: "部门管理",
        icon: "account_tree",
        page: "depts",
        route: "/departments",
        permissions: ["department:read"],
        roles: ["SUPER_ADMIN", "ADMIN"]
      },
      {label: "岗位管理", icon: "badge", page: "posts", route: "/positions", permissions: ["position:read"]},
      {
        label: "数据导入导出",
        icon: "swap_vert",
        page: "data-transfer",
        route: "/data-transfer",
        permissions: ["user:read", "owner:read", "plate:read"]
      },
    ],
  },
  {
    title: "车辆信息",
    items: [{
      label: "车辆信息",
      icon: "directions_car",
      children: [
        {label: "车主信息", icon: "person", page: "owners", route: "/owners", permissions: ["owner:read"]},
        {label: "车位信息", icon: "local_parking", page: "spots", route: "/spots", permissions: ["spot:read"]},
        {label: "车牌信息", icon: "pin_drop", page: "plates", route: "/plates", permissions: ["plate:read"]},
        {
          label: "进出申请",
          icon: "assignment_turned_in",
          page: "vehicle-inout-requests",
          route: "/vehicle-inout-requests",
          permissions: ["vehicle-inout-request:read"]
        },
        {label: "停车区域", icon: "map", page: "zones", route: "/zones", permissions: ["dictionary:read"]},
      ],
    }],
  },
  {
    title: "设备管理",
    items: [{label: "设备管理", icon: "router", page: "devices", route: "/devices", permissions: ["device:read"]}]
  },
  {
    title: "门禁管理",
    items: [{
      label: "门禁管理",
      icon: "door_front",
      children: [{
        label: "人员信息",
        icon: "badge",
        page: "gate-persons",
        route: "/gate-persons",
        permissions: ["gate-person:read"]
      }],
    }],
  },
  {
    title: "进出记录",
    items: [{
      label: "进出记录",
      icon: "swap_horiz",
      children: [
        {
          label: "人员进出",
          icon: "directions_walk",
          page: "person-records",
          route: "/person-records",
          permissions: ["person-record:read"]
        },
        {
          label: "车辆进出",
          icon: "directions_car",
          page: "vehicle-records",
          route: "/vehicle-records",
          permissions: ["vehicle-record:read"]
        },
      ],
    }],
  },
  {
    title: "秩序管理",
    items: [{
      label: "违规管理",
      icon: "gavel",
      page: "violations",
      route: "/violations",
      permissions: ["violation:read"]
    }]
  },
  {
    title: "系统",
    items: [
      {
        label: "数据同步",
        icon: "sync_alt",
        page: "synchronizations",
        route: "/synchronizations",
        permissions: ["dictionary:sync", "vehicle-record:sync", "owner:sync", "account:sync"],
      },
      {
        label: "同步历史",
        icon: "history",
        page: "sync-history",
        route: "/sync-history",
        permissions: ["sync-history:read"],
      },
      {
        label: "系统监控",
        icon: "monitor_heart",
        page: "system-monitor",
        route: "/system-monitor",
        permissions: ["system-monitor:read"],
      },
      {label: "日志管理", icon: "receipt_long", page: "logs", route: "/logs", permissions: ["audit:read"]},
    ],
  },
];

const expandedGroups = ref<string[]>([]);
const settingsOpen = ref(false);
const authStore = useAuthStore();
const logoMark = computed(() => props.systemName.trim().charAt(0).toUpperCase() || "A");

/**
 * 数据备份只对超级管理员开放。
 *
 * 与路由中间件（routeRoles）、后端 @PreAuthorize 保持同一口径：这里只是不显示入口，
 * 真正的拦截在服务端，改一个角色名不该成为拿到整库备份的唯一障碍。
 */
const isSuperAdmin = computed(() => authStore.user?.role === "SUPER_ADMIN");

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
  if (item.children?.length) {
    // 父项没有自己的权限时，只要还有可见子项就保留；全部子项不可见时整组隐藏，
    // 否则会渲染出一个点开是空的展开组。
    return visibleChildren(item).length > 0;
  }
  return matchesPermissions(item) && matchesRoles(item);
}

function visibleChildren(item: NavigationItem) {
  return (item.children ?? []).filter(itemVisible);
}

function matchesPermissions(item: NavigationItem) {
  return !item.permissions?.length
      || item.permissions.some((permission) => authStore.user?.permissions?.includes(permission) === true);
}

function matchesRoles(item: NavigationItem) {
  if (!item.roles?.length) {
    return true;
  }
  const role = authStore.user?.role;
  return typeof role === "string" && item.roles.includes(role);
}

async function selectPage(route?: string) {
  settingsOpen.value = false;
  emit("navigate");
  if (route) {
    await navigateTo(route);
  }
}

/**
 * 侧栏固定在视口左侧，折叠只改宽度；≤768px 时改为抽屉，靠水平位移滑入滑出。
 * 断点用 max-[768px]（含 768px）以对齐布局里 toggleSidebar 的 `innerWidth <= 768` 判断。
 */
const sidebarClass = computed(() => [
  "fixed inset-y-0 left-0 z-50 flex flex-col border-r border-[var(--border-strong)] bg-[var(--card)] transition-[width,transform] duration-300 ease-[ease]",
  props.collapsed ? "w-[var(--sidebar-w-min)] max-[768px]:w-[var(--sidebar-w)]" : "w-[var(--sidebar-w)]",
  props.mobileOpen
      ? "max-[768px]:translate-x-0 max-[768px]:shadow-[8px_0_24px_rgba(0,0,0,0.12)]"
      : "max-[768px]:-translate-x-full",
]);

// 字号一律写成任意值形式：Tailwind 的 text-xs/text-base 会连同行高一起写死，
// 而这里的行高由 body 继承（1.5 倍），写死行高会改变导航项的高度。
const navIconClass = "material-icons-outlined shrink-0 text-[18px] leading-none";
const dropdownItemClass = "flex w-full cursor-pointer items-center gap-2 rounded-md border-0 bg-transparent px-3 py-2 text-left text-[13px] text-[var(--text)] hover:bg-[var(--bg)]";

/** 折叠时隐藏文字，移动端抽屉里要还原成展开态的排版。 */
const collapsedHidden = computed(() => props.collapsed ? "hidden max-[768px]:block" : "");
// 分组标题不是 flex 子项，原型在移动端用的是 display: initial，对 h2 来说即 inline，
// 这里用 inline 保持同样的盒类型（改用 block 会让每组高出 13px）。
const collapsedGroupTitle = computed(() => props.collapsed ? "hidden max-[768px]:inline" : "");

const chevronClass = (open: boolean) => [
  "material-icons-outlined ml-auto text-[16px] leading-none transition-transform duration-200 ease-[ease]",
  open ? "rotate-90" : "",
  collapsedHidden.value,
];

/** 折叠时整组收起；展开态下才由 open 决定，移动端抽屉同理（折叠时也不展开子项）。 */
const subListClass = (open: boolean) => props.collapsed || !open ? "hidden" : "block";

/** 展开态与折叠态只在居中与内边距上不同；padding 放在这里而不是 navItemClass，避免同一属性出现两个 class。 */
const navItemPadding = computed(() => props.collapsed
    ? "justify-center p-2.5 max-[768px]:justify-start max-[768px]:px-2.5 max-[768px]:py-2"
    : "px-2.5 py-2");

const navItemBase = "relative flex w-full cursor-pointer items-center gap-2.5 rounded-md border-0 text-left whitespace-nowrap transition-[background-color,color] duration-200 ease-[ease]";
// 不做选中态时才能挂 hover 类，所以 bg-transparent 也只放在这里：Tailwind 把 bg-transparent
// 排在 bg-[var(--primary-soft)] 之后，两者若同时存在，选中项的底色会被它盖掉。
const navItemIdle = "bg-transparent text-[var(--text-sub)] hover:bg-[var(--bg)] hover:text-[var(--text)]";
// 选中指示条：展开态贴在左侧，折叠态是底部横条，移动端再还原成左侧竖条。
const activeBar = computed(() => props.collapsed
    ? "before:absolute before:content-[''] before:bg-[var(--primary)] before:bottom-0 before:left-[20%] before:h-[3px] before:w-[60%] before:rounded-t-[2px] max-[768px]:before:top-[20%] max-[768px]:before:bottom-auto max-[768px]:before:left-0 max-[768px]:before:h-[60%] max-[768px]:before:w-[3px] max-[768px]:before:rounded-tl-none max-[768px]:before:rounded-r-[2px]"
    : "before:absolute before:content-[''] before:bg-[var(--primary)] before:top-[20%] before:left-0 before:h-[60%] before:w-[3px] before:rounded-r-[2px]");

/** 选中项自带底色，所以不能再挂 hover 类：Tailwind 的 hover 规则排在普通工具类之后，会盖掉选中底色。 */
function navItemClass(active: boolean) {
  return active
      ? `${navItemBase} bg-[var(--primary-soft)] font-medium text-[var(--primary)] ${activeBar.value}`
      : `${navItemBase} ${navItemIdle}`;
}
</script>
