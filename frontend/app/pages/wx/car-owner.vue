<template>
  <main class="owner-page">
    <header class="page-header">
      <button aria-label="返回主页" class="back-button" type="button" @click="navigateTo('/wx')">
        <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m15 18-6-6 6-6" /></svg>
      </button>
      <div class="page-heading">
        <h1>车主信息</h1>
        <p>查看车主资料及名下车辆</p>
      </div>
      <button aria-label="刷新车主列表" class="refresh-button" :disabled="loading || loadingMore" type="button" @click="loadOwners(true)">
        <svg :class="{ 'is-spinning': loading || loadingMore }" aria-hidden="true" viewBox="0 0 24 24"><path d="M20 11a8 8 0 0 0-14.8-3L3 11M4 5v6h6M4 13a8 8 0 0 0 14.8 3L21 13m-1 6v-6h-6" /></svg>
      </button>
    </header>

    <section class="owner-banner" aria-label="车主档案概况">
      <div class="banner-copy">
        <span class="banner-kicker">OWNER DIRECTORY</span>
        <h2>车主档案</h2>
        <p>车辆与车位信息一目了然</p>
      </div>
      <div class="banner-illustration" aria-hidden="true">
        <svg viewBox="0 0 96 76"><path d="M18 61h61M24 57l4-17h39l7 17M31 40l5-11h23l7 11M19 57h61v7H19z"/><path d="M33 49h14M57 49h12M30 57v7m37-7v7"/><circle cx="69" cy="25" r="13"/><path d="m63 25 4 4 8-8"/></svg>
      </div>
      <div class="banner-foot"><span class="banner-count">{{ total }}</span><span>位可查看车主</span><span class="banner-separator"/><span>点击卡片查看详情</span></div>
    </section>

    <section class="owners-section" aria-labelledby="owners-title">
      <form class="search-bar" role="search" @submit.prevent="searchOwners">
        <svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4 4"/></svg>
        <input v-model.trim="keyword" aria-label="搜索车主" autocomplete="off" placeholder="搜索姓名、手机号或车主卡号" type="search">
        <button v-if="keyword" aria-label="清除搜索" class="clear-search" type="button" @click="clearSearch"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
        <button class="search-submit" type="submit">搜索</button>
      </form>

      <div class="section-heading">
        <div><h2 id="owners-title">车主列表</h2><p>共 {{ total }} 位车主</p></div>
        <span class="list-hint">点按查看详情</span>
      </div>

      <div ref="listElement" class="owner-list" @scroll.passive="onListScroll">
        <div v-if="loading" class="state-card" role="status"><span class="loader"/><strong>正在加载车主资料</strong><span>请稍候</span></div>
        <div v-else-if="errorMessage && owners.length === 0" class="state-card state-card--error" role="alert">
          <span class="state-icon">!</span><strong>车主信息暂时无法加载</strong><span>{{ errorMessage }}</span>
          <button class="retry-button" type="button" @click="loadOwners(true)">重新加载</button>
        </div>
        <div v-else-if="owners.length === 0" class="state-card">
          <span class="empty-icon" aria-hidden="true"><svg viewBox="0 0 48 48"><circle cx="24" cy="16" r="8"/><path d="M9 41v-4a15 15 0 0 1 30 0v4M6 41h36"/></svg></span>
          <strong>{{ keyword ? '没有找到相关车主' : '暂无可查看的车主' }}</strong>
          <span>{{ keyword ? '试试姓名、手机号或车主卡号' : '当前账号暂时没有关联车主资料' }}</span>
          <button v-if="keyword" class="retry-button" type="button" @click="clearSearch">清除搜索</button>
        </div>
        <template v-else>
          <button v-for="owner in owners" :key="owner.id" class="owner-card" type="button" @click="openDetails(owner)">
            <div class="owner-card__main">
              <span class="owner-avatar" :class="avatarTone(owner.id)">{{ ownerInitial(owner.name) }}</span>
              <div class="owner-identity">
                <div class="owner-name-line"><strong>{{ owner.name }}</strong><span :class="owner.status === 1 ? 'owner-status--active' : 'owner-status--inactive'" class="owner-status"><i/>{{ owner.status === 1 ? '正常' : '停用' }}</span></div>
                <span class="owner-dept">{{ owner.dept || '未登记部门' }}</span>
                <span class="owner-phone">{{ maskPhone(owner.phone) }}</span>
              </div>
              <svg class="card-chevron" aria-hidden="true" viewBox="0 0 24 24"><path d="m9 18 6-6-6-6"/></svg>
            </div>
            <div class="owner-card__meta">
              <span class="owner-card__card-id"><svg aria-hidden="true" viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 10h4m-4 4h2m5-3h4m-4 3h4"/></svg>卡号 {{ owner.card_id }}</span>
              <span class="owner-stat"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m4 11 1.5-4h13L20 11l1 2v5H3v-5l1-2Z"/><path d="M5.5 11h13M6 15h.1M18 15h.1"/></svg>{{ owner.plate_count }} 车牌</span>
              <span class="owner-stat"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 20V9l8-5 8 5v11M2 20h20M8 20v-6h8v6"/></svg>{{ owner.spot_count }} 车位</span>
            </div>
          </button>
          <div v-if="loadingMore" class="load-more-state" role="status"><span class="loader loader--small"/>正在加载更多车主</div>
          <div v-else-if="owners.length < total" class="load-more-hint">继续向上滑动加载更多</div>
          <div v-else class="load-more-hint">已显示全部车主</div>
          <div v-if="errorMessage" class="list-error" role="alert">{{ errorMessage }}<button type="button" @click="loadOwners(false)">重试</button></div>
        </template>
      </div>
    </section>

    <div v-if="selectedOwner" class="detail-overlay" @click.self="closeDetails">
      <section aria-labelledby="owner-detail-title" aria-modal="true" class="detail-sheet" role="dialog">
        <div class="sheet-handle"/>
        <header class="detail-header">
          <div><span class="detail-kicker">车主档案 · {{ selectedOwner.card_id }}</span><h2 id="owner-detail-title">{{ selectedOwner.name }}</h2></div>
          <button aria-label="关闭详情" class="close-button" type="button" @click="closeDetails"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
        </header>
        <div class="detail-scroll">
          <div class="profile-summary">
            <span class="profile-avatar" :class="avatarTone(selectedOwner.id)">{{ ownerInitial(selectedOwner.name) }}</span>
            <div><strong>{{ selectedOwner.name }}</strong><span>{{ selectedOwner.dept || '未登记部门' }}</span></div>
            <span :class="selectedOwner.status === 1 ? 'owner-status--active' : 'owner-status--inactive'" class="owner-status"><i/>{{ selectedOwner.status === 1 ? '正常' : '停用' }}</span>
          </div>

          <section class="detail-block">
            <h3>基本资料</h3>
            <dl class="detail-fields">
              <div><dt>车主卡号</dt><dd>{{ selectedOwner.card_id }}</dd></div>
              <div><dt>联系电话</dt><dd><a :href="`tel:${selectedOwner.phone}`">{{ selectedOwner.phone || '未登记' }}</a></dd></div>
              <div><dt>所属部门</dt><dd>{{ selectedOwner.dept || '未登记' }}</dd></div>
              <div><dt>账户余额</dt><dd class="balance-value">{{ formatBalance(selectedOwner.balance) }}</dd></div>
            </dl>
          </section>

          <section class="detail-block related-block">
            <div class="related-heading"><h3>名下车牌</h3><span>{{ detailLoading ? '读取中' : `${selectedOwner.plates?.length ?? selectedOwner.plate_count} 辆` }}</span></div>
            <div v-if="detailLoading" class="related-loading"><span class="loader loader--small"/>正在读取车辆资料</div>
            <div v-else-if="detailError" class="related-error">{{ detailError }}<button type="button" @click="loadOwnerDetail(selectedOwner)">重试</button></div>
            <div v-else-if="selectedOwner.plates?.length" class="related-list">
              <div v-for="plate in selectedOwner.plates" :key="plate.plate" class="related-card">
                <span class="related-icon related-icon--car"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m4 11 1.5-4h13L20 11l1 2v5H3v-5l1-2Z"/><path d="M5.5 11h13M6 15h.1M18 15h.1M7 18v2m10-2v2"/></svg></span>
                <div class="related-copy"><strong>{{ plate.plate }}</strong><span>{{ plate.car_brand || '车辆品牌未登记' }} · {{ plate.reg_date || '登记日期未知' }}</span><span v-if="plate.inspection_valid_until">年检有效期至 {{ plate.inspection_valid_until }}</span></div>
                <span :class="plate.status === 1 ? 'item-state--active' : 'item-state--inactive'" class="item-state">{{ plate.status === 1 ? '正常' : '停用' }}</span>
              </div>
            </div>
            <p v-else class="related-empty">暂无登记车辆</p>
          </section>

          <section class="detail-block related-block">
            <div class="related-heading"><h3>名下车位</h3><span>{{ detailLoading ? '读取中' : `${selectedOwner.spots?.length ?? selectedOwner.spot_count} 个` }}</span></div>
            <div v-if="detailLoading" class="related-loading"><span class="loader loader--small"/>正在读取车位资料</div>
            <div v-else-if="detailError" class="related-error">{{ detailError }}</div>
            <div v-else-if="selectedOwner.spots?.length" class="related-list">
              <div v-for="spot in selectedOwner.spots" :key="spot.code" class="related-card">
                <span class="related-icon related-icon--spot"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M5 21V4h14v17M9 21v-5h6v5M9 8h6m-6 4h6"/></svg></span>
                <div class="related-copy"><strong>{{ spot.code }}</strong><span>{{ spot.area }} · {{ spot.type }}</span><span v-if="spot.remark">{{ spot.remark }}</span></div>
                <span :class="spot.status === 1 ? 'item-state--active' : 'item-state--inactive'" class="item-state">{{ spot.status === 1 ? '正常' : '停用' }}</span>
              </div>
            </div>
            <p v-else class="related-empty">暂无登记车位</p>
          </section>
        </div>
        <footer class="detail-footer"><button class="footer-button" type="button" @click="closeDetails">完成</button></footer>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
