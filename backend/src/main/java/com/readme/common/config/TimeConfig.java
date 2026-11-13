package com.readme.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 날짜와 시각의 기준. 서버가 어느 시간대에서 돌든 한국 날짜로 계산한다(설계 초안 그룹·공고 5.4).
 *
 * <p>오늘 날짜, 마감됨 판정, postedAt 같은 한국 날짜는 이 Clock 을 주입받아 {@code LocalDate.now(clock)} 으로 구한다.
 * {@code LocalDate.now()} 처럼 시계 없이 부르지 않는다. 테스트에서는 고정된 Clock 으로 바꿔 끼운다.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(SEOUL);
	}

}
