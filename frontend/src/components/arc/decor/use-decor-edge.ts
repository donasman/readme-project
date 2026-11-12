"use client";

import { useDecor } from "../theme/provider";
import styles from "./decor.module.css";
import { useDecorEnabled } from "./use-decor-enabled";

/**
 * The class that draws the theme's edge on a component's root, or `undefined` when there is nothing to draw (no provider, a theme
 * without an edge, or the site's decor setting off). Leaving the class off keeps the markup the same as before decor existed.
 * The root must be `position: relative` and must not use `::before` itself, because `.edge` draws with it.
 */
export function useDecorEdgeClass(): string | undefined {
  const edge = useDecor()?.edge;
  const enabled = useDecorEnabled();
  return edge && enabled ? styles.edge : undefined;
}
