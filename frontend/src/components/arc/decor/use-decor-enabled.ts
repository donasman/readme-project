"use client";

import { useContext } from "react";
import { DecorEnabledContext } from "../theme/provider";

/** True unless the provider's `decorEnabled` is false (the site's own setting). Also true outside a provider. */
export function useDecorEnabled(): boolean {
  return useContext(DecorEnabledContext);
}
