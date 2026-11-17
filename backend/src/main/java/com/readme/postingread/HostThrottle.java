package com.readme.postingread;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * 같은 호스트에 보내는 요청 사이를 띄운다(기본 1초에 하나). 차례를 잡고, 그때까지 기다릴 시간을 돌려준다.
 *
 * <p>shortcut: 서버 한 대의 메모리에만 있다, 서버를 여러 대 띄우면 DB 나 공유 저장소로 옮긴다.
 */
final class HostThrottle {

	/** 이만큼 넘게 기다려야 하면 기다리지 않고 거절한다. 요청 스레드를 오래 붙잡지 않게. */
	static final Duration MAX_WAIT = Duration.ofSeconds(5);

	/** 호스트가 이만큼 쌓이면 이미 지난 차례를 지운다. */
	private static final int PRUNE_AT = 1000;

	private final long interval;

	private final LongSupplier nanoTime;

	/** 호스트 → 다음 요청을 보낼 수 있는 때(nanoTime). */
	private final Map<String, Long> next = new HashMap<>();

	HostThrottle(Duration interval, LongSupplier nanoTime) {
		this.interval = interval.toNanos();
		this.nanoTime = nanoTime;
	}

	/** 차례를 잡고 기다릴 시간을 돌려준다. MAX_WAIT 를 넘으면 차례를 잡지 않고 빈 값. */
	synchronized Optional<Duration> reserve(String host) {
		long now = this.nanoTime.getAsLong();
		long at = Math.max(now, this.next.getOrDefault(host, now));
		if (at - now > MAX_WAIT.toNanos()) {
			return Optional.empty();
		}
		if (this.next.size() >= PRUNE_AT) {
			this.next.values().removeIf((time) -> time - now <= 0);
		}
		this.next.put(host, at + this.interval);
		return Optional.of(Duration.ofNanos(at - now));
	}

}
