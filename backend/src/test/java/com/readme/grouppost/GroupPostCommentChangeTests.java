package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
 * 댓글 수정·삭제(설계 5.1: 수정은 쓴 사람, 삭제는 쓴 사람과 그룹장).
 */
@IntegrationTest
class GroupPostCommentChangeTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long leader;

	long me;

	long other;

	long group;

	long posting;

	long comment;

	@BeforeEach
	void jobWithMyComment() {
		this.leader = this.fixtures.user("가람");
		this.me = this.fixtures.user("다온");
		this.other = this.fixtures.user("나래");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.me, this.other);
		this.posting = this.fixtures.posting("예시 커머스", "백엔드", "ALWAYS", null);
		this.fixtures.post(this.group, this.posting, this.leader, NOW);
		this.comment = this.fixtures.comment(this.group, this.posting, this.me, "처음 쓴 글",
				Instant.parse("2026-11-10T03:00:00Z"));
	}

	@Test
	void authorEditsTheComment() {
		assertThat(edit(this.comment, this.me, "  고친 글 https://example.com/a  ")).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("""
					{
					  "id": %d, "author": { "id": %d, "name": "다온" }, "createdAt": "2026-11-10T12:00:00+09:00",
					  "body": [ { "text": "고친 글 " }, { "text": "https://example.com/a", "href": "https://example.com/a" } ]
					}
					""".formatted(this.comment, this.me));
		assertThat(this.jdbc.sql("select body from group_post_comments where id = :id").param("id", this.comment)
			.query(String.class).single()).isEqualTo("고친 글 https://example.com/a");
	}

	@Test
	void othersAndEvenTheLeaderCannotEdit() {
		assertThat(edit(this.comment, this.other, "남의 글")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
		assertThat(edit(this.comment, this.leader, "그룹장도 못 고친다")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void editNeedsText() {
		assertThat(edit(this.comment, this.me, "   ")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	@Test
	void authorAndLeaderDeleteButOtherMembersCannot() {
		long others = this.fixtures.comment(this.group, this.posting, this.other, "나래의 글", NOW);

		assertThat(delete(this.comment, this.other)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(this.comment, this.me)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(delete(others, this.leader)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.group, this.posting)
			.with(loginAs(this.me)))
			.bodyJson().isLenientlyEqualTo("""
					{ "post": { "commentCount": 0, "comments": [] } }
					""");
	}

	@Test
	void commentOfAnotherJobOrUnknownIs404() {
		long anotherPosting = this.fixtures.posting("예시 회사", "다른 공고", "ALWAYS", null);
		this.fixtures.post(this.group, anotherPosting, this.leader, NOW);

		assertThat(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/comments/{commentId}", this.group,
				anotherPosting, this.comment)
			.with(loginAs(this.me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"글\" }"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("COMMENT_NOT_FOUND");
		assertThat(delete(Long.MAX_VALUE, this.me)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("COMMENT_NOT_FOUND");
	}

	@Test
	void commentsOfATakenDownJobAre404() {
		this.fixtures.takeDown(this.group, this.posting);

		assertThat(edit(this.comment, this.me, "고친 글")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POST_NOT_FOUND");
		assertThat(delete(this.comment, this.me)).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MockMvcTester.MockMvcRequestBuilder edit(long commentId, long userId, String text) {
		return this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/comments/{commentId}", this.group, this.posting,
				commentId)
			.with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"%s\" }".formatted(text));
	}

	private MockMvcTester.MockMvcRequestBuilder delete(long commentId, long userId) {
		return this.mvc.delete().uri("/api/groups/{groupId}/posts/{postId}/comments/{commentId}", this.group,
				this.posting, commentId)
			.with(loginAs(userId)).with(xsrf());
	}

}
