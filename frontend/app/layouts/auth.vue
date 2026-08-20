<template>
  <n-config-provider :theme="osTheme" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <main class="auth-layout">
            <slot />
          </main>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { dateZhCN, darkTheme, useOsTheme, zhCN } from "naive-ui";

const osTheme = ref<typeof darkTheme | null>(null);

onMounted(() => {
  const naiveOsTheme = useOsTheme();

  const updateTheme = () => {
    osTheme.value = naiveOsTheme.value === "dark" ? darkTheme : null;
  };

  updateTheme();
  watch(naiveOsTheme, updateTheme);
});
</script>

<style scoped>
.auth-layout {
  min-height: 100dvh;
}
</style>
