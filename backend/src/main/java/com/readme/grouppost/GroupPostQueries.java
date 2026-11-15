package com.readme.grouppost;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.common.config.TimeConfig;
import com.readme.group.Disclosure;
import com.readme.group.MemberResponse;
import com.readme.grouppost.GroupPostDetailResponse.PostingDetails;
import com.readme.posting.ApplicationStatus;
import com.readme.posting.DeadlineResponse;
import com.readme.posting.PostSection;

/**
 * 그룹 공고 화면에 내보내는 것을 읽는다. 목록은 쿼리 둘(공고, 관심 멤버)로 읽는다(설계 6절 16번).
 * 그룹에서 내린 공고는 어디에서도 읽지 않는다.
 */
@Component
class GroupPostQueries {

	/** 공고 줄. 보는 사람의 관심, 보는 사람의 지원 상태, 이 그룹의 댓글 수가 붙는다. */
	private static final String POSTS = """
			select p.id, p.company, p.title, p.deadline_kind, p.deadline_date, p.source_url, gp.posted_at,
			       u.id as posted_by_id, u.display_name as posted_by_name,
			       exists (select 1 from group_post_interests i
			                where i.group_id = gp.group_id and i.posting_id = gp.posting_id
			                  and i.user_id = :viewerId) as my_interest,
			       up.decision, up.stage, up.result,
			       (select count(*) from group_post_comments c
			         where c.group_id = gp.group_id and c.posting_id = gp.posting_id) as comment_count
			  from group_posts gp
			  join postings p on p.id = gp.posting_id
			  left join users u on u.id = gp.posted_by
			  left join user_postings up on up.posting_id = gp.posting_id and up.user_id = :viewerId
			 where gp.group_id = :groupId
			   and gp.taken_down_at is null
			""";

	/** 이 그룹에서 관심을 켠 다른 멤버들과 그 멤버의 공개 범위, 지원 상태. */
	private static final String INTERESTS = """
			select i.posting_id, u.id as user_id, u.display_name, m.disclosure, up.decision, up.stage, up.result
			  from group_post_interests i
			  join group_posts gp on gp.group_id = i.group_id and gp.posting_id = i.posting_id
			  join group_members m on m.group_id = i.group_id and m.user_id = i.user_id
			  join users u on u.id = i.user_id
			  left join user_postings up on up.user_id = i.user_id and up.posting_id = i.posting_id
			 where i.group_id = :groupId
			   and i.user_id <> :viewerId
			   and gp.taken_down_at is null
			""";

	private static final String INTERESTS_ORDER = " order by i.posting_id, i.created_at, i.user_id";

	private static final String COMMENTS = """
			select c.id, c.body, c.created_at, u.id as author_id, u.display_name as author_name
			  from group_post_comments c
			  left join users u on u.id = c.author_id
			""";

	/**
	 * 홈의 그룹 소식. 내 그룹에 since 뒤로 올라온 공고는 그룹마다 한 줄(개수), 달린 댓글은 공고 × 쓴 사람마다 한 줄이다.
	 * 내린 공고와 그 댓글, 내가 올린 공고와 내가 쓴 댓글, 탈퇴한 사람의 댓글은 넣지 않는다. 댓글 본문과 지원 상태는 싣지 않는다.
	 */
	private static final String ACTIVITY = """
			select g.id as group_id, g.name, g.mark, null::bigint as posting_id, null::varchar as company,
			       null::varchar as author_name, count(*) as post_count, max(gp.posted_at) as happened_at
			  from group_posts gp
			  join group_members m on m.group_id = gp.group_id and m.user_id = :viewerId
			  join groups g on g.id = gp.group_id
			 where gp.taken_down_at is null
			   and gp.posted_at >= :since
			   and gp.posted_by is distinct from :viewerId
			 group by g.id, g.name, g.mark
			union all
			select g.id, g.name, g.mark, p.id, p.company, u.display_name, 0, max(c.created_at)
			  from group_post_comments c
			  join group_posts gp on gp.group_id = c.group_id and gp.posting_id = c.posting_id
			  join group_members m on m.group_id = c.group_id and m.user_id = :viewerId
			  join groups g on g.id = c.group_id
			  join postings p on p.id = c.posting_id
			  join users u on u.id = c.author_id
			 where gp.taken_down_at is null
			   and c.created_at >= :since
			   and c.author_id <> :viewerId
			 group by g.id, g.name, g.mark, p.id, p.company, u.id, u.display_name
			 order by happened_at desc, group_id, posting_id nulls first
			 limit :limit
			""";

