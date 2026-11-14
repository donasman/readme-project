package com.readme.postingread;

import static com.readme.postingread.HtmlTextTests.resource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.posting.DeadlineKind;
import com.readme.postingread.Deadlines.Read;
import com.readme.postingread.PostingMeta.Meta;

/**
 * 공고 페이지에서 회사명·직무·마감일 읽기. jobfit 의 tests/test_meta.py 를 옮겼고(위쪽 넷), test_company.py 에서 채용 플랫폼
 * 페이지를 보는 하나를 더했다. 아래쪽 값은 jobfit 의 meta.py 를 python -I 로 직접 돌려 얻었다.
 *
 * <p>옮기지 않은 것: test_company.py 의 그리팅·나인하이어·리크루터(채용 솔루션 페이지에 박힌 데이터 읽기, company.py).
 * 리드미는 이 단계를 아직 옮기지 않았다(공고 설계 10절).
 */
class PostingMetaTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

	// ── test_meta.py ───────────────────────────────────────────────────

	@Test
	void jobkoreaJsonLd() {
		Meta meta = PostingMeta.read(resource("jobkorea-sample.html"), TODAY);
		assertThat(meta.source()).isEqualTo("json-ld");
		assertThat(meta.company()).isEqualTo("예시컴퍼니");
		assertThat(meta.title()).isEqualTo("Node.js개발자(신입)");
		assertThat(meta.deadline()).isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 11, 15)));
	}

	@Test
	void saraminTitleAndSummaryDeadline() {
		Meta meta = PostingMeta.read(resource("saramin-sample.html"), TODAY);
		assertThat(meta.source()).isEqualTo("title");
		assertThat(meta.company()).isEqualTo("예시랩");
		assertThat(meta.title()).isEqualTo("백엔드 (Backend) 개발자"); // 폭 없는 공백·(D-7)·사이트 이름이 빠진다
		assertThat(meta.deadline()).isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 10, 27)));
	}

	@Test
	void unknownSiteTitleIsNotGuessed() {
		Meta meta = PostingMeta.read("<html><head><title>Backend Engineer - Acme Careers</title></head><body>hi</body></html>", TODAY);
		assertThat(meta.company()).isEmpty();
		assertThat(meta.title()).isEmpty();
		assertThat(meta.source()).isEmpty();
	}

	@Test
	void nameCleaning() {
		assertThat(PostingMeta.cleanCompany("㈜예시컴퍼니(example)")).isEqualTo("예시컴퍼니");
		assertThat(PostingMeta.cleanCompany("(주)예시랩")).isEqualTo("예시랩");
		assertThat(PostingMeta.cleanCompany("주식회사 카카오")).isEqualTo("카카오");
		assertThat(PostingMeta.cleanJobTitle("[시리즈A투자유치] Node.js개발자(신입)")).isEqualTo("Node.js개발자(신입)");
		assertThat(PostingMeta.cleanJobTitle("백엔드 ​개발자(D-7) - 사람인")).isEqualTo("백엔드 개발자");
	}

	// ── test_company.py ────────────────────────────────────────────────

	@Test
	void boardPageWithARecruiterApplyLinkIsStillReadAsBoard() {
		String html = resource("saramin-sample.html").replace("</body>",
				"<a href=\"#\" data-href=\"https://example.recruiter.co.kr/career/jobs/1\">홈페이지 지원</a></body>");
		Meta meta = PostingMeta.read(html, TODAY);
		assertThat(meta.company()).isEqualTo("예시랩");
		assertThat(meta.source()).isEqualTo("title");
	}

	// ── meta.py 를 직접 돌려 얻은 값 ──────────────────────────────────────

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			( 주 ) 예시                | 예시
			(유)예시                    | 예시
			예시 (Example Co., Ltd.) 코리아 | 예시 코리아
			예시​랩                | 예시랩
			""")
	void companyCleaningIsTheSameAsJobfit(String name, String expected) {
		assertThat(PostingMeta.cleanCompany(name)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			[A][B] 개발자 (오늘 마감)       | 개발자
			개발자(D-day)                  | 개발자
			"개발자 (마감 임박) | 잡코리아"   | 개발자
			""")
	void jobTitleCleaningIsTheSameAsJobfit(String title, String expected) {
		assertThat(PostingMeta.cleanJobTitle(title)).isEqualTo(expected);
	}

	@Test
	void jsonLdInAGraphWithTheOrganizationAsText() {
		Meta meta = PostingMeta.read("<script type=\"application/ld+json\">{\"@graph\":[{\"@type\":[\"JobPosting\"],\"title\":\"A\","
				+ "\"hiringOrganization\":\"주식회사 예시\"}]}</script>", TODAY);
		assertThat(meta).extracting(Meta::company, Meta::title, Meta::source).containsExactly("예시", "A", "json-ld");
		assertThat(meta.deadline()).isEqualTo(Read.UNKNOWN);
	}

	@Test
	void jsonLdWithoutACompanyFallsBackToThePageTitle() {
		Meta meta = PostingMeta.read("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"A\"}</script>"
				+ "<title>[(주)예시] 백엔드 - 사람인</title>", TODAY);
		assertThat(meta).extracting(Meta::company, Meta::title, Meta::source).containsExactly("예시", "백엔드", "title");
	}

	@Test
	void brokenJsonLdIsSkipped() {
		Meta meta = PostingMeta.read("<script type=\"application/ld+json\">{bad json</script><title>[예시] 백엔드 - 점핏</title>", TODAY);
		assertThat(meta).extracting(Meta::company, Meta::title, Meta::source).containsExactly("예시", "백엔드", "title");
	}

	@Test
	void jobkoreaTitleShape() {
		Meta meta = PostingMeta.read("<title>㈜예시 채용 - 백엔드 개발자 | 잡코리아</title>", TODAY);
		assertThat(meta).extracting(Meta::company, Meta::title, Meta::source).containsExactly("예시", "백엔드 개발자", "title");
	}

	@Test
	void wantedTitleIsNotGuessed() {
		// 사람인·점핏의 [회사] 꼴만 읽는다. 원티드 제목은 회사와 직무의 경계를 알 수 없다
		Meta meta = PostingMeta.read("<title>[예시] 백엔드 - 원티드</title>", TODAY);
		assertThat(meta.company()).isEmpty();
		assertThat(meta.source()).isEmpty();
	}

	@Test
	void ogTitleEntitiesAreDecoded() {
		Meta meta = PostingMeta.read("<meta property=\"og:title\" content=\"[(주)A&amp;B] 개발 &amp; 운영 - 사람인\">", TODAY);
		assertThat(meta).extracting(Meta::company, Meta::title).containsExactly("A&B", "개발 & 운영");
		assertThat(meta.titleFrom()).isEqualTo("[(주)A&B] 개발 & 운영 - 사람인");
	}

	@Test
	void summaryDeadlineWithoutAName() {
		assertThat(PostingMeta.read("<html><body><dl><dt>마감일</dt><dd>2026.12.01</dd></dl></body></html>", TODAY).deadline())
			.isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 12, 1)));
		// 기간이면 물결표 뒤
		Meta range = PostingMeta.read("<p>접수기간 : 2026.11.01 ~ 2026.11.30</p>", TODAY);
		assertThat(range.deadline()).isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 11, 30)));
		assertThat(range.deadlineFrom()).isEqualTo("접수기간 2026.11.01 ~ 2026.11.30");
	}

	// ── jobfit 과 다른 점 ─────────────────────────────────────────────────

	@Test
	void validThroughWithAnOffsetIsReadInSeoul() {
		// jobfit 은 앞 10자를 그대로 써서 UTC 날짜(11-15)가 된다. 한국 시각으로는 11-16 00:00 이다
		Meta meta = PostingMeta.read("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"백엔드\","
				+ "\"validThrough\":\"2026-11-15T15:00:00Z\",\"hiringOrganization\":{\"name\":\"(주)예시\"}}</script>", TODAY);
		assertThat(meta.deadline()).isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 11, 16)));
		assertThat(meta.deadlineFrom()).isEqualTo("2026-11-15T15:00:00Z");
	}

	@Test
	void summarySaysAlwaysOpen() {
		// jobfit 은 "상시채용"을 날짜가 아닌 글자로 두고 마감일을 비운다. 리드미는 상시(always)다
		Meta meta = PostingMeta.read("<p>마감일 : 상시채용</p>", TODAY);
		assertThat(meta.deadline()).isEqualTo(Read.ALWAYS);
		assertThat(meta.deadlineFrom()).isEqualTo("마감일 상시채용");
	}

	@Test
	void summaryDateWithoutAYear() {
		Meta meta = PostingMeta.read("<p>마감일 : ~11/20(목) 23:59</p>", TODAY);
		assertThat(meta.deadline()).isEqualTo(new Read(DeadlineKind.DATE, LocalDate.of(2026, 11, 20)));
	}

	@Test
	void evidenceIsTheTextTheValueCameFrom() {
		Meta saramin = PostingMeta.read(resource("saramin-sample.html"), TODAY);
		assertThat(saramin.companyFrom()).isEqualTo("[(주)예시랩] 백엔드 (Backend) 개발자(D-7) - 사람인");
		assertThat(saramin.titleFrom()).isEqualTo(saramin.companyFrom());
		assertThat(saramin.deadlineFrom()).isEqualTo("마감일 2026.10.27 23:59");
		assertThat(saramin.pageTitle()).isEqualTo("[(주)예시랩] 백엔드 (Backend) 개발자(D-7) - 사람인");
		Meta jobkorea = PostingMeta.read(resource("jobkorea-sample.html"), TODAY);
		assertThat(jobkorea.companyFrom()).isEqualTo("㈜예시컴퍼니(example)");
		assertThat(jobkorea.titleFrom()).isEqualTo("[시리즈A투자유치] Node.js개발자(신입)");
		assertThat(jobkorea.deadlineFrom()).isEqualTo("2026-11-15T23:59");
	}

}
