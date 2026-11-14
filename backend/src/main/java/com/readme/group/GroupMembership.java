package com.readme.group;

/**
 * 멤버 확인({@link GroupAccess#requireMember})의 결과. 그룹장만 할 수 있는 일(내리기, 남의 댓글 지우기, 나가기 막기)에 쓴다.
 *
 * @param leader 그룹장인지
 */
public record GroupMembership(boolean leader) {
}
