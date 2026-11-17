package com.readme.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.readme.common.error.ApiException;

/**
 * 로그인 실패 제한. 시계를 옮겨 가며 15분 창을 본다.
 */
class LoginThrottleTests {

	private static final String EMAIL = "garam@example.test";

	private final MovingClock clock = new MovingClock();

	private final LoginThrottle throttle = new LoginThrottle(this.clock);

	@Test
	void theEleventhAttemptWithinFifteenMinutesIsRefused() {
		failTenTimes(EMAIL);

		assertBlocked(EMAIL);
		// 다른 이메일은 따로 센다. 대소문자는 부르는 쪽이 맞춘다(LoginTests)
		assertThatNoException().isThrownBy(() -> this.throttle.countAttempt("narae@example.test"));
	}

	@Test
	void theBlockEndsAsTheOldestFailuresLeaveTheWindow() {
		// 0분부터 1분 간격으로 10번(0~9분)
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			this.throttle.countAttempt(EMAIL);
			this.clock.advance(Duration.ofMinutes(1));
		}

		// 14분 59초: 10번이 모두 15분 안이다
		this.clock.advance(Duration.ofMinutes(4).plusSeconds(59));
		assertBlocked(EMAIL);

		// 15분: 0분의 실패가 빠져 한 번 더 할 수 있다. 그 시도도 세므로 바로 다시 막힌다
		this.clock.advance(Duration.ofSeconds(1));
		assertThatNoException().isThrownBy(() -> this.throttle.countAttempt(EMAIL));
		assertBlocked(EMAIL);

		// 16분: 1분의 실패가 빠진다
		this.clock.advance(Duration.ofMinutes(1));
		assertThatNoException().isThrownBy(() -> this.throttle.countAttempt(EMAIL));
	}

	@Test
	void aBlockedAttemptIsNotCounted() {
		failTenTimes(EMAIL);
		// 10분에 막힌 시도는 세지 않는다. 그래서 막힘이 길어지지 않는다
		this.clock.advance(Duration.ofMinutes(10));
		for (int i = 0; i < 5; i++) {
			assertBlocked(EMAIL);
		}

		// 15분: 0분의 10번이 모두 빠진다
		this.clock.advance(Duration.ofMinutes(5));

		failTenTimes(EMAIL);
	}

	@Test
	void clearForgetsTheFailures() {
		failTenTimes(EMAIL);

		this.throttle.clear(EMAIL);

		failTenTimes(EMAIL);
	}

	@Test
	void emailsWhoseFailuresAllExpiredAreDropped() {
		this.throttle.countAttempt("a@example.test");
		this.throttle.countAttempt("b@example.test");
		this.throttle.countAttempt("c@example.test");
		assertThat(this.throttle.size()).isEqualTo(3);

		this.clock.advance(LoginThrottle.WINDOW);
		this.throttle.countAttempt("d@example.test");

		assertThat(this.throttle.size()).isEqualTo(1);
	}

	@Test
	void atMostMaxEmailsAreKeptAndTheEldestGoesFirst() {
		// 한 시각에 넣어 15분마다 지우는 것으로는 줄지 않는다
		failTenTimes(EMAIL);
		for (int i = 0; i < LoginThrottle.MAX_EMAILS; i++) {
			this.throttle.countAttempt("spray-" + i + "@example.test");
		}

		assertThat(this.throttle.size()).isEqualTo(LoginThrottle.MAX_EMAILS);
		// 가장 먼저 들어온 이메일이 빠져 센 것이 없어졌다
		assertThatNoException().isThrownBy(() -> this.throttle.countAttempt(EMAIL));
	}

	private void failTenTimes(String email) {
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			this.throttle.countAttempt(email);
		}
	}

	private void assertBlocked(String email) {
		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> this.throttle.countAttempt(email))
			.extracting(ApiException::errorCode)
			.isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS);
	}

	/** 테스트가 앞으로 옮기는 시계. */
	private static final class MovingClock extends Clock {

		private Instant now = Instant.parse("2026-11-12T15:30:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

	}

}
