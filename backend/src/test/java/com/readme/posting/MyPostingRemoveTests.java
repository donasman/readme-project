package com.readme.posting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * DELETE /api/me/postings/{postingId}: 내 공고에서 빼기(설계 4.2, 2026-11-16). 내 행과 함께 상태·메모·전형 일정이 지워지고, 내 그룹들에서
 * 켜 둔 관심이 꺼진다. 공고 본체, 그룹 공고, 다른 멤버의 관심은 그대로다.
 */
@IntegrationTest
class MyPostingRemoveTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long me;

	long other;

	long study;

	long club;

	long posting;

	@BeforeEach
	void postingInTwoOfMyGroups() {
		this.me = this.fixtures.user("다온");
		this.other = this.fixtures.user("가람");
		this.study = this.fixtures.group("백엔드 스터디", this.other, this.me);
		this.club = this.fixtures.group("취업 동아리", this.me);
		this.posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(this.study, this.posting, this.other, NOW);
		this.fixtures.post(this.club, this.posting, this.me, NOW);
		this.fixtures.interest(this.study, this.posting, this.me);
		this.fixtures.interest(this.club, this.posting, this.me);
		this.fixtures.interest(this.study, this.posting, this.other);
		this.fixtures.status(this.me, this.posting, "APPLIED", "TEST", "IN_PROGRESS");
		this.fixtures.status(this.other, this.posting, "UNDECIDED", null, null);
	}

	@Test
	void removeDeletesMyRowAndTurnsOffMyInterestsOnly() {
		this.jdbc.sql("""
				insert into user_posting_schedules (user_id, posting_id, stage, on_date)
				values (:userId, :postingId, 'TEST', date '2026-11-20')
				""").param("userId", this.me).param("postingId", this.posting).update();

		assertThat(remove(this.me, this.posting)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/api/me/schedules").with(loginAs(this.me))).bodyJson().isStrictlyEqualTo("[]");
		assertThat(count("select count(*) from user_postings where user_id = :userId and posting_id = :postingId",
				this.me)).isZero();
		assertThat(count("select count(*) from group_post_interests where user_id = :userId and posting_id = :postingId",
				this.me)).isZero(); // 두 그룹 모두
		assertThat(count("select count(*) from group_post_interests where user_id = :userId and posting_id = :postingId",
				this.other)).isOne();
		assertThat(count("select count(*) from user_postings where user_id = :userId and posting_id = :postingId",
				this.other)).isOne();
		// 그룹 공고는 그대로 보이고, 내 관심만 꺼져 있다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.study, this.posting)
			.with(loginAs(this.me))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.post.me.interested")
			.isEqualTo(false);
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.study, this.posting)
			.with(loginAs(this.other))).bodyJson().extractingPath("$.post.me.interested").isEqualTo(true);
	}

	@Test
	void addingAgainStartsFresh() {
		this.mvc.put()
			.uri("/api/me/postings/{id}/memo", this.posting)
			.with(loginAs(this.me))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"memo\": \"면접 준비\" }")
			.exchange();
		remove(this.me, this.posting);

		assertThat(this.mvc.put()
			.uri("/api/me/postings/{id}", this.posting)
			.with(loginAs(this.me))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"decision\": \"hold\" }")).hasStatusOk();
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).bodyJson()
			.isLenientlyEqualTo("""
					{ "posting": { "decision": "hold", "memo": null } }
					""");
	}

	@Test
	void postingNotInMyPostingsIs404() {
		long notMine = this.fixtures.posting("예시 은행", "서버", "ALWAYS", null);
		this.fixtures.status(this.other, notMine, "UNDECIDED", null, null);

		assertThat(remove(this.me, notMine)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("POSTING_NOT_FOUND");
		assertThat(count("select count(*) from user_postings where user_id = :userId and posting_id = " + notMine,
				this.other)).isOne();
		assertThat(remove(this.me, this.posting)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(remove(this.me, this.posting)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void removeNeedsXsrfAndLogin() {
		assertThat(this.mvc.delete().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me)))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.delete().uri("/api/me/postings/{id}", this.posting).with(xsrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("select count(*) from user_postings where user_id = :userId and posting_id = :postingId",
				this.me)).isOne();
	}

	private MvcTestResult remove(long userId, long postingId) {
		return this.mvc.delete().uri("/api/me/postings/{id}", postingId).with(loginAs(userId)).with(xsrf()).exchange();
	}

	private long count(String sql, long userId) {
		var spec = this.jdbc.sql(sql).param("userId", userId);
		if (sql.contains(":postingId")) {
			spec = spec.param("postingId", this.posting);
		}
		return spec.query(Long.class).single();
	}

}
