<template>
  <div v-if="isLoginPage">
    <slot/>
  </div>
  <div v-else class="app-shell">
    <app-sidebar
        :active-page="activePage"
        :collapsed="sidebarCollapsed"
        :mobile-open="mobileSidebarOpen"
        :system-name="systemName"
        @expand="sidebarCollapsed = false"
        @navigate="closeMobileSidebar"
        @settings="handleSettings"
    />
    <button
        v-if="mobileSidebarOpen"
        aria-label="关闭导航菜单"
        class="sidebar-backdrop"
        type="button"
        @click="mobileSidebarOpen = false"
    />

    <div :class="{ 'main--collapsed': sidebarCollapsed }" class="main">
      <header class="topbar">
        <div class="topbar__left">
          <button class="icon-btn" title="折叠菜单" type="button" @click="toggleSidebar">
            <span class="material-icons-outlined">menu</span>
          </button>
          <div aria-label="当前位置" class="breadcrumb">
            <span class="breadcrumb__item">首页</span>
            <span class="material-icons-outlined breadcrumb__sep">chevron_right</span>
            <span class="breadcrumb__item breadcrumb__item--active">{{ activePageLabel }}</span>
          </div>
        </div>
        <div class="topbar__right">
          <label class="search-box">
            <span class="material-icons-outlined">search</span>
            <input v-model="searchQuery" aria-label="搜索功能" placeholder="搜索功能..." type="search">
          </label>
          <button class="icon-btn" title="切换主题" type="button" @click="toggleTheme">
            <span class="material-icons-outlined">{{ isDark ? 'light_mode' : 'dark_mode' }}</span>
          </button>
          <button class="icon-btn badge" title="通知" type="button">
            <span class="material-icons-outlined">notifications_none</span><span class="badge__dot">5</span>
          </button>
          <button class="icon-btn" title="全屏" type="button" @click="toggleFullscreen">
            <span class="material-icons-outlined">fullscreen</span>
          </button>
          <label v-if="showDepartmentSwitcher" :title="`当前工作部门：${authStore.user?.working_department_name || '全部部门'}`"
                 class="dept-switch">
            <span class="material-icons-outlined">apartment</span>
            <select :value="currentDepartmentValue" aria-label="当前工作部门" @change="changeWorkingDepartment">
              <option v-if="canSelectAllDepartments" value="">全部部门</option>
              <option v-for="option in departmentOptions" :key="option.id" :value="String(option.id)">{{
                  option.name
                }}
              </option>
            </select>
          </label>
          <span v-if="departmentError" class="dept-switch__error" role="alert">{{ departmentError }}</span>
          <div class="user-chip">
            <button :aria-expanded="userMenuOpen" class="user-chip__button" type="button"
                    @click="userMenuOpen = !userMenuOpen">
              <span class="avatar">
                <img v-if="userAvatar" :src="userAvatar" alt="">
                <template v-else>{{ userInitial }}</template>
              </span>
              <span class="user-chip__info"><span class="user-chip__name">{{ userName }}</span><span
                  class="user-chip__role">{{ userRole }}</span></span>
              <span class="material-icons-outlined">expand_more</span>
            </button>
            <div v-show="userMenuOpen" class="dropdown-menu user-menu">
              <button class="dropdown-item" type="button" @click="navigate('profile')"><span
                  class="material-icons-outlined">person</span>个人中心
              </button>
              <button class="dropdown-item" type="button" @click="openPasswordTab"><span
                  class="material-icons-outlined">lock</span>修改密码
              </button>
              <button class="dropdown-item" type="button" @click="logout"><span
                  class="material-icons-outlined">logout</span>退出登录
              </button>
            </div>
          </div>
        </div>
      </header>
      <main class="content">
        <slot/>
      </main>
    </div>
  </div>
</template>

<script lang="ts" setup>
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const {isDark, toggle: toggleTheme} = useTheme();
const sidebarCollapsed = ref(false);
const mobileSidebarOpen = ref(false);
const userMenuOpen = ref(false);
const searchQuery = ref("");
const systemName = ref("Admin Pro");
const userName = computed(() => authStore.user?.username || "超级管理员");
const userRole = computed(() => authStore.user?.role || "admin");
const userAvatar = computed(() => authStore.avatar);
const departmentError = ref("");
const departmentOptions = computed(() => authStore.user?.working_department_options ?? []);
/** 只有默认不限部门的角色（平台管理与超级管理员）才能选「全部部门」。 */
const canSelectAllDepartments = computed(() => authStore.user?.scope === "ALL");
/**
 * 工作部门切换器只在存在部门维度时显示。
 * 待改密时必须隐藏：服务端把 /api/auth/working-department 也拦在改密之前（403），
 * 显示一个必然报错的控件只会让人困惑。
 */
