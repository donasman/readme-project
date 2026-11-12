"use client";

import { useEffect } from "react";
import type { RefObject } from "react";

/**
 * Plays a silent video only while it is on screen, and stops it the moment it is not: scrolled away, the tab hidden, the page left, or the
 * component taken off the page or hidden by the router (which keeps the old page around). `enabled` false never starts it and pauses it, for
 * people who asked for less motion. It forces `muted` too: a video that was in the server's HTML can reach the browser before React sets that
 * prop, and a clip with sound must never start by itself.
 */
export function useViewportPlayback(ref: RefObject<HTMLVideoElement | null>, enabled = true, threshold = 0.4): void {
  useEffect(() => {
    const video = ref.current;
    if (!video) return;
    video.muted = true;
    if (!enabled || typeof IntersectionObserver === "undefined") {
      video.pause();
      return;
    }
    let inView = false;
    const sync = () => {
      if (inView && document.visibilityState === "visible") void video.play().catch(() => undefined);
      else video.pause();
    };
    const watch = new IntersectionObserver(([entry]) => {
      inView = entry?.isIntersecting ?? false;
      sync();
    }, { threshold });
    watch.observe(video);
    document.addEventListener("visibilitychange", sync);
    window.addEventListener("pagehide", sync);
    return () => {
      watch.disconnect();
      document.removeEventListener("visibilitychange", sync);
      window.removeEventListener("pagehide", sync);
      video.pause();
    };
  }, [ref, enabled, threshold]);
}
