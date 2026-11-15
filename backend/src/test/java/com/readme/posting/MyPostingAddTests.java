package com.readme.posting;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

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
 * POST /api/me/postings: 그룹 없이 내 공고에 넣기(설계 4.2, 화면·API 대응 문서 3절 11번).
 *
 * <p>공고키는 DB 전체에서 유일하므로, 테스트마다 다른 공고번호를 쓴다.
 */
@IntegrationTest
class MyPostingAddTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long me;

	@BeforeEach
	void me() {
		this.me = this.fixtures.user("다온");
	}

	@Test
	void addPutsANewJobInMyJobsUndecidedAndAnswersLikeTheDetail() throws Exception {
		long number = jobNumber();
		String url = "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=%d&utm_source=x".formatted(number);

		MvcTestResult result = add(this.me, """
				{ "company": "  예시 커머스 ", "title": "백엔드 개발자 신입",
				  "deadline": { "kind": "date", "date": "2026-11-16" },
				  "deadlineName": "서류 접수 마감", "applyAt": "  ", "sourceUrl": "%s" }
				""".formatted(url)).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long postingId = postingIdOf(result);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "today": "2026-11-13",
				  "posting": {
				    "id": %d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
				    "deadline": { "kind": "date", "date": "2026-11-16" },
				    "decision": "undecided", "stage": null, "result": null, "status": null, "groups": [],
				    "applyAt": null, "sourceUrl": "%s", "sections": [], "keywords": [], "memo": null, "groupStates": []
				  }
				}
				""".formatted(postingId, url));
		assertThat(this.mvc.get().uri("/api/me/postings").with(loginAs(this.me)))
			.bodyJson().extractingPath("$.postings[0].id").isEqualTo((int) postingId);
		// 같은 공고를 알아보는 키는 그룹에 올릴 때와 같은 공고키다(설계 가정 3)
		assertThat(this.jdbc.sql("select posting_key from postings where id = :id").param("id", postingId)
			.query(String.class).single()).isEqualTo("saramin:" + number);
	}

	@Test
	void aJobWithTheSameKeyGetsMyRowAndKeepsTheFirstValues() throws Exception {
		long other = this.fixtures.user("가람");
		long group = this.fixtures.group("백엔드 스터디", other, this.me);
		String url = "https://www.wanted.co.kr/wd/" + jobNumber();
		MvcTestResult uploaded = this.mvc.post().uri("/api/groups/{groupId}/posts", group).with(loginAs(other))
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(job("처음 넣은 회사명", url)).exchange();
		long postId = ((Number) JsonPath.read(uploaded.getResponse().getContentAsString(), "$.post.id")).longValue();

		MvcTestResult result = add(this.me, job("다르게 넣은 회사명", url + "?utm_source=x")).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(postingIdOf(result)).isEqualTo(postId);
		// 공고 본체의 값은 고치지 않고, 그 공고가 올라온 내 그룹이 함께 보인다
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{ "posting": { "company": "처음 넣은 회사명", "decision": "undecided",
				  "groups": [ { "id": %d, "postId": %d } ],
				  "groupStates": [ { "id": %d, "interested": false, "commentCount": 0 } ] } }
				""".formatted(group, postId, group));
		assertThat(this.jdbc.sql("select count(*) from postings where source_url like :url")
			.param("url", url + "%").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void aJobAlreadyInMyJobsIs409WithItsPostingIdAndKeepsMyRow() throws Exception {
		long number = jobNumber();
		long postingId = postingIdOf(add(this.me,
				job("예시 커머스", "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=" + number)).exchange());
		this.fixtures.status(this.me, postingId, "APPLIED", "TEST", "IN_PROGRESS");

		// 주소 꼴이 달라도(모바일 주소, 추적값) 같은 공고다
		assertThat(add(this.me, job("예시 커머스",
				"https://m.saramin.co.kr/zf_user/jobs/view?rec_idx=%d&utm_medium=social".formatted(number))))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "ALREADY_IN_MY_POSTINGS", "message": "이미 내 공고에 있는 공고입니다", "postingId": %d }
					""".formatted(postingId));
		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", postingId).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.posting.status").isEqualTo("필기 단계");
	}

	@Test
	void addChecksTheInputAndPutsNothingInMyJobs() {
		assertInvalid(add(this.me, """
				{ "title": "공고", "deadline": { "kind": "unknown" } }
				"""));
		assertInvalid(add(this.me, """
				{ "company": "%s", "title": "공고", "deadline": { "kind": "unknown" } }
				""".formatted("가".repeat(201))));
		assertInvalid(add(this.me, """
				{ "company": "회사", "title": "공고" }
				"""));
		// 날짜는 "date"일 때만, 꼭 있어야 한다
		assertInvalid(add(this.me, """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "date" } }
				"""));
		assertInvalid(add(this.me, """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "always", "date": "2026-11-20" } }
				"""));
		// 원문 주소는 http·https 만, 역슬래시 없이
		assertInvalid(add(this.me, job("회사", "javascript:alert(1)")));
		assertInvalid(add(this.me, job("회사", "https://evil.example\\\\@www.saramin.co.kr/?rec_idx=" + jobNumber())));

		assertThat(this.mvc.get().uri("/api/me/postings").with(loginAs(this.me)))
			.bodyJson().extractingPath("$.postings").asArray().isEmpty();
	}

	@Test
	void addNeedsLogin() {
		assertThat(this.mvc.post().uri("/api/me/postings").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content(job("회사", "https://www.wanted.co.kr/wd/" + jobNumber())))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
	}

	private MockMvcTester.MockMvcRequestBuilder add(long userId, String body) {
		return this.mvc.post().uri("/api/me/postings").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
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

	private static long postingIdOf(MvcTestResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.posting.id")).longValue();
	}

}
