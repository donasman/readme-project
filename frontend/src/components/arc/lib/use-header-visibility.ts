"use client";

import { useEffect, useState } from "react";

/** Where the reader is in a scroll, and what the last stretch of scrolling in one direction added up to. */
export type ScrollState = { y: number; travel: number; hidden: boolean };

export const HEADER_HIDE = {
  /** Above this scroll position the header is always shown: the top of the page is its place. */
  offset: 96,
  /** How far down the reader has to scroll in one go before the header leaves. A small wobble does not hide it. */
  down: 14,
  /** How far up before it comes back. Smaller than `down`, so the header answers a scroll up at once. */
  up: 6,
} as const;

/**
 * One step of the hide-on-scroll rule, as a pure function. The position is clamped to the page (a rubber-band bounce past the bottom or the
 * top of a phone must not read as a scroll), travel in one direction adds up and restarts when the direction changes, and the header hides
 * after enough travel down and shows after a little travel up, or at the top of the page.
 */
export function stepScroll(state: ScrollState, rawY: number, max: number): ScrollState {
  const y = Math.max(0, Math.min(rawY, Math.max(0, max)));
  const dy = y - state.y;
  if (y < HEADER_HIDE.offset) return { y, travel: 0, hidden: false };
  if (dy === 0) return { ...state, y };
  const travel = Math.sign(dy) === Math.sign(state.travel) ? state.travel + dy : dy;
  if (travel > HEADER_HIDE.down) return { y, travel, hidden: true };
  if (travel < -HEADER_HIDE.up) return { y, travel, hidden: false };
  return { y, travel, hidden: state.hidden };
}

/**
 * True while a sticky header should be out of sight: the reader scrolls down the page. It returns as soon as they scroll up, and stays shown
 * at the top of the page. `enabled` false never hides it. A header that has keyboard focus inside should stay (`:focus-within`, in the CSS).
 */
export function useHeaderHidden(enabled = true): boolean {
  const [hidden, setHidden] = useState(false);
  useEffect(() => {
    if (!enabled || typeof window === "undefined") {
      setHidden(false);
      return;
    }
    let state: ScrollState = { y: window.scrollY, travel: 0, hidden: false };
    let frame = 0;
    const read = () => {
      frame = 0;
      state = stepScroll(state, window.scrollY, document.documentElement.scrollHeight - window.innerHeight);
      setHidden(state.hidden);
    };
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(read);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, [enabled]);
  return hidden;
}
