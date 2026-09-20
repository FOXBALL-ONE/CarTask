<template>
  <main class="public-page">
    <header class="masthead">
      <div class="masthead__inner">
        <span class="masthead__icon material-icons-outlined">campaign</span>
        <div><p>福清市车务管理系统</p><h1>宣传通告</h1></div>
      </div>
    </header>

    <section class="content" aria-live="polite">
      <div class="intro">
        <div><p class="eyebrow">ANNOUNCEMENTS</p><h2>最新宣传与服务通知</h2></div>
        <button class="refresh" title="刷新通告" type="button" @click="load"><span class="material-icons-outlined">refresh</span></button>
      </div>

      <p v-if="errorMessage" class="state state--error"><span class="material-icons-outlined">error_outline</span>{{ errorMessage }}</p>
      <p v-else-if="loading" class="state"><span class="material-icons-outlined spin">progress_activity</span>正在加载通告...</p>
      <p v-else-if="items.length === 0" class="state"><span class="material-icons-outlined">campaign</span>暂未发布通告</p>
      <div v-else class="announcement-list">
        <article v-for="item in items" :key="item.id" class="announcement">
          <time>{{ formatDate(item.published_at) }}</time>
          <div><h3>{{ item.title }}</h3><p>{{ item.content }}</p></div>
        </article>
      </div>
    </section>
  </main>
</template>

<script lang="ts" setup>
interface Announcement { id: number; title: string; content: string; published_at: string }

const http = useHttp();
const items = ref<Announcement[]>([]);
const loading = ref(true);
const errorMessage = ref("");

function formatDate(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await http.get<{items: Announcement[]}>("/announcements/public");
    items.value = result.items || [];
  } catch (error) {
    errorMessage.value = (error as {statusMessage?: string}).statusMessage || "通告暂时无法获取，请稍后重试";
  } finally {
    loading.value = false;
  }
}

useHead({title: "宣传通告"});
onMounted(load);
</script>

<style scoped>
.public-page { background: var(--bg); min-height: 100dvh; }.masthead { background: var(--primary); color: var(--on-solid); }.masthead__inner { align-items: center; display: flex; gap: 12px; margin: 0 auto; max-width: 920px; min-height: 112px; padding: 0 24px; }.masthead__icon { align-items: center; background: color-mix(in srgb, var(--on-solid) 18%, transparent); border-radius: 6px; display: flex; font-size: 30px; height: 52px; justify-content: center; width: 52px; }.masthead p { font-size: 12px; margin: 0 0 3px; opacity: .78; }.masthead h1 { font-size: 25px; font-weight: 650; letter-spacing: 0; margin: 0; }.content { margin: 0 auto; max-width: 920px; padding: 28px 24px 48px; }.intro { align-items: end; display: flex; justify-content: space-between; margin-bottom: 18px; }.eyebrow { color: var(--primary); font-size: 11px; font-weight: 700; letter-spacing: 0; margin: 0 0 6px; }.intro h2 { color: var(--text); font-size: 20px; font-weight: 650; margin: 0; }.refresh { align-items: center; background: var(--card); border: 1px solid var(--border-strong); border-radius: 6px; color: var(--text-sub); cursor: pointer; display: inline-flex; height: 36px; justify-content: center; width: 36px; }.refresh:hover { background: var(--primary-soft); color: var(--primary); }.refresh .material-icons-outlined { font-size: 19px; }.announcement-list { display: grid; gap: 12px; }.announcement { background: var(--card); border: 1px solid var(--border-strong); border-radius: 8px; display: grid; gap: 18px; grid-template-columns: 118px minmax(0, 1fr); padding: 20px; }.announcement time { border-right: 1px solid var(--border); color: var(--text-mute); font-family: Consolas, monospace; font-size: 12px; line-height: 1.6; padding-right: 18px; }.announcement h3 { color: var(--text); font-size: 16px; font-weight: 600; margin: 0 0 9px; }.announcement p { color: var(--text-sub); font-size: 13px; line-height: 1.8; margin: 0; white-space: pre-wrap; }.state { align-items: center; background: var(--card); border: 1px solid var(--border); border-radius: 8px; color: var(--text-mute); display: flex; flex-direction: column; font-size: 13px; gap: 10px; justify-content: center; min-height: 200px; }.state .material-icons-outlined { font-size: 28px; }.state--error { color: var(--danger); }.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } } @media (max-width: 600px) { .masthead__inner { min-height: 92px; padding: 0 16px; }.masthead__icon { font-size: 25px; height: 44px; width: 44px; }.masthead h1 { font-size: 21px; }.content { padding: 22px 16px 36px; }.intro h2 { font-size: 18px; }.announcement { display: block; padding: 16px; }.announcement time { border: 0; display: block; margin-bottom: 10px; padding: 0; }.announcement h3 { font-size: 15px; } }
</style>
