package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
 * 올리기, 그룹에서 내리기, 날짜 넣기(설계 4.2, 가정 2·3·4·5).
 *
 * <p>공고키는 DB 전체에서 유일하므로, 테스트마다 다른 공고번호와 회사명을 쓴다.
 */
@IntegrationTest
class GroupPostUploadTests {

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

	@BeforeEach
	void group() {
		this.leader = this.fixtures.user("가람");
		this.me = this.fixtures.user("다온");
		this.other = this.fixtures.user("나래");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.me, this.other);
	}

	// ── 올리기 ────────────────────────────────────────────────────────

	@Test
	void uploadMakesTheJobAndAnswersLikeTheDetail() throws Exception {
		long number = jobNumber();
		String url = "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=%d&utm_source=x".formatted(number);

		MvcTestResult result = upload(this.group, this.me, """
				{ "company": "  예시 커머스 ", "title": "백엔드 개발자 신입",
				  "deadline": { "kind": "date", "date": "2026-11-16" },
				  "deadlineName": "서류 접수 마감", "applyAt": "  ", "sourceUrl": "%s" }
				""".formatted(url)).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long postId = postIdOf(result);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "today": "2026-11-13",
				  "post": {
				    "id": %d, "groupId": %d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
				    "deadline": { "kind": "date", "date": "2026-11-16" },
				    "postedBy": { "id": %d, "name": "다온" }, "postedAt": "2026-11-13",
				    "interests": [], "me": { "interested": false, "status": null }, "commentCount": 0,
				    "deadlineName": "서류 접수 마감", "applyAt": null, "sourceUrl": "%s",
				    "sections": [], "keywords": [], "comments": []
				  }
				}
				""".formatted(postId, this.group, this.me, url));
		// 같은 공고를 알아보는 키는 jobfit 공고키다(설계 가정 3)
		assertThat(this.jdbc.sql("select posting_key from postings where id = :id").param("id", postId)
			.query(String.class).single()).isEqualTo("saramin:" + number);
	}

	@Test
	void sameJobAgainInTheSameGroupIs409WithItsPostId() throws Exception {
		long number = jobNumber();
		long postId = postIdOf(upload(this.group, this.me, job("예시 커머스",
				"https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=" + number)).exchange());

		// 주소 꼴이 달라도(모바일 주소, 추적값) 같은 공고다
		assertThat(upload(this.group, this.other, job("예시 커머스",
				"https://m.saramin.co.kr/zf_user/jobs/view?rec_idx=%d&utm_medium=social".formatted(number))))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "ALREADY_IN_GROUP", "message": "이미 이 그룹에 올라온 공고입니다", "postId": %d }
					""".formatted(postId));
	}

	@Test
	void sameJobInAnotherGroupSharesTheJobBodyAndKeepsTheFirstValues() throws Exception {
		long otherGroup = this.fixtures.group("둘째 그룹", this.me);
		String url = "https://www.wanted.co.kr/wd/" + jobNumber();
		long first = postIdOf(upload(this.group, this.me, job("처음 넣은 회사명", url)).exchange());

		MvcTestResult second = upload(otherGroup, this.me, job("다르게 넣은 회사명", url)).exchange();

		assertThat(second).hasStatus(HttpStatus.CREATED);
		assertThat(postIdOf(second)).isEqualTo(first);
		assertThat(second).bodyJson().extractingPath("$.post.company").isEqualTo("처음 넣은 회사명");
	}

	@Test
	void withoutALinkTheSameCompanyTitleAndDeadlineIsTheSameJob() {
		String company = "링크 없는 회사 " + UUID.randomUUID();
		String unknown = """
				{ "company": "%s", "title": "서버 개발", "deadline": { "kind": "unknown" } }
				""".formatted(company);
		String dated = """
				{ "company": "%s", "title": "서버 개발", "deadline": { "kind": "date", "date": "2026-12-01" } }
				""".formatted(company);

		assertThat(upload(this.group, this.me, unknown)).hasStatus(HttpStatus.CREATED);
		assertThat(upload(this.group, this.other, unknown)).hasStatus(HttpStatus.CONFLICT);
		// 마감일이 다르면 다른 공고다
		assertThat(upload(this.group, this.other, dated)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void reuploadingATakenDownJobRevivesTheSameRowWithItsCommentsAndInterests() throws Exception {
		long postId = postIdOf(upload(this.group, this.leader, job("예시 회사", "https://www.wanted.co.kr/wd/" + jobNumber()))
			.exchange());
		this.fixtures.comment(this.group, postId, this.me, "내려도 남는 댓글", NOW);
		this.fixtures.interest(this.group, postId, this.me);
		this.fixtures.takeDown(this.group, postId);
		String url = this.jdbc.sql("select source_url from postings where id = :id").param("id", postId)
			.query(String.class).single();

		MvcTestResult again = upload(this.group, this.me, job("예시 회사", url)).exchange();

		assertThat(again).hasStatus(HttpStatus.CREATED);
		assertThat(postIdOf(again)).isEqualTo(postId);
		// 다시 올린 사람이 올린 사람이 된다
		assertThat(again).bodyJson().isLenientlyEqualTo("""
				{ "post": { "postedBy": { "id": %d }, "me": { "interested": true }, "commentCount": 1 } }
				""".formatted(this.me));
	}

	@Test
	void uploadChecksTheInput() {
		String url = "https://www.wanted.co.kr/wd/1";
		assertInvalid(upload(this.group, this.me, """
				{ "title": "공고", "deadline": { "kind": "unknown" } }
				"""));
		assertInvalid(upload(this.group, this.me, """
				{ "company": "%s", "title": "공고", "deadline": { "kind": "unknown" } }
				""".formatted("가".repeat(201))));
		assertInvalid(upload(this.group, this.me, """
				{ "company": "회사", "title": "공고" }
				"""));
		assertInvalid(upload(this.group, this.me, """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "someday" } }
				"""));
		// 날짜는 "date"일 때만, 꼭 있어야 한다
		assertInvalid(upload(this.group, this.me, """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "date" } }
				"""));
		assertInvalid(upload(this.group, this.me, """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "always", "date": "2026-11-20" } }
				"""));
		// 원문 주소는 http·https 만
		assertInvalid(upload(this.group, this.me, job("회사", "javascript:alert(1)")));
		assertInvalid(upload(this.group, this.me, job("회사", "ftp://example.com/job")));
		assertThat(upload(this.group, this.me, job("회사", url.replace("https", "HTTPS") + jobNumber())))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void nonMemberCannotUpload() {
		assertThat(upload(this.group, this.fixtures.user("남"), job("회사", "https://www.wanted.co.kr/wd/" + jobNumber())))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("GROUP_NOT_FOUND");
	}

	// ── 그룹에서 내리기 ──────────────────────────────────────────────────

	@Test
	void posterTakesDownAndTheJobLeavesTheGroup() throws Exception {
		long postId = postIdOf(upload(this.group, this.me, job("회사", "https://www.wanted.co.kr/wd/" + jobNumber())).exchange());

		assertThat(takeDown(postId, this.me)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", this.group).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.posts").asArray().isEmpty();
		assertThat(this.jdbc.sql("select taken_down_by from group_posts where group_id = :g and posting_id = :p")
			.param("g", this.group).param("p", postId).query(Long.class).single()).isEqualTo(this.me);
		// 두 번째는 이미 없는 공고다
		assertThat(takeDown(postId, this.me)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("POST_NOT_FOUND");
	}

	@Test
	void leaderTakesDownAnyonesJobButOtherMembersCannot() throws Exception {
		long postId = postIdOf(upload(this.group, this.me, job("회사", "https://www.wanted.co.kr/wd/" + jobNumber())).exchange());

		assertThat(takeDown(postId, this.other)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
		assertThat(takeDown(postId, this.leader)).hasStatus(HttpStatus.NO_CONTENT);
	}

	// ── 날짜 넣기 ─────────────────────────────────────────────────────

	@Test
	void anyMemberFillsAnUnknownDeadlineOnceAndOtherGroupsSeeIt() throws Exception {
		long otherGroup = this.fixtures.group("둘째 그룹", this.me);
		String body = """
				{ "company": "날짜 없는 회사 %s", "title": "공고", "deadline": { "kind": "unknown" } }
				""".formatted(UUID.randomUUID());
		long postId = postIdOf(upload(this.group, this.me, body).exchange());
		upload(otherGroup, this.me, body).exchange();

		assertThat(fillDeadline(postId, this.other, "2026-11-20")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "kind": "date", "date": "2026-11-20" }
				""");

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", otherGroup, postId).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.post.deadline").isEqualTo(Map.of("kind", "date", "date", "2026-11-20"));
		assertThat(this.jdbc.sql("select deadline_set_by from postings where id = :id").param("id", postId)
			.query(Long.class).single()).isEqualTo(this.other);
		// 한 번뿐이다
		assertThat(fillDeadline(postId, this.leader, "2026-11-21")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
	}

	@Test
	void datedOrAlwaysJobsTakeNoDate() throws Exception {
		long always = postIdOf(upload(this.group, this.me, """
				{ "company": "상시 회사 %s", "title": "공고", "deadline": { "kind": "always" } }
				""".formatted(UUID.randomUUID())).exchange());

		assertThat(fillDeadline(always, this.me, "2026-11-20")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
		assertInvalid(this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/deadline", this.group, always)
			.with(loginAs(this.me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{}"));
	}

	private MockMvcTester.MockMvcRequestBuilder upload(long groupId, long userId, String body) {
		return this.mvc.post().uri("/api/groups/{groupId}/posts", groupId).with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder takeDown(long postId, long userId) {
		return this.mvc.delete().uri("/api/groups/{groupId}/posts/{postId}", this.group, postId)
			.with(loginAs(userId)).with(xsrf());
	}

	private MockMvcTester.MockMvcRequestBuilder fillDeadline(long postId, long userId, String date) {
		return this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/deadline", this.group, postId)
			.with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content("{ \"date\": \"%s\" }".formatted(date));
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	private static String job(String company, String sourceUrl) {
		return """
				{ "company": "%s", "title": "백엔드", "deadline": { "kind": "always" }, "sourceUrl": "%s" }
				""".formatted(company, sourceUrl);
	}

	private static long jobNumber() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
	}

	private static long postIdOf(MvcTestResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.post.id")).longValue();
	}

}
