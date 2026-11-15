import { useState } from "react"
import { useLocation, useNavigate, useRevalidator } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { setMyDisclosure } from "@/domain/group/api"
import { DisclosureOptions } from "@/domain/group/disclosure-options"
import type { Disclosure } from "@/domain/group/types"
import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { Avatar } from "@/shared/components/avatar"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, pageHead, sideColumn } from "@/shared/components/page-layout"
import { Panel, PanelRow } from "@/shared/components/panel"
import { CheckIcon, CopyIcon, LinkIcon } from "@/shared/icons"
import { copyText } from "@/shared/lib/clipboard"
import { paths } from "@/shared/lib/paths"
import { firstChar, joinClasses } from "@/shared/lib/utils"

import { createInvite, leaveGroup } from "../api"
import type { CreatedInvite } from "../types"
import { RoleChip } from "../components/role-chip"

/** 그룹을 막 만들고 왔을 때 넘기는 값: 만들면서 만든 초대 링크. */
export interface MembersPageState {
  invite?: CreatedInvite
}

/** 멤버·초대 관리(시안 7차 "멤버"): 멤버 목록, 내 공개 범위, 초대 링크, 그룹 나가기. */
export function GroupMembersPage() {
  const { group } = useGroup()
  const viewer = useViewer()
  const location = useLocation()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  const [invite, setInvite] = useState((location.state as MembersPageState | null)?.invite ?? null)
  const [disclosure, setDisclosure] = useState<Disclosure>(group.myDisclosure)
  const [leaving, setLeaving] = useState(false)
  const isLeader = group.leaderId === viewer.id
  const members = [...group.members].sort((a, b) => a.joinedAt.localeCompare(b.joinedAt))

  async function changeDisclosure(next: Disclosure) {
    setDisclosure(next)
    await setMyDisclosure(group.id, next)
    // 그룹의 값(공고 상세의 "내 공개 범위는 …")도 새로 읽는다
    void revalidator.revalidate()
  }

  async function leave() {
    await leaveGroup(group.id)
    navigate(paths.groups())
  }

  return (
    <Page className="gap-6 pt-10">
      <div className={pageHead}>
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-sm text-text-secondary">
            {group.name} · 멤버 {group.members.length}명
          </p>
          <Heading level={1} size="text-[clamp(2.5rem,10cqw,var(--text-5xl))]">
            멤버
          </Heading>
        </div>
        <Button type="button" className="flex-none" onClick={async () => setInvite(await createInvite(group.id))}>
          <LinkIcon data-icon="inline-start" aria-hidden="true" />
          초대 링크 만들기
        </Button>
      </div>

      <div className={columns}>
        <section aria-label="멤버 목록" className={joinClasses(mainColumn, "gap-3")}>
          <Panel as="ul" className="flex flex-col">
            {members.map((member, i) => (
              <PanelRow
                key={member.id}
                first={i === 0} className="gap-x-3 py-4"
              >
                <Avatar size={40}>{firstChar(member.name)}</Avatar>
                <div className="flex min-w-0 flex-[1_1_160px] flex-wrap items-center gap-x-2 gap-y-1.5">
                  <span className="font-bold">{member.name}</span>
                  {member.id === viewer.id && <RoleChip role="me" />}
                  {member.id === group.leaderId && <RoleChip role="leader" />}
                </div>
                <span className="text-label-sm whitespace-nowrap text-text-secondary">{member.joinedAt} 들어옴</span>
              </PanelRow>
            ))}
          </Panel>
          <p className="text-label-sm text-text-secondary">
            멤버 누구나 공고를 올리고 초대 링크를 만들 수 있습니다. 그룹에서 공고를 내리는 것은 올린 사람과 그룹장만 합니다.
          </p>
        </section>

        <aside className={joinClasses(sideColumn(320), "gap-6")}>
          <Panel aria-labelledby="members-disclosure" className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="members-disclosure" className={sectionTitle}>
                내 공개 범위
              </h2>
              <p className="text-sm text-text-secondary">이 그룹 멤버에게 내 지원 상태를 어디까지 보일지 정합니다.</p>
            </div>
            <DisclosureOptions value={disclosure} onChange={(next) => void changeDisclosure(next)} labelledBy="members-disclosure" />
            <p className="text-label-sm text-text-secondary">관심을 켠 공고에만 적용됩니다. 그룹마다 따로 정합니다.</p>
          </Panel>

          <Panel aria-labelledby="members-invite" className="flex flex-col gap-4 p-6">
            <h2 id="members-invite" className={sectionTitle}>
              초대 링크
            </h2>
            {invite ? <InviteLink key={invite.link} {...invite} /> : <p className="text-sm text-text-secondary">지금 쓸 수 있는 링크가 없습니다. 링크를 만들어 들어올 사람에게 보내세요.</p>}
          </Panel>

          <Panel aria-labelledby="members-leave" className="flex flex-col gap-4 p-6">
            <h2 id="members-leave" className={sectionTitle}>
              그룹 나가기
            </h2>
            {isLeader ? (
              <p className="text-sm text-text-secondary">그룹장은 나갈 수 없습니다.</p>
            ) : (
              <>
                <p className="text-sm text-text-secondary">나가면 이 그룹에서 켠 관심이 지워집니다. 내 공고 목록과 내가 쓴 댓글은 남습니다.</p>
                <Button type="button" variant="danger" className="self-start" onClick={() => setLeaving(true)}>
                  그룹 나가기
                </Button>
              </>
            )}
          </Panel>
        </aside>
      </div>

      <AppDialog
        open={leaving}
        onOpenChange={setLeaving}
        title="그룹에서 나갈까요?"
        subtitle={group.name}
        actions={
          <>
            <DialogCancel />
            <Button type="button" variant="danger" onClick={() => void leave()}>
              나가기
            </Button>
          </>
        }
      >
        <p className="text-sm">이 그룹에서 켠 관심이 지워집니다. 내 공고 목록과 내가 쓴 댓글은 남습니다. 다시 들어오려면 초대 링크가 있어야 합니다.</p>
      </AppDialog>
    </Page>
  )
}

/** 만든 초대 링크: 읽기만 하는 칸, 복사 버튼, 쓸 수 있는 날. 링크는 만들 때만 보여 줄 수 있다. */
function InviteLink({ link: url, expiresAt }: CreatedInvite) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    setCopied(await copyText(url))
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <label htmlFor="members-link" className="sr-only">
          초대 링크
        </label>
        <Input id="members-link" readOnly value={url} onFocus={(e) => e.target.select()} className="min-w-0 flex-1 text-sm md:text-sm" />
        <Button type="button" variant="outline" className="flex-none" onClick={() => void copy()}>
          {copied ? <CheckIcon data-icon="inline-start" aria-hidden="true" /> : <CopyIcon data-icon="inline-start" aria-hidden="true" />}
          {copied ? "복사함" : "복사"}
        </Button>
      </div>
      <p className="text-label-sm text-text-secondary">
        {expiresAt}까지 쓸 수 있습니다(7일). 링크를 가진 사람은 누구나 들어올 수 있습니다.
      </p>
      <p role="status" className="sr-only">
        {copied ? "초대 링크를 복사했습니다." : ""}
      </p>
    </div>
  )
}
