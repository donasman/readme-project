package com.readme.extraction;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.sql.Types;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.assertj.core.api.AssertProvider;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.common.token.Tokens;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;

/**
 * 자료 올리기·목록·지우기(경험카드 설계 4.2).
 */
@IntegrationTest
class CardSourceTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakeClaudeApi claude;

	// ── 올리기 ─────────────────────────────────────────────────────────

	@Test
	void uploadingAFileStoresTheTextNotTheFile() {
		long me = this.fixtures.user("다온");
		byte[] docx = ZipXmlTextTests.zip(Map.of("word/document.xml",
				"<w:document><w:p><w:t>주문 조회 API 개선</w:t></w:p><w:p><w:t>p95 1.8초 → 0.4초</w:t></w:p></w:document>"));

		MvcTestResult result = uploadFile(me, "C:\\fakepath\\이력서.docx", docx).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long id = id(result);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "source": { "id": %d, "name": "이력서.docx", "kind": "file", "chars": 28, "parts": 1, "cardCount": null,
				              "createdAt": "2026-11-13", "latestJob": null },
				  "duplicate": false
				}
				""".formatted(id));
		assertThat(this.jdbc.sql("""
				select kind, file_name, content_type, byte_size, sha256, body, char_count
				  from card_sources where id = :id
				""").param("id", id).query().singleRow())
			.containsEntry("kind", "FILE")
			.containsEntry("file_name", "이력서.docx")
			.containsEntry("content_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
			.containsEntry("byte_size", (long) docx.length)
			.containsEntry("sha256", Tokens.sha256Hex("주문 조회 API 개선\np95 1.8초 → 0.4초"))
			.containsEntry("body", "주문 조회 API 개선\np95 1.8초 → 0.4초")
			.containsEntry("char_count", 28);
	}

	@Test
	void pastingTextStoresItWithoutAFileName() {
		long me = this.fixtures.user("다온");

		MvcTestResult result = uploadText(me, "{ \"text\": \"  결제 알림 배치 개선\\r\\n하루 3만 건  \" }").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{ "source": { "name": null, "kind": "text", "chars": 19, "parts": 1, "latestJob": null }, "duplicate": false }
				""");
		assertThat(this.jdbc.sql("select file_name, content_type, byte_size, body from card_sources where id = :id")
			.param("id", id(result)).query().singleRow())
			.containsEntry("file_name", null)
			.containsEntry("content_type", null)
			.containsEntry("byte_size", null)
			.containsEntry("body", "결제 알림 배치 개선\n하루 3만 건");
	}

	@Test
	void partsAreTheNumberOfFiveThousandCharacterPieces() {
		long me = this.fixtures.user("다온");
		String text = "가".repeat(5000) + "\\n\\n" + "나".repeat(5000) + "\\n\\n" + "다".repeat(10);

		assertThat(uploadText(me, "{ \"text\": \"" + text + "\" }")).hasStatus(HttpStatus.CREATED)
			.bodyJson().isLenientlyEqualTo("{ \"source\": { \"chars\": 10014, \"parts\": 3 } }");
	}

	@Test
	void uploadingTheSameTextAgainReturnsTheFirstSource() {
		long me = this.fixtures.user("다온");
		long first = id(uploadText(me, "{ \"text\": \"같은 경험 정리\" }").exchange());

		// 같은 글을 다시 붙여넣어도, 같은 글의 파일을 올려도 처음 것을 돌려준다
		assertThat(uploadText(me, "{ \"text\": \"\\n같은 경험 정리\\n\" }")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "source": { "id": %d, "name": null, "kind": "text" }, "duplicate": true }
				""".formatted(first));
		assertThat(uploadFile(me, "경험.txt", "같은 경험 정리".getBytes(StandardCharsets.UTF_8))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("{ \"source\": { \"id\": %d }, \"duplicate\": true }".formatted(first));
		assertThat(count("select count(*) from card_sources where user_id = " + me)).isOne();

		// 다른 사람이 같은 글을 올리면 그 사람의 자료가 새로 생긴다
		assertThat(uploadText(this.fixtures.user("가람"), "{ \"text\": \"같은 경험 정리\" }"))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.duplicate").isEqualTo(false);
	}

	@Test
	void residentAndPhoneNumbersAreMaskedBeforeSaving() {
		long me = this.fixtures.user("다온");
		String raw = "홍길동 010-1234-5678\n주민등록번호 950101-1234567\n2023.03 ~ 2024.02 주문 조회 API 개선";
		String masked = "홍길동 [전화번호]\n주민등록번호 [주민번호]\n2023.03 ~ 2024.02 주문 조회 API 개선";

		MvcTestResult result = uploadText(me, "{ \"text\": \"" + raw.replace("\n", "\\n") + "\" }").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson()
			.isLenientlyEqualTo("{ \"source\": { \"chars\": %d } }".formatted(SourceText.length(masked)));
		long id = id(result);
		assertThat(this.jdbc.sql("select body, sha256, char_count from card_sources where id = :id").param("id", id)
			.query().singleRow())
			.containsEntry("body", masked)
			.containsEntry("sha256", Tokens.sha256Hex(masked))
			.containsEntry("char_count", SourceText.length(masked));
		// 가리기 전의 같은 글을 파일로 다시 올려도 같은 자료다
		assertThat(uploadFile(me, "이력서.txt", raw.getBytes(StandardCharsets.UTF_8))).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("{ \"source\": { \"id\": %d }, \"duplicate\": true }".formatted(id));
	}

	// ── 올리기 오류 ──────────────────────────────────────────────────────

	@Test
	void aFileOverTwentyMegabytesIsRefused() {
		long me = this.fixtures.user("다온");
		byte[] large = new byte[(int) SourceInput.MAX_BYTES + 1];

		assertError(uploadFile(me, "큰.txt", large), HttpStatus.CONTENT_TOO_LARGE, "SOURCE_TOO_LARGE");
		assertThat(count("select count(*) from card_sources where user_id = " + me)).isZero();
	}

	@Test
	void textOverAMillionCharactersIsRefused() {
		long me = this.fixtures.user("다온");

		assertError(uploadText(me, "{ \"text\": \"" + "가".repeat(SourceText.MAX_CHARS + 1) + "\" }"),
				HttpStatus.CONTENT_TOO_LARGE, "SOURCE_TOO_LARGE");
	}

	// ── 글자 층이 없는 파일: Claude 가 옮겨 적는다(경험카드 설계 4.7). Claude 는 FakeClaudeApi 가 답한다 ──

	@Test
	void aScannedPdfIsTranscribedByClaude() throws Exception {
		this.claude.reset();
		long me = this.fixtures.user("다온");
		this.claude.willAnswerRecord("김다온\n주문 조회 API 개선 프로젝트\n연락처 010-1234-5678");

		assertThat(uploadFile(me, "스캔.pdf", PdfTextTests.pdf(""))).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "source": { "name": "스캔.pdf", "kind": "file", "parts": 1 }, "duplicate": false }
					""");
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.DOCUMENT_READ);
		assertThat(this.claude.lastDocument().mediaType()).isEqualTo("application/pdf");
		assertThat(this.jdbc.sql("select body from card_sources where user_id = :userId").param("userId", me)
			.query(String.class).single()).isEqualTo("김다온\n주문 조회 API 개선 프로젝트\n연락처 [전화번호]");
	}

	@Test
	void aPhotoIsTranscribedButNotEveryFormat() {
		this.claude.reset();
		long me = this.fixtures.user("다온");
		this.claude.willAnswerRecord("사진 속 이력서");

		assertThat(uploadFile(me, "이력서.png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 }))
			.hasStatus(HttpStatus.CREATED);
		assertThat(this.claude.lastDocument().mediaType()).isEqualTo("image/png");
		assertThat(uploadFile(me, "IMG_0001.HEIC", new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0 }))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "code": "SOURCE_NO_TEXT", "message": "이 사진 형식은 읽지 못합니다. JPG나 PNG로 바꿔 올려 주세요" }
					""");
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	@Test
	void scannedPdfsOverTwentyPagesAreRefusedBeforeClaude() throws Exception {
		this.claude.reset();
		String[] pages = new String[ScannedFiles.MAX_PAGES + 1];
		Arrays.fill(pages, "");

		assertThat(uploadFile(this.fixtures.user("다온"), "스캔.pdf", PdfTextTests.pdf(pages)))
			.hasStatus(HttpStatus.CONTENT_TOO_LARGE)
			.bodyJson()
			.extractingPath("$.message")
			.isEqualTo("스캔한 PDF는 20쪽까지 읽습니다. 글자를 고를 수 있는 PDF나 나눈 파일로 올려 주세요");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void whenClaudeCannotReadItTheUploadFailsWithTheReason() throws Exception {
		this.claude.reset();
		long me = this.fixtures.user("다온");

		// 정해 둔 답이 없으면 키가 없는 서버처럼 503 이다
		assertError(uploadFile(me, "스캔.pdf", PdfTextTests.pdf("")), HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE");
		this.claude.willRefuse();
		assertThat(uploadFile(me, "스캔.pdf", PdfTextTests.pdf(""))).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "code": "SOURCE_NO_TEXT", "message": "글자를 읽지 못했습니다. 글자가 잘 보이는 파일로 다시 올려 주세요" }
					""");
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'DOCUMENT_READ', 'claude-opus-5-5', 0, 0, 2000000, now())
				""").param("userId", me).update();
		assertError(uploadFile(me, "스캔.pdf", PdfTextTests.pdf("")), HttpStatus.TOO_MANY_REQUESTS, "LLM_LIMIT_REACHED");
		assertThat(count("select count(*) from card_sources where user_id = " + me)).isZero();
	}

	@Test
	void unsupportedEmptyAndMissingInputs() {
		long me = this.fixtures.user("다온");

		assertError(uploadFile(me, "경력.csv", "이름,기간".getBytes(StandardCharsets.UTF_8)),
				HttpStatus.UNSUPPORTED_MEDIA_TYPE, "SOURCE_UNSUPPORTED_TYPE");
		assertError(uploadFile(me, "빈.txt", " \n ".getBytes(StandardCharsets.UTF_8)),
				HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_EMPTY");
		assertError(uploadText(me, "{ \"text\": \" \\n\\t \" }"), HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_EMPTY");
		assertError(uploadText(me, "{}"), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
		assertError(this.mvc.post().uri("/api/card-sources").with(loginAs(me)).with(xsrf()).multipart()
			.file(new MockMultipartFile("other", "a.txt", "text/plain", "글".getBytes(StandardCharsets.UTF_8))),
				HttpStatus.BAD_REQUEST, "INVALID_INPUT");
		assertThat(count("select count(*) from card_sources where user_id = " + me)).isZero();
	}

	@Test
	void uploadingNeedsLoginAndTheCsrfHeader() {
		assertError(this.mvc.post().uri("/api/card-sources").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"text\": \"글\" }"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
		assertError(this.mvc.post().uri("/api/card-sources").with(loginAs(this.fixtures.user("다온")))
			.contentType(MediaType.APPLICATION_JSON).content("{ \"text\": \"글\" }"), HttpStatus.FORBIDDEN, "CSRF_INVALID");
	}

	// ── 목록 ─────────────────────────────────────────────────────────

	@Test
	void theListShowsOnlyMySourcesNewestFirstWithTheLatestJob() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long older = id(uploadText(me, "{ \"text\": \"먼저 올린 글\" }").exchange());
		long newer = id(uploadFile(me, "포트폴리오.md", "# 나중에 올린 파일".getBytes(StandardCharsets.UTF_8)).exchange());
		uploadText(other, "{ \"text\": \"남의 글\" }").exchange();
		job(me, older, "DONE", 1, 1);
		long latest = job(me, older, "RUNNING", 2, 3);

		assertThat(this.mvc.get().uri("/api/card-sources").with(loginAs(me))).hasStatusOk().bodyJson()
			.isStrictlyEqualTo("""
					[
					  { "id": %d, "name": "포트폴리오.md", "kind": "file", "chars": 11, "parts": 1, "cardCount": null,
					    "createdAt": "2026-11-13", "latestJob": null },
					  { "id": %d, "name": null, "kind": "text", "chars": 7, "parts": 1, "cardCount": 0,
					    "createdAt": "2026-11-13",
					    "latestJob": { "id": %d, "status": "running", "doneParts": 2, "totalParts": 3 } }
					]
					""".formatted(newer, older, latest));
	}

	@Test
	void theListCountsTheCardsLeftFromEachSource() {
		long me = this.fixtures.user("다온");
		long extracted = id(uploadText(me, "{ \"text\": \"카드 둘을 뽑은 자료\" }").exchange());
		long job = job(me, extracted, "DONE", 1, 1);
		card(me, extracted, job);
		card(me, extracted, job);
		long emptied = id(uploadText(me, "{ \"text\": \"뽑은 카드를 모두 지운 자료\" }").exchange());
		long deleted = card(me, emptied, job(me, emptied, "DONE", 1, 1));
		this.jdbc.sql("delete from experience_cards where id = :id").param("id", deleted).update();
		long running = id(uploadText(me, "{ \"text\": \"뽑는 중인 자료\" }").exchange());
		card(me, running, job(me, running, "RUNNING", 1, 2));
		long fresh = id(uploadText(me, "{ \"text\": \"아직 뽑지 않은 자료\" }").exchange());
		card(me, null, null);

		// 지운 카드와 직접 만든 카드는 세지 않는다. 뽑는 중이면 지금까지 만든 카드 수다
		assertThat(this.mvc.get().uri("/api/card-sources").with(loginAs(me))).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("""
					[
					  { "id": %d, "cardCount": null },
					  { "id": %d, "cardCount": 1 },
					  { "id": %d, "cardCount": 0 },
					  { "id": %d, "cardCount": 2 }
					]
					""".formatted(fresh, running, emptied, extracted));
		// 올리기 응답의 source 도 같은 꼴이다. 이미 올린 자료면 그 자료의 카드 수가 온다
		assertThat(uploadText(me, "{ \"text\": \"카드 둘을 뽑은 자료\" }")).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("{ \"source\": { \"id\": %d, \"cardCount\": 2 }, \"duplicate\": true }".formatted(extracted));
	}

	// ── 지우기 ────────────────────────────────────────────────────────

	@Test
	void deletingMySourceKeepsTheCardsMadeFromIt() {
		long me = this.fixtures.user("다온");
		long source = id(uploadText(me, "{ \"text\": \"카드를 뽑은 자료\" }").exchange());
		long job = job(me, source, "DONE", 1, 1);
		long card = card(me, source, job);

		assertThat(delete(me, source)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/card-sources").with(loginAs(me))).bodyJson().isStrictlyEqualTo("[]");
		assertThat(count("select count(*) from card_extraction_jobs where id = " + job)).isZero();
		assertThat(this.jdbc.sql("select source_id, job_id from experience_cards where id = :id").param("id", card)
			.query().singleRow()).containsEntry("source_id", null).containsEntry("job_id", null);
	}

	@Test
	void someoneElsesSourceIsNotFound() {
		long owner = this.fixtures.user("가람");
		long source = id(uploadText(owner, "{ \"text\": \"가람의 자료\" }").exchange());

		assertError(delete(this.fixtures.user("다온"), source), HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND");
		assertError(delete(owner, source + 1_000_000), HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND");
		assertThat(count("select count(*) from card_sources where id = " + source)).isOne();
	}

	// ── 도우미 ────────────────────────────────────────────────────────

	private MockMvcTester.MockMvcRequestBuilder uploadText(long userId, String json) {
		return this.mvc.post().uri("/api/card-sources").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private MockMvcTester.MockMultipartMvcRequestBuilder uploadFile(long userId, String name, byte[] bytes) {
		return this.mvc.post().uri("/api/card-sources").with(loginAs(userId)).with(xsrf()).multipart()
			.file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
	}

	private MvcTestResult delete(long userId, long sourceId) {
		return this.mvc.delete().uri("/api/card-sources/{id}", sourceId).with(loginAs(userId)).with(xsrf()).exchange();
	}

	private long job(long userId, long sourceId, String status, int doneParts, int totalParts) {
		return this.jdbc.sql("""
				insert into card_extraction_jobs (user_id, source_id, status, total_parts, done_parts)
				values (:userId, :sourceId, :status, :totalParts, :doneParts) returning id
				""")
			.param("userId", userId)
			.param("sourceId", sourceId)
			.param("status", status)
			.param("totalParts", totalParts)
			.param("doneParts", doneParts)
			.query(Long.class)
			.single();
	}

	/** 카드 한 장. sourceId, jobId 가 null 이면 직접 만든 카드다. */
	private long card(long userId, Long sourceId, Long jobId) {
		return this.jdbc.sql("""
				insert into experience_cards (user_id, title, source_id, job_id) values (:userId, '카드', :sourceId, :jobId)
				returning id
				""")
			.param("userId", userId)
			.param("sourceId", sourceId, Types.BIGINT)
			.param("jobId", jobId, Types.BIGINT)
			.query(Long.class)
			.single();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

	private static long id(MvcTestResult result) {
		try {
			return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.source.id")).longValue();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void assertError(AssertProvider<MvcTestResultAssert> request, HttpStatus status, String code) {
		assertThat(request).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
