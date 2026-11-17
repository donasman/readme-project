package com.readme.posting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.group.Disclosure;
import com.readme.keyword.KeywordDictionary;
import com.readme.posting.MyPostingDetailResponse.Details;
import com.readme.posting.MyPostingDetailResponse.GroupState;

/**
 * 내 공고 화면(내 공고, 지원 현황, 달력, 홈, 공고 상세)에 내보내는 것을 읽는다. 내 공고 행(user_postings)이 있는 공고만 읽는다.
 * 그룹은 내가 멤버이고 그 공고를 내리지 않은 그룹만 싣는다.
 */
@Component
class MyPostingQueries {

	private static final String POSTINGS = """
			select p.id, p.company, p.title, p.deadline_kind, p.deadline_date, up.decision, up.stage, up.result
			  from user_postings up
			  join postings p on p.id = up.posting_id
			 where up.user_id = :userId
			""";

	/** 내 공고가 올라가 있는 내 그룹. 그 그룹에서의 내 관심, 그 그룹의 댓글 수, 내 공개 범위가 붙는다. */
	private static final String GROUPS = """
			select gp.posting_id, g.id, g.name, g.mark, m.disclosure,
			       exists (select 1 from group_post_interests i
			                where i.group_id = gp.group_id and i.posting_id = gp.posting_id
			                  and i.user_id = m.user_id) as interested,
			       (select count(*) from group_post_comments c
			         where c.group_id = gp.group_id and c.posting_id = gp.posting_id) as comment_count
			  from user_postings up
			  join group_posts gp on gp.posting_id = up.posting_id and gp.taken_down_at is null
			  join group_members m on m.group_id = gp.group_id and m.user_id = up.user_id
			  join groups g on g.id = gp.group_id
			 where up.user_id = :userId
			""";

	private static final String GROUPS_ORDER = " order by gp.posting_id, m.joined_at, g.id";

