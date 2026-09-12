/**
 * 在首次渲染之前从 sessionStorage 恢复登录态。
 *
 * 菜单与页面内的操作按钮都是按权限过滤的，而权限来自 `authStore.user`。原先这一步是在布局的
 * `onMounted` 里做的，晚于首次渲染——结果就是硬刷新后先渲染出一个几乎为空的菜单，再补齐，
 * 观感上像是"菜单偶尔会消失"。放到插件里，渲染前就已经有权限数据了。
 *
 * 只做本地恢复；随后的会话校验仍由 store 里的 refreshSession 在后台发起。
 */
export default defineNuxtPlugin(() => {
    const authStore = useAuthStore();
    if (authStore.isAuthenticated && !authStore.user) {
        authStore.restoreSession();
    }
});
