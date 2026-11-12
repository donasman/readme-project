"use client";

import { useEffect, useState } from "react";

const startOfToday = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
};

/** The viewer's local date. Undefined during SSR and hydration, then updates across midnight.
 *  The registry item for calendar does not ship this file, so it is written here from the documented contract. */
export function useToday(): Date | undefined {
  const [today, setToday] = useState<Date | undefined>(undefined);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const sync = () => {
      const current = startOfToday();
      setToday(previous => (previous && previous.getTime() === current.getTime() ? previous : current));
      const next = new Date(current.getFullYear(), current.getMonth(), current.getDate() + 1);
      timer = setTimeout(sync, next.getTime() - Date.now() + 50);
    };
    sync();
    return () => clearTimeout(timer);
  }, []);
  return today;
}
