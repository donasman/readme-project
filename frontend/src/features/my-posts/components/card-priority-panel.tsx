import { closestCenter, DndContext, KeyboardSensor, PointerSensor, useSensor, useSensors, type Announcements, type DragEndEvent } from "@dnd-kit/core"
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { useState } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { BusyButton } from "@/shared/components/busy-button"
import { PanelHead } from "@/shared/components/heading"
import { IconNote } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { ArrowDownIcon, ArrowUpIcon, ChevronDownIcon, GripVerticalIcon, PlusIcon, XIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { draftCardPriority, saveCardPriority } from "../api"
import { addableCards, MAX_PRIORITY, moveBy } from "../card-priority"
import type { CardChoice, DraftResult, PriorityCard } from "../types"

const DRAFT_ERRORS: Record<Extract<DraftResult, { ok: false }>["error"], string> = {
  NO_CARDS: "경험카드가 없어 초안을 만들 수 없습니다. 카드를 먼저 만들어 주세요.",
  NO_POSTING_CONTENT: "공고 내용이 없어 초안을 만들 수 없습니다.",
  LLM_LIMIT_REACHED: "이번 달 LLM 사용 한도에 닿아 초안을 만들 수 없습니다. 순서는 직접 정할 수 있습니다.",
}

interface CardPriorityPanelProps {
  postingId: string
  initial: PriorityCard[]
  /** 더할 수 있는 내 카드 전부 */
  choices: CardChoice[]
}

/**
 * 경험카드 우선순위(카드 우선순위 문서): 이 공고에 먼저 쓸 카드 5장까지. 초안은 LLM이 짜고(누를 때만), 그 뒤에는 끌거나 위·아래 버튼으로
 * 순서를 바꾸고, 빼고, 더한다. 바꿀 때마다 목록 전체를 저장한다. 시안이 없다.
 */
export function CardPriorityPanel({ postingId, initial, choices }: CardPriorityPanelProps) {
  const [cards, setCards] = useState(initial)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [announced, setAnnounced] = useState("")
  const sensors = useSensors(useSensor(PointerSensor), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))
  const addable = addableCards(choices, cards)

  // 화면은 먼저 바꾸고, 저장에 실패하면 되돌린 뒤 오류를 던져 화면 아래 알림이 보이게 한다
  async function save(next: PriorityCard[], message: string) {
    const before = cards
    setCards(next)
    try {
      setCards(await saveCardPriority(postingId, next.map((c) => c.cardId)))
      setAnnounced(message)
    } catch (error) {
      setCards(before)
      throw error
    }
  }

  async function draft() {
    setConfirming(false)
    setDraftError(null)
    const result = await draftCardPriority(postingId)
    if (!result.ok) return setDraftError(DRAFT_ERRORS[result.error])
    setCards(result.cards)
    setAnnounced(result.cards.length > 0 ? `초안으로 카드 ${result.cards.length}장을 골랐습니다.` : "이 공고에 맞는 카드를 고르지 못했습니다.")
  }

  function onDragEnd({ active, over }: DragEndEvent) {
    if (!over || active.id === over.id) return
    const from = cards.findIndex((c) => c.cardId === active.id)
    const to = cards.findIndex((c) => c.cardId === over.id)
    // 끌기의 결과는 dnd-kit의 알림 칸이 읽는다(announcements)
    void save(moveBy(cards, from, to - from), "")
  }

  // dnd-kit이 끌 때 화면 읽기에 알리는 글. 기본은 영어다
  const titleOf = (id: string | number) => cards.find((c) => c.cardId === id)?.title ?? ""
  const placeOf = (id: string | number) => cards.findIndex((c) => c.cardId === id) + 1
  const announcements: Announcements = {
    onDragStart: ({ active }) => `${titleOf(active.id)}을(를) 집었습니다. ${placeOf(active.id)}번째 자리입니다.`,
    onDragOver: ({ active, over }) => (over ? `${titleOf(active.id)}을(를) ${placeOf(over.id)}번째 자리로 옮기는 중입니다.` : undefined),
    onDragEnd: ({ active, over }) => (over ? `${titleOf(active.id)}을(를) ${placeOf(over.id)}번째로 옮겼습니다.` : `${titleOf(active.id)}을(를) 놓았습니다.`),
    onDragCancel: ({ active }) => `${titleOf(active.id)} 옮기기를 취소했습니다.`,
  }

  return (
    <Panel aria-labelledby="mp-priority" className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <PanelHead id="mp-priority" title="경험카드 우선순위">
          이 공고에 먼저 쓸 카드입니다. 끌거나 화살표로 순서를 바꿉니다.
        </PanelHead>
        {cards.length > 0 ? (
          <Button type="button" variant="outline" size="sm" onClick={() => setConfirming(true)}>
            LLM으로 초안 다시 만들기
          </Button>
        ) : (
          <BusyButton type="button" variant="outline" size="sm" onClick={draft}>
            LLM으로 초안 만들기
          </BusyButton>
        )}
      </div>
      {draftError && <IconNote tone="danger">{draftError}</IconNote>}

      {cards.length === 0 ? (
        <p className="text-sm text-text-secondary">아직 고른 카드가 없습니다. LLM으로 초안을 만들거나 카드를 직접 더해 보세요.</p>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={onDragEnd}
          accessibility={{ announcements, screenReaderInstructions: { draggable: "스페이스 키로 집고 위·아래 화살표로 옮긴 뒤 스페이스 키로 놓습니다. Esc 키로 취소합니다." } }}
        >
          <SortableContext items={cards.map((c) => c.cardId)} strategy={verticalListSortingStrategy}>
            <ol className="flex flex-col gap-2">
              {cards.map((card, index) => (
                <PriorityRow
                  key={card.cardId}
                  card={card}
                  index={index}
                  last={index === cards.length - 1}
                  onMove={(by) => void save(moveBy(cards, index, by), `${card.title}을(를) ${index + by + 1}번째로 옮겼습니다.`)}
                  onRemove={() => void save(cards.filter((c) => c.cardId !== card.cardId), `${card.title}을(를) 뺐습니다.`)}
                />
              ))}
            </ol>
          </SortableContext>
        </DndContext>
      )}

      {addable.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="outline" size="sm" className="self-start" />}>
            <PlusIcon data-icon="inline-start" aria-hidden="true" />
            카드 더하기
            <ChevronDownIcon data-icon="inline-end" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {addable.map((c) => (
              <DropdownMenuItem key={c.cardId} onClick={() => void save([...cards, { ...c, reason: null }], `${c.title}을(를) 더했습니다.`)}>
                {c.experience ? `${c.experience} · ${c.title}` : c.title}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      {cards.length >= MAX_PRIORITY && <p className="text-label-sm text-text-secondary">{MAX_PRIORITY}장까지 둘 수 있습니다. 다른 카드를 넣으려면 하나를 빼 주세요.</p>}
      <p role="status" className="sr-only">
        {announced}
      </p>

      <AppDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="초안으로 바꿀까요?"
        actions={
          <>
            <DialogCancel />
            <BusyButton type="button" onClick={draft}>
              초안 만들기
            </BusyButton>
          </>
        }
      >
        <p className="text-sm">지금 순서가 LLM이 새로 고른 카드로 바뀝니다. 이번 달 LLM 사용액이 듭니다.</p>
      </AppDialog>
    </Panel>
  )
}

/** 끝에서 꺼진 위·아래 버튼도 초점을 잃지 않게 aria-disabled로 두므로(focusableWhenDisabled) 흐림을 여기서 준다 */
const stepButton = "aria-disabled:pointer-events-none aria-disabled:opacity-35"

interface PriorityRowProps {
  card: PriorityCard
  index: number
  last: boolean
  onMove: (by: -1 | 1) => void
  onRemove: () => void
}

/** 우선순위 한 줄: 끄는 손잡이, 순번, 카드 제목(카드 상세로), 고른 이유, 위·아래·빼기 버튼(휴대폰은 아래 줄). */
function PriorityRow({ card, index, last, onMove, onRemove }: PriorityRowProps) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: card.cardId })
  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={joinClasses(
        "flex flex-wrap items-start gap-x-3 rounded-control border border-border-subtle bg-surface-raised py-3 pr-2 pl-1",
        isDragging && "relative z-10 shadow-raised"
      )}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        {...attributes}
        {...listeners}
        aria-label={`${card.title} 끌어서 옮기기`}
        className="flex size-8 flex-none cursor-grab touch-none items-center justify-center rounded-control text-text-secondary hover:bg-muted active:cursor-grabbing"
      >
        <GripVerticalIcon aria-hidden="true" className="size-4" />
      </button>
      <span aria-hidden="true" className="mt-1 w-4 flex-none text-sm font-bold text-text-secondary">
        {index + 1}
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1 pt-0.5">
        <Link to={paths.card(card.cardId)} className="font-bold text-foreground hover:underline">
          {card.title}
        </Link>
        {card.experience && <span className="text-label-sm text-text-secondary">{card.experience}</span>}
        {card.reason && <p className="text-sm text-text-secondary">{card.reason}</p>}
      </div>
      {/* 휴대폰은 글 칸이 좁아지지 않게 버튼 줄을 글 아래로 내린다 */}
      <div className="flex flex-none items-center max-sm:w-full max-sm:justify-end">
        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${card.title} 위로`} disabled={index === 0} focusableWhenDisabled className={stepButton} onClick={() => onMove(-1)}>
          <ArrowUpIcon aria-hidden="true" />
        </Button>
        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${card.title} 아래로`} disabled={last} focusableWhenDisabled className={stepButton} onClick={() => onMove(1)}>
          <ArrowDownIcon aria-hidden="true" />
        </Button>
        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${card.title} 빼기`} onClick={onRemove}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>
    </li>
  )
}
