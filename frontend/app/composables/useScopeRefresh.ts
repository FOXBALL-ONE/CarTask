/**
 * 工作部门切换后重新加载页面数据。
 *
 * 各列表页的数据都是 onMounted 时加载进本地 ref 的，对会话没有响应式依赖，所以切换工作部门
 * 不会自动刷新——不接这个组合式函数，页面会停留在旧部门的数据上，看起来像是切换没生效。
 *
 * 用法：在页面里 `useScopeRefresh(loadXxx)`，传已定义好的加载函数。
 */
export function useScopeRefresh(load: () => void | Promise<void>) {
    const authStore = useAuthStore();
    watch(
        () => authStore.scopeEpoch,
        () => {
            void load();
        },
    );
}
