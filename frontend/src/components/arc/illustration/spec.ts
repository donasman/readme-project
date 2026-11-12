/**
 * Illustration spec: a declarative list of flat shapes. One spec drives the React renderer, the
 * exported .svg files and the Figma page, so the three never drift apart.
 */

/** Colour roles. Each maps to a theme token, so a theme recolours every illustration it inherits. */
export const ROLES = ["blue", "yellow", "ink", "mute", "paper", "onYellow", "glow", "blueSubtle", "yellowSubtle"] as const;
export type Role = (typeof ROLES)[number];

/** role -> theme token and the base light value used when no theme is loaded. */
export const ROLE_TOKENS: Record<Role, { token: string; light: string; dark: string }> = {
  blue: { token: "--accent", light: "#2563eb", dark: "#6b9bff" },
  yellow: { token: "--highlight", light: "#fbbf24", dark: "#fbbf24" },
  ink: { token: "--inverse", light: "#252729", dark: "#252729" },
  mute: { token: "--border", light: "#dfe5eb", dark: "#2a2f35" },
  paper: { token: "--surface", light: "#ffffff", dark: "#101214" },
  onYellow: { token: "--highlight-foreground", light: "#252729", dark: "#252729" },
  glow: { token: "--glow", light: "#fbbf24", dark: "#fbbf24" },
  blueSubtle: { token: "--accent-subtle", light: "#dbeafe", dark: "#172554" },
  yellowSubtle: { token: "--highlight-subtle", light: "#fef3c7", dark: "#422006" },
};

/** Ambient loops that play only when `animated` is on. Entrance (`enter`) is separate. */
export const MOTIONS = ["float", "drift", "pulse", "blink", "sway", "orbit"] as const;
export type Motion = (typeof MOTIONS)[number];

interface ShapeBase {
  fill: Role;
  /** Entrance stagger index: the shape rises into place after `enter * 90ms`. Omit for no entrance. */
  enter?: number;
  motion?: Motion;
  /** Extra delay (s) before the ambient loop starts. */
  delay?: number;
  /** Rotation centre for `orbit`, in viewBox units. */
  origin?: readonly [number, number];
  opacity?: number;
  /** Gaussian blur in viewBox units, for soft glows. */
  blur?: number;
}

export type Shape =
  | (ShapeBase & { kind: "rect"; x: number; y: number; w: number; h: number; r: number })
  | (ShapeBase & { kind: "circle"; cx: number; cy: number; r: number })
  | (ShapeBase & { kind: "ellipse"; cx: number; cy: number; rx: number; ry: number; rotate?: number })
  | (ShapeBase & { kind: "path"; d: string });

export interface IllustrationSpec {
  key: IllustrationKey;
  width: number;
  height: number;
  shapes: readonly Shape[];
}

export const ILLUSTRATION_KEYS = [
  "hero-collage",
  "hero-stage",
  "hero-stack",
  "hero-orbit",
  "header-rail",
  "header-steps",
  "card-collage",
  "card-dots",
  "card-pill",
  "card-arch",
  "card-tabs",
  "page-404",
  "page-faq",
  "page-login",
  "page-contact",
  "splash",
  "footer-band",
] as const;
export type IllustrationKey = (typeof ILLUSTRATION_KEYS)[number];

/** page: the top of a 404, FAQ, login or contact page. splash: the first screen of the app. footer: a full-width band under the site footer. */
export type IllustrationKind = "hero" | "header" | "card" | "page" | "splash" | "footer";

export const ILLUSTRATION_META: Record<
  IllustrationKey,
  { kind: IllustrationKind; title: { ko: string; en: string }; summary: { ko: string; en: string } }
