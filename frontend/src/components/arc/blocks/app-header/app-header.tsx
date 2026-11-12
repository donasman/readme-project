"use client";

import { useEffect, useId, useState } from "react";
import type { HTMLAttributes, ReactNode } from "react";
import { motion } from "motion/react";
import { useReducedMotion } from "../../lib/use-reduced-motion";
import { motionTokens } from "../../lib/motion-tokens";
import { Devs7udioLogo } from "../../lib/brand-logo";
import { useHeaderHidden } from "../../lib/use-header-visibility";
import { BRAND_NAME, Devs7udioMark } from "../../lib/brand-mark";
import { playUiSound } from "../../sound/play";
import styles from "./app-header.module.css";

export interface AppHeaderItem {
  href: string;
  label: string;
  /** Marks the section the reader is in. At most one item should carry it. */
  current?: boolean;
}

export interface AppHeaderProps extends Omit<HTMLAttributes<HTMLElement>, "title"> {
  /**
   * The brand and where it points. By default the full devs7udio logo stands alone, with no mark and no text beside it.
   * Give `logo` your own node to replace it, or `mark` and `name` to show a mark with a name instead.
   */
  brand?: { name?: string; href: string; mark?: ReactNode; label?: string; logo?: ReactNode };
  items: AppHeaderItem[];
  /** The accessible name of the primary navigation, e.g. "주요 메뉴". */
  navLabel: string;
  /** Actions that stay in the bar at every width, such as the search trigger. */
  barActions?: ReactNode;
  /** Actions that move into the menu below the md breakpoint: language, theme, account. Place your own dividers. */
  actions?: ReactNode;
  cta?: { href: string; label: string };
  /** The narrow-screen menu, usually a MobileNavSheet. Left out, the links stay in the bar at every width. */
  menu?: ReactNode;
  /** Sticks to the top and lifts into a floating bar once the page has scrolled. Default true. */
  sticky?: boolean;
  /** A sticky header tucks away while the reader scrolls down and comes back when they scroll up. Default true. */
  hideOnScroll?: boolean;
}

/**
 * The site's top bar, built around an open actions slot: the brand, the links with the current section marked, the
 * actions a site chooses to put there, and one call to action. Scrolling lifts it into a floating bar — a layer over
 * the page, so its border takes the fill color and the shadow draws the edge.
 */
export function AppHeader({
  className,
  brand = { href: "/" },
  items,
  navLabel,
  barActions,
  actions,
  cta,
  menu,
  sticky = true,
  hideOnScroll = true,
  ...props
}: AppHeaderProps) {
  const reduced = useReducedMotion() ?? false;
  const indicatorId = useId();
  const [scrolled, setScrolled] = useState(false);
  const hidden = useHeaderHidden(sticky && hideOnScroll);
  // The full logo is the default; a custom mark or name asks for the mark-and-name form instead.
  const showLogo = brand.logo !== undefined || (brand.mark === undefined && brand.name === undefined);

  useEffect(() => {
    if (!sticky || typeof window === "undefined") return;
    // A little past the bar's own shadow, so a one-pixel scroll does not flicker the lift.
    const read = () => setScrolled(window.scrollY > 8);
    read();
    window.addEventListener("scroll", read, { passive: true });
    return () => window.removeEventListener("scroll", read);
  }, [sticky]);

  return (
    <header
      {...props}
      data-print="hide"
      data-scrolled={scrolled ? "" : undefined}
      data-hidden={hidden ? "" : undefined}
      className={[styles.header, sticky ? styles.sticky : "", className].filter(Boolean).join(" ")}
    >
      <div className={styles.bar}>
        <a href={brand.href} className={styles.brand} aria-label={brand.label ?? (showLogo ? (brand.name ?? BRAND_NAME) : undefined)} onClick={() => playUiSound("tick")}>
          {showLogo ? (brand.logo ?? <Devs7udioLogo height={26} animated />) : <>
            {brand.mark ?? <Devs7udioMark size={26} />}
            <span aria-hidden={brand.label ? "true" : undefined}>{brand.name ?? BRAND_NAME}</span>
          </>}
        </a>

        <nav aria-label={navLabel} className={styles.nav}>
          <ul className={styles.navList}>
            {items.map(item => (
              <li key={item.href}>
                <a
                  href={item.href}
                  aria-current={item.current ? "page" : undefined}
                  className={styles.navLink}
                  onClick={() => playUiSound("tick")}
                >
                  {item.current ? (
                    <motion.span
                      layoutId={indicatorId}
                      className={styles.indicator}
                      transition={reduced ? { duration: 0 } : motionTokens.spring.morph}
                      aria-hidden="true"
                    />
                  ) : null}
                  <span className={styles.navLabel}>{item.label}</span>
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className={styles.actions}>
          {barActions}
          {actions ? <div className={styles.wideActions}>{actions}</div> : null}
          {cta ? (
            <a href={cta.href} className={styles.cta} onClick={() => playUiSound("click")}>
              {cta.label}
            </a>
          ) : null}
          {menu ? <div className={styles.menuSlot}>{menu}</div> : null}
        </div>
      </div>
    </header>
  );
}

AppHeader.displayName = "AppHeader";

export default AppHeader;
