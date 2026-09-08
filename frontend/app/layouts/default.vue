<template>
  <div v-if="isLoginPage"><slot /></div>
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
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭导航菜单"
      @click="mobileSidebarOpen = false"
    />

    <div class="main" :class="{ 'main--collapsed': sidebarCollapsed }">
      <header class="topbar">
        <div class="topbar__left">
          <button class="icon-btn" type="button" title="折叠菜单" @click="toggleSidebar">
            <span class="material-icons-outlined">menu</span>
          </button>
          <div class="breadcrumb" aria-label="当前位置">
            <span class="breadcrumb__item">首页</span>
            <span class="material-icons-outlined breadcrumb__sep">chevron_right</span>
            <span class="breadcrumb__item breadcrumb__item--active">{{ activePageLabel }}</span>
          </div>
        </div>
        <div class="topbar__right">
          <label class="search-box">
            <span class="material-icons-outlined">search</span>
            <input v-model="searchQuery" type="search" placeholder="搜索功能..." aria-label="搜索功能">
          </label>
          <button class="icon-btn" type="button" title="切换主题" @click="toggleTheme">
            <span class="material-icons-outlined">{{ darkTheme ? 'light_mode' : 'dark_mode' }}</span>
          </button>
          <button class="icon-btn badge" type="button" title="通知">
            <span class="material-icons-outlined">notifications_none</span><span class="badge__dot">5</span>
          </button>
          <button class="icon-btn" type="button" title="全屏" @click="toggleFullscreen">
            <span class="material-icons-outlined">fullscreen</span>
          </button>
          <div class="user-chip">
            <button class="user-chip__button" type="button" :aria-expanded="userMenuOpen" @click="userMenuOpen = !userMenuOpen">
              <span class="avatar">{{ userInitial }}</span>
              <span class="user-chip__info"><span class="user-chip__name">{{ userName }}</span><span class="user-chip__role">{{ userRole }}</span></span>
              <span class="material-icons-outlined">expand_more</span>
            </button>
            <div v-show="userMenuOpen" class="dropdown-menu user-menu">
              <button type="button" class="dropdown-item" @click="navigate('profile')"><span class="material-icons-outlined">person</span>个人中心</button>
              <button type="button" class="dropdown-item" @click="navigate('password')"><span class="material-icons-outlined">lock</span>修改密码</button>
              <button type="button" class="dropdown-item" @click="logout"><span class="material-icons-outlined">logout</span>退出登录</button>
            </div>
          </div>
        </div>
      </header>
      <main class="content"><slot /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const sidebarCollapsed = ref(false);
const mobileSidebarOpen = ref(false);
const darkTheme = ref(false);
const userMenuOpen = ref(false);
const searchQuery = ref("");
const systemName = ref("Admin Pro");
const userName = computed(() => authStore.user?.username || "超级管理员");
const userRole = computed(() => authStore.user?.role || "admin");

const pageLabels: Record<string, string> = {
  dashboard: "仪表盘", users: "用户管理", roles: "角色管理", depts: "部门管理", posts: "岗位管理",
  owners: "车主信息", spots: "车位信息", plates: "车牌信息", devices: "设备管理", "gate-persons": "人员信息",
  "person-records": "人员进出", "vehicle-records": "车辆进出", logs: "日志管理", profile: "个人中心", password: "修改密码",
};
const routePages: Record<string, string> = {
  "/": "dashboard",
  "/users": "users",
  "/roles": "roles",
  "/gate-persons": "gate-persons",
  "/devices": "devices",
  "/departments": "depts",
  "/positions": "posts",
  "/owners": "owners",
  "/spots": "spots",
  "/plates": "plates",
  "/person-records": "person-records",
  "/vehicle-records": "vehicle-records",
  "/logs": "logs",
};
const pagePaths: Record<string, string> = {
  dashboard: "/",
  users: "/users",
  roles: "/roles",
  "gate-persons": "/gate-persons",
  devices: "/devices",
  depts: "/departments",
  posts: "/positions",
  owners: "/owners",
  spots: "/spots",
  plates: "/plates",
  "person-records": "/person-records",
  "vehicle-records": "/vehicle-records",
  logs: "/logs",
};
const isLoginPage = computed(() => route.path === "/login");
const activePage = computed(() => typeof route.query.page === "string" ? route.query.page : (routePages[route.path] ?? "dashboard"));
const activePageLabel = computed(() => pageLabels[activePage.value] ?? "仪表盘");
const userInitial = computed(() => userName.value.trim().charAt(0).toUpperCase() || "A");

