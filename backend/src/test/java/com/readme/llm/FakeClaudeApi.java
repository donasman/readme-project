package com.readme.llm;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트의 Claude 호출. 실제 API 를 부르지 않고, 테스트가 정해 둔 답을 차례로 돌려준다. @IntegrationTest 를 붙인 테스트에서
 * 주입받아 쓴다. 내 PC 에 키가 있어 dev 프로필을 함께 켜는 테스트에서도 이것이 먼저다(@Primary).
 *
 * <p>정해 둔 답이 없으면 실패한다. 테스트끼리 서버를 나눠 쓰므로 LLM 을 부르는 테스트는 시작할 때 {@link #reset} 한다.
 */
@Primary
public class FakeClaudeApi implements ClaudeApi {

	private final Queue<ClaudeReply<?>> replies = new ConcurrentLinkedQueue<>();

	private final List<ClaudeRequest<?>> requests = new CopyOnWriteArrayList<>();

	@Override
	@SuppressWarnings("unchecked")
	public <T> ClaudeReply<T> send(ClaudeRequest<T> request) {
		this.requests.add(request);
		ClaudeReply<?> reply = this.replies.poll();
		if (reply == null) {
			throw new AssertionError("FakeClaudeApi 에 정해 둔 답이 없다: " + request.feature());
		}
		return (ClaudeReply<T>) reply;
	}

	/** 다음 호출의 답. 정한 순서대로 쓴다. */
	public void willReply(ClaudeReply<?> reply) {
		this.replies.add(reply);
	}

	/** 받은 요청들. 받은 순서다. */
	public List<ClaudeRequest<?>> requests() {
		return List.copyOf(this.requests);
	}

	public void reset() {
		this.replies.clear();
		this.requests.clear();
	}

}
