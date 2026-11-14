package com.readme.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.readme.auth.EmailToken.Purpose;
import com.readme.common.error.ApiException;
import com.readme.common.token.Tokens;

/**
 * 이메일·비밀번호 계정: 가입, 이메일 인증, 로그인 확인, 비밀번호 재설정(로그인 설계 4절).
 *
 * <p>메일과 세션 끊기는 DB 의 변경이 커밋된 뒤에 한다. 커밋되지 않은 링크를 메일로 보내지 않기 위해서다.
 */
@Service
class AccountService {

	/** 가입 확인 링크(가정 L5). */
	static final Duration VERIFY_LINK_LIFETIME = Duration.ofHours(24);

	/** 비밀번호 재설정 링크(가정 L5). */
	static final Duration RESET_LINK_LIFETIME = Duration.ofHours(1);

	/** 같은 사람에게 같은 종류의 링크를 다시 보내기까지의 시간(2026-11-15 정함). */
	static final Duration LINK_COOLDOWN = Duration.ofMinutes(1);

	/** 같은 사람에게 같은 종류의 링크를 최근 24시간 동안 보내는 최대 수(2026-11-15 정함). */
	static final int LINKS_PER_DAY = 5;

	private final AccountRepository accounts;

	private final AccountQueries queries;

	private final EmailTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final AuthMails mails;

	private final LoginThrottle loginThrottle;

	private final Clock clock;

	/** 없는 이메일로 로그인할 때도 비밀번호를 견주는 데 드는 시간이 같도록 쓰는 해시. */
	private final String dummyHash;

