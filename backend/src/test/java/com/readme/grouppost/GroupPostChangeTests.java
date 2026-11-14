package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
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

/**
 * PUT …/interest, POST …/comments.
 */
@IntegrationTest
class GroupPostChangeTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long leader;

	long me;

	long group;

	long posting;

	@BeforeEach
	void groupWithOneJob() {
		this.leader = this.fixtures.user("가람");
		this.me = this.fixtures.user("다온");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.me);
		this.posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(this.group, this.posting, this.leader, NOW);
	}

	// ── 관심 ───────────────────────────────────────────────────────────

	@Test
	void turningInterestOnAddsMeToTheMembersAndToMyJobs() {
		assertThat(setInterest(true)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": true, "status": null }
				""");

		// 내 공고 행이 미정으로 생긴다
		assertThat(decision()).isEqualTo("UNDECIDED");
		// 다른 멤버에게는 내가 관심 멤버로 보인다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", this.group).with(loginAs(this.leader)))
			.bodyJson().isLenientlyEqualTo("""
					{ "posts": [ { "interests": [ { "member": { "id": %d, "name": "다온" }, "applied": false } ] } ] }
					""".formatted(this.me));
	}

	@Test
	void sameRequestTwiceGivesTheSameResult() {
		setInterest(true).exchange();

		assertThat(setInterest(true)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": true, "status": null }
				""");
		assertThat(interestRows()).isEqualTo(1);
	}

	@Test
	void turningInterestOffKeepsMyJobRow() {
		setInterest(true).exchange();

		assertThat(setInterest(false)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": false, "status": null }
				""");
		assertThat(interestRows()).isZero();
		assertThat(decision()).isEqualTo("UNDECIDED");
	}

	@Test
	void turningInterestOnKeepsAnExistingStatus() {
		this.fixtures.status(this.me, this.posting, "APPLIED", "TEST", "IN_PROGRESS");

		assertThat(setInterest(true)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": true, "status": "필기 단계" }
				""");
		assertThat(setInterest(false)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": false, "status": "필기 단계" }
				""");
	}

	@Test
	void interestCanBeTurnedOffAfterApplyingAndTheJobStaysInMyJobs() {
		// 설계 9절 "지원한 뒤 관심 끄기": 상세에 끄기를 둔다(2026-11-15 정함)
		this.fixtures.interest(this.group, this.posting, this.me);
		this.fixtures.status(this.me, this.posting, "APPLIED", "INTERVIEW_1", "IN_PROGRESS");

		assertThat(setInterest(false)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "interested": false, "status": "1차 면접 단계" }
				""");
		assertThat(interestRows()).isZero();
		// 내 공고 행과 지원 상태는 그대로다
		assertThat(this.jdbc.sql("""
				select decision || '/' || stage || '/' || result from user_postings
				 where user_id = :userId and posting_id = :postingId
				""").param("userId", this.me).param("postingId", this.posting).query(String.class).single())
			.isEqualTo("APPLIED/INTERVIEW_1/IN_PROGRESS");
		// 상태를 바꾸는 길도 그대로 열려 있다
		assertThat(this.mvc.put().uri("/api/me/postings/{postingId}", this.posting).with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"decision\": \"applied\", \"stage\": \"interview_1\", \"result\": \"passed\" }"))
			.hasStatusOk();
	}

	@Test
	void withInterestOffIAmNotAnInterestedMemberEvenWithAStatus() {
		// 설계 9절: 다른 그룹에서 관심을 켜고 지원한 공고가 이 그룹에도 있다. 이 그룹의 관심은 꺼져 있다.
		long otherGroup = this.fixtures.group("둘째 그룹", this.me);
		this.fixtures.post(otherGroup, this.posting, this.me, NOW);
		this.fixtures.interest(otherGroup, this.posting, this.me);
		this.fixtures.status(this.me, this.posting, "APPLIED", "TEST", "IN_PROGRESS");
		this.fixtures.disclosure(this.group, this.me, "PROGRESS");
		String mine = """
				{ "interests": [], "me": { "interested": false, "status": "필기 단계" } }
				""";

		// 내 화면: 관심 멤버에 나는 없고, 내 상태 알약(me.status)은 보인다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", this.group).with(loginAs(this.me)))
			.bodyJson().isLenientlyEqualTo("{ \"posts\": [ %s ] }".formatted(mine));
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.group, this.posting)
			.with(loginAs(this.me)))
			.bodyJson().isLenientlyEqualTo("{ \"post\": %s }".formatted(mine));
		// 다른 멤버의 화면에도 없다. 두 화면이 같다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.group, this.posting)
			.with(loginAs(this.leader)))
			.bodyJson().isLenientlyEqualTo("{ \"post\": { \"interests\": [] } }");
	}

	@Test
	void interestNeedsTrueOrFalse() {
		assertThat(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/interest", this.group, this.posting)
			.with(loginAs(this.me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	// ── 댓글 ───────────────────────────────────────────────────────────

	@Test
	void commentIsStoredAsTypedAndAnsweredWithLinksSplit() throws Exception {
		MvcTestResult result = addComment("  후기 https://example.com/review 참고, javascript:alert(1) 는 글자  ").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d, "author": { "id": %d, "name": "다온" }, "createdAt": "2026-11-13T00:30:00+09:00",
				  "body": [
				    { "text": "후기 " },
				    { "text": "https://example.com/review", "href": "https://example.com/review" },
				    { "text": " 참고, javascript:alert(1) 는 글자" }
				  ]
				}
				""".formatted(id, this.me));
		// 앞뒤 공백만 지우고 글자 그대로 둔다
		assertThat(this.jdbc.sql("select body from group_post_comments where id = :id").param("id", id).query(String.class)
			.single()).isEqualTo("후기 https://example.com/review 참고, javascript:alert(1) 는 글자");
		// 상세와 목록에 바로 보인다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.group, this.posting)
			.with(loginAs(this.leader)))
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "commentCount": 1, "comments": [ { "id": %d } ] } }
					""".formatted(id));
	}

	@Test
	void commentMustHaveTextAndAtMost2000Characters() {
		assertThat(addComment("   ")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		assertThat(addComment("가".repeat(2001))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		assertThat(addComment("가".repeat(2000))).hasStatus(HttpStatus.CREATED);
	}

	private MockMvcTester.MockMvcRequestBuilder setInterest(boolean interested) {
		return this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/interest", this.group, this.posting)
			.with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"interested\": %s }".formatted(interested));
	}

	private MockMvcTester.MockMvcRequestBuilder addComment(String text) {
		return this.mvc.post().uri("/api/groups/{groupId}/posts/{postId}/comments", this.group, this.posting)
			.with(loginAs(this.me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"text\": \"%s\" }".formatted(text));
	}

	private int interestRows() {
		return this.jdbc.sql("""
				select count(*) from group_post_interests
				 where group_id = :groupId and posting_id = :postingId and user_id = :userId
				""")
			.param("groupId", this.group)
			.param("postingId", this.posting)
			.param("userId", this.me)
			.query(Integer.class)
			.single();
	}

	private String decision() {
		return this.jdbc.sql("select decision from user_postings where user_id = :userId and posting_id = :postingId")
			.param("userId", this.me)
			.param("postingId", this.posting)
			.query(String.class)
			.optional()
			.orElse(null);
	}

}
