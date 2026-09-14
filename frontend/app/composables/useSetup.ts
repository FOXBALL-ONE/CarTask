import {useHttp} from "~/composables/useHttp";

/**
 * 配置引导接口的客户端。
 *
 * 口令放在 sessionStorage 而不是 cookie：它只在配置期间有效，理应随浏览器关闭而消失；
 * 而实施现场常常是几个人共用一台机器，留一个持久化的口令没有任何好处。
 */
export const SETUP_TOKEN_KEY = "cartask_setup_token";

/** 与后端 SetupSection.id 一一对应；换名称要两边一起改。 */
export type SetupStepId = "database" | "redis" | "keytop" | "storage" | "sms" | "administrator";

export interface SetupStatus {
    setup_required: boolean;
    completed_steps?: SetupStepId[];
    env_file?: string;
}

export interface SetupDraft {
    values: Record<string, string>;
    configured_secrets: string[];
    completed_steps: SetupStepId[];
}

export interface DatabaseProbeResult {
    product: string;
    version: string;
    database: string;
    tables: number;
}

export interface RedisProbeResult {
    version: string;
    database: number;
}

export interface KeytopProbeResult {
    message: string;
    areas: number | null;
}

export interface StorageProbeResult {
    root: string;
    base_url: string;
}

export interface SmsProbeResult {
    phone: string;
    code: string;
}

export interface CompletionResult {
    env_file: string;
    backup_file: string | null;
    restarting: boolean;
}

/** 后端统一响应之外的一层包装，用于把失败信息取成一句能直接显示的话。 */
interface RequestFailure {
    statusCode?: number;
    statusMessage?: string;
    data?: {transport_failure?: boolean};
    message?: string;
}

export const useSetup = () => {
    const http = useHttp();
    const token = ref("");

    if (import.meta.client) {
        token.value = sessionStorage.getItem(SETUP_TOKEN_KEY) ?? "";
    }

    function rememberToken(value: string) {
        token.value = value.trim();
        if (!import.meta.client) {
            return;
        }
        if (token.value) {
            sessionStorage.setItem(SETUP_TOKEN_KEY, token.value);
        } else {
            sessionStorage.removeItem(SETUP_TOKEN_KEY);
        }
    }

    function setupHeaders(): Record<string, string> | undefined {
        return token.value ? {"X-Setup-Token": token.value} : undefined;
    }

    /** 口令不对时后端回 403；把它与「服务没起来」区分开，页面的下一步动作完全不同。 */
    function describeFailure(error: unknown): string {
        const failure = error as RequestFailure;
        if (failure?.data?.transport_failure) {
            return "无法连接后端服务，请确认服务已在运行";
        }
        return failure?.statusMessage || failure?.message || "请求失败";
    }

    async function status(): Promise<SetupStatus> {
        return await http.get<SetupStatus>("/setup/status");
    }

    async function draft(): Promise<SetupDraft> {
        return await http.get<SetupDraft>("/setup/draft", undefined, {headers: setupHeaders()});
    }

    return {
        token,
        rememberToken,
        describeFailure,
        status,
        draft,
        verifyDatabase: (params: {url: string; username: string; password: string}) =>
            http.post<DatabaseProbeResult>("/setup/database/verify", params, {headers: setupHeaders()}),
        verifyRedis: (params: {host: string; port: string; password: string}) =>
            http.post<RedisProbeResult>("/setup/redis/verify", params, {headers: setupHeaders()}),
        verifyKeytop: (params: {
            base_url: string;
            app_id: string;
            park_id: string;
            park_name: string;
            app_secret: string;
        }) => http.post<KeytopProbeResult>("/setup/keytop/verify", params, {headers: setupHeaders()}),
        verifyStorage: (params: {storage_root: string; base_url: string}) =>
            http.post<StorageProbeResult>("/setup/storage/verify", params, {headers: setupHeaders()}),
        verifySms: (params: {
            access_key_id: string;
            access_key_secret: string;
            sign_name: string;
            template_code: string;
            phone: string;
            endpoint: string;
        }) => http.post<SmsProbeResult>("/setup/sms/verify", params, {headers: setupHeaders()}),
        skipSms: () => http.post<{enabled: boolean}>("/setup/sms/skip", undefined, {headers: setupHeaders()}),
        saveAdministrator: (params: {username: string; password: string; confirm_password: string}) =>
            http.post<{username: string}>("/setup/administrator/save", params, {headers: setupHeaders()}),
        complete: (frontendOrigin: string) =>
            http.post<CompletionResult>(
                "/setup/complete",
                {frontend_origin: frontendOrigin},
                {headers: setupHeaders()},
            ),
    };
};
