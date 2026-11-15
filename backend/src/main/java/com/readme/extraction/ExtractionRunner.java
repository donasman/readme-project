package com.readme.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.readme.common.error.ApiException;
import com.readme.extraction.ExtractionSteps.Work;
import com.readme.keyword.KeywordDictionary;
import com.readme.llm.Llm;
import com.readme.llm.LlmFeature;

/**
 * 추출 작업을 뒤에서 돌린다(경험카드 설계 4.6). 조각마다 LLM 에 묻고(트랜잭션 밖) ExtractionSteps 로 저장한다. 한도는 시작할 때
 * 본다(CardExtractions). 도중에 한도에 닿아도 끝까지 간다.
 *
 * <ul>
 * <li>동시에 도는 작업은 2개다. LLM 호출이 길어 많이 돌리면 API 의 요청 한도와 서버 메모리를 쓴다.</li>
 * <li>서버가 뜰 때 QUEUED·RUNNING 으로 남은 작업을 마친 조각 다음부터 이어 돌린다(배포 계획 6절 8번).</li>
 * <li>LLM 이 실패하면(키 없음, API 오류, 쓸 수 없는 답) 작업을 FAILED 로 두고 그 메시지를 남긴다. 다시 시작하면 이어서 한다.</li>
 * <li>app.extraction.synchronous 가 true 면(테스트) 부른 스레드에서 바로 돈다.</li>
 * </ul>
 */
@Component
class ExtractionRunner {

	private static final Logger log = LoggerFactory.getLogger(ExtractionRunner.class);

	private final ExtractionSteps steps;

	private final CardExtractionQueries queries;

	private final CardExtractionJobRepository jobs;

	private final Llm llm;

	private final boolean synchronous;

	private final ExecutorService executor = Executors.newFixedThreadPool(2, Thread.ofVirtual().name("extraction-", 1).factory());

	/** shortcut: 이 서버에서 도는 작업. 서버 한 대 가정이다, 여러 대면 작업 행을 잠가 한 서버만 돌게 한다 */
	private final Set<Long> running = ConcurrentHashMap.newKeySet();

	ExtractionRunner(ExtractionSteps steps, CardExtractionQueries queries, CardExtractionJobRepository jobs, Llm llm,
			@Value("${app.extraction.synchronous:false}") boolean synchronous) {
		this.steps = steps;
		this.queries = queries;
		this.jobs = jobs;
		this.llm = llm;
		this.synchronous = synchronous;
	}

	/** 작업을 돌린다. 이미 이 서버에서 돌고 있으면 아무것도 하지 않는다. 트랜잭션 밖에서 부른다. */
	void submit(long jobId) {
		if (this.synchronous) {
			run(jobId);
		}
		else {
			this.executor.execute(() -> run(jobId));
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	void resumeUnfinished() {
		List<Long> unfinished = this.jobs.findUnfinishedIds();
		if (!unfinished.isEmpty()) {
			log.info("끝나지 않은 카드 뽑기 {}개를 이어 돌린다", unfinished.size());
		}
		unfinished.forEach(this::submit);
	}

	private void run(long jobId) {
		if (!this.running.add(jobId)) {
			return;
		}
		try {
			Work work = this.steps.begin(jobId);
			if (work == null) {
				return;
			}
			int total = work.parts().size();
			for (int part = work.doneParts() + 1; part <= total; part++) {
				String text = work.parts().get(part - 1);
				String question = ExtractionPrompt.question(this.queries.cardTitles(work.userId()),
						tagHints(work.userId(), text), work.sourceName(), part, total, text);
				ExtractionAnswer answer = this.llm.ask(work.userId(), LlmFeature.CARD_EXTRACT, ExtractionPrompt.SYSTEM,
						question, ExtractionAnswer.class);
				this.steps.savePart(work, part, answer);
			}
			this.steps.finish(jobId);
		}
		catch (ApiException ex) {
			log.info("카드 뽑기 {} 실패: {}", jobId, ex.errorCode().name());
			this.steps.fail(jobId, ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("카드 뽑기 {} 실패", jobId, ex);
			this.steps.fail(jobId, "카드를 뽑지 못했습니다. 다시 시도해 주세요");
		}
		finally {
			this.running.remove(jobId);
		}
	}

	/** 태그 표기 후보: 자료 조각에 나온 사전 낱말(많이 나온 순), 그다음 내 카드의 태그(jobfit 의 prepare). */
	private List<String> tagHints(long userId, String text) {
		List<String> hints = new ArrayList<>(KeywordDictionary.standard().find(text).keySet());
		hints.addAll(this.queries.cardTags(userId));
		return hints;
	}

	@PreDestroy
	void stop() {
		this.executor.shutdownNow();
	}

}
