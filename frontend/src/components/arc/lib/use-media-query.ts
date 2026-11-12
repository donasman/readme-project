"use client";

import { useSyncExternalStore } from "react";

/** Whether a media query matches. False on the server and until the browser answers, so the first paint is the wide layout. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", notify);
      return () => list.removeEventListener("change", notify);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}
