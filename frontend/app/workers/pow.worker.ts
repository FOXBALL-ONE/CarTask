interface PowRequest {
  type: "solve";
  requestId: number;
  challengeId: string;
  purpose: string;
  seed: string;
  difficultyBits: number;
}

self.onmessage = async (event: MessageEvent<PowRequest>) => {
  const request = event.data;
  if (request.type !== "solve") return;
  const encoder = new TextEncoder();
  const target = request.difficultyBits;
  for (let nonce = 0; nonce <= Number.MAX_SAFE_INTEGER; nonce += 1) {
    const input = `shopmall-pow:v1:${request.challengeId}:${request.purpose}:${request.seed}:${nonce}`;
    const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(input)));
    let zeroBits = 0;
    for (const byte of digest) {
      if (byte === 0) {
        zeroBits += 8;
        continue;
      }
      zeroBits += Math.clz32(byte) - 24;
      break;
    }
    if (zeroBits >= target) {
      self.postMessage({type: "solved", requestId: request.requestId, nonce: String(nonce)});
      return;
    }
    if (nonce % 2048 === 0) {
      self.postMessage({type: "progress", requestId: request.requestId, attempts: nonce});
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  }
  self.postMessage({type: "failed", requestId: request.requestId});
};
