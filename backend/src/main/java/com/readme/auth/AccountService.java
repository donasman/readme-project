package com.readme.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.readme.auth.AuthRequests.SignupExtras;
import com.readme.auth.EmailToken.Purpose;
import com.readme.common.error.ApiException;
import com.readme.common.token.Tokens;

/**
 * 계정: 가입, 이메일 인증, 로그인 확인, 비밀번호 재설정(로그인 설계 4절), 구글 로그인의 계정 찾기·연결·만들기(3.2),
 * 로그인한 채로 구글 연결하기(4.1), 가입 값(생년월일·성별)과 동의(4.1, 2026-11-16).
 *
 * <p>가입 값은 profile 이 프로필에 넣는다. auth 는 profile 을 불러오지 않고 이벤트(SignupValues, GoogleValues)를 낸다.
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

	/** 같은 사람에게 같은 종류의 링크를 다시 보내기까지의 시간(2026-11-15 정함). */
	static final Duration LINK_COOLDOWN = Duration.ofMinutes(1);

	/** 같은 사람에게 같은 종류의 링크를 최근 24시간 동안 보내는 최대 수(2026-11-15 정함). */
	static final int LINKS_PER_DAY = 5;

	/** LINKS_PER_DAY 를 세는 기간. 이보다 오래된 링크 행은 세지 않으므로 deleteSpentLinks 가 지울 수 있다. */
	static final Duration LINK_COUNT_WINDOW = Duration.ofDays(1);

	private final AccountRepository accounts;

	private final AccountQueries queries;

	private final EmailTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final AuthMails mails;

	private final LoginThrottle loginThrottle;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** 없는 이메일로 로그인할 때도 비밀번호를 견주는 데 드는 시간이 같도록 쓰는 해시. */
	private final String dummyHash;

	AccountService(AccountRepository accounts, AccountQueries queries, EmailTokenRepository tokens,
			PasswordEncoder passwordEncoder, FindByIndexNameSessionRepository<? extends Session> sessions,
			AuthMails mails, LoginThrottle loginThrottle, ApplicationEventPublisher events, Clock clock) {
		this.accounts = accounts;
		this.queries = queries;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.mails = mails;
		this.loginThrottle = loginThrottle;
		this.events = events;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/**
	 * 인증 전 계정을 만들고 가입 확인 메일을 보낸다. 인증을 마친 계정이 그 이메일을 쓰면 409 EMAIL_TAKEN.
	 * 인증 전 계정이 이미 있으면 새로 넣은 이름과 비밀번호로 바꾸고 새 링크를 보낸다(2026-11-14 정함).
	 * 남이 내 이메일로 가입만 해 두어도 내가 가입할 수 있고, 그 사람의 비밀번호와 링크는 쓸 수 없게 된다.
	 * 링크를 보내는 제한(sendLink)에 걸리면 이름과 비밀번호만 바꾸고, 전에 보낸 링크가 그대로 인증을 마친다.
	 *
	 * <p>동의는 users 에 남기고, 생년월일·성별은 profile 이 프로필에 넣는다(SignupValues). 인증 전 계정을 덮어쓰면 앞서 가입한
	 * 사람의 동의와 생년월일·성별도 이번 값으로 바뀐다.
	 */
	@Transactional
	void signup(String name, String email, String password, SignupExtras extras) {
		String hash = this.passwordEncoder.encode(password);
		Instant now = this.clock.instant();
		boolean created = this.accounts.insertIfEmailIsNew(email, name, hash, extras.birthGenderConsent(),
				extras.eventNoticeConsent(), now) == 1;
		if (!created) {
			Credentials existing = this.queries.findCredentials(email).orElseThrow();
			if (existing.verified() || this.accounts.overwriteUnverified(existing.id(), name, hash,
					extras.birthGenderConsent(), extras.eventNoticeConsent(), now) == 0) {
				throw new ApiException(AuthErrorCode.EMAIL_TAKEN);
			}
		}
		Credentials account = this.queries.findCredentials(email).orElseThrow();
		// 새 계정에 값이 없으면 넣을 것이 없다. 덮어쓴 계정은 앞서 가입한 사람의 값을 지워야 하므로 늘 낸다
		if (!created || extras.birthDate() != null || extras.gender() != null) {
			this.events.publishEvent(new SignupValues(account.id(), extras.birthDate(), extras.gender()));
		}
		sendLink(account, Purpose.VERIFY_EMAIL, now);
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
	 * 구글이 확인한 사람의 계정 id. 연결할 계정이 없으면 비어 있다(로그인 설계 3.2, 가정 L3).
	 * <ol>
	 * <li>이 구글 계정(sub)이 연결된 계정이 있으면 그 계정이다.</li>
	 * <li>없으면 구글이 이메일을 확인했을 때만(email_verified) 같은 이메일(대소문자 무시)의 계정에 연결한다. 계정이 없으면
	 * 인증을 마친, 비밀번호 없는 계정을 만든다. 인증 전 계정이면 비밀번호를 지우고, 이름을 구글의 것으로 바꾸고, 인증을 마치고,
	 * 보낸 가입 확인 링크를 쓸 수 없게 한 뒤 연결한다. 그 이메일로 먼저 가입만 해 둔 남이 비밀번호로 들어오지 못하게 한다.
	 * 그 사람의 동의와 생년월일·성별도 지운다(SignupValues).</li>
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
				this.tokens.cancelUnused(account.id(), Purpose.VERIFY_EMAIL.name(), now);
				this.events.publishEvent(new SignupValues(account.id(), null, null));
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

	/** 로그인한 채로 구글 연결하기의 결과(로그인 설계 4.1). */
	enum GoogleLink {
		/** 연결했다. 그 구글 계정이 이미 이 계정에 연결되어 있었어도 같다. */
		LINKED,
		/** 그 구글 계정이 다른 계정에 연결되어 있다. */
		IN_USE,
		/** 이 계정에 다른 구글 계정이 이미 연결되어 있다. */
		HAS_GOOGLE
	}

	/**
	 * 로그인한 사람의 계정에 구글 계정(sub)을 연결한다(로그인 설계 4.1). 구글이 이 브라우저에서 방금 확인한 구글 계정이라서
	 * 이메일과 email_verified 는 보지 않는다. email 은 provider_email 에 넣을 뿐이다. 있는 연결을 옮기거나 바꾸지 않는다.
	 */
	@Transactional
	GoogleLink linkGoogle(long userId, String sub, String email) {
		if (this.accounts.linkGoogle(userId, sub, email, this.clock.instant()) == 1) {
			return GoogleLink.LINKED;
		}
		Optional<Long> owner = this.queries.findGoogleUser(sub);
		GoogleLink result = owner.isEmpty() ? GoogleLink.HAS_GOOGLE
				: (owner.get() == userId) ? GoogleLink.LINKED : GoogleLink.IN_USE;
		log.info("구글 연결: 사용자 {} 는 {}", userId, result);
		return result;
	}

	/**
	 * 구글 동의 화면에서 생년월일이나 성별을 허락한 사람(로그인 설계 4.1). 생년월일·성별의 수집·이용 동의를 남기고(처음 동의한 때를 둔다),
	 * People API 로 읽은 값이 있으면 profile 이 프로필의 빈 칸에 넣는다(GoogleValues). 이미 있는 값은 덮어쓰지 않는다.
	 *
	 * @param gender MALE, FEMALE, null
	 */
	@Transactional
	void receiveGoogleValues(long userId, LocalDate birthDate, String gender) {
		this.accounts.recordBirthGenderConsent(userId, this.clock.instant());
		if (birthDate != null || gender != null) {
			this.events.publishEvent(new GoogleValues(userId, birthDate, gender));
		}
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
	 * 그 계정의 다른 재설정 링크도 쓸 수 없게 된다. 로그인 실패로 막혀 있었으면 풀어 새 비밀번호로 바로 들어오게 한다.
	 */
	@Transactional
	void resetPassword(String token, String password) {
		Instant now = this.clock.instant();
		long userId = use(token, Purpose.RESET_PASSWORD, now).userId();
		this.accounts.changePassword(userId, this.passwordEncoder.encode(password), now);
		this.tokens.cancelUnused(userId, Purpose.RESET_PASSWORD.name(), now);
		this.loginThrottle.clear(this.queries.findMe(userId).orElseThrow().email().toLowerCase(Locale.ROOT));
		afterCommit(() -> endSessions(userId));
	}

	/**
	 * 매일 04:00(서울)에 더 쓸 데가 없는 링크 행을 지운다(2026-11-16 정함). 24시간 안에 만든 행은 sendLink 가 세므로 남긴다.
	 * 그보다 오래되었어도 아직 쓸 수 있는 링크는 남긴다. 서버가 여러 대면 모두 돌지만 같은 행을 지우므로 괜찮다.
	 */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	@Transactional
	void deleteSpentLinks() {
		Instant now = this.clock.instant();
		log.info("지난 링크 {}개를 지웠다", this.tokens.deleteSpent(now.minus(LINK_COUNT_WINDOW), now));
	}

	/**
	 * 전에 보낸 같은 종류의 링크를 쓸 수 없게 하고, 새 링크를 만들어 커밋 뒤에 메일로 보낸다.
	 * 1분 안에 보냈거나 최근 24시간 동안 5번 보냈으면 아무것도 하지 않는다. 전에 보낸 링크는 그대로 쓸 수 있고, 응답도 같다.
	 */
	private void sendLink(Credentials account, Purpose purpose, Instant now) {
		// shortcut: simultaneous requests (up to the pool size, 5) can all pass these counts and each send a link,
		// lock the users row before counting if that gets abused
		if (this.tokens.countCreatedSince(account.id(), purpose, now.minus(LINK_COOLDOWN)) > 0
				|| this.tokens.countCreatedSince(account.id(), purpose, now.minus(LINK_COUNT_WINDOW)) >= LINKS_PER_DAY) {
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
