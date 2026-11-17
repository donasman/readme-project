// 화면 회귀 검사: 모양이 바뀌면 안 되는 변경(공용 부품 뽑기 등) 앞뒤로 화면을 찍어 픽셀을 비교한다.
// 개발 서버(npm run dev)를 띄운 채로:
//   npm run regress -- shoot before     고치기 전
//   npm run regress -- shoot after      고친 뒤
//   npm run regress -- compare before after
// 결과는 .regress/<이름>/ 에 남는다(git에 올리지 않는다). 새 패키지 없이 설치된 Chrome을 DevTools 프로토콜로 부린다.
// 가짜 서버는 새로 불러올 때마다 처음 상태라, 장면마다 주소를 새로 열어 같은 화면을 찍는다.
import { spawn } from "node:child_process"
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"

const OUT = join(import.meta.dirname, "..", ".regress")
// Chrome 프로필은 프로젝트 밖에 둔다. 안에 두면 lint와 vitest가 프로필 속 확장 프로그램 파일까지 읽는다
const PROFILE = join(tmpdir(), "readme-regress-profile")
const APP = process.env.REGRESS_URL ?? "http://127.0.0.1:5173"
const CHROME =
  process.env.CHROME_PATH ??
  [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
  ].find(existsSync)
const [mode, a, b] = process.argv.slice(2)

