package com.readme;

import java.util.concurrent.Executor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * 통합 테스트의 @Async 일(커밋 뒤의 공고 분석)을 요청 안에서 끝낸다. 다른 스레드에서 돌리고 끝날 때까지 기다린다.
 *
 * <ul>
 * <li>요청이 돌아오면 일이 끝나 있어 테스트가 결과를 바로 본다.</li>
 * <li>앞 테스트가 남긴 일이 다음 테스트의 FakeClaudeApi 답을 가져가지 않는다.</li>
 * <li>같은 스레드에서 바로 돌리지 않는 것은, 커밋 뒤(@TransactionalEventListener)에도 그 스레드에 끝난 트랜잭션의 연결이 아직
 * 묶여 있어 일의 쓰기가 커밋되지 않기 때문이다. 운영에서는 Boot 의 applicationTaskExecutor 가 다른 스레드에서 돌린다.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class AwaitAsyncConfiguration implements AsyncConfigurer {

	@Override
	public Executor getAsyncExecutor() {
		return (task) -> {
			try {
				Thread.ofVirtual().start(task).join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		};
	}

}
