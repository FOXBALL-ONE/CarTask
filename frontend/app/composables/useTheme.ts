/**
 * 全站主题状态：唯一的真值是 <html> 上的 data-theme 属性，颜色全部由 CSS 变量驱动。
 *
 * 首屏由 nuxt.config.ts 里的内联脚本在读 localStorage 后写入该属性，脚本在首次绘制前执行，
 * 所以不会出现"先亮后暗"的闪烁；这里只负责读取属性、跟随切换，以及让 naive-ui 之类的
 * 组件库拿到同一份状态。
 *
 * 默认浅色。只有显式存过 "dark" 才进深色——不跟随系统偏好，避免用户没动过设置界面却自己变暗。
 */
export type ThemeMode = "light" | "dark";

const STORAGE_KEY = "theme";

export function useTheme() {
    const mode = useState<ThemeMode>("theme-mode", () => "light");
    const isDark = computed(() => mode.value === "dark");

    function apply(value: ThemeMode) {
        mode.value = value;
        if (!import.meta.client) {
            return;
        }
        document.documentElement.dataset.theme = value;
        try {
            localStorage.setItem(STORAGE_KEY, value);
        } catch {
            // 隐私模式下 localStorage 不可写，主题只在本次会话生效即可。
        }
    }

    function toggle() {
        apply(isDark.value ? "light" : "dark");
    }

    /**
     * 把状态对齐到首屏脚本已经写好的属性。
     * 只能在 onMounted 之后调用：服务端与首次客户端渲染都要保持浅色，
     * 否则水合时图标和组件库主题会和 DOM 对不上。
     */
    function syncFromDom() {
        if (!import.meta.client) {
            return;
        }
        mode.value = document.documentElement.dataset.theme === "dark" ? "dark" : "light";
    }

    return {mode, isDark, toggle, apply, syncFromDom};
}