const BE1 = "/groups/be/posts/be1"
const NEW = "/groups/be/posts/new"
// 장면: path를 열고 steps를 차례로 한다. ["press", 글자] 그 글자나 aria-label의 버튼·링크를 누른다,
// ["click", CSS 선택자] 그 요소 가운데를 마우스로 누른다(메뉴처럼 포인터 이벤트가 필요한 것), ["fill", 선택자, 값],
// ["wait", 글자] 그 글자가 화면에 나올 때까지(15초까지) 기다린다(카드 뽑기처럼 시간이 걸리는 것).
const SCENARIOS = [
  // 그룹 공고(4차)
  { name: "list-desktop", path: "/groups/be/posts", width: 1280 },
  { name: "list-desktop-dark", path: "/groups/be/posts", width: 1280, theme: "dark" },
  { name: "list-mobile", path: "/groups/be/posts", width: 390, scale: 2 },
  { name: "list-mobile-dark", path: "/groups/be/posts", width: 390, scale: 2, theme: "dark" },
  { name: "list-closed", path: "/groups/be/posts?tab=closed", width: 1280 },
  { name: "list-filtered", path: "/groups/pub/posts?sort=recent&mine=1", width: 1280 },
  { name: "list-group-menu", path: "/groups/be/posts", width: 1280, steps: [["click", 'header [data-slot="dropdown-menu-trigger"]']] },
  { name: "mobile-menu", path: "/groups/be/posts", width: 390, scale: 2, steps: [["click", 'button[aria-label="메뉴"]']] },
  { name: "detail-desktop", path: BE1, width: 1280 },
  { name: "detail-desktop-dark", path: BE1, width: 1280, theme: "dark" },
  { name: "detail-mobile", path: BE1, width: 390, scale: 2 },
  { name: "detail-other", path: "/groups/mes/posts/mes1", width: 1280 },
  { name: "error-group", path: "/groups/nope/posts", width: 1280 },
  { name: "error-post", path: "/groups/be/posts/nope", width: 1280 },
  { name: "error-path", path: "/nope", width: 1280 },
  { name: "dev-components", path: "/dev/components", width: 1280 },
  // 창(7차)
  { name: "dialog-deadline", path: "/groups/be/posts", width: 1280, steps: [["press", "날짜 넣기"]] },
  { name: "dialog-takedown", path: "/groups/be/posts/be5", width: 1280, steps: [["press", "그룹에서 내리기"]] },
  // 공고 고치기(10차). be5는 마감을 모르는 내 공고, mes1은 가짜 서버가 남과 함께 쓰는 공고로 본다
  { name: "dialog-post-edit", path: "/groups/be/posts/be5", width: 1280, steps: [["press", "공고 고치기"]] },
  { name: "dialog-post-edit-date", path: "/groups/be/posts/be5", width: 1280, steps: [["press", "공고 고치기"], ["press", "날짜"], ["press", "저장"]] },
  { name: "dialog-post-edit-shared", path: "/groups/mes/posts/mes1", width: 1280, steps: [["press", "공고 고치기"], ["press", "저장"]] },
  { name: "post-edited", path: "/groups/be/posts/be5", width: 1280, steps: [["press", "공고 고치기"], ["press", "날짜"], ["fill", "#pe-due", "2026-11-30"], ["press", "저장"]] },
  { name: "dialog-comment-edit", path: BE1, width: 1280, steps: [["press", "수정"]] },
  { name: "dialog-comment-delete", path: BE1, width: 1280, steps: [["press", "삭제"]] },
  { name: "dialog-disclosure", path: BE1, width: 1280, steps: [["press", "공개 범위 바꾸기"]] },
  { name: "dialog-card-delete", path: "/cards/1", width: 1280, steps: [["press", "삭제"]] },
  { name: "dialog-review-drop", path: "/cards/review", width: 1280, steps: [["press", "이 카드 버리기"]] },
  { name: "dialog-leave", path: "/groups/be/members", width: 1280, steps: [["press", "그룹 나가기"]] },
  { name: "dialog-create-group", path: "/groups", width: 1280, steps: [["press", "그룹 만들기"]] },
  { name: "action-error", path: "/groups/be/posts?mock=offline", width: 1280, steps: [["press", "관심 켜기"]] },
  // 공고 올리기(7차)
  { name: "post-new", path: NEW, width: 1280 },
  { name: "post-new-text", path: NEW, width: 1280, steps: [["press", "본문 붙여넣기"]] },
  { name: "post-new-duplicate", path: NEW, width: 1280, steps: [["fill", "#pn-url", "https://careers.example.com/jobs/1001"], ["press", "읽어 오기"]] },
  { name: "post-new-failed", path: NEW, width: 1280, steps: [["fill", "#pn-url", "https://example.com/login/job"], ["press", "읽어 오기"]] },
  { name: "post-check", path: NEW, width: 1280, steps: [["fill", "#pn-url", "https://careers.example.com/jobs/2001"], ["press", "읽어 오기"]] },
  { name: "post-check-mobile", path: NEW, width: 390, scale: 2, steps: [["fill", "#pn-url", "https://careers.example.com/jobs/2001"], ["press", "읽어 오기"]] },
  // 그룹(7차)
  { name: "groups", path: "/groups", width: 1280 },
  { name: "groups-empty", path: "/groups?mock=no-groups", width: 1280 },
  { name: "members", path: "/groups/be/members", width: 1280 },
  { name: "members-leader", path: "/groups/mes/members", width: 1280 },
  { name: "members-mobile", path: "/groups/be/members", width: 390, scale: 2 },
  // 초대 목록·취소(10차). 백엔드 스터디에는 쓸 수 있는 링크가 셋 있고(내 것 하나), MES 준비반은 내가 그룹장이고 링크가 없다
  { name: "members-link-made", path: "/groups/mes/members", width: 1280, steps: [["press", "초대 링크 만들기"]] },
  { name: "dialog-invite-cancel", path: "/groups/be/members", width: 1280, steps: [["press", "내가 2026-11-10 09:40에 만든 링크 취소"]] },
  { name: "members-link-cancelled", path: "/groups/be/members", width: 1280, steps: [["press", "내가 2026-11-10 09:40에 만든 링크 취소"], ["press", "링크 취소"]] },
  { name: "invite", path: "/invite/Xk3f9QmT2a", width: 1280 },
  { name: "invite-guest", path: "/invite/Xk3f9QmT2a?mock=guest", width: 1280 },
  { name: "invite-expired", path: "/invite/nope", width: 1280 },
  // 로그인 전(1·5·9차)
  { name: "landing", path: "/welcome", width: 1280 },
  { name: "landing-mobile", path: "/welcome", width: 390, scale: 2 },
  { name: "login", path: "/login", width: 1280 },
  { name: "signup", path: "/signup", width: 1280 },
  // 생년월일을 넣고 수집·이용 동의 없이 가입을 누르면 동의 칸 아래에 알린다(로그인 문서 7절 27번)
  { name: "signup-consent", path: "/signup", width: 1280, steps: [["fill", "#signup-name", "다온"], ["fill", "#signup-email", "new@example.com"], ["fill", "#signup-password", "readme1234"], ["fill", "#signup-birth", "2000-01-01"], ["press", "가입하기"]] },
  { name: "password-reset", path: "/password-reset", width: 1280 },
  { name: "verify-expired", path: "/verify-email", width: 1280 },
  // 경험카드(1·6차)
  { name: "cards", path: "/cards", width: 1280 },
  { name: "cards-dark", path: "/cards", width: 1280, theme: "dark" },
  { name: "card", path: "/cards/1", width: 1280 },
  { name: "card-edit", path: "/cards/1/edit", width: 1280 },
  { name: "card-new", path: "/cards/new", width: 1280 },
  { name: "card-sources", path: "/cards/sources", width: 1280 },
  { name: "card-sources-mobile", path: "/cards/sources", width: 390, scale: 2 },
  { name: "card-sources-done", path: "/cards/sources?mock=empty-profile", width: 1280, steps: [["fill", "#src-text", "2025년 3월부터 예시 커머스에서 주문 조회 API 응답 시간을 개선했다."], ["press", "이 글 읽기"], ["press", "카드 뽑기 시작"], ["wait", "프로필 칸 확인"]] },
  { name: "card-review", path: "/cards/review", width: 1280 },
  // 홈·내 공고(8·9차)
  { name: "home", path: "/", width: 1280 },
  { name: "home-dark", path: "/", width: 1280, theme: "dark" },
  { name: "home-mobile", path: "/", width: 390, scale: 2 },
  { name: "my-posts", path: "/my-posts", width: 1280 },
  { name: "my-posts-mobile", path: "/my-posts", width: 390, scale: 2 },
  { name: "my-post", path: "/my-posts/p-be1", width: 1280 },
  { name: "my-post-new", path: "/my-posts/new", width: 1280 },
  { name: "my-post-check", path: "/my-posts/new", width: 1280, steps: [["fill", "#pn-url", "https://careers.example.com/jobs/2001"], ["press", "읽어 오기"]] },
  // 예시 공고 be1의 주소를 넣으면 가짜 서버가 이미 내 공고에 있는 공고(p-be1)로 본다
  { name: "my-post-arrived", path: "/my-posts/new", width: 1280, steps: [["fill", "#pn-url", "https://careers.example.com/jobs/be1"], ["press", "읽어 오기"], ["press", "상시"], ["press", "내 공고에 넣기"]] },
  { name: "board", path: "/board", width: 1280 },
  { name: "board-editor", path: "/board?posting=p-be1", width: 1280 },
  { name: "calendar", path: "/calendar", width: 1280 },
  { name: "mail", path: "/mail", width: 1280 },
  // 설정(9차)
  { name: "account", path: "/settings/account", width: 1280 },
  { name: "account-no-google", path: "/settings/account?mock=no-google", width: 1280 },
  // 구글 연결에서 돌아온 결과(로그인 문서 7절 23번)
  { name: "account-google-linked", path: "/settings/account?google=linked", width: 1280 },
  { name: "account-google-in-use", path: "/settings/account?mock=no-google&google=in-use", width: 1280 },
  // 비밀번호 바꾸기(10차). 창의 버튼은 내 정보의 버튼과 글자가 같아 선택자로 누른다. 가짜 서버의 지금 비밀번호는 readme1234다
  { name: "dialog-password", path: "/settings/account", width: 1280, steps: [["press", "비밀번호 바꾸기"]] },
  { name: "dialog-password-wrong", path: "/settings/account", width: 1280, steps: [["press", "비밀번호 바꾸기"], ["fill", "#pw-current", "nope1234"], ["fill", "#pw-new", "readme5678"], ["click", "[role=dialog] button[type=submit]"]] },
  { name: "dialog-password-short", path: "/settings/account", width: 1280, steps: [["press", "비밀번호 바꾸기"], ["fill", "#pw-current", "readme1234"], ["fill", "#pw-new", "short"], ["click", "[role=dialog] button[type=submit]"]] },
  { name: "account-password-changed", path: "/settings/account", width: 1280, steps: [["press", "비밀번호 바꾸기"], ["fill", "#pw-current", "readme1234"], ["fill", "#pw-new", "readme5678"], ["click", "[role=dialog] button[type=submit]"]] },
  { name: "dialog-password-create", path: "/settings/account?mock=no-password", width: 1280, steps: [["press", "비밀번호 만들기"]] },
  { name: "dialog-password-sent", path: "/settings/account?mock=no-password", width: 1280, steps: [["press", "비밀번호 만들기"], ["press", "재설정 메일 받기"]] },
  { name: "profile", path: "/settings/profile", width: 1280 },
  { name: "profile-empty", path: "/settings/profile?mock=empty-profile", width: 1280 },
  { name: "profile-source", path: "/settings/profile?mock=empty-profile&from=source", width: 1280 },
  { name: "profile-error", path: "/settings/profile", width: 1280, steps: [["fill", '[aria-label="학력 1"] input[inputmode="decimal"]', "4.8"], ["press", "저장"]] },
  { name: "profile-saved", path: "/settings/profile", width: 1280, steps: [["fill", '[aria-label="자격증 2"] input', "컴활"], ["press", "저장"]] },
  { name: "profile-mobile", path: "/settings/profile", width: 390, scale: 2 },
  { name: "mail-settings", path: "/settings/mail", width: 1280 },
  { name: "withdraw", path: "/settings/withdraw", width: 1280 },
  { name: "settings-mobile", path: "/settings/account", width: 390, scale: 2 },
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function openChrome() {
  if (!CHROME) throw new Error("Chrome을 찾지 못했다. CHROME_PATH에 chrome 실행 파일 경로를 넣어 주세요.")
  const port = 9336
  const chrome = spawn(CHROME, ["--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${PROFILE}`, "--no-first-run", "--no-default-browser-check", "--hide-scrollbars", "--force-color-profile=srgb", "--font-render-hinting=none", "--disable-gpu", "--disable-gpu-rasterization", "--disable-lcd-text", "--disable-partial-raster", "--disable-threaded-animation", "--disable-threaded-scrolling", "--num-raster-threads=1", "about:blank"], { stdio: "ignore" })
  let targets = []
  for (let i = 0; i < 75 && !targets.some((t) => t.type === "page"); i++) {
    try {
      targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
    } catch {
      /* 아직 뜨는 중 */
    }
    if (!targets.some((t) => t.type === "page")) await sleep(200)
  }
  const page = targets.find((t) => t.type === "page")
  if (!page) {
    // 여기서 끝나면 shoot의 finally가 닿지 않으므로 직접 닫는다
    chrome.kill()
    throw new Error("Chrome의 페이지에 붙지 못했다")
  }
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r) => ws.addEventListener("open", r, { once: true }))
  let id = 0
  const pending = new Map()
  const problems = []
  ws.addEventListener("message", (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
    if (m.method === "Runtime.exceptionThrown") problems.push("exception: " + (m.params.exceptionDetails.exception?.description ?? m.params.exceptionDetails.text).split("\n")[0])
    if (m.method === "Runtime.consoleAPICalled" && (m.params.type === "error" || m.params.type === "warning")) problems.push(`console.${m.params.type}: ` + m.params.args.map((x) => x.value ?? x.description ?? "").join(" ").slice(0, 300))
  })
  const send = (method, params = {}) => new Promise((r) => { const n = ++id; pending.set(n, r); ws.send(JSON.stringify({ id: n, method, params })) })
  const evaluate = async (expression) => (await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true })).result?.result?.value
  await send("Page.enable")
  await send("Runtime.enable")
  return { send, evaluate, problems, close: () => { ws.close(); chrome.kill() } }
}

