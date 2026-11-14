package com.readme.auth;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.auth.WithdrawalResponse.Lead;
import com.readme.auth.WithdrawalResponse.Successor;

/**
 * 탈퇴 화면에 보일 것을 읽는다. 다른 기능의 테이블(카드, 자료, 내 공고, 그룹)을 세기만 한다.
 */
@Component
class WithdrawalQueries {

	private static final String COUNTS = """
			select (select count(*) from experience_cards where user_id = :userId) as cards,
			       (select count(*) from card_sources where user_id = :userId) as sources,
			       (select count(*) from user_postings where user_id = :userId) as postings,
			       (select count(*) from group_members where user_id = :userId) as group_count
			""";

	// 이어받을 사람의 순서(들어온 시각, 같으면 id)는 탈퇴 때 실제로 넘기는 group 의 LeaderHandover 와 같아야 한다
	private static final String LEADS = """
			select g.id, g.name,
			       (select count(*) from group_members o where o.group_id = g.id and o.user_id <> me.user_id) as others,
			       u.id as successor_id, u.display_name as successor_name
			  from group_members me
			  join groups g on g.id = me.group_id
			  left join users u on u.id = (select o.user_id from group_members o
			                                where o.group_id = g.id and o.user_id <> me.user_id
			                                order by o.joined_at, o.user_id limit 1)
			 where me.user_id = :userId and me.role = 'LEADER'
			 order by me.joined_at, g.id
			""";

	private final JdbcClient jdbc;

	WithdrawalQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	WithdrawalResponse find(long userId) {
		List<Lead> leads = this.jdbc.sql(LEADS)
			.param("userId", userId)
			.query((rs, rowNum) -> {
				long successorId = rs.getLong("successor_id");
				Successor successor = rs.wasNull() ? null : new Successor(successorId, rs.getString("successor_name"));
				return new Lead(rs.getLong("id"), rs.getString("name"), rs.getInt("others"), successor);
			})
			.list();
		return this.jdbc.sql(COUNTS)
			.param("userId", userId)
			.query((rs, rowNum) -> new WithdrawalResponse(rs.getInt("cards"), rs.getInt("sources"), rs.getInt("postings"),
					rs.getInt("group_count"), leads))
			.single();
	}

}
