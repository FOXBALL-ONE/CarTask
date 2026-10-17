<template>
  <main class="report-form-page">
    <header class="form-header">
      <button aria-label="返回我的举报" class="back-button" type="button" @click="navigateTo('/wx/violation')">
        <svg aria-hidden="true" viewBox="0 0 24 24"><path d="m15 18-6-6 6-6" /></svg>
      </button>
      <div><h1>新增举报</h1><p>请提供清晰、准确的现场信息</p></div>
    </header>

    <form class="report-form" @submit.prevent="submitReport">
      <section class="form-intro">
        <span class="intro-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 3 3.8 7.5v5.8c0 4 3.5 6.8 8.2 7.7 4.7-.9 8.2-3.7 8.2-7.7V7.5L12 3Z"/><path d="M12 8v5m0 3h.01"/></svg></span>
        <div><strong>文明出行，从及时反馈开始</strong><p>提交的信息仅用于核实处理，请尽量填写完整。</p></div>
      </section>

      <section class="form-section" aria-labelledby="vehicle-section-title">
        <h2 id="vehicle-section-title"><span>01</span> 违规车辆</h2>
        <label class="field">
          <span>车牌号码 <i>*</i></span>
          <input v-model.trim="form.vehicleNumber" autocomplete="off" autocapitalize="characters" maxlength="64" placeholder="例如：闽A12345" required>
        </label>
        <label class="field">
          <span>违规类型 <i>*</i></span>
          <input v-model.trim="form.violationType" :list="types.length ? 'violation-type-options' : undefined" autocomplete="off" maxlength="64" placeholder="请选择或输入违规类型" required>
          <datalist id="violation-type-options"><option v-for="type in types" :key="type.id" :value="type.name">{{ type.description || type.name }}</option></datalist>
          <small v-if="typesError" class="field-hint field-hint--error">{{ typesError }}，仍可手动填写类型</small>
          <small v-else class="field-hint">选择列表类型，或直接填写其他情况</small>
        </label>
      </section>

      <section class="form-section" aria-labelledby="details-section-title">
        <h2 id="details-section-title"><span>02</span> 发生情况</h2>
        <label class="field">
          <span>发生时间 <i>*</i></span>
          <input v-model="form.violationTime" type="datetime-local" required>
        </label>
        <label class="field">
          <span>发生地点</span>
          <input v-model.trim="form.location" maxlength="255" placeholder="例如：办公楼东侧停车区">
        </label>
        <label class="field">
          <span>违规说明 <i>*</i></span>
          <textarea v-model.trim="form.description" maxlength="1024" placeholder="请描述现场情况，便于工作人员核实" required rows="4" />
          <small class="field-hint">{{ form.description.length }} / 1024</small>
        </label>
      </section>

      <section class="form-section evidence-section" aria-labelledby="evidence-section-title">
        <div class="evidence-heading"><div><h2 id="evidence-section-title"><span>03</span> 现场图片</h2><p>支持 JPG、PNG、GIF、WebP、BMP，单张不超过 5MB</p></div><span class="optional-tag">选填</span></div>
        <input ref="fileInput" accept="image/jpeg,image/png,image/gif,image/webp,image/bmp" class="visually-hidden" type="file" @change="selectEvidence">
        <input ref="cameraInput" accept="image/jpeg,image/png,image/gif,image/webp,image/bmp" capture="environment" class="visually-hidden" type="file" @change="selectEvidence">
        <div v-if="!evidencePreview" class="upload-options">
          <button class="upload-tile" type="button" @click="fileInput?.click()">
            <span class="upload-icon"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 16V4m-4 4 4-4 4 4"/><path d="M5 14v5h14v-5"/></svg></span>
            <strong>从相册选择</strong><small>选择已有现场图片</small>
          </button>
          <button class="upload-tile upload-tile--camera" type="button" @click="cameraInput?.click()">
            <span class="upload-icon"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 8h3l1.5-2h7L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg></span>
            <strong>拍摄照片</strong><small>打开摄像头拍摄上传</small>
          </button>
        </div>
        <div v-else class="evidence-preview">
          <img :src="evidencePreview" alt="现场图片预览">
          <button aria-label="移除现场图片" class="remove-image" type="button" @click="removeEvidence"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
          <div class="replace-actions"><button type="button" @click="fileInput?.click()">从相册更换</button><button type="button" @click="cameraInput?.click()">重新拍摄</button></div>
        </div>
        <p v-if="formError" class="form-error" role="alert"><svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v5m0 3h.01"/></svg>{{ formError }}</p>
      </section>

      <footer class="form-footer">
        <p><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 3 4 6v5c0 5 3.4 8.2 8 10 4.6-1.8 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/></svg>提交即表示所填信息真实有效</p>
        <button class="submit-button" :disabled="saving" type="submit">
          <svg v-if="saving" class="submit-spinner" aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" /></svg>
          <svg v-else aria-hidden="true" viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" /></svg>
          {{ saving ? '正在提交…' : '提交举报' }}
        </button>
      </footer>
    </form>
  </main>
