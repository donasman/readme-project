/**
 * 그룹. 내 그룹 목록, 멤버 확인, 공개 범위, 그룹 만들기·나가기·초대.
 *
 * <p>테이블: groups, group_members, group_invites. API: /api/groups, /api/group-invites.
 * <p>다른 기능은 {@link com.readme.group.GroupAccess} 로 "이 사람이 이 그룹의 멤버인가"를 확인한다.
 * 근거: docs/리드미_설계_그룹_공고.md.
 */
package com.readme.group;
