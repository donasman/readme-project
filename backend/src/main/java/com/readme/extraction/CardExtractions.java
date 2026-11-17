package com.readme.extraction;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.llm.Llm;

/** 카드 뽑기 시작(경험카드 설계 4.6). 작업을 만들기만 하고, 돌리는 것은 컨트롤러가 이 트랜잭션 뒤에 ExtractionRunner 에 맡긴다. */
@Service
class CardExtractions {

	private final CardExtractionJobRepository jobs;

	private final CardExtractionQueries queries;

	private final Llm llm;

	private final Clock clock;

	CardExtractions(CardExtractionJobRepository jobs, CardExtractionQueries queries, Llm llm, Clock clock) {
		this.jobs = jobs;
		this.queries = queries;
		this.llm = llm;
		this.clock = clock;
	}

	/**
	 * 내 자료의 카드 뽑기를 시작하고 작업 id 를 돌려준다.
	 * <ul>
	 * <li>그 자료의 작업이 이미 기다리거나 돌고 있으면 그 작업이다(새로 만들지 않고 한도도 보지 않는다).</li>
	 * <li>이번 달 LLM 한도에 닿았으면 429 LLM_LIMIT_REACHED.</li>
	 * <li>가장 최근 작업이 실패했으면 그 작업을 마친 조각 다음부터 다시 돌린다(이미 쓴 LLM 사용액을 다시 쓰지 않는다).</li>
	 * <li>그 밖에는 새 작업이다. 다 뽑은 자료를 다시 뽑으면, 이미 있는 카드와 제목이 같은 카드는 건너뛴다.</li>
	 * </ul>
	 * 남의 자료나 없는 자료는 404 SOURCE_NOT_FOUND.
	 */
	@Transactional
	long start(long userId, long sourceId) {
		CardExtractionQueries.Source source = this.queries.mySource(userId, sourceId)
			.orElseThrow(() -> new ApiException(ExtractionErrorCode.SOURCE_NOT_FOUND));
		CardExtractionJob latest = this.jobs.findFirstBySourceIdOrderByCreatedAtDescIdDesc(sourceId).orElse(null);
		if (latest != null && (latest.status().equals("QUEUED") || latest.status().equals("RUNNING"))) {
			return latest.getId();
		}
		this.llm.requireBudget(userId);
		if (latest != null && latest.status().equals("FAILED") && latest.doneParts() < latest.totalParts()) {
			latest.queueAgain();
			return latest.getId();
		}
		return this.jobs.save(new CardExtractionJob(userId, sourceId, SourceParts.count(source.body()), this.clock.instant()))
			.getId();
	}

}