	private static final TypeReference<List<PostSection>> SECTIONS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	GroupPostQueries(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/** 목록. 올린 시각의 역순이다. 마감순 정렬과 묶음은 프런트가 한다(설계 가정 9). */
	List<GroupPostResponse> list(long groupId, long viewerId) {
		Map<Long, List<InterestResponse>> interests = interests(INTERESTS + INTERESTS_ORDER, groupId, viewerId, null);
		return this.jdbc.sql(POSTS + " order by gp.posted_at desc, p.id desc")
			.param("groupId", groupId)
			.param("viewerId", viewerId)
			.query((rs, rowNum) -> post(rs, groupId, interests))
			.list();
	}

	/** 상세. 그 그룹에 없거나 내린 공고면 비어 있다. */
	Optional<GroupPostDetailResponse> detail(long groupId, long postingId, long viewerId) {
		Map<Long, List<InterestResponse>> interests = interests(
				INTERESTS + " and i.posting_id = :postingId" + INTERESTS_ORDER, groupId, viewerId, postingId);
		Optional<GroupPostResponse> post = this.jdbc.sql(POSTS + " and gp.posting_id = :postingId")
			.param("groupId", groupId)
			.param("viewerId", viewerId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> post(rs, groupId, interests))
			.optional();
		return post.map((found) -> GroupPostDetailResponse.of(found, details(postingId), keywords(postingId),
				comments(groupId, postingId)));
	}

	/** 그 그룹에 있고 내리지 않은 공고인지. */
	boolean isVisible(long groupId, long postingId) {
		return this.jdbc.sql("""
				select exists (select 1 from group_posts
				                where group_id = :groupId and posting_id = :postingId and taken_down_at is null)
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.query(Boolean.class)
			.single();
	}

	/** 이 공고가 다른 그룹에도 올라와 있고(내리지 않은), 그 그룹에 올린 사람이 이 사람이 아닌지. 탈퇴해 비었으면 남으로 본다. */
	boolean isSharedWithAnotherPoster(long groupId, long postingId, long userId) {
		return this.jdbc.sql("""
				select exists (select 1 from group_posts
				                where posting_id = :postingId and group_id <> :groupId and taken_down_at is null
				                  and posted_by is distinct from :userId)
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.param("userId", userId)
			.query(Boolean.class)
			.single();
	}

	MyStateResponse myState(long groupId, long postingId, long viewerId) {
		boolean interested = this.jdbc.sql("""
				select exists (select 1 from group_post_interests
				                where group_id = :groupId and posting_id = :postingId and user_id = :viewerId)
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.param("viewerId", viewerId)
			.query(Boolean.class)
			.single();
		ApplicationStatus status = this.jdbc
			.sql("select decision, stage, result from user_postings where user_id = :viewerId and posting_id = :postingId")
			.param("viewerId", viewerId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> status(rs))
			.optional()
			.orElse(null);
		return new MyStateResponse(interested, ApplicationStatus.labelOf(status));
	}

	CommentResponse comment(long commentId) {
		return this.jdbc.sql(COMMENTS + " where c.id = :commentId")
			.param("commentId", commentId)
			.query((rs, rowNum) -> comment(rs))
			.single();
	}

	/** 홈의 그룹 소식. 최근 것부터 limit 줄. */
	List<GroupNewsResponse> activity(long viewerId, OffsetDateTime since, int limit) {
		return this.jdbc.sql(ACTIVITY)
			.param("viewerId", viewerId)
			.param("since", since)
			.param("limit", limit)
			.query((rs, rowNum) -> news(rs))
			.list();
	}

	private static GroupNewsResponse news(ResultSet rs) throws SQLException {
		Long postingId = rs.getObject("posting_id", Long.class);
		String text = (postingId == null) ? "새 공고 %d건".formatted(rs.getLong("post_count"))
				: "%s 님이 %s 공고에 댓글을 남겼습니다".formatted(rs.getString("author_name"), rs.getString("company"));
		return new GroupNewsResponse(
				new GroupNewsResponse.GroupLabel(rs.getLong("group_id"), rs.getString("name"), rs.getString("mark")),
				text, postingId);
	}

	private Map<Long, List<InterestResponse>> interests(String sql, long groupId, long viewerId, Long postingId) {
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("groupId", groupId).param("viewerId", viewerId);
		if (postingId != null) {
			statement = statement.param("postingId", postingId);
		}
		return statement
			.query((rs, rowNum) -> new InterestRow(rs.getLong("posting_id"),
					InterestResponse.of(new MemberResponse(rs.getLong("user_id"), rs.getString("display_name")),
							Disclosure.valueOf(rs.getString("disclosure")), status(rs))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(InterestRow::postingId,
					Collectors.mapping(InterestRow::interest, Collectors.toList())));
	}

	private GroupPostResponse post(ResultSet rs, long groupId, Map<Long, List<InterestResponse>> interests)
			throws SQLException {
		long postingId = rs.getLong("id");
		MyStateResponse me = new MyStateResponse(rs.getBoolean("my_interest"), ApplicationStatus.labelOf(status(rs)));
		return new GroupPostResponse(postingId, groupId, rs.getString("company"), rs.getString("title"),
				DeadlineResponse.of(rs.getString("deadline_kind"), rs.getObject("deadline_date", LocalDate.class)),
				member(rs, "posted_by_id", "posted_by_name"),
				LocalDate.ofInstant(instant(rs, "posted_at"), TimeConfig.SEOUL),
				interests.getOrDefault(postingId, List.of()), me, rs.getInt("comment_count"), rs.getString("source_url"));
	}

	private PostingDetails details(long postingId) {
		return this.jdbc
			.sql("select deadline_name, apply_at, sections::text as sections from postings where id = :postingId")
			.param("postingId", postingId)
			.query((rs, rowNum) -> new PostingDetails(rs.getString("deadline_name"), rs.getString("apply_at"),
					this.jsonMapper.readValue(rs.getString("sections"), SECTIONS)))
			.single();
	}

	private List<String> keywords(long postingId) {
		return this.jdbc.sql("select keyword from posting_keywords where posting_id = :postingId order by seq")
			.param("postingId", postingId)
			.query(String.class)
			.list();
	}

	private List<CommentResponse> comments(long groupId, long postingId) {
		return this.jdbc.sql(COMMENTS + " where c.group_id = :groupId and c.posting_id = :postingId order by c.created_at, c.id")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> comment(rs))
			.list();
	}

	private static CommentResponse comment(ResultSet rs) throws SQLException {
		return new CommentResponse(rs.getLong("id"), member(rs, "author_id", "author_name"),
				instant(rs, "created_at").atZone(TimeConfig.SEOUL).toOffsetDateTime(),
				CommentPart.split(rs.getString("body")));
	}

	private static ApplicationStatus status(ResultSet rs) throws SQLException {
		return ApplicationStatus.of(rs.getString("decision"), rs.getString("stage"), rs.getString("result"));
	}

	/** 사람이 계정을 지웠으면 null. */
	private static MemberResponse member(ResultSet rs, String idColumn, String nameColumn) throws SQLException {
		Long id = rs.getObject(idColumn, Long.class);
		return (id != null) ? new MemberResponse(id, rs.getString(nameColumn)) : null;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private record InterestRow(long postingId, InterestResponse interest) {
	}

}
