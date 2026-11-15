package com.readme.grouppost;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import com.readme.posting.PostingKeys;

/**
 * 올리기, 그룹에서 내리기, 날짜 넣기(설계 4.2, 가정 2·3·4·5).
 *
 * <p>공고키는 DB 전체에서 유일하므로, 테스트마다 다른 공고번호와 회사명을 쓴다.
 */
@IntegrationTest
class GroupPostUploadTests {

	/** 날짜 넣기의 본문(올리기의 deadline 과 같은 꼴). 응답도 같은 꼴이다. */
	private static final String DATE_20 = "{ \"kind\": \"date\", \"date\": \"2026-11-20\" }";

	private static final String ALWAYS = "{ \"kind\": \"always\" }";

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
				    "sections": [], "keywords": [], "analysis": null, "comments": []
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
	void hashRoutedJobsOfOneSiteAreDifferentJobs() {
		// 해시(#/)로 화면을 바꾸는 채용 사이트. # 뒤가 다르면 다른 공고다
		String site = "https://careers-%d.example.co.kr/#/jobs/".formatted(jobNumber());

		assertThat(upload(this.group, this.me, job("해시 주소 회사", site + "101"))).hasStatus(HttpStatus.CREATED);
		assertThat(upload(this.group, this.me, job("해시 주소 회사", site + "102"))).hasStatus(HttpStatus.CREATED);
		// 같은 화면의 주소는 끝의 / 나 추적값이 달라도 같은 공고다
		assertThat(upload(this.group, this.other, job("해시 주소 회사", site + "101/?utm_source=x")))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("ALREADY_IN_GROUP");
	}

