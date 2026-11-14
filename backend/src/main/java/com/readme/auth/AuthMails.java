package com.readme.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import com.readme.common.config.AppProperties;

/**
 * 가입 확인·비밀번호 재설정 메일. 링크는 프런트의 화면 주소다(화면·API 대응 문서 4절).
 *
 * <p>보내지 못해도 오류를 내지 않고 기록만 남긴다. 계정은 이미 저장되었고, 메일은 "다시 받기"로 다시 보낼 수 있다.
 * 기록에는 토큰과 이메일을 남기지 않는다.
 */
@Component
class AuthMails {

	private static final Logger log = LoggerFactory.getLogger(AuthMails.class);

	private final MailSender mailSender;

	private final AppProperties app;

	AuthMails(MailSender mailSender, AppProperties app) {
		this.mailSender = mailSender;
		this.app = app;
	}

	void sendVerification(Credentials account, String token) {
		send(account, "[리드미] 이메일을 확인해 주세요", """
				리드미 가입을 마치려면 아래 링크를 눌러 이메일을 확인해 주세요.

				%s/verify-email?token=%s

				링크는 24시간 동안 쓸 수 있습니다. 가입한 적이 없다면 이 메일은 무시해 주세요.
				""".formatted(this.app.frontendOrigin(), token));
	}

	void sendPasswordReset(Credentials account, String token) {
		send(account, "[리드미] 비밀번호 재설정", """
				아래 링크에서 새 비밀번호를 정해 주세요.

				%s/password-reset/new?token=%s

				링크는 1시간 동안 쓸 수 있습니다. 비밀번호를 바꾸면 다른 기기의 로그인이 모두 풀립니다.
				요청한 적이 없다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않습니다.
				""".formatted(this.app.frontendOrigin(), token));
	}

	private void send(Credentials account, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(this.app.mailFrom());
		message.setTo(account.email());
		message.setSubject(subject);
		message.setText(text);
		try {
			this.mailSender.send(message);
		}
		catch (MailException ex) {
			log.warn("메일을 보내지 못했다(사용자 {}, {}): {}", account.id(), subject, ex.getClass().getSimpleName());
		}
	}

}
