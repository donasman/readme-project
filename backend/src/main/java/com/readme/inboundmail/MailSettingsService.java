package com.readme.inboundmail;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;

/** 수신 주소와 보내는 주소를 바꾼다(메일 설계 M4, M5, 4.2). */
@Service
class MailSettingsService {

	/** 보내는 주소의 상한 */
	static final int MAX_SENDERS = 10;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

	/** 토큰 길이. 36^16 ≈ 2^82 라 추측할 수 없다 */
	static final int TOKEN_LENGTH = 16;

	private final MailInboxRepository inboxes;

	private final RegisteredSenderRepository senders;

	private final JdbcClient jdbc;

	private final Clock clock;

	MailSettingsService(MailInboxRepository inboxes, RegisteredSenderRepository senders, JdbcClient jdbc, Clock clock) {
		this.inboxes = inboxes;
		this.senders = senders;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** 수신 주소가 없으면 만들고, 처음 만들 때 계정 이메일을 보내는 주소에 넣는다. */
	@Transactional
	void ensure(long userId) {
		if (this.inboxes.insertIfAbsent(userId, newToken(), this.clock.instant()) == 1) {
			String email = this.jdbc.sql("select email from users where id = :userId")
				.param("userId", userId)
				.query(String.class)
				.single();
			this.senders.insertIfAbsent(userId, email.toLowerCase(Locale.ROOT), this.clock.instant());
		}
	}

	/** 수신 주소를 새로 만든다. 앞 주소로 온 메일은 바로 받지 않는다. */
	@Transactional
	void renew(long userId) {
		ensure(userId);
		this.inboxes.renew(userId, newToken(), this.clock.instant());
	}

	/**
	 * 보내는 주소를 더한다. 이미 있으면 그대로 둔다. 10개를 넘으면 409 TOO_MANY_SENDERS.
	 *
	 * <p>shortcut: 같은 사람이 동시에 더하면 10개를 조금 넘을 수 있다, 받는 데는 문제가 없다
	 */
	@Transactional
	void addSender(long userId, String email) {
		String key = email.strip().toLowerCase(Locale.ROOT);
		if (this.senders.existsById(new RegisteredSender.Key(userId, key))) {
			return;
		}
		if (this.senders.countByUserId(userId) >= MAX_SENDERS) {
			throw new ApiException(InboundMailErrorCode.TOO_MANY_SENDERS);
		}
		this.senders.insertIfAbsent(userId, key, this.clock.instant());
	}

	/** 보내는 주소를 지운다. 없으면 아무 일도 하지 않는다. */
	@Transactional
	void removeSender(long userId, String email) {
		this.senders.deleteOne(userId, email.strip().toLowerCase(Locale.ROOT));
	}

	static String newToken() {
		StringBuilder token = new StringBuilder(TOKEN_LENGTH);
		for (int i = 0; i < TOKEN_LENGTH; i++) {
			token.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
		}
		return token.toString();
	}

}
