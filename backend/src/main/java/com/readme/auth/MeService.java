package com.readme.auth;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

/**
 * 로그인한 사람이 자기 계정을 고친다: 표시 이름, 비밀번호 바꾸기, 탈퇴(로그인 설계 4절, 2026-11-15).
 *
 * <p>세션 끊기는 AccountService 처럼 DB 의 변경이 커밋된 뒤에 한다. 되돌려진 변경 때문에 로그인이 풀리지 않게 하기 위해서다.
 */
@Service
class MeService {

	private final AccountRepository accounts;

	private final AccountQueries queries;

	private final PasswordEncoder passwordEncoder;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	MeService(AccountRepository accounts, AccountQueries queries, PasswordEncoder passwordEncoder,
			FindByIndexNameSessionRepository<? extends Session> sessions, ApplicationEventPublisher events,
			Clock clock) {
		this.accounts = accounts;
		this.queries = queries;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.events = events;
		this.clock = clock;
	}

	/** 표시 이름을 바꾼다. 세션은 남았는데 계정이 지워졌으면 401. */
	@Transactional
	void rename(long userId, String name) {
		if (this.accounts.rename(userId, name, this.clock.instant()) == 0) {
			throw new ApiException(CommonErrorCode.UNAUTHENTICATED);
		}
	}

	/**
	 * 지금 비밀번호를 확인하고 새 비밀번호로 바꾼다. 이 세션(keepSessionId)은 두고 이 사람의 다른 세션은 모두 끊는다.
	 * 비밀번호가 없는 계정(구글로만 가입)은 409 NO_PASSWORD, 지금 비밀번호가 틀리면 400 WRONG_PASSWORD.
	 */
	@Transactional
	void changePassword(long userId, String currentPassword, String newPassword, String keepSessionId) {
		Credentials account = credentials(userId);
		if (account.passwordHash() == null) {
			throw new ApiException(AuthErrorCode.NO_PASSWORD);
		}
		// shortcut: 지금 비밀번호를 틀려도 세지 않아, 세션을 가진 사람은 계속 맞혀 볼 수 있다(로그인 실패 제한은 로그인만 센다),
		// 그런 시도가 보이면 로그인처럼 사람마다 틀린 수를 센다
		if (!matches(currentPassword, account.passwordHash())) {
			throw new ApiException(AuthErrorCode.WRONG_PASSWORD);
		}
		this.accounts.changePassword(userId, this.passwordEncoder.encode(newPassword), this.clock.instant());
		afterCommit(() -> endSessions(userId, keepSessionId));
	}

	/**
	 * 탈퇴. 비밀번호가 있는 계정은 비밀번호(틀리면 400 WRONG_PASSWORD), 없는 계정은 계정의 이메일(대소문자 무시, 틀리면 400
	 * WRONG_EMAIL)로 확인한다.
	 *
	 * <p>한 트랜잭션에서: AccountDeleting 을 내 받는 기능이 자기 테이블을 정리하게 하고(group 이 그룹장을 넘긴다),
	 * users 행을 지운다. 나머지는 외래 키가 지우거나(cascade) 비운다(set null). 커밋 뒤에 이 사람의 세션을 모두 지운다.
	 */
	@Transactional
	void withdraw(long userId, String password, String email) {
		Credentials account = credentials(userId);
		if (account.passwordHash() != null) {
			// shortcut: 비밀번호 바꾸기처럼 틀린 수를 세지 않는다, 그런 시도가 보이면 함께 센다
			if (!matches(password, account.passwordHash())) {
				throw new ApiException(AuthErrorCode.WRONG_PASSWORD);
			}
		}
		else if (email == null || !email.equalsIgnoreCase(account.email())) {
			throw new ApiException(AuthErrorCode.WRONG_EMAIL);
		}
		this.events.publishEvent(new AccountDeleting(userId));
		this.accounts.deleteById(userId);
		afterCommit(() -> endSessions(userId, null));
	}

	private Credentials credentials(long userId) {
		return this.queries.findCredentials(userId).orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHENTICATED));
	}

	/** 해시가 받는 길이보다 긴 비밀번호는 견주지 못하므로 틀린 것으로 본다(로그인과 같다). */
	private boolean matches(String password, String hash) {
		return password != null && PasswordRule.Validator.fitsHash(password) && this.passwordEncoder.matches(password, hash);
	}

	/** 이 사람의 세션을 keepSessionId 만 두고 지운다. null 이면 모두 지운다. 세션의 주인 이름은 사용자 id 다(LoginUser.getName). */
	private void endSessions(long userId, String keepSessionId) {
		this.sessions.findByPrincipalName(String.valueOf(userId))
			.keySet()
			.stream()
			.filter((sessionId) -> !sessionId.equals(keepSessionId))
			.forEach(this.sessions::deleteById);
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