</template>

<script setup lang="ts">
interface ViolationTypeOption {
  id: number;
  name: string;
  description: string | null;
}

interface ViolationTypeList {
  items: ViolationTypeOption[];
}

definePageMeta({layout: false});

const http = useHttp();
const types = ref<ViolationTypeOption[]>([]);
const typesError = ref("");
const formError = ref("");
const saving = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const cameraInput = ref<HTMLInputElement | null>(null);
const evidenceFile = ref<File | null>(null);
const evidencePreview = ref("");
const form = reactive({
  vehicleNumber: "",
  violationType: "",
  violationTime: nowForInput(),
  location: "",
  description: "",
});

function nowForInput() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 16);
}

async function loadTypes() {
  try {
    const result = await http.get<ViolationTypeList>("/wx/violation-reports/types");
    types.value = result.items || [];
  } catch (error) {
    typesError.value = (error as {statusMessage?: string}).statusMessage || "违规类型列表暂不可用";
  }
}

function selectEvidence(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  formError.value = "";
  if (!file.type.startsWith("image/")) {
    formError.value = "请选择图片文件";
    input.value = "";
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    formError.value = "图片不能超过 5MB";
    input.value = "";
    return;
  }
  if (evidencePreview.value) URL.revokeObjectURL(evidencePreview.value);
  evidenceFile.value = file;
  evidencePreview.value = URL.createObjectURL(file);
}

function removeEvidence() {
  if (evidencePreview.value) URL.revokeObjectURL(evidencePreview.value);
  evidencePreview.value = "";
  evidenceFile.value = null;
  if (fileInput.value) fileInput.value.value = "";
  if (cameraInput.value) cameraInput.value.value = "";
}

async function submitReport() {
  formError.value = "";
  saving.value = true;
  try {
    const payload = new FormData();
    payload.append("vehicle_number", form.vehicleNumber.toUpperCase());
    payload.append("violation_type", form.violationType);
    payload.append("violation_time", `${form.violationTime}:00`);
    if (form.location) payload.append("location", form.location);
    payload.append("description", form.description);
    if (evidenceFile.value) payload.append("evidence", evidenceFile.value);
    await http.post("/wx/violation-reports", payload, {payloadMode: "json"});
    await navigateTo({path: "/wx/violation", query: {submitted: "1"}});
  } catch (error) {
    formError.value = (error as {statusMessage?: string}).statusMessage || "提交失败，请检查网络后重试";
  } finally {
    saving.value = false;
  }
}

onMounted(loadTypes);
onBeforeUnmount(() => {
  if (evidencePreview.value) URL.revokeObjectURL(evidencePreview.value);
});
</script>

