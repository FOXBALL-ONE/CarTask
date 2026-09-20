import {TOKEN_COOKIE} from "~/composables/useHttp";

/**
 * 路由 → 需要的权限之一（ANY 语义）。
 *
 * 与侧边栏菜单的权限映射保持同一份口径：菜单只是不显示入口，真正的拦截靠这里 + 服务端。
 * 不在这张表里的路由（例如个人中心）不做权限校验。
 */
const routePermissions: Record<string, string[]> = {
    "/": ["dashboard:read"],
    "/users": ["user:read"],
    "/online-users": ["user:read"],
    "/roles": ["role:read"],
    "/departments": ["department:read"],
    "/positions": ["position:read"],
    "/data-transfer": ["user:read", "owner:read", "plate:read"],
    "/owners": ["owner:read"],
    "/spots": ["spot:read"],
    "/plates": ["plate:read"],
    "/vehicle-inout-requests": ["vehicle-inout-request:read"],
    "/zones": ["dictionary:read"],
    "/devices": ["device:read"],
    "/gate-persons": ["gate-person:read"],
    "/person-records": ["person-record:read"],
    "/vehicle-records": ["vehicle-record:read"],
    "/violations": ["violation:read"],
    "/synchronizations": ["dictionary:sync", "vehicle-record:sync", "owner:sync", "account:sync", "plate-sync:reconcile"],
    "/sync-history": ["sync-history:read"],
    "/system-monitor": ["system-monitor:read"],
    "/logs": ["audit:read"],
    "/backup": ["backup:manage"],
};

/**
 * 额外要求担任的角色之一。
 *
 * 与侧边栏同一份口径：部门管理需要 role:read 渲染用户列表的角色列、需要 department:read
 * 画出部门树，但角色配置与部门维护页面本身是平台控制台，不该给他。
 */
const routeRoles: Record<string, string[]> = {
    "/roles": ["SUPER_ADMIN", "ADMIN"],
    "/departments": ["SUPER_ADMIN", "ADMIN"],
    // 备份产物是整库 SQL 加全部附件，权限码之外再卡一道角色，避免有人把 backup:manage 配给别的角色。
    "/backup": ["SUPER_ADMIN"],
};

export default defineNuxtRouteMiddleware(async (to) => {
    // 登录页与配置引导页都在会话之外：引导页要在系统还没有任何账号时就可用。
    if (to.path === "/login" || to.path === "/setup" || to.path === "/commute-routes" || to.path === "/public-announcements") {
        return;
    }

    const authToken = useCookie<string | null>(TOKEN_COOKIE, {
        sameSite: "lax",
        path: "/",
    });
    if (!authToken.value?.trim()) {
        return navigateTo("/login");
    }

    const authStore = useAuthStore();
    // 硬刷新时 authStore.user 仍是 null：它由布局的 onMounted 填充，而中间件跑在它之前。
    // 不先补一次会话就直接读权限，每次刷新都会被判成无权限而弹回首页。
    if (!authStore.user) {
        await authStore.refreshSession().catch(() => null);
    }

    // 初始密码未修改时只放行个人中心改密页（服务端另有过滤器强制拦截）。
    if (authStore.mustChangePassword && to.path !== "/profile") {
        return navigateTo({path: "/profile", query: {tab: "password"}});
    }

    const required = routePermissions[to.path];
    const requiredRoles = routeRoles[to.path];
    // 首页不做拦截：它本身就是无权限时的落点，再拦就会形成重定向死循环。
    if (to.path === "/") {
        return;
    }
    if (required && !required.some((permission) => authStore.user?.permissions?.includes(permission))) {
        return navigateTo("/");
    }
    if (requiredRoles && !requiredRoles.includes(authStore.user?.role ?? "")) {
        return navigateTo("/");
    }
});
