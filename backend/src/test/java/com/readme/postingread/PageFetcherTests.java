package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.common.error.ApiException;
import com.readme.common.error.ErrorCode;
import com.readme.postingread.PageFetcher.Page;

/**
 * 페이지 받기와 SSRF 막기. 내 PC 에 띄운 사이트(LocalSite, 127.0.0.1)를 읽으므로 루프백을 허용한 PageFetcher 를 쓴다.
 * 루프백을 허용해도 사설망·메타데이터 주소는 막힌다. 인코딩 풀기의 값은 jobfit 의 fetcher.py(_decode)를 python -I 로 돌려 얻었다.
 */
class PageFetcherTests {

	private static final String UA = "ReadmeFetcher/1.0 (+http://localhost:5173)";

	private final LocalSite site = LocalSite.start();

	private final PageFetcher fetcher = new PageFetcher(UA, settings(Duration.ofSeconds(2), true));

	@AfterEach
	void stop() {
		this.site.close();
	}

	@Test
	void readsOnePageWithAnHonestUserAgentAndNoCookies() {
		this.site.html("/jobs/1", "<p>공고</p>");
		Page page = this.fetcher.html(this.site.url("/jobs/1"), "");
		assertThat(page.html()).isEqualTo("<p>공고</p>");
		assertThat(this.site.lastHeaders("/jobs/1").getFirst("User-Agent")).isEqualTo(UA);
		assertThat(this.site.lastHeaders("/jobs/1").containsKey("Cookie")).isFalse();
		assertThat(this.site.lastHeaders("/jobs/1").containsKey("Referer")).isFalse();
	}

	@Test
	void sendsTheRefererThePlanAsksFor() {
		this.site.html("/detail", "<p>상세</p>");
		this.fetcher.html(this.site.url("/detail"), "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=1");
		assertThat(this.site.lastHeaders("/detail").getFirst("Referer")).isEqualTo("https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=1");
	}

	// ── robots.txt ────────────────────────────────────────────────────

	@Test
	void robotsDisallowBlocksAndNothingIsFetched() {
		this.site.robots("User-agent: *\nDisallow: /secret\n").html("/secret/1", "<p>비밀</p>");
		assertFails(() -> this.fetcher.html(this.site.url("/secret/1"), ""), PostingReadErrorCode.FETCH_BLOCKED, "robots.txt");
		assertThat(this.site.hits("/secret/1")).isZero();
	}

	@Test
	void robotsForOurNameIsFollowed() {
		this.site.robots("User-agent: *\nAllow: /\n\nUser-agent: ReadmeFetcher\nDisallow: /\n").html("/jobs/1", "<p>공고</p>");
		assertFails(() -> this.fetcher.html(this.site.url("/jobs/1"), ""), PostingReadErrorCode.FETCH_BLOCKED, "robots.txt");
	}

	@Test
	void robotsIsReadOncePerSite() {
		this.site.robots("User-agent: *\nDisallow: /secret\n").html("/a", "<p>a</p>").html("/b", "<p>b</p>");
		this.fetcher.html(this.site.url("/a"), "");
		this.fetcher.html(this.site.url("/b"), "");
		assertThat(this.site.hits("/robots.txt")).isEqualTo(1);
	}

	@Test
	void missingRobotsAllowsButAnErroringOneStopsUs() {
		this.site.html("/a", "<p>a</p>");
		assertThat(this.fetcher.html(this.site.url("/a"), "").html()).isEqualTo("<p>a</p>"); // robots.txt 404
		try (LocalSite down = LocalSite.start()) {
			down.respond("/robots.txt", 503, "text/plain", new byte[0]).html("/a", "<p>a</p>");
			assertFails(() -> this.fetcher.html(down.url("/a"), ""), PostingReadErrorCode.FETCH_FAILED, "닿지 못했습니다");
			assertThat(down.hits("/a")).isZero();
		}
	}

	// ── 넘어가기(redirect) ─────────────────────────────────────────────

	@Test
	void followsRedirectsAndReportsTheLastAddress() {
		this.site.redirect("/old", "/new").html("/new", "<p>새 주소</p>");
		Page page = this.fetcher.html(this.site.url("/old"), "");
		assertThat(page.url().toString()).isEqualTo(this.site.url("/new"));
		assertThat(page.html()).isEqualTo("<p>새 주소</p>");
	}

