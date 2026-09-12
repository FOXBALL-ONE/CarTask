import {TOKEN_COOKIE} from "~/composables/useHttp";

export default defineNuxtRouteMiddleware((to) => {
    if (to.path === "/login") {
        return;
    }

    const authToken = useCookie<string | null>(TOKEN_COOKIE, {
        sameSite: "lax",
        path: "/",
    });

    if (authToken.value?.trim()) {
        const authStore = useAuthStore();
        // 初始密码未修改时只放行个人中心改密页（服务端另有过滤器强制拦截）。
        if (authStore.mustChangePassword && to.path !== "/profile") {
            return navigateTo({path: "/profile", query: {tab: "password"}});
        }
        return;
    }

    return navigateTo("/login");
});
