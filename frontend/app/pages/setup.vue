<template>
  <div class="setup-page">
    <!-- 口令闸门：配置模式下没有账号体系，口令是唯一能证明「这台机器归你管」的东西。 -->
    <div v-if="phase === 'locked'" class="gate">
      <div class="gate__card">
        <div class="gate__head">
          <div aria-hidden="true" class="mark">
            <span class="mark__lane"/>
            <span class="mark__post"/>
            <span class="mark__arm"/>
          </div>
          <div>
            <h1 class="gate__title">系统尚未初始化</h1>
            <p class="gate__desc">请先填写服务启动日志中的「配置口令」</p>
          </div>
        </div>
        <label class="field">
          <span class="field__label">配置口令</span>
          <div class="field__wrap">
            <span class="material-icons-outlined">key</span>
            <input
                v-model="tokenInput"
                autocomplete="off"
                class="field__input field__input--token"
                placeholder="启动横幅里的 8 位口令"
                spellcheck="false"
                type="text"
                @keyup.enter="unlock"
            >
          </div>
        </label>
        <button :disabled="busy || !tokenInput.trim()" class="btn btn--primary" type="button" @click="unlock">
          {{ busy ? "校验中..." : "进入配置引导" }}
        </button>
        <p v-if="errorMessage" class="alert alert--error">{{ errorMessage }}</p>
        <p class="gate__hint">
          口令在服务启动日志里，形如「配置口令 XXXXXXXX」；也可以用 <code>SETUP_TOKEN</code> 环境变量固定。
        </p>
      </div>
    </div>

    <!-- 已完成 -->
    <div v-else-if="phase === 'already'" class="gate">
      <div class="gate__card">
        <div class="gate__head">
          <div aria-hidden="true" class="mark mark--ok"><span class="material-icons-outlined">verified</span></div>
          <div>
            <h1 class="gate__title">系统已完成初始化</h1>
            <p class="gate__desc">当前服务运行在正常模式，无需再走配置引导</p>
          </div>
        </div>
        <button class="btn btn--primary" type="button" @click="goLogin">前往登录</button>
        <p class="gate__hint">
          需要修改配置时，在服务的工作目录下把 <code>.env</code> 里的 <code>SETUP_MODE</code> 设为
          <code>true</code>，重启后重新打开本页。
        </p>
      </div>
    </div>

    <!-- 重启中 -->
    <div v-else-if="phase === 'restarting'" class="gate">
      <div class="gate__card">
        <div class="gate__head">
          <div :class="restartTimedOut ? 'mark--warn' : 'mark--spin'" aria-hidden="true" class="mark">
            <span class="material-icons-outlined">{{ restartTimedOut ? "error_outline" : "autorenew" }}</span>
          </div>
          <div>
            <h1 class="gate__title">{{ restartTitle }}</h1>
            <p class="gate__desc">{{ restartDescription }}</p>
          </div>
        </div>
        <p class="gate__hint">
          配置已写入 <code>{{ envFile }}</code><span v-if="backupFile">，原文件备份为 <code>{{
            backupFile
          }}</code></span>。
        </p>
        <p v-if="restartTimedOut" class="alert alert--error">
          请查看服务日志里第一条错误：多半是数据库地址或用户名密码填错，或目标库不存在。
          改好 <code>.env</code> 后重启服务即可，也可以把 <code>SETUP_MODE</code> 设为 <code>true</code> 重新走一遍引导。
        </p>
        <button v-if="restarted" class="btn btn--primary" type="button" @click="goLogin">立即前往登录</button>
      </div>
    </div>

    <!-- 向导 -->
    <div v-else class="shell">
      <aside class="rail">
        <header class="brand">
          <div class="brand__text">
            <h1 class="brand__title">系统配置引导</h1>
            <p class="brand__desc">车辆出入管理平台</p>
          </div>
        </header>

        <ol class="steps">
          <li
              v-for="(step, index) in steps"
              :key="step.id"
              :class="{
              'step--active': index === currentIndex,
              'step--done': isDone(step.id),
              'step--ahead': index > currentIndex,
            }"
              class="step"
          >
            <button class="step__button" type="button" @click="jump(index)">
              <span class="step__marker">
                <span v-if="isDone(step.id)" class="material-icons-outlined">check</span>
                <template v-else>{{ index + 1 }}</template>
              </span>
              <span class="step__text">
                <span class="step__title">{{ step.title }}</span>
                <span class="step__hint">{{ step.hint }}</span>
              </span>
            </button>
          </li>
        </ol>

        <p class="rail__note">
          配置将写入<br>
          <code>{{ envFile }}</code>
        </p>
      </aside>

      <section class="panel">
        <header class="panel__head">
          <h2 class="panel__title">{{ currentStep.title }}</h2>
          <p class="panel__desc">{{ currentStep.description }}</p>
        </header>

        <!-- 1. 数据库 -->
        <div v-if="currentStep.id === 'database'" class="form">
          <label class="field">
            <span class="field__label">连接串</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">link</span>
              <input v-model="database.url" class="field__input" placeholder="jdbc:postgresql://主机:5432/cartask"
                     spellcheck="false">
            </div>
            <span class="field__hint">数据库需要先在服务器上创建好；表结构由服务首次启动时自动建立。</span>
          </label>
          <div class="form__row">
            <label class="field">
              <span class="field__label">用户名</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">person</span>
                <input v-model="database.username" autocomplete="off" class="field__input" placeholder="postgres">
              </div>
            </label>
            <label class="field">
              <span class="field__label">密码</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">lock</span>
                <input v-model="database.password" autocomplete="new-password" class="field__input"
                       placeholder="没有密码可留空"
                       type="password">
              </div>
            </label>
          </div>
        </div>

        <!-- 2. Redis -->
        <div v-else-if="currentStep.id === 'redis'" class="form">
          <div class="form__row form__row--host">
            <label class="field">
              <span class="field__label">主机</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">dns</span>
                <input v-model="redis.host" class="field__input" placeholder="127.0.0.1" spellcheck="false">
              </div>
            </label>
            <label class="field">
              <span class="field__label">端口</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">tag</span>
                <input v-model="redis.port" class="field__input" inputmode="numeric" placeholder="6379">
              </div>
            </label>
          </div>
          <label class="field">
            <span class="field__label">密码</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">lock</span>
              <input v-model="redis.password" autocomplete="new-password" class="field__input"
                     placeholder="未启用鉴权可留空"
                     type="password">
            </div>
            <span class="field__hint">登录会话的在线状态保存在 5 号库，服务端需允许使用该库（<code>databases</code> 至少 6）。</span>
          </label>
        </div>

        <!-- 3. 科拓 -->
        <div v-else-if="currentStep.id === 'keytop'" class="form">
          <label class="field">
            <span class="field__label">接口地址</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">cloud</span>
              <input v-model="keytop.base_url" class="field__input" placeholder="https://kp-open.keytop.cn/unite-api"
                     spellcheck="false">
            </div>
          </label>
          <div class="form__row">
            <label class="field">
              <span class="field__label">appId</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">badge</span>
                <input v-model="keytop.app_id" class="field__input" inputmode="numeric"
                       placeholder="科拓分配的应用编号">
              </div>
            </label>
            <label class="field">
              <span class="field__label">车场编号 parkId</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">local_parking</span>
                <input v-model="keytop.park_id" class="field__input" placeholder="科拓分配的车场编号"
                       spellcheck="false">
              </div>
            </label>
          </div>
          <label class="field">
            <span class="field__label">appSecret</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">vpn_key</span>
              <input v-model="keytop.app_secret" autocomplete="new-password" class="field__input"
                     placeholder="平台分配的密钥"
                     type="password">
            </div>
            <span class="field__hint">校验时会真实调用一次只读接口，同时验证 appId、parkId 与密钥是否配套。</span>
          </label>
          <label class="field">
            <span class="field__label">车场名称（选填）</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">store</span>
              <input v-model="keytop.park_name" class="field__input" placeholder="用于本地展示">
            </div>
          </label>
        </div>

        <!-- 4. 文件存储 -->
        <div v-else-if="currentStep.id === 'storage'" class="form">
          <label class="field">
            <span class="field__label">存储根目录</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">folder</span>
              <input v-model="storage.storage_root" class="field__input" placeholder="./st" spellcheck="false">
            </div>
            <span class="field__hint">
              附件、导入导出文件都落在这里，可以填相对路径（相对服务的工作目录）或绝对路径；
              留空表示使用工作目录。校验时会试写一次，最终按绝对路径记录。
            </span>
          </label>
          <label class="field">
            <span class="field__label">下载基址</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">public</span>
              <input v-model="storage.base_url" class="field__input" placeholder="http://192.168.1.95:8080"
                     spellcheck="false">
            </div>
            <span class="field__hint">必须是浏览器能访问到的后端绝对地址，用来拼接附件下载链接，不要填 localhost。</span>
          </label>
        </div>

        <!-- 5. 短信 -->
        <div v-else-if="currentStep.id === 'sms'" class="form">
          <p class="form__note">
            短信用于手机号验证码登录与找回。不启用不影响密码登录，配置完成后再补也可以。
          </p>
          <div class="form__row">
            <label class="field">
              <span class="field__label">AccessKey ID</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">badge</span>
                <input v-model="sms.access_key_id" autocomplete="off" class="field__input"
                       placeholder="阿里云 AccessKey ID"
                       spellcheck="false">
              </div>
            </label>
            <label class="field">
              <span class="field__label">AccessKey Secret</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">vpn_key</span>
                <input v-model="sms.access_key_secret" autocomplete="new-password" class="field__input"
                       placeholder="阿里云 AccessKey Secret"
                       type="password">
              </div>
            </label>
          </div>
          <div class="form__row">
            <label class="field">
              <span class="field__label">短信签名</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">edit_note</span>
                <input v-model="sms.sign_name" class="field__input" placeholder="已审核通过的签名">
              </div>
            </label>
            <label class="field">
              <span class="field__label">模板编号</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">description</span>
                <input v-model="sms.template_code" class="field__input" placeholder="SMS_xxxxxxxxx" spellcheck="false">
              </div>
            </label>
          </div>
          <label class="field">
            <span class="field__label">接收测试短信的手机号</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">smartphone</span>
              <input v-model="sms.phone" class="field__input" inputmode="tel" placeholder="例如 13800000000" type="tel">
            </div>
            <span class="field__hint">校验会真发一条测试短信；模板参数与登录验证码一致，都使用 <code>code</code>。</span>
          </label>
        </div>

        <!-- 6. 管理员 -->
        <div v-else-if="currentStep.id === 'administrator'" class="form">
          <p class="form__note">
            这个账号是系统的第一个超级管理员，拥有全部权限，且是新建账号、分配角色的唯一入口。
            服务首次以新模式启动时创建；账号建好后服务会自行清除本页填写的密码。
          </p>
          <label class="field">
            <span class="field__label">用户名</span>
            <div class="field__wrap">
              <span class="material-icons-outlined">account_circle</span>
              <input v-model="administrator.username" autocomplete="off" class="field__input" placeholder="admin"
                     spellcheck="false">
            </div>
            <span class="field__hint">字母、数字、下划线、点、短横线，3-32 位。</span>
          </label>
          <div class="form__row">
            <label class="field">
              <span class="field__label">密码</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">lock</span>
                <input v-model="administrator.password" autocomplete="new-password" class="field__input"
                       placeholder="至少 8 位"
                       type="password">
              </div>
            </label>
            <label class="field">
              <span class="field__label">确认密码</span>
              <div class="field__wrap">
                <span class="material-icons-outlined">lock_reset</span>
                <input v-model="administrator.confirm_password" autocomplete="new-password" class="field__input"
                       placeholder="再输入一次" type="password">
              </div>
            </label>
          </div>
          <p class="field__hint">
            需包含大写字母、小写字母、数字、符号中的至少两类。登录后建议在「个人中心」再改一次。
          </p>
        </div>

        <!-- 7. 确认 -->
        <div v-else class="form">
          <p class="form__note">
            确认无误后点击「写入配置并启动」。服务会把配置写入工作目录下的 <code>.env</code>，
            原文件会先备份，然后自动重启进入正常模式。
          </p>
          <dl class="review">
            <template v-for="item in review" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>
                <span v-if="item.masked" class="chip">已保存</span>
                <code v-else>{{ item.value || "—" }}</code>
              </dd>
            </template>
          </dl>
          <p v-if="errorMessage" class="alert alert--error">{{ errorMessage }}</p>
        </div>

        <!-- 验证结果 -->
        <p v-if="errorMessage && currentStep.id !== 'review'" class="alert alert--error">{{ errorMessage }}</p>
        <p v-else-if="successMessage && currentStep.id !== 'review'" class="alert alert--ok">
          <!-- 验证通过即自动前进，所以这条结果通常显示在下一步上；带上步骤名，免得被读成当前步骤的结论 -->
          <template v-if="successStepId && successStepId !== currentStep.id">
            <strong>{{ stepTitleOf(successStepId) }}已验证：</strong>
          </template>
          {{ successMessage }}
        </p>

        <footer class="panel__foot">
          <button
              v-if="currentIndex > 0"
              :disabled="busy"
              class="btn btn--ghost"
              type="button"
              @click="back"
          >上一步
          </button>
          <span class="panel__spacer"/>

          <button
              v-if="currentStep.id === 'sms'"
              :disabled="busy"
              class="btn btn--ghost"
              type="button"
              @click="skipSms"
          >暂不启用，跳过
          </button>

          <button v-if="currentStep.id === 'review'" :disabled="busy" class="btn btn--primary" type="button"
                  @click="finish">
            {{ busy ? "正在写入..." : "写入配置并启动" }}
          </button>
          <button v-else :disabled="busy" class="btn btn--primary" type="button" @click="next">
            {{ nextLabel }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
// 配置引导页：系统还没有账号体系时的唯一入口。
//
// 每一步的「下一步」就是一次真实验证——连库、连缓存、调科拓、写存储目录、发测试短信。验证通过即
// 保存草稿，因此这里的等待时间是真的，失败信息也是外部依赖原样返回的原因。不做「先保存后测试」：
// 那样页面上会出现「存了但没验过」的中间态，而没验过的配置要到重启之后才暴露。
import {type SetupStepId, useSetup} from "~/composables/useSetup";

const setup = useSetup();

type Phase = "locked" | "wizard" | "restarting" | "already";
type StepId = SetupStepId | "review";

interface StepDefinition {
  id: StepId;
  title: string;
  hint: string;
  description: string;
}

const steps: StepDefinition[] = [
  {
    id: "database",
    title: "数据库",
    hint: "PostgreSQL",
    description: "填写 PostgreSQL 连接信息，存放系统的全部业务数据。",
  },
  {
    id: "redis",
    title: "Redis",
    hint: "会话与限流",
    description: "登录会话的在线状态与登录限流都放在 Redis 上。",
  },
  {
    id: "keytop",
    title: "科拓开放平台",
    hint: "车场数据来源",
    description: "车辆、车位、进出记录都由科拓开放平台同步而来，需要平台分配的凭据。",
  },
  {
    id: "storage",
    title: "文件存储",
    hint: "附件与导出",
    description: "指定附件与导入导出文件的存放目录，以及供浏览器下载的地址。",
  },
  {
    id: "sms",
    title: "短信",
    hint: "可跳过",
    description: "短信用于手机号验证码登录；不启用也不影响密码登录。",
  },
  {
    id: "administrator",
    title: "管理员账号",
    hint: "超级管理员",
    description: "创建系统的第一个超级管理员，配置完成后用它登录。",
  },
  {
    id: "review",
    title: "确认并启动",
    hint: "写入配置",
    description: "核对以上配置，写入配置文件并重启服务。",
  },
];

const phase = ref<Phase>("locked");
const currentIndex = ref(0);
const busy = ref(false);
const errorMessage = ref("");
const successMessage = ref("");
/** 产生这条成功信息的步骤，用于在自动前进后标清它属于哪一步。 */
const successStepId = ref<StepId | null>(null);
const envFile = ref(".env");
const backupFile = ref("");
const restarted = ref(false);
/** 等重启超时：多半是新配置有问题，服务起不来了，得直说而不是一直转圈。 */
const restartTimedOut = ref(false);
const restartTitle = computed(() => {
  if (restartTimedOut.value) {
    return "服务未能启动";
  }
  return restarted.value ? "服务已就绪" : "正在按新配置重启";
});
const restartDescription = computed(() => {
  if (restartTimedOut.value) {
    return "配置已保存，但服务在预期时间内没有就绪";
  }
  return restarted.value ? "正在跳转到登录页…" : "数据库、缓存与全部业务模块正在启动，通常需要十几秒";
});
// 口令只在客户端可读（sessionStorage），因此这里先留空，由 onMounted 补上，
// 避免服务端渲染出的 HTML 与浏览器接管后的第一帧不一致。
const tokenInput = ref("");
const doneSteps = ref<Set<StepId>>(new Set());

/** 重启后的轮询节奏：服务通常十几秒内起来，90 秒足够覆盖慢机器。 */
const RESTART_POLL_ATTEMPTS = 90;
const RESTART_POLL_INTERVAL_MS = 1000;

const database = reactive({url: "", username: "", password: ""});
const redis = reactive({host: "", port: "6379", password: ""});
const keytop = reactive({base_url: "", app_id: "", park_id: "", park_name: "", app_secret: ""});
const storage = reactive({storage_root: "./st", base_url: ""});
const sms = reactive({
  access_key_id: "",
  access_key_secret: "",
  sign_name: "",
  template_code: "",
  phone: "",
  endpoint: "",
});
const administrator = reactive({username: "admin", password: "", confirm_password: ""});

/** 已保存的机密项：值为空且这里标了「已保存」时，说明草稿里那份仍然有效。 */
const savedSecrets = ref<Set<string>>(new Set());
/** 各步骤的字段是否被人改过。改过就必须重新验证——旧结论不能代表新值。 */
const dirty = reactive<Record<string, boolean>>({});
const applyingDraft = ref(false);

const currentStep = computed(() => steps[currentIndex.value] ?? steps[0]!);

const nextLabel = computed(() =>
    isDone(currentStep.value.id) && !dirty[currentStep.value.id] ? "下一步" : "验证并继续",
);

const review = computed(() => [
  {label: "数据库", value: database.url, masked: false},
  {label: "数据库账号", value: database.username, masked: false},
  {label: "数据库密码", value: "", masked: hasSecret("DB_PASSWORD") || Boolean(database.password)},
  {label: "Redis", value: redis.host ? `${redis.host}:${redis.port}` : "", masked: false},
  {label: "Redis 密码", value: "", masked: hasSecret("REDIS_PASSWORD") || Boolean(redis.password)},
  {label: "科拓 appId", value: keytop.app_id, masked: false},
  {label: "科拓 parkId", value: keytop.park_id, masked: false},
  {label: "科拓 appSecret", value: "", masked: hasSecret("KEYTOP_APP_SECRET") || Boolean(keytop.app_secret)},
  {label: "存储目录", value: storage.storage_root || "（服务工作目录）", masked: false},
  {label: "下载基址", value: storage.base_url, masked: false},
  {label: "短信", value: isDone("sms") && hasSecret("SMS_ACCESS_KEY_SECRET") ? "已启用" : "未启用", masked: false},
  {label: "管理员", value: administrator.username, masked: false},
]);

function isDone(id: StepId): boolean {
  return doneSteps.value.has(id);
}

function stepTitleOf(id: StepId): string {
  return steps.find((step) => step.id === id)?.title ?? "";
}

function hasSecret(key: string): boolean {
  return savedSecrets.value.has(key);
}

/** 把草稿回填到表单。回填过程不算「改动」，否则刚进页面每一步都会被判成需要重新验证。 */
function applyDraft(values: Record<string, string>, secrets: string[], completed: SetupStepId[]) {
  applyingDraft.value = true;
  database.url = values.DB_URL ?? database.url;
  database.username = values.DB_USERNAME ?? database.username;
  redis.host = values.REDIS_HOST ?? redis.host;
  redis.port = values.REDIS_PORT || redis.port;
  keytop.base_url = values.KEYTOP_BASE_URL ?? keytop.base_url;
  keytop.app_id = values.KEYTOP_APP_ID ?? keytop.app_id;
  keytop.park_id = values.KEYTOP_PARK_ID ?? keytop.park_id;
  keytop.park_name = values.KEYTOP_PARK_NAME ?? keytop.park_name;
  storage.storage_root = values.FILE_STORAGE_ROOT ?? storage.storage_root;
  storage.base_url = values.FILE_BASE_URL ?? storage.base_url;
  sms.access_key_id = values.SMS_ACCESS_KEY_ID ?? sms.access_key_id;
  sms.sign_name = values.SMS_SIGN_NAME ?? sms.sign_name;
  sms.template_code = values.SMS_TEMPLATE_CODE ?? sms.template_code;
  administrator.username = values.ADMIN_INITIALIZER_USERNAME ?? administrator.username;

  savedSecrets.value = new Set(secrets);
  doneSteps.value = new Set(completed);
  // 回填刚落地的这一刻，用户还什么都没改；不清掉脏标记，已完成的步骤会被要求重新验证一遍。
  Object.keys(dirty).forEach((key) => {
    dirty[key] = false;
  });
  currentIndex.value = Math.min(firstPendingIndex(completed), steps.length - 1);
  applyingDraft.value = false;
}

function firstPendingIndex(completed: SetupStepId[]): number {
  const index = steps.findIndex((step) => step.id !== "review" && !completed.includes(step.id as SetupStepId));
  return index === -1 ? steps.length - 1 : index;
}

// flush: 'sync' 才能让 applyingDraft 这个开关真正起作用：默认的 pre 刷新会把回填造成的变更
// 推迟到下一个微任务，那时开关已经复位，整页字段都会被误判成「改过」。
watch(database, () => markDirty("database"), {deep: true, flush: "sync"});
watch(redis, () => markDirty("redis"), {deep: true, flush: "sync"});
watch(keytop, () => markDirty("keytop"), {deep: true, flush: "sync"});
watch(storage, () => markDirty("storage"), {deep: true, flush: "sync"});
watch(sms, () => markDirty("sms"), {deep: true, flush: "sync"});
watch(administrator, () => markDirty("administrator"), {deep: true, flush: "sync"});

function markDirty(id: string) {
  if (!applyingDraft.value) {
    dirty[id] = true;
  }
}

function clearMessages() {
  errorMessage.value = "";
  successMessage.value = "";
  successStepId.value = null;
}

/** 下载基址默认取后端地址：实施人员填的多半就是它，少一个要凭记忆敲的字段。 */
function defaultStorageBaseUrl(): string {
  const configured = useRuntimeConfig().public.baseUrl as string | undefined;
  if (!configured) {
    return "";
  }
  return configured.replace(/\/api\/?$/, "");
}

async function unlock() {
  clearMessages();
  busy.value = true;
  setup.rememberToken(tokenInput.value);
  try {
    const draft = await setup.draft();
    applyDraft(draft.values, draft.configured_secrets, draft.completed_steps);
    phase.value = "wizard";
    successMessage.value = "";
    successStepId.value = null;
  } catch (error: unknown) {
    // 口令不对就不要再留着它：下一次请求带着同样的错误口令只会得到同样的 403。
    setup.rememberToken("");
    errorMessage.value = setup.describeFailure(error);
  } finally {
    busy.value = false;
  }
}

function jump(index: number) {
  clearMessages();
  currentIndex.value = index;
}

function back() {
  clearMessages();
  currentIndex.value = Math.max(0, currentIndex.value - 1);
}

function markDone(id: StepId, message: string) {
  doneSteps.value = new Set([...doneSteps.value, id]);
  dirty[id] = false;
  successStepId.value = id;
  successMessage.value = message;
}

async function verifyCurrent(): Promise<void> {
  const id = currentStep.value.id;
  if (id === "database") {
    const result = await setup.verifyDatabase({...database});
    markDone(
        "database",
        `已连接 ${result.product} ${result.version} · 库 ${result.database} · ` +
        (result.tables > 0 ? `已有 ${result.tables} 张表` : "空库，将自动建表"),
    );
    return;
  }
  if (id === "redis") {
    const result = await setup.verifyRedis({...redis});
    markDone("redis", `已连接 Redis ${result.version}，会话使用 ${result.database} 号库`);
    return;
  }
  if (id === "keytop") {
    const result = await setup.verifyKeytop({...keytop});
    markDone(
        "keytop",
        `平台返回「${result.message}」` + (result.areas === null ? "" : `，车场共 ${result.areas} 个区域`),
    );
    return;
  }
  if (id === "storage") {
    const result = await setup.verifyStorage({...storage});
    markDone("storage", `存储目录可写：${result.root}`);
    return;
  }
  if (id === "sms") {
    const result = await setup.verifySms({...sms});
    markDone("sms", `已向 ${result.phone} 发送测试短信，验证码为 ${result.code}，请确认收到`);
    return;
  }
  if (id === "administrator") {
    const result = await setup.saveAdministrator({...administrator});
    markDone("administrator", `管理员 ${result.username} 已登记，将在服务重启时创建`);
  }
}

async function next() {
  clearMessages();
  const id = currentStep.value.id;

  // 已经验证过、且没有再改动：直接前进，不必逼着操作者把密码重敲一遍（草稿里的值仍然有效）。
  if (isDone(id) && !dirty[id]) {
    currentIndex.value = Math.min(currentIndex.value + 1, steps.length - 1);
    return;
  }

  busy.value = true;
  try {
    await verifyCurrent();
    currentIndex.value = Math.min(currentIndex.value + 1, steps.length - 1);
  } catch (error: unknown) {
    errorMessage.value = setup.describeFailure(error);
    successMessage.value = "";
    successStepId.value = null;
  } finally {
    busy.value = false;
  }
}

async function skipSms() {
  clearMessages();
  busy.value = true;
  try {
    await setup.skipSms();
    markDone("sms", "已跳过短信配置，稍后可在配置文件中补上");
    currentIndex.value = Math.min(currentIndex.value + 1, steps.length - 1);
  } catch (error: unknown) {
    errorMessage.value = setup.describeFailure(error);
  } finally {
    busy.value = false;
  }
}

async function finish() {
  clearMessages();
  busy.value = true;
  try {
    const result = await setup.complete(import.meta.client ? window.location.origin : "");
    envFile.value = result.env_file;
    backupFile.value = result.backup_file ?? "";
    phase.value = "restarting";
    void waitForRestart();
  } catch (error: unknown) {
    errorMessage.value = setup.describeFailure(error);
  } finally {
    busy.value = false;
  }
}

/**
 * 等后端重启完成。
 *
 * 判据是 `/setup/status` 回答 `setup_required: false`：这个路径在两套应用里都存在，配置模式下答 true、
 * 正常模式下答 false，因此既能区分「还在重启的空窗」（请求直接失败）也能区分「已经起来了」。
 * 只认一次成功不够——重启期间旧进程可能还没完全放开端口，会答出最后一次 true。
 */
async function waitForRestart() {
  for (let attempt = 0; attempt < RESTART_POLL_ATTEMPTS; attempt += 1) {
    await new Promise((resolve) => setTimeout(resolve, RESTART_POLL_INTERVAL_MS));
    try {
      const status = await setup.status();
      if (!status.setup_required) {
        restarted.value = true;
        await new Promise((resolve) => setTimeout(resolve, 1200));
        await goLogin();
        return;
      }
    } catch {
      // 旧进程已关闭、新进程尚未监听：正是要等的那个窗口。
    }
  }
  // 等不到就明说。停在这一屏不吭声，操作者只会以为还在启动，而真正的原因在服务日志里。
  restartTimedOut.value = true;
}

async function goLogin() {
  await navigateTo("/login", {replace: true});
}

useHead({
  title: "系统配置引导",
  link: [
    {
      rel: "stylesheet",
      href: "https://fonts.googleapis.com/icon?family=Material+Icons+Outlined",
    },
  ],
});

onMounted(async () => {
  storage.base_url = defaultStorageBaseUrl();

  try {
    const status = await setup.status();
    if (!status.setup_required) {
      phase.value = "already";
      return;
    }
    envFile.value = status.env_file ?? envFile.value;
  } catch {
    // 状态接口不可达：仍显示口令闸门，解锁时会给出更具体的连接失败提示。
  }

  phase.value = "locked";
  // 口令在本会话里还留着（例如刷新页面）：直接试着用它换草稿，省一次输入。
  const remembered = setup.token.value.trim();
  if (remembered) {
    tokenInput.value = remembered;
    await unlock();
  }
});
</script>

<style scoped>
/* ==========================================================================
   设计方向：与登录页同一套——浅色混凝土场地 + 车位线透视 + 蓝色动作色，
   页头的道闸沿用同一个标志。操作者是连着打开这两个页面的，
   引导页看起来像另一个系统的产物会很突兀。

   与登录页的不同在于，这里信息量大得多：左边是步骤轨道，右边是当前步骤。
   全页唯一的强调色仍然是蓝色；绿色只用来表示「已验证通过」。
   ========================================================================== */
.setup-page {
  --s-bg: #eef2f7;
  --s-bg-glow: rgb(37 99 235 / 12%);
  --s-lane: rgb(15 23 42 / 8%);
  --s-panel: #fff;
  --s-surface: rgb(15 23 42 / 4%);
  --s-inset: #f8fafc;
  --s-line: rgb(15 23 42 / 8%);
  --s-line-strong: rgb(15 23 42 / 14%);
  --s-text: #0f172a;
  --s-sub: #475569;
  --s-mute: #64748b;
  --s-accent: #3b82f6;
  --s-accent-deep: #2563eb;
  --s-accent-text: #1d4ed8;
  --s-mark: #f5c518;
  --s-post: #64748b;
  --s-ok: #059669;
  --s-ok-bg: #ecfdf5;
  --s-danger: #dc2626;
  --s-danger-bg: #fef2f2;
  --s-danger-border: #fecaca;
  --s-card-shadow: 0 18px 48px rgb(15 23 42 / 12%);
  --s-tr: .18s ease;

  align-items: center;
  background: var(--s-bg);
  color: var(--s-text);
  display: flex;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 13px;
  justify-content: center;
  line-height: normal;
  min-height: 100dvh;
  overflow: hidden;
  padding: 24px;
  position: relative;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}

/* 地面车位线：与登录页同一处透视，向远处收拢并淡出 */
.setup-page::before {
  content: "";
  position: absolute;
  inset: 58% -30% -12% -30%;
  background-image: repeating-linear-gradient(90deg, var(--s-lane) 0 1px, transparent 1px 96px);
  transform: perspective(520px) rotateX(64deg);
  transform-origin: bottom center;
  -webkit-mask-image: linear-gradient(to top, rgb(0 0 0 / 85%), transparent 80%);
  mask-image: linear-gradient(to top, rgb(0 0 0 / 85%), transparent 80%);
  pointer-events: none;
}

.setup-page::after {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(58% 40% at 50% 0%, var(--s-bg-glow), transparent 72%);
  pointer-events: none;
}

/* ====== 道闸标志：与登录页同一个造型，只是不参与动效 ====== */
.mark {
  flex: 0 0 auto;
  height: 46px;
  position: relative;
  width: 54px;
}

.mark__lane {
  background: var(--s-line-strong);
  bottom: 4px;
  height: 1px;
  left: 0;
  position: absolute;
  right: 0;
}

.mark__post {
  background: var(--s-post);
  border-radius: 2px;
  bottom: 4px;
  height: 16px;
  position: absolute;
  right: 0;
  width: 6px;
}

.mark__arm {
  background: repeating-linear-gradient(115deg, var(--s-mark) 0 6px, #1f2937 6px 12px);
  border-radius: 3px;
  bottom: 18px;
  height: 4px;
  position: absolute;
  right: 2px;
  transform: rotate(-58deg);
  transform-origin: right center;
  width: 32px;
}

/* 已完成 / 重启中用图标替掉道闸 */
.mark--ok, .mark--spin {
  align-items: center;
  background: var(--s-ok-bg);
  border: 1px solid rgb(5 150 105 / 24%);
  border-radius: 12px;
  color: var(--s-ok);
  display: flex;
  height: 46px;
  justify-content: center;
  width: 46px;
}

.mark--spin {
  animation: setupSpin 1.6s linear infinite;
  color: var(--s-accent-text);
  background: var(--s-surface);
  border-color: var(--s-line-strong);
}

.mark--warn {
  align-items: center;
  background: var(--s-danger-bg);
  border: 1px solid var(--s-danger-border);
  border-radius: 12px;
  color: var(--s-danger);
  display: flex;
  height: 46px;
  justify-content: center;
  width: 46px;
}

@keyframes setupSpin {
  to {
    transform: rotate(360deg);
  }
}

/* ====== 口令闸门 / 完成提示：单卡片居中 ====== */
.gate {
  position: relative;
  z-index: 1;
  width: 440px;
  max-width: 100%;
}

.gate__card {
  background: var(--s-panel);
  border: 1px solid var(--s-line-strong);
  border-radius: 16px;
  box-shadow: var(--s-card-shadow);
  padding: 26px 30px 30px;
  animation: setupIn .45s cubic-bezier(.22, .9, .28, 1);
}

@keyframes setupIn {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.gate__head {
  align-items: center;
  display: flex;
  gap: 14px;
  margin-bottom: 22px;
}

.gate__title {
  color: var(--s-text);
  font-size: 19px;
  font-weight: 700;
  margin: 0;
}

.gate__desc {
  color: var(--s-mute);
  font-size: 12px;
  margin: 5px 0 0;
}

.gate__hint {
  color: var(--s-mute);
  font-size: 12px;
  line-height: 1.7;
  margin: 16px 0 0;
}

.gate__hint code, .rail__note code, .form__note code, .field__hint code {
  background: var(--s-surface);
  border: 1px solid var(--s-line);
  border-radius: 5px;
  color: var(--s-sub);
  font-family: ui-monospace, Consolas, 'SFMono-Regular', monospace;
  font-size: 11.5px;
  padding: 1px 5px;
  overflow-wrap: anywhere;
}

/* ====== 向导外壳 ====== */
.shell {
  background: var(--s-panel);
  border: 1px solid var(--s-line-strong);
  border-radius: 16px;
  box-shadow: var(--s-card-shadow);
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  max-width: 1000px;
  min-height: 560px;
  overflow: hidden;
  position: relative;
  width: 100%;
  z-index: 1;
  animation: setupIn .45s cubic-bezier(.22, .9, .28, 1);
}

/* ====== 左侧步骤轨道 ====== */
.rail {
  background: var(--s-inset);
  border-right: 1px solid var(--s-line);
  display: flex;
  flex-direction: column;
  padding: 22px 16px 20px;
}

/* 侧栏里不放道闸标志：46px 高的标志和两行文字挤在同一行，栏杆会顶到标题的笔画上，
  实机截图看着像一处错位。道闸留给口令闸门那几张卡片——那里它是主角，也有地方展开。 */
.brand {
  padding: 0 6px 20px;
}

.brand__text {
  min-width: 0;
}

.brand__title {
  color: var(--s-text);
  font-size: 15px;
  font-weight: 700;
  margin: 0;
}

.brand__desc {
  color: var(--s-mute);
  font-size: 11px;
  letter-spacing: .06em;
  margin: 3px 0 0;
}

.steps {
  flex: 1;
  list-style: none;
  margin: 0;
  padding: 0;
}

.step {
  position: relative;
}

/* 步骤之间的连接线画在标记圆心下方；最后一个不画 */
.step:not(:last-child)::after {
  background: var(--s-line-strong);
  bottom: 4px;
  content: "";
  left: 22px;
  position: absolute;
  top: 30px;
  width: 1px;
}

.step--done:not(:last-child)::after {
  background: rgb(5 150 105 / 35%);
}

.step__button {
  align-items: center;
  background: none;
  border: 0;
  border-radius: 9px;
  cursor: pointer;
  display: flex;
  font: inherit;
  gap: 11px;
  padding: 7px 8px;
  text-align: left;
  transition: background var(--s-tr);
  width: 100%;
}

.step__button:hover {
  background: var(--s-surface);
}

.step__marker {
  align-items: center;
  background: var(--s-panel);
  border: 1px solid var(--s-line-strong);
  border-radius: 50%;
  color: var(--s-mute);
  display: flex;
  flex: 0 0 22px;
  font-size: 11px;
  font-weight: 600;
  height: 22px;
  justify-content: center;
  position: relative;
  transition: background var(--s-tr), border-color var(--s-tr), color var(--s-tr);
  width: 22px;
  z-index: 1;
}

.step__marker .material-icons-outlined {
  font-size: 14px;
}

.step__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.step__title {
  color: var(--s-sub);
  font-size: 13px;
  font-weight: 500;
}

.step__hint {
  color: var(--s-mute);
  font-size: 11px;
}

.step--done .step__marker {
  background: var(--s-ok);
  border-color: var(--s-ok);
  color: var(--on-solid);
}

.step--done .step__title {
  color: var(--s-text);
}

.step--active .step__button {
  background: var(--s-panel);
  box-shadow: 0 1px 3px rgb(15 23 42 / 10%);
}

.step--active .step__marker {
  border-color: var(--s-accent);
  box-shadow: 0 0 0 3px rgb(59 130 246 / 15%);
  color: var(--s-accent-text);
}

.step--active.step--done .step__marker {
  background: var(--s-ok);
  border-color: var(--s-ok);
  box-shadow: 0 0 0 3px rgb(5 150 105 / 15%);
  color: var(--on-solid);
}

.step--active .step__title {
  color: var(--s-accent-text);
  font-weight: 600;
}

.rail__note {
  border-top: 1px solid var(--s-line);
  color: var(--s-mute);
  font-size: 11px;
  line-height: 1.8;
  margin: 16px 0 0;
  padding: 14px 6px 0;
}

/* ====== 右侧面板 ====== */
.panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 26px 30px 22px;
}

.panel__head {
  margin-bottom: 20px;
}

.panel__title {
  color: var(--s-text);
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.panel__desc {
  color: var(--s-sub);
  font-size: 12.5px;
  line-height: 1.7;
  margin: 6px 0 0;
}

.panel__foot {
  align-items: center;
  display: flex;
  gap: 10px;
  margin-top: auto;
  padding-top: 22px;
}

.panel__spacer {
  flex: 1;
}

/* ====== 表单 ====== */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form__row {
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr 1fr;
}

/* 主机名长、端口短，等分会把端口框撑成一块空白 */
.form__row--host {
  grid-template-columns: minmax(0, 1fr) 118px;
}

.form__note {
  background: var(--s-surface);
  border: 1px solid var(--s-line);
  border-radius: 9px;
  color: var(--s-sub);
  font-size: 12.5px;
  line-height: 1.75;
  margin: 0;
  padding: 12px 14px;
}

.field {
  display: block;
  min-width: 0;
}

.field__label {
  color: var(--s-sub);
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 6px;
}

.field__wrap {
  position: relative;
}

.field__wrap .material-icons-outlined {
  color: var(--s-mute);
  font-size: 18px;
  left: 12px;
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
}

.field__input {
  background: var(--s-panel);
  border: 1px solid var(--s-line-strong);
  border-radius: 9px;
  color: var(--s-text);
  font-size: 13.5px;
  height: 42px;
  outline: none;
  padding: 0 12px 0 38px;
  transition: border-color var(--s-tr), box-shadow var(--s-tr);
  width: 100%;
}

.field__input--token {
  font-family: ui-monospace, Consolas, 'SFMono-Regular', monospace;
  font-size: 16px;
  letter-spacing: .22em;
  text-transform: uppercase;
}

.field__input::placeholder {
  color: var(--s-mute);
  opacity: 1;
}

/* 字距是为了让抄进来的口令逐位可读；占位符不是口令，被拉开只会显得排版出错。 */
.field__input--token::placeholder {
  font-family: inherit;
  font-size: 13px;
  letter-spacing: normal;
  text-transform: none;
}

.field__input:focus-visible, .field__input:focus {
  border-color: var(--s-accent);
  box-shadow: 0 0 0 3px rgb(59 130 246 / 15%);
}

.field__hint {
  color: var(--s-mute);
  display: block;
  font-size: 11.5px;
  line-height: 1.7;
  margin-top: 6px;
}

/* ====== 按钮 ====== */
.btn {
  border: 1px solid transparent;
  border-radius: 9px;
  cursor: pointer;
  font: inherit;
  font-size: 13.5px;
  font-weight: 600;
  height: 42px;
  padding: 0 22px;
  transition: background var(--s-tr), border-color var(--s-tr), box-shadow var(--s-tr), filter var(--s-tr);
  white-space: nowrap;
}

.btn--primary {
  background: linear-gradient(180deg, var(--s-accent-deep), var(--s-accent-text));
  box-shadow: 0 8px 20px rgb(37 99 235 / 24%);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  box-shadow: 0 10px 26px rgb(37 99 235 / 32%);
  filter: brightness(1.05);
}

.btn--ghost {
  background: var(--s-panel);
  border-color: var(--s-line-strong);
  color: var(--s-sub);
}

.btn--ghost:hover:not(:disabled) {
  background: var(--s-surface);
  color: var(--s-text);
}

.btn:disabled {
  box-shadow: none;
  cursor: not-allowed;
  opacity: .5;
}

.gate__card .btn {
  width: 100%;
}

/* ====== 提示条 ====== */
.alert {
  border-radius: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  margin: 16px 0 0;
  padding: 10px 13px;
}

.alert--error {
  background: var(--s-danger-bg);
  border: 1px solid var(--s-danger-border);
  color: var(--s-danger);
}

.alert--ok {
  background: var(--s-ok-bg);
  border: 1px solid rgb(5 150 105 / 22%);
  color: var(--success-text);
}

.gate__card .alert {
  margin-bottom: 0;
}

/* ====== 确认页摘要 ====== */
.review {
  display: grid;
  gap: 1px;
  margin: 0;
  background: var(--s-line);
  border: 1px solid var(--s-line);
  border-radius: 9px;
  overflow: hidden;
}

.review dt, .review dd {
  background: var(--s-panel);
  font-size: 12.5px;
  margin: 0;
  padding: 9px 13px;
}

.review dt {
  color: var(--s-mute);
}

.review dd {
  color: var(--s-text);
  overflow-wrap: anywhere;
}

.review dd code {
  background: none;
  border: 0;
  padding: 0;
  font-size: 12.5px;
}

.review {
  grid-template-columns: 132px minmax(0, 1fr);
}

.chip {
  background: var(--s-ok-bg);
  border: 1px solid rgb(5 150 105 / 22%);
  border-radius: 4px;
  color: var(--success-text);
  font-size: 11px;
  padding: 2px 8px;
}

/* 键盘可达性 */
.step__button:focus-visible, .btn:focus-visible {
  outline: 2px solid var(--s-accent);
  outline-offset: 2px;
}

/* ====== 窄屏：轨道改成横向条，面板占满 ====== */
@media (max-width: 860px) {
  .setup-page {
    align-items: flex-start;
    padding: 16px;
    overflow: auto;
  }

  .setup-page::before {
    display: none;
  }

  .shell {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
  }

  .rail {
    border-right: 0;
    border-bottom: 1px solid var(--s-line);
    padding: 18px 16px 12px;
  }

  .brand {
    padding-bottom: 14px;
  }

  .steps {
    display: flex;
    gap: 4px;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .step:not(:last-child)::after {
    display: none;
  }

  .step__button {
    flex-direction: column;
    gap: 6px;
    min-width: 76px;
    padding: 6px 4px;
    text-align: center;
  }

  .step__text {
    align-items: center;
  }

  .step__hint {
    display: none;
  }

  .rail__note {
    display: none;
  }

  .panel {
    padding: 20px 18px 18px;
  }
}

@media (max-width: 560px) {
  .form__row {
    grid-template-columns: minmax(0, 1fr);
  }

  .review {
    grid-template-columns: minmax(0, 1fr);
  }

  .review dt {
    padding-bottom: 2px;
  }

  .review dd {
    padding-top: 2px;
  }

  .panel__foot {
    flex-wrap: wrap;
  }

  .panel__foot .btn {
    flex: 1;
    padding: 0 14px;
  }

  .panel__spacer {
    display: none;
  }

  /* 16px 以下 iOS 会自动放大表单，必须保持 16px */
  .field__input {
    font-size: 16px;
    height: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .gate__card, .shell, .mark--spin {
    animation: none;
  }
}

/* ==========================================================================
   深色：与登录页同一套夜间场地
   变量逐项对应浅色，只改颜色值。层次关系照旧：右侧面板是最亮的一层，
   左侧轨道再深一档，与浅色下「面板 #fff、轨道 #f8fafc」的先后一致。
   栏杆黄（路面标线色）两种主题下都够亮，不动。
   ========================================================================== */
[data-theme="dark"] .setup-page {
  --s-bg: #18181b;
  --s-bg-glow: rgb(96 165 250 / 14%);
  --s-lane: rgb(250 250 250 / 8%);
  --s-panel: #27272a;
  --s-surface: rgb(0 0 0 / 20%); /* 比面板更深一档：提示块、代码底色、悬停态都靠它缩进去 */
  --s-inset: rgb(0 0 0 / 14%); /* 步骤轨道：比面板深一点，但比 surface 浅 */
  --s-line: rgb(250 250 250 / 9%);
  --s-line-strong: #3f3f46;
  --s-text: #fafafa;
  --s-sub: #d4d4d8;
  --s-mute: #a1a1aa;
  --s-accent: #60a5fa;
  --s-accent-text: #93c5fd;
  --s-post: #a1a1aa; /* 立柱：浅色下是深灰，深色下反过来用浅灰才立得住 */
  --s-ok: #68d4a6;
  --s-ok-bg: #15352a;
  --s-danger: #f87171;
  --s-danger-bg: #3b2023;
  --s-danger-border: #6b3438;
  --s-card-shadow: 0 18px 48px rgb(0 0 0 / 45%);
}

/* 主按钮的渐变同样只取深蓝两档。深色下 --s-accent-text 提亮成了浅蓝，白字压上去读不出来，
   而这条按钮的对比度取舍与主题无关，所以把浅色的两个值写回来，不跟着变量走。 */
[data-theme="dark"] .setup-page .btn--primary {
  background: linear-gradient(180deg, #2563eb, #1d4ed8);
}
</style>