// 글꼴과 두 번의 화면 그리기를 기다린다.
const SETTLE = `new Promise(async (r) => { await document.fonts.ready; requestAnimationFrame(() => requestAnimationFrame(r)) })`

async function step(c, [kind, target, value]) {
  if (kind === "press") {
    const ok = await c.evaluate(`(() => { const el = [...document.querySelectorAll("button, a, [role=radio], [role=tab]")].find((x) => x.textContent.trim() === ${JSON.stringify(target)} || x.getAttribute("aria-label") === ${JSON.stringify(target)}); el?.click(); return !!el })()`)
    if (!ok) throw new Error(`누를 것을 찾지 못했다: ${target}`)
  } else if (kind === "click") {
    const box = await c.evaluate(`(() => { const r = document.querySelector(${JSON.stringify(target)})?.getBoundingClientRect(); return r && { x: r.x + r.width / 2, y: r.y + r.height / 2 } })()`)
    if (!box) throw new Error(`누를 요소가 없다: ${target}`)
    for (const type of ["mousePressed", "mouseReleased"]) await c.send("Input.dispatchMouseEvent", { type, x: box.x, y: box.y, button: "left", clickCount: 1 })
  } else if (kind === "fill") {
    // React가 값 바뀜을 알도록 원래 setter로 넣고 input 이벤트를 보낸다
    await c.evaluate(`(() => { const el = document.querySelector(${JSON.stringify(target)}); el.focus(); const proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(proto, "value").set.call(el, ${JSON.stringify(value)}); el.dispatchEvent(new Event("input", { bubbles: true })) })()`)
  } else if (kind === "wait") {
    let seen = false
    for (let i = 0; i < 75 && !(seen = await c.evaluate(`document.body.innerText.includes(${JSON.stringify(target)})`)); i++) await sleep(200)
    if (!seen) throw new Error(`글자가 나오지 않았다: ${target}`)
  }
  await sleep(700)
}