const showDepartmentSwitcher = computed(
    () => !authStore.mustChangePassword
        && departmentOptions.value.length > 0
        && (authStore.user?.scope === "DEPARTMENT" || authStore.user?.scope === "ALL"),
);
const currentDepartmentValue = computed(() => (authStore.user?.working_department_id == null ? "" : String(authStore.user.working_department_id)));

async function changeWorkingDepartment(event: Event) {
  const select = event.target as HTMLSelectElement;
  const raw = select.value;
  departmentError.value = "";
  try {
    await authStore.switchWorkingDepartment(raw ? Number(raw) : null);
  } catch (error) {
    // 失败时把控件退回原值，否则界面显示的部门和后端实际生效的部门会不一致。
    select.value = currentDepartmentValue.value;
    departmentError.value = (error as { statusMessage?: string }).statusMessage || "工作部门切换失败";
  }
}


const pageLabels: Record<string, string> = {
  dashboard: "仪表盘",
  users: "用户管理",
  "online-users": "在线用户",
  roles: "角色管理",
  depts: "部门管理",
  posts: "岗位管理",
  "data-transfer": "数据导入导出",
  owners: "车主信息",
  spots: "车位信息",
  plates: "车牌信息",
  "vehicle-inout-requests": "进出申请",
  zones: "停车区域",
  devices: "设备管理",
  "gate-persons": "人员信息",
  "person-records": "人员进出",
  "vehicle-records": "车辆进出",
  violations: "违规管理",
  synchronizations: "数据同步",
  "sync-history": "同步执行历史",
  "system-monitor": "系统监控",
  logs: "日志管理",
  backup: "数据备份",
  about: "关于系统",
  profile: "个人中心",
  password: "修改密码",
};
const routePages: Record<string, string> = {
  "/": "dashboard",
  "/users": "users",
  "/online-users": "online-users",
  "/roles": "roles",
  "/gate-persons": "gate-persons",
  "/devices": "devices",
  "/departments": "depts",
  "/positions": "posts",
  "/data-transfer": "data-transfer",
  "/owners": "owners",
  "/spots": "spots",
  "/plates": "plates",
  "/vehicle-inout-requests": "vehicle-inout-requests",
  "/zones": "zones",
  "/person-records": "person-records",
  "/vehicle-records": "vehicle-records",
  "/violations": "violations",
  "/synchronizations": "synchronizations",
  "/sync-history": "sync-history",
  "/system-monitor": "system-monitor",
  "/logs": "logs",
  "/backup": "backup",
  "/about": "about",
  "/profile": "profile",
};
const pagePaths: Record<string, string> = {
  dashboard: "/",
  users: "/users",
  "online-users": "/online-users",
  roles: "/roles",
  "gate-persons": "/gate-persons",
  devices: "/devices",
  depts: "/departments",
  posts: "/positions",
  "data-transfer": "/data-transfer",
  owners: "/owners",
  spots: "/spots",
  plates: "/plates",
  "vehicle-inout-requests": "/vehicle-inout-requests",
  zones: "/zones",
  "person-records": "/person-records",
  "vehicle-records": "/vehicle-records",
  violations: "/violations",
  synchronizations: "/synchronizations",
  "sync-history": "/sync-history",
  "system-monitor": "/system-monitor",
  logs: "/logs",
  backup: "/backup",
  about: "/about",
  profile: "/profile",
};
// 不使用外壳的页面：登录与配置引导都在会话之外，任何一条侧边栏或顶栏都是多余的。
const isLoginPage = computed(() => route.path === "/login" || route.path === "/setup");
const activePage = computed(() => typeof route.query.page === "string" ? route.query.page : (routePages[route.path] ?? "dashboard"));
const activePageLabel = computed(() => pageLabels[activePage.value] ?? "仪表盘");
const userInitial = computed(() => userName.value.trim().charAt(0).toUpperCase() || "A");

useHead({
  title: () => (route.path === "/setup" ? "系统配置引导" : isLoginPage.value ? "登录" : `${activePageLabel.value} - ${systemName.value}`),
  link: [{rel: "stylesheet", href: "https://fonts.googleapis.com/icon?family=Material+Icons+Outlined"}],
});

function toggleSidebar() {
  if (window.innerWidth <= 768) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value;
    return;
  }
  sidebarCollapsed.value = !sidebarCollapsed.value;
}

function closeMobileSidebar() {
  mobileSidebarOpen.value = false;
}

async function navigate(page: string, routePath?: string) {
  userMenuOpen.value = false;
  closeMobileSidebar();
  const path = routePath || pagePaths[page];
  if (path) {
    await router.replace(path);
    return;
  }
  await router.replace({query: {...route.query, page}});
}

/** 顶栏「修改密码」直接打开个人中心的改密标签。 */
async function openPasswordTab() {
  userMenuOpen.value = false;
  closeMobileSidebar();
  await router.replace({path: "/profile", query: {tab: "password"}});
}

function handleSettings(action: "config" | "backup" | "about") {
  void navigate(action === "config" ? "logs" : action === "backup" ? "backup" : "about");
}