	@Test
	void aLinkTooLongForAKeyMakesANewJobEachTime() throws Exception {
		// 공고키 칸(300자)보다 긴 키가 나오는 주소는 키 없이 넣는다(엔티티로 넣는 유일한 길).
		// 같은 공고인지 알아볼 수 없으므로 같은 그룹에 다시 올려도 409 가 아니라 새 공고다.
		String url = "https://careers.example.com/jobs/" + "a".repeat(400);
		String body = """
				{ "company": "긴 주소 회사", "title": "백엔드", "deadline": { "kind": "date", "date": "2026-11-20" },
				  "deadlineName": "서류 마감", "applyAt": "홈페이지", "sourceUrl": "%s",
				  "keywords": [ "자바" ], "sections": [ { "title": "주요 업무", "items": [ "API 개발" ] } ] }
				""".formatted(url);

		MvcTestResult first = upload(this.group, this.me, body).exchange();
		MvcTestResult second = upload(this.group, this.other, body).exchange();

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(second).hasStatus(HttpStatus.CREATED);
		long firstId = postIdOf(first);
		assertThat(postIdOf(second)).isNotEqualTo(firstId);
		assertThat(this.jdbc.sql("select count(*) from postings where id in (:ids) and posting_key is null")
			.param("ids", List.of(firstId, postIdOf(second)))
			.query(Integer.class)
			.single()).isEqualTo(2);
		// 엔티티로 넣은 값도 다른 공고와 같은 응답으로 읽힌다
		assertThat(first).bodyJson().isLenientlyEqualTo("""
				{ "post": { "id": %d, "company": "긴 주소 회사", "title": "백엔드",
				  "deadline": { "kind": "date", "date": "2026-11-20" }, "deadlineName": "서류 마감", "applyAt": "홈페이지",
				  "sourceUrl": "%s", "postedBy": { "id": %d }, "postedAt": "2026-11-13" } }
				""".formatted(firstId, url, this.me));
		// 키 없이 넣은 공고에도 키워드와 공고 내용 묶음이 들어간다
		assertThat(first).bodyJson().extractingPath("$.post.keywords").isEqualTo(List.of("Java"));
		assertThat(first).bodyJson().extractingPath("$.post.sections").isEqualTo(
				List.of(Map.of("title", "주요 업무", "items", List.of("API 개발"))));
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
		// 역슬래시는 받지 않는다. 브라우저는 \ 를 / 로 읽어 evil.example 을 여는데, 공고키는 사람인 공고가 된다
		assertInvalid(upload(this.group, this.me, job("회사", "https://evil.example\\\\@www.saramin.co.kr/?rec_idx=" + jobNumber())));
		assertThat(upload(this.group, this.me, job("회사", url.replace("https", "HTTPS") + jobNumber())))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void uploadKeepsDictionaryKeywordsAndNonEmptySectionsAndTheDetailShowsThem() throws Exception {
		// 칸 확인 화면이 보내는 묶음에는 from 이 붙어 있다. 서버는 버린다
		MvcTestResult result = upload(this.group, this.me, """
				{ "company": "키워드 회사", "title": "백엔드", "deadline": { "kind": "always" },
				  "sourceUrl": "https://www.wanted.co.kr/wd/%d",
				  "keywords": [ "스프링부트", " java ", "Java", "", "  ", "사전에 없는 낱말", "k8s" ],
				  "sections": [
				    { "title": " 주요 업무 ", "items": [ " 주문 API 개발 ", "", "  ", "운영" ], "from": "담당 업무" },
				    { "title": "우대 사항", "items": [ " ", "" ], "from": "우대" },
				    { "title": "필수 요건", "items": [ "Java 3년" ] }
				  ] }
				""".formatted(jobNumber())).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{ "post": {
				  "keywords": [ "Spring Boot", "Java", "Kubernetes" ],
				  "sections": [
				    { "title": "주요 업무", "items": [ "주문 API 개발", "운영" ] },
				    { "title": "필수 요건", "items": [ "Java 3년" ] }
				  ] } }
				""");
		assertThat(result).bodyJson().extractingPath("$.post.sections[0]").isEqualTo(
				Map.of("title", "주요 업무", "items", List.of("주문 API 개발", "운영")));
		assertThat(this.jdbc.sql("select keyword || ':' || seq from posting_keywords where posting_id = :id order by seq")
			.param("id", postIdOf(result)).query(String.class).list())
			.containsExactly("Spring Boot:1", "Java:2", "Kubernetes:3");
	}

	@Test
	void uploadChecksTheKeywordsAndSections() {
		String job = """
				{ "company": "회사", "title": "공고", "deadline": { "kind": "always" }, %s }
				""";
		String keywords101 = "\"keywords\": [" + "\"Java\",".repeat(100) + "\"Java\"]";
		String sections101 = "\"sections\": [" + "{ \"title\": \"묶음\", \"items\": [\"항목\"] },".repeat(100)
				+ "{ \"title\": \"묶음\", \"items\": [\"항목\"] }]";
		assertInvalid(upload(this.group, this.me, job.formatted(keywords101)));
		assertInvalid(upload(this.group, this.me, job.formatted("\"keywords\": [\"%s\"]".formatted("a".repeat(101)))));
		assertInvalid(upload(this.group, this.me, job.formatted(sections101)));
		assertInvalid(upload(this.group, this.me, job.formatted("\"sections\": [{ \"title\": \" \", \"items\": [\"항목\"] }]")));
		assertInvalid(upload(this.group, this.me,
				job.formatted("\"sections\": [{ \"title\": \"%s\", \"items\": [\"항목\"] }]".formatted("가".repeat(51)))));
		// 묶음의 이름과 항목을 모두 더해 50,000자까지다(읽어 오기가 받는 본문의 한도)
		String atLimit = "\"sections\": [{ \"title\": \"가\", \"items\": [\"%s\", \"%s\"] }]"
			.formatted("나".repeat(25_000), "다".repeat(24_999));
		assertThat(upload(this.group, this.me, """
				{ "company": "한도 회사 %s", "title": "공고", "deadline": { "kind": "always" }, %s }
				""".formatted(UUID.randomUUID(), atLimit))).hasStatus(HttpStatus.CREATED);
		assertInvalid(upload(this.group, this.me, job.formatted(atLimit.replace("\"가\"", "\"가가\""))));
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

		assertThat(fillDeadline(postId, this.other, DATE_20)).hasStatusOk().bodyJson().isStrictlyEqualTo(DATE_20);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", otherGroup, postId).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.post.deadline").isEqualTo(Map.of("kind", "date", "date", "2026-11-20"));
		assertThat(this.jdbc.sql("select deadline_set_by from postings where id = :id").param("id", postId)
			.query(Long.class).single()).isEqualTo(this.other);
		// 한 번뿐이다
		assertThat(fillDeadline(postId, this.leader, ALWAYS)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
	}

	@Test
	void anUnknownDeadlineCanBecomeAlwaysOpenOnce() throws Exception {
		long postId = postIdOf(upload(this.group, this.me, """
				{ "company": "상시인지 모를 회사 %s", "title": "공고", "deadline": { "kind": "unknown" } }
				""".formatted(UUID.randomUUID())).exchange());

		assertThat(fillDeadline(postId, this.other, ALWAYS)).hasStatusOk().bodyJson().isStrictlyEqualTo(ALWAYS);

		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts", this.group).with(loginAs(this.me)))
			.bodyJson().extractingPath("$.posts[0].deadline").isEqualTo(Map.of("kind", "always"));
		assertThat(this.jdbc.sql("select deadline_set_by from postings where id = :id").param("id", postId)
			.query(Long.class).single()).isEqualTo(this.other);
		assertThat(fillDeadline(postId, this.leader, DATE_20)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
	}

	@Test
	void datedOrAlwaysJobsTakeNoDeadline() throws Exception {
		long always = postIdOf(upload(this.group, this.me, """
				{ "company": "상시 회사 %s", "title": "공고", "deadline": { "kind": "always" } }
				""".formatted(UUID.randomUUID())).exchange());
		long dated = postIdOf(upload(this.group, this.me, """
				{ "company": "날짜 회사 %s", "title": "공고", "deadline": { "kind": "date", "date": "2026-11-16" } }
				""".formatted(UUID.randomUUID())).exchange());

		assertThat(fillDeadline(always, this.me, DATE_20)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
		assertThat(fillDeadline(dated, this.me, ALWAYS)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("DEADLINE_ALREADY_SET");
	}

	@Test
	void fillingTheDateOfALinklessJobMakesItTheSameJobAsThatDate() throws Exception {
		String company = "링크 없는 회사 " + UUID.randomUUID();
		long postId = postIdOf(upload(this.group, this.me, linkless(company, null)).exchange());

		assertThat(fillDeadline(postId, this.other, DATE_20)).hasStatusOk();

		// 회사명·공고명·마감일이 같으므로 같은 공고다(공고키를 새 날짜로 다시 만든다)
		assertThat(upload(this.group, this.leader, linkless(company, "2026-11-20"))).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.postId").isEqualTo((int) postId);
	}

	@Test
	void fillingKeepsTheOldKeyWhenAnotherJobAlreadyHasTheNewOne() throws Exception {
		String company = "같은 날짜가 이미 있는 회사 " + UUID.randomUUID();
		long unknown = postIdOf(upload(this.group, this.me, linkless(company, null)).exchange());
		long dated = postIdOf(upload(this.group, this.me, linkless(company, "2026-11-20")).exchange());

		assertThat(fillDeadline(unknown, this.me, DATE_20)).hasStatusOk();

		assertThat(dated).isNotEqualTo(unknown);
		assertThat(this.jdbc.sql("select posting_key from postings where id = :id").param("id", unknown)
			.query(String.class).single()).isEqualTo(PostingKeys.derive(null, company, "공고", "").key());
	}

	@Test
	void theDeadlineBodyIsADateOrAlwaysInTheUploadShape() throws Exception {
		long postId = postIdOf(upload(this.group, this.me, """
				{ "company": "모양 확인 회사 %s", "title": "공고", "deadline": { "kind": "unknown" } }
				""".formatted(UUID.randomUUID())).exchange());

		for (String body : new String[] { "{}", "{ \"date\": \"2026-11-20\" }", "{ \"kind\": \"unknown\" }",
				"{ \"kind\": \"date\" }", "{ \"kind\": \"always\", \"date\": \"2026-11-20\" }", "{ \"kind\": \"someday\" }" }) {
			assertInvalid(fillDeadline(postId, this.me, body));
		}
		// 잘못된 요청은 아무것도 바꾸지 않았다
		assertThat(fillDeadline(postId, this.me, DATE_20)).hasStatusOk();
	}

	private MockMvcTester.MockMvcRequestBuilder upload(long groupId, long userId, String body) {
		return this.mvc.post().uri("/api/groups/{groupId}/posts", groupId).with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder takeDown(long postId, long userId) {
		return this.mvc.delete().uri("/api/groups/{groupId}/posts/{postId}", this.group, postId)
			.with(loginAs(userId)).with(xsrf());
	}

	private MockMvcTester.MockMvcRequestBuilder fillDeadline(long postId, long userId, String body) {
		return this.mvc.put().uri("/api/groups/{groupId}/posts/{postId}/deadline", this.group, postId)
			.with(loginAs(userId)).with(xsrf())
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

	/** 링크 없는 공고. date 가 null 이면 마감 모름. */
	private static String linkless(String company, String date) {
		String deadline = (date != null) ? "{ \"kind\": \"date\", \"date\": \"%s\" }".formatted(date) : "{ \"kind\": \"unknown\" }";
		return """
				{ "company": "%s", "title": "공고", "deadline": %s }
				""".formatted(company, deadline);
	}

	private static long jobNumber() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
	}

	private static long postIdOf(MvcTestResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.post.id")).longValue();
	}

}
