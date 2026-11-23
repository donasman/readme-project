import { useState } from "react"
import { Link } from "react-router"

import { PanelHead, subLabel } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, ChevronDownIcon, ChevronRightIcon, CircleAlertIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { shownCards } from "../requirement-cards"
import type { CardMatch, RequirementResult } from "../types"
import { CardPreviewDialog, type CardPreview } from "./card-preview-dialog"

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

interface RequirementRowProps {
  requirement: RequirementResult
  matches: Map<string, CardMatch>
  onOpen: (index: number) => void
}

/** 요건 하나: 글, 오른쪽에 결과, 아래에 맞춘 카드 줄(셋까지, 나머지는 펴기)이나 프로필 값. 카드를 누르면 미리 보기 창을 연다. */
function RequirementRow({ requirement, matches, onOpen }: RequirementRowProps) {
  const { status, cards, profileItems } = requirement
  const [expanded, setExpanded] = useState(false)
  const { shown, more } = shownCards(cards, expanded)
  return (
    <li className="flex flex-col gap-2 text-sm">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">{requirement.text}</span>
        {status === "matched" || status === "profile" ? (
          <span className={STATUS_BADGES.on}>
            <CheckIcon aria-hidden="true" className="size-3" />
            {status === "matched" ? `카드 ${cards.length}장` : "프로필에 있음"}
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
      {status === "matched" ? (
        <>
          <ul className="flex flex-col gap-1.5">
            {shown.map((c, i) => {
              const match = matches.get(c.cardId)
              return (
                <li key={c.cardId}>
                  <button
                    type="button"
                    aria-haspopup="dialog"
                    onClick={() => onOpen(i)}
                    className="flex min-h-14 w-full cursor-pointer items-center gap-2.5 rounded-control border border-border-subtle bg-surface py-2.5 pr-2.5 pl-3.5 text-left text-foreground"
                  >
                    <span className="flex min-w-0 flex-auto flex-col gap-0.5">
                      {match?.experience && <span className="text-xs text-text-secondary">{match.experience}</span>}
                      <span className="leading-[1.35] font-bold">{match?.title}</span>
                      <span className="text-xs font-semibold text-accent-strong">겹친 태그 · {c.overlap.join(", ")}</span>
                    </span>
                    <ChevronRightIcon aria-hidden="true" className="size-4 flex-none text-text-secondary" />
                  </button>
                </li>
              )
            })}
          </ul>
          {more > 0 && (
            <button
              type="button"
              aria-expanded={expanded}
              onClick={() => setExpanded(!expanded)}
              className="-my-1.5 inline-flex min-h-control-md cursor-pointer items-center gap-1 self-start px-1 text-label-sm font-bold"
            >
              {expanded ? "접기" : `카드 ${more}장 더 보기`}
              <ChevronDownIcon aria-hidden="true" className={joinClasses("size-3.5", expanded && "rotate-180")} />
            </button>
          )}
        </>
      ) : (
        profileItems.length > 0 && <span className="text-label-sm text-text-secondary">프로필: {profileItems.join(", ")}</span>
      )}
    </li>
  )
}

/**
 * 요건과 내 카드(12차 시안 안 B): 필수·우대 요건마다 카드 있음, 프로필에 있음, 카드 없음, 판단 못 함. 아래에 맞는 카드.
 * 프로필은 넣지 않아도 되는 값이라, 자격증·어학 요건이 판단 못 함이면 프로필에 넣으라고 알린다.
 */
export function RequirementsPanel({ requirements, matches }: { requirements: RequirementResult[]; matches: CardMatch[] }) {
  const byId = new Map(matches.map((m) => [m.cardId, m]))
  const [preview, setPreview] = useState<CardPreview | null>(null)
  // 맞는 카드 목록에서 연 창은 그 카드의 matches[].overlap(공고 전체와 겹친 낱말)을 보인다
  const allCards = matches.map((m) => ({ cardId: m.cardId, overlap: m.overlap }))
  return (
    <Panel aria-labelledby="mp-req" className="flex flex-col gap-4 p-6">
      <PanelHead id="mp-req" title="요건과 내 카드">
        공고 분석이 뽑은 요건마다 내 카드의 태그와 프로필을 맞춰 본 결과입니다. 카드를 누르면 내용을 볼 수 있습니다.
      </PanelHead>
      {requirements.length === 0 && <p className="text-sm text-text-secondary">공고 분석이 뽑은 필수·우대 요건이 없습니다.</p>}
      {KINDS.map(({ kind, label }) => {
        const rows = requirements.filter((r) => r.kind === kind)
        return (
          rows.length > 0 && (
            <div key={kind} className="flex flex-col gap-2">
              <h3 className={subLabel}>{label}</h3>
              <ul className="flex flex-col gap-4">
                {rows.map((r, i) => (
                  <RequirementRow key={i} requirement={r} matches={byId} onOpen={(index) => setPreview({ label: r.text, overlapTitle: "이 요건과 겹친 태그", cards: r.cards, index })} />
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
                <button
                  type="button"
                  aria-haspopup="dialog"
                  onClick={() => setPreview({ label: "맞는 카드", overlapTitle: "이 공고와 겹친 태그", cards: allCards, index: i })}
                  className="flex w-full min-w-0 cursor-pointer flex-col gap-1 text-left text-foreground"
                >
                  <span className="text-sm font-bold">{m.title}</span>
                  <span className="text-label-sm text-text-secondary">겹치는 것: {m.overlap.join(", ")}</span>
                </button>
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
      <CardPreviewDialog
        preview={preview}
        matches={byId}
        onIndex={(index) => setPreview((p) => p && { ...p, index })}
        onClose={() => setPreview(null)}
      />
    </Panel>
  )
}
