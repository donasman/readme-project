package com.readme.llm;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Primary;

import com.readme.common.error.ApiException;

/**
 * 통합 테스트의 Claude 호출. 실제 API 를 부르지 않고, 테스트가 정해 둔 답을 차례로 돌려준다. @IntegrationTest 를 붙인 테스트에서
 * 주입받아 쓴다. 내 PC 에 키가 있어 dev 프로필을 함께 켜는 테스트에서도 이것이 먼저다(@Primary).
 *
 * <p>정해 둔 답이 없으면 키가 없는 서버처럼 503 LLM_UNAVAILABLE 이다. 그래서 LLM 을 부르는 줄 모르는 테스트는 LLM 없이 도는
 * 결과를 본다. 테스트끼리 서버를 나눠 쓰므로 LLM 을 부르는 테스트는 시작할 때 {@link #reset} 한다.
 */
@Primary
public class FakeClaudeApi implements ClaudeApi {

	/** 정해 둔 답의 토큰: 입력 1,000, 출력 100. Opus 5.5 로 4,000 + 2,000 = 6,000 micros 다. */
	public static final long ANSWER_COST_MICROS = 6_000;

	private final Queue<ClaudeReply<?>> replies = new ConcurrentLinkedQueue<>();

	private final List<ClaudeRequest<?>> requests = new CopyOnWriteArrayList<>();

	@Override
	@SuppressWarnings("unchecked")
	public <T> ClaudeReply<T> send(ClaudeRequest<T> request) {
		this.requests.add(request);
		ClaudeReply<?> reply = this.replies.poll();
		if (reply == null) {
			throw new ApiException(LlmErrorCode.LLM_UNAVAILABLE);
		}
		return (ClaudeReply<T>) reply;
	}

	/** 다음 호출의 답. 정한 순서대로 쓴다. */
	public void willReply(ClaudeReply<?> reply) {
		this.replies.add(reply);
	}

	/** 다음 호출에 이 값으로 답한다(Opus 5.5 한 번, {@link #ANSWER_COST_MICROS}). */
	public void willAnswer(Object value) {
		willReply(ClaudeReply.done(value, List.of(attempt())));
	}

	/** 다음 호출에서 거절한다(사용액은 든다). */
	public void willRefuse() {
		willReply(ClaudeReply.failed(Outcome.REFUSED, List.of(attempt())));
	}

	/** 받은 요청들. 받은 순서다. */
	public List<ClaudeRequest<?>> requests() {
		return List.copyOf(this.requests);
	}

	/** 받은 요청 수. */
	public int calls() {
		return this.requests.size();
	}

	/** 마지막으로 받은 요청의 사용자 글. */
	public String lastText() {
		return this.requests.getLast().text();
	}

	/** 마지막으로 받은 요청의 기능. */
	public LlmFeature lastFeature() {
		return this.requests.getLast().feature();
	}

	public void reset() {
		this.replies.clear();
		this.requests.clear();
	}

	private static LlmAttempt attempt() {
		return new LlmAttempt("claude-opus-5-5", 1000, 100, 0, 0, 0);
	}

}
