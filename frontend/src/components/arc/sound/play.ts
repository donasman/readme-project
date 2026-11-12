import { BUNDLED_SOUNDS, type BundledSound, type SoundRole } from "../theme/roles";
import { playBuffer, prepareBuffer, warmAudioEngine } from "./engine";
import { getSoundSettings } from "./settings";
import type { SoundAsset } from "./types";

/** What happened, not which file: components ask for a role and the active theme decides which sound it is. */
export type UiSoundName = SoundRole;

/** Files load on first use, so pages that never play a sound never download one. Sounds are Kenney (CC0) via soundcn. */
const bundled: Record<BundledSound, () => Promise<SoundAsset>> = {
  "click-001": () => import("./sounds/click-001").then((m) => m.click001Sound),
  "tick-001": () => import("./sounds/tick-001").then((m) => m.tick001Sound),
  "select-001": () => import("./sounds/select-001").then((m) => m.select001Sound),
  "switch-on": () => import("./sounds/switch-on").then((m) => m.switchOnSound),
  "switch-off": () => import("./sounds/switch-off").then((m) => m.switchOffSound),
  "open-001": () => import("./sounds/open-001").then((m) => m.open001Sound),
  "close-001": () => import("./sounds/close-001").then((m) => m.close001Sound),
  "success-chime": () => import("./sounds/success-chime").then((m) => m.successChimeSound),
  "error-001": () => import("./sounds/error-001").then((m) => m.error001Sound),
  "notification-pop": () => import("./sounds/notification-pop").then((m) => m.notificationPopSound),
  "confirmation-001": () => import("./sounds/confirmation-001").then((m) => m.confirmation001Sound),
};

/** The sound of each role when the active theme does not override it, and how loud it is before the user's volume. */
const defaults: Record<UiSoundName, { file: BundledSound; gain: number }> = {
  click: { file: "click-001", gain: 0.6 },
  tick: { file: "tick-001", gain: 0.5 },
  select: { file: "select-001", gain: 0.5 },
  "toggle-on": { file: "switch-on", gain: 0.7 },
  "toggle-off": { file: "switch-off", gain: 0.7 },
  open: { file: "open-001", gain: 0.6 },
  close: { file: "close-001", gain: 0.6 },
  success: { file: "success-chime", gain: 0.6 },
  error: { file: "error-001", gain: 0.6 },
  notify: { file: "notification-pop", gain: 0.6 },
  confirm: { file: "confirmation-001", gain: 0.6 },
};

const isBundled = (ref: string): ref is BundledSound => (BUNDLED_SOUNDS as readonly string[]).includes(ref);

/** The active theme's sounds: only the roles it overrides, already merged over its parents. */
let themeSounds: Partial<Record<UiSoundName, string>> = {};
const bundledCache = new Map<BundledSound, Promise<string>>();
const lastPlayed = new Map<UiSoundName, number>();
/** Two calls of the same sound inside this window collapse into one (a click that also fires a change). */
const MIN_GAP_MS = 40;

/** Sets the sounds of the active theme. Pass undefined to go back to the defaults. Called by ThemeAssetsProvider. */
export function setThemeSounds(sounds: Partial<Record<UiSoundName, string>> | undefined): void {
  themeSounds = sounds ?? {};
}

/** The sounds now set, so a caller that borrows another theme's sounds for a moment can put them back. */
export function getThemeSounds(): Partial<Record<UiSoundName, string>> {
  return themeSounds;
}

function bundledSource(file: BundledSound): Promise<string> {
  let pending = bundledCache.get(file);
  if (!pending) {
    pending = bundled[file]().then((asset) => asset.dataUri);
    pending.catch(() => bundledCache.delete(file));
    bundledCache.set(file, pending);
  }
  return pending;
}

/** What to play for a role: the theme's sound if it set one, otherwise the default. A data URI or a URL plays as is. */
function sourceFor(name: UiSoundName): Promise<string> {
  const ref = themeSounds[name];
  if (ref) return isBundled(ref) ? bundledSource(ref) : Promise.resolve(ref);
  return bundledSource(defaults[name].file);
}

/** Plays a UI sound if sound is on. Safe to call anywhere on the client; on the server it does nothing. */
export function playUiSound(name: UiSoundName): void {
  if (typeof window === "undefined") return;
  const { enabled, volume } = getSoundSettings();
  if (!enabled || volume <= 0) return;
  const now = performance.now();
  if (now - (lastPlayed.get(name) ?? -Infinity) < MIN_GAP_MS) return;
  lastPlayed.set(name, now);
  sourceFor(name).then((src) => playBuffer(src, volume * defaults[name].gain)).catch(() => {});
}

/** Starts loading the common sounds of the active theme, for example after the first pointer press. Optional. */
export function preloadUiSounds(names: UiSoundName[] = ["click", "tick", "select", "toggle-on", "toggle-off"]): void {
  if (typeof window === "undefined") return;
  for (const name of names) sourceFor(name).catch(() => {});
}

/**
 * Gets the sound engine ready while nothing else is going on: the audio context and the common sounds, decoded. Does nothing when sound
 * is off, so a visitor who opted out never loads the engine. Call it after the first user gesture (a context built earlier is not allowed to start).
 */
export function warmUiSounds(names: UiSoundName[] = ["click", "tick", "select", "toggle-on", "toggle-off", "open", "close"]): void {
  if (typeof window === "undefined") return;
  const { enabled, volume } = getSoundSettings();
  if (!enabled || volume <= 0) return;
  const run = () => {
    warmAudioEngine();
    for (const name of names) sourceFor(name).then(prepareBuffer).catch(() => {});
  };
  if (typeof window.requestIdleCallback === "function") window.requestIdleCallback(run, { timeout: 2000 });
  else window.setTimeout(run, 200);
}
