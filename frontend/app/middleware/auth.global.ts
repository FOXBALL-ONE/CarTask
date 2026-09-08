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
        return;
    }

    return navigateTo("/login");
});
