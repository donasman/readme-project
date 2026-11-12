import { ELEVATION_KEYS, SHAPE_KEYS, TYPE_FACES, type ModeValue, type ThemeAssets } from "./roles";

/**
 * Theme values end up inside a stylesheet, so only plain CSS values pass: letters, digits, spaces and the punctuation lengths,
 * font stacks, colors and shadows use. No `;`, braces, backslashes, comments or url()/image functions, which could break out of
 * the declaration or fetch something.
 */
const SAFE_VALUE = /^[\p{L}\p{N}\s.,%()/'"#:+*_-]{1,300}$/u;
const FORBIDDEN = /url\s*\(|image(-set)?\s*\(|expression\s*\(|\/\*|\*\/|@import/i;

export function isSafeCssValue(value: string): boolean {
  return SAFE_VALUE.test(value) && !FORBIDDEN.test(value);
}

const TYPE_VARS = {
  family: { display: "--font-display", body: "--font-body" },
  tracking: { display: "--tracking-display", body: "--tracking-body" },
  leading: { display: "--leading-display", body: "--leading-body" },
} as const;

type Declarations = { light: string[]; dark: string[] };

function pick(value: ModeValue): { light: string; dark: string } {
  return typeof value === "string" ? { light: value, dark: value } : value;
}

/** The declarations a theme sets, split by color mode. Values that are not safe CSS are dropped. */
export function themeStyleDeclarations(assets: ThemeAssets | undefined): Declarations {
  const out: Declarations = { light: [], dark: [] };
  if (!assets) return out;
  const both = (name: string, value: string | undefined) => {
    if (value === undefined || !isSafeCssValue(value)) return;
    out.light.push(`${name}:${value}`);
    out.dark.push(`${name}:${value}`);
  };
  for (const group of ["family", "tracking", "leading"] as const) {
    for (const face of TYPE_FACES) both(TYPE_VARS[group][face], assets.typography?.[group]?.[face]);
  }
  for (const key of SHAPE_KEYS) both(`--radius-${key}`, assets.shape?.[key]);
  for (const key of ELEVATION_KEYS) {
    const raw = assets.elevation?.[key];
    if (raw === undefined) continue;
    const { light, dark } = pick(raw);
    if (!isSafeCssValue(light) || !isSafeCssValue(dark)) continue;
    out.light.push(`--shadow-${key}:${light}`);
    out.dark.push(`--shadow-${key}:${dark}`);
  }
  const edge = assets.decor?.edge;
  if (edge) {
    both("--decor-edge-size", edge.size ?? "3px");
    both("--decor-edge-fill", edge.fill);
  }
  return out;
}

/**
 * The stylesheet that puts a theme's type, corners and shadows in effect. The selectors mirror foundation.css (light, the explicit
 * dark switch by data-theme or the .dark class, and the OS dark setting when nothing is chosen) with one extra `:root` so they win over it wherever the sheet loads.
 * `scope` replaces `:root:root`, for a sheet that applies only under an attribute (the site's season themes).
 * Returns an empty string when the theme overrides none of them.
 */
export function themeStyleCss(assets: ThemeAssets | undefined, scope = ":root:root"): string {
  const { light, dark } = themeStyleDeclarations(assets);
  if (light.length === 0) return "";
  const darkBody = dark.join(";");
  return [
    `${scope}{${light.join(";")}}`,
    `${scope}[data-theme="dark"],${scope}.dark{${darkBody}}`,
    `@media (prefers-color-scheme: dark){${scope}:not([data-theme="light"]):not([data-theme="dark"]):not(.light):not(.dark){${darkBody}}}`,
  ].join("\n");
}
