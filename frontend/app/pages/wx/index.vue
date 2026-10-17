<template>
  <div class="wx-page">
    <main class="home-content">
      <section aria-labelledby="navigation-title" class="home-section navigation-section">
        <h2 id="navigation-title" class="section-title">导航栏</h2>
        <div class="navigation-grid">
          <button class="navigation-item" type="button" @click="navigateTo('/wx/violation')">
            <svg aria-hidden="true" viewBox="0 0 32 32">
              <path d="m5 13.4 22-9-9 22-3.1-9.9L5 13.4Z" />
              <path d="m14.9 16.5 7-7" />
            </svg>
            <span>违规上报</span>
          </button>
          <button class="navigation-item" type="button" @click="navigateTo('/commute-routes')">
            <svg aria-hidden="true" viewBox="0 0 32 32">
              <path d="M7.5 4.5h14a2 2 0 0 1 2 2v17a2 2 0 0 1-2 2h-14a2 2 0 0 1-2-2v-17a2 2 0 0 1 2-2Z" />
              <path d="M10 10h9M10 14h9M10 18h5" />
              <circle cx="22.5" cy="22.5" r="6" />
              <path d="M22.5 19v3.7l2.3 1.4" />
            </svg>
            <span>路线查询</span>
          </button>
        </div>
      </section>

      <section aria-labelledby="promotion-title" class="home-section promotion-section">
        <h2 id="promotion-title" class="section-title">宣传栏</h2>
        <div aria-label="宣传内容" class="promotion-panel">
          <div v-if="announcementLoading" class="promotion-state" role="status">
            <span class="promotion-spinner" />正在加载宣传通告
          </div>
          <div v-else-if="announcementError" class="promotion-state promotion-state--error" role="alert">
            {{ announcementError }}
            <button type="button" @click="loadAnnouncements">重新加载</button>
          </div>
          <div v-else-if="announcements.length === 0" class="promotion-state">
            <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 6h16v12H4z" /><path d="M8 10h8M8 14h5" /></svg>
            暂无宣传通告
          </div>
          <div v-else class="announcement-list">
            <article v-for="announcement in announcements" :key="announcement.id" class="announcement-card">
              <div class="announcement-card__meta">
                <span class="announcement-card__mark"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 6h16v12H4z" /><path d="M8 10h8M8 14h5" /></svg></span>
                <time>{{ formatAnnouncementDate(announcement.published_at) }}</time>
              </div>
              <h3>{{ announcement.title }}</h3>
              <p>{{ announcement.content }}</p>
            </article>
          </div>
        </div>
      </section>
    </main>

    <nav aria-label="底部导航" class="bottom-nav">
      <button aria-current="page" class="bottom-nav__item bottom-nav__item--active" type="button">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="m3.5 10 8.5-7 8.5 7M6 9v11h12V9M9 20v-6h6v6" />
        </svg>
        <span>主页</span>
      </button>
      <button class="bottom-nav__item" type="button" @click="navigateTo('/wx/notice')">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M5 5h14M12 5v15M7 12l5-5 5 5" />
        </svg>
        <span>通报栏</span>
      </button>
      <button class="bottom-nav__item" type="button" @click="navigateTo('/wx/car-owner')">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <circle cx="9" cy="8" r="3.5" />
          <path d="M2.5 20v-1.5a6.5 6.5 0 0 1 13 0V20M16 5a3.5 3.5 0 0 1 0 6.8M18 14a5 5 0 0 1 3.5 4.8V20" />
        </svg>
        <span>车主</span>
      </button>
      <button class="bottom-nav__item" type="button" @click="navigateTo('/wx/myself')">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M9.6 3.3a2.5 2.5 0 0 1 4.8 0l.4 1.3a8 8 0 0 1 1.8 1l1.3-.4a2.5 2.5 0 0 1 3.4 3.4l-.4 1.3a8 8 0 0 1 0 2l.4 1.3a2.5 2.5 0 0 1-3.4 3.4l-1.3-.4a8 8 0 0 1-1.8 1l-.4 1.3a2.5 2.5 0 0 1-4.8 0l-.4-1.3a8 8 0 0 1-1.8-1l-1.3.4a2.5 2.5 0 0 1-3.4-3.4l.4-1.3a8 8 0 0 1 0-2l-.4-1.3a2.5 2.5 0 0 1 3.4-3.4l1.3.4a8 8 0 0 1 1.8-1l.4-1.3Z" />
          <circle cx="12" cy="11.5" r="3" />
        </svg>
        <span>设置</span>
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
interface Announcement {
  id: number;
  title: string;
  content: string;
  published_at: string;
}

definePageMeta({layout: false});

const http = useHttp();
const announcements = ref<Announcement[]>([]);
const announcementLoading = ref(true);
const announcementError = ref("");

