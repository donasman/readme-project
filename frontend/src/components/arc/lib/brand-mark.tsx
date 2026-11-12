import type { SVGProps } from "react";

/**
 * The devs7udio app mark: a brand-yellow rounded square with a white bolt (favicon-round.svg).
 * The colours are the brand's and stay fixed in both themes, so it reads on `--surface` and on dark alike.
 * Pass `size` for a square mark, or style it with `className`. It casts a soft yellow shadow unless `shadow={false}`.
 */
export function Devs7udioMark({ size = 24, shadow = true, style, ...props }: Omit<SVGProps<SVGSVGElement>, "viewBox" | "fill"> & { size?: number; shadow?: boolean }) {
  // The yellow tile casts its own color (see --shadow-highlight-resting); `shadow={false}` for flat contexts such as a favicon.
  const lifted = shadow ? { filter: "drop-shadow(0 4px 10px color-mix(in oklab, var(--highlight) 36%, transparent))", ...style } : style;
  return <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" style={lifted} {...props}>
    <path d="M0 16C0 7.16344 7.16344 0 16 0H32C40.8366 0 48 7.16344 48 16V32C48 40.8366 40.8366 48 32 48H16C7.16344 48 0 40.8366 0 32V16Z" fill="#FBBF24" />
    <path d="M34.8401 19.45C34.6052 19.018 34.1354 18.75 33.6255 18.75H24.6534L27.5661 11.7992C27.7362 11.3959 27.6843 10.9363 27.4283 10.5753C27.1723 10.2144 26.6987 10 26.292 10H16.2084C15.5192 10 14.9371 10.487 14.846 11.1397L13.0126 24.2647C12.9589 24.6398 13.0788 25.0172 13.3402 25.3016C13.6016 25.5859 13.9791 25.75 14.375 25.75H22.757L20.3633 36.3648C20.2254 36.9784 20.562 37.5986 21.1654 37.8463C21.3418 37.9672 21.5252 38 21.7085 38C22.1508 38 22.5805 37.7949 22.8435 37.4291L34.7604 20.8041C35.0463 20.4016 35.0807 19.882 34.8401 19.45Z" fill="#fff" />
  </svg>;
}

/** The service name every brand-bearing block shows by default. */
export const BRAND_NAME = "devs7udio";
