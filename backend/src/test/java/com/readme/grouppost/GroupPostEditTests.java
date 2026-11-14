package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.FixedClockConfiguration.TODAY;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

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
 * PUT /api/groups/{groupId}/posts/{postId}: 공고 고치기(설계 4.5, 9절의 "공고 고치기"를 2026-11-15 정함).
 */
@IntegrationTest
class GroupPostEditTests {

	private static final String EDIT = """
			{ "company": "  고친 커머스 ", "title": "고친 공고명", "deadline": { "kind": "date", "date": "2026-11-30" } }
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long leader;

	long poster;

	long other;

	long group;

	long posting;

	String key;

	@BeforeEach
	void postedByAMember() {
		this.leader = this.fixtures.user("가람");
		this.poster = this.fixtures.user("다온");
		this.other = this.fixtures.user("나래");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.poster, this.other);
		this.posting = this.fixtures.posting("예시 커머스", "백엔드", "DATE", TODAY.plusDays(7));
		this.fixtures.details(this.posting, "서류 접수 마감", "회사 채용 홈페이지", "https://example.com/jobs/1", "[]");
		this.key = "url:example.com/jobs/" + UUID.randomUUID();
		this.jdbc.sql("update postings set posting_key = :key where id = :id")
			.param("key", this.key)
			.param("id", this.posting)
			.update();
		this.fixtures.post(this.group, this.posting, this.poster, NOW);
	}

	@Test
	void posterEditsCompanyTitleAndDeadlineAndGetsTheDetail() {
		assertThat(edit(this.poster, EDIT)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{
				  "today": "2026-11-13",
				  "post": {
				    "id": %d, "groupId": %d, "company": "고친 커머스", "title": "고친 공고명",
				    "deadline": { "kind": "date", "date": "2026-11-30" },
				    "postedBy": { "id": %d, "name": "다온" },
				    "deadlineName": "서류 접수 마감", "applyAt": "회사 채용 홈페이지", "sourceUrl": "https://example.com/jobs/1"
				  }
				}
				""".formatted(this.posting, this.group, this.poster));
		// 원문 주소와 공고키는 그대로다
		assertThat(this.jdbc.sql("select posting_key from postings where id = :id").param("id", this.posting)
			.query(String.class).single()).isEqualTo(this.key);
	}

	@Test
	void posterMayChangeADeadlineThatIsAlreadySetButOthersStillCannot() {
		assertThat(edit(this.poster, """
				{ "company": "예시 커머스", "title": "백엔드", "deadline": { "kind": "always" } }
				""")).hasStatusOk().bodyJson().extractingPath("$.post.deadline").isEqualTo(Map.of("kind", "always"));

		// 다른 멤버의 날짜 넣기는 지금처럼 마감을 읽지 못한 공고에만 한 번이다
		assertThat(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/deadline", this.group, this.posting)
			.with(loginAs(this.other)).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"kind\": \"date\", \"date\": \"2026-12-01\" }"))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
	}

	@Test
	void theEditShowsInAnotherGroupThatSharesTheJob() {
		long otherLeader = this.fixtures.user("라온");
		long otherGroup = this.fixtures.group("둘째 그룹", otherLeader);
		this.fixtures.post(otherGroup, this.posting, otherLeader, NOW);

		edit(this.poster, EDIT).exchange();

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", otherGroup).with(loginAs(otherLeader)))
			.bodyJson().isLenientlyEqualTo("""
					{ "posts": [ { "id": %d, "company": "고친 커머스", "title": "고친 공고명",
					               "deadline": { "kind": "date", "date": "2026-11-30" } } ] }
					""".formatted(this.posting));
	}

	@Test
	void anotherMemberAndEvenTheLeaderGet403() {
		for (long member : new long[] { this.other, this.leader }) {
			assertThat(edit(member, EDIT)).hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
		}
		assertThat(this.jdbc.sql("select company from postings where id = :id").param("id", this.posting)
			.query(String.class).single()).isEqualTo("예시 커머스");
	}

	@Test
	void nonMemberGets404GroupNotFound() {
		long stranger = this.fixtures.user("모르는 사람");

		assertThat(edit(stranger, EDIT)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	@Test
	void takenDownPostIs404PostNotFound() {
		this.fixtures.takeDown(this.group, this.posting);

		assertThat(edit(this.poster, EDIT)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POST_NOT_FOUND");
	}

	@Test
	void editIsValidatedLikeTheUpload() {
		String date = "{ \"kind\": \"date\", \"date\": \"2026-11-30\" }";
		String[] invalid = {
				body("   ", "공고명", date),
				body("가".repeat(201), "공고명", date),
				body("회사", "", date),
				body("회사", "가".repeat(301), date),
				body("회사", "공고명", "{ \"kind\": \"unknown\" }"),
				body("회사", "공고명", "{ \"kind\": \"date\" }"),
				body("회사", "공고명", "{ \"kind\": \"always\", \"date\": \"2026-11-30\" }"),
				"{ \"company\": \"회사\", \"title\": \"공고명\" }" };
		for (String request : invalid) {
			assertThat(edit(this.poster, request)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		}
		// 길이는 테이블 칸과 같다: 회사명 200자, 공고명 300자
		assertThat(edit(this.poster, body("가".repeat(200), "가".repeat(300), date))).hasStatusOk();
	}

	private static String body(String company, String title, String deadline) {
		return "{ \"company\": \"%s\", \"title\": \"%s\", \"deadline\": %s }".formatted(company, title, deadline);
	}

	private MockMvcTester.MockMvcRequestBuilder edit(long userId, String body) {
		return this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}", this.group, this.posting)
			.with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
