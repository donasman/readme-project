package com.readme.inboundmail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.readme.common.config.TimeConfig;
import com.readme.common.error.ApiException;
import com.readme.llm.Llm;
import com.readme.llm.LlmErrorCode;
import com.readme.llm.LlmFeature;
import com.readme.posting.ApplicationStatus;

/**
 * 받은 메일을 LLM 으로 분류한다(메일 설계 M8, M10, M14). 받은 트랜잭션이 커밋되면 다른 스레드에서 한 번, 그리고 10분마다 남은 메일을
 * 다시 본다(서버가 다시 떴거나, LLM 이 답하지 않았거나, 한도에 닿았던 메일).
 *
 * <ul>
 * <li>사용액은 그 수신 주소의 사람에게 든다. 한도에 닿았으면 분류하지 않고 기다린다(7일 뒤 지워진다).</li>
 * <li>같은 메일을 둘이 함께 분류하지 않는다. 시작할 때 조건이 붙은 update 로 잡는다(reading_started_at).</li>
 * <li>LLM 이 답하지 않았으면(503) 놓아 다시 기다리게 한다. 답을 받았는데 쓸 수 없거나 그 뒤에 오류가 났으면 메일을 지운다.
 * 다시 부르면 또 돈이 든다.</li>
 * </ul>
 */
@Component
class MailClassifier {

	private static final Logger log = LoggerFactory.getLogger(MailClassifier.class);

	/** 이보다 오래 끝나지 않은 분류는 멈춘 것(서버가 다시 떴다)으로 보고 다시 잡는다 */
	static final Duration STALE = Duration.ofMinutes(10);

	/** LLM 에 보내는 본문의 앞부분. 채용 메일의 알맹이는 앞에 있고, 뒤는 대개 이전 메일의 인용이다 */
	static final int BODY_CHARS = 6_000;

	/** LLM 에 보내는 내 공고 수(최근에 바뀐 것부터) */
	static final int MY_POSTINGS = 100;

	/** 한 번의 다시 보기에서 분류할 메일 수 */
	static final int SWEEP_LIMIT = 50;

	static final String PROMPT = prompt();

	private final MailSteps steps;

	private final Llm llm;

	private final JdbcClient jdbc;

	private final Clock clock;

