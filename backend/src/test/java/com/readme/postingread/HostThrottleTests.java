package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * 같은 호스트에 1초에 하나. 시계를 손으로 움직여 기다릴 시간만 본다(실제로 자지 않는다).
 */
class HostThrottleTests {

	private final AtomicLong now = new AtomicLong(1_000_000_000L);

	private final HostThrottle throttle = new HostThrottle(Duration.ofSeconds(1), this.now::get);

	@Test
	void secondRequestToTheSameHostWaitsForItsTurn() {
		assertThat(this.throttle.reserve("www.saramin.co.kr")).contains(Duration.ZERO);
		assertThat(this.throttle.reserve("www.saramin.co.kr")).contains(Duration.ofSeconds(1));
		assertThat(this.throttle.reserve("www.saramin.co.kr")).contains(Duration.ofSeconds(2));
		// 다른 호스트는 따로 센다
		assertThat(this.throttle.reserve("www.jobkorea.co.kr")).contains(Duration.ZERO);
	}

	@Test
	void timePassingShortensTheWait() {
		this.throttle.reserve("example.com");
		this.now.addAndGet(Duration.ofMillis(400).toNanos());
		assertThat(this.throttle.reserve("example.com")).contains(Duration.ofMillis(600));
		this.now.addAndGet(Duration.ofSeconds(5).toNanos());
		assertThat(this.throttle.reserve("example.com")).contains(Duration.ZERO);
	}

	@Test
	void refusesInsteadOfWaitingTooLong() {
		for (int i = 0; i <= 5; i++) {
			assertThat(this.throttle.reserve("busy.example.com")).isPresent();
		}
		// 여섯이 줄을 섰으면 일곱째는 6초를 기다려야 한다. 차례를 잡지 않으므로 뒤의 요청을 더 밀지 않는다
		assertThat(this.throttle.reserve("busy.example.com")).isEmpty();
		this.now.addAndGet(Duration.ofSeconds(1).toNanos());
		assertThat(this.throttle.reserve("busy.example.com")).contains(Duration.ofSeconds(5));
	}

}