	AccountService(AccountRepository accounts, AccountQueries queries, EmailTokenRepository tokens,
			PasswordEncoder passwordEncoder, FindByIndexNameSessionRepository<? extends Session> sessions,
			AuthMails mails, LoginThrottle loginThrottle, Clock clock) {
		this.accounts = accounts;
		this.queries = queries;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.mails = mails;
		this.loginThrottle = loginThrottle;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/**
	 * 인증 전 계정을 만들고 가입 확인 메일을 보낸다. 인증을 마친 계정이 그 이메일을 쓰면 409 EMAIL_TAKEN.
	 * 인증 전 계정이 이미 있으면 새로 넣은 이름과 비밀번호로 바꾸고 새 링크를 보낸다(2026-11-14 정함).
	 * 남이 내 이메일로 가입만 해 두어도 내가 가입할 수 있고, 그 사람의 비밀번호와 링크는 쓸 수 없게 된다.
	 * 링크를 보내는 제한(sendLink)에 걸리면 이름과 비밀번호만 바꾸고, 전에 보낸 링크가 그대로 인증을 마친다.
	 */
	@Transactional
	void signup(String name, String email, String password) {
		String hash = this.passwordEncoder.encode(password);
		Instant now = this.clock.instant();
		if (this.accounts.insertIfEmailIsNew(email, name, hash, now) == 0) {
			Credentials existing = this.queries.findCredentials(email).orElseThrow();
			if (existing.verified() || this.accounts.overwriteUnverified(existing.id(), name, hash, now) == 0) {
				throw new ApiException(AuthErrorCode.EMAIL_TAKEN);
			}
		}
		sendLink(this.queries.findCredentials(email).orElseThrow(), Purpose.VERIFY_EMAIL, now);
	}

	/** 가입 확인 링크로 인증을 마친다. 쓸 수 없는 링크면 410 LINK_EXPIRED. */
	@Transactional
	void verifyEmail(String token) {
		Instant now = this.clock.instant();
		this.accounts.markVerified(use(token, Purpose.VERIFY_EMAIL, now).userId(), now);
	}

	/** 인증 전 계정에만 새 확인 메일을 보낸다. 계정이 없거나 이미 인증했어도 같은 응답이다. */
	@Transactional
	void resendVerification(String email) {
		this.queries.findCredentials(email)
			.filter((account) -> !account.verified())
			.ifPresent((account) -> sendLink(account, Purpose.VERIFY_EMAIL, this.clock.instant()));
	}

	/**
	 * 이메일과 비밀번호를 확인하고 사용자 id 를 돌려준다. 계정이 없거나, 비밀번호가 없거나(구글로만 가입), 틀리면 모두
	 * 401 INVALID_CREDENTIALS. 비밀번호가 맞아도 인증 전이면 403 EMAIL_NOT_VERIFIED(가정 L4).
	 * 그 이메일로 15분 안에 10번 로그인하지 못했으면 비밀번호를 보기 전에 429 TOO_MANY_ATTEMPTS(LoginThrottle).
	 */
	@Transactional
	long login(String email, String password) {
		// 세는 글자와 계정을 찾는 글자가 같아야 한다. "İ"를 자바는 i 와 위 점(U+0307)으로, PostgreSQL 은 i 로 낮춘다.
		// 따로 낮추면 같은 계정을 두고 세는 칸만 새로 생긴다

		String key = email.toLowerCase(Locale.ROOT);
		this.loginThrottle.countAttempt(key);
		Credentials account = this.queries.findCredentials(key).orElse(null);
		String hash = (account != null) ? account.passwordHash() : null;
		boolean matches = PasswordRule.Validator.fitsHash(password)
				&& this.passwordEncoder.matches(password, (hash != null) ? hash : this.dummyHash) && hash != null;
		if (!matches) {
			throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		if (!account.verified()) {
			throw new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED);
		}
		this.loginThrottle.clear(key);
		this.accounts.recordLogin(account.id(), this.clock.instant());
		return account.id();
	}

	/**
	 * 재설정 메일을 보낸다. 계정이 없어도 같은 응답이다. 구글로만 가입한 계정도 받는다. 재설정을 마치면 비밀번호가 생긴다.
	 */
	@Transactional
	void requestPasswordReset(String email) {
		this.queries.findCredentials(email)
			.ifPresent((account) -> sendLink(account, Purpose.RESET_PASSWORD, this.clock.instant()));
	}

	/** 재설정 링크를 아직 쓸 수 있는지. 쓸 수 없으면 410 LINK_EXPIRED. 링크를 쓰지는 않는다. */
	@Transactional(readOnly = true)
	void checkResetLink(String token) {
		usable(token, Purpose.RESET_PASSWORD, this.clock.instant());
	}

	/**
	 * 새 비밀번호로 바꾸고, 그 계정의 로그인을 모두 끊는다. 인증 전 계정이면 인증도 마친다(메일을 받았다는 뜻이라서).
	 * 그 계정의 다른 재설정 링크도 쓸 수 없게 된다.
	 */
	@Transactional
	void resetPassword(String token, String password) {
		Instant now = this.clock.instant();
		long userId = use(token, Purpose.RESET_PASSWORD, now).userId();
		this.accounts.changePassword(userId, this.passwordEncoder.encode(password), now);
		this.tokens.cancelUnused(userId, Purpose.RESET_PASSWORD.name(), now);
		afterCommit(() -> endSessions(userId));
	}

	/**
	 * 전에 보낸 같은 종류의 링크를 쓸 수 없게 하고, 새 링크를 만들어 커밋 뒤에 메일로 보낸다.
	 * 1분 안에 보냈거나 최근 24시간 동안 5번 보냈으면 아무것도 하지 않는다. 전에 보낸 링크는 그대로 쓸 수 있고, 응답도 같다.
	 */
	private void sendLink(Credentials account, Purpose purpose, Instant now) {
		// shortcut: simultaneous requests (up to the pool size, 5) can all pass these counts and each send a link,
		// lock the users row before counting if that gets abused
		if (this.tokens.countCreatedSince(account.id(), purpose, now.minus(LINK_COOLDOWN)) > 0
				|| this.tokens.countCreatedSince(account.id(), purpose, now.minus(Duration.ofDays(1))) >= LINKS_PER_DAY) {
			return;
		}
		this.tokens.cancelUnused(account.id(), purpose.name(), now);
		String token = Tokens.newToken();
		Duration lifetime = (purpose == Purpose.VERIFY_EMAIL) ? VERIFY_LINK_LIFETIME : RESET_LINK_LIFETIME;
		this.tokens.save(new EmailToken(account.id(), purpose, Tokens.sha256Hex(token), now.plus(lifetime), now));
		afterCommit(() -> {
			if (purpose == Purpose.VERIFY_EMAIL) {
				this.mails.sendVerification(account, token);
			}
			else {
				this.mails.sendPasswordReset(account, token);
			}
		});
	}

	private EmailToken usable(String token, Purpose purpose, Instant now) {
		return this.tokens.findUsable(Tokens.sha256Hex(token), purpose, now)
			.orElseThrow(() -> new ApiException(AuthErrorCode.LINK_EXPIRED));
	}

	/** 링크를 쓴다. 같은 링크를 두 번 눌러도 한 번만 된다. */
	private EmailToken use(String token, Purpose purpose, Instant now) {
		EmailToken emailToken = usable(token, purpose, now);
		if (this.tokens.use(emailToken.id(), now) == 0) {
			throw new ApiException(AuthErrorCode.LINK_EXPIRED);
		}
		return emailToken;
	}

	/** 세션의 주인 이름은 사용자 id 다(LoginUser.getName). */
	private void endSessions(long userId) {
		this.sessions.findByPrincipalName(String.valueOf(userId)).keySet().forEach(this.sessions::deleteById);
	}

	private static void afterCommit(Runnable action) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

}
