package com.readme.extraction;

import java.time.LocalDate;

/**
 * 올린 자료 하나(GET /api/card-sources 의 한 항목, POST 의 source). 프런트의 CardSource 에 kind, parts, latestJob 을 더했다.
 *
 * @param name      파일 이름. 붙여넣은 글이면 null
 * @param kind      "file", "text"
 * @param chars     본문 글자 수(코드 포인트)
 * @param parts     본문을 5,000자씩 나눈 조각 수 = 카드를 뽑을 때의 LLM 호출 수
 * @param createdAt 올린 날(한국 날짜)
 * @param latestJob 이 자료의 가장 최근 추출 작업. 없으면 null
 */
record CardSourceResponse(long id, String name, SourceInput.Kind kind, int chars, int parts, LocalDate createdAt,
		LatestJob latestJob) {

	/**
	 * 추출 작업의 진행. 화면을 나갔다 와도 뽑는 중인 작업을 찾을 수 있게 목록에 싣는다(화면·API 대응 문서 3절 23번).
	 *
	 * @param status "queued", "running", "done", "failed"
	 */
	record LatestJob(long id, String status, int doneParts, int totalParts) {
	}

}
