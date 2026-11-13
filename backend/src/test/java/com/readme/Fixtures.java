package com.readme;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 통합 테스트가 자기 데이터를 DB 에 직접 넣는 도우미. @IntegrationTest 를 붙인 테스트에서 주입받아 쓴다.
 *
 * <p>테스트끼리 DB 를 나눠 쓰므로 이메일 같은 유일한 값은 겹치지 않게 만든다. 순서가 중요한 시각(들어온 때, 관심을 켠 때)은
 * 부른 순서대로 늘어나고, 모두 멈춘 시계의 NOW 보다 앞이다. 그래서 테스트 안에서 API 로 만든 것(NOW)이 늘 뒤에 온다.
 */
public class Fixtures {

	private final JdbcClient jdbc;

	private final AtomicLong ticks = new AtomicLong();

	Fixtures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ── 사람 ───────────────────────────────────────────────────────────

	public long user(String name) {
		return this.jdbc.sql("""
				insert into users (email, display_name, email_verified_at)
				values (:email, :name, now()) returning id
				""")
			.param("email", UUID.randomUUID() + "@example.test")
			.param("name", name)
			.query(Long.class)
			.single();
	}

	public String emailOf(long userId) {
		return this.jdbc.sql("select email from users where id = :id").param("id", userId).query(String.class).single();
	}

	/** 계정 삭제. 멤버·관심·내 공고는 지워지고, 올린 사람과 댓글 쓴 사람은 빈다(테이블의 on delete). */
	public void deleteUser(long userId) {
		this.jdbc.sql("delete from users where id = :id").param("id", userId).update();
	}

	// ── 그룹 ───────────────────────────────────────────────────────────

	/** 그룹을 만들고 leader 를 그룹장, members 를 멤버로 넣는다. 들어온 순서는 인자 순서다. 표시 글자는 이름의 첫 글자. */
	public long group(String name, long leaderId, long... memberIds) {
		long groupId = this.jdbc.sql("insert into groups (name, mark, created_by) values (:name, :mark, :leader) returning id")
			.param("name", name)
			.param("mark", name.substring(0, 1))
			.param("leader", leaderId)
			.query(Long.class)
			.single();
		join(groupId, leaderId, "LEADER");
		for (long memberId : memberIds) {
			join(groupId, memberId, "MEMBER");
		}
		return groupId;
	}

	/** 공개 범위: "INTEREST", "APPLIED", "PROGRESS". */
	public void disclosure(long groupId, long userId, String disclosure) {
		this.jdbc.sql("update group_members set disclosure = :disclosure where group_id = :groupId and user_id = :userId")
			.param("disclosure", disclosure)
			.param("groupId", groupId)
			.param("userId", userId)
			.update();
	}

	private void join(long groupId, long userId, String role) {
		this.jdbc.sql("""
				insert into group_members (group_id, user_id, role, joined_at)
				values (:groupId, :userId, :role, :joinedAt)
				""")
			.param("groupId", groupId)
			.param("userId", userId)
			.param("role", role)
			.param("joinedAt", nextMoment())
			.update();
	}

	// ── 공고 ───────────────────────────────────────────────────────────

	/** 공고 본체. deadlineKind: "DATE"(날짜 필요), "ALWAYS", "UNKNOWN". */
	public long posting(String company, String title, String deadlineKind, LocalDate deadlineDate) {
		return this.jdbc.sql("""
				insert into postings (company, title, deadline_kind, deadline_date)
				values (:company, :title, :kind, :date) returning id
				""")
			.param("company", company)
			.param("title", title)
			.param("kind", deadlineKind)
			.param("date", deadlineDate, Types.DATE)
			.query(Long.class)
			.single();
	}

	/** 상세에만 보이는 공고 본체의 값. sectionsJson 은 [{ "title": …, "items": […] }] 꼴. */
	public void details(long postingId, String deadlineName, String applyAt, String sourceUrl, String sectionsJson) {
		this.jdbc.sql("""
				update postings
				   set deadline_name = :deadlineName, apply_at = :applyAt, source_url = :sourceUrl,
				       sections = cast(:sections as jsonb)
				 where id = :id
				""")
			.param("deadlineName", deadlineName)
			.param("applyAt", applyAt)
			.param("sourceUrl", sourceUrl)
			.param("sections", sectionsJson)
			.param("id", postingId)
			.update();
	}

	public void keywords(long postingId, String... keywords) {
		for (int i = 0; i < keywords.length; i++) {
			this.jdbc.sql("insert into posting_keywords (posting_id, keyword, seq) values (:postingId, :keyword, :seq)")
				.param("postingId", postingId)
				.param("keyword", keywords[i])
				.param("seq", i + 1)
				.update();
		}
	}

	/** 그룹에 공고를 올린다. */
	public void post(long groupId, long postingId, long postedBy, Instant postedAt) {
		this.jdbc.sql("""
				insert into group_posts (group_id, posting_id, posted_by, posted_at)
				values (:groupId, :postingId, :postedBy, :postedAt)
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.param("postedBy", postedBy)
			.param("postedAt", Timestamp.from(postedAt))
			.update();
	}

	public void takeDown(long groupId, long postingId) {
		this.jdbc.sql("update group_posts set taken_down_at = now() where group_id = :groupId and posting_id = :postingId")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.update();
	}

	// ── 관심, 지원 상태, 댓글 ─────────────────────────────────────────────

	public void interest(long groupId, long postingId, long userId) {
		this.jdbc.sql("""
				insert into group_post_interests (group_id, posting_id, user_id, created_at)
				values (:groupId, :postingId, :userId, :createdAt)
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.param("userId", userId)
			.param("createdAt", nextMoment())
			.update();
	}

	/** 내 공고 행을 이 상태로 둔다. stage, result 는 지원함(APPLIED)일 때만 넣는다. */
	public void status(long userId, long postingId, String decision, String stage, String result) {
		this.jdbc.sql("""
				insert into user_postings (user_id, posting_id, decision, stage, result)
				values (:userId, :postingId, :decision, :stage, :result)
				on conflict (user_id, posting_id)
				do update set decision = excluded.decision, stage = excluded.stage, result = excluded.result
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.param("decision", decision)
			.param("stage", stage, Types.VARCHAR)
			.param("result", result, Types.VARCHAR)
			.update();
	}

	public long comment(long groupId, long postingId, long authorId, String body, Instant at) {
		return this.jdbc.sql("""
				insert into group_post_comments (group_id, posting_id, author_id, body, created_at, updated_at)
				values (:groupId, :postingId, :authorId, :body, :at, :at) returning id
				""")
			.param("groupId", groupId)
			.param("postingId", postingId)
			.param("authorId", authorId)
			.param("body", body)
			.param("at", Timestamp.from(at))
			.query(Long.class)
			.single();
	}

	/** 부를 때마다 1ms 씩 늘어나는 시각. 멈춘 시계의 NOW 하루 전에서 시작한다. */
	private Timestamp nextMoment() {
		return Timestamp.from(FixedClockConfiguration.NOW.minus(Duration.ofDays(1)).plusMillis(this.ticks.incrementAndGet()));
	}

}
