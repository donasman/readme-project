import { useEffect, useState } from "react"
import { useLoaderData, useLocation, useNavigate, useRevalidator, type LoaderFunctionArgs } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { setMyDisclosure } from "@/domain/group/api"
import { DisclosureOptions } from "@/domain/group/disclosure-options"
import type { Disclosure } from "@/domain/group/types"
import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { Avatar } from "@/shared/components/avatar"
import { sectionTitle, subLabel } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel, PanelRow } from "@/shared/components/panel"
import { CheckIcon, CopyIcon } from "@/shared/icons"
import { copyText } from "@/shared/lib/clipboard"
import { subjectParticle } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"
import { docTitle, firstChar, joinClasses } from "@/shared/lib/utils"

import { getInvites, leaveGroup, revokeInvite } from "../api"
import type { CreatedInvite, LiveInvite } from "../types"
import { RoleChip } from "../components/role-chip"

/** 그룹을 막 만들고 왔을 때 넘기는 값: 만들면서 만든 초대 링크. */
export interface MembersPageState {
  invite?: CreatedInvite
}

/** 쓸 수 있는 초대 링크들. 그룹 값은 그룹 라우트가 읽는다. */
export function membersLoader({ params }: LoaderFunctionArgs) {
  return getInvites(params.groupId ?? "")
}

