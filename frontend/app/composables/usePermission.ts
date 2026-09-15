/**
 * 按权限控制页面内的展示与可操作性。
 *
 * 只传一个权限时用 [can]，多个权限表示"任一满足"时用 [canAny]。**缺省不传权限视为无需权限**，
 * 所以没有参数就是"始终显示"，不要用 `can()` 来兜底判断登录态——那是路由中间件和 store 的事。
 *
 * 注意：这只是体验层的隐藏，不是安全边界。真正的拦截在服务端的 @PreAuthorize 与数据范围过滤，
 * 隐藏按钮只是避免用户点了才发现没权限。
 */
export function usePermission() {
    const authStore = useAuthStore();

    function canAny(...permissions: string[]) {
        if (permissions.length === 0) {
            return true;
        }
        return permissions.some((permission) => authStore.user?.permissions?.includes(permission) === true);
    }

    function can(permission: string) {
        return canAny(permission);
    }

    return {can, canAny};
}
