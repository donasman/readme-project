package com.readme;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/**
 * 통합 테스트의 메일 발송. 보내지 않고 모아 둔다. @IntegrationTest 를 붙인 테스트에서 주입받아 쓴다.
 *
 * <p>테스트끼리 서버를 나눠 쓰므로 받는 주소로 골라 읽는다. 테스트마다 다른 이메일을 쓴다.
 * dev 프로필(메일 수신기 주소가 있다)에서도 이것이 먼저다(@Primary).
 */
@Primary
public class TestMailbox implements MailSender {

	private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

	private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(SimpleMailMessage message) {
		this.sent.add(new SimpleMailMessage(message));
	}

	@Override
	public void send(SimpleMailMessage... messages) {
		Arrays.stream(messages).forEach(this::send);
	}

	/** 이 주소로 보낸 메일들. 보낸 순서다. 주소는 대소문자를 가리지 않는다. */
	public List<SimpleMailMessage> to(String email) {
		return this.sent.stream()
			.filter((message) -> message.getTo() != null
					&& Arrays.stream(message.getTo()).anyMatch((to) -> to.equalsIgnoreCase(email)))
			.toList();
	}

	/** 이 주소로 마지막에 보낸 메일. 없으면 테스트가 실패한다. */
	public SimpleMailMessage lastTo(String email) {
		List<SimpleMailMessage> messages = to(email);
		if (messages.isEmpty()) {
			throw new AssertionError(email + " 로 보낸 메일이 없다");
		}
		return messages.getLast();
	}

	/** 이 주소로 마지막에 보낸 메일의 링크에 든 토큰. */
	public String lastTokenTo(String email) {
		Matcher matcher = TOKEN.matcher(lastTo(email).getText());
		if (!matcher.find()) {
			throw new AssertionError(email + " 로 보낸 마지막 메일에 링크가 없다");
		}
		return matcher.group(1);
	}

}
