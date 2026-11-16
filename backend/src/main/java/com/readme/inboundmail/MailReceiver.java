package com.readme.inboundmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.readme.inboundmail.SenderCheck.Verdict;

/**
 * 서명을 확인한 메일을 받는다(메일 설계 M4~M9). 받는 주소로 사람을 찾고, 보낸 사람을 확인하고, 본문 글만 저장한다.
 * 분류는 커밋 뒤 {@link MailClassifier} 가 한다. 받지 않는 메일은 저장하지 않고 기록만 남긴다.
 */
@Component
class MailReceiver {

	private static final Logger log = LoggerFactory.getLogger(MailReceiver.class);

	/** 저장해 두는 본문 글의 상한. 분류에는 앞 MailClassifier.BODY_CHARS 만 보낸다 */
	static final int MAX_BODY_CHARS = 20_000;

	/** Gmail 전달 확인 메일 제목의 확인 코드: "(#123456789) Gmail 전달 확인 - …" */
	private static final Pattern GMAIL_CODE = Pattern.compile("\\(#(\\d{4,20})\\)");

	private final InboundMailProperties properties;

	private final MailInboxRepository inboxes;

	private final RegisteredSenderRepository senders;

	private final InboundMailRepository mails;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	MailReceiver(InboundMailProperties properties, MailInboxRepository inboxes, RegisteredSenderRepository senders,
			InboundMailRepository mails, ApplicationEventPublisher events, Clock clock) {
		this.properties = properties;
		this.inboxes = inboxes;
		this.senders = senders;
		this.mails = mails;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	void receive(String envelopeFrom, String envelopeTo, String authResults, byte[] raw) {
		Optional<Long> owner = owner(envelopeTo);
		if (owner.isEmpty()) {
			log.info("모르는 수신 주소로 온 메일을 버렸다");
			return;
		}
		long userId = owner.get();
		RawMail mail = RawMail.parse(raw);
		Verdict verdict = SenderCheck.check(envelopeFrom, mail.fromAddress(), AuthResults.parse(authResults),
				new HashSet<>(this.senders.findEmails(userId)));
		if (verdict == Verdict.REJECT) {
			log.info("사용자 {} 의 보내는 주소로 확인되지 않은 메일을 버렸다", userId);
			// 실제 메일로 확인할 때만 켠다(메일 설계 6절 4번). 주소가 들어 있어 평소에는 남기지 않는다
			log.debug("버린 메일: 봉투 From {}, From {}, 인증 결과 {}", envelopeFrom, mail.fromAddress(), authResults);
			return;
		}
		String key = sha256((mail.messageId() != null) ? mail.messageId().getBytes(StandardCharsets.UTF_8) : raw);
		String subject = cut(mail.subject(), 500);
		boolean forwarding = verdict == Verdict.GMAIL_FORWARDING;
		int inserted = this.mails.insertIfNew(userId, key, cut(mail.sender(), 320), subject, this.clock.instant(),
				forwarding ? "FORWARDING" : "WAITING", forwarding ? null : cut(mail.text(), MAX_BODY_CHARS),
				forwarding ? gmailCode(subject) : null);
		if (inserted == 1 && !forwarding) {
			this.events.publishEvent(new MailClassifier.Received(this.mails.findIdByKey(userId, key).orElseThrow()));
		}
	}

	/** 봉투 To 의 @ 앞(+ 뒤는 뗀다)을 토큰으로 수신 주소의 주인을 찾는다. 수신 도메인이 아니면 없다. */
	private Optional<Long> owner(String envelopeTo) {
		String to = SenderCheck.normalize(envelopeTo);
		if (to == null || !this.properties.hasDomain()) {
			return Optional.empty();
		}
		int at = to.lastIndexOf('@');
		if (!to.substring(at + 1).equals(this.properties.domain().toLowerCase(Locale.ROOT))) {
			return Optional.empty();
		}
		String local = to.substring(0, at);
		int plus = local.indexOf('+');
		return this.inboxes.findUserIdByToken((plus >= 0) ? local.substring(0, plus) : local);
	}

	static String gmailCode(String subject) {
		Matcher matcher = GMAIL_CODE.matcher(subject);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String cut(String value, int max) {
		return (value.length() > max) ? value.substring(0, max) : value;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