async function shoot(label) {
  const out = join(OUT, label)
  mkdirSync(out, { recursive: true })
  const c = await openChrome()
  // 장면이 실패해도 Chrome을 닫는다. 남으면 다음 실행이 그 Chrome의 포트와 프로필에 붙는다
  try {
    await c.send("Page.navigate", { url: APP + "/welcome" })
    await sleep(1000)
    for (const s of SCENARIOS) {
      const scale = s.scale ?? 1
      await c.send("Emulation.setDeviceMetricsOverride", { width: s.width, height: 900, deviceScaleFactor: scale, mobile: s.width < 500 })
      await c.evaluate(`localStorage.setItem("theme", "${s.theme ?? "light"}")`)
      await c.send("Page.navigate", { url: APP + s.path })
      // 개발 서버는 처음 여는 화면을 그때 번역하므로 고정 시간 대신 main이 뜰 때까지 기다린다
      for (let i = 0; i < 50 && !(await c.evaluate(`document.readyState === "complete" && !!document.querySelector("main")`)); i++) await sleep(200)
      await sleep(400)
      await c.evaluate(SETTLE)
      for (const st of s.steps ?? []) await step(c, st)
      const height = Math.max(await c.evaluate("document.documentElement.scrollHeight"), s.width < 500 ? 844 : 800)
      await c.send("Emulation.setDeviceMetricsOverride", { width: s.width, height, deviceScaleFactor: scale, mobile: s.width < 500 })
      await sleep(400)
      await c.evaluate(SETTLE)
      const shot = await c.send("Page.captureScreenshot", { format: "png" })
      writeFileSync(join(out, s.name + ".png"), Buffer.from(shot.result.data, "base64"))
      console.log("shot", s.name, `${s.width}x${height}@${scale}`)
    }
    await c.evaluate(`localStorage.removeItem("theme")`)
    console.log(c.problems.length ? "PROBLEMS:\n" + [...new Set(c.problems)].join("\n") : "no console errors, warnings or exceptions")
  } finally {
    c.close()
  }
}

