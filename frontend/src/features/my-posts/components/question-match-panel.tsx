import { useState } from "react"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { STORY_PARTS } from "@/domain/card/card-rules"
import { formatQuestionLimit } from "@/domain/posting/analysis"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { BusyButton } from "@/shared/components/busy-button"
import { Chip } from "@/shared/components/chip"
import { PanelHead } from "@/shared/components/heading"
import { IconNote } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { busyButton } from "@/shared/components/step-button"
import { ChevronDownIcon, ChevronRightIcon, XIcon } from "@/shared/icons"

import { matchQuestions, saveQuestions, setQuestionCards } from "../api"
import { addableQuestionCards, isResearchType, maxCards, QUESTION_TYPES } from "../question-match"
import type { CardChoice, CardMatch, MatchResult, Question } from "../types"
import { CardPreviewDialog, type CardPreview } from "./card-preview-dialog"
import { QuestionEditDialog } from "./question-edit-dialog"

const MATCH_ERRORS: Record<Extract<MatchResult, { ok: false }>["error"], string> = {
  NO_QUESTIONS: "문항이 없어 카드를 고를 수 없습니다. 문항을 먼저 넣어 주세요.",
  NO_CARDS: "경험카드가 없어 카드를 고를 수 없습니다. 카드를 먼저 만들어 주세요.",
  LLM_LIMIT_REACHED: "이번 달 LLM 사용 한도에 닿아 카드를 고를 수 없습니다. 카드는 직접 고를 수 있습니다.",
}

const EMPHASIS_LABELS = new Map(STORY_PARTS)

const REUSED = "문항과 카드가 바뀌지 않아 앞서 고른 결과를 그대로 보입니다."

interface QuestionMatchPanelProps {
  postingId: string
  initial: Question[]
  /** 더할 수 있는 내 카드 전부 */
  choices: CardChoice[]
}

/**
 * 자기소개서 문항과 경험카드(문항 매칭 문서 5절, 시안 없음): 문항마다 번호, 글, 글자 수, 유형, 고른 카드(주된 카드와 보조 카드).
 * 카드는 LLM이 모든 문항을 한 번에 고르고(누를 때만), 그 뒤에는 문항마다 "카드 바꾸기"로 고친다. 문항은 "문항 고치기" 창에서 통째로 고친다.
 * 고르는 동안(몇 초)은 문항과 카드를 고치지 못하게 막는다. 그 사이 고친 것을 늦게 끝난 결과가 덮어쓰지 않게 한다(카드 우선순위와 같다).
 */
