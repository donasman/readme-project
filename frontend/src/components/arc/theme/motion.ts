import { motionTokens } from "../motion-tokens";
import { DURATION_KEYS, type MotionOverrides } from "./roles";

type Groups = "duration" | "ease" | "spring";
type Mutable = Record<Groups, Record<string, unknown>>;

const target = motionTokens as unknown as Mutable;
const defaults: Mutable = { duration: { ...motionTokens.duration }, ease: { ...motionTokens.ease }, spring: { ...motionTokens.spring } };

/**
 * Applies a theme's motion to `motionTokens` in place (null puts the defaults back). Components that read the tokens when they render
 * or when an interaction runs follow the theme. Constants a module captured when it was first imported keep the values from then,
 * so a theme chosen before the page loads is fully applied and a switch while the page is open finishes on the next load.
 */
export function applyMotionTheme(overrides: MotionOverrides | null | undefined): void {
  for (const group of ["duration", "ease", "spring"] as const) {
    for (const key of Object.keys(target[group])) target[group][key] = defaults[group][key];
    const patch = overrides?.[group] as Record<string, unknown> | undefined;
    if (patch) for (const key of Object.keys(patch)) if (key in target[group] && patch[key] !== undefined) target[group][key] = patch[key];
  }
}

/** The CSS variables the stylesheets read for motion, with a theme's durations and easings (springs only exist in script). */
export function motionCssVars(overrides: MotionOverrides | null | undefined): Record<string, string> {
  const vars: Record<string, string> = {};
  for (const key of DURATION_KEYS) {
    const value = overrides?.duration?.[key];
    if (value !== undefined && key !== "exit") vars[`--duration-${key}`] = `${Math.round(value * 1000)}ms`;
  }
  for (const [key, cssName] of [["enter", "--ease-enter"], ["exit", "--ease-exit"], ["standard", "--ease-standard"], ["inOut", "--ease-in-out"]] as const) {
    const value = overrides?.ease?.[key];
    if (value) vars[cssName] = `cubic-bezier(${value.join(",")})`;
  }
  return vars;
}

