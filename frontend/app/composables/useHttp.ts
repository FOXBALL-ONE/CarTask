import {createFetch, type FetchOptions} from "ofetch";
import type {ApiResult} from "~/types/http";

type ParamMode = "query" | "json";
type QueryParams = Record<string, unknown>;
type JsonBody = BodyInit | Record<string, unknown> | null | undefined;
type NotificationType = "warning" | "error";

interface AuthResponse {
    access_token: string;
    expires_at: string;
}

interface RequestFailure {
    status: number;
    message: string;
    data?: ApiResult<unknown>;
    retryAfterSeconds?: number;
    transportFailure?: boolean;
}

export interface HttpRequestOptions<T> extends Omit<FetchOptions<"json">, "baseURL" | "query" | "params" | "body"> {
    method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
    payloadMode?: ParamMode;
    params?: QueryParams;
    body?: T;
    businessErrorStatuses?: number[];
}

const AUTH_FAILURE_STATUSES = new Set([401]);
const TOKEN_COOKIE = "cartask_auth_token";
const LOGIN_PATH = "/login";

let clientSessionCleanupPromise: Promise<void> | null = null;
let clientSessionExpired = false;

function normalizeAuthorization(token?: string | null) {
    const rawToken = token?.trim();
    if (!rawToken) {
        return "";
    }

    if (/^bearer\s+/i.test(rawToken)) {
        return rawToken;
    }

    return `Bearer ${rawToken}`;
}

function getResponseStatus(response: ApiResult<unknown>): number {
    const status = Number(response.status);
    return Number.isFinite(status) ? status : 500;
}

function getResponseMessage(response: ApiResult<unknown>): string {
    return response.message ?? "Request failed";
}

function isSuccessfulStatus(status: number): boolean {
    return status === 0 || (status >= 200 && status < 300);
}

function isAuthenticationFailure(status: number): boolean {
    return AUTH_FAILURE_STATUSES.has(status);
}

