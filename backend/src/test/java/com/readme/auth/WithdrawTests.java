package com.readme.auth;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 탈퇴: 미리 보기(GET /api/me/withdrawal)와 탈퇴(POST /api/me/withdraw). 로그인 설계 4절, 그룹 설계 9절(그룹장 넘기기), 2026-11-15.
 * 지워지는 범위는 그룹·공고 설계 3.3 에서 확인한 외래 키 그대로다.
 */
@IntegrationTest
class WithdrawTests {

	private static final String PASSWORD = "correct horse";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	// ── 미리 보기 ──────────────────────────────────────────────────────

	@Test
	void previewCountsWhatGoesAndWhoTakesOverMyGroups() {
		long me = this.fixtures.user("다온");
		long first = this.fixtures.user("가람");
		long second = this.fixtures.user("나래");
		long led = this.fixtures.group("백엔드 스터디", me, first, second);
		long solo = this.fixtures.group("혼자 그룹", me);
		this.fixtures.group("남의 그룹", first, me);
		card(me);
		card(me);
		card(first);
		source(me);
		this.fixtures.status(me, this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null), "UNDECIDED", null, null);
		this.fixtures.status(me, this.fixtures.posting("예시 은행", "전산", "ALWAYS", null), "HOLD", null, null);

		assertThat(this.mvc.get().uri("/api/me/withdrawal").with(loginAs(me))).hasStatusOk().bodyJson()
			.isStrictlyEqualTo("""
					{
					  "cards": 2, "sources": 1, "postings": 2, "groups": 3,
					  "leads": [
					    { "id": %d, "name": "백엔드 스터디", "others": 2, "successor": { "id": %d, "name": "가람" } },
					    { "id": %d, "name": "혼자 그룹", "others": 0, "successor": null }
					  ]
					}
					""".formatted(led, first, solo));
	}

	@Test
	void previewOfAnEmptyAccount() {
		assertThat(this.mvc.get().uri("/api/me/withdrawal").with(loginAs(this.fixtures.user("다온")))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{ "cards": 0, "sources": 0, "postings": 0, "groups": 0, "leads": [] }
					""");
	}

	// ── 탈퇴 ───────────────────────────────────────────────────────────

	@Test
	void aWrongPasswordIs400AndDeletesNothing() {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		long other = this.fixtures.user("가람");
		long group = this.fixtures.group("백엔드 스터디", me, other);

		for (String body : new String[] { "{ \"password\": \"wrong password\" }", "{ \"email\": \"%s\" }"
			.formatted(this.fixtures.emailOf(me)) }) {
			assertThat(withdraw(loginAs(me), body)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
					{ "code": "WRONG_PASSWORD", "message": "비밀번호가 맞지 않습니다" }
					""");
		}

		assertThat(exists("users", "id", me)).isTrue();
		assertThat(leaderOf(group)).isEqualTo(me);
	}

	@Test
	void withdrawingDeletesTheAccountAndEndsEveryLoginIncludingThisOne() {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		String email = this.fixtures.emailOf(me);
		String phone = setCookie(login(email), "SESSION");
		String laptop = setCookie(login(email), "SESSION");
		card(me);
		source(me);

		MvcTestResult result = this.mvc.post().uri("/api/me/withdraw").cookie(new Cookie("SESSION", phone)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"%s\" }".formatted(PASSWORD)).exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		// 이 브라우저의 세션 쿠키를 지운다
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anyMatch((header) -> header.startsWith("SESSION=;") && header.contains("Max-Age=0"));
		assertThat(exists("users", "id", me)).isFalse();
		assertThat(exists("experience_cards", "user_id", me)).isFalse();
		assertThat(exists("card_sources", "user_id", me)).isFalse();
		assertThat(this.jdbc.sql("select count(*) from spring_session where principal_name = :name")
			.param("name", String.valueOf(me)).query(Integer.class).single()).isZero();
		for (String session : new String[] { phone, laptop }) {
			assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login(email)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void theEarliestOtherMemberBecomesTheLeaderOfMyGroup() {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		long first = this.fixtures.user("가람");
		long second = this.fixtures.user("나래");
		long group = this.fixtures.group("백엔드 스터디", me, first, second);

		assertThat(withdraw(loginAs(me), passwordBody())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(leaderOf(group)).isEqualTo(first);
		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(second))).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("""
					[ { "id": %d, "leaderId": %d, "members": [ { "id": %d }, { "id": %d } ] } ]
					""".formatted(group, first, first, second));
	}

	@Test
	void theSuccessorIsTheEarliestJoinerNotTheSmallestIdAndThePreviewAgrees() throws Exception {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		long smallerId = this.fixtures.user("가람");
		long earlierJoiner = this.fixtures.user("나래");
		// 나래가 먼저 들어왔지만 id 는 가람이 작다. id 순으로 고르면 가람이 된다
		long group = this.fixtures.group("백엔드 스터디", me, earlierJoiner, smallerId);
		long solo = this.fixtures.group("혼자 그룹", me);

		MvcTestResult preview = this.mvc.get().uri("/api/me/withdrawal").with(loginAs(me)).exchange();
		assertThat(preview).hasStatusOk();
		String body = preview.getResponse().getContentAsString();
		long previewed = ((Number) JsonPath.read(body, "$.leads[0].successor.id")).longValue();
		assertThat(((Number) JsonPath.read(body, "$.leads[0].id")).longValue()).isEqualTo(group);
		assertThat((Object) JsonPath.read(body, "$.leads[1].successor")).isNull();

		assertThat(withdraw(loginAs(me), passwordBody())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(leaderOf(group)).isEqualTo(earlierJoiner).isEqualTo(previewed);
		assertThat(exists("groups", "id", solo)).isFalse();
	}

	@Test
	void myGroupWithNoOneElseIsDeletedButThePostingStays() {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		long solo = this.fixtures.group("혼자 그룹", me);
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(solo, posting, me, NOW);
		this.fixtures.comment(solo, posting, me, "메모", NOW);

		assertThat(withdraw(loginAs(me), passwordBody())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(exists("groups", "id", solo)).isFalse();
		assertThat(exists("group_posts", "group_id", solo)).isFalse();
		assertThat(exists("group_post_comments", "group_id", solo)).isFalse();
		assertThat(exists("postings", "id", posting)).isTrue();
	}

	@Test
	void postsAndCommentsStayWithoutMeAndOthersKeepTheirData() {
		long me = this.fixtures.passwordUser("다온", PASSWORD, true);
		long other = this.fixtures.user("가람");
		long group = this.fixtures.group("남의 그룹", other, me);
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.jdbc.sql("update postings set created_by = :me where id = :id").param("me", me).param("id", posting).update();
		this.fixtures.post(group, posting, me, NOW);
		long myComment = this.fixtures.comment(group, posting, me, "제가 올렸어요", NOW);
		long theirComment = this.fixtures.comment(group, posting, other, "고마워요", NOW);
		this.fixtures.interest(group, posting, me);
		this.fixtures.interest(group, posting, other);
		this.fixtures.status(me, posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");
		this.fixtures.status(other, posting, "HOLD", null, null);
		card(other);

		assertThat(withdraw(loginAs(me), passwordBody())).hasStatus(HttpStatus.NO_CONTENT);

		// 올린 공고와 쓴 댓글은 남고 올린 사람·쓴 사람만 빈다(set null)
		assertThat(value("select posted_by from group_posts where group_id = :id and posting_id = " + posting, group))
			.isNull();
		assertThat(value("select author_id from group_post_comments where id = :id", myComment)).isNull();
		assertThat(value("select created_by from postings where id = :id", posting)).isNull();
		// 내 관심, 내 공고, 멤버 자격은 지워진다(cascade)
		assertThat(count("select count(*) from group_post_interests where user_id = :id", me)).isZero();
		assertThat(exists("user_postings", "user_id", me)).isFalse();
		assertThat(exists("group_members", "user_id", me)).isFalse();
		// 남의 것은 그대로다
		assertThat(leaderOf(group)).isEqualTo(other);
		assertThat(value("select author_id from group_post_comments where id = :id", theirComment)).isEqualTo(other);
		assertThat(count("select count(*) from group_post_interests where user_id = :id", other)).isEqualTo(1);
		assertThat(exists("user_postings", "user_id", other)).isTrue();
		assertThat(exists("experience_cards", "user_id", other)).isTrue();
	}

	@Test
	void anAccountWithoutAPasswordConfirmsWithItsEmail() {
		// 구글로만 가입한 계정
		long me = this.fixtures.user("다온");
		String email = this.fixtures.emailOf(me);

		assertThat(withdraw(loginAs(me), "{ \"email\": \"other-%s\" }".formatted(email)))
			.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("WRONG_EMAIL");
		assertThat(withdraw(loginAs(me), "{ \"password\": \"%s\" }".formatted(PASSWORD)))
			.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("WRONG_EMAIL");
		assertThat(exists("users", "id", me)).isTrue();

		// 대소문자와 앞뒤 공백은 가리지 않는다
		assertThat(withdraw(loginAs(me), "{ \"email\": \" %s \" }".formatted(email.toUpperCase(Locale.ROOT))))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(exists("users", "id", me)).isFalse();
	}

	private MvcTestResult withdraw(RequestPostProcessor login, String body) {
		return this.mvc.post().uri("/api/me/withdraw").with(login).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content(body).exchange();
	}

	private static String passwordBody() {
		return "{ \"password\": \"%s\" }".formatted(PASSWORD);
	}

	private MvcTestResult login(String email) {
		return this.mvc.post().uri("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)).exchange();
	}

	private void card(long userId) {
		this.jdbc.sql("insert into experience_cards (user_id, title) values (:userId, '카드')").param("userId", userId).update();
	}

	private void source(long userId) {
		String sha256 = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
		this.jdbc.sql("""
				insert into card_sources (user_id, kind, sha256, body, char_count)
				values (:userId, 'TEXT', :sha256, '본문', 2)
				""").param("userId", userId).param("sha256", sha256).update();
	}

	private Long leaderOf(long groupId) {
		return this.jdbc.sql("select user_id from group_members where group_id = :id and role = 'LEADER'")
			.param("id", groupId).query(Long.class).optional().orElse(null);
	}

	private boolean exists(String table, String column, long id) {
		return count("select count(*) from " + table + " where " + column + " = :id", id) > 0;
	}

	private int count(String sql, long id) {
		return this.jdbc.sql(sql).param("id", id).query(Integer.class).single();
	}

	/** 한 행의 한 칸. single() 은 null 을 받지 않아 list() 로 읽는다. */
	private Long value(String sql, long id) {
		return this.jdbc.sql(sql).param("id", id).query((rs, rowNum) -> rs.getObject(1, Long.class)).list().getFirst();
	}

}
