package com.readme;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.readme.common.config.TimeConfig;

/**
 * 통합 테스트의 시계를 멈춰 둔다. 서버의 "오늘"과 "지금"이 늘 같다.
 *
 * <p>2026-11-13 00:30(서울)은 UTC 로 아직 11-12 다. 날짜를 UTC 로 잘못 계산하면 테스트가 하루 어긋나 틀린다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-11-12T15:30:00Z");

	/** NOW 의 서울 날짜 */
	public static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(NOW, TimeConfig.SEOUL);
	}

}
