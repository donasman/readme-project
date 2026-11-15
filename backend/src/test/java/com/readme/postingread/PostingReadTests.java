package com.readme.postingread;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.posting.PostingKeys;

/**
 * 공고 읽어 오기 API(공고 설계 4.6). 링크는 테스트가 띄운 내 PC 의 사이트(LocalSite)를 읽는다. 테스트 설정이 루프백을 허용한다
 * (application-test.yml 의 app.posting-read.allow-loopback-for-tests). 멈춘 시계의 오늘은 2026-11-13 이다.
 */
@IntegrationTest
class PostingReadTests {

	private static final LocalSite site = LocalSite.start();

	/** 붙여넣는 본문. 첫 줄의 [회사] 공고명, 요약 칸의 마감일, 머리 둘 아래의 항목. */
	private static final String TEXT = String.join("\n", "[(주)예시랩] 백엔드 개발자(D-7)", "마감일 : ~11/20(목) 23:59", "주요 업무",
			"- 주문 API 개발", "자격 요건", "- Java 3년 이상");

	private static final String PAGE = """
			<html><head><title>[(주)로컬랩] 서버 개발자(D-3) - 사람인</title></head>
			<body><nav><ul><li>로그인</li><li>회원가입</li></ul></nav>
			<dl><dt>마감일</dt><dd>2026.11.30 23:59</dd></dl>
			<h2>담당 업무</h2><ul><li>주문 API 개발</li><li>배치 개선</li></ul>
			<h2>자격 요건</h2><ul><li>Java 3년 이상</li></ul>
			</body></html>
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	long leader;

	long me;

	long group;

	@BeforeAll
	static void robots() {
		site.robots("User-agent: *\nDisallow: /private/\n");
	}

	@AfterAll
	static void stop() {
		site.close();
	}

	@BeforeEach
	void group() {
		this.leader = this.fixtures.user("가람");
		this.me = this.fixtures.user("다온");
		this.group = this.fixtures.group("백엔드 스터디", this.leader, this.me);
	}

	// ── 본문 ───────────────────────────────────────────────────────────

	@Test
	void textIsReadWithEvidenceUnreadFieldsAndSections() {
		assertThat(read(this.me, "{ \"text\": \"%s\" }".formatted(json(TEXT)))).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("""
					{
					  "company": "예시랩", "title": "백엔드 개발자",
					  "deadline": { "kind": "date", "date": "2026-11-20" },
					  "deadlineName": "", "applyAt": "", "sourceUrl": "",
					  "evidence": {
					    "company": "[(주)예시랩] 백엔드 개발자(D-7)",
					    "title": "[(주)예시랩] 백엔드 개발자(D-7)",
					    "deadline": "마감일 ~11/20(목) 23:59"
					  },
					  "unread": ["deadlineName", "applyAt", "sourceUrl"],
					  "sections": [
					    { "title": "주요 업무", "items": ["주문 API 개발"], "from": "주요 업무" },
					    { "title": "필수 요건", "items": ["Java 3년 이상"], "from": "자격 요건" }
					  ],
					  "keywords": ["API", "Java"],
					  "source": { "title": "[(주)예시랩] 백엔드 개발자(D-7)", "text": "%s" }
					}
					""".formatted(json(TEXT)));
	}

	@Test
	void textWithNothingRecognizableIsStillReadAndEverythingIsUnread() {
		assertThat(read(this.me, "{ \"text\": \"함께 일할 분을 찾습니다\", \"sourceUrl\": \"\" }")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "company": "", "title": "", "deadline": { "kind": "unknown" }, "evidence": {},
					  "unread": ["company", "title", "deadline", "deadlineName", "applyAt", "sourceUrl"], "sections": [],
					  "keywords": [] }
					""");
	}

	@Test
	void alwaysOpenPostings() {
		assertThat(read(this.me, "{ \"text\": \"[예시] 개발자\\n접수기간 : 채용시 마감\" }")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.deadline")
			.isEqualTo(Map.of("kind", "always"));
	}

	// ── 링크 ───────────────────────────────────────────────────────────

	@Test
	void linkIsFetchedFromTheSiteAndRead() {
		String path = "/jobs/" + UUID.randomUUID();
		site.html(path, PAGE);
		String url = site.url(path);

		assertThat(read(this.me, "{ \"url\": \"%s\" }".formatted(url))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "company": "로컬랩", "title": "서버 개발자",
					  "deadline": { "kind": "date", "date": "2026-11-30" },
					  "deadlineName": "", "applyAt": "", "sourceUrl": "%s",
					  "evidence": {
					    "company": "[(주)로컬랩] 서버 개발자(D-3) - 사람인",
					    "title": "[(주)로컬랩] 서버 개발자(D-3) - 사람인",
					    "deadline": "마감일 2026.11.30 23:59"
					  },
					  "unread": ["deadlineName", "applyAt"],
					  "sections": [
					    { "title": "주요 업무", "items": ["주문 API 개발", "배치 개선"], "from": "담당 업무" },
					    { "title": "필수 요건", "items": ["Java 3년 이상"], "from": "자격 요건" }
					  ],
					  "keywords": ["API", "Java"],
					  "source": { "title": "[(주)로컬랩] 서버 개발자(D-3)" }
					}
					""".formatted(url));
		// 고유한 이름에 연락할 주소(프런트 주소)를 붙인다. 쿠키는 보내지 않는다
		assertThat(site.lastHeaders(path).getFirst("User-Agent")).isEqualTo("ReadmeFetcher/1.0 (+http://localhost:5173)");
		assertThat(site.lastHeaders(path).containsKey("Cookie")).isFalse();
		assertThat(site.hits(path)).isEqualTo(1);
	}

	@Test
	void linkWithKoreanLettersIsRead() {
		// 검색 결과에서 복사한 주소에는 검색어(searchword=백엔드)가 한글 그대로 들어 있다
		String path = "/jobs/" + UUID.randomUUID();
		site.html(path, PAGE);
		String url = site.url(path) + "?searchword=백엔드";
		assertThat(read(this.me, "{ \"url\": \"%s\" }".formatted(url))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{ \"company\": \"로컬랩\", \"sourceUrl\": \"%s\" }".formatted(url));
	}

	@Test
	void pageDisallowedByRobotsIsNotFetched() {
		String path = "/private/" + UUID.randomUUID();
		site.html(path, PAGE);
		assertFetchError(read(this.me, "{ \"url\": \"%s\" }".formatted(site.url(path))), "FETCH_BLOCKED", "robots.txt");
		assertThat(site.hits(path)).isZero();
	}

	@Test
	void blockedPageAsksForPaste() {
		String path = "/forbidden/" + UUID.randomUUID();
		site.respond(path, 403, "text/html", "<p>막힘</p>".getBytes(StandardCharsets.UTF_8));
		assertFetchError(read(this.me, "{ \"url\": \"%s\" }".formatted(site.url(path))), "FETCH_BLOCKED", "막았습니다");
		String busy = "/busy/" + UUID.randomUUID();
		site.respond(busy, 429, "text/html", new byte[0]);
		assertFetchError(read(this.me, "{ \"url\": \"%s\" }".formatted(site.url(busy))), "FETCH_BLOCKED", "요청이 많다며");
	}

	@Test
	void slowPageTimesOut() {
		String path = "/slow/" + UUID.randomUUID();
		site.slow(path, Duration.ofSeconds(4));
		assertFetchError(read(this.me, "{ \"url\": \"%s\" }".formatted(site.url(path))), "FETCH_FAILED", "제때 응답하지");
	}

	@Test
	void pageWithoutAPostingBodyAsksForPaste() {
		String path = "/menu/" + UUID.randomUUID();
		site.html(path, "<nav><ul><li>로그인</li><li>채용정보</li></ul></nav><div id=\"root\"></div>");
		assertFetchError(read(this.me, "{ \"url\": \"%s\" }".formatted(site.url(path))), "FETCH_FAILED", "공고 본문을 찾지 못했습니다");
	}

	@Test
	void privateAddressesAreRefused() {
		assertFetchError(read(this.me, "{ \"url\": \"http://169.254.169.254/latest/meta-data/\" }"), "FETCH_FAILED",
				"읽을 수 없는 주소");
		assertFetchError(read(this.me, "{ \"url\": \"http://10.0.0.1/admin\" }"), "FETCH_FAILED", "읽을 수 없는 주소");
	}

	// ── 같은 공고 안내 ──────────────────────────────────────────────────

	@Test
	void memberIsToldTheLinkIsAlreadyInTheGroupWithoutFetchingIt() {
		String path = "/dup/" + UUID.randomUUID();
		site.html(path, PAGE);
		String url = site.url(path);
		long postingId = postingFor(url, "예시 커머스", "백엔드 개발자 신입");
		this.fixtures.post(this.group, postingId, this.leader, Instant.parse("2026-11-10T01:00:00Z"));

		assertThat(read(this.me, "{ \"url\": \"%s\", \"groupId\": %d }".formatted(url, this.group))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "company": "", "sourceUrl": "%s", "keywords": [],
					  "duplicate": {
					    "postId": %d, "company": "예시 커머스", "title": "백엔드 개발자 신입",
					    "postedBy": { "id": %d, "name": "가람" }, "postedAt": "2026-11-10"
					  }
					}
					""".formatted(url, postingId, this.leader));
		assertThat(site.hits(path)).isZero(); // 이미 그룹에 있는 공고의 페이지는 다시 받지 않는다
	}

	@Test
	void pastedTextWithTheSameSourceUrlIsReadAndToldToo() {
		String url = wantedUrl();
		long postingId = postingFor(url, "예시 커머스", "백엔드 개발자 신입");
		this.fixtures.post(this.group, postingId, this.leader, Instant.parse("2026-11-10T01:00:00Z"));
		// 주소 꼴이 달라도(추적값) 공고키가 같으면 같은 공고다
		String body = "{ \"text\": \"%s\", \"sourceUrl\": \"%s?utm_source=x\", \"groupId\": %d }".formatted(json(TEXT), url, this.group);

		assertThat(read(this.me, body)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "company": "예시랩", "sourceUrl": "%s?utm_source=x", "duplicate": { "postId": %d } }
					""".formatted(url, postingId));
	}

	@Test
	void noNoticeWithoutAGroupOrForATakenDownPost() {
		String url = wantedUrl();
		long postingId = postingFor(url, "예시 커머스", "백엔드 개발자 신입");
		this.fixtures.post(this.group, postingId, this.leader, Instant.parse("2026-11-10T01:00:00Z"));
		String text = json(TEXT);

		assertThat(read(this.me, "{ \"text\": \"%s\", \"sourceUrl\": \"%s\" }".formatted(text, url))).hasStatusOk()
			.bodyJson()
			.doesNotHavePath("$.duplicate");
		this.fixtures.takeDown(this.group, postingId);
		assertThat(read(this.me, "{ \"text\": \"%s\", \"sourceUrl\": \"%s\", \"groupId\": %d }".formatted(text, url, this.group)))
			.hasStatusOk()
			.bodyJson()
			.doesNotHavePath("$.duplicate");
	}

	@Test
	void nonMemberGroupIs404AndNothingIsFetched() {
		long stranger = this.fixtures.user("마루");
		String path = "/jobs/" + UUID.randomUUID();
		site.html(path, PAGE);
		assertThat(read(stranger, "{ \"url\": \"%s\", \"groupId\": %d }".formatted(site.url(path), this.group)))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("GROUP_NOT_FOUND");
		assertThat(site.hits(path)).isZero();
	}

	// ── 파일 ───────────────────────────────────────────────────────────

	@Test
	void fileIsReadLikeTheSamePastedText() throws Exception {
		String pasted = read(this.me, "{ \"text\": \"%s\" }".formatted(json(TEXT))).exchange().getResponse()
			.getContentAsString();
		byte[] file = docx(paragraphs(TEXT.split("\n")));

		// 원문 제목은 붙여넣은 본문처럼 첫 줄이고, 원문 링크가 없어 sourceUrl 은 "" 이고 unread 에 있다
		assertThat(readFile(this.me, "공고.docx", file).param("groupId", String.valueOf(this.group)))
			.hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(pasted);
	}

	@Test
	void nonMemberGroupIs404ForAFileToo() {
		byte[] file = docx(paragraphs(TEXT.split("\n")));
		assertThat(readFile(this.fixtures.user("마루"), "공고.docx", file).param("groupId", String.valueOf(this.group)))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("GROUP_NOT_FOUND");
	}

	@Test
	void fileOverTwentyMegabytesIsTooLarge() {
		assertFileError(readFile(this.me, "공고.pdf", new byte[20 * 1024 * 1024 + 1]), HttpStatus.CONTENT_TOO_LARGE,
				"FILE_TOO_LARGE");
	}

	@Test
	void fileWithMoreTextThanCanBePastedIsTooLarge() {
		assertFileError(readFile(this.me, "공고.docx", docx(paragraphs("가".repeat(PostingReadRequest.MAX_TEXT + 1)))),
				HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE");
	}

	@Test
	void onlyPdfDocxHwpxAndHwpAreRead() {
		// 자료 올리기는 읽는 글 파일도 공고 읽어 오기는 받지 않는다
		assertFileError(readFile(this.me, "공고.txt", TEXT.getBytes(StandardCharsets.UTF_8)), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"FILE_UNSUPPORTED_TYPE");
	}

	@Test
	void brokenFileIsUnreadable() {
		assertFileError(readFile(this.me, "공고.docx", docx("<w:document><w:p><w:t>깨진")), HttpStatus.UNPROCESSABLE_CONTENT,
				"FILE_UNREADABLE");
	}

	@Test
	void scannedPdfHasNoText() throws IOException {
		assertFileError(readFile(this.me, "스캔.pdf", blankPdf()), HttpStatus.UNPROCESSABLE_CONTENT, "FILE_NO_TEXT");
	}

	@Test
	void fileFormNeedsTheFileAndANumericGroupId() {
		assertThat(this.mvc.post().uri("/api/postings/read").with(loginAs(this.me)).with(xsrf()).multipart()
			.file(new MockMultipartFile("other", "공고.docx", "application/octet-stream", docx(paragraphs("본문")))))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_INPUT");
		assertThat(readFile(this.me, "공고.docx", docx(paragraphs("본문"))).param("groupId", "abc"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_INPUT");
	}

	// ── 입력 검사, 로그인 ────────────────────────────────────────────────

	@Test
	void exactlyOneOfUrlAndText() {
		String url = site.url("/jobs/1");
		for (String body : new String[] { "{ \"url\": \"%s\", \"text\": \"본문\" }".formatted(url), "{}", "{ \"text\": \"   \" }",
				"{ \"groupId\": %d }".formatted(this.group),
				// 원문 링크는 본문과 함께만
				"{ \"url\": \"%s\", \"sourceUrl\": \"%s\" }".formatted(url, url),
				// 주소는 http·https 만, 역슬래시 없이
				"{ \"url\": \"ftp://example.com/a\" }", "{ \"url\": \"https://evil.example\\\\@www.saramin.co.kr/a\" }",
				"{ \"text\": \"본문\", \"sourceUrl\": \"javascript:alert(1)\" }",
				"{ \"text\": \"%s\" }".formatted("가".repeat(50_001)) }) {
			assertThat(read(this.me, body)).as(body.substring(0, Math.min(body.length(), 80)))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.code")
				.isEqualTo("INVALID_INPUT");
		}
	}

	@Test
	void loginIsRequired() {
		assertThat(this.mvc.post().uri("/api/postings/read").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"text\": \"본문\" }")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ── 도우미 ─────────────────────────────────────────────────────────

	private MockMvcTester.MockMvcRequestBuilder read(long userId, String body) {
		return this.mvc.post().uri("/api/postings/read").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMultipartMvcRequestBuilder readFile(long userId, String name, byte[] bytes) {
		return this.mvc.post().uri("/api/postings/read").with(loginAs(userId)).with(xsrf()).multipart()
			.file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
	}

	private static void assertFileError(MockMvcTester.MockMultipartMvcRequestBuilder request, HttpStatus status, String code) {
		MvcTestResult result = request.exchange();
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.message").asString().contains("공고 본문을 복사해 붙여넣어 주세요");
	}

	/** 문단마다 한 줄인 DOCX 본문. 글에 &lt; &amp; 가 없어야 한다. */
	private static String paragraphs(String... lines) {
		return Arrays.stream(lines).map((line) -> "<w:p><w:t>" + line + "</w:t></w:p>")
			.collect(Collectors.joining("", "<w:document><w:body>", "</w:body></w:document>"));
	}

	private static byte[] docx(String documentXml) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("word/document.xml"));
			zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		return bytes.toByteArray();
	}

	/** 글자 층이 없는 한 쪽짜리 PDF(스캔본). */
	private static byte[] blankPdf() throws IOException {
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

	private static void assertFetchError(MockMvcTester.MockMvcRequestBuilder request, String code, String why) {
		MvcTestResult result = request.exchange();
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.message").asString().contains(why, "공고 본문을 복사해 붙여넣어 주세요");
	}

	private static String wantedUrl() {
		return "https://www.wanted.co.kr/wd/" + ThreadLocalRandom.current().nextLong(1, 1_000_000_000_000L);
	}

	/** 이 링크의 공고키를 단 공고 본체. 올리기를 거치지 않고 넣는다. */
	private long postingFor(String url, String company, String title) {
		long postingId = this.fixtures.posting(company, title, "UNKNOWN", null);
		this.jdbc.sql("update postings set posting_key = :key, source_url = :url where id = :id")
			.param("key", PostingKeys.derive(url, null, null, null).key())
			.param("url", url)
			.param("id", postingId)
			.update();
		return postingId;
	}

	private static String json(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

}
