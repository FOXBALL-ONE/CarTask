// https://nuxt.com/docs/api/configuration/nuxt-config
import AutoImport from "unplugin-auto-import/vite";
import { NaiveUiResolver } from "unplugin-vue-components/resolvers";
import Components from "unplugin-vue-components/vite";

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  runtimeConfig: {
    public: {
      baseUrl: process.env.BASE_URL || process.env.NUXT_PUBLIC_BASE_URL || 'http://127.0.0.1:8080/api',
    },
  },

  app: {
    head: {
      // 与原型页面的 <meta name="viewport"> 完全一致
      meta: [
        {
          name: 'viewport',
          content: 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover',
          // 压过 unhead 默认注入的 viewport meta（tagPriority high → 权重更低，去重时胜出）
          tagPriority: 'high',
        },
      ],
    },
  },

  modules: [
    '@nuxt/eslint',
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    '@bg-dev/nuxt-naiveui',
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
  devServer: {
    port: 8090,
  },
})
