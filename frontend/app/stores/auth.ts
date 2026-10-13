import {defineStore} from "pinia";
import {TOKEN_COOKIE, useHttp} from "~/composables/useHttp";

export interface WorkingDepartmentOption {
    id: number;
    name: string;
    selected: boolean;
}

export interface AuthUser {
    user_id: number;
    username: string;
    role: string;
    permissions: string[];
    /** 头像 data URL；未设置时为 null。 */
    avatar?: string | null;
    /** 为 true 时前端需强制跳转到改密页，服务端也会拦截其他接口。 */
    must_change_password?: boolean;
    /** 数据范围：ALL 不限部门 / DEPARTMENT 限定部门 / SELF 仅本人。 */
    scope?: "ALL" | "DEPARTMENT" | "SELF";
    /** 当前工作部门；null 表示不限部门。 */
    working_department_id?: number | null;
    working_department_name?: string | null;
    /** 当前用户可切换的工作部门；对本人范围的角色为空。 */
    working_department_options?: WorkingDepartmentOption[];
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
    /** 短信用途；后端按用途分别存放验证码，登录与换绑手机号互不覆盖。 */
    purpose?: "LOGIN" | "CHANGE_PHONE";
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
    /**
     * 短信验证是否生效。
     *
     * 后端可以临时关掉短信验证（cartask.sms.skip-verification），此时前端要一起跳过验证码步骤，
     * 否则界面还在等一条永远收不到的短信。取不到状态时按 true 处理：宁可多要一个验证码，
     * 也不要因为一次网络抖动静默跳过校验。
     */
    const smsVerificationEnabled = ref(true);
    const errorMessage = ref("");
    const isAuthenticated = computed(() => Boolean(token.value));
    // 初始密码未修改：登录后必须先改密，其他页面一律不放行。
    const mustChangePassword = computed(() => user.value?.must_change_password === true);
    const avatar = computed(() => user.value?.avatar?.trim() || "");
    /**
     * 数据范围版本号。切换工作部门后自增，各列表页 watch 它重新拉取。
     *
     * 各页面的数据都是 onMounted 加载进本地 ref 的，对会话没有响应式依赖，
     * 所以不能指望切完部门页面会自己刷新。
     */
    const scopeEpoch = ref(0);

    /** 个人中心改动当前用户字段后同步本地会话缓存，顶栏无需整页刷新即可更新。 */
    function patchCurrentUser(patch: Partial<AuthUser>) {
        if (!user.value) {
            return;
        }
        user.value = {...user.value, ...patch};
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
                data.user = {...data.user, ...patch};
                sessionStorage.setItem("loginUser", JSON.stringify(data));
            }
        } catch {
            sessionStorage.removeItem("loginUser");
        }
    }

    /** 改头像后同步顶栏显示。 */
    function setAvatar(value: string | null) {
        patchCurrentUser({avatar: value});
    }

    /** 改密成功后本端会话已失效，同步清掉本地「必须改密」标记。 */
    function markPasswordChanged() {
        patchCurrentUser({must_change_password: false});
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
    // 后端临时关闭短信验证时，这一步与图形验证码都会被跳过。
    async function sendSmsCode(payload: SmsSendPayload) {
        smsSending.value = true;
        errorMessage.value = "";

        try {
            await http.post("/auth/sms/send", {
                ...payload,
                purpose: payload.purpose ?? "LOGIN",
                captchaToken: captchaToken.value,
            }, {payloadMode: "json"});
        } catch (error: unknown) {
            errorMessage.value = describeError(error, "验证码发送失败");
            throw error;
        } finally {
            smsSending.value = false;
        }
    }

    /** 查询短信验证是否生效；失败时按「生效」处理，见 smsVerificationEnabled 的说明。 */
    async function loadSmsVerificationStatus() {
        try {
            const data = await http.get<{ verification_enabled: boolean }>("/auth/sms/status");
            smsVerificationEnabled.value = data.verification_enabled !== false;
        } catch {
            smsVerificationEnabled.value = true;
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

    /**
     * 切换当前工作部门。
     *
     * 服务端只改 Redis 会话、不重新签发 token，所以这里刷新会话后自增 scopeEpoch，
     * 由各列表页据此重新拉取——否则页面还停留在旧部门的数据上。
     */
    async function switchWorkingDepartment(departmentId: number | null) {
        const state = await http.put<{
            current_id: number | null;
            current_name: string | null;
            scope: "ALL" | "DEPARTMENT" | "SELF";
            options: WorkingDepartmentOption[];
        }>("/auth/working-department", {department_id: departmentId});
        if (user.value) {
            user.value = {
                ...user.value,
                scope: state.scope,
                working_department_id: state.current_id,
                working_department_name: state.current_name,
                working_department_options: state.options,
            };
            if (import.meta.client) {
                sessionStorage.setItem("loginUser", JSON.stringify({
                    expires_at: expiresAt.value,
                    user: user.value,
                }));
            }
        }
        scopeEpoch.value += 1;
        return state;
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
        smsVerificationEnabled,
        errorMessage,
        isAuthenticated,
        mustChangePassword,
        avatar,
        scopeEpoch,
        refreshCaptcha,
        login,
        smsLogin,
        sendSmsCode,
        loadSmsVerificationStatus,
        setAvatar,
        markPasswordChanged,
        restoreSession,
        refreshSession,
        switchWorkingDepartment,
        logout,
        clearError,
        setError,
    };
});
