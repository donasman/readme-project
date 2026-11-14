// 같은 모양 세기: 같은 클래스 줄이 세 파일 이상에 있으면 알린다.
// "같은 모양이 3곳에서 나오면 공용 부품으로 뽑는다"(frontend/CLAUDE.md)를 사람이 세지 않게 한다.
//   npm run repeats           클래스가 모두 같은 줄
//   npm run repeats -- --loose 여백·크기 값만 다른 줄까지(뽑을 때 그 값은 className으로 받는다)
// 알려 주기만 하고 실패로 끝나지 않는다. 받아 온 부품(components/ui, arc), 가짜 서버, /dev/components는 세지 않는다.
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join, relative } from "node:path"

const SRC = join(import.meta.dirname, "..", "src")
const SKIP = /^(components[\\/](ui|arc)|mocks|app[\\/]dev)$/
const loose = process.argv.includes("--loose")

const files = []
;(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) {
      if (!SKIP.test(relative(SRC, p))) walk(p)
    } else if (/\.tsx?$/.test(name) && !name.includes(".test.")) files.push(p)
  }
})(SRC)

const CLASS = /^[\w\-:[\]/().,%#>*&=@!]+$/
const VISUAL = /^(?:[a-z]+:)*-?(flex|grid|inline|rounded|text-|bg-|border|gap-|p[xytblr]?-|m[xytblr]?-|size-|min-|max-|font-|leading-|tracking-|shadow|items-|justify-)/
// --loose에서 빼는 값: 여백, 간격, 크기, 줄 높이
const SPACING = /^(?:[a-z]+:)*-?(p[xytblr]?|m[xytblr]?|gap(-[xy])?|size|[wh]|min-[wh]|max-[wh]|leading|basis|flex-\[)[-[]/
// 생김새(색, 테두리, 모서리, 글자). --loose는 이것이 남는 줄만 묶는다. 배치만 남으면 뜻이 다른 줄끼리 묶이기 때문이다
const LOOK = /^(?:[a-z]+:)*(bg-|border|rounded|text-|font-|shadow|underline)/

const groups = new Map()
for (const file of files) {
  const text = readFileSync(file, "utf8")
  // 속성 값이나 인자 자리의 문자열만 읽는다. 화면 글 안의 따옴표("모름"으로 …)가 짝을 밀지 않게 한다
  for (const [, str] of text.matchAll(/(?<=[=(,?:[{]\s*)"([^"\n]{8,})"/g)) {
    const tokens = str.split(/\s+/).filter(Boolean)
    if (tokens.length < 4 || !tokens.every((t) => CLASS.test(t)) || !tokens.some((t) => VISUAL.test(t))) continue
    const kept = loose ? tokens.filter((t) => !SPACING.test(t)) : tokens
    if (kept.length < 3 || (loose && !kept.some((t) => LOOK.test(t)))) continue
    const key = [...kept].sort().join(" ")
    if (!groups.has(key)) groups.set(key, { sample: str, files: new Set() })
    groups.get(key).files.add(relative(SRC, file).replaceAll("\\", "/"))
  }
}

const found = [...groups.values()].filter((g) => g.files.size >= 3).sort((x, y) => y.files.size - x.files.size)
for (const g of found) {
  const shared = [...g.files].some((f) => f.startsWith("shared/components/"))
  console.log(`${g.files.size}곳${shared ? " (공용 부품에 이미 있다)" : ""}: ${g.sample}`)
  for (const f of g.files) console.log(`    ${f}`)
}
console.log(found.length ? `\n세 파일 이상에 나오는 클래스 줄 ${found.length}개${loose ? "(여백·크기 값 무시)" : ""}. 공용 부품으로 뽑을지 본다.` : "세 파일 이상에 나오는 클래스 줄이 없다.")
