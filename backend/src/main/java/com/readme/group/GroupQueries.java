package com.readme.group;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.config.TimeConfig;
import com.readme.group.GroupRequests.InvitePreviewResponse;
import com.readme.group.GroupRequests.LiveInviteResponse;
import com.readme.group.GroupResponse.GroupMemberResponse;
import com.readme.group.GroupResponse.NearestDeadline;

@Component
class GroupQueries {

	// 열린 공고: 내리지 않았고, 마감일이 오늘 이후다. 마감일 당일은 진행 중이고, 상시·확인 필요는 늘 진행 중이다(설계 5.4).
	// 가장 가까운 마감은 열린 공고 가운데 날짜가 있는 것만 본다(상시·확인 필요는 deadline_date 가 null 이라 빠진다).
	// 같은 날이면 공고 목록의 순서(올린 시각의 역순)에서 앞의 것이다.
	private static final String MY_GROUPS = """
			select g.id, g.name, g.mark, me.disclosure,
			       (select l.user_id from group_members l where l.group_id = g.id and l.role = 'LEADER') as leader_id,
			       (select count(*)
			          from group_posts gp
			          join postings p on p.id = gp.posting_id
			         where gp.group_id = g.id
			           and gp.taken_down_at is null
			           and (p.deadline_kind <> 'DATE' or p.deadline_date >= :today)) as open_post_count,
			       nd.deadline_date as nearest_date, nd.company as nearest_company, nd.title as nearest_title
			  from group_members me
			  join groups g on g.id = me.group_id
			  left join lateral (
			        select p.deadline_date, p.company, p.title
			          from group_posts gp
			          join postings p on p.id = gp.posting_id
			         where gp.group_id = g.id
			           and gp.taken_down_at is null
			           and p.deadline_date >= :today
			         order by p.deadline_date, gp.posted_at desc, p.id desc
			         limit 1) nd on true
			 where me.user_id = :userId
			""";

	private static final String MEMBERS = """
			select m.group_id, u.id, u.display_name, m.joined_at
			  from group_members m
			  join users u on u.id = m.user_id
			 where m.group_id in (select group_id from group_members where user_id = :userId)
			""";

	private static final String MEMBERS_ORDER = " order by m.group_id, m.joined_at, u.id";

	// 쓸 수 있는 초대: 취소되지 않았고 만료 시각 전이다(GroupInvite.usableAt 과 같다)
	private static final String LIVE = "(i.revoked_at is null and i.expires_at > :now)";

	private static final String INVITE_PREVIEW = """
			select g.name, g.mark, u.display_name as inviter_name,
			       (select count(*) from group_members m where m.group_id = g.id) as member_count,
			       %s as usable
			  from group_invites i
			  join groups g on g.id = i.group_id
			  left join users u on u.id = i.created_by
			 where i.token_hash = :tokenHash
			""".formatted(LIVE);

	private static final String LIVE_INVITES = """
			select i.id, i.created_at, i.expires_at, u.id as creator_id, u.display_name as creator_name
			  from group_invites i
			  left join users u on u.id = i.created_by
			 where i.group_id = :groupId
			   and %s
			 order by i.created_at desc, i.id desc
			""".formatted(LIVE);

	private final JdbcClient jdbc;

	GroupQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 내가 속한 그룹들. 들어간 순서다. */
	List<GroupResponse> findMyGroups(long userId, LocalDate today) {
		Map<Long, List<GroupMemberResponse>> members = members(MEMBERS + MEMBERS_ORDER, userId, null);
		return this.jdbc.sql(MY_GROUPS + " order by me.joined_at, g.id")
			.param("userId", userId)
			.param("today", today)
			.query((rs, rowNum) -> group(rs, members))
			.list();
	}

	/** 내가 속한 그룹 하나. 멤버가 아니면 비어 있다. */
	Optional<GroupResponse> findMyGroup(long userId, long groupId, LocalDate today) {
		Map<Long, List<GroupMemberResponse>> members = members(MEMBERS + " and m.group_id = :groupId" + MEMBERS_ORDER,
				userId, groupId);
		return this.jdbc.sql(MY_GROUPS + " and g.id = :groupId")
			.param("userId", userId)
			.param("groupId", groupId)
			.param("today", today)
			.query((rs, rowNum) -> group(rs, members))
			.optional();
	}

	/** 토큰 해시로 찾은 초대의 미리 보기와, 지금 쓸 수 있는지. 그런 초대가 없으면 비어 있다. */
	Optional<InvitePreview> findInvitePreview(String tokenHash, Instant now) {
		return this.jdbc.sql(INVITE_PREVIEW)
			.param("tokenHash", tokenHash)
			.param("now", Timestamp.from(now))
			.query((rs, rowNum) -> new InvitePreview(rs.getBoolean("usable"),
					new InvitePreviewResponse(rs.getString("name"), rs.getString("mark"), rs.getString("inviter_name"),
							rs.getInt("member_count"))))
			.optional();
	}

	/** 이 그룹의 쓸 수 있는 초대들. 최근에 만든 것이 앞이다. */
	List<LiveInviteResponse> findLiveInvites(long groupId, Instant now) {
		return this.jdbc.sql(LIVE_INVITES)
			.param("groupId", groupId)
			.param("now", Timestamp.from(now))
			.query((rs, rowNum) -> {
				Long creatorId = rs.getObject("creator_id", Long.class);
				MemberResponse createdBy = (creatorId != null) ? new MemberResponse(creatorId, rs.getString("creator_name"))
						: null;
				return new LiveInviteResponse(rs.getLong("id"), createdBy, seoulTime(rs, "created_at"),
						seoulTime(rs, "expires_at"));
			})
			.list();
	}

	private Map<Long, List<GroupMemberResponse>> members(String sql, long userId, Long groupId) {
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("userId", userId);
		if (groupId != null) {
			statement = statement.param("groupId", groupId);
		}
		return statement
			.query((rs, rowNum) -> new MemberRow(rs.getLong("group_id"),
					new GroupMemberResponse(rs.getLong("id"), rs.getString("display_name"),
							seoulTime(rs, "joined_at").toLocalDate())))
			.list()
			.stream()
			.collect(Collectors.groupingBy(MemberRow::groupId, LinkedHashMap::new,
					Collectors.mapping(MemberRow::member, Collectors.toList())));
	}

	private static GroupResponse group(ResultSet rs, Map<Long, List<GroupMemberResponse>> members)
			throws SQLException {
		long groupId = rs.getLong("id");
		LocalDate nearestDate = rs.getObject("nearest_date", LocalDate.class);
		NearestDeadline nearest = (nearestDate != null)
				? new NearestDeadline(nearestDate, rs.getString("nearest_company"), rs.getString("nearest_title")) : null;
		return new GroupResponse(groupId, rs.getString("name"), rs.getString("mark"),
				members.getOrDefault(groupId, List.of()), rs.getObject("leader_id", Long.class),
				rs.getInt("open_post_count"), Disclosure.valueOf(rs.getString("disclosure")), nearest);
	}

	/** timestamptz 를 한국 시각(+09:00)으로. */
	private static OffsetDateTime seoulTime(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).atZoneSameInstant(TimeConfig.SEOUL).toOffsetDateTime();
	}

	/** 초대 미리 보기와, 지금 쓸 수 있는지(아니면 410). */
	record InvitePreview(boolean usable, InvitePreviewResponse response) {
	}

	private record MemberRow(long groupId, GroupMemberResponse member) {
	}

}
