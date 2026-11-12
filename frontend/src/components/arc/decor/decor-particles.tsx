"use client";

import { useEffect, useRef, useState, type CSSProperties, type ReactElement } from "react";
import { useReducedMotion } from "../lib/use-reduced-motion";
import { useDecor } from "../theme/provider";
import type { ThemeDecor } from "../theme/roles";
import styles from "./decor.module.css";
import { useDecorEnabled } from "./use-decor-enabled";

type Slot = "button" | "card" | "badge" | "chip";

export interface DecorParticlesProps {
  /** The kind of component the particles sit on. Lets the stylesheet and the placement tune where they start. */
  slot: Slot;
  /**
   * Shows hover-only particles without a pointer hover, for a state the component marks itself (for example a selected chip).
   * Never wire this to `:focus-visible`: focus keeps the ring and the edge only, with no particles.
   */
  active?: boolean;
}

/**
 * Where particle `index` of `count` sits: spread evenly along the top with a small fixed offset, so every render (server or client)
 * lands in the same place. No randomness on purpose. Buttons, badges and chips start just above the top edge; cards start inside the box
 * (their overflow is clipped, so a shape above the top would never show).
 */
function placement(index: number, count: number, slot: Slot): CSSProperties {
  const left = ((index + 0.5) / count) * 100 + (((index * 7) % 5) - 2) * 2;
  const rise = ((index * 5) % 3) * 0.2;
  const y = slot === "card" ? 1.1 + rise : -0.35 - rise;
  return {
    "--decor-x": `${left.toFixed(1)}%`,
    "--decor-y": `${y.toFixed(2)}em`,
    "--decor-delay": `${(((index * 3) % count) * 0.28).toFixed(2)}s`,
    "--decor-scale": (0.85 + ((index * 2) % 3) * 0.15).toFixed(2),
  } as CSSProperties;
}

/** True for a mouse-like pointer, the only kind that hovers. */
function isFinePointer(event: PointerEvent): boolean {
  return event.pointerType !== "touch" && event.pointerType !== "pen" && window.matchMedia("(hover: hover) and (pointer: fine)").matches;
}

type Particles = NonNullable<ThemeDecor["particles"]>;

/** A disabled, aria-disabled or busy parent shows no particles (the stylesheet hides them too, for a parent that turns disabled mid-hover). */
const BLOCKED = ':disabled, [aria-disabled="true"], [aria-busy="true"]';
/** A little longer than the layer's opacity fade (--duration-fast, .15s), so the shapes keep moving until they are gone. */
const FADE_MS = 220;

/**
 * The layer itself. It idles as one empty element: the shapes are mounted on the parent's first fine-pointer enter (or at once when
 * `active`, or for always-on buttons) and stay mounted afterwards. Touch and pen never mount them.
 * Hover is tracked here, as a `data-hover` attribute on the layer, instead of with a `:hover` rule in the stylesheet: a `:hover` rule
 * that reaches into descendants makes every hover change anywhere on the page re-check the particles under it, which measured as several
 * times the style work of a page without decor. The attribute touches only this layer.
 */
function ParticleLayer({ particles, slot, active }: { particles: Particles; slot: Slot; active?: boolean }): ReactElement | null {
  const reduced = useReducedMotion();
  // Always-on particles are a button thing: many infinite animations across a page of cards and tags do not scale, so elsewhere they wait for hover.
  const always = particles.on === "always" && slot === "button";
  const [armed, setArmed] = useState(false);
  const layer = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const node = layer.current;
    const parent = node?.parentElement;
    if (reduced || always || !node || !parent) return;
    // On leave the layer fades out first (data-leaving keeps the shapes moving for the fade), then the animations stop.
    let fading: ReturnType<typeof setTimeout> | undefined;
    const stopFade = () => { clearTimeout(fading); node.removeAttribute("data-leaving"); };
    const enter = (event: PointerEvent) => {
      if (!isFinePointer(event) || parent.matches(BLOCKED)) return;
      stopFade();
      setArmed(true);
      node.setAttribute("data-hover", "");
    };
    const leave = () => {
      if (!node.hasAttribute("data-hover")) return;
      node.removeAttribute("data-hover");
      node.setAttribute("data-leaving", "");
      clearTimeout(fading);
      fading = setTimeout(stopFade, FADE_MS);
    };
    parent.addEventListener("pointerenter", enter);
    parent.addEventListener("pointerleave", leave);
    return () => {
      parent.removeEventListener("pointerenter", enter);
      parent.removeEventListener("pointerleave", leave);
      stopFade();
      node.removeAttribute("data-hover");
    };
  }, [reduced, always]);
  if (reduced) return null;
  const mounted = always || armed || Boolean(active);
  return (
    <span
      ref={layer}
      className={styles.particles}
      aria-hidden="true"
      data-on={always ? "always" : "hover"}
      data-motion={particles.motion}
      data-slot={slot}
      data-decor-active={active ? "" : undefined}
    >
      {mounted
        ? Array.from({ length: particles.count }, (_, index) => (
            <span key={index} className={styles.particle} style={placement(index, particles.count, slot)}>
              <svg viewBox="0 0 24 24" width="1em" height="1em" fill={particles.fill ? "currentColor" : "none"} stroke={particles.fill ? "none" : "currentColor"} strokeWidth={2} strokeLinejoin="round" style={{ color: `var(--${particles.color})` }}>
                <path d={particles.glyph} />
              </svg>
            </span>
          ))
        : null}
    </span>
  );
}

/**
 * Small shapes that drift around the top of their parent (which must be `position: relative`). They are decoration only: hidden from
 * assistive tech, never take a click, never change the parent's size. Renders nothing without particles, with decor off, or with
 * reduced motion. Without a theme that has particles it costs no subscription at all (the reduced-motion hook lives in the layer).
 * `on: "always"` is honoured on slot "button" only; every other slot treats it as "hover".
 */
export function DecorParticles({ slot, active }: DecorParticlesProps): ReactElement | null {
  const particles = useDecor()?.particles;
  const enabled = useDecorEnabled();
  if (!particles || !enabled) return null;
  return <ParticleLayer particles={particles} slot={slot} active={active} />;
}
