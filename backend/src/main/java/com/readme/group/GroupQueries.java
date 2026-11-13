package com.readme.group;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class GroupQueries {

	// 열린 공고: 내리지 않았고, 마감일이 오늘 이후다. 마감일 당일은 진행 중이고, 상시·확인 필요는 늘 진행 중이다(설계 5.4).
	private static final String MY_GROUPS = """
			select g.id, g.name, g.mark, me.disclosure,
			       (select l.user_id from group_members l where l.group_id = g.id and l.role = 'LEADER') as leader_id,
			       (select count(*)
			          from group_posts gp
			          join postings p on p.id = gp.posting_id
			         where gp.group_id = g.id
			           and gp.taken_down_at is null
			           and (p.deadline_kind <> 'DATE' or p.deadline_date >= :today)) as open_post_count
			  from group_members me
			  join groups g on g.id = me.group_id
			 where me.user_id = :userId
			 order by me.joined_at, g.id
			""";

	private static final String MEMBERS_OF_MY_GROUPS = """
			select m.group_id, u.id, u.display_name
			  from group_members m
			  join users u on u.id = m.user_id
			 where m.group_id in (select group_id from group_members where user_id = :userId)
			 order by m.group_id, m.joined_at, u.id
			""";

	private final JdbcClient jdbc;

	GroupQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<GroupResponse> findMyGroups(long userId, LocalDate today) {
		Map<Long, List<MemberResponse>> members = this.jdbc.sql(MEMBERS_OF_MY_GROUPS)
			.param("userId", userId)
			.query((rs, rowNum) -> new MemberRow(rs.getLong("group_id"),
					new MemberResponse(rs.getLong("id"), rs.getString("display_name"))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(MemberRow::groupId, LinkedHashMap::new,
					Collectors.mapping(MemberRow::member, Collectors.toList())));
		return this.jdbc.sql(MY_GROUPS)
			.param("userId", userId)
			.param("today", today)
			.query((rs, rowNum) -> {
				long groupId = rs.getLong("id");
				return new GroupResponse(groupId, rs.getString("name"), rs.getString("mark"),
						members.getOrDefault(groupId, List.of()), rs.getObject("leader_id", Long.class),
						rs.getInt("open_post_count"), Disclosure.valueOf(rs.getString("disclosure")));
			})
			.list();
	}

	private record MemberRow(long groupId, MemberResponse member) {
	}

}
