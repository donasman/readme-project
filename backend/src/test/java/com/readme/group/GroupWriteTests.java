package com.readme.group;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
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
 * 그룹 만들기, 공개 범위 바꾸기, 나가기(설계 4.1).
 */
@IntegrationTest
class GroupWriteTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	// ── 그룹 만들기 ──────────────────────────────────────────────────────

	@Test
	void creatorBecomesTheLeaderAndTheGroupComesBackLikeTheList() throws Exception {
		long me = this.fixtures.user("다온");

		MvcTestResult result = create(me, "{ \"name\": \"  백엔드 스터디  \", \"mark\": \"BE\" }").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long groupId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d, "name": "백엔드 스터디", "mark": "BE",
				  "members": [ { "id": %d, "name": "다온" } ],
				  "leaderId": %d, "openPostCount": 0, "myDisclosure": "applied"
				}
				""".formatted(groupId, me, me));
		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).bodyJson()
			.extractingPath("$[*].id").asArray().extracting((id) -> ((Number) id).longValue()).containsExactly(groupId);
	}

	@Test
	void blankMarkUsesTheFirstCharacterOfTheName() {
		assertThat(create(this.fixtures.user("다온"), "{ \"name\": \"공기업 전산직\", \"mark\": \" \" }"))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.mark").isEqualTo("공");
		assertThat(create(this.fixtures.user("다온"), "{ \"name\": \"MES 준비반\" }"))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.mark").isEqualTo("M");
	}

	@Test
	void nameIsRequiredAndBothFitTheTable() {
		long me = this.fixtures.user("다온");

		assertInvalid(create(me, "{ \"name\": \"  \" }"));
		assertInvalid(create(me, "{ \"name\": \"%s\" }".formatted("가".repeat(51))));
		assertInvalid(create(me, "{ \"name\": \"그룹\", \"mark\": \"다섯글자임\" }"));
		assertThat(create(me, "{ \"name\": \"%s\" }".formatted("가".repeat(50)))).hasStatus(HttpStatus.CREATED);
	}

	// ── 공개 범위 ──────────────────────────────────────────────────────

	@Test
	void changingMyDisclosureChangesWhatOthersSee() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("백엔드 스터디", leader, me);
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(group, posting, leader, NOW);
		this.fixtures.interest(group, posting, me);
		this.fixtures.status(me, posting, "APPLIED", "TEST", "IN_PROGRESS");

		assertThat(changeDisclosure(group, me, "progress")).hasStatusOk()
			.bodyJson().extractingPath("$.myDisclosure").isEqualTo("progress");
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", group).with(loginAs(leader))).bodyJson()
			.isLenientlyEqualTo("""
					{ "posts": [ { "interests": [ { "member": { "id": %d }, "applied": true, "progress": "필기 단계" } ] } ] }
					""".formatted(me));

		assertThat(changeDisclosure(group, me, "interest")).hasStatusOk()
			.bodyJson().extractingPath("$.myDisclosure").isEqualTo("interest");
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", group).with(loginAs(leader))).bodyJson()
			.extractingPath("$.posts[0].interests[0]").isEqualTo(Map.of(
					"member", Map.of("id", (int) me, "name", "다온"), "applied", false));
	}

	@Test
	void disclosureMustBeOneOfTheThreeValues() {
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("그룹", me);

		assertInvalid(changeDisclosure(group, me, "everything"));
		assertInvalid(this.mvc.put().uri("/api/groups/{groupId}/me/disclosure", group).with(loginAs(me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"));
	}

	@Test
	void nonMemberCannotChangeDisclosure() {
		long group = this.fixtures.group("그룹", this.fixtures.user("가람"));

		assertThat(changeDisclosure(group, this.fixtures.user("남"), "progress")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	// ── 나가기 ────────────────────────────────────────────────────────

	@Test
	void leavingRemovesMyInterestsHereButKeepsMyCommentsAndMyJobs() {
		long leader = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long group = this.fixtures.group("백엔드 스터디", leader, me);
		long posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(group, posting, me, NOW);
		this.fixtures.interest(group, posting, me);
		this.fixtures.status(me, posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");
		this.fixtures.comment(group, posting, me, "나가도 남는 댓글", NOW);

		assertThat(leave(group, me)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/groups").with(loginAs(me))).bodyJson().isStrictlyEqualTo("[]");
		assertThat(count("select count(*) from group_post_interests where user_id = " + me)).isZero();
		assertThat(count("select count(*) from user_postings where user_id = " + me)).isOne();
		// 남은 멤버에게 내 댓글과 내가 올린 공고는 그대로 보인다
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", group, posting).with(loginAs(leader)))
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "postedBy": { "id": %d }, "interests": [], "commentCount": 1,
					            "comments": [ { "author": { "id": %d, "name": "다온" } } ] } }
					""".formatted(me, me));
	}

	@Test
	void leaderCannotLeave() {
		long leader = this.fixtures.user("가람");
		long group = this.fixtures.group("그룹", leader, this.fixtures.user("다온"));

		assertThat(leave(group, leader)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("LEADER_CANNOT_LEAVE");
	}

	@Test
	void nonMemberCannotLeave() {
		long group = this.fixtures.group("그룹", this.fixtures.user("가람"));

		assertThat(leave(group, this.fixtures.user("남"))).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	private MockMvcTester.MockMvcRequestBuilder create(long userId, String body) {
		return this.mvc.post().uri("/api/groups").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder changeDisclosure(long groupId, long userId, String disclosure) {
		return this.mvc.put().uri("/api/groups/{groupId}/me/disclosure", groupId).with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{ \"disclosure\": \"%s\" }".formatted(disclosure));
	}

	private MockMvcTester.MockMvcRequestBuilder leave(long groupId, long userId) {
		return this.mvc.delete().uri("/api/groups/{groupId}/me", groupId).with(loginAs(userId)).with(xsrf());
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	private int count(String sql) {
		return this.jdbc.sql(sql).query(Integer.class).single();
	}

}
