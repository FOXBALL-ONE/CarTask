import {defineStore} from "pinia";
import {useHttp} from "~/composables/useHttp";
import {useAuthStore} from "~/stores/auth";

export type Gender = "MALE" | "FEMALE" | "UNKNOWN";

/** 个人中心展示数据，对应后端 ProfileService.ProfileData。 */
export interface ProfileData {
    user_id: number;
    username: string;
    name: string | null;
    phone: string | null;
    email: string;
    gender: Gender;
    role: string;
    role_name: string;
    department_name: string | null;
    avatar: string | null;
    must_change_password: boolean;
    created_at: string;
}

export interface ProfileUpdatePayload {
    name?: string;
    phone?: string;
    email?: string;
    gender?: Gender;
}

export interface PasswordChangePayload {
    current_password: string;
    new_password: string;
}

export const useProfileStore = defineStore("profile", () => {
    const http = useHttp();
    const authStore = useAuthStore();
    const loading = ref(false);
    const saving = ref(false);
    const uploading = ref(false);

    async function load(): Promise<ProfileData> {
        loading.value = true;
        try {
            return await http.get<ProfileData>("/profile");
        } finally {
            loading.value = false;
        }
    }

    async function save(payload: ProfileUpdatePayload): Promise<ProfileData> {
        saving.value = true;
        try {
            return await http.put<ProfileData, ProfileUpdatePayload>("/profile", payload, {payloadMode: "json"});
        } finally {
            saving.value = false;
        }
    }

    /** 上传头像。后端只接受压缩后的 data URL，成功后同步顶栏显示。 */
    async function updateAvatar(avatar: string): Promise<ProfileData> {
        uploading.value = true;
        try {
            const data = await http.put<ProfileData, { avatar: string }>(
                "/profile/avatar",
                {avatar},
                {payloadMode: "json"},
            );
            authStore.setAvatar(data.avatar);
            return data;
        } finally {
            uploading.value = false;
        }
    }

    /** 修改密码。后端会撤销全部历史会话，调用方需在成功后引导重新登录。 */
    async function changePassword(payload: PasswordChangePayload): Promise<void> {
        saving.value = true;
        try {
            await http.put("/profile/password", payload, {payloadMode: "json"});
            authStore.markPasswordChanged();
        } finally {
            saving.value = false;
        }
    }

    return {
        loading,
        saving,
        uploading,
        load,
        save,
        updateAvatar,
        changePassword,
    };
});
