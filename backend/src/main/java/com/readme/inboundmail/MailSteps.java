package com.readme.inboundmail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.readme.posting.ApplicationStatus;
import com.readme.posting.UserPostings;

/**
 * 분류의 각 단계를 짧은 트랜잭션으로 나눈다(LLM 은 트랜잭션 밖에서 부른다). 오래된 메일을 지우는 예약작업도 여기에 둔다(메일 설계 M9).
 */
@Component
class MailSteps {

	private static final Logger log = LoggerFactory.getLogger(MailSteps.class);

	/** 분류하지 못한 메일을 이만큼 두고 지운다 */
	static final Duration KEEP_UNREAD = Duration.ofDays(7);

	/** 확인함의 행을 이만큼 두고 지운다(확인을 기다리는 것은 남긴다) */
	static final Duration KEEP_READ = Duration.ofDays(30);

	private final InboundMailRepository mails;

	private final UserPostings userPostings;

	private final JdbcClient jdbc;

	private final Clock clock;

	MailSteps(InboundMailRepository mails, UserPostings userPostings, JdbcClient jdbc, Clock clock) {
		this.mails = mails;
		this.userPostings = userPostings;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	boolean claim(long mailId, Instant startedAt, Instant staleBefore) {
		return this.mails.claim(mailId, startedAt, staleBefore) == 1;
	}

	@Transactional
	void release(long mailId, Instant startedAt) {
		this.mails.release(mailId, startedAt);
	}

	@Transactional
	void delete(long mailId) {
		this.mails.deleteById(mailId);
	}

	@Transactional(readOnly = true)
	List<Long> toRead(Instant staleBefore, int limit) {
		return this.mails.findToRead(staleBefore, limit);
	}

	/**
	 * 분류를 끝낸다(메일 설계 M11). 새 공고는 바로 내 공고에 넣는다. 새로 넣은 공고도 확인할 제안도 없으면 메일을 지운다.
	 * 분류를 잡은(READING) 메일이 아니면 아무 일도 하지 않는다.
	 */
	@Transactional
	void finish(long mailId, MailRules.Outcome outcome) {
		InboundMail mail = this.mails.findById(mailId).filter((m) -> "READING".equals(m.status())).orElse(null);
		if (mail == null) {
			return;
		}
		long userId = mail.userId();
		long postingId;
		boolean added = false;
		if (outcome.newPosting() != null) {
			MailRules.NewPosting posting = outcome.newPosting();
			UserPostings.Added result = this.userPostings.addFromMail(userId, posting.company(), posting.title(),
					posting.deadline());
			postingId = result.postingId();
			added = result.created();
		}
		else {
			postingId = outcome.postingId();
		}
		boolean review = MailRules.worthReview(outcome, currentStatus(userId, postingId));
		if (!added && !review) {
			this.mails.delete(mail);
			return;
		}
		mail.read(postingId, added, review ? outcome.proposal() : null, outcome.scheduleDate(), outcome.scheduleTime(),
				outcome.quote(), outcome.summary());
	}

	/**
	 * 매일 04:10(서울)에 분류하지 못하고 7일이 지난 메일과, 30일이 지난 확인함의 행(확인을 기다리는 것은 빼고)을 지운다.
	 * 서버가 여러 대면 모두 돌지만 같은 행을 지우므로 괜찮다.
	 */
	@Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
	@Transactional
	void deleteOld() {
		Instant now = this.clock.instant();
		int unread = this.mails.deleteUnread(now.minus(KEEP_UNREAD));
		int old = this.mails.deleteOld(now.minus(KEEP_READ));
		log.info("받은 메일을 지웠다: 분류하지 못한 것 {}통, 지난 것 {}통", unread, old);
	}

	private ApplicationStatus currentStatus(long userId, long postingId) {
		return this.jdbc.sql("""
				select decision, stage, result from user_postings where user_id = :userId and posting_id = :postingId
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> ApplicationStatus.of(rs.getString("decision"), rs.getString("stage"),
					rs.getString("result")))
			.optional()
			.orElse(null);
	}

}