	@Test
	void redirectToAPrivateAddressIsRefused() {
		this.site.redirect("/go", "http://169.254.169.254/latest/meta-data/");
		assertFails(() -> this.fetcher.html(this.site.url("/go"), ""), PostingReadErrorCode.FETCH_FAILED, "읽을 수 없는 주소");
		this.site.redirect("/go2", "http://10.0.0.5/admin");
		assertFails(() -> this.fetcher.html(this.site.url("/go2"), ""), PostingReadErrorCode.FETCH_FAILED, "읽을 수 없는 주소");
	}

	@Test
	void redirectToANonHttpSchemeIsRefused() {
		this.site.redirect("/go", "file:///etc/passwd");
		assertFails(() -> this.fetcher.html(this.site.url("/go"), ""), PostingReadErrorCode.FETCH_FAILED, "읽을 수 없는 주소");
	}

	@Test
	void redirectIntoADisallowedPathIsBlocked() {
		this.site.robots("User-agent: *\nDisallow: /private\n").redirect("/open", "/private/1").html("/private/1", "<p>x</p>");
		assertFails(() -> this.fetcher.html(this.site.url("/open"), ""), PostingReadErrorCode.FETCH_BLOCKED, "robots.txt");
		assertThat(this.site.hits("/private/1")).isZero();
	}

	@Test
	void atMostThreeRedirects() {
		this.site.redirect("/r0", "/r1").redirect("/r1", "/r2").redirect("/r2", "/r3").html("/r3", "<p>셋째</p>");
		assertThat(this.fetcher.html(this.site.url("/r0"), "").html()).isEqualTo("<p>셋째</p>");
		this.site.redirect("/r3", "/r4").html("/r4", "<p>넷째</p>");
		assertFails(() -> this.fetcher.html(this.site.url("/r0"), ""), PostingReadErrorCode.FETCH_FAILED, "너무 여러 번");
		assertThat(this.site.hits("/r4")).isZero();
	}

