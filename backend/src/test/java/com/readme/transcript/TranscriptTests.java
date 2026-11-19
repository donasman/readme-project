package com.readme.transcript;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;
import com.readme.transcript.TranscriptReader.TranscriptAnswer;
import com.readme.transcript.TranscriptReader.TranscriptAnswer.Row;

/** 성적증명서 읽기 API(학점 계산기 문서 4절). Claude 는 FakeClaudeApi 가 정해 둔 답을 준다. */
@IntegrationTest
class TranscriptTests {

	private static final String TEXT = "Example University Transcript 2019 Spring Programming 3 A+ Calculus 3 C+";

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FakeClaudeApi claude;

	long me;

	@BeforeEach
	void user() {
		this.claude.reset();
		this.me = this.fixtures.user("다온");
	}

	@Test
	void pdfIsReadIntoCourseRowsAndOnlyTheUsageIsKept() throws IOException {
		this.claude.willAnswer(new TranscriptAnswer("예시대학교", "컴퓨터공학과", "4.5", List.of(
				new Row("", "2019학년도", "1학기", "전필", "프로그래밍기초", "3", "A+", "", List.of()),
				new Row("", "2019", "2", "교선", "미적분학2", "3.0", "c+", "before", List.of()),
				new Row("", "2020", "하계", "전선", "데이터통신", "3", "Ao", "", List.of("credit")))));

		assertThat(read(this.me, "성적증명서.pdf", pdf(TEXT))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{
				  "school": "예시대학교",
				  "department": "컴퓨터공학과",
				  "scale": 4.5,
				  "courses": [
				    { "year": 1, "term": "1", "category": "전공필수", "name": "프로그래밍기초", "credit": 3, "grade": "A+", "retake": null, "unsure": [] },
				    { "year": 1, "term": "2", "category": "교양선택", "name": "미적분학2", "credit": 3, "grade": "C+", "retake": "before", "unsure": [] },
				    { "year": 2, "term": "여름", "category": "전공선택", "name": "데이터통신", "credit": 3, "grade": "A0", "retake": null, "unsure": ["credit"] }
				  ]
				}
				""");
		assertThat(this.claude.calls()).isEqualTo(1);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.TRANSCRIPT_READ);
		assertThat(this.claude.lastText()).contains(TEXT);
		assertThat(this.claude.lastDocument()).isNull();
		assertThat(this.jdbc.sql("select feature from llm_usage where user_id = :id").param("id", this.me).query(String.class)
			.list()).containsExactly("TRANSCRIPT_READ");
	}

	@Test
	void photoIsTranscribedByClaudeThenRead() {
		this.claude.willAnswerRecord(TEXT); // 옮겨 적은 글(ScannedFiles)
		this.claude.willAnswer(new TranscriptAnswer("", "", "", List.of(
				new Row("1", "", "1", "전공필수", "프로그래밍기초", "3", "A+", "", List.of()))));

		assertThat(read(this.me, "성적증명서.png", PNG)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "school": null, "department": null, "scale": null, "courses": [ { "name": "프로그래밍기초" } ] }
				""");
		// 옮겨 적기(DOCUMENT_READ) 한 번, 옮겨 적은 글로 과목 줄 읽기 한 번
		assertThat(this.claude.calls()).isEqualTo(2);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.TRANSCRIPT_READ);
		assertThat(this.claude.lastText()).contains(TEXT);
		assertThat(this.jdbc.sql("select feature from llm_usage where user_id = :id order by id").param("id", this.me)
			.query(String.class).list()).containsExactly("DOCUMENT_READ", "TRANSCRIPT_READ");
	}

	@Test
	void transcriptWithoutCoursesIs422() throws IOException {
		this.claude.willAnswer(new TranscriptAnswer("예시대학교", "", "", List.of()));

		assertError(read(this.me, "이력서.pdf", pdf(TEXT)), HttpStatus.UNPROCESSABLE_CONTENT, "TRANSCRIPT_NO_COURSES");
	}

	@Test
	void onlyPdfAndPhotosAreRead() {
		assertError(read(this.me, "성적.txt", TEXT.getBytes(StandardCharsets.UTF_8)), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"TRANSCRIPT_UNSUPPORTED_TYPE");
		// 자료 올리기가 읽는 DOCX(zip)와 Claude 가 받지 않는 사진(TIFF)도 받지 않는다
		assertError(read(this.me, "성적.docx", new byte[] { 'P', 'K', 3, 4, 0 }), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"TRANSCRIPT_UNSUPPORTED_TYPE");
		assertError(read(this.me, "성적.tif", new byte[] { 'I', 'I', '*', 0, 0 }), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"TRANSCRIPT_UNSUPPORTED_TYPE");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void pdfOverTenMegabytesAndPhotoOverFiveAreTooLarge() {
		assertError(read(this.me, "성적.pdf", padded("%PDF-1.7\n", 10 * 1024 * 1024 + 1)), HttpStatus.CONTENT_TOO_LARGE,
				"TRANSCRIPT_TOO_LARGE");
		assertError(read(this.me, "성적.png", padded(new String(PNG, StandardCharsets.ISO_8859_1), 5 * 1024 * 1024 + 1)),
				HttpStatus.CONTENT_TOO_LARGE, "TRANSCRIPT_TOO_LARGE");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void transcriptTextOverTheLimitIsTooLarge() throws IOException {
		String[] pages = new String[TranscriptReader.MAX_CHARS / 80 + 1];
		Arrays.fill(pages, "Programming 3 A+ ".repeat(5));

		assertError(read(this.me, "성적.pdf", pdf(pages)), HttpStatus.CONTENT_TOO_LARGE, "TRANSCRIPT_TOO_LARGE");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void brokenPdfIsUnreadable() {
		assertError(read(this.me, "성적.pdf", "%PDF-1.7\n깨진 파일".getBytes(StandardCharsets.UTF_8)),
				HttpStatus.UNPROCESSABLE_CONTENT, "TRANSCRIPT_UNREADABLE");
	}

	@Test
	void unusableAnswerIsUnreadable() throws IOException {
		this.claude.willRefuse();

		assertError(read(this.me, "성적.pdf", pdf(TEXT)), HttpStatus.UNPROCESSABLE_CONTENT, "TRANSCRIPT_UNREADABLE");
	}

	@Test
	void withoutClaudeIs503AndOverTheLimitIs429() throws IOException {
		assertError(read(this.me, "성적.pdf", pdf(TEXT)), HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE");

		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'TRANSCRIPT_READ', 'claude-opus-5-5', 0, 0, 2000000, now())
				""").param("userId", this.me).update();
		assertError(read(this.me, "성적.pdf", pdf(TEXT)), HttpStatus.TOO_MANY_REQUESTS, "LLM_LIMIT_REACHED");
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	@Test
	void fileIsRequired() {
		assertError(this.mvc.post().uri("/api/transcripts/read").with(loginAs(this.me)).with(xsrf()).multipart()
			.file(new MockMultipartFile("other", "성적.pdf", "application/pdf", PNG)), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
	}

	@Test
	void loginIsRequired() {
		assertThat(this.mvc.post().uri("/api/transcripts/read").with(xsrf()).multipart()
			.file(new MockMultipartFile("file", "성적.png", "image/png", PNG))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MockMvcTester.MockMultipartMvcRequestBuilder read(long userId, String name, byte[] bytes) {
		return this.mvc.post().uri("/api/transcripts/read").with(loginAs(userId)).with(xsrf()).multipart()
			.file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
	}

	private static void assertError(MockMvcTester.MockMultipartMvcRequestBuilder request, HttpStatus status, String code) {
		assertThat(request).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	/** head 로 시작하고 size 바이트가 되게 0 을 채운 파일. */
	private static byte[] padded(String head, int size) {
		byte[] bytes = new byte[size];
		byte[] start = head.getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(start, 0, bytes, 0, start.length);
		return bytes;
	}

	/** 쪽마다 글자 층이 있는 PDF. 글은 Helvetica 로 쓸 수 있는 글자만 쓴다. */
	private static byte[] pdf(String... pages) throws IOException {
		try (PDDocument document = new PDDocument()) {
			for (String text : pages) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					content.beginText();
					content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
					content.newLineAtOffset(20, 700);
					content.showText(text);
					content.endText();
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

}
