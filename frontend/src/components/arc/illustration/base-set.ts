import type { IllustrationKey, IllustrationSpec, Motion, Role, Shape } from "./spec";

type Opts = Partial<{ enter: number; motion: Motion; delay: number; origin: readonly [number, number]; opacity: number; blur: number }>;

export const rect = (x: number, y: number, w: number, h: number, fill: Role, r = 50, o: Opts = {}): Shape => ({ kind: "rect", x, y, w, h, r, fill, ...o });
/** A pill is a rect whose radius is half its short side. */
export const pill = (x: number, y: number, w: number, h: number, fill: Role, o: Opts = {}): Shape => rect(x, y, w, h, fill, Math.min(w, h) / 2, o);
export const path = (d: string, fill: Role, o: Opts = {}): Shape => ({ kind: "path", d, fill, ...o });
export const dot = (cx: number, cy: number, r: number, fill: Role, o: Opts = {}): Shape => ({ kind: "circle", cx, cy, r, fill, ...o });

export const spec = (key: IllustrationKey, width: number, height: number, shapes: Shape[]): IllustrationSpec => ({ key, width, height, shapes });

const shift = (shapes: Shape[], dx: number): Shape[] =>
  shapes.map((s) => {
    if (s.kind === "rect") return { ...s, x: s.x + dx };
    if (s.kind === "circle" || s.kind === "ellipse") return { ...s, cx: s.cx + dx };
    return s;
  });

const recolor = (shapes: Shape[], map: Partial<Record<Role, Role>>): Shape[] => shapes.map((s) => ({ ...s, fill: map[s.fill] ?? s.fill }));

/** One 480-wide module of the header rail; the second copy swaps blue and yellow. */
const railModule = (): Shape[] => [
  rect(-60, 56, 260, 220, "blue", 50, { enter: 0 }),
  dot(100, 120, 34, "paper", { enter: 0, motion: "float" }),
  pill(56, 176, 120, 28, "yellow", { enter: 0 }),
  rect(200, -60, 190, 200, "yellow", 50, { enter: 1 }),
  dot(250, 76, 13, "onYellow", { enter: 1, motion: "blink" }),
  dot(292, 76, 13, "onYellow", { enter: 1, motion: "blink", delay: 0.4 }),
  dot(346, 76, 22, "paper", { enter: 1, motion: "pulse" }),
  rect(200, 140, 120, 140, "ink", 50, { enter: 2 }),
  dot(260, 240, 44, "yellow", { enter: 2, motion: "float" }),
  rect(320, 140, 160, 140, "mute", 50, { enter: 3 }),
  pill(380, 160, 40, 80, "blue", { enter: 3, motion: "sway" }),
];

const stepFills: Role[] = ["mute", "blue", "mute", "yellow", "mute", "ink"];


/** A skyline of pills and tiles standing on the bottom edge, for the band under a site footer. */
function footerSkyline(): Shape[] {
  const items: { x: number; w: number; h: number; fill: Role; pill?: boolean; dot?: Role; slot?: Role }[] = [
    { x: -30, w: 170, h: 110, fill: "mute", pill: true },
    { x: 150, w: 130, h: 170, fill: "blue", pill: true, dot: "paper" },
    { x: 290, w: 210, h: 90, fill: "yellow", dot: "onYellow" },
    { x: 510, w: 150, h: 190, fill: "ink", pill: true, dot: "yellow" },
    { x: 670, w: 190, h: 130, fill: "mute", slot: "blue" },
    { x: 870, w: 140, h: 160, fill: "yellow", pill: true, dot: "paper" },
    { x: 1020, w: 200, h: 100, fill: "blue", dot: "paper" },
    { x: 1230, w: 130, h: 180, fill: "ink", pill: true, dot: "blue" },
    { x: 1370, w: 110, h: 120, fill: "mute", pill: true },
  ];
  return items.flatMap((it, i): Shape[] => {
    const y = 200 - it.h;
    const body = rect(it.x, y, it.w, it.h + 60, it.fill, it.pill ? it.w / 2 : 45, { enter: i });
    const out: Shape[] = [body];
    if (it.dot) out.push(dot(it.x + it.w / 2, y + (it.pill ? it.w / 2 : it.h / 2), it.pill ? it.w * 0.2 : 20, it.dot, { enter: i, motion: i % 2 ? "float" : "pulse", delay: i * 0.2 }));
    if (it.slot) out.push(pill(it.x + it.w / 2 - 20, y + 24, 40, 70, it.slot, { enter: i, motion: "sway" }));
    return out;
  });
}

