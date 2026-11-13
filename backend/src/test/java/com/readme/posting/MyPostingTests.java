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
 * PUT /api/me/postings/{postingId}: 내 지원 상태(설계 4.3, 5.3).
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

	private MockMvcTester.MockMvcRequestBuilder setStatus(long postingId, String body) {
		return this.mvc.put().uri("/api/me/postings/{postingId}", postingId).with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

}
