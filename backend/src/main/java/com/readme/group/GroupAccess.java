package com.readme.group;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;

/**
 * 그룹 API 의 첫 검사: 이 사람이 이 그룹의 멤버인가(설계 5.1). 그룹 공고처럼 그룹 안의 것을 다루는 기능도 이것을 부른다.
 */
@Component
public class GroupAccess {

	private final JdbcClient jdbc;

	GroupAccess(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 멤버가 아니거나 그룹이 없으면 404 GROUP_NOT_FOUND. */
	public GroupMembership requireMember(long groupId, long userId) {
		return this.jdbc.sql("select role, disclosure from group_members where group_id = :groupId and user_id = :userId")
			.param("groupId", groupId)
			.param("userId", userId)
			.query((rs, rowNum) -> new GroupMembership(groupId, userId, "LEADER".equals(rs.getString("role")),
					Disclosure.valueOf(rs.getString("disclosure"))))
			.optional()
			.orElseThrow(() -> new ApiException(GroupErrorCode.GROUP_NOT_FOUND));
	}

}
