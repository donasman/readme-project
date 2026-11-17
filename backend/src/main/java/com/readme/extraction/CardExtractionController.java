package com.readme.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;
import com.readme.common.error.ApiException;

/** 카드 뽑기(경험카드 설계 4.2, 4.6). 내 자료와 내 작업만 다룬다. */
@RestController
class CardExtractionController {

	private final CardExtractions extractions;

	private final ExtractionRunner runner;

	private final CardExtractionQueries queries;

	CardExtractionController(CardExtractions extractions, ExtractionRunner runner, CardExtractionQueries queries) {
		this.extractions = extractions;
		this.runner = runner;
		this.queries = queries;
	}

	/** 뽑기를 시작한다. 202 와 작업. 작업은 뒤에서 돌고, 화면은 GET /api/card-extractions/{jobId} 로 몇 초마다 묻는다. */
	@PostMapping("/api/card-sources/{sourceId}/extract")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ExtractionJobResponse start(@PathVariable long sourceId, @CurrentUser LoginUser user) {
		long jobId = this.extractions.start(user.id(), sourceId);
		this.runner.submit(jobId); // 작업을 만든 트랜잭션이 끝난 뒤라야 다른 스레드가 그 행을 본다
		return job(jobId, user);
	}

	@GetMapping("/api/card-extractions/{jobId}")
	ExtractionJobResponse job(@PathVariable long jobId, @CurrentUser LoginUser user) {
		return this.queries.job(user.id(), jobId).orElseThrow(() -> new ApiException(ExtractionErrorCode.JOB_NOT_FOUND));
	}

}
