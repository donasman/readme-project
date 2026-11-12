"use client";

import { useSyncExternalStore } from "react";

export interface SoundSettings { enabled: boolean; volume: number }

const STORAGE_KEY = "devs7udio:sound";
/** Readme: sound is off by default (upstream ships it on). A visitor who turns it on keeps that choice in this browser. */
export const DEFAULT_SOUND_SETTINGS: SoundSettings = { enabled: false, volume: 0.5 };

/** Where the settings are kept. An app can swap it, for example to remember nothing until the visitor agrees to cookies. */
export interface SoundStorage { get: (key: string) => string | null; set: (key: string, value: string) => void }
const localStorageAdapter: SoundStorage = {
  get: (key) => { try { return window.localStorage.getItem(key); } catch { return null; } },
  set: (key, value) => { try { window.localStorage.setItem(key, value); } catch { /* storage blocked */ } },
};
let storage: SoundStorage = localStorageAdapter;
export function setSoundStorage(next: SoundStorage | undefined): void { storage = next ?? localStorageAdapter; }

let current: SoundSettings = DEFAULT_SOUND_SETTINGS;
let loaded = false;
const listeners = new Set<() => void>();

function load() {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  current = DEFAULT_SOUND_SETTINGS;
  try {
    const raw = storage.get(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<SoundSettings>;
      current = {
        enabled: typeof parsed.enabled === "boolean" ? parsed.enabled : DEFAULT_SOUND_SETTINGS.enabled,
        volume: typeof parsed.volume === "number" ? Math.min(1, Math.max(0, parsed.volume)) : DEFAULT_SOUND_SETTINGS.volume,
      };
    }
  } catch {
    /* storage blocked: keep the defaults for this session */
  }
}

function commit(next: SoundSettings) {
  current = next;
  storage.set(STORAGE_KEY, JSON.stringify(next));
  listeners.forEach((l) => l());
}

export function getSoundSettings(): SoundSettings { load(); return current; }
export function setSoundEnabled(enabled: boolean) { load(); commit({ ...current, enabled }); }
export function setSoundVolume(volume: number) { load(); commit({ ...current, volume: Math.min(1, Math.max(0, volume)) }); }

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => { if (event.key === STORAGE_KEY || event.key === null) { loaded = false; load(); listeners.forEach((l) => l()); } };
  window.addEventListener("storage", onStorage);
  return () => { listeners.delete(listener); window.removeEventListener("storage", onStorage); };
}

/** Current settings, with the defaults during server rendering and hydration. */
export function useSoundSettings(): SoundSettings {
  return useSyncExternalStore(subscribe, getSoundSettings, () => DEFAULT_SOUND_SETTINGS);
}
