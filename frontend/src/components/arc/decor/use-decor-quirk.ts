"use client";

import type { TargetAndTransition } from "motion/react";
import { motionTokens } from "../lib/motion-tokens";
import { useReducedMotion } from "../lib/use-reduced-motion";
import { useDecor } from "../theme/provider";
import type { ThemeDecor } from "../theme/roles";
import { useDecorEnabled } from "./use-decor-enabled";

export interface DecorQuirk {
  whileHover?: TargetAndTransition;
  whileTap?: TargetAndTransition;
}

const NONE: DecorQuirk = {};
/** Keyframes need a tween: a component's own spring transition only takes two values. */
const swing = { duration: motionTokens.duration.moderate, ease: "easeInOut" } as const;
const WOBBLE: DecorQuirk = { whileHover: { rotate: [0, -3, 3, -2, 0], transition: swing } };
const BOB: DecorQuirk = { whileHover: { y: [0, -2, 0], transition: swing } };
const JELLY: DecorQuirk = { whileTap: { scaleX: 1.06, scaleY: 0.92 } };

function resolve(kind: "hover" | "press", quirk: ThemeDecor["quirk"], quiet: boolean): DecorQuirk {
  if (quiet || !quirk) return NONE;
  if (kind === "hover") return quirk.hover === "wobble" ? WOBBLE : quirk.hover === "bob" ? BOB : NONE;
  return quirk.press === "jelly" ? JELLY : NONE;
}

/**
 * Both gestures in one call, for a component that already knows whether motion is reduced (`reduced`) and so subscribes only once.
 * Empty when the theme has no quirk, decor is off, motion is reduced, or the component is `disabled` (a disabled, busy or
 * menu-anchor component should neither wobble nor squish).
 */
export function useDecorQuirks(reduced: boolean, disabled = false): { hover: DecorQuirk; press: DecorQuirk } {
  const quirk = useDecor()?.quirk;
  const enabled = useDecorEnabled();
  const quiet = !enabled || reduced || disabled;
  return { hover: resolve("hover", quirk, quiet), press: resolve("press", quirk, quiet) };
}

/** One gesture's quirk, to spread over a component's own `whileHover` / `whileTap`. Same rules as `useDecorQuirks`; resolves reduced motion itself. */
export function useDecorQuirk(kind: "hover" | "press", opts?: { disabled?: boolean }): DecorQuirk {
  const reduced = useReducedMotion();
  const quirks = useDecorQuirks(reduced, opts?.disabled);
  return quirks[kind];
}
