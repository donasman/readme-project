package com.readme.postingread;

import static com.readme.postingread.HtmlTextTests.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.readme.common.error.ApiException;
import com.readme.posting.DeadlineKind;
import com.readme.postingread.PostingReadResponse.DeadlineValue;
import com.readme.postingread.PostingReadResponse.SectionValue;

/**
 * 사이트 규칙대로 받은 페이지를 읽는 순서(요약 페이지 → 본문이 없으면 상세요강). jobfit 의 사람인·잡코리아 합성 픽스처를 실제 사이트
 * 주소에 돌려주는 가짜 PageFetcher 로 본다. 바깥에 나가지 않는다. 공고 설계 4.6 의 응답 예시가 이 값이다.
 */
class PostingReaderTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

	private final List<String> fetched = new ArrayList<>();

	/** careers.example.com 에 돌려줄 페이지. */
	private String companyPage = "";

	private final PostingReader reader = new PostingReader(new PageFetcher("test", PageFetcherTests.settings(Duration.ofSeconds(1), false)) {

		@Override
		Page html(String url, String referer) {
			PostingReaderTests.this.fetched.add(url + " ← " + referer);
			if (url.contains("careers.example.com")) {
				return new Page(URI.create(url), PostingReaderTests.this.companyPage);
			}
			String name = url.contains("view-detail") ? "saramin-sample-detail.html"
					: url.contains("jobs/view") ? "saramin-sample.html"
					: url.contains("GI_Read_Comt_Ifrm") ? "jobkorea-sample-detail.html" : "jobkorea-sample.html";
			return new Page(URI.create(url), resource(name));
		}

	});

	@Test
	void saraminSummaryAndDetail() {
		String url = "https://www.saramin.co.kr/zf_user/jobs/relay/view?view_type=list&rec_idx=10000001";
		PostingReadResponse read = this.reader.fromUrl(url, TODAY);

		assertThat(this.fetched).containsExactly("https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=10000001 ← " + url,
				"https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=10000001&rec_seq=0 ← "
						+ "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=10000001");
		assertThat(read.company()).isEqualTo("예시랩");
		assertThat(read.title()).isEqualTo("백엔드 (Backend) 개발자");
		assertThat(read.deadline()).isEqualTo(new DeadlineValue(DeadlineKind.DATE, LocalDate.of(2026, 10, 27)));
		assertThat(read.sourceUrl()).isEqualTo(url);
		assertThat(read.evidence()).containsExactly(Map.entry("company", "[(주)예시랩] 백엔드 (Backend) 개발자(D-7) - 사람인"),
				Map.entry("title", "[(주)예시랩] 백엔드 (Backend) 개발자(D-7) - 사람인"), Map.entry("deadline", "마감일 2026.10.27 23:59"));
		assertThat(read.unread()).containsExactly("deadlineName", "applyAt");
		assertThat(read.sections()).extracting(SectionValue::title, SectionValue::from)
			.containsExactly(tuple("회사 소개", "회사 소개"), tuple("모집 부문", "모집부문"), tuple("주요 업무", "담당 업무"),
					tuple("필수 요건", "필요 스택"), tuple("필수 요건", "필수 경험과 역량"), tuple("우대 사항", "우대 사항"),
					tuple("전형 절차", "전형절차"), tuple("근무 조건", "근무조건"), tuple("접수 방법", "접수기간 및 방법"));
		assertThat(read.sections().get(2).items()).containsExactly("Python 기반 RESTful API 및 서비스 서버 개발",
				"실시간 센서 데이터 수집·처리 및 DB 설계·최적화", "웹 애플리케이션 배포 및 운영");
		assertThat(read.sections().get(3).items()).containsExactly("주요 언어 : Python / Java 중 1개 이상",
				"데이터베이스 : MongoDB 또는 PostgreSQL 활용 경험");
		assertThat(read.source().title()).isEqualTo("[(주)예시랩] 백엔드 (Backend) 개발자(D-7)");
		assertThat(read.source().text()).startsWith(resource("saramin-sample.txt")).endsWith(resource("saramin-sample-detail.txt"));
	}

	@Test
	void jobkoreaJsonLdAndDetail() {
		PostingReadResponse read = this.reader.fromUrl("https://www.jobkorea.co.kr/Recruit/GI_Read/10000002?Oem_Code=C1", TODAY);

		assertThat(this.fetched).hasSize(2);
		assertThat(read.company()).isEqualTo("예시컴퍼니");
		assertThat(read.title()).isEqualTo("Node.js개발자(신입)");
		assertThat(read.deadline()).isEqualTo(new DeadlineValue(DeadlineKind.DATE, LocalDate.of(2026, 11, 15)));
		assertThat(read.evidence()).containsEntry("deadline", "2026-11-15T23:59");
		assertThat(read.sections()).extracting(SectionValue::title).startsWith("회사 소개", "주요 업무", "필수 요건", "우대 사항");
		// 항목의 "- " 글머리는 떼고 ※ 안내는 둔다
		assertThat(read.sections().get(3).items()).contains("※ 본 채용은 채용 완료 시 조기 마감될 수 있습니다.");
	}

	@Test
	void atMostThreeLinksAreReadAtOnce() throws Exception {
		CountDownLatch inside = new CountDownLatch(PostingReader.MAX_READS);
		CountDownLatch release = new CountDownLatch(1);
		PostingReader slow = new PostingReader(new PageFetcher("test", PageFetcherTests.settings(Duration.ofSeconds(1), false)) {

			@Override
			Page html(String url, String referer) {
				inside.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return new Page(URI.create(url), resource(url.contains("view-detail") ? "saramin-sample-detail.html" : "saramin-sample.html"));
			}

		});
		String url = "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=10000001";
		ExecutorService pool = Executors.newFixedThreadPool(PostingReader.MAX_READS);
		try {
			List<Future<PostingReadResponse>> running = new ArrayList<>();
			for (int i = 0; i < PostingReader.MAX_READS; i++) {
				running.add(pool.submit(() -> slow.fromUrl(url, TODAY)));
			}
			assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
			// 넷째는 기다리지 않고 바로 거절한다
			assertThatThrownBy(() -> slow.fromUrl(url, TODAY)).isInstanceOf(ApiException.class)
				.hasMessageContaining("읽고 있는 링크가 많습니다")
				.extracting((ex) -> ((ApiException) ex).errorCode())
				.isEqualTo(PostingReadErrorCode.FETCH_FAILED);
			release.countDown();
			for (Future<PostingReadResponse> read : running) {
				assertThat(read.get(5, TimeUnit.SECONDS).company()).isEqualTo("예시랩");
			}
			assertThat(slow.fromUrl(url, TODAY).company()).isEqualTo("예시랩"); // 끝나면 다시 읽는다
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** 페이지나 붙여넣은 글에 공백 5만 칸(보통 공백과 em 공백)이 있어도 읽기가 바로 끝난다. */
	@ParameterizedTest
	@ValueSource(strings = { " ", " " })
	void longRunsOfSpacesTakeLinearTime(String space) {
		String run = space.repeat(50_000);
		this.companyPage = "<title>x" + run + "x</title><script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"x" + run
				+ "\",\"hiringOrganization\":{\"name\":\"x" + run + "(a)\"}}</script><h2>담당 업무</h2><ul><li>[" + run + "]</li></ul>"
				+ "<h2>자격 요건</h2><p>마감일 :" + run + "x</p><p>x" + run + "- 사람인</p>";
		String text = String.join("\n", "[예시" + run + "] 개발자(D-1)" + run, "마감일 : ~11/20" + run, "주요 업무", "-" + run + "개발",
				"[" + run + "]", "자격 요건", "- Java" + run + "3년");
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
			assertThat(this.reader.fromUrl("https://careers.example.com/jobs/1", TODAY).sections()).extracting(SectionValue::title)
				.containsExactly("주요 업무", "필수 요건");
			assertThat(this.reader.fromText(text, null, TODAY).sections()).extracting(SectionValue::title)
				.containsExactly("주요 업무", "필수 요건");
		});
	}

}
