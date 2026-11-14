package com.readme.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 계정: 가입, 이메일 인증, 로그인 확인, 비밀번호 재설정(로그인 설계 4절), 구글 로그인의 계정 찾기·연결·만들기(3.2).
 *
 * <p>메일과 세션 끊기는 DB 의 변경이 커밋된 뒤에 한다. 커밋되지 않은 링크를 메일로 보내지 않기 위해서다.
 */
@Service
class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	/** 가입 확인 링크(가정 L5). */
	static final Duration VERIFY_LINK_LIFETIME = Duration.ofHours(24);

	/** 비밀번호 재설정 링크(가정 L5). */
	static final Duration RESET_LINK_LIFETIME = Duration.ofHours(1);

	private final AccountRepository accounts;

	private final AccountQueries queries;

	private final EmailTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final AuthMails mails;

	private final Clock clock;

	/** 없는 이메일로 로그인할 때도 비밀번호를 견주는 데 드는 시간이 같도록 쓰는 해시. */
	private final String dummyHash;

	AccountService(AccountRepository accounts, AccountQueries queries, EmailTokenRepository tokens,
			PasswordEncoder passwordEncoder, FindByIndexNameSessionRepository<? extends Session> sessions,
			AuthMails mails, Clock clock) {
		this.accounts = accounts;
		this.queries = queries;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.mails = mails;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/**
	 * 인증 전 계정을 만들고 가입 확인 메일을 보낸다. 인증을 마친 계정이 그 이메일을 쓰면 409 EMAIL_TAKEN.
	 * 인증 전 계정이 이미 있으면 새로 넣은 이름과 비밀번호로 바꾸고 새 링크를 보낸다(2026-11-14 정함).
	 * 남이 내 이메일로 가입만 해 두어도 내가 가입할 수 있고, 그 사람의 비밀번호와 링크는 쓸 수 없게 된다.
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
	 */
	@Transactional
	long login(String email, String password) {
		Credentials account = this.queries.findCredentials(email).orElse(null);
		String hash = (account != null) ? account.passwordHash() : null;
		boolean matches = PasswordRule.Validator.fitsHash(password)
				&& this.passwordEncoder.matches(password, (hash != null) ? hash : this.dummyHash) && hash != null;
		if (!matches) {
			throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		if (!account.verified()) {
			throw new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED);
		}
		this.accounts.recordLogin(account.id(), this.clock.instant());
		return account.id();
	}

	/**
	 * 구글이 확인한 사람의 계정 id. 연결할 계정이 없으면 비어 있다(로그인 설계 3.2, 가정 L3).
	 * <ol>
	 * <li>이 구글 계정(sub)이 연결된 계정이 있으면 그 계정이다.</li>
	 * <li>없으면 구글이 이메일을 확인했을 때만(email_verified) 같은 이메일(대소문자 무시)의 계정에 연결한다. 계정이 없으면
	 * 인증을 마친, 비밀번호 없는 계정을 만든다. 인증 전 계정이면 비밀번호를 지우고, 이름을 구글의 것으로 바꾸고, 인증을 마치고,
	 * 보낸 가입 확인 링크를 지운 뒤 연결한다. 그 이메일로 먼저 가입만 해 둔 남이 비밀번호로 들어오지 못하게 한다.</li>
	 * <li>구글이 이메일을 확인하지 않았거나, 그 계정에 다른 구글 계정이 이미 연결되어 있으면 비어 있다.</li>
	 * </ol>
	 *
	 * @param name 구글의 이름. 없으면 이메일의 @ 앞을 쓴다
	 */
	@Transactional
	OptionalLong loginWithGoogle(String sub, String email, boolean emailVerified, String name) {
		Instant now = this.clock.instant();
		Long userId = this.queries.findGoogleUser(sub).orElse(null);
		if (userId == null) {
			if (!emailVerified || email == null) {
				log.info("구글 로그인 거부: 구글이 확인하지 않은 이메일");
				return OptionalLong.empty();
			}
			String displayName = displayName(name, email);
			this.accounts.insertGoogleUserIfEmailIsNew(email, displayName, now);
			Credentials account = this.queries.findCredentials(email).orElseThrow();
			if (!account.verified() && this.accounts.takeOverUnverified(account.id(), displayName, now) == 1) {
				this.tokens.deleteUnused(account.id(), Purpose.VERIFY_EMAIL.name());
			}
			if (this.accounts.linkGoogle(account.id(), sub, email, now) == 0
					&& !this.queries.findGoogleUser(sub).equals(Optional.of(account.id()))) {
				log.info("구글 로그인 거부: 사용자 {} 에 다른 구글 계정이 연결되어 있다", account.id());
				return OptionalLong.empty();
			}
			userId = account.id();
		}
		this.accounts.recordLogin(userId, now);
		return OptionalLong.of(userId);
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
		this.tokens.deleteUnused(userId, Purpose.RESET_PASSWORD.name());
		afterCommit(() -> endSessions(userId));
	}

	/** 전에 보낸 같은 종류의 링크를 지우고, 새 링크를 만들어 커밋 뒤에 메일로 보낸다. */
	private void sendLink(Credentials account, Purpose purpose, Instant now) {
		this.tokens.deleteUnused(account.id(), purpose.name());
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

	/** 구글의 이름, 없으면 이메일의 @ 앞. users.display_name 의 50자에 맞춰 자른다. */
	private static String displayName(String name, String email) {
		String value = (name != null && !name.isBlank()) ? name.strip() : email.substring(0, Math.max(0, email.indexOf('@')));
		return value.substring(0, value.offsetByCodePoints(0, Math.min(50, value.codePointCount(0, value.length()))));
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
