import {useHttp} from "~/composables/useHttp";

/** 侧边栏与登录页顶部的图标和标题，对应后端 SystemBrandingService.BrandingData。 */
export interface BrandingData {
    title: string;
    /** data URL；为 null 时使用前端自带的 /favicon.ico。 */
    logo: string | null;
}

export const DEFAULT_BRANDING_TITLE = "福清市机关事务服务中心车务管理系统";
export const DEFAULT_BRANDING_LOGO = "/favicon.ico";

let pending: Promise<void> | null = null;

/**
 * 全局共享一份品牌信息：侧边栏、登录页、页面标题都读这里，保存后调用 apply 即可同步刷新。
 * 接口失败时保留默认值，不影响登录。
 */
export const useBranding = () => {
    const branding = useState<BrandingData>("branding", () => ({
        title: DEFAULT_BRANDING_TITLE,
        logo: null,
    }));
    const loaded = useState("branding-loaded", () => false);

    const title = computed(() => branding.value.title || DEFAULT_BRANDING_TITLE);
    const logo = computed(() => branding.value.logo || DEFAULT_BRANDING_LOGO);

    const apply = (data: BrandingData) => {
        branding.value = {title: data.title, logo: data.logo};
        loaded.value = true;
    };

    const load = async (force = false) => {
        if (loaded.value && !force) return;
        pending ??= (async () => {
            try {
                apply(await useHttp().get<BrandingData>("/branding"));
            } catch {
                // 后端不可达时沿用默认标题和图标。
            } finally {
                pending = null;
            }
        })();
        await pending;
    };

    return {branding, title, logo, load, apply};
};
