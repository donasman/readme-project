package com.readme.posting;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * PUT /api/me/postings/{postingId}: 내 지원 상태(설계 4.2, 5.3). PUT …/memo: 나만 보는 메모(설계 4.2).
 */
@IntegrationTest
class MyPostingTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long me;

	long group;

	long posting;

	@BeforeEach
	void jobInMyGroup() {
		this.me = this.fixtures.user("다온");
		this.group = this.fixtures.group("백엔드 스터디", this.me);
		this.posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(this.group, this.posting, this.me, NOW);
	}

	@Test
	void myStatusComesBackWithItsLabelAndShowsInTheGroup() {
		assertThat(setStatus(this.posting, "{ \"decision\": \"applied\", \"stage\": \"test\", \"result\": \"in_progress\" }"))
			.hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{ "postingId": %d, "decision": "applied", "stage": "test", "result": "in_progress", "status": "필기 단계" }
					""".formatted(this.posting));
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", this.group).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.posts[0].me.status").isEqualTo("필기 단계");
	}

	@Test
	void notAppliedHasNoStageResultOrLabel() {
		assertThat(setStatus(this.posting, "{ \"decision\": \"skip\" }")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "postingId": %d, "decision": "skip", "stage": null, "result": null, "status": null }
				""".formatted(this.posting));
	}

	@Test
	void sameRequestTwiceLeavesOneRow() {
		String body = "{ \"decision\": \"applied\", \"stage\": \"interview_1\", \"result\": \"passed\" }";

		setStatus(this.posting, body).exchange();
		assertThat(setStatus(this.posting, body)).hasStatusOk()
			.bodyJson().extractingPath("$.status").isEqualTo("1차 면접 합격");
		assertThat(this.jdbc.sql("select count(*) from user_postings where user_id = :userId").param("userId", this.me)
			.query(Integer.class).single()).isOne();
	}

	@Test
	void stageAndResultGoOnlyWithAppliedAndTogether() {
		assertInvalid(setStatus(this.posting, "{ \"decision\": \"applied\" }"));
		assertInvalid(setStatus(this.posting, "{ \"decision\": \"applied\", \"stage\": \"test\" }"));
		assertInvalid(setStatus(this.posting, "{ \"decision\": \"hold\", \"stage\": \"test\", \"result\": \"passed\" }"));
		assertInvalid(setStatus(this.posting, "{ \"decision\": \"maybe\" }"));
		assertInvalid(setStatus(this.posting, "{}"));
	}

	@Test
	void aJobAlreadyInMyJobsCanChangeEvenAfterItLeftTheGroup() {
		this.fixtures.status(this.me, this.posting, "UNDECIDED", null, null);
		this.fixtures.takeDown(this.group, this.posting);

		assertThat(setStatus(this.posting, "{ \"decision\": \"hold\" }")).hasStatusOk();
	}

	@Test
	void jobOutsideMyGroupsAndMyJobsIs404() {
		long othersGroup = this.fixtures.group("남의 그룹", this.fixtures.user("가람"));
		long othersJob = this.fixtures.posting("예시 회사", "남의 공고", "ALWAYS", null);
		this.fixtures.post(othersGroup, othersJob, this.me, NOW);

		assertThat(setStatus(othersJob, "{ \"decision\": \"skip\" }")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POSTING_NOT_FOUND");
		assertThat(setStatus(Long.MAX_VALUE, "{ \"decision\": \"skip\" }")).hasStatus(HttpStatus.NOT_FOUND);
	}

	// ── 나만 보는 메모 ─────────────────────────────────────────────────

	@Test
	void memoIsSavedAndClearedWithoutTouchingTheStatus() {
		setStatus(this.posting, "{ \"decision\": \"applied\", \"stage\": \"test\", \"result\": \"in_progress\" }").exchange();

		assertThat(setMemo(this.posting, "{ \"memo\": \"필기는 NCS와 전산 전공\" }")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(detail()).bodyJson().isLenientlyEqualTo("""
				{ "posting": { "memo": "필기는 NCS와 전산 전공", "decision": "applied", "stage": "test", "status": "필기 단계" } }
				""");

		// 상태를 바꿔도 메모는 남는다
		setStatus(this.posting, "{ \"decision\": \"hold\" }").exchange();
		assertThat(detail()).bodyJson().isLenientlyEqualTo("""
				{ "posting": { "memo": "필기는 NCS와 전산 전공", "decision": "hold", "stage": null, "status": null } }
				""");

		// 빈 메모는 지운다. 상태는 그대로다
		assertThat(setMemo(this.posting, "{ \"memo\": \"  \\n \" }")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(detail()).bodyJson().isLenientlyEqualTo("""
				{ "posting": { "memo": null, "decision": "hold" } }
				""");
		assertThat(setMemo(this.posting, "{ \"memo\": \"다시\" }")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(setMemo(this.posting, "{ \"memo\": null }")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(detail()).bodyJson().extractingPath("$.posting.memo").isNull();
	}

	@Test
	void memoIsAtMost2000CharactersLikeTheColumn() {
		this.fixtures.status(this.me, this.posting, "UNDECIDED", null, null);
		String longest = "가".repeat(2000);

		assertThat(setMemo(this.posting, "{ \"memo\": \"%s\" }".formatted(longest))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(detail()).bodyJson().extractingPath("$.posting.memo").isEqualTo(longest);
		assertInvalid(setMemo(this.posting, "{ \"memo\": \"%s\" }".formatted(longest + "가")));
		assertThat(detail()).bodyJson().extractingPath("$.posting.memo").isEqualTo(longest);
	}

	@Test
	void memoNeedsTheJobInMyJobs() {
		// 내 그룹에 있어도 내 공고 행이 없으면 404 다(상태 바꾸기는 행을 만들지만 메모는 만들지 않는다)
		assertThat(setMemo(this.posting, "{ \"memo\": \"메모\" }")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POSTING_NOT_FOUND");

		long other = this.fixtures.user("가람");
		this.fixtures.status(other, this.posting, "UNDECIDED", null, null);
		assertThat(setMemo(this.posting, "{ \"memo\": \"남의 행\" }")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(setMemo(Long.MAX_VALUE, "{ \"memo\": \"메모\" }")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.jdbc.sql("select memo is null from user_postings where user_id = :userId and posting_id = :postingId")
			.param("userId", other).param("postingId", this.posting).query(Boolean.class).single()).isTrue();
	}

	private MockMvcTester.MockMvcRequestBuilder setMemo(long postingId, String body) {
		return this.mvc.put().uri("/api/me/postings/{postingId}/memo", postingId).with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder detail() {
		return this.mvc.get().uri("/api/me/postings/{postingId}", this.posting).with(loginAs(this.me));
	}

	private MockMvcTester.MockMvcRequestBuilder setStatus(long postingId, String body) {
		return this.mvc.put().uri("/api/me/postings/{postingId}", postingId).with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

}