	// ── 막힘, 실패 ─────────────────────────────────────────────────────

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			401 | FETCH_BLOCKED | 로그인이 필요한
			403 | FETCH_BLOCKED | 막았습니다
			429 | FETCH_BLOCKED | 요청이 많다며
			404 | FETCH_FAILED  | 내려갔거나
			500 | FETCH_FAILED  | 사이트 쪽 오류
			""")
	void statusesSayWhyAndAskForPaste(int status, PostingReadErrorCode code, String why) {
		this.site.respond("/p", status, "text/html", "<p>오류</p>".getBytes(StandardCharsets.UTF_8));
		assertFails(() -> this.fetcher.html(this.site.url("/p"), ""), code, why);
	}

	@Test
	void onlyHtmlIsAccepted() {
		this.site.respond("/posting.pdf", 200, "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))
			.respond("/data", 200, "application/json", "{}".getBytes(StandardCharsets.US_ASCII))
			.respond("/plain", 200, "text/plain", "글자".getBytes(StandardCharsets.UTF_8));
		for (String path : new String[] { "/posting.pdf", "/data", "/plain" }) {
			assertFails(() -> this.fetcher.html(this.site.url(path), ""), PostingReadErrorCode.FETCH_FAILED, "웹 페이지가 아닙니다");
		}
		this.site.respond("/xhtml", 200, "application/xhtml+xml; charset=utf-8", "<p>x</p>".getBytes(StandardCharsets.UTF_8));
		assertThat(this.fetcher.html(this.site.url("/xhtml"), "").html()).isEqualTo("<p>x</p>");
	}

	@Test
	void bodiesOverThreeMegabytesAreRefused() {
		byte[] big = new byte[PageFetcher.MAX_BYTES + 1];
		Arrays.fill(big, (byte) 'a');
		this.site.respond("/big", 200, "text/html", big).chunked("/big-chunked", "text/html", big);
		assertFails(() -> this.fetcher.html(this.site.url("/big"), ""), PostingReadErrorCode.FETCH_FAILED, "너무 큽니다");
		assertFails(() -> this.fetcher.html(this.site.url("/big-chunked"), ""), PostingReadErrorCode.FETCH_FAILED, "너무 큽니다");
		byte[] fits = Arrays.copyOf(big, PageFetcher.MAX_BYTES);
		this.site.respond("/fits", 200, "text/html", fits);
		assertThat(this.fetcher.html(this.site.url("/fits"), "").html()).hasSize(PageFetcher.MAX_BYTES);
	}

	@Test
	void slowServersTimeOut() {
		PageFetcher impatient = new PageFetcher(UA, settings(Duration.ofMillis(300), true));
		this.site.slow("/slow", Duration.ofSeconds(3));
		long started = System.nanoTime();
		assertFails(() -> impatient.html(this.site.url("/slow"), ""), PostingReadErrorCode.FETCH_FAILED, "제때 응답하지");
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
	}

	@Test
	void loopbackIsRefusedUnlessTheTestSwitchIsOn() {
		PageFetcher production = new PageFetcher(UA, settings(Duration.ofSeconds(2), false));
		this.site.html("/a", "<p>a</p>");
		assertFails(() -> production.html(this.site.url("/a"), ""), PostingReadErrorCode.FETCH_FAILED, "읽을 수 없는 주소");
		assertThat(this.site.hits("/robots.txt")).isZero(); // robots.txt 도 받으러 가지 않는다
		assertThat(this.site.hits("/a")).isZero();
	}

	@Test
	void privateAddressesAreRefusedBeforeConnecting() {
		for (String url : new String[] { "http://10.0.0.1/", "http://192.168.0.1/admin", "http://169.254.169.254/latest/meta-data/",
				"http://[fd00::1]/", "http://[::ffff:10.0.0.1]/", "ftp://example.com/x" }) {
			assertFails(() -> this.fetcher.html(url, ""), PostingReadErrorCode.FETCH_FAILED, "읽을 수 없는 주소");
		}
	}

	// ── 인코딩 풀기: fetcher.py 의 _decode 를 직접 돌려 얻은 값 ─────────────────────

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			utf8 header           | ecb184ec9aa920eab3b5eab3a020e2809320ebb0b1ec9794eb939c | text/html; charset=utf-8       | 채용 공고 – 백엔드
			euc-kr header         | c3a4bfeb20b0f8b0ed202d20b9e9bfa3b5e5                   | text/html; charset=EUC-KR      | 채용 공고 - 백엔드
			latin1 header ignored | ecb184ec9aa920eab3b5eab3a020e2809320ebb0b1ec9794eb939c | text/html; charset=ISO-8859-1  | 채용 공고 – 백엔드
			no charset cp949      | b0a1b3aab4d9                                           | text/html                      | 가나다
			wrong header          | b0a1b3aab4d9                                           | text/html; charset=utf-8       | 가나다
			unknown charset       | ecb184ec9aa920eab3b5eab3a020e2809320ebb0b1ec9794eb939c | text/html; charset=x-nope      | 채용 공고 – 백엔드
			quoted charset        | ecb184ec9aa920eab3b5eab3a020e2809320ebb0b1ec9794eb939c | text/html; charset='UTF-8'     | 채용 공고 – 백엔드
			invalid everywhere    | fffe80616263                                           | text/html                      | ���abc
			""")
	void decodesLikeJobfit(String name, String hex, String contentType, String expected) {
		assertThat(PageFetcher.decode(HexFormat.of().parseHex(hex), contentType)).isEqualTo(expected);
	}

	@Test
	void metaCharsetIsReadFromTheFirstBytes() {
		byte[] meta = HexFormat.of().parseHex("3c6d65746120636861727365743d226575632d6b72223ec3a4bfeb20b0f8b0ed202d20b9e9bfa3b5e5");
		assertThat(PageFetcher.decode(meta, "text/html")).isEqualTo("<meta charset=\"euc-kr\">채용 공고 - 백엔드");
		byte[] httpEquiv = HexFormat.of().parseHex("3c6d65746120687474702d65717569763d22436f6e74656e742d547970652220636f6e74656e743d22746578742f68746d6c3b20636861727365743d6b735f635f353630312d31393837223eb0a1b3aa");
		assertThat(PageFetcher.decode(httpEquiv, "text/html")).endsWith("charset=ks_c_5601-1987\">가나");
		byte[] cp949 = HexFormat.of().parseHex("b0a1b3aab4d9");
		assertThat(PageFetcher.decode(cp949, "text/html; charset=\"EUC-KR\"")).isEqualTo("가나다");
	}

	static PostingReadProperties settings(Duration readTimeout, boolean allowLoopback) {
		return new PostingReadProperties(Duration.ofSeconds(1), readTimeout, Duration.ZERO, allowLoopback);
	}

	private static void assertFails(ThrowingCallable call, ErrorCode code, String why) {
		assertThatThrownBy(call).isInstanceOf(ApiException.class)
			.hasMessageContaining(why)
			.hasMessageContaining("공고 본문을 복사해 붙여넣어 주세요")
			.extracting((ex) -> ((ApiException) ex).errorCode())
			.isEqualTo(code);
	}

}
