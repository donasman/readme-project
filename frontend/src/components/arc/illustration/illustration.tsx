"use client";

import { useThemeAssets } from "../theme/provider";
import { BASE_ILLUSTRATIONS } from "./base-set";
import { IllustrationSvg, type IllustrationSvgProps } from "./illustration-svg";
import styles from "./illustration.module.css";
import type { IllustrationKey, IllustrationSpec } from "./spec";

export interface IllustrationProps extends Omit<IllustrationSvgProps, "spec"> {
  /** Which illustration. Hero, header and card sizes are listed in `ILLUSTRATION_META`. */
  name: IllustrationKey;
  /** Draws this spec instead of the active theme's art. For showing a particular theme's picture outside its provider, such as a theme gallery. */
  spec?: IllustrationSpec;
}

/**
 * A flat illustration for heroes, section headers and card covers. It draws the active theme's own art when the theme has
 * made one, and the base art when it has not (see `ThemeAssetsProvider`). Colours always follow the theme's tokens.
 * Size it with the width of its container: the aspect ratio is fixed by the illustration.
 */
export function Illustration({ name, spec: given, animated = false, className, ...rest }: IllustrationProps) {
  const themed = useThemeAssets().illustrations?.[name];
  const spec = given ?? themed ?? BASE_ILLUSTRATIONS[name];
  return <IllustrationSvg spec={spec} animated={animated} className={className ? `${styles.root} ${className}` : styles.root} {...rest} />;
}