/** 멤버·초대 관리(시안 7차 "멤버", 10차 초대 목록·취소): 멤버 목록, 내 공개 범위, 초대 링크와 쓸 수 있는 링크, 그룹 나가기. */
export function GroupMembersPage() {
  const invites = useLoaderData<typeof membersLoader>()
  const { group } = useGroup()
  const viewer = useViewer()
  const location = useLocation()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  // 그룹을 만들고 왔을 때와 그룹 머리 영역에서 초대 링크를 만들었을 때 들고 온 링크. 이 화면에 있을 때 또 만들어 와도
  // 새 링크를 잡도록 도착(location.key)마다 본다. 기록의 상태는 지운다(새로 고침과 뒤로 가기에 남으면 취소한 주소가 다시 보인다)
  const arrived = (location.state as MembersPageState | null)?.invite
  const [invite, setInvite] = useState(arrived ?? null)
  const [arrivalKey, setArrivalKey] = useState(location.key)
  if (arrived && location.key !== arrivalKey) {
    setArrivalKey(location.key)
    setInvite(arrived)
  }
  useEffect(() => {
    if (location.state) void navigate(".", { replace: true, state: null })
  }, [location.state, navigate])
  const [disclosure, setDisclosure] = useState<Disclosure>(group.myDisclosure)
  const [leaving, setLeaving] = useState(false)
  const [cancelling, setCancelling] = useState<LiveInvite | null>(null)
  const [cancelled, setCancelled] = useState(false)
  const isLeader = group.leaderId === viewer.id
  const mine = (i: LiveInvite) => i.createdBy?.id === viewer.id
  const members = [...group.members].sort((a, b) => a.joinedAt.localeCompare(b.joinedAt))

  async function changeDisclosure(next: Disclosure) {
    setDisclosure(next)
    await setMyDisclosure(group.id, next)
    // 그룹의 값(공고 상세의 "내 공개 범위는 …")도 새로 읽는다
    void revalidator.revalidate()
  }

  function askCancel(target: LiveInvite) {
    // 앞 취소의 알림을 비워 두어야 같은 글이 다시 들어갈 때 다시 읽힌다
    setCancelled(false)
    setCancelling(target)
  }

  async function cancelInvite(target: LiveInvite) {
    await revokeInvite(group.id, target.id)
    // shortcut: 만들기 응답에 링크 id가 없어 내가 만든 가장 새 링크를 위 칸의 주소로 본다, 응답에 id가 생기면 그것으로 맞춘다
    if (target.id === invites.find(mine)?.id) setInvite(null)
    setCancelling(null)
    setCancelled(true)
    void revalidator.revalidate()
  }

  async function leave() {
    await leaveGroup(group.id)
    navigate(paths.groups())
  }

  return (
    <Page className="gap-6 pt-6">
      <title>{docTitle("멤버", group.name)}</title>
      {/* 그룹 이름, 탭, 초대 링크 만들기는 위의 그룹 머리 영역에 있다. 제목은 화면 읽기에만 둔다 */}
      <h1 className="sr-only">멤버</h1>

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
            {!invite && invites.length === 0 && <p className="text-sm text-text-secondary">지금 쓸 수 있는 링크가 없습니다. 링크를 만들어 들어올 사람에게 보내세요.</p>}
            {invite && <InviteLink key={invite.link} {...invite} />}
            {invites.length > 0 && (
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>쓸 수 있는 링크 {invites.length}개</h3>
                <ul className="flex flex-col">
                  {invites.map((i) => (
                    <li key={i.id} className="flex items-center gap-3 border-t border-border-subtle py-2.5">
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                          <span className="text-sm font-bold">{i.createdBy?.name ?? "탈퇴한 멤버"}</span>
                          {mine(i) && <RoleChip role="me" />}
                        </div>
                        <div className="flex flex-wrap gap-x-3 text-label-sm text-text-secondary">
                          <span className="whitespace-nowrap">{i.createdAt} 만듦</span>
                          <span className="whitespace-nowrap">{i.expiresAt}까지</span>
                        </div>
                      </div>
                      {(isLeader || mine(i)) && (
                        <Button type="button" variant="outline" size="sm" className="h-(--control-height-md) flex-none" aria-label={`${inviteMaker(i, mine(i))} ${i.createdAt}에 만든 링크 취소`} onClick={() => askCancel(i)}>
                          취소
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
                <p className="text-label-sm text-text-secondary">
                  {`${isLeader ? "그룹장은 모든 링크를 취소할 수 있습니다." : "내가 만든 링크만 취소할 수 있습니다. 그룹장은 모든 링크를 취소합니다."} 링크 주소는 만든 직후에만 보여 목록에는 나오지 않습니다.`}
                </p>
              </div>
            )}
            {/* 취소한 줄과 창을 연 버튼이 함께 사라져, 결과를 말로 알린다 */}
            <p role="status" className="sr-only">
              {cancelled ? "초대 링크를 취소했습니다." : ""}
            </p>
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

      <AppDialog
        open={cancelling !== null}
        onOpenChange={(open) => !open && setCancelling(null)}
        title="초대 링크를 취소할까요?"
        subtitle={cancelling && `${inviteMaker(cancelling, mine(cancelling))} ${cancelling.createdAt}에 만든 링크 · ${cancelling.expiresAt}까지`}
        actions={
          <>
            {/* 링크 "취소"와 겹치지 않게 닫기로 쓴다(시안 10차) */}
            <DialogCancel>닫기</DialogCancel>
            <Button type="button" variant="danger" onClick={() => cancelling && void cancelInvite(cancelling)}>
              링크 취소
            </Button>
          </>
        }
      >
        <p className="text-sm">취소하면 이 링크로는 더 들어올 수 없고, 되살릴 수 없습니다. 이미 들어온 멤버와 다른 초대 링크는 그대로입니다.</p>
      </AppDialog>
    </Page>
  )
}

/** 취소 버튼과 창의 "누가": 내가, 가람이, 탈퇴한 멤버가. 같은 분에 만든 링크끼리 버튼 이름이 겹치지 않게 넣는다. */
function inviteMaker(invite: LiveInvite, mine: boolean): string {
  if (mine) return "내가"
  const name = invite.createdBy?.name ?? "탈퇴한 멤버"
  return name + subjectParticle(name)
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
        {expiresAt}까지 쓸 수 있습니다(7일). 링크를 가진 사람은 누구나 들어올 수 있습니다. 주소는 지금만 보이니 복사해 두세요.
      </p>
      <p role="status" className="sr-only">
        {copied ? "초대 링크를 복사했습니다." : ""}
      </p>
    </div>
  )
}