function requestPath(url: string): string {
    try {
        return new URL(url, "http://localhost:8080").pathname.replace(/\/+$/, "");
    } catch {
        return url.split(/[?#]/, 1)[0]?.replace(/\/+$/, "") ?? url;
    }
}

function isLoginEndpoint(url: string): boolean {
    return /\/(?:api\/)?auth\/login(?:\/|$)/.test(requestPath(url));
}

function isLogoutEndpoint(url: string): boolean {
    return /\/(?:api\/)?auth\/logout$/.test(requestPath(url));
}

function isAuthenticationEndpoint(url: string): boolean {
    return isLoginEndpoint(url) || isLogoutEndpoint(url);
}

function requestFailure(error: unknown): RequestFailure {
    const value = error as {
        response?: {status?: number; _data?: ApiResult<unknown>; headers?: Headers};
        data?: ApiResult<unknown>;
        statusCode?: number;
        message?: string;
    };
    const data = value.response?._data ?? value.data;
    const rawStatus = data?.status ?? value.response?.status ?? value.statusCode;
    const status = Number(rawStatus);
    const retryAfter = value.response?.headers?.get("Retry-After")?.trim();
    const retryAfterNumber = Number(retryAfter);
    const retryAfterDate = retryAfter && !Number.isFinite(retryAfterNumber)
        ? Date.parse(retryAfter)
        : Number.NaN;
    const retryAfterSeconds = Number.isFinite(retryAfterNumber) && retryAfterNumber >= 0
        ? Math.ceil(retryAfterNumber)
        : Number.isFinite(retryAfterDate)
            ? Math.max(0, Math.ceil((retryAfterDate - Date.now()) / 1000))
            : undefined;

    return {
        status: Number.isFinite(status) && status > 0 ? status : 500,
        message: data?.message ?? value.message ?? "Request failed",
        data,
        retryAfterSeconds,
        transportFailure: rawStatus === undefined || rawStatus === null,
    };
}

function toRequestError(failure: RequestFailure) {
    return createError({
        statusCode: failure.status,
        statusMessage: failure.message,
        data: {
            ...(failure.data ?? {}),
            retry_after: failure.retryAfterSeconds,
            transport_failure: failure.transportFailure === true,
        },
    });
}

export const useHttp = (baseURL?: string) => {
    // 单 JWT 由前端保存，并在每次请求中显式写入 Authorization 请求头。
    const authToken = useCookie<string | null>(TOKEN_COOKIE, {
        sameSite: "lax",
        path: "/",
    });
    const toast = useToast();
    const router = useRouter();
    const configuredApiBase = useRuntimeConfig().public.apiBase;
    const authApiBase = typeof configuredApiBase === "string" && configuredApiBase
        ? configuredApiBase
        : "http://127.0.0.1:8080/api";
    const apiBase = baseURL || authApiBase;

    const http = createFetch({
        defaults: {
            baseURL: apiBase,
            credentials: "omit",
            headers: {
                Accept: "application/json",
            },
        },
    });
    const authHttp = createFetch({
        defaults: {
            baseURL: authApiBase,
            credentials: "omit",
            headers: {
                Accept: "application/json",
            },
        },
    });

    const notify = (type: NotificationType, title: string, description?: string) => {
        if (import.meta.server) {
            return;
        }

        toast.add({
            title,
            description,
            color: type,
        });
    };

    const persistAccessToken = (token: string) => {
        authToken.value = token;
        clientSessionExpired = false;
    };

    const expireSession = async (message: string, failure: RequestFailure): Promise<never> => {
        if (import.meta.server) {
            authToken.value = null;
            throw toRequestError(failure);
        }

        if (!clientSessionExpired) {
            clientSessionExpired = true;
            const tokenToLogout = authToken.value;
            authToken.value = null;
            notify("error", message);

            clientSessionCleanupPromise = (async () => {
                try {
                    await authHttp("/auth/logout", {
                        method: "POST",
                        headers: tokenToLogout
                            ? {Authorization: normalizeAuthorization(tokenToLogout)}
                            : undefined,
                    });
                } catch {
                    // 无论服务端清理是否可达，本地 JWT 都必须清除并返回登录页。
                }

                if (router.currentRoute.value.path !== LOGIN_PATH) {
                    try {
                        await router.replace(LOGIN_PATH);
                    } catch {
                        window.location.assign(LOGIN_PATH);
                    }
                }
            })().finally(() => {
                clientSessionCleanupPromise = null;
            });
        }

        if (clientSessionCleanupPromise) {
            await clientSessionCleanupPromise;
        }
        throw toRequestError(failure);
    };

    const requestBase = async <TResponse, TPayload = Record<string, unknown>>(
        url: string,
        payload?: TPayload,
        options: HttpRequestOptions<TPayload> = {},
    ): Promise<ApiResult<TResponse>> => {
        const {
            payloadMode: requestedPayloadMode,
            method = "GET",
            params,
            body,
            businessErrorStatuses = [],
            ...fetchOptions
        } = options;
        // 登录接口固定使用 JSON body，避免调用方遗漏 payloadMode 后把凭据拼到 URL。
        const payloadMode = requestedPayloadMode ?? (isLoginEndpoint(url) ? "json" : "query");
        const query = payloadMode === "query"
            ? (params ?? (payload as QueryParams | undefined))
            : params;
        const requestBody = payloadMode === "json"
            ? ((body ?? payload) as JsonBody)
            : undefined;

        const send = async () => {
            const requestHeaders = new Headers(fetchOptions.headers as HeadersInit | undefined);
            const authorization = normalizeAuthorization(authToken.value);
            if (authorization && !requestHeaders.has("Authorization")) {
                requestHeaders.set("Authorization", authorization);
            }

            const response = await http<ApiResult<TResponse>>(url, {
                method,
                ...fetchOptions,
                query,
                headers: requestHeaders,
                body: requestBody,
            });
            const status = getResponseStatus(response);
            if (!isSuccessfulStatus(status)) {
                throw createError({
                    statusCode: status,
                    statusMessage: getResponseMessage(response),
                    data: response,
                });
            }

            if (isLoginEndpoint(url)) {
                const data = response.data as unknown as Partial<AuthResponse> | undefined;
                const token = data?.access_token?.trim();
                if (token) {
                    persistAccessToken(token);
                }
            } else if (isLogoutEndpoint(url)) {
                authToken.value = null;
                clientSessionExpired = true;
            }

            return response;
        };

        let failure: RequestFailure;
        try {
            return await send();
        } catch (error: unknown) {
            failure = requestFailure(error);
        }

        // 单 JWT 模式不提供刷新接口；认证失败后直接清理本地 token 并要求重新登录。
        if (
            import.meta.server
            || isAuthenticationEndpoint(url)
            || !isAuthenticationFailure(failure.status)
            || businessErrorStatuses.includes(failure.status)
        ) {
            throw toRequestError(failure);
        }
        if (clientSessionExpired) {
            throw createError({
                statusCode: failure.status,
                statusMessage: "登录已失效，请重新登录",
                data: failure.data,
            });
        }
        return expireSession("登录状态已失效，请重新登录", failure);
    };

    const request = async <TResponse, TPayload = Record<string, unknown>>(
        url: string,
        payload?: TPayload,
        options: HttpRequestOptions<TPayload> = {},
    ): Promise<TResponse> => {
        const response = await requestBase<TResponse, TPayload>(url, payload, options);
        return response.data;
    };

    return {
        request,
        requestRaw: requestBase,
        get: <TResponse>(url: string, params?: QueryParams, options?: Omit<HttpRequestOptions<QueryParams>, "method" | "payloadMode" | "params" | "body">) =>
            request<TResponse>(url, params, {
                ...options,
                method: "GET",
                payloadMode: "query",
            }),
        getRaw: <TResponse>(url: string, params?: QueryParams, options?: Omit<HttpRequestOptions<QueryParams>, "method" | "payloadMode" | "params" | "body">) =>
            requestBase<TResponse>(url, params, {
                ...options,
                method: "GET",
                payloadMode: "query",
            }),
        post: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            request<TResponse, TPayload>(url, payload, {
                ...options,
                method: "POST",
            }),
        postRaw: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            requestBase<TResponse, TPayload>(url, payload, {
                ...options,
                method: "POST",
            }),
        put: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            request<TResponse, TPayload>(url, payload, {
                ...options,
                method: "PUT",
            }),
        putRaw: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            requestBase<TResponse, TPayload>(url, payload, {
                ...options,
                method: "PUT",
            }),
        patch: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            request<TResponse, TPayload>(url, payload, {
                ...options,
                method: "PATCH",
            }),
        patchRaw: <TResponse, TPayload = QueryParams>(url: string, payload?: TPayload, options?: Omit<HttpRequestOptions<TPayload>, "method">) =>
            requestBase<TResponse, TPayload>(url, payload, {
                ...options,
                method: "PATCH",
            }),
        delete: <TResponse>(url: string, params?: QueryParams, options?: Omit<HttpRequestOptions<QueryParams>, "method" | "payloadMode" | "params" | "body">) =>
            request<TResponse>(url, params, {
                ...options,
                method: "DELETE",
                payloadMode: "query",
            }),
        deleteRaw: <TResponse>(url: string, params?: QueryParams, options?: Omit<HttpRequestOptions<QueryParams>, "method" | "payloadMode" | "params" | "body">) =>
            requestBase<TResponse>(url, params, {
                ...options,
                method: "DELETE",
                payloadMode: "query",
            }),
    };
};
