// cover-art scene engine, TypeScript copy for the registry: context (title, summary, tags) -> scene spec (JSON) -> SVG.
// The studio and the cover-art skill ship the same engine as scene.mjs (packages/core/lib/cover-scene/scene.mjs); a test keeps the
// two giving the same scenes and markup. Flat filled shapes, rounded corners, no outlines, one palette per mood.

export type Tone = "plain" | "blue" | "yellow" | "ink";
export type Item = { k: string; x: number; y: number; s: number; r: number; c: string; d?: string };
export type Scene = { v: 1; tone: Tone; concept: string; mood: string; items: Item[]; dots: { x: number; y: number; r: number; c: string }[] };
export type ComposeInput = { title?: string; summary?: string; tags?: string[]; kind?: string; tone?: Tone; seed?: string; concept?: string; mood?: string; layout?: "solo" | "duo" | "trio" };

export const WIDTH = 600;
export const HEIGHT = 400;

/** Colour tokens a scene may name. `ink` and `gray` follow the page theme (CSS variables with a fallback); the rest are fixed. */
export const COLORS: Record<string, string> = {
  ink: "var(--foreground, #1A1A1A)",
  gray: "var(--border, #DFE5EB)",
  white: "#FFFFFF",
  blue: "#2563EB",
  yellow: "#FBBF24",
  green: "#37B66C",
  purple: "#8B5CF6",
  pink: "#F0568A",
  teal: "#14B8C4",
  orange: "#FB7C3C",
};

/** The ground of each band tone (plain is the soft card ground). */
export const GROUNDS: Record<Tone, string> = {
  plain: "var(--surface-muted, #F7F5F1)",
  blue: "var(--accent, #2563EB)",
  yellow: "var(--highlight, #FBBF24)",
  ink: "color-mix(in oklab, var(--inverse, #1A1A1A) 88%, var(--inverse-foreground, #fff))",
};

/** Mood -> the colours a scene draws with (first is the main one). The same mood always looks related, different moods look different. */
export const PALETTES: Record<string, string[]> = {
  calm: ["blue", "teal", "yellow"],
  energetic: ["orange", "pink", "yellow"],
  serious: ["ink", "blue", "yellow"],
  playful: ["purple", "pink", "yellow"],
  fresh: ["green", "teal", "yellow"],
  warm: ["orange", "yellow", "purple"],
};
export const MOODS = Object.keys(PALETTES);
/** The names a theme may use for `cover.concept` and `cover.mood` (tuples, so a schema can list them). */
export const COVER_MOODS = MOODS as [string, ...string[]];

/** Concept -> the shapes that can stand for it, and the words (ko and en) that point at it. */
export const CONCEPTS: Record<string, { shapes: string[]; words: RegExp }> = {
  motion: { shapes: ["trail", "orbit", "chevrons"], words: /motion|animat|transition|gsap|움직|모션|애니메이션|전환/ },
  data: { shapes: ["bars", "stairs", "clock"], words: /rank|data|stat|metric|chart|analytics|순위|랭킹|지표|통계|차트|데이터/ },
  flow: { shapes: ["nodes", "link", "stack"], words: /flow|pipeline|workflow|automat|\bci\b|agent|n8n|자동화|흐름|파이프라인|워크플로/ },
  system: { shapes: ["grid", "squircle", "fan"], words: /design system|component|library|icon|token|ui kit|컴포넌트|디자인 시스템|라이브러리|아이콘|토큰/ },
  ai: { shapes: ["spark", "orbit", "burst"], words: /\bai\b|llm|model|gpt|claude|gemini|agent|인공지능|모델|에이전트/ },
  speed: { shapes: ["chevrons", "quarter", "trail"], words: /speed|perform|fast|latency|vitals|build|성능|속도|빠르|최적화/ },
  safety: { shapes: ["arch", "ring", "hex"], words: /secur|privacy|auth|password|permission|보안|개인정보|인증|권한|약관|법/ },
  launch: { shapes: ["burst", "spark", "drop"], words: /launch|release|news|breaking|announc|update|출시|발표|속보|뉴스|업데이트/ },
  web: { shapes: ["window", "fan", "grid"], words: /web|browser|css|html|frontend|page|site|웹|브라우저|페이지|사이트/ },
  brand: { shapes: ["petals", "halves", "crescent"], words: /brand|logo|color|identity|typography|font|브랜드|로고|색|서체|글꼴/ },
  code: { shapes: ["brackets", "stack", "nodes"], words: /code|dev|api|sdk|typescript|react|next|코드|개발|프레임워크/ },
  mobile: { shapes: ["phone", "squircle", "fan"], words: /app|mobile|ios|android|앱|모바일/ },
  spooky: { shapes: ["ghost", "pumpkin"], words: /(?!)/ },
  harvest: { shapes: ["songpyeon", "persimmon"], words: /(?!)/ },
};