interface OwnerSummary {
  id: number;
  name: string;
  dept: string;
  phone: string;
  card_id: string;
  plate_count: number;
  spot_count: number;
  status: number;
  balance?: string;
  plates?: OwnerPlate[];
  spots?: OwnerSpot[];
}

interface OwnerPlate {
  plate: string;
  car_brand: string;
  status: number;
  reg_date: string;
  inspection_valid_until: string | null;
}

interface OwnerSpot {
  code: string;
  area: string;
  type: string;
  status: number;
  remark: string | null;
}

interface OwnerList {
  items: OwnerSummary[];
  total: number;
  page: number;
  page_size: number;
}

interface OwnerDetail extends OwnerSummary {
  balance: string;
  plates: OwnerPlate[];
  spots: OwnerSpot[];
}

const http = useHttp();
definePageMeta({layout: false});

const owners = ref<OwnerSummary[]>([]);
const total = ref(0);
const page = ref(0);
const pageSize = 12;
const keyword = ref("");
const loading = ref(true);
const loadingMore = ref(false);
const errorMessage = ref("");
const listElement = ref<HTMLElement | null>(null);
const selectedOwner = ref<OwnerSummary | null>(null);
const detailLoading = ref(false);
const detailError = ref("");
let listRequestId = 0;
let detailRequestId = 0;