function formatAnnouncementDate(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

async function loadAnnouncements() {
  announcementLoading.value = true;
  announcementError.value = "";
  try {
    const result = await http.get<{ items: Announcement[] }>("/announcements/public");
    announcements.value = result.items || [];
  } catch (error) {
    announcementError.value = (error as { statusMessage?: string }).statusMessage || "宣传通告暂时无法获取";
  } finally {
    announcementLoading.value = false;
  }
}

onMounted(loadAnnouncements);
</script>

<style scoped>
:global(html),
:global(body),
:global(#__nuxt) {
  background: #f6f7f8;
  height: 100%;
  margin: 0;
  min-height: 100%;
}

.wx-page {
  box-sizing: border-box;
  background: #f6f7f8;
  color: #333;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  height: 100dvh;
  margin: 0 auto;
  max-width: 458px;
  min-height: 0;
  overflow: hidden;
  padding: 0 clamp(12px, 3.72vw, 17px) 0 clamp(16px, 5.46vw, 25px);
}

.home-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.home-section {
  flex: 0 0 auto;
}

.section-title {
  align-items: center;
  color: #36a0e3;
  display: flex;
  font-size: 14px;
  font-weight: 600;
  gap: 9px;
  height: 39px;
  letter-spacing: 1px;
  margin: 0;
  padding: 0 5px;
}

.section-title::before {
  background: #44a9ef;
  border-radius: 2px;
  content: "";
  height: 14px;
  width: 4px;
}

.navigation-grid {
  background: #fff;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  height: 88px;
}

.navigation-item {
  align-items: center;
  background: #fff;
  border: 0;
  border-right: 1px solid #f3f3f3;
  color: #666;
  display: flex;
  flex-direction: column;
  font: inherit;
  font-size: 12px;
  gap: 7px;
  justify-content: center;
  padding: 0;
}

.navigation-item:last-child {
  border-right: 0;
}

.navigation-item svg {
  fill: none;
  height: 28px;
  stroke: #111;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
  width: 28px;
}

.promotion-section {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.promotion-section .section-title {
  flex: 0 0 39px;
}

.promotion-panel {
  background: #fff;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 10px;
}

.promotion-state {
  align-items: center;
  color: #9aa4ad;
  display: flex;
  flex-direction: column;
  font-size: 11px;
  gap: 8px;
  height: 100%;
  justify-content: center;
  text-align: center;
}

.promotion-state svg {
  fill: none;
  height: 28px;
  stroke: #b4c1ca;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.4;
  width: 28px;
}

.promotion-state--error {
  color: #c95c55;
}

.promotion-state button {
  background: #edf6ff;
  border: 0;
  border-radius: 6px;
  color: #2183d4;
  font: inherit;
  font-size: 10px;
  padding: 7px 12px;
}

.promotion-spinner {
  animation: promotion-spin .9s linear infinite;
  border: 2px solid #dfeaf4;
  border-radius: 50%;
  border-top-color: #278ee4;
  height: 18px;
  width: 18px;
}

.announcement-list {
  display: grid;
  gap: 8px;
  height: 100%;
  overflow-y: auto;
  scrollbar-width: none;
}

.announcement-list::-webkit-scrollbar {
  display: none;
}

.announcement-card {
  background: #fbfdff;
  border: 1px solid #e8f0f5;
  border-radius: 9px;
  flex: 0 0 auto;
  padding: 10px 11px;
}

.announcement-card__meta {
  align-items: center;
  color: #9aa5ae;
  display: flex;
  font-size: 9px;
  gap: 6px;
}

.announcement-card__mark {
  align-items: center;
  background: #eaf5ff;
  border-radius: 5px;
  color: #328fd2;
  display: inline-flex;
  height: 22px;
  justify-content: center;
  width: 22px;
}

.announcement-card__mark svg {
  fill: none;
  height: 13px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
  width: 13px;
}

.announcement-card h3 {
  color: #303a42;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.45;
  margin: 8px 0 4px;
}

.announcement-card p {
  color: #7e8992;
  display: -webkit-box;
  font-size: 10px;
  line-height: 1.6;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  white-space: pre-wrap;
}

@keyframes promotion-spin {
  to { transform: rotate(360deg); }
}

.bottom-nav {
  background: #fff;
  border-top: 1px solid #f1f1f1;
  display: grid;
  flex: 0 0 51px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 6px;
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.bottom-nav__item {
  align-items: center;
  background: transparent;
  border: 0;
  color: #777;
  display: flex;
  flex-direction: column;
  font: inherit;
  font-size: 12px;
  gap: 1px;
  justify-content: center;
  padding: 3px 0 1px;
}

.bottom-nav__item svg {
  fill: none;
  height: 23px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
  width: 23px;
}

.bottom-nav__item--active {
  color: #2196f3;
}

@media (max-height: 680px) {
  .section-title,
  .promotion-section .section-title {
    height: 34px;
    flex-basis: 34px;
  }

  .navigation-grid {
    height: 76px;
  }

  .promotion-panel {
    padding: 8px;
  }
}
</style>
