import { Link } from "react-router"

import { formatQuestionLimit } from "@/domain/posting/analysis"
import type { PostingAnalysis } from "@/domain/posting/types"
import { sectionTitle, subLabel } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, CircleAlertIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import type { CardMatch, RequirementResult } from "../types"

const KINDS = [
  { kind: "must", label: "필수 역량" },
  { kind: "nice", label: "우대 역량" },
] as const

const STATUS_BADGES = {
  /** 옅은 강조색 알약과 체크(카드 있음, 프로필에 있음) */
  on: "inline-flex h-6 flex-none items-center gap-0.5 rounded-full bg-accent-subtle pr-2.5 pl-1.5 text-xs font-semibold whitespace-nowrap text-accent-strong",
  /** 굵은 작은 글 */
  text: "inline-flex min-h-6 flex-none items-center gap-1 text-xs font-bold whitespace-nowrap",
}

/** 요건 하나: 글, 오른쪽에 결과, 아래에 맞춘 카드 이름이나 프로필 값. */
function RequirementRow({ requirement, titles }: { requirement: RequirementResult; titles: Map<string, string> }) {
  const { status, cardIds, profileItems } = requirement
  const below =
    status === "matched"
      ? cardIds.map((id) => titles.get(id) ?? "").filter(Boolean).join(" · ")
      : profileItems.length > 0
        ? `프로필: ${profileItems.join(", ")}`
        : null
  return (
    <li className="flex flex-col gap-1 text-sm">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">{requirement.text}</span>
        {status === "matched" || status === "profile" ? (
          <span className={STATUS_BADGES.on}>
            <CheckIcon aria-hidden="true" className="size-3" />
            {status === "matched" ? `카드 ${cardIds.length}장` : "프로필에 있음"}
          </span>
        ) : status === "unmatched" ? (
          <span className={STATUS_BADGES.text}>
            <CircleAlertIcon aria-hidden="true" className="size-3.5 flex-none text-warning" />
            카드 없음
          </span>
        ) : (
          <span className={joinClasses(STATUS_BADGES.text, "text-text-secondary")}>판단 못 함</span>
        )}
      </div>
      {below && <span className="text-label-sm text-text-secondary">{below}</span>}
    </li>
  )
}

/**
 * 요건과 내 카드(12차 시안 안 B): 필수·우대 요건마다 카드 있음, 프로필에 있음, 카드 없음, 판단 못 함. 아래에 맞는 카드.
 * 프로필은 넣지 않아도 되는 값이라, 자격증·어학 요건이 판단 못 함이면 프로필에 넣으라고 알린다.
 */
export function RequirementsPanel({ requirements, matches }: { requirements: RequirementResult[]; matches: CardMatch[] }) {
  const titles = new Map(matches.map((m) => [m.cardId, m.title]))
  return (
    <Panel aria-labelledby="mp-req" className="flex flex-col gap-4 p-6">
      <div className="flex flex-col gap-1">
        <h2 id="mp-req" className={sectionTitle}>
          요건과 내 카드
        </h2>
        <p className="text-sm text-text-secondary">공고 분석이 뽑은 요건마다 내 카드의 태그와 프로필을 맞춰 본 결과입니다.</p>
      </div>
      {requirements.length === 0 && <p className="text-sm text-text-secondary">공고 분석이 뽑은 필수·우대 요건이 없습니다.</p>}
      {KINDS.map(({ kind, label }) => {
        const rows = requirements.filter((r) => r.kind === kind)
        return (
          rows.length > 0 && (
            <div key={kind} className="flex flex-col gap-2">
              <h3 className={subLabel}>{label}</h3>
              <ul className="flex flex-col gap-2">
                {rows.map((r, i) => (
                  <RequirementRow key={i} requirement={r} titles={titles} />
                ))}
              </ul>
            </div>
          )
        )
      })}
      <div className="flex flex-col gap-1 border-t border-border-subtle pt-4">
        <h3 className={subLabel}>맞는 카드</h3>
        {matches.length > 0 ? (
          <ul className="flex flex-col">
            {matches.map((m, i) => (
              <li key={m.cardId} className={joinClasses("border-t py-2.5", i > 0 ? "border-border-subtle" : "border-transparent")}>
                <Link to={paths.card(m.cardId)} className="flex min-w-0 flex-col gap-1 text-foreground">
                  <span className="text-sm font-bold">{m.title}</span>
                  <span className="text-label-sm text-text-secondary">겹치는 것: {m.overlap.join(", ")}</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-text-secondary">겹치는 카드가 없습니다.</p>
        )}
      </div>
      <p className="text-label-sm text-text-secondary">
        판단 못 함은 카드 태그로 맞춰 볼 수 없는 요건입니다. 자격증·어학·전공 요건은{" "}
        <Link to={paths.profile()} className="font-bold text-primary underline">
          기본 프로필
        </Link>
        에 넣으면 맞춰 봅니다.
      </p>
    </Panel>
  )
}

/** 자기소개서 문항(12차 시안 안 B): 공고 분석이 찾은 문항과 글자 수. */
export function QuestionsPanel({ questions }: { questions: PostingAnalysis["questions"] }) {
  return (
    <Panel aria-labelledby="mp-questions" className="flex flex-col gap-4 p-6">
      <h2 id="mp-questions" className={sectionTitle}>
        자기소개서 문항
      </h2>
      <ol className="flex flex-col gap-3">
        {questions.map((q, i) => (
          <li key={i} className="flex flex-col gap-0.5 text-sm">
            <span>{q.text}</span>
            <span className="text-[13px] font-bold">{formatQuestionLimit(q.limit)}</span>
          </li>
        ))}
      </ol>
      <p className="text-label-sm text-text-secondary">공고에 적힌 문항을 AI가 찾은 것입니다. 원문과 맞는지 확인하세요.</p>
    </Panel>
  )
}
