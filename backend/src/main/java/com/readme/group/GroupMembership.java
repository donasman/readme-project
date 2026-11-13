package com.readme.group;

/**
 * 한 사람의 한 그룹 가입 정보.
 *
 * @param leader     그룹장인지
 * @param disclosure 이 사람이 이 그룹에 정한 공개 범위
 */
public record GroupMembership(long groupId, long userId, boolean leader, Disclosure disclosure) {
}
