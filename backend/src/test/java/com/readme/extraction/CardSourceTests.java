package com.readme.extraction;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
				  "source": { "id": %d, "name": "이력서.docx", "kind": "file", "chars": 28, "parts": 1,
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

	@Test
	void aScannedPdfHasNoText() throws Exception {
		assertError(uploadFile(this.fixtures.user("다온"), "스캔.pdf", PdfTextTests.pdf("")),
				HttpStatus.UNPROCESSABLE_CONTENT, "SOURCE_NO_TEXT");
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
					  { "id": %d, "name": "포트폴리오.md", "kind": "file", "chars": 11, "parts": 1, "createdAt": "2026-11-13",
					    "latestJob": null },
					  { "id": %d, "name": null, "kind": "text", "chars": 7, "parts": 1, "createdAt": "2026-11-13",
					    "latestJob": { "id": %d, "status": "running", "doneParts": 2, "totalParts": 3 } }
					]
					""".formatted(newer, older, latest));
	}

	// ── 지우기 ────────────────────────────────────────────────────────

	@Test
	void deletingMySourceKeepsTheCardsMadeFromIt() {
		long me = this.fixtures.user("다온");
		long source = id(uploadText(me, "{ \"text\": \"카드를 뽑은 자료\" }").exchange());
		long job = job(me, source, "DONE", 1, 1);
		long card = this.jdbc.sql("""
				insert into experience_cards (user_id, title, source_id, job_id) values (:userId, '카드', :sourceId, :jobId)
				returning id
				""").param("userId", me).param("sourceId", source).param("jobId", job).query(Long.class).single();

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
