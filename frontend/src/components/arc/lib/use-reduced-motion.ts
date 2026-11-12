"use client";

import { useSyncExternalStore } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

/** True when the visitor asked for less motion: the OS setting, or `data-motion="reduce"` on <html> (the site's own setting). */
export function prefersReducedMotion(): boolean {
  if (typeof window === "undefined") return false;
  return window.matchMedia(QUERY).matches || document.documentElement.getAttribute("data-motion") === "reduce";
}

/** One media query and one attribute observer for the whole page, fanned out to every listener; torn down with the last one. */
const listeners = new Set<() => void>();
let teardown: (() => void) | null = null;

function notify() {
  for (const listener of [...listeners]) listener();
}

function start() {
  const query = window.matchMedia(QUERY);
  query.addEventListener("change", notify);
  const observer = new MutationObserver(notify);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-motion"] });
  teardown = () => {
    query.removeEventListener("change", notify);
    observer.disconnect();
  };
}

/** Subscribes to changes of reduced motion. Returns the unsubscribe function. */
export function subscribeReducedMotion(change: () => void): () => void {
  listeners.add(change);
  if (listeners.size === 1) start();
  return () => {
    if (!listeners.delete(change) || listeners.size > 0) return;
    teardown?.();
    teardown = null;
  };
}

/**
 * Reduced motion for components. Unlike motion's own `useReducedMotion`, it follows the site's setting too and updates when
 * either changes. The server and the hydration pass see `false`; CSS covers the first paint.
 */
export function useReducedMotion(): boolean {
  return useSyncExternalStore(subscribeReducedMotion, prefersReducedMotion, () => false);
}
