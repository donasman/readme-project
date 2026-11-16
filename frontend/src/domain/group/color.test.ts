import { describe, expect, it } from "vitest"

import { GROUP_COLORS, groupColor } from "./color"

// WCAG 상대 휘도 대비. 그룹 표시는 흰 글자를 그 색 위에 쓴다
function contrastWithWhite(hex: string): number {
  const channel = (i: number) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
  return 1.05 / (luminance + 0.05)
}

describe("groupColor", () => {
  it("같은 id에는 늘 같은 색을 준다", () => {
    expect(groupColor("be")).toBe(groupColor("be"))
    expect(GROUP_COLORS).toContain(groupColor("12345"))
  })

  it("연이은 그룹 번호와 가짜 서버의 그룹은 서로 다른 색이다", () => {
    expect(new Set(["1", "2", "3", "4", "5", "6"].map(groupColor)).size).toBe(6)
    expect(new Set(["be", "pub", "mes"].map(groupColor)).size).toBe(3)
  })

  it("모든 색이 흰 글자와 4.5:1을 넘는다", () => {
    for (const color of GROUP_COLORS) expect(contrastWithWhite(color)).toBeGreaterThan(4.5)
  })
})