export const COVER_CONCEPTS = Object.keys(CONCEPTS) as [string, ...string[]];

/** Concepts only named on purpose (a season theme's `cover`, or a title that says so); the random fallback never picks them, so older covers keep their look. */
export const SEASON_CONCEPTS: string[] = ["spooky", "harvest"];

/** Words that lean a mood. Kind of post is the fallback when no word matches. */
const MOOD_WORDS: [string, RegExp][] = [
  ["serious", /secur|law|legal|privacy|policy|breaking|보안|약관|법|정책|속보|규제/],
  ["energetic", /rank|launch|speed|fast|race|순위|랭킹|출시|빠르|경쟁/],
  ["playful", /animat|motion|icon|game|fun|play|애니메이션|모션|아이콘|게임|놀이/],
  ["fresh", /growth|open source|free|new|성장|오픈소스|무료|새로/],
  ["warm", /brand|story|community|case|브랜드|이야기|커뮤니티|사례/],
  ["calm", /design system|guide|docs|tutorial|가이드|문서|디자인 시스템|튜토리얼/],
];
const MOOD_OF_KIND: Record<string, string> = { news: "serious", rankings: "energetic", trends: "calm", series: "warm", "deep-dives": "calm", playground: "playful" };

/** A stable string hash (FNV-1a, 32 bit): the same everywhere. */
export function hash(text: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193) >>> 0;
  return h;
}

/** A small seeded random number generator (mulberry32). */
export function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const INK = COLORS.ink!;
const G = "var(--surface-muted, #F7F5F1)";

/**
 * Shape drawings, around 0,0 in about 280 x 280. `c` is the main colour, `d` the second. Holes are real holes (evenodd), so a ring shows the ground.
 * Every name here can be used as `k` in a hand-written scene.
 */
