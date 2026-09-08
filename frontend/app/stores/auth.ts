import {defineStore} from "pinia";
import {TOKEN_COOKIE, useHttp} from "~/composables/useHttp";

export interface AuthUser {
    user_id: number;
    username: string;
    role: string;
}

export interface LoginResponse {
    access_token: string;
    expires_at: string;
    user: AuthUser;
}

interface LoginPayload {
    username: string;
    password: string;
    captchaToken: string;
    captchaAnswer: string;
}

interface LoginError {
    statusMessage?: string;
    data?: {
        retry_after?: number;
        transport_failure?: boolean;
    };
}

interface CaptchaResponse {
    token: string;
    image: string;
}

export const useAuthStore = defineStore("auth", () => {
    const http = useHttp();
    const token = useCookie<string | null>(TOKEN_COOKIE, {
        sameSite: "lax",
        path: "/",
    });
    const user = ref<AuthUser | null>(null);
    const expiresAt = ref<string | null>(null);
    const captchaToken = ref("");
    const captchaImage = ref("");
    const captchaFailed = ref(false);
    const captchaLoading = ref(false);
    const loading = ref(false);
    const errorMessage = ref("");
    const isAuthenticated = computed(() => Boolean(token.value));

    async function refreshCaptcha() {
        captchaFailed.value = false;
        captchaLoading.value = true;

        try {
            const data = await http.get<CaptchaResponse>("/auth/captcha");
            const token = data.token?.trim();
            const image = data.image?.trim();
            if (!token || !image.startsWith("data:image/")) {
                throw new Error("验证码响应无效");
            }
            captchaToken.value = token;
            captchaImage.value = image;
        } catch {
            captchaToken.value = "";
            captchaImage.value = "";
            captchaFailed.value = true;
        } finally {
            captchaLoading.value = false;
        }
    }

    async function login(payload: Omit<LoginPayload, "captchaToken">) {
        loading.value = true;
        errorMessage.value = "";

        try {
            const data = await http.post<LoginResponse, LoginPayload>("/auth/login", {
                ...payload,
                captchaToken: captchaToken.value,
            });
            token.value = data.access_token;
            user.value = data.user;
            expiresAt.value = data.expires_at;

            if (import.meta.client) {
                sessionStorage.setItem("loginUser", JSON.stringify(data));
            }

            return data;
        } catch (error: unknown) {
            const loginError = error as LoginError;
            const retryAfter = loginError.data?.retry_after;
            const retryMessage = typeof retryAfter === "number" && retryAfter > 0
                ? `，请 ${retryAfter} 秒后重试`
                : "";

            errorMessage.value = loginError.data?.transport_failure
                ? "网络请求失败，请检查服务器是否启动"
                : `${loginError.statusMessage || "登录失败"}${retryMessage}`;
            throw error;
        } finally {
            loading.value = false;
        }
    }

    function restoreSession() {
        if (!import.meta.client || !token.value || user.value) {
            return;
        }

        const stored = sessionStorage.getItem("loginUser");
        if (!stored) {
            return;
        }

        try {
            const data = JSON.parse(stored) as Partial<LoginResponse>;
            if (data.user) {
                user.value = data.user;
                expiresAt.value = data.expires_at ?? null;
            }
        } catch {
            sessionStorage.removeItem("loginUser");
        }
    }

    async function logout() {
        try {
            if (token.value) {
                await http.post("/auth/logout");
            }
        } finally {
            token.value = null;
            user.value = null;
            expiresAt.value = null;
            errorMessage.value = "";
            if (import.meta.client) {
                sessionStorage.removeItem("loginUser");
            }
        }
    }

    function clearError() {
        errorMessage.value = "";
    }

    function setError(message: string) {
        errorMessage.value = message;
    }

    return {
        token,
        user,
        expiresAt,
        captchaToken,
        captchaImage,
        captchaFailed,
        captchaLoading,
        loading,
        errorMessage,
        isAuthenticated,
        refreshCaptcha,
        login,
        restoreSession,
        logout,
        clearError,
        setError,
    };
});
