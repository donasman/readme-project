"use client";

import { createContext, useContext, useEffect, useMemo, type ReactNode } from "react";
import { setThemeSounds } from "../sound/play";
import { applyMotionTheme, motionCssVars } from "./motion";
import type { ThemeAssets, ThemeDecor } from "./roles";
import { themeStyleCss } from "./style";

const EMPTY: ThemeAssets = {};
const ThemeAssetsContext = createContext<ThemeAssets>(EMPTY);
/** Whether season decor is on. Read through `useDecorEnabled` (decor/use-decor-enabled.ts). */
export const DecorEnabledContext = createContext<boolean>(true);

/** The icons, sounds, motion, type, shape and elevation of the active theme. Empty outside a provider, which means the defaults. */
export function useThemeAssets(): ThemeAssets {
  return useContext(ThemeAssetsContext);
}

/** The theme's particles and quirk (its edge is CSS variables). `undefined` outside a provider or when the theme has no decor. */
export function useDecor(): ThemeDecor | undefined {
  return useContext(ThemeAssetsContext).decor;
}

/**
 * Puts a theme's icons, sounds, motion, type, corners and shadows in effect for everything below it. Pass the resolved assets of the theme
 * (the theme's own overrides merged over its parents, see lib/theme/assets.ts). Leave `assets` out for the defaults.
 * `decorEnabled={false}` turns the theme's decor off (no particles, no quirk) and sets `<html data-decor="off">` so the edge goes too: this is how a
 * site setting switches it. Defaults to true.
 * Use one provider per page: sounds and motion are shared by the whole page, and overlays render outside this subtree.
 */
export function ThemeAssetsProvider({ assets, decorEnabled = true, children }: { assets?: ThemeAssets; decorEnabled?: boolean; children: ReactNode }) {
  const value = assets ?? EMPTY;
  // Applied during render on purpose: children read the sounds and motion tokens while they render, so they must be set first.
  useMemo(() => { setThemeSounds(value.sounds); applyMotionTheme(value.motion); }, [value]);
  useEffect(() => {
    const vars = motionCssVars(value.motion);
    const root = document.documentElement;
    for (const [name, css] of Object.entries(vars)) root.style.setProperty(name, css);
    return () => { for (const name of Object.keys(vars)) root.style.removeProperty(name); };
  }, [value.motion]);
  // Applied again from an effect: React may discard a memo, and StrictMode runs the cleanup once right after mounting, which would
  // otherwise leave the defaults in place for a provider that started with assets.
  useEffect(() => {
    setThemeSounds(value.sounds);
    applyMotionTheme(value.motion);
    return () => { setThemeSounds(undefined); applyMotionTheme(null); };
  }, [value]);
  // The same switch that hides particles and quirk also hides the edge, which is CSS: <html data-decor="off">. Set from here so the prop
  // and the attribute cannot disagree. While decor is on this leaves <html> alone, because the site's head script may have set "off"
  // before hydration, when the first render still sees the default (on).
  useEffect(() => {
    if (decorEnabled) return;
    const root = document.documentElement;
    root.setAttribute("data-decor", "off");
    return () => root.removeAttribute("data-decor");
  }, [decorEnabled]);
  // Type, corners and shadows are plain CSS variables. A style element in the tree (not an effect) so they are there on the first paint.
  const css = useMemo(() => themeStyleCss(value), [value]);
  return (
    <ThemeAssetsContext.Provider value={value}>
      <DecorEnabledContext.Provider value={decorEnabled}>
        {css ? <style data-theme-assets dangerouslySetInnerHTML={{ __html: css }} /> : null}
        {children}
      </DecorEnabledContext.Provider>
    </ThemeAssetsContext.Provider>
  );
}