export const BASE_ILLUSTRATIONS: Record<IllustrationKey, IllustrationSpec> = {
  "hero-collage": spec("hero-collage", 960, 540, [
    rect(92, 87, 434, 520, "blue", 50, { enter: 0 }),
    dot(309, 246, 65, "paper", { enter: 0, motion: "float" }),
    pill(136, 377, 347, 44, "yellow", { enter: 0, motion: "drift" }),
    rect(526, -40, 450, 301, "yellow", 50, { enter: 1 }),
    dot(613, 109, 22, "onYellow", { enter: 1, motion: "blink" }),
    dot(699, 109, 22, "onYellow", { enter: 1, motion: "blink", delay: 0.5 }),
    dot(830, 131, 65, "paper", { enter: 1, motion: "pulse" }),
    rect(526, 261, 260, 320, "ink", 50, { enter: 2 }),
    dot(656, 540, 105, "yellow", { enter: 2, motion: "float" }),
    rect(786, 261, 190, 320, "mute", 50, { enter: 3 }),
    pill(830, 311, 87, 174, "blue", { enter: 3, motion: "sway" }),
  ]),
  "hero-stage": spec("hero-stage", 960, 540, [
    rect(-40, 90, 480, 520, "yellow", 50, { enter: 0 }),
    dot(130, 190, 22, "onYellow", { enter: 0, motion: "blink" }),
    dot(216, 190, 22, "onYellow", { enter: 0, motion: "blink", delay: 0.5 }),
    dot(300, 330, 72, "paper", { enter: 0, motion: "float" }),
    rect(480, -40, 520, 300, "blue", 50, { enter: 1 }),
    dot(700, 130, 62, "paper", { enter: 1, motion: "pulse" }),
    pill(560, 190, 260, 40, "yellow", { enter: 1, motion: "drift" }),
    rect(480, 260, 200, 320, "mute", 50, { enter: 2 }),
    pill(540, 330, 80, 160, "yellow", { enter: 2, motion: "sway" }),
    rect(680, 260, 330, 320, "ink", 50, { enter: 3 }),
    dot(845, 540, 100, "blue", { enter: 3, motion: "float" }),
  ]),
  "hero-stack": spec("hero-stack", 960, 540, [
    rect(140, 320, 170, 300, "mute", 85, { enter: 0 }),
    rect(330, 230, 170, 400, "blue", 85, { enter: 1 }),
    dot(415, 315, 38, "paper", { enter: 1, motion: "float" }),
    rect(520, 150, 170, 480, "yellow", 85, { enter: 2 }),
    dot(605, 235, 24, "onYellow", { enter: 2, motion: "blink" }),
    rect(710, 70, 170, 560, "ink", 85, { enter: 3 }),
    dot(795, 155, 38, "yellow", { enter: 3, motion: "pulse" }),
  ]),
  "hero-orbit": spec("hero-orbit", 960, 540, [
    dot(480, 270, 230, "mute", { enter: 0 }),
    dot(480, 270, 150, "blue", { enter: 1 }),
    dot(480, 270, 66, "paper", { enter: 2, motion: "pulse" }),
    dot(480, 36, 30, "yellow", { enter: 3, motion: "orbit", origin: [480, 270] }),
    dot(480, 516, 16, "ink", { enter: 3, motion: "orbit", origin: [480, 270], delay: 0 }),
    dot(150, 90, 22, "yellow", { enter: 4, motion: "float" }),
    dot(820, 450, 22, "blue", { enter: 4, motion: "float", delay: 0.6 }),
  ]),
  "header-rail": spec("header-rail", 960, 240, [
    ...railModule(),
    ...shift(recolor(railModule(), { blue: "yellow", yellow: "blue", onYellow: "paper" }), 480),
  ]),
  "header-steps": spec(
    "header-steps",
    960,
    240,
    [90, 120, 150, 180, 210, 240].flatMap((h, i): Shape[] => {
      const x = 60 + i * 136;
      const y = 240 - h;
      const fill = stepFills[i]!;
      const bar = rect(x, y, 112, h + 60, fill, 56, { enter: i });
      if (fill === "blue") return [bar, dot(x + 56, y + 56, 22, "paper", { enter: i, motion: "float" })];
      if (fill === "yellow") return [bar, dot(x + 56, y + 56, 22, "onYellow", { enter: i, motion: "pulse" })];
      if (fill === "ink") return [bar, dot(x + 56, y + 56, 22, "yellow", { enter: i, motion: "float" })];
      return [bar];
    }),
  ),
  "card-collage": spec("card-collage", 480, 320, [
    rect(60, 48, 220, 300, "blue", 40, { enter: 0 }),
    dot(170, 128, 38, "paper", { enter: 0, motion: "float" }),
    pill(90, 214, 160, 28, "yellow", { enter: 0, motion: "drift" }),
    rect(280, -20, 220, 150, "yellow", 40, { enter: 1 }),
    dot(330, 60, 12, "onYellow", { enter: 1, motion: "blink" }),
    dot(370, 60, 12, "onYellow", { enter: 1, motion: "blink", delay: 0.5 }),
    dot(430, 70, 30, "paper", { enter: 1, motion: "pulse" }),
    rect(280, 130, 120, 220, "ink", 40, { enter: 2 }),
    dot(340, 320, 56, "yellow", { enter: 2, motion: "float" }),
    rect(400, 130, 100, 220, "mute", 40, { enter: 3 }),
    pill(428, 160, 44, 110, "blue", { enter: 3, motion: "sway" }),
  ]),
  "card-dots": spec("card-dots", 480, 320, [
    rect(40, 40, 400, 240, "yellow", 48, { enter: 0 }),
    dot(120, 110, 20, "onYellow", { enter: 1, motion: "blink" }),
    dot(190, 110, 20, "onYellow", { enter: 1, motion: "blink", delay: 0.5 }),
    dot(330, 160, 60, "paper", { enter: 2, motion: "pulse" }),
    pill(100, 200, 160, 36, "blue", { enter: 3, motion: "drift" }),
  ]),
  "card-pill": spec("card-pill", 480, 320, [
    rect(120, 30, 240, 340, "mute", 56, { enter: 0 }),
    pill(200, 80, 80, 200, "blue", { enter: 1, motion: "sway" }),
    dot(240, 120, 24, "paper", { enter: 1, motion: "pulse" }),
    dot(400, 90, 22, "yellow", { enter: 2, motion: "float" }),
    dot(90, 250, 14, "ink", { enter: 2, motion: "blink" }),
  ]),
  "card-arch": spec("card-arch", 480, 320, [
    rect(80, 40, 320, 340, "ink", 56, { enter: 0 }),
    dot(240, 320, 110, "yellow", { enter: 1, motion: "float" }),
    dot(240, 120, 40, "paper", { enter: 2, motion: "pulse" }),
    dot(150, 200, 10, "mute", { enter: 2, motion: "blink" }),
    dot(330, 200, 10, "mute", { enter: 2, motion: "blink", delay: 0.5 }),
  ]),
  "page-404": spec("page-404", 960, 400, [
    rect(80, 60, 240, 360, "yellow", 50, { enter: 0 }),
    pill(120, 90, 40, 150, "onYellow", { enter: 0 }),
    pill(120, 200, 170, 40, "onYellow", { enter: 0, motion: "drift" }),
    pill(240, 90, 40, 250, "onYellow", { enter: 0 }),
    dot(480, 200, 120, "blue", { enter: 1, motion: "pulse" }),
    dot(480, 200, 56, "paper", { enter: 1 }),
    pill(540, 338, 100, 30, "mute", { enter: 2, motion: "drift", delay: 0.4 }),
    dot(400, 352, 12, "yellow", { enter: 2, motion: "blink" }),
    rect(640, 60, 240, 360, "ink", 50, { enter: 3 }),
    pill(680, 90, 40, 150, "yellow", { enter: 3 }),
    pill(680, 200, 170, 40, "yellow", { enter: 3, motion: "drift", delay: 0.3 }),
    pill(800, 90, 40, 250, "yellow", { enter: 3 }),
  ]),
  "page-faq": spec("page-faq", 960, 400, [
    rect(100, 70, 400, 130, "blue", 65, { enter: 0, motion: "float" }),
    dot(190, 135, 15, "paper", { enter: 0, motion: "blink" }),
    dot(245, 135, 15, "paper", { enter: 0, motion: "blink", delay: 0.4 }),
    dot(300, 135, 15, "paper", { enter: 0, motion: "blink", delay: 0.8 }),
    rect(380, 230, 440, 130, "yellow", 65, { enter: 1, motion: "float", delay: 0.5 }),
    pill(440, 262, 260, 18, "onYellow", { enter: 1 }),
    pill(440, 304, 170, 18, "onYellow", { enter: 1 }),
    rect(660, -40, 220, 190, "mute", 50, { enter: 2 }),
    dot(770, 70, 44, "ink", { enter: 2, motion: "pulse" }),
    dot(770, 70, 14, "yellow", { enter: 2 }),
    dot(70, 300, 16, "ink", { enter: 3, motion: "blink" }),
    dot(310, 300, 10, "blue", { enter: 3, motion: "blink", delay: 0.6 }),
  ]),
  "page-login": spec("page-login", 960, 400, [
    rect(-40, 90, 260, 360, "yellow", 50, { enter: 0 }),
    dot(80, 200, 24, "onYellow", { enter: 0, motion: "blink" }),
    dot(150, 200, 24, "onYellow", { enter: 0, motion: "blink", delay: 0.5 }),
    pill(396, 24, 36, 190, "ink", { enter: 1 }),
    pill(528, 24, 36, 190, "ink", { enter: 1 }),
    pill(396, 24, 168, 36, "ink", { enter: 1 }),
    rect(340, 150, 280, 300, "blue", 50, { enter: 2 }),
    dot(480, 250, 38, "paper", { enter: 2, motion: "pulse" }),
    pill(466, 262, 28, 90, "paper", { enter: 2 }),
    dot(750, 130, 42, "yellow", { enter: 3, motion: "float" }),
    pill(782, 120, 120, 20, "yellow", { enter: 3, motion: "drift" }),
    pill(850, 140, 20, 44, "yellow", { enter: 3 }),
    rect(680, 250, 340, 200, "mute", 50, { enter: 4 }),
    pill(740, 290, 110, 36, "blue", { enter: 4, motion: "drift", delay: 0.4 }),
  ]),
  "page-contact": spec("page-contact", 960, 400, [
    rect(60, 110, 150, 320, "ink", 44, { enter: 0 }),
    dot(135, 170, 12, "yellow", { enter: 0, motion: "blink" }),
    pill(100, 360, 70, 12, "mute", { enter: 0 }),
    rect(280, 110, 400, 280, "blue", 44, { enter: 1 }),
    path("M280 150 L480 280 L680 150 L680 130 Q680 110 660 110 L300 110 Q280 110 280 130 Z", "ink", { enter: 2, motion: "float" }),
    dot(480, 290, 26, "paper", { enter: 2, motion: "pulse" }),
    pill(740, 70, 190, 64, "mute", { enter: 3, motion: "drift" }),
    dot(780, 102, 12, "blue", { enter: 3 }),
    pill(740, 160, 130, 64, "yellow", { enter: 4, motion: "drift", delay: 0.5 }),
    dot(780, 192, 12, "onYellow", { enter: 4 }),
    dot(900, 300, 30, "blue", { enter: 4, motion: "float" }),
  ]),
  splash: spec("splash", 600, 600, [
    rect(80, 80, 220, 220, "blue", 70, { enter: 0 }),
    dot(190, 190, 54, "paper", { enter: 0, motion: "pulse" }),
    rect(300, 80, 220, 220, "yellow", 70, { enter: 1 }),
    dot(370, 150, 16, "onYellow", { enter: 1, motion: "blink" }),
    dot(450, 150, 16, "onYellow", { enter: 1, motion: "blink", delay: 0.5 }),
    rect(80, 300, 220, 220, "ink", 70, { enter: 2 }),
    dot(190, 520, 70, "yellow", { enter: 2, motion: "float" }),
    rect(300, 300, 220, 220, "mute", 70, { enter: 3 }),
    pill(386, 346, 48, 128, "blue", { enter: 3, motion: "sway" }),
    dot(300, 30, 16, "yellow", { enter: 4, motion: "orbit", origin: [300, 300] }),
    dot(300, 574, 10, "blue", { enter: 4, motion: "orbit", origin: [300, 300] }),
  ]),
  "footer-band": spec("footer-band", 1440, 200, footerSkyline()),
  "card-tabs": spec("card-tabs", 480, 320, [
    pill(60, 52, 360, 56, "blue", { enter: 0, motion: "drift" }),
    dot(88, 80, 16, "paper", { enter: 0 }),
    pill(100, 132, 320, 56, "yellow", { enter: 1, motion: "drift", delay: 0.3 }),
    dot(128, 160, 16, "onYellow", { enter: 1 }),
    pill(60, 212, 280, 56, "mute", { enter: 2, motion: "drift", delay: 0.6 }),
    dot(88, 240, 16, "blue", { enter: 2 }),
  ]),
};
