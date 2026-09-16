<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="naiveThemeOverrides">
    <nuxt-route-announcer/>
    <nuxt-layout>
      <n-el>
        <nuxt-page/>
      </n-el>
    </nuxt-layout>
  </n-config-provider>
</template>

<script lang="ts" setup>
import {darkTheme} from "naive-ui";

/*
 * 页面本身的颜色全部走 assets/css/theme.css 的 CSS 变量，这里只解决组件库：
 * naive-ui 的 n-el / n-button 等用的是自己的 --n-* 变量，不跟 data-theme 走，
 * 不显式告诉它当前是深色，深色页面里就会冒出几块白底组件。
 *
 * 色值刻意和 theme.css 对齐。naive-ui 会用 primaryColor 参与派生计算，
 * 所以这里只能写实际色值，写成 var(--primary) 会算不出悬停色。
 */
const {isDark, syncFromDom} = useTheme();

const naiveTheme = computed(() => (isDark.value ? darkTheme : null));

const naiveThemeOverrides = computed(() => (isDark.value
    ? {
        common: {
            primaryColor: "#60a5fa",
            primaryColorHover: "#93c5fd",
            primaryColorPressed: "#3b82f6",
            bodyColor: "#18181b",
            cardColor: "#27272a",
            textColorBase: "#fafafa",
            borderColor: "#3f3f46",
        },
    }
    : {
        common: {
            primaryColor: "#2563eb",
            primaryColorHover: "#3b82f6",
            primaryColorPressed: "#1d4ed8",
            bodyColor: "#fafafa",
            cardColor: "#fff",
            textColorBase: "#18181b",
            borderColor: "#e4e4e7",
        },
    }));

// 首屏主题由 nuxt.config.ts 的内联脚本写进 <html>，这里把它读回状态，供组件库与切换按钮使用。
onMounted(syncFromDom);
</script>

<style scoped>
</style>
