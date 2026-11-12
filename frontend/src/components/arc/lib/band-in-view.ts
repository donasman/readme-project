"use client";

import { createContext, useContext } from "react";

/**
 * Whether the band around a block has scrolled into view. A band (for example `StoryBand`) provides it so the headline and the
 * rows inside rise together on the band's cue; `null` means no band is around and each block watches the viewport itself.
 */
export const BandInViewContext = createContext<boolean | null>(null);

/** The surrounding band's in-view state, or `null` outside a band. */
export function useBandInView(): boolean | null {
  return useContext(BandInViewContext);
}