const SHAPES: Record<string, (c: string, d: string) => string> = {
  circle: (c: string) => `<circle r="120" fill="${c}"/>`,
  ring: (c: string) => `<path fill="${c}" fill-rule="evenodd" d="M-124 0a124 124 0 1 0 248 0a124 124 0 1 0 -248 0ZM-74 0a74 74 0 1 1 148 0a74 74 0 1 1 -148 0Z"/>`,
  squircle: (c: string, d: string) => `<rect x="-120" y="-120" width="240" height="240" rx="70" fill="${c}"/><circle r="46" fill="${d}"/>`,
  pill: (c: string, d: string) => `<rect x="-140" y="-48" width="280" height="96" rx="48" fill="${c}"/><circle cx="-92" r="22" fill="${d}"/>`,
  arch: (c: string, d: string) => `<path d="M-110 120 V-10 a110 110 0 0 1 220 0 V120 z" fill="${c}"/><circle cy="-8" r="40" fill="${d}"/>`,
  half: (c: string, d: string) => `<path d="M0 -120 a120 120 0 0 1 0 240 Z" fill="${c}"/><path d="M0 -120 a120 120 0 0 0 0 240 Z" fill="${d}"/>`,
  halves: (c: string, d: string) => `<path d="M0 -120 a120 120 0 0 1 0 240 Z" fill="${c}"/><path d="M0 -120 a120 120 0 0 0 0 240 Z" fill="${d}"/><circle r="32" fill="${G}"/>`,
  quarter: (c: string, d: string) => `<path d="M-120 124 V-120 A244 244 0 0 1 124 124 Z" fill="${c}"/><circle cx="-62" cy="62" r="30" fill="${d}"/>`,
  tri: (c: string, d: string) => `<path d="M0 -125 L120 95 Q124 120 96 120 H-96 Q-124 120 -120 95 Z" fill="${c}" stroke="${c}" stroke-width="22" stroke-linejoin="round"/><circle cy="44" r="28" fill="${d}"/>`,
  drop: (c: string, d: string) => `<path d="M0 -132 C70 -52 112 -12 112 40 a112 112 0 0 1 -224 0 C-112 -12 -70 -52 0 -132 Z" fill="${c}"/><circle cx="-34" cy="46" r="24" fill="${d}" opacity=".85"/>`,
  spark: (c: string, d: string) => `<path d="M0 -136 C10 -42 42 -10 136 0 C42 10 10 42 0 136 C-10 42 -42 10 -136 0 C-42 -10 -10 -42 0 -136 Z" fill="${c}"/><circle cx="92" cy="-92" r="16" fill="${d}"/>`,
  hex: (c: string, d: string) => `<path d="M0 -108 L94 -54 V54 L0 108 L-94 54 V-54 Z" fill="${c}" stroke="${c}" stroke-width="30" stroke-linejoin="round"/><circle r="34" fill="${d}"/>`,
  crescent: (c: string, d: string) => `<path d="M30 -122 A124 124 0 1 0 30 122 A92 92 0 1 1 30 -122 Z" fill="${c}"/><circle cx="70" cy="-60" r="18" fill="${d}"/>`,
  petals: (c: string, d: string) => `<g fill="${c}"><rect x="-40" y="-130" width="80" height="260" rx="40"/><rect x="-130" y="-40" width="260" height="80" rx="40"/></g><circle r="30" fill="${d}"/>`,
  stairs: (c: string, d: string) => `<rect x="-130" y="30" width="72" height="90" rx="26" fill="${d}"/><rect x="-36" y="-30" width="72" height="150" rx="26" fill="${c}"/><rect x="58" y="-100" width="72" height="220" rx="26" fill="${INK}"/>`,
  bars: (c: string, d: string) => `<rect x="-120" y="-10" width="64" height="130" rx="32" fill="${d}"/><rect x="-32" y="-70" width="64" height="190" rx="32" fill="${c}"/><rect x="56" y="-130" width="64" height="250" rx="32" fill="${INK}"/>`,
  grid: (c: string, d: string) => `<rect x="-128" y="-128" width="120" height="120" rx="34" fill="${c}"/><circle cx="68" cy="-68" r="60" fill="${d}"/><rect x="-128" y="8" width="120" height="120" rx="60" fill="${INK}"/><rect x="8" y="8" width="120" height="120" rx="34" fill="${COLORS.gray}"/>`,
  stack: (c: string, d: string) => `<rect x="-134" y="-104" width="176" height="60" rx="30" fill="${c}"/><rect x="-54" y="-30" width="188" height="60" rx="30" fill="${INK}"/><rect x="-100" y="44" width="150" height="60" rx="30" fill="${d}"/>`,
  fan: (c: string, d: string) => `<g transform="translate(-74 8) rotate(-14)"><rect x="-64" y="-96" width="128" height="184" rx="30" fill="${c}"/></g><g transform="translate(74 8) rotate(14)"><rect x="-64" y="-96" width="128" height="184" rx="30" fill="${d}"/></g><rect x="-64" y="-110" width="128" height="196" rx="30" fill="${INK}"/>`,
  window: (c: string, d: string) => `<rect x="-132" y="-94" width="264" height="188" rx="36" fill="${COLORS.gray}"/><circle cx="-96" cy="-60" r="9" fill="${INK}"/><circle cx="-70" cy="-60" r="9" fill="${c}"/><circle cx="-44" cy="-60" r="9" fill="${d}"/><rect x="-100" y="-26" width="120" height="104" rx="22" fill="${c}"/><rect x="34" y="-26" width="66" height="48" rx="22" fill="${INK}"/>`,
  phone: (c: string, d: string) => `<rect x="-78" y="-136" width="156" height="272" rx="42" fill="${INK}"/><rect x="-62" y="-118" width="124" height="236" rx="30" fill="${c}"/><circle cy="-34" r="34" fill="${d}"/><rect x="-36" y="26" width="72" height="18" rx="9" fill="#fff" opacity=".85"/>`,
  orbit: (c: string, d: string) => `<path fill="${c}" fill-rule="evenodd" opacity=".9" d="M-130 0a130 130 0 1 0 260 0a130 130 0 1 0 -260 0ZM-112 0a112 112 0 1 1 224 0a112 112 0 1 1 -224 0Z"/><circle r="50" fill="${INK}"/><circle cx="92" cy="-92" r="26" fill="${d}"/><circle cx="-112" cy="0" r="14" fill="${c}"/>`,
  trail: (c: string) => `<circle cx="-130" r="20" fill="${c}" opacity=".16"/><circle cx="-82" r="34" fill="${c}" opacity=".3"/><circle cx="-12" r="52" fill="${c}" opacity=".55"/><circle cx="84" r="76" fill="${c}"/><rect x="-150" y="104" width="300" height="14" rx="7" fill="${COLORS.gray}"/>`,
  chevrons: (c: string, d: string) => `<g fill="none" stroke-width="40" stroke-linecap="round" stroke-linejoin="round"><path d="M-110 -90 L-40 0 L-110 90" stroke="${d}" opacity=".5"/><path d="M-30 -90 L40 0 L-30 90" stroke="${c}" opacity=".8"/><path d="M50 -90 L120 0 L50 90" stroke="${INK}"/></g>`,
  nodes: (c: string, d: string) => `<path d="M-84 -60 L70 -76 M-84 -60 L-30 80 M70 -76 L-30 80" fill="none" stroke="${COLORS.gray}" stroke-width="16" stroke-linecap="round"/><circle cx="-84" cy="-60" r="46" fill="${c}"/><circle cx="70" cy="-76" r="38" fill="${INK}"/><circle cx="-30" cy="80" r="42" fill="${d}"/>`,
  link: (c: string, d: string) => `<g transform="rotate(-24)"><rect x="-150" y="-44" width="170" height="88" rx="44" fill="${c}"/><rect x="-20" y="-44" width="170" height="88" rx="44" fill="${d}" opacity=".92"/><rect x="-60" y="-14" width="120" height="28" rx="14" fill="${INK}"/></g>`,
  clock: (c: string) => `<circle r="120" fill="${c}"/><circle r="84" fill="${G}"/><rect x="-6" y="-66" width="12" height="70" rx="6" fill="${INK}"/><rect x="-5" y="-4" width="10" height="56" rx="5" fill="${INK}" transform="rotate(-62)"/>`,
  burst: (c: string, d: string) => Array.from({ length: 8 }, (_, k) => `<rect x="-14" y="-140" width="28" height="64" rx="14" fill="${k % 2 ? d : c}" transform="rotate(${k * 45})"/>`).join("") + `<circle r="52" fill="${INK}"/>`,
  brackets: (c: string, d: string) => `<g fill="none" stroke-width="40" stroke-linecap="round" stroke-linejoin="round"><path d="M-70 -80 L-140 0 L-70 80" stroke="${c}"/><path d="M70 -80 L140 0 L70 80" stroke="${d}"/></g><rect x="-14" y="-96" width="28" height="192" rx="14" fill="${INK}" transform="rotate(18)"/>`,
  ghost: (c, d) => `<path d="M-100 124 V-18 a100 100 0 0 1 200 0 V124 l-33 -28 l-33 28 l-34 -28 l-33 28 l-34 -28 l-33 28 Z" fill="${c}"/><circle cx="-34" cy="-22" r="15" fill="${INK}"/><circle cx="34" cy="-22" r="15" fill="${INK}"/><circle cx="-30" cy="-27" r="5" fill="#fff"/><circle cx="38" cy="-27" r="5" fill="#fff"/><circle cx="-64" cy="14" r="15" fill="${d}" opacity=".7"/><circle cx="64" cy="14" r="15" fill="${d}" opacity=".7"/><ellipse cx="0" cy="16" rx="9" ry="12" fill="${INK}"/>`,
  pumpkin: (c, d) => `<rect x="-12" y="-132" width="24" height="42" rx="11" fill="${COLORS.green}"/><ellipse cx="-56" cy="14" rx="70" ry="94" fill="${c}" opacity=".85"/><ellipse cx="56" cy="14" rx="70" ry="94" fill="${c}" opacity=".85"/><ellipse cx="0" cy="14" rx="62" ry="100" fill="${c}"/><circle cx="-36" cy="4" r="14" fill="${INK}"/><circle cx="36" cy="4" r="14" fill="${INK}"/><path d="M-30 46 Q0 78 30 46" fill="none" stroke="${INK}" stroke-width="12" stroke-linecap="round"/><circle cx="-70" cy="40" r="13" fill="${d}" opacity=".7"/><circle cx="70" cy="40" r="13" fill="${d}" opacity=".7"/>`,
  songpyeon: (c, d) => `<path d="M-128 70 A128 128 0 0 1 128 70 Q128 104 96 104 H-96 Q-128 104 -128 70 Z" fill="${c}"/><path d="M-6 -50 C4 -92 44 -106 74 -92 C64 -56 28 -38 -6 -50 Z" fill="${d}"/><circle cx="-44" cy="30" r="13" fill="${INK}"/><circle cx="44" cy="30" r="13" fill="${INK}"/><path d="M-20 56 Q0 74 20 56" fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round"/><circle cx="-76" cy="56" r="13" fill="#fff" opacity=".55"/><circle cx="76" cy="56" r="13" fill="#fff" opacity=".55"/>`,
  persimmon: (c, d) => `<circle cx="0" cy="14" r="112" fill="${c}"/><path d="M0 -84 C-26 -118 -62 -104 -70 -88 C-44 -86 -22 -82 0 -84 C22 -82 44 -86 70 -88 C62 -104 26 -118 0 -84 Z" fill="${d}"/><rect x="-8" y="-128" width="16" height="30" rx="8" fill="${INK}"/><circle cx="-36" cy="22" r="12" fill="${INK}"/><circle cx="36" cy="22" r="12" fill="${INK}"/><path d="M-18 48 Q0 64 18 48" fill="none" stroke="${INK}" stroke-width="9" stroke-linecap="round"/><circle cx="-66" cy="44" r="12" fill="#fff" opacity=".45"/><circle cx="66" cy="44" r="12" fill="#fff" opacity=".45"/>`,
};
export const SHAPE_KEYS = Object.keys(SHAPES);

