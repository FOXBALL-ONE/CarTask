import {defineStore} from "pinia";
import {TOKEN_COOKIE, useHttp} from "~/composables/useHttp";

export interface AuthUser {
    user_id: number;
    username: string;
    role: string;
    permissions: string[];
    /** 头像 data URL；未设置时为 null。 */
    avatar?: string | null;
    /** 为 true 时前端需强制跳转到改密页，服务端也会拦截其他接口。 */
    must_change_password?: boolean;
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

interface SmsSendPayload {
    phone: string;
    captchaAnswer: string;
}

interface SmsLoginPayload {
    phone: string;
    code: string;
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
    const smsSending = ref(false);
    const errorMessage = ref("");
    const isAuthenticated = computed(() => Boolean(token.value));
    // 初始密码未修改：登录后必须先改密，其他页面一律不放行。
    const mustChangePassword = computed(() => user.value?.must_change_password === true);
    const avatar = computed(() => user.value?.avatar?.trim() || "");

    /** 个人中心改动当前用户字段后同步本地会话缓存，顶栏无需整页刷新即可更新。 */
    function patchCurrentUser(patch: Partial<AuthUser>) {
        if (!user.value) {
            return;
        }
        user.value = { ...user.value, ...patch };
        if (!import.meta.client) {
            return;
        }
        const stored = sessionStorage.getItem("loginUser");
        if (!stored) {
            return;
        }
        try {
            const data = JSON.parse(stored) as Partial<LoginResponse>;
            if (data.user) {
                data.user = { ...data.user, ...patch };
                sessionStorage.setItem("loginUser", JSON.stringify(data));
            }
        } catch {
            sessionStorage.removeItem("loginUser");
        }
    }

    /** 改头像后同步顶栏显示。 */
    function setAvatar(value: string | null) {
        patchCurrentUser({ avatar: value });
    }

    /** 改密成功后本端会话已失效，同步清掉本地「必须改密」标记。 */
    function markPasswordChanged() {
        patchCurrentUser({ must_change_password: false });
    }

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

    // 登录成功后的落地动作（密码登录与短信登录共用）：写入 token、用户信息与本地会话缓存。
    function applyLoginResponse(data: LoginResponse) {
        token.value = data.access_token;
        user.value = data.user;
        expiresAt.value = data.expires_at;

        if (import.meta.client) {
            sessionStorage.setItem("loginUser", JSON.stringify(data));
        }
    }

    // 把接口失败统一转成展示文案：网络不可达、业务消息、以及 Retry-After 倒计时。
    function describeError(error: unknown, fallback: string): string {
        const failure = error as LoginError;
        const retryAfter = failure.data?.retry_after;
        const retryMessage = typeof retryAfter === "number" && retryAfter > 0
            ? `，请 ${retryAfter} 秒后重试`
            : "";

        return failure.data?.transport_failure
            ? "网络请求失败，请检查服务器是否启动"
            : `${failure.statusMessage || fallback}${retryMessage}`;
    }

    async function login(payload: Omit<LoginPayload, "captchaToken">) {
        loading.value = true;
        errorMessage.value = "";

        try {
            const data = await http.post<LoginResponse, LoginPayload>("/auth/login", {
                ...payload,
                captchaToken: captchaToken.value,
            });
            applyLoginResponse(data);

            return data;
        } catch (error: unknown) {
            errorMessage.value = describeError(error, "登录失败");
            throw error;
        } finally {
            loading.value = false;
        }
    }

    async function smsLogin(payload: SmsLoginPayload) {
        loading.value = true;
        errorMessage.value = "";

        try {
            const data = await http.post<LoginResponse, SmsLoginPayload>("/auth/sms/login", payload);
            applyLoginResponse(data);

            return data;
        } catch (error: unknown) {
            errorMessage.value = describeError(error, "登录失败");
            throw error;
        } finally {
            loading.value = false;
        }
    }

    // 发送短信验证码同样要过图形验证码：后端校验通过后立即作废该 token，
    // 因此调用方成功拿到验证码后必须刷新图形验证码，否则重发与再次校验都会失败。
    async function sendSmsCode(payload: SmsSendPayload) {
        smsSending.value = true;
        errorMessage.value = "";

        try {
            await http.post("/auth/sms/send", {
                ...payload,
                purpose: "LOGIN",
                captchaToken: captchaToken.value,
            }, { payloadMode: "json" });
        } catch (error: unknown) {
            errorMessage.value = describeError(error, "验证码发送失败");
            throw error;
        } finally {
            smsSending.value = false;
        }
    }

    function restoreSession() {
        if (!import.meta.client || !token.value || user.value) {
            return;
        }

        const stored = sessionStorage.getItem("loginUser");
        if (!stored) {
            void refreshSession().catch(() => undefined);
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
        void refreshSession().catch(() => undefined);
    }

    async function refreshSession() {
        if (!token.value) {
            return null;
        }

        const currentUser = await http.get<AuthUser>("/auth/session");
        user.value = currentUser;
        if (import.meta.client) {
            sessionStorage.setItem("loginUser", JSON.stringify({
                expires_at: expiresAt.value,
                user: currentUser,
            }));
        }
        return currentUser;
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
        smsSending,
        errorMessage,
        isAuthenticated,
        mustChangePassword,
        avatar,
        refreshCaptcha,
        login,
        smsLogin,
        sendSmsCode,
        setAvatar,
        markPasswordChanged,
        restoreSession,
        refreshSession,
        logout,
        clearError,
        setError,
    };
});
