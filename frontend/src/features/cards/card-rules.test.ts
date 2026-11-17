import { describe, expect, it } from "vitest"

import { countUnverified, DEFAULT_CARD_FILTERS, experienceNames, filterCards, groupByExperience, knownTag, orderTags, orgAndRole, sceneNumber, startFrom, type CardFilters } from "./card-rules"
import type { ExperienceCard } from "./types"

let seq = 0
function card(over: Partial<ExperienceCard> = {}): ExperienceCard {
  seq += 1
  return { id: String(seq), experience: "", title: `카드${seq}`, period: null, org: null, role: null, tags: [], summary: "", usableFor: ["기타"], verified: true, ...over }
}
const titles = (cards: ExperienceCard[]) => cards.map((c) => c.title)
const filters = (over: Partial<CardFilters> = {}): CardFilters => ({ ...DEFAULT_CARD_FILTERS, ...over })

describe("filterCards", () => {
  const cards = [
    card({ title: "확인 전 Java", verified: false, tags: ["Java", "Spring Boot"] }),
    card({ title: "확인 Java", tags: ["Java"] }),
    card({ title: "확인 Python", tags: ["Python"] }),
  ]

  it("전체는 다 보인다", () => {
    expect(titles(filterCards(cards, filters()))).toEqual(["확인 전 Java", "확인 Java", "확인 Python"])
  })
  it("확인 필요, 확인됨", () => {
    expect(titles(filterCards(cards, filters({ status: "unverified" })))).toEqual(["확인 전 Java"])
    expect(titles(filterCards(cards, filters({ status: "verified" })))).toEqual(["확인 Java", "확인 Python"])
  })
  it("태그는 그 태그가 붙은 카드만, 상태 거르기와 함께", () => {
    expect(titles(filterCards(cards, filters({ tag: "Java" })))).toEqual(["확인 전 Java", "확인 Java"])
    expect(titles(filterCards(cards, filters({ tag: "Java", status: "verified" })))).toEqual(["확인 Java"])
    expect(titles(filterCards(cards, filters({ tag: "Kotlin" })))).toEqual([])
  })
  it("확인 필요 수는 거르기와 상관없이 센다", () => {
    expect(countUnverified(cards)).toBe(1)
  })
})

describe("orderTags", () => {
  it("붙은 카드가 많은 순, 같으면 대소문자 없이 이름순", () => {
    const tags = [
      { name: "Redis", count: 1 },
      { name: "Spring Boot", count: 2 },
      { name: "jpa", count: 1 },
      { name: "Java", count: 2 },
      { name: "Kotlin", count: 1 },
    ]
    expect(orderTags(tags).map((t) => t.name)).toEqual(["Java", "Spring Boot", "jpa", "Kotlin", "Redis"])
    expect(tags[0].name).toBe("Redis") // 받은 배열은 그대로 둔다
  })
})

describe("orgAndRole", () => {
  it("있는 것만 가운데 점으로 잇는다", () => {
    expect(orgAndRole({ org: "예시 스터디", role: "스터디장" })).toBe("예시 스터디 · 스터디장")
    expect(orgAndRole({ org: "개인 프로젝트", role: null })).toBe("개인 프로젝트")
    expect(orgAndRole({ org: null, role: null })).toBe("")
  })
})

describe("startFrom", () => {
  const cards = [{ id: "1" }, { id: "2" }, { id: "3" }]
  it("고른 카드부터 보고 앞 카드를 뒤로 돌려 하나도 빠뜨리지 않는다", () => {
    expect(startFrom(cards, "3").map((c) => c.id)).toEqual(["3", "1", "2"])
  })
  it("첫 카드나 없는 id면 그대로", () => {
    expect(startFrom(cards, "1").map((c) => c.id)).toEqual(["1", "2", "3"])
    expect(startFrom(cards, "9").map((c) => c.id)).toEqual(["1", "2", "3"])
    expect(startFrom(cards, null).map((c) => c.id)).toEqual(["1", "2", "3"])
  })
})

describe("knownTag", () => {
  const tags = [{ name: "Java", count: 2 }, { name: "Redis", count: 1 }]
  it("지금 없는 태그는 거르지 않는다", () => {
    expect(knownTag("Kotlin", tags)).toBe("")
    expect(knownTag("Redis", tags)).toBe("Redis")
    expect(knownTag("", tags)).toBe("")
  })
})

describe("경험별 묶기(경험카드 문서 4.8)", () => {
  const scenes = () => [
    card({ title: "느린 쿼리", experience: "주문 조회 API 개선" }),
    card({ title: "혼자 1" }),
    card({ title: "혼자 2" }),
    card({ title: "캐시 설득", experience: " 주문 조회 api 개선 " }),
    card({ title: "스터디", experience: "알고리즘 스터디" }),
    card({ title: "혼자 3" }),
  ]

  it("같은 경험은 처음 나온 자리에 모으고, 경험이 없는 카드는 이어진 것끼리 묶는다", () => {
    const blocks = groupByExperience(scenes())
    expect(blocks.map((b) => [b.experience, titles(b.cards)])).toEqual([
      ["주문 조회 API 개선", ["느린 쿼리", "캐시 설득"]],
      ["", ["혼자 1", "혼자 2"]],
      ["알고리즘 스터디", ["스터디"]],
      ["", ["혼자 3"]],
    ])
  })

  it("공백만 있는 경험 이름은 경험이 없는 것이다", () => {
    expect(groupByExperience([card({ experience: "  " })]).map((b) => b.experience)).toEqual([""])
    expect(groupByExperience([])).toEqual([])
  })

  it("이미 쓴 경험 이름은 한 번씩, 처음 쓴 모양으로", () => {
    expect(experienceNames(scenes())).toEqual(["주문 조회 API 개선", "알고리즘 스터디"])
  })

  it("확인 중인 카드가 같은 경험의 몇 번째 장면인지", () => {
    const cards = scenes()
    expect(sceneNumber(cards, 0)).toEqual({ nth: 1, of: 2 })
    expect(sceneNumber(cards, 3)).toEqual({ nth: 2, of: 2 })
    // 그 경험의 카드가 한 장이거나 경험이 없으면 알리지 않는다
    expect(sceneNumber(cards, 4)).toBeNull()
    expect(sceneNumber(cards, 1)).toBeNull()
    expect(sceneNumber(cards, 9)).toBeNull()
  })
})
