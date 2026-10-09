import {useHttp} from "~/composables/useHttp";

export type PowPurpose = "LOGIN" | "SMS_SEND";
export type PowMode = "AUTO" | "MANUAL";

interface PowChallenge {
  challenge_id: string;
  purpose: PowPurpose;
  seed: string;
  difficulty_bits: number;
  expires_at: string;
  algorithm: string;
  protocol_version: number;
}

interface WorkerResult {
  type: "solved" | "progress" | "failed";
  requestId: number;
  nonce?: string;
  attempts?: number;
}

export function usePowVerification() {
  const http = useHttp();
  const status = ref<"idle" | "running" | "manual" | "verified" | "failed">("idle");
  const attempts = ref(0);
  const errorMessage = ref("");
  let worker: Worker | null = null;
  let requestId = 0;

  async function verify(purpose: PowPurpose, mode: PowMode = "AUTO") {
    if (!import.meta.client) throw new Error("POW 只能在浏览器中执行");
    const currentRequest = ++requestId;
    status.value = "running";
    errorMessage.value = "";
    attempts.value = 0;
    worker?.terminate();
    const challenge = await http.post<PowChallenge, { purpose: PowPurpose; mode: PowMode }>(
      "/verification/pow/challenges",
      {purpose, mode},
      {payloadMode: "json"},
    );
    const expiresAt = Date.parse(challenge.expires_at);
    const remaining = Math.max(1000, expiresAt - Date.now());
    const solved = await new Promise<string>((resolve, reject) => {
      const timer = window.setTimeout(() => {
        worker?.terminate();
        worker = null;
        reject(new Error("人机验证计算超时"));
      }, remaining);
      worker = new Worker(new URL("~/workers/pow.worker.ts", import.meta.url), {type: "module"});
      worker.onmessage = (event: MessageEvent<WorkerResult>) => {
        if (event.data.requestId !== currentRequest) return;
        if (event.data.type === "progress") {
          attempts.value = event.data.attempts ?? attempts.value;
        } else if (event.data.type === "solved" && event.data.nonce) {
          window.clearTimeout(timer);
          worker?.terminate();
          worker = null;
          resolve(event.data.nonce);
        } else if (event.data.type === "failed") {
          window.clearTimeout(timer);
          worker?.terminate();
          worker = null;
          reject(new Error("人机验证计算失败"));
        }
      };
      worker.onerror = () => {
        window.clearTimeout(timer);
        worker?.terminate();
        worker = null;
        reject(new Error("当前浏览器不支持人机验证"));
      };
      worker.postMessage({
        type: "solve",
        requestId: currentRequest,
        challengeId: challenge.challenge_id,
        purpose: challenge.purpose,
        seed: challenge.seed,
        difficultyBits: challenge.difficulty_bits,
      });
    });
    if (currentRequest !== requestId) throw new Error("人机验证已取消");
    status.value = "verified";
    return {powChallengeId: challenge.challenge_id, powNonce: solved};
  }

  function cancel() {
    requestId += 1;
    worker?.terminate();
    worker = null;
    status.value = "manual";
  }

  async function verifyWithFallback(purpose: PowPurpose) {
    try {
      return await verify(purpose, "AUTO");
    } catch (error) {
      status.value = "manual";
      errorMessage.value = error instanceof Error ? error.message : "人机验证失败，请点击重试";
      throw error;
    }
  }

  onUnmounted(() => {
    requestId += 1;
    worker?.terminate();
  });

  return {status, attempts, errorMessage, verify, verifyWithFallback, cancel};
}

export interface VerificationMode {
  pow_enabled: boolean;
  captcha_required: boolean;
  pow_replaces_captcha: boolean;
  pow_and_captcha_required: boolean;
  pow_fallback_to_captcha: boolean;
  sms_verification_enabled: boolean;
}
