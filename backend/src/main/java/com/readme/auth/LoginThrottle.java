package com.readme.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;

/**
 * 이메일마다 로그인 실패를 센다(2026-11-15 정함). 15분 안에 10번 로그인하지 못한 이메일은 429 TOO_MANY_ATTEMPTS 로 막는다.
 * 막힌 동안에는 비밀번호가 맞아도 막는다. 센 실패가 15분을 지나 빠지면 풀린다. 로그인에 성공하면 센 것을 지운다.
 *
 * <p>계정이 있는지와 관계없이 이메일 글자로 센다. 막혔다는 것이 계정이 있다는 뜻이 아니다.
 * 시도는 비밀번호를 견주기 전에 세고, 성공하면 지운다. 같이 들어온 요청들이 모두 검사를 지나 10번을 넘기지 못하게 하려는 것이다.
 * 이메일은 부르는 쪽(AccountService)이 소문자로 맞춰 넘기고, 계정도 그 글자로 찾는다.
 *
 * <p>shortcut: per-process memory, lost on restart; move to the DB or Redis when more than one server runs
 */
@Component
class LoginThrottle {

	static final int MAX_FAILURES = 10;

	static final Duration WINDOW = Duration.ofMinutes(15);

	private final Clock clock;

	/** 이메일마다 15분 안에 센 시도의 시각. 앞이 오래된 것이다. 비어 있는 것은 두지 않는다. */
	private final Map<String, Deque<Instant>> failures = new HashMap<>();

	/** 아무 이메일이나 넣어 메모리를 채우지 못하게, 15분마다 지난 시도만 남은 이메일을 지운다. */
	private Instant nextSweep = Instant.MIN;

	LoginThrottle(Clock clock) {
		this.clock = clock;
	}

	/** 로그인 시도를 하나 센다. 15분 안에 이미 10번 세었으면 세지 않고 429 TOO_MANY_ATTEMPTS. */
	synchronized void countAttempt(String email) {
		Instant now = this.clock.instant();
		Instant since = now.minus(WINDOW);
		dropExpired(now, since);
		Deque<Instant> recent = this.failures.computeIfAbsent(email, (k) -> new ArrayDeque<>());
		while (!recent.isEmpty() && !recent.peekFirst().isAfter(since)) {
			recent.removeFirst();
		}
		if (recent.size() >= MAX_FAILURES) {
			throw new ApiException(AuthErrorCode.TOO_MANY_ATTEMPTS);
		}
		recent.addLast(now);
	}

	/** 로그인에 성공했다. 그 이메일에서 센 것을 지운다. */
	synchronized void clear(String email) {
		this.failures.remove(email);
	}

	/** 센 것을 들고 있는 이메일 수. 테스트가 메모리를 비우는지 본다. */
	synchronized int size() {
		return this.failures.size();
	}

	private void dropExpired(Instant now, Instant since) {
		if (now.isBefore(this.nextSweep)) {
			return;
		}
		this.failures.values().removeIf((times) -> !times.getLast().isAfter(since));
		this.nextSweep = now.plus(WINDOW);
	}

}
