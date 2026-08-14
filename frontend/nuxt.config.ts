// https://nuxt.com/docs/api/configuration/nuxt-config
import AutoImport from "unplugin-auto-import/vite";
import { NaiveUiResolver } from "unplugin-vue-components/resolvers";
import Components from "unplugin-vue-components/vite";

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: [
    '@nuxt/eslint',
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    'dayjs-nuxt',
    "nuxt-build-info",
    '@bg-dev/nuxt-naiveui'
  ],
  vite: {
    plugins: [
      AutoImport({
        imports: [
          {
            "naive-ui": [
              "useDialog",
              "useMessage",
              "useNotification",
              "useLoadingBar",
            ],
          },
        ],
      }),
      Components({
        resolvers: [NaiveUiResolver()],
      }),
    ]
  },
  dayjs: {
    locales: ["zh-cn"],
    defaultLocale: "zh-cn",
    plugins: ["timezone", "localizedFormat"],
    defaultTimezone: "Asia/Shanghai",
  },
  devServer: {
    host: "0.0.0.0",
    port: 8090,
  },
})