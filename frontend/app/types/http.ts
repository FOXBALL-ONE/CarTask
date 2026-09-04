export interface ApiResult<T> {
    status?: number;
    success?: boolean;
    message?: string;
    data: T;
}