> = {
  "hero-collage": { kind: "hero", title: { ko: "히어로 콜라주", en: "Hero collage" }, summary: { ko: "네 개의 둥근 면이 맞물린 기본 히어로.", en: "The default hero: four rounded tiles that interlock." } },
  "hero-stage": { kind: "hero", title: { ko: "히어로 무대", en: "Hero stage" }, summary: { ko: "큰 노랑 면을 앞세운 비대칭 히어로.", en: "An asymmetric hero led by a large yellow tile." } },
  "hero-stack": { kind: "hero", title: { ko: "히어로 스택", en: "Hero stack" }, summary: { ko: "알약 기둥이 계단처럼 오르는 성장 히어로.", en: "Pill columns climbing like steps, for growth stories." } },
  "hero-orbit": { kind: "hero", title: { ko: "히어로 궤도", en: "Hero orbit" }, summary: { ko: "겹친 원 둘레를 작은 원이 도는 히어로.", en: "Concentric circles with a small one orbiting." } },
  "header-rail": { kind: "header", title: { ko: "헤더 레일", en: "Header rail" }, summary: { ko: "타일이 가로로 이어지는 섹션 헤더 띠.", en: "A band of tiles running across a section header." } },
  "header-steps": { kind: "header", title: { ko: "헤더 계단", en: "Header steps" }, summary: { ko: "알약이 오르막으로 늘어서는 섹션 헤더 띠.", en: "Pills rising left to right along a section header." } },
  "card-collage": { kind: "card", title: { ko: "카드 콜라주", en: "Card collage" }, summary: { ko: "히어로 콜라주를 카드 비율로 줄인 커버.", en: "The hero collage condensed for a card cover." } },
  "card-dots": { kind: "card", title: { ko: "카드 점", en: "Card dots" }, summary: { ko: "노랑 면에 점과 원, 알약을 얹은 커버.", en: "A yellow tile with dots, a circle and a pill." } },
  "card-pill": { kind: "card", title: { ko: "카드 알약", en: "Card pill" }, summary: { ko: "세로 알약 하나가 중심인 조용한 커버.", en: "A quiet cover centred on one vertical pill." } },
  "card-arch": { kind: "card", title: { ko: "카드 아치", en: "Card arch" }, summary: { ko: "어두운 면 위로 반원이 떠오르는 커버.", en: "A half circle rising over a dark tile." } },
  "page-404": { kind: "page", title: { ko: "404 페이지", en: "404 page" }, summary: { ko: "4, 0, 4를 도형으로 쓴 길 잃은 페이지의 상단 그림.", en: "A 4-0-4 built from shapes, for the top of a not-found page." } },
  "page-faq": { kind: "page", title: { ko: "FAQ 페이지", en: "FAQ page" }, summary: { ko: "질문과 답이 오가는 말풍선으로 그린 FAQ 상단 그림.", en: "Question and answer bubbles for the top of an FAQ page." } },
  "page-login": { kind: "page", title: { ko: "로그인 페이지", en: "Login page" }, summary: { ko: "자물쇠와 열쇠로 그린 로그인 상단 그림.", en: "A lock and key for the top of a sign-in page." } },
  "page-contact": { kind: "page", title: { ko: "연락 페이지", en: "Contact page" }, summary: { ko: "편지 봉투와 대화 말풍선으로 그린 문의 상단 그림.", en: "An envelope and chat bubbles for the top of a contact page." } },
  splash: { kind: "splash", title: { ko: "스플래시", en: "Splash" }, summary: { ko: "네 개의 면이 모여 하나가 되고 점이 도는 첫 화면 그림. 항상 animated로 써요.", en: "Four tiles gather into one while dots orbit. Meant to be played animated." } },
  "footer-band": { kind: "footer", title: { ko: "푸터 띠", en: "Footer band" }, summary: { ko: "사이트 푸터 아래를 가로로 채우는 도시 윤곽 띠.", en: "A skyline band that runs full width under the site footer." } },
  "card-tabs": { kind: "card", title: { ko: "카드 탭", en: "Card tabs" }, summary: { ko: "알약 세 줄이 목록처럼 쌓인 커버.", en: "Three pills stacked like a list." } },
};

export type IllustrationSet = Partial<Record<IllustrationKey, IllustrationSpec>>;