	MailClassifier(MailSteps steps, Llm llm, JdbcClient jdbc, Clock clock) {
		this.steps = steps;
		this.llm = llm;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** 메일을 받았다({@link MailReceiver}). 커밋된 뒤에만 받는다. */
	record Received(long mailId) {
	}

	@Async
	@TransactionalEventListener
	void onReceived(Received event) {
		classify(event.mailId());
	}

	/** 남은 메일을 다시 본다. 주기는 app.inbound-mail.sweep-cron 이다(테스트는 끄고 {@link #classify} 를 부른다). */
	@Scheduled(cron = "${app.inbound-mail.sweep-cron:0 */10 * * * *}", zone = "Asia/Seoul")
	void sweep() {
		Instant now = this.clock.instant();
		for (long mailId : this.steps.toRead(now.minus(STALE), SWEEP_LIMIT)) {
			classify(mailId);
		}
	}

	/** 메일 하나를 분류한다. 오류는 기록만 하고 밖으로 내지 않는다. */
	void classify(long mailId) {
		try {
			run(mailId);
		}
		catch (ApiException ex) {
			log.info("메일 {} 을 분류하지 않았다: {}", mailId, ex.errorCode().name());
		}
		catch (RuntimeException ex) {
			log.warn("메일 {} 을 분류하지 못했다", mailId, ex);
		}
	}

	private void run(long mailId) {
		Source source = source(mailId).orElse(null);
		if (source == null) {
			return;
		}
		this.llm.requireBudget(source.userId());
		// DB 의 timestamptz 는 마이크로초까지라, 놓을 때 같은 값으로 찾도록 잘라 둔다
		Instant startedAt = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (!this.steps.claim(mailId, startedAt, startedAt.minus(STALE))) {
			return;
		}
		List<MyPosting> postings = myPostings(source.userId());
		String body = (source.body().length() > BODY_CHARS) ? source.body().substring(0, BODY_CHARS) : source.body();
		MailAnswer answer;
		try {
			answer = this.llm.ask(source.userId(), LlmFeature.MAIL_CLASSIFY, PROMPT, question(source, body, postings),
					MailAnswer.class);
		}
		catch (ApiException ex) {
			if (ex.errorCode() == LlmErrorCode.LLM_UNAVAILABLE) {
				this.steps.release(mailId, startedAt);
			}
			else {
				this.steps.delete(mailId);
			}
			throw ex;
		}
		catch (RuntimeException ex) {
			this.steps.delete(mailId);
			throw ex;
		}
		try {
			Set<Long> ids = postings.stream().map(MyPosting::id).collect(Collectors.toSet());
			MailRules.Outcome outcome = MailRules.decide(answer, body, source.subject(), ids);
			if (outcome == null) {
				this.steps.delete(mailId);
				return;
			}
			this.steps.finish(mailId, outcome);
		}
		catch (RuntimeException ex) {
			this.steps.delete(mailId);
			throw ex;
		}
	}

	/** LLM 에 보내는 글: 오늘, 메일, 내 공고 목록(메일 설계 4.3). */
	String question(Source source, String body, List<MyPosting> postings) {
		StringBuilder text = new StringBuilder();
		text.append("오늘: ").append(LocalDate.now(this.clock)).append("\n\n");
		text.append("[메일]\n");
		text.append("보낸 곳: ").append(source.sender()).append('\n');
		text.append("받은 날: ").append(LocalDate.ofInstant(source.receivedAt(), TimeConfig.SEOUL)).append('\n');
		text.append("제목: ").append(source.subject()).append('\n');
		text.append("본문:\n").append(body).append("\n\n");
		text.append("[내 공고 목록] id | 회사 | 공고명 | 지금 상태\n");
		if (postings.isEmpty()) {
			text.append("(없음)\n");
		}
		for (MyPosting posting : postings) {
			text.append(posting.id())
				.append(" | ")
				.append(posting.company())
				.append(" | ")
				.append(posting.title())
				.append(" | ")
				.append(statusText(posting.status()))
				.append('\n');
		}
		return text.toString();
	}

	private static String statusText(ApplicationStatus status) {
		return switch (status.decision()) {
			case UNDECIDED -> "미정";
			case HOLD -> "보류";
			case SKIP -> "지원안함";
			case APPLIED -> status.label();
		};
	}

	private Optional<Source> source(long mailId) {
		return this.jdbc.sql("""
				select user_id, sender, subject, received_at, body from inbound_mails
				 where id = :id and body is not null
				""")
			.param("id", mailId)
			.query((rs, rowNum) -> new Source(rs.getLong("user_id"), rs.getString("sender"), rs.getString("subject"),
					rs.getTimestamp("received_at").toInstant(), rs.getString("body")))
			.optional();
	}

	private List<MyPosting> myPostings(long userId) {
		return this.jdbc.sql("""
				select p.id, p.company, p.title, up.decision, up.stage, up.result
				  from user_postings up
				  join postings p on p.id = up.posting_id
				 where up.user_id = :userId
				 order by up.updated_at desc, p.id desc
				 limit :limit
				""")
			.param("userId", userId)
			.param("limit", MY_POSTINGS)
			.query((rs, rowNum) -> new MyPosting(rs.getLong("id"), rs.getString("company"), rs.getString("title"),
					ApplicationStatus.of(rs.getString("decision"), rs.getString("stage"), rs.getString("result"))))
			.list();
	}

	record Source(long userId, String sender, String subject, Instant receivedAt, String body) {
	}

	record MyPosting(long id, String company, String title, ApplicationStatus status) {
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/mail-classify.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: mail-classify.md", ex);
		}
	}

}
