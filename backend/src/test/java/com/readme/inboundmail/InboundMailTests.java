package com.readme.inboundmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 받기부터 확인함까지(메일 설계 4.1, 4.3, 4.4, 4.5). Worker 대신 테스트가 서명해 보낸다. Claude 는 FakeClaudeApi 가 답하고, 커밋 뒤의
 * 분류는 요청이 돌아오기 전에 끝난다(AwaitAsyncConfiguration).
 */
@IntegrationTest
class InboundMailTests {

	private static final String SECRET = "test-inbound-secret";

	private static final String STATUS_BODY = "1차 면접에 합격하셨습니다.\n2차 면접은 11월 17일 오후 2시입니다.";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	FakeClaudeApi claude;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	MailClassifier classifier;

	@Autowired
	MailSteps steps;

	@BeforeEach
	void resetClaude() {
		this.claude.reset();
	}

	@Test
	void unsignedStaleOrWronglySignedRequestsAre401() {
		byte[] body = mail("hi", "hello").getBytes(StandardCharsets.UTF_8);
		String now = String.valueOf(FixedClockConfiguration.NOW.getEpochSecond());
		String old = String.valueOf(FixedClockConfiguration.NOW.getEpochSecond() - 301);

		assertThat(this.mvc.post().uri("/api/inbound-mail").contentType("message/rfc822").content(body))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("MAIL_SIGNATURE_INVALID");
		assertThat(signed(old, "a@b.example", "x@inbound.readme.test", "", body, SECRET))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(signed(now, "a@b.example", "x@inbound.readme.test", "", body, "other-secret"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tooLargeMailIs413() {
		byte[] body = new byte[InboundMailController.MAX_BYTES + 1];
		String now = String.valueOf(FixedClockConfiguration.NOW.getEpochSecond());

		assertThat(signed(now, "a@b.example", "x@inbound.readme.test", "", body, SECRET))
			.hasStatus(HttpStatus.CONTENT_TOO_LARGE)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("MAIL_TOO_LARGE");
	}

	@Test
	void unknownAddressOrUnregisteredSenderIsDropped() {
		Me me = me("다온");
		String stranger = "stranger@gmail.com";
		String auth = "mx.cloudflare.net; spf=pass smtp.mailfrom=" + stranger;

		assertThat(deliver("nobody@inbound.readme.test", stranger, auth, mail("a", "b")))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(deliver(me.address().replace("@inbound.readme.test", "@other.example"), stranger, auth, mail("a", "b")))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(deliver(me.address(), stranger, auth, mail("a", "b"))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.claude.calls()).isZero();
		assertThat(mails(me.id())).isZero();
	}

	@Test
	void statusMailWaitsForReviewThenApplyChangesStatusAndSchedule() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시 보안", "주니어 서버 개발", "UNKNOWN", null);
		this.fixtures.status(me.id(), posting, "APPLIED", "INTERVIEW_1", "PASSED");
		this.claude.willAnswer(new MailAnswer(true, posting, "", "", "", "interview_2", "in_progress", "2026-11-17", "14:00",
				"2차 면접은 11월 17일 오후 2시입니다.", "예시 보안 면접 안내"));

		assertThat(forward(me, mail("[예시 보안] 2차 면접 안내", STATUS_BODY))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.MAIL_CLASSIFY);
		assertThat(this.claude.lastText()).contains("오늘: 2026-11-13", "제목: [예시 보안] 2차 면접 안내", STATUS_BODY,
				posting + " | 예시 보안 | 주니어 서버 개발 | 1차 면접 합격");
		long mailId = onlyMail(me.id());
		assertThat(this.jdbc.sql("select body is null from inbound_mails where id = :id")
			.param("id", mailId)
			.query(Boolean.class)
			.single()).isTrue(); // 분류하면 본문 글을 지운다
		assertThat(mailbox(me.id())).bodyJson().isStrictlyEqualTo("""
				{ "pending": [ {
				    "id": %d, "from": "예시 보안 채용팀", "receivedAt": "2026-11-13T00:30:00+09:00",
				    "subject": "[예시 보안] 2차 면접 안내", "summary": "예시 보안 면접 안내",
				    "postingId": %d, "company": "예시 보안", "title": "주니어 서버 개발",
				    "before": { "decision": "applied", "stage": "interview_1", "result": "passed" },
				    "after": { "decision": "applied", "stage": "interview_2", "result": "in_progress" },
				    "schedule": { "stage": "interview_2", "date": "2026-11-17", "time": "14:00" },
				    "quote": "2차 면접은 11월 17일 오후 2시입니다." } ],
				  "added": [], "forwarding": [], "waiting": 0 }
				""".formatted(mailId, posting));

		assertThat(action(me.id(), mailId, "apply")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/me/postings/{id}", posting).with(loginAs(me.id()))).bodyJson()
			.extractingPath("$.posting.stage")
			.isEqualTo("interview_2");
		assertThat(this.mvc.get().uri("/api/me/schedules").with(loginAs(me.id()))).bodyJson().isStrictlyEqualTo("""
				[ { "postingId": %d, "company": "예시 보안", "title": "주니어 서버 개발", "stage": "interview_2",
				    "date": "2026-11-17", "time": "14:00" } ]
				""".formatted(posting));
		assertThat(mailbox(me.id())).bodyJson().extractingPath("$.pending").asArray().isEmpty();
		assertThat(action(me.id(), mailId, "apply")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("MAIL_NOT_FOUND");
	}

	@Test
	void ignoreKeepsTheStatus() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시 핀테크", "서버 개발 인턴", "UNKNOWN", null);
		this.fixtures.status(me.id(), posting, "APPLIED", "DOCUMENT", "IN_PROGRESS");
		this.claude.willAnswer(new MailAnswer(true, posting, "", "", "", "document", "failed", "", "",
				"아쉽게도 이번 전형에서는 모시지 못하게 되었습니다.", "예시 핀테크 서류 결과"));
		forward(me, mail("[예시 핀테크] 서류 결과", "아쉽게도 이번 전형에서는 모시지 못하게 되었습니다."));
		long mailId = onlyMail(me.id());

		assertThat(action(me.id(), mailId, "ignore")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mailbox(me.id())).bodyJson().extractingPath("$.pending").asArray().isEmpty();
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", posting).with(loginAs(me.id()))).bodyJson()
			.extractingPath("$.posting.result")
			.isEqualTo("in_progress");
		assertThat(action(me.id(), mailId, "ignore")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void newPostingIsAddedRightAwayAndCanBeReverted() {
		Me me = me("다온");
		String company = "예시 클라우드 " + UUID.randomUUID();
		this.claude.willAnswer(new MailAnswer(true, 0, company, "플랫폼 엔지니어 신입", "2026-11-30", "document",
				"in_progress", "", "", "지원이 완료되었습니다.", "예시 클라우드 지원 완료"));

		forward(me, mail("[예시 채용] 지원 완료", "플랫폼 엔지니어 신입 지원이 완료되었습니다."));

		long mailId = onlyMail(me.id());
		long posting = this.jdbc.sql("select id from postings where company = :company")
			.param("company", company)
			.query(Long.class)
			.single();
		assertThat(mailbox(me.id())).bodyJson().isLenientlyEqualTo("""
				{ "pending": [ { "id": %d, "postingId": %d, "company": "%s", "title": "플랫폼 엔지니어 신입",
				                 "before": { "decision": "undecided", "stage": null, "result": null },
				                 "after": { "decision": "applied", "stage": "document", "result": "in_progress" },
				                 "schedule": null } ],
				  "added": [ { "id": %d, "from": "예시 보안 채용팀", "subject": "[예시 채용] 지원 완료", "postingId": %d,
				               "company": "%s", "title": "플랫폼 엔지니어 신입" } ] }
				""".formatted(mailId, posting, company, mailId, posting, company));
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", posting).with(loginAs(me.id()))).bodyJson()
			.extractingPath("$.posting.deadline.date")
			.isEqualTo("2026-11-30");

		assertThat(action(me.id(), mailId, "revert")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mvc.get().uri("/api/me/postings/{id}", posting).with(loginAs(me.id())))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mailbox(me.id())).bodyJson().isLenientlyEqualTo("""
				{ "pending": [], "added": [] }
				""");
		assertThat(action(me.id(), mailId, "revert")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void mailAboutAPostingAlreadyInMyListWithNothingNewIsNotKept() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시 보안", "주니어 서버 개발", "UNKNOWN", null);
		this.fixtures.status(me.id(), posting, "APPLIED", "INTERVIEW_2", "IN_PROGRESS");
		this.claude.willAnswer(new MailAnswer(true, posting, "", "", "", "interview_2", "in_progress", "", "", "", ""));

		forward(me, mail("다시 안내", "2차 면접 진행 중입니다."));

		assertThat(mails(me.id())).isZero();
	}

	@Test
	void nonRecruitingMailIsNotKept() {
		Me me = me("다온");
		this.claude.willAnswer(new MailAnswer(false, 0, "", "", "", "", "", "", "", "", ""));

		forward(me, mail("이번 주 소식", "뉴스레터입니다."));

		assertThat(this.claude.calls()).isOne();
		assertThat(mails(me.id())).isZero();
		assertThat(mailbox(me.id())).bodyJson().isStrictlyEqualTo("""
				{ "pending": [], "added": [], "forwarding": [], "waiting": 0 }
				""");
	}

	@Test
	void sameMailIsReceivedOnce() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시 보안", "주니어", "UNKNOWN", null);
		this.fixtures.status(me.id(), posting, "APPLIED", "TEST", "IN_PROGRESS");
		this.claude.willAnswer(new MailAnswer(true, posting, "", "", "", "test", "passed", "", "", "", "필기 결과"));
		String raw = mail("<" + UUID.randomUUID() + "@company.example>", "필기 결과", "필기 합격");

		forward(me, raw);
		forward(me, raw);

		assertThat(this.claude.calls()).isOne();
		assertThat(mails(me.id())).isOne();
	}

	@Test
	void unansweredMailWaitsAndIsReadLater() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시 보안", "주니어 서버 개발", "UNKNOWN", null);
		this.fixtures.status(me.id(), posting, "APPLIED", "INTERVIEW_1", "IN_PROGRESS");

		forward(me, mail("면접 결과", STATUS_BODY)); // 답이 없다: LLM 이 답하지 않은 것(503)과 같다

		assertThat(mailbox(me.id())).bodyJson().extractingPath("$.waiting").isEqualTo(1);
		long mailId = onlyMail(me.id());

		this.claude.willAnswer(new MailAnswer(true, posting, "", "", "", "interview_2", "in_progress", "", "", "", "면접"));
		this.classifier.classify(mailId);

		assertThat(mailbox(me.id())).bodyJson().isLenientlyEqualTo("""
				{ "pending": [ { "id": %d } ], "waiting": 0 }
				""".formatted(mailId));
	}

	@Test
	void unusableAnswerDropsTheMail() {
		Me me = me("다온");
		this.claude.willRefuse();

		forward(me, mail("면접 결과", STATUS_BODY));

		assertThat(mails(me.id())).isZero(); // 다시 부르면 또 돈이 들어 지운다
	}

	@Test
	void gmailForwardingConfirmationShowsTheCode() {
		Me me = me("다온");
		String raw = """
				Message-ID: <%s@google.com>
				From: Gmail Team <forwarding-noreply@google.com>
				Subject: (#123456789) Gmail Forwarding Confirmation - Receive Mail from name@gmail.com
				Content-Type: text/plain; charset=UTF-8

				Confirmation code: 123456789
				""".formatted(UUID.randomUUID()).replace("\n", "\r\n");
		String auth = "mx.cloudflare.net; dkim=pass header.d=google.com; spf=pass smtp.mailfrom=x@gaia.bounces.google.com";

		assertThat(deliver(me.address(), "x@gaia.bounces.google.com", auth, raw)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.claude.calls()).isZero();
		long mailId = onlyMail(me.id());
		assertThat(mailbox(me.id())).bodyJson().isStrictlyEqualTo("""
				{ "pending": [], "added": [], "waiting": 0,
				  "forwarding": [ { "id": %d, "receivedAt": "2026-11-13T00:30:00+09:00",
				    "subject": "(#123456789) Gmail Forwarding Confirmation - Receive Mail from name@gmail.com",
				    "code": "123456789" } ] }
				""".formatted(mailId));

		assertThat(action(me.id(), mailId, "ignore")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mails(me.id())).isZero();
	}

	@Test
	void renewedAddressStopsTheOldOne() {
		Me me = me("다온");
		this.mvc.post().uri("/api/me/mail-settings/inbound-address").with(loginAs(me.id())).with(xsrf()).exchange();

		forward(me, mail("a", "b")); // 앞 주소로 보낸다

		assertThat(this.claude.calls()).isZero();
		assertThat(mails(me.id())).isZero();
	}

	@Test
	void othersCannotSeeOrTouchMyMail() {
		Me me = me("다온");
		long other = this.fixtures.user("가람");
		this.claude.willAnswer(new MailAnswer(true, 0, "예시 " + UUID.randomUUID(), "공고", "", "document", "in_progress", "",
				"", "", "지원 완료"));
		forward(me, mail("지원 완료", "지원 완료"));
		long mailId = onlyMail(me.id());

		assertThat(mailbox(other)).bodyJson().isLenientlyEqualTo("""
				{ "pending": [], "added": [] }
				""");
		for (String verb : new String[] { "apply", "ignore", "revert" }) {
			assertThat(action(other, mailId, verb)).hasStatus(HttpStatus.NOT_FOUND);
		}
		assertThat(mailbox(me.id())).bodyJson().extractingPath("$.added").asArray().hasSize(1);
	}

	@Test
	void oldMailsAreDeletedButPendingOnesStay() {
		Me me = me("다온");
		long posting = this.fixtures.posting("예시", "공고", "UNKNOWN", null);
		row(me.id(), "WAITING", null, null, 8); // 분류하지 못하고 7일이 지났다: 지운다
		long freshUnread = row(me.id(), "WAITING", null, null, 6);
		row(me.id(), "READ", posting, "IGNORED", 31); // 30일이 지났다: 지운다
		long oldPending = row(me.id(), "READ", posting, "PENDING", 31); // 확인을 기다린다: 남긴다
		long freshRead = row(me.id(), "READ", posting, "APPLIED", 29);

		this.steps.deleteOld();

		assertThat(this.jdbc.sql("select id from inbound_mails where user_id = :userId order by id")
			.param("userId", me.id())
			.query(Long.class)
			.list()).containsExactly(freshUnread, oldPending, freshRead);
	}

	@Test
	void applyNeedsXsrf() {
		Me me = me("다온");

		assertThat(this.mvc.post().uri("/api/me/mailbox/{id}/apply", 1).with(loginAs(me.id())))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// ── 도움 ───────────────────────────────────────────────────────────

	/** 수신 주소를 만든 사람. email 은 계정 이메일(보내는 주소에 처음부터 있다) */
	private record Me(long id, String address, String email) {
	}

	private Me me(String name) {
		long id = this.fixtures.user(name);
		MvcTestResult settings = this.mvc.get().uri("/api/me/mail-settings").with(loginAs(id)).exchange();
		return new Me(id, JsonPath.read(body(settings), "$.inboundAddress"),
				this.fixtures.emailOf(id));
	}

	/** Gmail 자동 전달처럼 보낸다: 봉투가 계정 이메일의 +caf_ 꼴이고 spf=pass 다. */
	private MvcTestResult forward(Me me, String raw) {
		String envelope = me.email().replace("@", "+caf_=t=inbound.readme.test@");
		String auth = "mx.cloudflare.net; dkim=pass header.d=company.example; spf=pass smtp.mailfrom=" + envelope;
		return deliver(me.address(), envelope, auth, raw);
	}

	private MvcTestResult deliver(String to, String from, String auth, String raw) {
		String now = String.valueOf(FixedClockConfiguration.NOW.getEpochSecond());
		return signed(now, from, to, auth, raw.getBytes(StandardCharsets.UTF_8), SECRET);
	}

	private MvcTestResult signed(String timestamp, String from, String to, String auth, byte[] body, String secret) {
		return this.mvc.post()
			.uri("/api/inbound-mail")
			.contentType("message/rfc822")
			.header("X-Readme-Timestamp", timestamp)
			.header("X-Readme-Envelope-From", from)
			.header("X-Readme-Envelope-To", to)
			.header("X-Readme-Auth-Results", auth)
			.header("X-Readme-Signature", MailSignature.sign(secret, timestamp, from, to, auth, body))
			.content(body)
			.exchange();
	}

	private MvcTestResult mailbox(long userId) {
		return this.mvc.get().uri("/api/me/mailbox").with(loginAs(userId)).exchange();
	}

	private MvcTestResult action(long userId, long mailId, String verb) {
		return this.mvc.post()
			.uri("/api/me/mailbox/{id}/" + verb, mailId)
			.with(loginAs(userId))
			.with(xsrf())
			.exchange();
	}

	/** 받은 지 daysAgo 일 된 메일 행. review 가 있으면 제안(필기 진행 중)도 넣는다 */
	private long row(long userId, String status, Long postingId, String review, int daysAgo) {
		return this.jdbc.sql("""
				insert into inbound_mails (user_id, message_key, sender, subject, received_at, status, body, posting_id,
				                           review, stage, result)
				values (:userId, :key, 'hr', '제목', :receivedAt, :status, :body, :postingId, :review,
				        :stage, :result)
				returning id
				""")
			.param("userId", userId)
			.param("key", java.util.HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes()).substring(0, 64))
			.param("receivedAt", java.sql.Timestamp.from(FixedClockConfiguration.NOW.minus(java.time.Duration.ofDays(daysAgo))))
			.param("status", status)
			.param("body", "WAITING".equals(status) ? "본문" : null, java.sql.Types.VARCHAR)
			.param("postingId", postingId, java.sql.Types.BIGINT)
			.param("review", review, java.sql.Types.VARCHAR)
			.param("stage", (review != null) ? "TEST" : null, java.sql.Types.VARCHAR)
			.param("result", (review != null) ? "IN_PROGRESS" : null, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
	}

	private long mails(long userId) {
		return this.jdbc.sql("select count(*) from inbound_mails where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	private long onlyMail(long userId) {
		return this.jdbc.sql("select id from inbound_mails where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	private static String mail(String subject, String body) {
		return mail("<" + UUID.randomUUID() + "@company.example>", subject, body);
	}

	/** 회사가 보낸 채용 메일(From 은 회사). 제목과 이름은 RFC 2047, 본문은 base64 다. */
	private static String mail(String messageId, String subject, String body) {
		return """
				Message-ID: %s
				From: %s <recruit@company.example>
				To: someone@gmail.com
				Subject: %s
				MIME-Version: 1.0
				Content-Type: text/plain; charset=UTF-8
				Content-Transfer-Encoding: base64

				%s
				""".formatted(messageId, word("예시 보안 채용팀"), word(subject), base64(body)).replace("\n", "\r\n");
	}

	private static String word(String text) {
		return "=?UTF-8?B?" + base64(text) + "?=";
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