useHead({
  title: () => isLoginPage.value ? "登录" : `${activePageLabel.value} - ${systemName.value}`,
  link: [{ rel: "stylesheet", href: "https://fonts.googleapis.com/icon?family=Material+Icons+Outlined" }],
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
  await router.replace({ query: { ...route.query, page } });
}

function handleSettings(action: "config" | "backup" | "about") {
  void navigate(action === "config" ? "logs" : "dashboard");
}

function toggleTheme() {
  darkTheme.value = !darkTheme.value;
  document.documentElement.dataset.theme = darkTheme.value ? "dark" : "light";
  localStorage.setItem("theme", darkTheme.value ? "dark" : "light");
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
  const storedTheme = localStorage.getItem("theme");
  if (storedSystemName) systemName.value = storedSystemName;
  darkTheme.value = storedTheme === "dark";
  document.documentElement.dataset.theme = darkTheme.value ? "dark" : "light";
  authStore.restoreSession();
});
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) { min-height: 100%; }
:global(body) { background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; font-size: 13px; }
:global([data-theme="light"]), .app-shell { --primary: #2563eb; --primary-soft: #eff4ff; --bg: #fafafa; --card: #fff; --text: #18181b; --text-sub: #71717a; --text-mute: #a1a1aa; --border: #f0f0f0; --border-strong: #e4e4e7; --red: #dc2626; --sidebar-w: 220px; --sidebar-w-min: 64px; --topbar-h: 56px; --tr: 0.2s ease; }
:global([data-theme="dark"]) { --primary: #60a5fa; --primary-soft: #172554; --bg: #18181b; --card: #27272a; --text: #fafafa; --text-sub: #d4d4d8; --text-mute: #a1a1aa; --border: #3f3f46; --border-strong: #52525b; --red: #f87171; }
.app-shell { display: flex; min-height: 100dvh; }
.main { display: flex; flex: 1; flex-direction: column; margin-left: var(--sidebar-w); min-height: 100dvh; transition: margin-left 0.3s ease; }
.main--collapsed { margin-left: var(--sidebar-w-min); }
.topbar { align-items: center; background: var(--card); border-bottom: 1px solid var(--border-strong); display: flex; height: var(--topbar-h); justify-content: space-between; padding: 0 24px; position: sticky; top: 0; z-index: 40; }
.topbar__left, .topbar__right { align-items: center; display: flex; }
.topbar__left { gap: 16px; }
.topbar__right { gap: 4px; }
.icon-btn { align-items: center; background: transparent; border: 0; border-radius: 6px; color: var(--text-sub); cursor: pointer; display: flex; height: 32px; justify-content: center; position: relative; transition: background var(--tr), color var(--tr); width: 32px; }
.icon-btn:hover { background: var(--bg); color: var(--text); }
.badge__dot { align-items: center; background: var(--red); border: 1.5px solid var(--card); border-radius: 7px; color: #fff; display: flex; font-size: 9px; height: 14px; justify-content: center; min-width: 14px; padding: 0 3px; position: absolute; right: 2px; top: 2px; }
.breadcrumb { align-items: center; color: var(--text-mute); display: flex; font-size: 13px; gap: 2px; }
.breadcrumb__item { color: var(--text-sub); }
.breadcrumb__item--active { color: var(--text); font-weight: 500; }
.breadcrumb__sep { font-size: 16px; }
.search-box { align-items: center; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; display: flex; gap: 6px; height: 32px; padding: 0 10px; width: 200px; }
.search-box .material-icons-outlined { color: var(--text-mute); font-size: 16px; }
.search-box input { background: transparent; border: 0; color: var(--text); font: inherit; min-width: 0; outline: none; width: 100%; }
.user-chip { margin-left: 4px; position: relative; }
.user-chip__button { align-items: center; background: transparent; border: 0; border-radius: 20px; color: var(--text-sub); cursor: pointer; display: flex; gap: 8px; padding: 3px 8px 3px 3px; }
.user-chip__button:hover { background: var(--bg); }
.avatar { align-items: center; background: var(--text); border-radius: 50%; color: var(--card); display: flex; flex-shrink: 0; font-size: 12px; font-weight: 600; height: 28px; justify-content: center; width: 28px; }
.user-chip__info { display: grid; text-align: left; }
.user-chip__name { color: var(--text); font-size: 13px; font-weight: 500; line-height: 1.2; }
.user-chip__role { color: var(--text-mute); font-size: 11px; }
.dropdown-menu { background: var(--card); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 8px 24px rgb(0 0 0 / 12%); min-width: 180px; padding: 6px; position: absolute; z-index: 200; }
.user-menu { margin-top: 6px; right: 0; top: 100%; }
.dropdown-item { align-items: center; background: transparent; border: 0; border-radius: 6px; color: var(--text); cursor: pointer; display: flex; font: inherit; font-size: 13px; gap: 8px; padding: 8px 12px; text-align: left; width: 100%; }
.dropdown-item:hover { background: var(--bg); }
.dropdown-item .material-icons-outlined { color: var(--text-sub); font-size: 18px; }
.content { flex: 1; min-width: 0; }
.sidebar-backdrop { background: rgb(0 0 0 / 32%); border: 0; inset: 0; position: fixed; z-index: 45; }
@media (max-width: 768px) {
  .main, .main--collapsed { margin-left: 0; }
  .topbar { padding: 0 16px; }
  .search-box { display: none; }
  .user-chip__role { display: none; }
}
</style>
