import { useState, type FormEvent } from "react"
import { useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { paths } from "@/shared/lib/paths"

import { createGroup, createInvite } from "../api"
import type { MembersPageState } from "../pages/group-members-page"

const NAME_LIMIT = 50

/**
 * 그룹 만들기 창(시안 7차 "내 그룹 · 그룹 만들기 창"). 만들면 초대 링크를 하나 만들어 들고 멤버 화면으로 간다
 * (시안의 "만들기"가 "멤버 · 초대 링크를 만든 뒤"로 이어진다).
 */
export function CreateGroupDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const navigate = useNavigate()
  const [name, setName] = useState("")
  const [mark, setMark] = useState("")
  const [missing, setMissing] = useState(false)
  const [saving, setSaving] = useState(false)

  // 닫으면 쓴 것을 비운다(다시 열면 빈 칸)
  function changeOpen(next: boolean) {
    if (!next) {
      setName("")
      setMark("")
      setMissing(false)
    }
    onOpenChange(next)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return setMissing(true)
    setSaving(true)
    try {
      const group = await createGroup({ name, mark })
      const invite = await createInvite(group.id)
      const state: MembersPageState = { invite }
      navigate(paths.groupMembers(group.id), { state })
    } finally {
      setSaving(false)
    }
  }

  return (
    <AppDialog
      open={open}
      onOpenChange={changeOpen}
      placement="top"
      title="그룹 만들기"
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="create-group" disabled={saving}>
            만들기
          </Button>
        </>
      }
    >
      <form id="create-group" onSubmit={submit} className="contents">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="gc-name" className="text-sm font-medium">
            그룹 이름
          </label>
          <Input
            id="gc-name"
            value={name}
            maxLength={NAME_LIMIT}
            placeholder="백엔드 스터디"
            aria-invalid={missing || undefined}
            aria-describedby="gc-name-note"
            onChange={(e) => {
              setName(e.target.value)
              setMissing(false)
            }}
            className="md:text-base aria-invalid:ring-0"
          />
          {missing ? (
            <p id="gc-name-note" className="text-label-sm font-medium text-danger">
              그룹 이름을 넣어 주세요.
            </p>
          ) : (
            <p id="gc-name-note" className="text-label-sm text-text-secondary">
              {NAME_LIMIT}자까지 쓸 수 있습니다.
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="gc-mark" className="text-sm font-medium">
            표시 글자
          </label>
          <Input
            id="gc-mark"
            value={mark}
            maxLength={2}
            placeholder={name.trim().slice(0, 1) || "백"}
            aria-describedby="gc-mark-note"
            onChange={(e) => setMark(e.target.value)}
            className="max-w-30 md:text-base"
          />
          <p id="gc-mark-note" className="text-label-sm text-text-secondary">
            헤더와 목록의 동그라미에 들어가는 한두 글자입니다. 비우면 이름 첫 글자를 씁니다.
          </p>
        </div>
      </form>
    </AppDialog>
  )
}