	private static final TypeReference<List<PostSection>> SECTIONS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	MyPostingQueries(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * 내 공고 전부. 내 공고에 넣은 시각의 역순이다. 진행 중·마감됨, 결정별 거르기, 검색, 정렬, 지원 현황의 숫자는 프런트가 한다
	 * (그룹 공고의 설계 가정 6과 같다).
	 */
	// shortcut: 나눠 받지 않고 한 번에 전부 준다, 마감된 공고가 쌓여 응답이 느려지면 나눠 받기를 더한다
	List<MyPostingResponse> list(long userId) {
		Map<Long, List<GroupState>> groups = groupStates(GROUPS + GROUPS_ORDER, userId, null);
		return this.jdbc.sql(POSTINGS + " order by up.created_at desc, up.posting_id desc")
			.param("userId", userId)
			.query((rs, rowNum) -> posting(rs, groups))
			.list();
	}

	/** 상세. 내 공고에 없는 공고면 비어 있다. */
	/** 내 전형 일정 전부. 날짜, 시각(없으면 그날의 끝), 회사명 순이다. */
	List<ScheduleResponse> schedules(long userId) {
		return this.jdbc.sql("""
				select s.posting_id, p.company, p.title, s.stage, s.on_date, to_char(s.at_time, 'HH24:MI') as at_time
				  from user_posting_schedules s
				  join postings p on p.id = s.posting_id
				 where s.user_id = :userId
				 order by s.on_date, s.at_time nulls last, p.company, s.posting_id
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new ScheduleResponse(rs.getLong("posting_id"), rs.getString("company"),
					rs.getString("title"), ApplicationStatus.Stage.valueOf(rs.getString("stage")),
					rs.getObject("on_date", LocalDate.class), rs.getString("at_time")))
			.list();
	}

	Optional<MyPostingDetailResponse> detail(long userId, long postingId) {
		Map<Long, List<GroupState>> groups = groupStates(GROUPS + " and up.posting_id = :postingId" + GROUPS_ORDER,
				userId, postingId);
		return this.jdbc.sql(POSTINGS + " and up.posting_id = :postingId")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> posting(rs, groups))
			.optional()
			.map((posting) -> {
				Details details = details(userId, postingId);
				return MyPostingDetailResponse.of(posting, details, keywords(postingId),
						CardMatching.match(details.analysis(), myCards(userId), profileFacts(userId),
								KeywordDictionary.standard()),
						groups.getOrDefault(postingId, List.of()));
			});
	}

	/** 요건과 맞춰 볼 내 프로필 값(profile 패키지의 테이블을 읽기만 한다). 프로필을 넣지 않았으면 모두 빈 목록이다. */
	private CardMatching.ProfileFacts profileFacts(long userId) {
		return new CardMatching.ProfileFacts(
				column("select name from profile_certificates where user_id = :userId order by seq", userId),
				column("select test from profile_languages where user_id = :userId order by seq", userId),
				column("select major from profile_educations where user_id = :userId and major is not null order by seq",
						userId));
	}

	private List<String> column(String sql, long userId) {
		return this.jdbc.sql(sql).param("userId", userId).query(String.class).list();
	}

	/** 내 카드와 태그(card 패키지의 테이블을 읽기만 한다). 먼저 만든 카드가 앞이다. */
	private List<CardMatching.MatchCard> myCards(long userId) {
		Map<Long, List<String>> tags = this.jdbc.sql("""
				select t.card_id, t.tag from card_tags t join experience_cards c on c.id = t.card_id
				 where c.user_id = :userId order by t.card_id, t.tag
				""").param("userId", userId).query((rs, rowNum) -> Map.entry(rs.getLong("card_id"), rs.getString("tag")))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return this.jdbc.sql("select id, title from experience_cards where user_id = :userId order by created_at, id")
			.param("userId", userId)
			.query((rs, rowNum) -> new CardMatching.MatchCard(rs.getLong("id"), rs.getString("title"),
					tags.getOrDefault(rs.getLong("id"), List.of())))
			.list();
	}

	private Map<Long, List<GroupState>> groupStates(String sql, long userId, Long postingId) {
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("userId", userId);
		if (postingId != null) {
			statement = statement.param("postingId", postingId);
		}
		return statement
			.query((rs, rowNum) -> new GroupState(rs.getLong("id"), rs.getString("name"), rs.getString("mark"),
					rs.getLong("posting_id"), rs.getBoolean("interested"), rs.getInt("comment_count"),
					Disclosure.valueOf(rs.getString("disclosure"))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(GroupState::postId));
	}

	private static MyPostingResponse posting(ResultSet rs, Map<Long, List<GroupState>> groups) throws SQLException {
		long postingId = rs.getLong("id");
		ApplicationStatus status = ApplicationStatus.of(rs.getString("decision"), rs.getString("stage"),
				rs.getString("result"));
		return new MyPostingResponse(postingId, rs.getString("company"), rs.getString("title"),
				DeadlineResponse.of(rs.getString("deadline_kind"), rs.getObject("deadline_date", LocalDate.class)),
				status.decision(), status.stage(), status.result(), status.label(),
				groups.getOrDefault(postingId, List.of()).stream().map(GroupState::group).toList());
	}

	private Details details(long userId, long postingId) {
		return this.jdbc.sql("""
				select p.apply_at, p.source_url, p.sections::text as sections, p.analysis::text as analysis, up.memo
				  from user_postings up
				  join postings p on p.id = up.posting_id
				 where up.user_id = :userId and up.posting_id = :postingId
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> new Details(rs.getString("apply_at"), rs.getString("source_url"),
					this.jsonMapper.readValue(rs.getString("sections"), SECTIONS),
					(rs.getString("analysis") != null)
							? this.jsonMapper.readValue(rs.getString("analysis"), PostingAnalysis.class) : null,
					rs.getString("memo")))
			.single();
	}

	private List<String> keywords(long postingId) {
		return this.jdbc.sql("select keyword from posting_keywords where posting_id = :postingId order by seq")
			.param("postingId", postingId)
			.query(String.class)
			.list();
	}

}
