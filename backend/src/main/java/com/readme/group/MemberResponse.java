package com.readme.group;

/**
 * 응답에 들어가는 사람 한 명. 프런트의 Member 모양이다.
 *
 * @param name 표시 이름(users.display_name)
 */
public record MemberResponse(long id, String name) {
}
