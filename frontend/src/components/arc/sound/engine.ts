/** Web Audio playback adapted from soundcn's sound-engine (MIT). Adds a Safari fallback and a server guard. */
let audioContext: AudioContext | null = null;
const bufferCache = new Map<string, Promise<AudioBuffer>>();

type AudioContextCtor = typeof AudioContext;

function getAudioContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!audioContext) {
    const Ctor: AudioContextCtor | undefined = window.AudioContext ?? (window as unknown as { webkitAudioContext?: AudioContextCtor }).webkitAudioContext;
    if (!Ctor) return null;
    audioContext = new Ctor();
  }
  return audioContext;
}

async function readBytes(src: string): Promise<ArrayBuffer> {
  if (src.startsWith("data:")) {
    const binary = atob(src.slice(src.indexOf(",") + 1));
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }
  const response = await fetch(src);
  if (!response.ok) throw new Error(`sound ${src} could not be loaded`);
  return response.arrayBuffer();
}

/** `src` is a data URI or a path or URL to an audio file. Decoded buffers are cached per source. */
function decode(ctx: AudioContext, src: string): Promise<AudioBuffer> {
  const cached = bufferCache.get(src);
  if (cached) return cached;
  const pending = readBytes(src).then((bytes) => ctx.decodeAudioData(bytes));
  bufferCache.set(src, pending);
  pending.catch(() => bufferCache.delete(src));
  return pending;
}

/** Builds the audio context ahead of the first sound. Constructing it blocks the main thread for tens of milliseconds, which shows as a hitch in the first press, so do it while the page is idle. */
export function warmAudioEngine(): void {
  getAudioContext();
}

/** Decodes a sound into the cache before it is needed, so its first play does not wait for the decoder. Never throws. */
export async function prepareBuffer(src: string): Promise<void> {
  const ctx = getAudioContext();
  if (!ctx) return;
  try {
    await decode(ctx, src);
  } catch {
    /* decoded again on demand */
  }
}

/** Plays a sound once. Resolves when playback has started; never throws (a blocked or failed sound is silent). */
export async function playBuffer(src: string, volume: number): Promise<void> {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    if (ctx.state === "suspended") {
      // Before the first press or key the browser keeps the context suspended; waiting would replay everything queued at once.
      if (typeof navigator !== "undefined" && navigator.userActivation && !navigator.userActivation.hasBeenActive) return;
      await ctx.resume();
    }
    const buffer = await decode(ctx, src);
    const source = ctx.createBufferSource();
    const gain = ctx.createGain();
    source.buffer = buffer;
    gain.gain.value = Math.min(1, Math.max(0, volume));
    source.connect(gain);
    gain.connect(ctx.destination);
    source.start(0);
  } catch {
    /* autoplay policy or decode error: stay silent */
  }
}