async function compare(la, lb) {
  const c = await openChrome()
  // 장면이 실패해도 Chrome을 닫는다. 남으면 다음 실행이 그 Chrome의 포트와 프로필에 붙는다
  try {
    let changed = 0
    for (const s of SCENARIOS) {
      const fa = join(OUT, la, s.name + ".png")
      const fb = join(OUT, lb, s.name + ".png")
      if (!existsSync(fa) || !existsSync(fb)) { changed++; console.log(s.name.padEnd(24), "missing"); continue }
      // PNG를 브라우저에서 풀어 화소를 비교한다(이미지 패키지 없이)
      const r = await c.evaluate(`(async () => {
        const load = (d) => new Promise((ok) => { const i = new Image(); i.onload = () => ok(i); i.src = "data:image/png;base64," + d })
        const [x, y] = await Promise.all([load(${JSON.stringify(readFileSync(fa).toString("base64"))}), load(${JSON.stringify(readFileSync(fb).toString("base64"))})])
        if (x.width !== y.width || x.height !== y.height) return { size: [x.width + "x" + x.height, y.width + "x" + y.height] }
        const px = (img) => { const cv = new OffscreenCanvas(img.width, img.height); const g = cv.getContext("2d"); g.drawImage(img, 0, 0); return g.getImageData(0, 0, img.width, img.height).data }
        const p = px(x), q = px(y); let diff = 0, big = 0, max = 0, top = -1, bottom = -1
        for (let i = 0; i < p.length; i += 4) { const d = Math.max(Math.abs(p[i] - q[i]), Math.abs(p[i+1] - q[i+1]), Math.abs(p[i+2] - q[i+2])); if (d) { diff++; if (d > max) max = d; if (d > 16) big++; const row = Math.floor(i / 4 / x.width); if (top < 0) top = row; bottom = row } }
        return { diff, big, max, total: x.width * x.height, top, bottom }
      })()`)
      if (r.size) { changed++; console.log(s.name.padEnd(24), "SIZE", r.size.join(" -> ")); continue }
      if (r.diff) changed++
      console.log(s.name.padEnd(24), r.diff ? `DIFF ${r.diff} px (${((r.diff / r.total) * 100).toFixed(3)}%) max ${r.max}, >16: ${r.big}, rows ${r.top}-${r.bottom}` : "same")
    }
    console.log(changed ? `${changed} scenario(s) differ` : "all scenarios pixel-identical")
    process.exitCode = changed ? 1 : 0
  } finally {
    c.close()
  }
}

if (mode === "shoot" && a) await shoot(a)
else if (mode === "compare" && a && b) await compare(a, b)
else console.log("usage: npm run regress -- shoot <label> | compare <labelA> <labelB>")
