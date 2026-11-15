package com.readme.llm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.readme.common.config.TimeConfig;
import com.readme.common.error.ApiException;
import com.readme.llm.ClaudeApi.ClaudeReply;
import com.readme.llm.ClaudeApi.ClaudeRequest;

/**
 * 다른 기능이 LLM 을 부르는 한 곳(남은 작업 2단계). 부를 때마다 시도별 토큰과 금액을 llm_usage 에 남긴다.
 *
 * <p>한도는 사용자가 기능을 시작할 때 {@link #requireBudget} 으로 본다. {@link #ask} 는 한도를 보지 않는다. 그래서 이미 시작한 일
 * (자료의 여러 조각을 차례로 읽는 추출 작업)은 도중에 한도에 닿아도 끝까지 간다. 한도를 일 하나만큼 넘을 수 있다.
 *
 * <p>DB 트랜잭션 안에서 부르지 않는다. 답을 기다리는 동안 연결(풀 5개)을 잡는다.
 */
@Service
public class Llm {

	private static final Logger log = LoggerFactory.getLogger(Llm.class);

	private final ClaudeApi claude;

	private final LlmUsageRecorder recorder;

	private final LlmUsageQueries usages;

	private final LlmProperties properties;

	private final Clock clock;

	Llm(ClaudeApi claude, LlmUsageRecorder recorder, LlmUsageQueries usages, LlmProperties properties, Clock clock) {
		this.claude = claude;
		this.recorder = recorder;
		this.usages = usages;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 이번 달(서울 기준 달력 월) 사용액이 한도보다 적어야 한다. 닿았으면 429 LLM_LIMIT_REACHED.
	 *
	 * <p>shortcut: 같은 사람이 동시에 둘을 시작하면 둘 다 통과할 수 있다, 그만큼 한도를 더 넘는다. 문제가 되면 사람마다 잠근다.
	 */
	public void requireBudget(long userId) {
		LocalDate firstDay = LocalDate.now(this.clock).withDayOfMonth(1);
		Instant monthStart = firstDay.atStartOfDay(TimeConfig.SEOUL).toInstant();
		long limitMicros = this.properties.monthlyLimitUsd().multiply(BigDecimal.valueOf(1_000_000)).longValueExact();
		if (this.usages.spentMicros(userId, monthStart) >= limitMicros) {
			throw new ApiException(LlmErrorCode.LLM_LIMIT_REACHED);
		}
	}

	/**
	 * Claude 에 묻고 답을 type 의 모양으로 받는다. 사용액은 userId 에 넣는다(그룹이 나눠 쓰는 공고 분석은 올린 사람).
	 *
	 * <p>답을 못 받으면 503 LLM_UNAVAILABLE(사용액 없음), 받았지만 쓸 수 없으면 502 LLM_FAILED(사용액 있음)다.
	 *
	 * @param system 지시문. 없으면 null
	 * @param text   사용자 글. 자료와 공고의 글이 여기에 든다
	 * @param type   답의 모양. record 로 만든다. 그 JSON 스키마로 구조화 출력을 받는다
	 */
	public <T> T ask(long userId, LlmFeature feature, String system, String text, Class<T> type) {
		ClaudeReply<T> reply = this.claude
			.send(new ClaudeRequest<>(this.properties.model(), feature, system, text, type));
		this.recorder.record(userId, feature, reply.attempts());
		if (reply.outcome() == ClaudeApi.Outcome.DONE) {
			return reply.value();
		}
		log.warn("{} 의 답을 쓸 수 없다: {}", feature, reply.outcome());
		throw new ApiException(LlmErrorCode.LLM_FAILED);
	}

}