export function QuestionMatchPanel({ postingId, initial, choices }: QuestionMatchPanelProps) {
  const [questions, setQuestions] = useState(initial)
  const [matchError, setMatchError] = useState<string | null>(null)
  // 서버가 LLM을 부르지 않고 앞 결과를 다시 줬다. 다음에 무언가 고치면 지운다
  const [reused, setReused] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [matching, setMatching] = useState(false)
  // 카드 바꾸기가 도는 동안. 끝나기 전에 메뉴를 다시 열면 앞 목록으로 보내 방금 더한 카드가 사라진다
  const [changing, setChanging] = useState(false)
  const busy = matching || changing
  const [editing, setEditing] = useState(false)
  // 창을 열 때마다 지금 문항으로 칸을 새로 채운다
  const [editKey, setEditKey] = useState(0)
  const [preview, setPreview] = useState<CardPreview | null>(null)
  const [announced, setAnnounced] = useState("")
  // 저장 전 문항(공고 분석의 것)은 모두 id가 없다
  const unsaved = questions.some((q) => q.id === null)
  const matched = questions.some((q) => q.type !== null || q.cards.length > 0)
  const byId = new Map<string, CardMatch>(questions.flatMap((q) => q.cards).map((c) => [c.cardId, { cardId: c.cardId, title: c.title, experience: c.experience, overlap: [] }]))

  // 다시 고르기 창은 끝날 때까지 열어 두어 "고르는 중"을 보인다
  async function match() {
    setMatchError(null)
    setReused(false)
    setMatching(true)
    setAnnounced("카드를 고르는 중입니다.")
    try {
      const result = await matchQuestions(postingId)
      if (!result.ok) {
        setMatchError(MATCH_ERRORS[result.error])
        return setAnnounced(MATCH_ERRORS[result.error])
      }
      setQuestions(result.questions)
      setReused(result.reused)
      setAnnounced(result.reused ? REUSED : `문항 ${result.questions.length}개의 카드를 골랐습니다.`)
    } finally {
      setMatching(false)
      setConfirming(false)
    }
  }

  // 저장 전 문항은 id가 없어 카드를 둘 수 없으니 지금 문항을 먼저 저장한다(고르기와 같다)
  async function changeCards(index: number, cardIds: string[], message: string) {
    setChanging(true)
    setReused(false)
    // 같은 글을 두 번 알려도 읽히게 먼저 비운다
    setAnnounced("")
    try {
      const saved = unsaved ? await saveQuestions(postingId, questions) : questions
      setQuestions(saved)
      setQuestions(await setQuestionCards(postingId, saved[index].id ?? "", cardIds))
      setAnnounced(message)
    } finally {
      setChanging(false)
    }
  }

  function openEditor() {
    setEditKey((k) => k + 1)
    setEditing(true)
  }

  return (
    <Panel aria-labelledby="mp-questions" className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <PanelHead id="mp-questions" title="자기소개서 문항">
          문항마다 쓸 경험카드입니다. 카드를 누르면 내용을 볼 수 있습니다.
        </PanelHead>
        {questions.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" disabled={busy} onClick={openEditor}>
              문항 고치기
            </Button>
            {/* 처음 고르기와 다시 고르기는 같은 버튼이다. 버튼을 바꿔 끼우면 초점이 body로 떨어진다 */}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy}
              focusableWhenDisabled
              className={busyButton}
              onClick={() => (matched ? setConfirming(true) : void match())}
            >
              {matching ? "고르는 중…" : "LLM으로 카드 고르기"}
            </Button>
          </div>
        )}
      </div>
      {matchError && <IconNote tone="danger">{matchError}</IconNote>}
      {reused && <p className="text-label-sm text-text-secondary">{REUSED}</p>}

      {questions.length === 0 ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-sm text-text-secondary">공고에서 문항을 찾지 못했습니다. 지원서의 문항을 넣어 주세요.</p>
          <Button type="button" variant="outline" size="sm" onClick={openEditor}>
            문항 넣기
          </Button>
        </div>
      ) : (
        <ol className="flex flex-col">
          {questions.map((q, i) => (
            <QuestionRow
              key={q.id ?? i}
              question={q}
              number={i + 1}
              choices={choices}
              locked={matching}
              busy={busy}
              onOpen={(index) =>
                setPreview({
                  label: `${i + 1}번 문항`,
                  overlapTitle: "살릴 칸",
                  overlapTags: false,
                  cards: q.cards.map((c) => ({ cardId: c.cardId, overlap: c.emphasis.map((e) => EMPHASIS_LABELS.get(e) ?? e) })),
                  index,
                })
              }
              onChange={(cardIds, message) => changeCards(i, cardIds, message)}
            />
          ))}
        </ol>
      )}
      {unsaved && <p className="text-label-sm text-text-secondary">공고에서 찾은 문항입니다. 지원서의 문항과 맞는지 확인하세요.</p>}
      <p role="status" className="sr-only">
        {announced}
      </p>

      <QuestionEditDialog
        key={editKey}
        open={editing}
        onOpenChange={setEditing}
        questions={questions}
        unsaved={unsaved}
        onSave={async (list) => {
          setAnnounced("")
          setReused(false)
          setQuestions(await saveQuestions(postingId, list))
          setEditing(false)
          setAnnounced(`문항 ${list.length}개를 저장했습니다.`)
        }}
      />
      <AppDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="카드를 다시 고를까요?"
        actions={
          <>
            <DialogCancel />
            <BusyButton type="button" onClick={match}>
              {matching ? "고르는 중…" : "카드 고르기"}
            </BusyButton>
          </>
        }
      >
        <p className="text-sm">모든 문항의 카드가 LLM이 고른 카드로 바뀝니다. 공고나 문항, 카드가 바뀌었으면 이번 달 LLM 사용액이 듭니다.</p>
      </AppDialog>
      <CardPreviewDialog preview={preview} matches={byId} onIndex={(index) => setPreview((p) => p && { ...p, index })} onClose={() => setPreview(null)} />
    </Panel>
  )
}

interface QuestionRowProps {
  question: Question
  number: number
  choices: CardChoice[]
  /** 고르는 중이면 카드 바꾸기 메뉴를 막는다 */
  locked: boolean
  /**
   * 고르거나 카드를 바꾸는 중이면 메뉴의 항목을 막는다. 카드를 바꾸는 동안은 메뉴 버튼을 끄지 않는다:
   * 항목을 누르면 초점이 메뉴 버튼으로 돌아오는데, 메뉴 버튼(base-ui Menu.Trigger)은 꺼진 채 초점을 받지 못한다
   */
  busy: boolean
  onOpen: (index: number) => void
  onChange: (cardIds: string[], message: string) => Promise<void>
}