async function toggleFullscreen() {
  if (document.fullscreenElement) {
    await document.exitFullscreen();
  } else {
    await document.documentElement.requestFullscreen();
  }
}

async function logout() {
  userMenuOpen.value = false;
  try {
    await authStore.logout();
  } catch {
    // The local token must be removed even when the server session is already unavailable.
  }
  await router.replace("/login");
}

onMounted(() => {
  const storedSystemName = localStorage.getItem("sysName");
  if (storedSystemName) systemName.value = storedSystemName;
  authStore.restoreSession();
});

// 初始密码未修改时只允许停留在个人中心改密页。
// 标记来自登录响应或 /auth/session，整页刷新时会在会话恢复后才变为 true，
// 因此这里用 immediate + 持续监听，作为路由中间件之外的兜底。
watch(
    () => authStore.mustChangePassword,
    async (required) => {
      if (!import.meta.client || !required || route.path === "/profile") {
        return;
      }
      await router.replace({path: "/profile", query: {tab: "password"}});
    },
    {immediate: true},
);
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) {
  min-height: 100%;
}

.app-shell {
  display: flex;
  min-height: 100dvh;
}

.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin-left: var(--sidebar-w);
  min-height: 100dvh;
  transition: margin-left 0.3s ease;
}

.main--collapsed {
  margin-left: var(--sidebar-w-min);
}

.topbar {
  align-items: center;
  background: var(--card);
  border-bottom: 1px solid var(--border-strong);
  display: flex;
  height: var(--topbar-h);
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 40;
}

.topbar__left, .topbar__right {
  align-items: center;
  display: flex;
}

.topbar__left {
  gap: 16px;
}

.topbar__right {
  gap: 4px;
}

.icon-btn {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  height: 32px;
  justify-content: center;
  position: relative;
  transition: background var(--tr), color var(--tr);
  width: 32px;
}

.icon-btn:hover {
  background: var(--bg);
  color: var(--text);
}

.badge__dot {
  align-items: center;
  background: var(--red);
  border: 1.5px solid var(--card);
  border-radius: 7px;
  color: var(--on-solid);
  display: flex;
  font-size: 9px;
  height: 14px;
  justify-content: center;
  min-width: 14px;
  padding: 0 3px;
  position: absolute;
  right: 2px;
  top: 2px;
}

.breadcrumb {
  align-items: center;
  color: var(--text-mute);
  display: flex;
  font-size: 13px;
  gap: 2px;
}

.breadcrumb__item {
  color: var(--text-sub);
}

.breadcrumb__item--active {
  color: var(--text);
  font-weight: 500;
}

.breadcrumb__sep {
  font-size: 16px;
}

.search-box {
  align-items: center;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  display: flex;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  width: 200px;
}

.search-box .material-icons-outlined {
  color: var(--text-mute);
  font-size: 16px;
}

.search-box input {
  background: transparent;
  border: 0;
  color: var(--text);
  font: inherit;
  min-width: 0;
  outline: none;
  width: 100%;
}

.dept-switch {
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  color: var(--text-sub);
  display: inline-flex;
  gap: 6px;
  height: 32px;
  margin-left: 4px;
  padding: 0 10px;
}

.dept-switch .material-icons-outlined {
  font-size: 16px;
}

.dept-switch select {
  background: transparent;
  border: 0;
  color: var(--text);
  font: inherit;
  font-size: 12px;
  max-width: 150px;
  outline: none;
}

.dept-switch__error {
  color: var(--red);
  font-size: 11px;
  max-width: 180px;
}

.user-chip {
  margin-left: 4px;
  position: relative;
}

.user-chip__button {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 20px;
  color: var(--text-sub);
  cursor: pointer;
  display: flex;
  gap: 8px;
  padding: 3px 8px 3px 3px;
}

.user-chip__button:hover {
  background: var(--bg);
}

.avatar {
  align-items: center;
  background: var(--text);
  border-radius: 50%;
  color: var(--card);
  display: flex;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  height: 28px;
  justify-content: center;
  overflow: hidden;
  width: 28px;
}

.avatar img {
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.user-chip__info {
  display: grid;
  text-align: left;
}

.user-chip__name {
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.2;
}

.user-chip__role {
  color: var(--text-mute);
  font-size: 11px;
}

.dropdown-menu {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-pop);
  min-width: 180px;
  padding: 6px;
  position: absolute;
  z-index: 200;
}

.user-menu {
  margin-top: 6px;
  right: 0;
  top: 100%;
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

.content {
  flex: 1;
  min-width: 0;
}

.sidebar-backdrop {
  background: rgb(0 0 0 / 32%);
  border: 0;
  inset: 0;
  position: fixed;
  z-index: 45;
}

@media (max-width: 768px) {
  .main, .main--collapsed {
    margin-left: 0;
  }

  .topbar {
    padding: 0 16px;
  }

  .search-box {
    display: none;
  }

  .user-chip__role {
    display: none;
  }
}
</style>