async function loadOwners(reset: boolean) {
  if (!reset && (loading.value || loadingMore.value || owners.value.length >= total.value)) return;
  const requestId = ++listRequestId;
  const nextPage = reset ? 1 : page.value + 1;
  errorMessage.value = "";
  if (reset) {
    loading.value = true;
    loadingMore.value = false;
    page.value = 0;
    owners.value = [];
    total.value = 0;
  } else {
    loadingMore.value = true;
  }

  try {
    const result = await http.get<OwnerList>("/wx/car-owners", {
      keyword: keyword.value || undefined,
      page: nextPage,
      page_size: pageSize,
    });
    if (requestId !== listRequestId) return;
    owners.value = reset ? result.items || [] : [...owners.value, ...(result.items || [])];
    total.value = result.total || 0;
    page.value = nextPage;
  } catch (error) {
    if (requestId === listRequestId) errorMessage.value = (error as {statusMessage?: string}).statusMessage || "请检查网络后重试";
  } finally {
    if (requestId === listRequestId) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}

function searchOwners() {
  void loadOwners(true);
}

function clearSearch() {
  keyword.value = "";
  void loadOwners(true);
}

function onListScroll() {
  const element = listElement.value;
  if (element && element.scrollHeight - element.scrollTop - element.clientHeight < 120) void loadOwners(false);
}

function ownerInitial(name: string) {
  return name.trim().slice(0, 1) || "车";
}

function avatarTone(id: number) {
  return `owner-avatar--tone-${id % 4}`;
}

function maskPhone(phone: string) {
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone || "暂无联系电话";
}

function formatBalance(balance?: string) {
  const value = Number(balance);
  if (!Number.isFinite(value)) return "—";
  return new Intl.NumberFormat("en-US", {style: "currency", currency: "USD"}).format(value);
}

async function openDetails(owner: OwnerSummary) {
  selectedOwner.value = {...owner, plates: undefined, spots: undefined};
  detailError.value = "";
  await loadOwnerDetail(owner);
}

async function loadOwnerDetail(owner: OwnerSummary) {
  const requestId = ++detailRequestId;
  detailLoading.value = true;
  detailError.value = "";
  try {
    const result = await http.get<OwnerDetail>(`/wx/car-owners/${owner.id}`);
    if (requestId === detailRequestId) selectedOwner.value = result;
  } catch (error) {
    if (requestId === detailRequestId) detailError.value = (error as {statusMessage?: string}).statusMessage || "车主详细资料暂时无法加载";
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false;
  }
}

function closeDetails() {
  detailRequestId += 1;
  selectedOwner.value = null;
  detailLoading.value = false;
}

onMounted(() => void loadOwners(true));
onBeforeUnmount(() => {
  listRequestId += 1;
  detailRequestId += 1;
});
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) { background: #f4f6f8; min-height: 100%; margin: 0; }
.owner-page { background: #f4f6f8; color: #252d34; display: flex; flex-direction: column; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; height: 100dvh; margin: 0 auto; max-width: 458px; overflow: hidden; }
.page-header { align-items: center; background: #fff; border-bottom: 1px solid #edf0f2; display: flex; flex: 0 0 70px; gap: 12px; padding: 0 16px; }
.back-button, .refresh-button, .close-button { align-items: center; background: transparent; border: 0; color: #59636d; display: inline-flex; flex: 0 0 auto; justify-content: center; padding: 0; }
.back-button { height: 38px; margin-left: -8px; width: 32px; }
.back-button svg, .refresh-button svg, .close-button svg { fill: none; height: 22px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; width: 22px; }
.page-heading { flex: 1; min-width: 0; }
.page-heading h1 { color: #20272e; font-size: 16px; font-weight: 650; line-height: 1.3; margin: 0; }
.page-heading p { color: #98a1aa; font-size: 11px; margin: 4px 0 0; }
.refresh-button { border: 1px solid #e7ebef; border-radius: 8px; height: 32px; width: 34px; }
.refresh-button svg { height: 16px; width: 16px; }
.refresh-button:disabled { opacity: .6; }
.is-spinning { animation: spin .9s linear infinite; }
.owner-banner { background: linear-gradient(125deg, #2589dd, #48adeb); border-radius: 14px; box-shadow: 0 8px 20px rgb(41 139 221 / 15%); color: #fff; flex: 0 0 124px; margin: 15px 16px 4px; overflow: hidden; padding: 14px 17px 0; position: relative; }
.banner-copy { position: relative; z-index: 1; }
.banner-kicker { color: rgb(255 255 255 / 75%); font-size: 8px; font-weight: 650; letter-spacing: 1.2px; }
.banner-copy h2 { font-size: 20px; font-weight: 650; letter-spacing: .3px; margin: 4px 0 2px; }
.banner-copy p { color: rgb(255 255 255 / 86%); font-size: 10px; margin: 0; }
.banner-illustration { color: rgb(255 255 255 / 32%); height: 74px; position: absolute; right: 14px; top: 8px; width: 92px; }
.banner-illustration svg { fill: none; height: 100%; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; width: 100%; }
.banner-foot { align-items: center; border-top: 1px solid rgb(255 255 255 / 24%); bottom: 0; color: rgb(255 255 255 / 90%); display: flex; font-size: 9px; gap: 5px; left: 17px; padding: 8px 0; position: absolute; right: 17px; }
.banner-count { font-size: 13px; font-weight: 700; }
.banner-separator { background: rgb(255 255 255 / 55%); height: 3px; margin: 0 3px; width: 3px; }
.owners-section { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 9px 16px 0; }
.search-bar { align-items: center; background: #fff; border: 1px solid #e9edf1; border-radius: 9px; display: flex; flex: 0 0 42px; gap: 8px; padding: 0 10px; }
.search-bar > svg { fill: none; flex: 0 0 auto; height: 17px; stroke: #9ca7b0; stroke-linecap: round; stroke-width: 1.6; width: 17px; }
.search-bar input { background: transparent; border: 0; color: #303941; flex: 1; font: inherit; font-size: 11px; min-width: 0; outline: none; }
.search-bar input::placeholder { color: #b0bac2; }
.clear-search { align-items: center; background: transparent; border: 0; color: #a2abb3; display: inline-flex; height: 22px; justify-content: center; padding: 0; width: 22px; }
.clear-search svg { fill: none; height: 13px; stroke: currentColor; stroke-linecap: round; stroke-width: 1.8; width: 13px; }
.search-submit { background: #278ddd; border: 0; border-radius: 6px; color: #fff; font: inherit; font-size: 10px; height: 27px; padding: 0 11px; }
.section-heading { align-items: center; display: flex; flex: 0 0 auto; justify-content: space-between; padding: 13px 0 9px; }
.section-heading h2 { color: #2b333a; font-size: 14px; font-weight: 650; margin: 0; }
.section-heading p { color: #9ba4ad; font-size: 10px; margin: 3px 0 0; }
.list-hint { color: #a4adb5; font-size: 9px; }
.owner-list { display: flex; flex: 1; flex-direction: column; gap: 9px; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding-bottom: 10px; scrollbar-width: none; }
.owner-list::-webkit-scrollbar { display: none; }
.owner-card { background: #fff; border: 1px solid #edf0f3; border-radius: 11px; color: inherit; flex: 0 0 auto; font: inherit; padding: 12px 12px 0; text-align: left; width: 100%; }
.owner-card:active { background: #f9fbfd; }
.owner-card__main { align-items: center; display: flex; gap: 10px; min-width: 0; padding-bottom: 11px; }
.owner-avatar, .profile-avatar { align-items: center; border-radius: 50%; display: inline-flex; flex: 0 0 auto; font-weight: 650; justify-content: center; }
.owner-avatar { font-size: 15px; height: 43px; width: 43px; }
.owner-avatar--tone-0 { background: #e8f4ff; color: #398bca; }
.owner-avatar--tone-1 { background: #edf7ef; color: #4d9870; }
.owner-avatar--tone-2 { background: #fff2e9; color: #d18c58; }
.owner-avatar--tone-3 { background: #f2edff; color: #8770bf; }
.owner-identity { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.owner-name-line { align-items: center; display: flex; gap: 7px; }
.owner-name-line > strong { color: #2f373e; font-size: 13px; font-weight: 650; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.owner-status, .item-state { align-items: center; border-radius: 999px; display: inline-flex; flex: 0 0 auto; font-size: 9px; gap: 4px; padding: 4px 7px; white-space: nowrap; }
.owner-status i { background: currentColor; border-radius: 50%; height: 4px; width: 4px; }
.owner-status--active, .item-state--active { background: #eaf7f0; color: #329363; }
.owner-status--inactive, .item-state--inactive { background: #f1f3f5; color: #929ba4; }
.owner-dept, .owner-phone { color: #8e99a2; font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-chevron { fill: none; flex: 0 0 auto; height: 18px; stroke: #b6c0c8; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; width: 18px; }
.owner-card__meta { align-items: center; border-top: 1px solid #f0f2f4; color: #8c98a2; display: flex; gap: 11px; min-height: 34px; }
.owner-card__card-id, .owner-stat { align-items: center; display: inline-flex; font-size: 9px; gap: 4px; white-space: nowrap; }
.owner-card__card-id { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.owner-card__meta svg { fill: none; flex: 0 0 auto; height: 14px; stroke: #9aa7b0; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.4; width: 14px; }
.state-card { align-items: center; background: #fff; border: 1px solid #edf0f3; border-radius: 12px; color: #98a2ac; display: flex; flex: 1; flex-direction: column; justify-content: center; min-height: 180px; padding: 24px; text-align: center; }
.state-card > strong { color: #49545e; font-size: 13px; font-weight: 600; margin-top: 12px; }
.state-card > span:last-of-type { font-size: 10px; margin-top: 6px; }
.loader { animation: spin .9s linear infinite; border: 2px solid #dfeaf4; border-radius: 50%; border-top-color: #278ee4; height: 22px; width: 22px; }
.loader--small { height: 15px; width: 15px; }
.state-card--error .state-icon { align-items: center; background: #fff0ef; border-radius: 50%; color: #d7635c; display: flex; font-size: 18px; height: 36px; justify-content: center; width: 36px; }
.retry-button { background: #edf6ff; border: 0; border-radius: 7px; color: #2183d4; font: inherit; font-size: 11px; margin-top: 13px; padding: 8px 13px; }
.empty-icon { align-items: center; background: #f2f7fb; border-radius: 50%; color: #a8bac8; display: flex; height: 58px; justify-content: center; width: 58px; }
.empty-icon svg { fill: none; height: 31px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 31px; }
.load-more-state, .load-more-hint { align-items: center; color: #a0a9b1; display: flex; flex: 0 0 auto; font-size: 9px; gap: 7px; justify-content: center; min-height: 34px; }
.list-error { align-items: center; background: #fff1f0; border-radius: 7px; color: #c95c55; display: flex; flex: 0 0 auto; font-size: 10px; justify-content: space-between; padding: 8px 10px; }
.list-error button, .related-error button { background: transparent; border: 0; color: #ba524b; font: inherit; font-size: 10px; text-decoration: underline; }
.detail-overlay { align-items: flex-end; background: rgb(20 30 40 / 42%); display: flex; inset: 0; justify-content: center; position: fixed; z-index: 50; }
.detail-sheet { background: #fff; border-radius: 18px 18px 0 0; display: flex; flex-direction: column; max-height: 89dvh; max-width: 458px; overflow: hidden; padding-bottom: env(safe-area-inset-bottom, 0px); width: 100%; }
.sheet-handle { background: #dce1e5; border-radius: 999px; flex: 0 0 auto; height: 4px; margin: 9px auto 3px; width: 34px; }
.detail-header { align-items: center; border-bottom: 1px solid #f0f2f4; display: flex; flex: 0 0 auto; justify-content: space-between; padding: 12px 18px; }
.detail-kicker { color: #9aa3ad; font-size: 9px; }
.detail-header h2 { color: #272f36; font-size: 16px; margin: 4px 0 0; }
.close-button { border-radius: 50%; height: 32px; width: 32px; }
.close-button svg { height: 19px; width: 19px; }
.detail-scroll { flex: 1; overflow-y: auto; padding: 13px 16px 20px; }
.profile-summary { align-items: center; background: linear-gradient(120deg, #f1f8ff, #f6fbff); border: 1px solid #e7f2fb; border-radius: 11px; display: flex; gap: 10px; padding: 11px; }
.profile-avatar { background: #deeffe; color: #3788c8; font-size: 16px; height: 42px; width: 42px; }
.profile-summary > div { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.profile-summary > div strong { color: #303941; font-size: 13px; }
.profile-summary > div span { color: #83909a; font-size: 10px; }
.detail-block { margin-top: 17px; }
.detail-block h3, .related-heading h3 { color: #4c5963; font-size: 11px; font-weight: 650; margin: 0; }
.detail-fields { margin: 7px 0 0; }
.detail-fields > div { align-items: center; border-bottom: 1px solid #f1f3f5; display: flex; justify-content: space-between; padding: 9px 1px; }
.detail-fields dt { color: #8e98a1; font-size: 10px; }
.detail-fields dd { color: #343d45; font-size: 10px; margin: 0; text-align: right; }
.detail-fields dd a { color: inherit; text-decoration: none; }
.detail-fields .balance-value { color: #277eb9; font-weight: 650; }
.related-heading { align-items: center; display: flex; justify-content: space-between; }
.related-heading > span { color: #9aa4ad; font-size: 9px; }
.related-list { display: grid; gap: 7px; margin-top: 9px; }
.related-card { align-items: center; background: #fafbfd; border: 1px solid #edf0f3; border-radius: 9px; display: flex; gap: 8px; min-width: 0; padding: 9px; }
.related-icon { align-items: center; border-radius: 7px; display: inline-flex; flex: 0 0 auto; height: 32px; justify-content: center; width: 32px; }
.related-icon--car { background: #eaf4fd; color: #398bc6; }
.related-icon--spot { background: #eef7ef; color: #54936c; }
.related-icon svg { fill: none; height: 18px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.4; width: 18px; }
.related-copy { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.related-copy strong { color: #313b43; font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-copy span { color: #8e99a2; font-size: 9px; line-height: 1.35; }
.item-state { font-size: 8px; padding: 4px 6px; }
.related-empty { background: #fafbfd; border-radius: 8px; color: #a3acb4; font-size: 10px; margin: 9px 0 0; padding: 14px; text-align: center; }
.related-loading, .related-error { align-items: center; color: #8c98a2; display: flex; font-size: 9px; gap: 7px; margin-top: 9px; min-height: 40px; }
.related-error { background: #fff1f0; border-radius: 7px; color: #c95c55; justify-content: space-between; padding: 7px 9px; }
.detail-footer { border-top: 1px solid #f0f2f4; flex: 0 0 auto; padding: 10px 16px calc(10px + env(safe-area-inset-bottom, 0px)); }
.footer-button { background: #288fe5; border: 0; border-radius: 9px; color: #fff; font: inherit; font-size: 12px; font-weight: 600; height: 41px; width: 100%; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