/** Small shapes that sit around the main one; each reads as a dot, a tile, a bar or a ring. */
const SATELLITES = ["circle", "squircle", "pill", "half", "ring"];
/** Where they sit (the 600 x 400 picture). */
const SLOTS: [number, number][] = [[96, 88], [506, 98], [88, 312], [506, 322], [112, 205], [512, 210]];

/** The colour a shape may not take on a ground (blue on blue, yellow on yellow, dark on dark) turns to a colour that reads. */
function readable(color: string, tone: Tone): string {
  if (tone === "blue" && color === "blue") return "white";
  if (tone === "yellow" && color === "yellow") return "ink";
  if (tone === "ink" && color === "ink") return "white";
  return color;
}

/** A seeded shuffle (Fisher-Yates). `sort(() => rand() - 0.5)` would draw a different number of random values in different engines, so Node and a browser would not agree. */
function shuffled<T>(list: T[], rand: () => number): T[] {
  const out = [...list];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

const pick = <T,>(list: T[], rand: () => number): T => list[Math.floor(rand() * list.length) % list.length];

/** Scores each concept by how many of its words the text hits; the best wins, a tie goes to the first. */
export function conceptOf(text: string): string {
  let best = "";
  let bestScore = 0;
  for (const [name, { words }] of Object.entries(CONCEPTS)) {
    const score = (text.match(new RegExp(words.source, "g")) || []).length;
    if (score > bestScore) [best, bestScore] = [name, score];
  }
  return best;
}

export function moodOf(text: string, kind: string | undefined, rand: () => number): string {
  for (const [mood, words] of MOOD_WORDS) if (words.test(text)) return mood;
  return (kind ? MOOD_OF_KIND[kind] : undefined) || pick(MOODS, rand);
}

/** Context -> scene. The same input always gives the same scene (the seed defaults to the title and tags); change `seed` to get another take. */
export function compose(input: ComposeInput = {}): Scene {
  const { title = "", summary = "", tags = [], kind, tone = "plain" } = input;
  const text = [title, summary, ...tags].join(" ").toLowerCase();
  const rand = rng(hash(`${input.seed ?? `${title}|${tags.join(",")}`}`));
  const concept = input.concept && CONCEPTS[input.concept] ? input.concept : conceptOf(text) || pick(Object.keys(CONCEPTS).filter((k) => !SEASON_CONCEPTS.includes(k)), rand);
  const mood = input.mood && PALETTES[input.mood] ? input.mood : moodOf(text, kind, rand);
  const palette = PALETTES[mood].map((c) => readable(c, tone));
  const [main, second, third] = palette;
  const shapes = CONCEPTS[concept].shapes;
  const layout = input.layout || pick(["solo", "solo", "duo", "trio"], rand);
  const items: Item[] = [];
  const tilt = () => Math.round((rand() - 0.5) * 18);
  const hole = "white";
  const heroShape = pick(shapes, rand);
  if (layout === "solo") {
    items.push({ k: heroShape, x: 300, y: 205, s: round(0.82 + rand() * 0.1), r: tilt(), c: main, d: second === main ? "white" : second });
  } else {
    // Two or three shapes of the same concept overlap a little, the biggest one in front.
    const others = shapes.filter((s) => s !== heroShape);
    const second2 = pick(others.length ? others : shapes, rand);
    items.push({ k: second2, x: 430, y: 196, s: round(0.46 + rand() * 0.06), r: tilt(), c: second, d: third });
    items.push({ k: heroShape, x: layout === "trio" ? 232 : 222, y: 212, s: round(0.7 + rand() * 0.08), r: tilt(), c: main, d: second });
    if (layout === "trio") items.push({ k: pick(SATELLITES, rand), x: 380, y: 318, s: 0.3, r: tilt(), c: third, d: hole });
  }
  // Small shapes around the middle, in the palette's other colours and the neutral ones.
  const slots = shuffled(SLOTS, rand).slice(0, 2 + Math.floor(rand() * 2));
  const small = [second, third, "gray", main];
  slots.forEach(([x, y], i) => items.push({ k: pick(SATELLITES, rand), x, y, s: round(0.22 + rand() * 0.1), r: Math.round((rand() - 0.5) * 40), c: small[i % small.length], d: hole }));
  const dotColors = [main, third, "gray"];
  const dots = Array.from({ length: 8 }, (_, i) => ({ x: Math.round(20 + rand() * 560), y: Math.round(20 + rand() * 360), r: 3 + Math.floor(rand() * 3) * 2, c: dotColors[i % 3] }));
  return { v: 1, tone, concept, mood, items, dots };
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

/** A colour token (`blue`) or a hex colour (`#2563EB`); anything else turns into ink, so a spec of unknown origin cannot put text into the markup. */
function colorOf(name: unknown, tone: Tone): string {
  if (typeof name === "string" && /^#[0-9a-fA-F]{6}$/.test(name)) return name;
  return COLORS[readable(typeof name === "string" && name in COLORS ? name : "ink", tone)];
}

const num = (n: unknown, fallback = 0): number => (typeof n === "number" && Number.isFinite(n) ? round(n) : fallback);

/** The markup inside a `<svg viewBox="0 0 600 400">`. Only numbers and fixed colours reach it. */
export function sceneMarkup(scene: Scene): string {
  const tone = scene.tone in GROUNDS ? scene.tone : "plain";
  const dots = (scene.dots || []).map((d) => `<circle cx="${num(d.x)}" cy="${num(d.y)}" r="${num(d.r, 4)}" fill="${colorOf(d.c, tone)}" opacity=".4"/>`).join("");
  const items = (scene.items || [])
    .filter((it) => it && it.k in SHAPES)
    .map((it) => {
      const c = colorOf(it.c, tone);
      const d = colorOf(it.d ?? "white", tone === "ink" ? "plain" : tone);
      return `<g transform="translate(${num(it.x, 300)} ${num(it.y, 200)}) scale(${num(it.s, 1)}) rotate(${num(it.r)})">${SHAPES[it.k](c, d)}</g>`;
    })
    .join("");
  // On the ink ground the shapes' dark parts (the theme's foreground) would vanish, so inside it the foreground is the light ink.
  const art = `${dots}${items}`;
  return `<rect width="${WIDTH}" height="${HEIGHT}" fill="${GROUNDS[tone]}"/>${tone === "ink" ? `<g style="--foreground:var(--inverse-foreground,#fff)">${art}</g>` : art}`;
}

/** A whole SVG document of a scene. */
export function sceneSvg(scene: Scene, { width = WIDTH, height = HEIGHT }: { width?: number; height?: number } = {}): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${WIDTH} ${HEIGHT}" width="${width}" height="${height}" role="img">${sceneMarkup(scene)}</svg>`;
}
