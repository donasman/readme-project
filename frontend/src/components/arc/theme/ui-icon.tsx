"use client";

import { HugeiconsIcon } from "@hugeicons/react";
import { Alert02Icon, AlertCircleIcon, ArrowDown01Icon, ArrowDown02Icon, ArrowLeft01Icon, ArrowLeft02Icon, ArrowRight01Icon, ArrowRight02Icon, ArrowUp01Icon, ArrowUp02Icon, Calendar03Icon, Cancel01Icon, CancelCircleIcon, CheckmarkCircle02Icon, Clock01Icon, Copy01Icon, Delete02Icon, File01Icon, InformationCircleIcon, Link01Icon, Loading03Icon, Mail01Icon, MinusSignIcon, Moon02Icon, MoreHorizontalIcon, Notification03Icon, PauseIcon, PlayIcon, PlusSignIcon, RotateLeft01Icon, RotateRight01Icon, Search01Icon, Sun03Icon, Tick02Icon } from "@hugeicons/core-free-icons";
import type { ComponentPropsWithoutRef } from "react";
import { useThemeAssets } from "./provider";
import type { CustomIcon, IconRole } from "./roles";

/** The icon each role shows when the active theme does not override it. */
export const DEFAULT_ICONS = {
  "close": Cancel01Icon,
  "check": Tick02Icon,
  "chevron-down": ArrowDown01Icon,
  "chevron-up": ArrowUp01Icon,
  "chevron-left": ArrowLeft01Icon,
  "chevron-right": ArrowRight01Icon,
  "arrow-right": ArrowRight02Icon,
  "arrow-left": ArrowLeft02Icon,
  "arrow-up": ArrowUp02Icon,
  "arrow-down": ArrowDown02Icon,
  "search": Search01Icon,
  "plus": PlusSignIcon,
  "minus": MinusSignIcon,
  "more": MoreHorizontalIcon,
  "delete": Delete02Icon,
  "calendar": Calendar03Icon,
  "clock": Clock01Icon,
  "info": InformationCircleIcon,
  "success": CheckmarkCircle02Icon,
  "warning": Alert02Icon,
  "error": CancelCircleIcon,
  "alert": AlertCircleIcon,
  "copy": Copy01Icon,
  "link": Link01Icon,
  "play": PlayIcon,
  "pause": PauseIcon,
  "reset": RotateLeft01Icon,
  "retry": RotateRight01Icon,
  "loading": Loading03Icon,
  "notification": Notification03Icon,
  "mail": Mail01Icon,
  "file": File01Icon,
  "theme-light": Sun03Icon,
  "theme-dark": Moon02Icon,
} as const;

type IconProps = Omit<ComponentPropsWithoutRef<typeof HugeiconsIcon>, "icon">;

export function CustomSvgIcon({ icon, size = 24, strokeWidth, color = "currentColor", ...rest }: { icon: CustomIcon } & IconProps) {
  const width = icon.strokeWidth ?? strokeWidth ?? 1.5;
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox={icon.viewBox ?? "0 0 24 24"} width={size} height={size} fill="none" color={color} {...(rest as object)}>
      {icon.paths.map((d, index) => icon.fill
        ? <path key={index} d={d} fill="currentColor" />
        : <path key={index} d={d} stroke="currentColor" strokeWidth={strokeWidth ?? width} strokeLinecap="round" strokeLinejoin="round" />)}
    </svg>
  );
}

/**
 * An interface icon by role (`iconRole`; `role` stays free for the ARIA attribute). It shows the default glyph, or the glyph the active theme defines for that role.
 * Props match HugeiconsIcon (size, strokeWidth, className, aria-*). An explicit strokeWidth prop wins over the theme's.
 */
export function UiIcon({ iconRole, ...props }: { iconRole: IconRole } & IconProps) {
  const custom = useThemeAssets().icons?.[iconRole];
  if (custom) return <CustomSvgIcon icon={custom} {...props} />;
  return <HugeiconsIcon icon={DEFAULT_ICONS[iconRole]} {...props} />;
}