/** 문항 하나: 번호, 글, 글자 수와 유형, 카드 바꾸기 메뉴(더하기, 주된·보조 바꾸기), 그 아래 카드 줄(누르면 미리 보기 창, 옆에 빼기). */
function QuestionRow({ question, number, choices, locked, busy, onOpen, onChange }: QuestionRowProps) {
  const { text, limit, type, cards } = question
  const ids = cards.map((c) => c.cardId)
  const addable = addableQuestionCards(choices, question)
  const swappable = cards.length === 2 && maxCards(limit) === 2
  return (
    <li className="flex flex-col gap-3 border-t border-border-subtle py-4 first:border-t-0 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-start gap-x-3 gap-y-2">
        <span aria-hidden="true" className="w-4 flex-none font-bold text-text-secondary">
          {number}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <p className="font-bold">{text}</p>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <span className="text-[13px] font-bold">{formatQuestionLimit(limit)}</span>
            {type && <Chip as="span">{QUESTION_TYPES[type]}</Chip>}
          </div>
        </div>
        {/* 휴대폰은 글 칸이 좁아지지 않게 메뉴를 글 아래로 내린다 */}
        <div className="flex flex-none max-sm:w-full max-sm:justify-end">
          <DropdownMenu>
            <DropdownMenuTrigger disabled={locked} aria-label={`${number}번 문항 카드 바꾸기`} render={<Button variant="outline" size="sm" />}>
              카드 바꾸기
              <ChevronDownIcon data-icon="inline-end" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {swappable && (
                <DropdownMenuItem disabled={busy} onClick={() => void onChange([ids[1], ids[0]], `${number}번 문항의 주된 카드를 ${cards[1].title}(으)로 바꿨습니다.`)}>
                  주된 카드와 보조 카드 바꾸기
                </DropdownMenuItem>
              )}
              {swappable && addable.length > 0 && <DropdownMenuSeparator />}
              {addable.length > 0 && (
                <DropdownMenuGroup>
                  <DropdownMenuLabel>다른 카드 더하기</DropdownMenuLabel>
                  {addable.map((c) => (
                    <DropdownMenuItem key={c.cardId} disabled={busy} onClick={() => void onChange([...ids, c.cardId], `${number}번 문항에 ${c.title}을(를) 더했습니다.`)}>
                      {c.experience ? `${c.experience} · ${c.title}` : c.title}
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuGroup>
              )}
              {!swappable && addable.length === 0 && <DropdownMenuItem disabled>더할 수 있는 카드가 없습니다</DropdownMenuItem>}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {cards.length > 0 ? (
        <ul className="flex flex-col gap-1.5 sm:pl-7">
          {cards.map((c, i) => (
            <li key={c.cardId} className="flex items-center gap-1">
              <button
                type="button"
                aria-haspopup="dialog"
                onClick={() => onOpen(i)}
                className="flex min-h-14 min-w-0 flex-1 cursor-pointer items-center gap-2.5 rounded-control border border-border-subtle bg-surface py-2.5 pr-2.5 pl-3.5 text-left text-foreground"
              >
                <span className="flex min-w-0 flex-auto flex-col gap-1">
                  <span className="text-xs text-text-secondary">
                    {i === 0 ? "주된 카드" : "보조 카드"}
                    {c.experience && ` · ${c.experience}`}
                  </span>
                  <span className="leading-[1.35] font-bold">{c.title}</span>
                  {c.reason && <span className="text-sm text-text-secondary">{c.reason}</span>}
                  {c.emphasis.length > 0 && (
                    <span className="flex flex-wrap items-center gap-1.5 pt-0.5">
                      <span className="text-xs font-semibold text-accent-strong">살릴 칸</span>
                      {c.emphasis.map((e) => (
                        <Chip key={e} as="span">
                          {EMPHASIS_LABELS.get(e) ?? e}
                        </Chip>
                      ))}
                    </span>
                  )}
                </span>
                <ChevronRightIcon aria-hidden="true" className="size-4 flex-none text-text-secondary" />
              </button>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={`${number}번 문항에서 ${c.title} 빼기`}
                disabled={busy}
                onClick={() => void onChange(ids.filter((id) => id !== c.cardId), `${number}번 문항에서 ${c.title}을(를) 뺐습니다.`)}
              >
                <XIcon aria-hidden="true" />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        isResearchType(type) && <p className="text-sm text-text-secondary sm:pl-7">회사와 직무 조사가 중심인 문항이라 카드를 고르지 않았습니다.</p>
      )}
    </li>
  )
}
