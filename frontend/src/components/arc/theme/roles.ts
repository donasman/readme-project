import type { IllustrationKey, IllustrationSpec } from "../illustration/spec";

/**
 * What a theme can change besides colors: the icon set, the sounds, the motion, the type, the corner shape and the shadows.
 * Pure data (no React) so the registry schema and the theme resolver can import it too.
 */

/** Interface icons that every component asks for by role, not by glyph. Content icons inside sample blocks are not roles. */
export const ICON_ROLES = [
  "close", "check", "chevron-down", "chevron-up", "chevron-left", "chevron-right",
  "arrow-right", "arrow-left", "arrow-up", "arrow-down", "search", "plus", "minus", "more", "delete",
  "calendar", "clock", "info", "success", "warning", "error", "alert", "copy", "link",
  "play", "pause", "reset", "retry", "loading", "notification", "mail", "file", "theme-light", "theme-dark",
] as const;
export type IconRole = (typeof ICON_ROLES)[number];

/** What happened, not which file. Components play a role and the active theme decides the sound. */
export const SOUND_ROLES = ["click", "tick", "select", "toggle-on", "toggle-off", "open", "close", "success", "error", "notify", "confirm"] as const;
export type SoundRole = (typeof SOUND_ROLES)[number];

/** Sound files that ship with the library. A theme can point a role at one of these by name. */
export const BUNDLED_SOUNDS = ["click-001", "tick-001", "select-001", "switch-on", "switch-off", "open-001", "close-001", "success-chime", "error-001", "notification-pop", "confirmation-001"] as const;
export type BundledSound = (typeof BUNDLED_SOUNDS)[number];

/** A theme icon: SVG path data on a 24px grid (stroke by default, fill when `fill` is true). */
export interface CustomIcon {
  paths: string[];
  viewBox?: string;
  strokeWidth?: number;
  fill?: boolean;
}

export const DURATION_KEYS = ["instant", "fast", "exit", "standard", "considered"] as const;
export const EASE_KEYS = ["enter", "exit", "standard", "inOut"] as const;
export const SPRING_KEYS = ["responsive", "gentle", "snappy", "smooth", "morph"] as const;
export type CubicBezier = [number, number, number, number];
export type SpringSpec = { type: "spring"; stiffness: number; damping: number } | { type: "spring"; visualDuration: number; bounce: number };

/** Partial on purpose: a theme only writes what it changes. Durations are seconds. */
export interface MotionOverrides {
  duration?: Partial<Record<(typeof DURATION_KEYS)[number], number>>;
  ease?: Partial<Record<(typeof EASE_KEYS)[number], CubicBezier>>;
  spring?: Partial<Record<(typeof SPRING_KEYS)[number], SpringSpec>>;
}

/** Where the type of a theme can differ. `display` is headings, `body` is running text. */
export const TYPE_FACES = ["display", "body"] as const;
export type TypeFace = (typeof TYPE_FACES)[number];

/** Corner sizes of the three kinds of surface. The pill radius is fixed on purpose: it is a shape, not a size. */
export const SHAPE_KEYS = ["control", "panel", "surface"] as const;
export type ShapeKey = (typeof SHAPE_KEYS)[number];

/** Shadow steps from a resting card to a floating layer. */
export const ELEVATION_KEYS = ["resting", "raised", "floating"] as const;
export type ElevationKey = (typeof ELEVATION_KEYS)[number];

/** A CSS value, or one per color mode when the shadow has to change with it (dark surfaces need stronger shadows). */
export type ModeValue = string | { light: string; dark: string };

/** Partial on purpose: a theme only writes what it changes. Sizes are CSS values such as "18px" or "1.125rem". */
export interface TypographyOverrides {
  /** A CSS font-family list. The font file itself has to be loaded by the page (@font-face). */
  family?: Partial<Record<TypeFace, string>>;
  /** Letter spacing, for example "-.03em". */
  tracking?: Partial<Record<TypeFace, string>>;
  /** Unitless line height, for example "1.4". */
  leading?: Partial<Record<TypeFace, string>>;
}
export type ShapeOverrides = Partial<Record<ShapeKey, string>>;
export type ElevationOverrides = Partial<Record<ElevationKey, ModeValue>>;

export const DECOR_PARTICLE_COLORS = ["accent", "highlight", "glow"] as const;
export const DECOR_PARTICLE_MOTIONS = ["fall", "float", "twinkle", "pop"] as const;
export const DECOR_PARTICLE_ON = ["hover", "always"] as const;
export const DECOR_QUIRK_HOVER = ["wobble", "bob", "none"] as const;
export const DECOR_QUIRK_PRESS = ["jelly", "none"] as const;

/**
 * A theme's seasonal decoration. Each part is optional and inherited on its own. `edge` becomes CSS variables (first paint),
 * `particles` and `quirk` travel through the React context.
 */
export interface ThemeDecor {
  /** The strip along an element's top edge. Plain CSS values. `size` defaults to 3px. */
  edge?: { size?: string; fill: string };
  /** A small shape that drifts around an element. `glyph` is SVG path data on a 24px grid. */
  particles?: {
    glyph: string;
    fill?: boolean;
    color: (typeof DECOR_PARTICLE_COLORS)[number];
    count: number;
    motion: (typeof DECOR_PARTICLE_MOTIONS)[number];
    on: (typeof DECOR_PARTICLE_ON)[number];
  };
  /** Playfulness added to the motion of hover and press. */
  quirk?: { hover?: (typeof DECOR_QUIRK_HOVER)[number]; press?: (typeof DECOR_QUIRK_PRESS)[number] };
}

/**
 * The cover art a theme gives blocks that draw one (the login block's stage): which shapes (`concept`), which colours (`mood`), which take
 * (`seed`) and how many shapes (`layout`). Each is optional and inherited on its own. Names are those of the cover-art engine (lib/cover-scene.ts).
 */
export interface ThemeCover {
  concept?: string;
  mood?: string;
  seed?: string;
  layout?: "solo" | "duo" | "trio";
}

/** The icons, sounds, motion, type, shape, elevation, illustrations, cover art and decor a theme overrides. Everything left out is inherited. */
export interface ThemeAssets {
  icons?: Partial<Record<IconRole, CustomIcon>>;
  /** A bundled sound name, a path or URL to an audio file, or a data: URI. */
  sounds?: Partial<Record<SoundRole, string>>;
  motion?: MotionOverrides;
  typography?: TypographyOverrides;
  shape?: ShapeOverrides;
  elevation?: ElevationOverrides;
  /** The theme's own art, one illustration at a time. A key left out draws the base art in the theme's colours. */
  illustrations?: Partial<Record<IllustrationKey, IllustrationSpec>>;
  /** The cover art blocks draw in this theme. Left out means the block's own default. */
  cover?: ThemeCover;
  /** Seasonal decoration: edge, particles and quirk. Left out means none. */
  decor?: ThemeDecor;
}