<style scoped>
:global(html), :global(body), :global(#__nuxt) { background: #f4f6f8; min-height: 100%; margin: 0; }
.report-form-page { background: #f4f6f8; color: #252d34; display: flex; flex-direction: column; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; height: 100dvh; margin: 0 auto; max-width: 458px; overflow: hidden; }
.form-header { align-items: center; background: #fff; border-bottom: 1px solid #edf0f2; display: flex; flex: 0 0 70px; gap: 12px; padding: 0 16px; }
.back-button { align-items: center; background: transparent; border: 0; color: #59636d; display: inline-flex; flex: 0 0 32px; height: 38px; justify-content: center; margin-left: -8px; padding: 0; }
.back-button svg { fill: none; height: 22px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; width: 22px; }
.form-header h1 { color: #20272e; font-size: 16px; font-weight: 650; line-height: 1.3; margin: 0; }
.form-header p { color: #98a1aa; font-size: 11px; margin: 4px 0 0; }
.report-form { flex: 1; min-height: 0; overflow-y: auto; padding: 13px 16px 20px; }
.form-intro { align-items: center; background: #eaf5ff; border: 1px solid #dceeff; border-radius: 11px; display: flex; gap: 10px; margin-bottom: 15px; padding: 12px; }
.intro-mark { align-items: center; background: #fff; border-radius: 9px; color: #2589dc; display: inline-flex; flex: 0 0 36px; height: 36px; justify-content: center; }
.intro-mark svg { fill: none; height: 21px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 21px; }
.form-intro strong { color: #317ab3; display: block; font-size: 11px; font-weight: 650; }
.form-intro p { color: #7897ae; font-size: 10px; line-height: 1.5; margin: 4px 0 0; }
.form-section { background: #fff; border: 1px solid #edf0f3; border-radius: 12px; margin-bottom: 11px; padding: 14px 13px 3px; }
.form-section h2 { align-items: center; color: #313a42; display: flex; font-size: 13px; font-weight: 650; gap: 7px; margin: 0 0 14px; }
.form-section h2 span { align-items: center; background: #edf6ff; border-radius: 5px; color: #2387d7; display: inline-flex; font-size: 9px; height: 19px; justify-content: center; width: 22px; }
.field { display: grid; gap: 7px; margin-bottom: 13px; }
.field > span { color: #4b565f; font-size: 11px; font-weight: 550; }
.field > span i { color: #ed6b63; font-style: normal; }
.field input, .field textarea { background: #fbfcfd; border: 1px solid #e7ebef; border-radius: 8px; box-sizing: border-box; color: #303941; font: inherit; font-size: 12px; outline: none; padding: 10px 11px; width: 100%; }
.field input { height: 40px; }
.field input::placeholder, .field textarea::placeholder { color: #b1bac2; }
.field input:focus, .field textarea:focus { background: #fff; border-color: #83bced; box-shadow: 0 0 0 3px rgb(54 145 220 / 9%); }
.field input[type="datetime-local"] { color: #59636d; }
.field textarea { line-height: 1.6; min-height: 95px; resize: vertical; }
.field-hint { color: #9aa4ad; font-size: 10px; margin-top: -2px; }
.field-hint--error { color: #cf766f; }
.evidence-section { padding-bottom: 13px; }
.evidence-heading { align-items: flex-start; display: flex; justify-content: space-between; }
.evidence-heading h2 { margin-bottom: 5px; }
.evidence-heading p { color: #9aa4ad; font-size: 10px; margin: 0 0 12px; }
.optional-tag { background: #f4f6f8; border-radius: 999px; color: #9ba4ad; font-size: 9px; padding: 4px 8px; }
.visually-hidden { display: none; }
.upload-options { display: grid; gap: 8px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.upload-tile { align-items: center; background: #fbfdff; border: 1px dashed #cddce8; border-radius: 9px; color: #65737f; display: flex; flex-direction: column; font: inherit; min-height: 112px; justify-content: center; padding: 8px 4px; width: 100%; }
.upload-tile--camera { background: #f4f9ff; border-color: #b9d7ef; }
.upload-icon { align-items: center; background: #edf6ff; border-radius: 50%; color: #288bda; display: inline-flex; height: 35px; justify-content: center; width: 35px; }
.upload-icon svg { fill: none; height: 19px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; width: 19px; }
.upload-tile strong { color: #52606c; font-size: 11px; font-weight: 600; margin-top: 8px; }
.upload-tile small { color: #a0aab3; font-size: 9px; margin-top: 4px; }
.evidence-preview { border-radius: 9px; max-width: 100%; overflow: hidden; position: relative; }
.evidence-preview img { background: #f4f6f8; display: block; max-height: 210px; max-width: 100%; object-fit: contain; }
.remove-image { align-items: center; background: rgb(24 32 40 / 72%); border: 0; border-radius: 50%; color: #fff; display: flex; height: 27px; justify-content: center; padding: 0; position: absolute; right: 8px; top: 8px; width: 27px; }
.remove-image svg { fill: none; height: 15px; stroke: currentColor; stroke-linecap: round; stroke-width: 1.8; width: 15px; }
.replace-actions { display: flex; gap: 7px; margin-top: 8px; }
.replace-actions button { background: #edf6ff; border: 0; border-radius: 6px; color: #287fbd; font: inherit; font-size: 10px; padding: 7px 10px; }
.form-error { align-items: flex-start; background: #fff1f0; border-radius: 8px; color: #c95c55; display: flex; font-size: 11px; gap: 7px; line-height: 1.5; margin: 12px 0 0; padding: 9px; }
.form-error svg { fill: none; flex: 0 0 auto; height: 15px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; width: 15px; }
.form-footer { padding: 2px 0 0; }
.form-footer p { align-items: center; color: #a0a9b1; display: flex; font-size: 10px; gap: 5px; justify-content: center; margin: 5px 0 11px; }
.form-footer p svg { fill: none; height: 14px; stroke: #77b795; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.6; width: 14px; }
.submit-button { align-items: center; background: #288fe5; border: 0; border-radius: 9px; box-shadow: 0 5px 12px rgb(40 143 229 / 18%); color: #fff; display: flex; font: inherit; font-size: 13px; font-weight: 650; gap: 7px; height: 44px; justify-content: center; width: 100%; }
.submit-button:disabled { cursor: wait; opacity: .65; }
.submit-button svg { fill: none; height: 17px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 17px; }
.submit-spinner { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
